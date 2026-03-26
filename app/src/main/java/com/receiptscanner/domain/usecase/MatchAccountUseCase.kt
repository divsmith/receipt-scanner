package com.receiptscanner.domain.usecase

import com.receiptscanner.domain.model.AccountMatchResult
import com.receiptscanner.domain.repository.YnabRepository
import javax.inject.Inject

class MatchAccountUseCase @Inject constructor(
    private val ynabRepository: YnabRepository,
) {
    /**
     * Match an account by the last 4 digits of a card number extracted from a receipt.
     * Looks at the note field of YNAB accounts for the last-4 digits.
     * Falls back to a configured default account ID if no card match found.
     */
    suspend operator fun invoke(
        budgetId: String,
        cardLastFour: String?,
        defaultAccountId: String? = null,
    ): AccountMatchResult? {
        val accounts = ynabRepository.getAccounts(budgetId).getOrElse { return null }
        val activeAccounts = accounts.filter { !it.closed && it.onBudget }

        // Try to match by card last-4 in account notes
        if (!cardLastFour.isNullOrBlank()) {
            val matched = activeAccounts.firstOrNull { account ->
                account.note?.contains(cardLastFour) == true
            }
            if (matched != null) {
                return AccountMatchResult(
                    account = matched,
                    matchedByCardNumber = true,
                )
            }
        }

        // Fall back to default account
        if (defaultAccountId != null) {
            val defaultAccount = activeAccounts.firstOrNull { it.id == defaultAccountId }
            if (defaultAccount != null) {
                return AccountMatchResult(
                    account = defaultAccount,
                    matchedByCardNumber = false,
                )
            }
        }

        // Last resort: return the first active checking/credit card account
        val fallback = activeAccounts.firstOrNull { it.type in listOf("checking", "creditCard") }
            ?: activeAccounts.firstOrNull()

        return fallback?.let {
            AccountMatchResult(account = it, matchedByCardNumber = false)
        }
    }
}
