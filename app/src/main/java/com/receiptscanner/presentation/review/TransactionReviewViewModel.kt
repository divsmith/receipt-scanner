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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
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
                state.copy(
                    payeeMatches = payeeMatches,
                    selectedPayeeId = bestPayee?.payee?.id,
                    payeeName = if (bestPayee != null && bestPayee.confidence > 0.7)
                        bestPayee.payee.name else state.payeeName,
                )
            }

            val bestMatch = payeeMatches.firstOrNull()
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
                    selectedCategory = bestCategory?.category ?: state.selectedCategory,
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
        viewModelScope.launch {
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
                    selectedCategory = suggestions.firstOrNull()?.category ?: state.selectedCategory,
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
        _uiState.update { it.copy(selectedCategory = category) }
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
                amount = -state.amountMilliunits,
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
                onFailure = { e ->
                    // Enqueue for offline retry
                    transactionQueueRepository.enqueue(transaction)
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            isSubmitted = true,
                            error = null,
                        )
                    }
                    _submissionSuccess.emit(Unit)
                },
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
