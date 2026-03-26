package com.receiptscanner.presentation.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receiptscanner.data.local.UserPreferencesManager
import com.receiptscanner.domain.model.Account
import com.receiptscanner.domain.model.AccountMatchResult
import com.receiptscanner.domain.model.Category
import com.receiptscanner.domain.model.CategoryGroup
import com.receiptscanner.domain.model.ClearedStatus
import com.receiptscanner.domain.model.PayeeMatchResult
import com.receiptscanner.domain.model.Transaction
import com.receiptscanner.domain.repository.ReceiptRepository
import com.receiptscanner.domain.repository.YnabRepository
import com.receiptscanner.domain.usecase.MatchAccountUseCase
import com.receiptscanner.domain.usecase.MatchPayeeUseCase
import com.receiptscanner.domain.repository.TransactionQueueRepository
import com.receiptscanner.domain.usecase.SubmitTransactionUseCase
import com.receiptscanner.domain.usecase.SuggestCategoryUseCase
import com.receiptscanner.domain.util.MilliunitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import javax.inject.Inject

@HiltViewModel
class TransactionReviewViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
    private val matchPayeeUseCase: MatchPayeeUseCase,
    private val suggestCategoryUseCase: SuggestCategoryUseCase,
    private val matchAccountUseCase: MatchAccountUseCase,
    private val submitTransactionUseCase: SubmitTransactionUseCase,
    private val ynabRepository: YnabRepository,
    private val userPreferencesManager: UserPreferencesManager,
    private val transactionQueueRepository: TransactionQueueRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val receiptImagePath: String? = null,
        val payeeName: String = "",
        val selectedPayeeId: String? = null,
        val amount: String = "",
        val amountMilliunits: Long = 0,
        val date: LocalDate = LocalDate.now(),
        val memo: String = "",
        val selectedAccount: Account? = null,
        val selectedCategory: Category? = null,
        val accounts: List<Account> = emptyList(),
        val categoryGroups: List<CategoryGroup> = emptyList(),
        val payeeMatches: List<PayeeMatchResult> = emptyList(),
        val categorySuggestions: List<CategorySuggestion> = emptyList(),
        val accountMatch: AccountMatchResult? = null,
        val isSubmitting: Boolean = false,
        val isSubmitted: Boolean = false,
        val error: String? = null,
        val budgetId: String? = null,
        /** True once the user has explicitly chosen a category — prevents late matching results from overriding it. */
        val userEditedCategory: Boolean = false,
        /** True once the user has explicitly chosen a payee — prevents late matching results from overriding it. */
        val userEditedPayee: Boolean = false,
    ) {
        data class CategorySuggestion(
            val category: Category,
            val frequency: Int,
            val confidence: Double,
        )
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _submissionSuccess = MutableSharedFlow<Unit>()
    val submissionSuccess: SharedFlow<Unit> = _submissionSuccess.asSharedFlow()

    private val receiptId: String = savedStateHandle.get<String>("receiptId") ?: ""

    /** Tracks the latest payee search coroutine so stale results can be cancelled. */
    private var payeeSearchJob: Job? = null

    init {
        loadReceipt()
    }

    private fun loadReceipt() {
        viewModelScope.launch {
            val budgetId = userPreferencesManager.getBudgetId()
            if (budgetId == null) {
                _uiState.update { it.copy(isLoading = false, error = "No budget configured. Please check Settings.") }
                return@launch
            }
            _uiState.update { it.copy(budgetId = budgetId) }

            val receipt = receiptRepository.getReceiptById(receiptId).getOrNull()
            if (receipt == null) {
                _uiState.update { it.copy(isLoading = false, error = "Receipt not found") }
                return@launch
            }

            val extractedData = receipt.extractedData
            val storeName = extractedData?.storeName ?: ""
            val totalMilliunits = extractedData?.totalAmount ?: 0L
            val receiptDate = extractedData?.date ?: LocalDate.now()
            val cardLastFour = extractedData?.cardLastFour

            _uiState.update {
                it.copy(
                    receiptImagePath = receipt.imagePath,
                    payeeName = storeName,
                    amount = if (totalMilliunits != 0L)
                        MilliunitConverter.milliunitsToDollars(totalMilliunits).toPlainString()
                    else "",
                    amountMilliunits = totalMilliunits,
                    date = receiptDate,
                    memo = "Scanned receipt",
                )
            }

            // Load accounts, categories, and run matching in parallel
            launch { loadAccounts(budgetId) }
            launch { loadCategories(budgetId) }
            launch { runMatching(budgetId, storeName, cardLastFour) }
        }
    }

    private suspend fun loadAccounts(budgetId: String) {
        ynabRepository.getAccounts(budgetId).onSuccess { accounts ->
            val active = accounts.filter { !it.closed && it.onBudget }
            _uiState.update { it.copy(accounts = active) }
        }
    }

    private suspend fun loadCategories(budgetId: String) {
        ynabRepository.getCategories(budgetId).onSuccess { groups ->
            _uiState.update { it.copy(categoryGroups = groups, isLoading = false) }
        }.onFailure {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun runMatching(budgetId: String, storeName: String, cardLastFour: String?) {
        if (storeName.isNotBlank()) {
            val payeeMatches = matchPayeeUseCase(storeName, budgetId)
            _uiState.update { state ->
                val bestPayee = payeeMatches.firstOrNull()
                // Only auto-apply payee if the user hasn't already chosen one
                val shouldApplyPayee = !state.userEditedPayee && bestPayee != null && bestPayee.confidence > 0.7
                state.copy(
                    payeeMatches = payeeMatches,
                    selectedPayeeId = if (shouldApplyPayee) bestPayee!!.payee.id else state.selectedPayeeId,
                    payeeName = if (shouldApplyPayee) bestPayee!!.payee.name else state.payeeName,
                )
            }

            val bestMatch = _uiState.value.payeeMatches.firstOrNull()
            val suggestions = suggestCategoryUseCase(
                budgetId = budgetId,
                payeeId = bestMatch?.payee?.id,
                payeeName = storeName,
            )
            _uiState.update { state ->
                val bestCategory = suggestions.firstOrNull()
                state.copy(
                    categorySuggestions = suggestions.map {
                        UiState.CategorySuggestion(it.category, it.frequency, it.confidence)
                    },
                    // Only auto-apply category if the user hasn't already chosen one
                    selectedCategory = if (!state.userEditedCategory) bestCategory?.category ?: state.selectedCategory
                    else state.selectedCategory,
                )
            }
        }

        val defaultAccountId = userPreferencesManager.getDefaultAccountId()
        val accountMatch = matchAccountUseCase(budgetId, cardLastFour, defaultAccountId)
        _uiState.update {
            it.copy(
                accountMatch = accountMatch,
                selectedAccount = accountMatch?.account ?: it.selectedAccount,
            )
        }
    }

    fun updatePayeeName(name: String) {
        _uiState.update { it.copy(payeeName = name, selectedPayeeId = null) }
        val budgetId = _uiState.value.budgetId ?: return
        // Cancel the previous search so stale results don't overwrite the latest query
        payeeSearchJob?.cancel()
        payeeSearchJob = viewModelScope.launch {
            if (name.length >= 2) {
                val matches = matchPayeeUseCase(name, budgetId)
                _uiState.update { it.copy(payeeMatches = matches) }
            } else {
                _uiState.update { it.copy(payeeMatches = emptyList()) }
            }
        }
    }

    fun selectPayee(match: PayeeMatchResult) {
        _uiState.update {
            it.copy(
                payeeName = match.payee.name,
                selectedPayeeId = match.payee.id,
                payeeMatches = emptyList(),
                userEditedPayee = true,
            )
        }
        val budgetId = _uiState.value.budgetId ?: return
        viewModelScope.launch {
            val suggestions = suggestCategoryUseCase(
                budgetId = budgetId,
                payeeId = match.payee.id,
                payeeName = match.payee.name,
            )
            _uiState.update { state ->
                state.copy(
                    categorySuggestions = suggestions.map {
                        UiState.CategorySuggestion(it.category, it.frequency, it.confidence)
                    },
                    // Explicit payee selection may update category suggestion but still respects userEditedCategory
                    selectedCategory = if (!state.userEditedCategory)
                        suggestions.firstOrNull()?.category ?: state.selectedCategory
                    else state.selectedCategory,
                )
            }
        }
    }

    fun updateAmount(amount: String) {
        val milliunits = try {
            if (amount.isNotBlank()) {
                MilliunitConverter.dollarsToMilliunits(amount.toBigDecimal())
            } else 0L
        } catch (_: Exception) { _uiState.value.amountMilliunits }

        _uiState.update { it.copy(amount = amount, amountMilliunits = milliunits) }
    }

    fun updateDate(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    fun updateMemo(memo: String) {
        _uiState.update { it.copy(memo = memo) }
    }

    fun selectAccount(account: Account) {
        _uiState.update { it.copy(selectedAccount = account) }
    }

    fun selectCategory(category: Category) {
        _uiState.update { it.copy(selectedCategory = category, userEditedCategory = true) }
    }

    fun submitTransaction() {
        val state = _uiState.value
        val budgetId = state.budgetId ?: return
        val account = state.selectedAccount ?: run {
            _uiState.update { it.copy(error = "Please select an account") }
            return
        }

        if (state.amountMilliunits == 0L) {
            _uiState.update { it.copy(error = "Please enter an amount") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            val transaction = Transaction(
                accountId = account.id,
                date = state.date,
                // abs() prevents double-negation if user types a negative amount manually
                amount = -abs(state.amountMilliunits),
                payeeName = state.payeeName.ifBlank { null },
                payeeId = state.selectedPayeeId,
                categoryId = state.selectedCategory?.id,
                categoryName = state.selectedCategory?.name,
                memo = state.memo.ifBlank { null },
                cleared = ClearedStatus.UNCLEARED,
                approved = true,
            )

            submitTransactionUseCase(budgetId, transaction).fold(
                onSuccess = { transactionId ->
                    receiptRepository.updateReceiptStatus(receiptId, transactionId, "submitted")
                    _uiState.update { it.copy(isSubmitting = false, isSubmitted = true) }
                    _submissionSuccess.emit(Unit)
                },
                onFailure = { _ ->
                    // Attempt to queue for offline retry; surface error if that also fails
                    transactionQueueRepository.enqueue(transaction).fold(
                        onSuccess = {
                            _uiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    isSubmitted = true,
                                    error = null,
                                )
                            }
                            _submissionSuccess.emit(Unit)
                        },
                        onFailure = { queueError ->
                            _uiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    error = "Submission failed and could not be queued: ${queueError.message}",
                                )
                            }
                        },
                    )
                },
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
