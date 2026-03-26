package com.receiptscanner.domain.usecase

import com.receiptscanner.domain.model.PayeeMatchResult
import com.receiptscanner.domain.repository.YnabRepository
import com.receiptscanner.domain.util.FuzzyMatcher
import javax.inject.Inject

class MatchPayeeUseCase @Inject constructor(
    private val ynabRepository: YnabRepository,
) {
    suspend operator fun invoke(
        storeName: String,
        budgetId: String,
    ): List<PayeeMatchResult> {
        if (storeName.isBlank()) return emptyList()

        val payees = ynabRepository.getPayees(budgetId).getOrElse { return emptyList() }

        return payees
            .map { payee ->
                PayeeMatchResult(
                    payee = payee,
                    confidence = FuzzyMatcher.combinedScore(storeName, payee.name),
                )
            }
            .filter { it.confidence > 0.3 }
            .sortedByDescending { it.confidence }
            .take(5)
    }
}
