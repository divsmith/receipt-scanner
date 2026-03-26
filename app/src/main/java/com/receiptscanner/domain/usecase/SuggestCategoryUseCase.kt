package com.receiptscanner.domain.usecase

import com.receiptscanner.domain.model.CategorySuggestion
import com.receiptscanner.domain.repository.YnabRepository
import javax.inject.Inject

class SuggestCategoryUseCase @Inject constructor(
    private val ynabRepository: YnabRepository,
) {
    /**
     * Suggest categories for a given payee. If payeeId is provided, look at
     * historical transactions for that payee and rank categories by frequency.
     * If no payeeId, return the most commonly used categories overall.
     */
    suspend operator fun invoke(
        budgetId: String,
        payeeId: String? = null,
        payeeName: String? = null,
    ): List<CategorySuggestion> {
        val transactions = ynabRepository.getTransactions(budgetId).getOrElse { return emptyList() }
        val categoryGroups = ynabRepository.getCategories(budgetId).getOrElse { return emptyList() }

        val categoryMap = categoryGroups
            .flatMap { it.categories }
            .filter { !it.hidden }
            .associateBy { it.id }

        val relevantTransactions = if (payeeId != null) {
            transactions.filter { it.payeeId == payeeId }
        } else if (payeeName != null) {
            transactions.filter { it.payeeName?.equals(payeeName, ignoreCase = true) == true }
        } else {
            transactions
        }

        val categoryFrequency = relevantTransactions
            .filter { it.categoryId != null }
            .groupingBy { it.categoryId!! }
            .eachCount()

        val totalCount = categoryFrequency.values.sum().coerceAtLeast(1)

        return categoryFrequency
            .entries
            .mapNotNull { (categoryId, count) ->
                val category = categoryMap[categoryId] ?: return@mapNotNull null
                CategorySuggestion(
                    category = category,
                    frequency = count,
                    confidence = count.toDouble() / totalCount,
                )
            }
            .sortedByDescending { it.frequency }
            .take(5)
    }
}
