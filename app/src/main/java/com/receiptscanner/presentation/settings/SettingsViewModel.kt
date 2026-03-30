package com.receiptscanner.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.receiptscanner.data.local.TokenProvider
import com.receiptscanner.data.local.UserPreferencesManager
import com.receiptscanner.data.remote.copilot.CopilotTokenProvider
import com.receiptscanner.domain.model.Account
import com.receiptscanner.domain.model.Budget
import com.receiptscanner.domain.model.OcrMode
import com.receiptscanner.domain.repository.TransactionQueueRepository
import com.receiptscanner.domain.repository.YnabRepository
import com.receiptscanner.domain.usecase.SyncPayeeCacheUseCase
import com.receiptscanner.worker.OfflineQueueWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tokenProvider: TokenProvider,
    private val copilotTokenProvider: CopilotTokenProvider,
    private val ynabRepository: YnabRepository,
    private val syncPayeeCacheUseCase: SyncPayeeCacheUseCase,
    private val userPreferencesManager: UserPreferencesManager,
    private val transactionQueueRepository: TransactionQueueRepository,
    private val workManager: WorkManager,
) : ViewModel() {

    data class UiState(
        val token: String = "",
        val isTokenSaved: Boolean = false,
        val budgets: List<Budget> = emptyList(),
        val selectedBudgetId: String? = null,
        val selectedBudgetName: String? = null,
        val accounts: List<Account> = emptyList(),
        val defaultAccountId: String? = null,
        val isSyncing: Boolean = false,
        val pendingCount: Int = 0,
        val ocrMode: OcrMode = OcrMode.LOCAL,
        val copilotToken: String = "",
        val isCopilotTokenSaved: Boolean = false,
        val error: String? = null,
        val successMessage: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadSavedState()
        observePendingCount()
        observeOcrMode()
    }

    private fun observeOcrMode() {
        viewModelScope.launch {
            userPreferencesManager.ocrMode.collect { mode ->
                _uiState.update { it.copy(ocrMode = mode) }
            }
        }
    }

    private fun observePendingCount() {
        viewModelScope.launch {
            // Use actionable count (PENDING + FAILED) so the retry panel stays visible for failed items
            transactionQueueRepository.getActionableCount().collect { count ->
                _uiState.update { it.copy(pendingCount = count) }
            }
        }
    }

    private fun loadSavedState() {
        viewModelScope.launch {
            val existingToken = tokenProvider.getToken()
            val existingCopilotToken = copilotTokenProvider.getToken()
            val budgetId = userPreferencesManager.getBudgetId()
            val defaultAccountId = userPreferencesManager.getDefaultAccountId()

            _uiState.update {
                it.copy(
                    // Never expose the raw token in UI state; show only a masked hint for saved tokens
                    token = if (!existingToken.isNullOrBlank())
                        "••••" + existingToken.takeLast(4)
                    else "",
                    isTokenSaved = !existingToken.isNullOrBlank(),
                    copilotToken = if (!existingCopilotToken.isNullOrBlank())
                        "••••" + existingCopilotToken.takeLast(4)
                    else "",
                    isCopilotTokenSaved = !existingCopilotToken.isNullOrBlank(),
                    selectedBudgetId = budgetId,
                    defaultAccountId = defaultAccountId,
                )
            }

            if (!existingToken.isNullOrBlank()) {
                loadBudgets()
                if (budgetId != null) {
                    loadAccounts(budgetId)
                }
            }
        }
    }

    fun updateToken(token: String) {
        _uiState.update { it.copy(token = token) }
    }

    fun saveToken() {
        val token = _uiState.value.token.trim()
        if (token.isBlank() || token.startsWith("••••")) {
            _uiState.update { it.copy(error = "Please enter a new token") }
            return
        }
        tokenProvider.setToken(token)
        _uiState.update {
            it.copy(
                // Replace displayed value with masked version immediately after save
                token = "••••" + token.takeLast(4),
                isTokenSaved = true,
                successMessage = "Token saved",
            )
        }
        loadBudgets()
    }

    fun updateOcrMode(mode: OcrMode) {
        if (mode == OcrMode.CLOUD && !_uiState.value.isCopilotTokenSaved) {
            _uiState.update { it.copy(error = "Please save a GitHub token first") }
            return
        }
        viewModelScope.launch {
            userPreferencesManager.saveOcrMode(mode)
        }
    }

    fun updateCopilotToken(token: String) {
        _uiState.update { it.copy(copilotToken = token) }
    }

    fun saveCopilotToken() {
        val token = _uiState.value.copilotToken.trim()
        if (token.isBlank() || token.startsWith("••••")) {
            _uiState.update { it.copy(error = "Please enter a GitHub token") }
            return
        }
        copilotTokenProvider.setToken(token)
        _uiState.update {
            it.copy(
                copilotToken = "••••" + token.takeLast(4),
                isCopilotTokenSaved = true,
                successMessage = "GitHub token saved",
            )
        }
    }

    fun loadBudgets() {
        viewModelScope.launch {
            ynabRepository.getBudgets().fold(
                onSuccess = { budgets ->
                    _uiState.update { it.copy(budgets = budgets, error = null) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to load budgets") }
                },
            )
        }
    }

    fun selectBudget(budget: Budget) {
        viewModelScope.launch {
            userPreferencesManager.saveBudget(budget.id, budget.name)
            _uiState.update {
                it.copy(
                    selectedBudgetId = budget.id,
                    selectedBudgetName = budget.name,
                    accounts = emptyList(),
                    defaultAccountId = null,
                )
            }
            loadAccounts(budget.id)
        }
    }

    private fun loadAccounts(budgetId: String) {
        viewModelScope.launch {
            ynabRepository.getAccounts(budgetId).fold(
                onSuccess = { accounts ->
                    val active = accounts.filter { !it.closed && it.onBudget }
                    _uiState.update { it.copy(accounts = active) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to load accounts") }
                },
            )
        }
    }

    fun selectDefaultAccount(account: Account) {
        viewModelScope.launch {
            userPreferencesManager.saveDefaultAccountId(account.id)
            _uiState.update { it.copy(defaultAccountId = account.id) }
        }
    }

    fun syncCache() {
        val budgetId = _uiState.value.selectedBudgetId ?: run {
            _uiState.update { it.copy(error = "Please select a budget first") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, error = null) }
            syncPayeeCacheUseCase(budgetId).fold(
                onSuccess = {
                    _uiState.update { it.copy(isSyncing = false, successMessage = "YNAB data synced successfully") }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isSyncing = false, error = e.message ?: "Sync failed") }
                },
            )
        }
    }

    fun retryPending() {
        viewModelScope.launch {
            transactionQueueRepository.retryFailed()
            OfflineQueueWorker.enqueueOneShot(workManager)
            _uiState.update { it.copy(successMessage = "Retrying pending transactions…") }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            tokenProvider.setToken(null)
            copilotTokenProvider.setToken(null)
            userPreferencesManager.clearAll()
            _uiState.value = UiState()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }
}
