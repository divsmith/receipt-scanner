package com.receiptscanner.domain.usecase

import com.receiptscanner.domain.repository.YnabRepository
import javax.inject.Inject

class SyncPayeeCacheUseCase @Inject constructor(
    private val ynabRepository: YnabRepository,
) {
    suspend operator fun invoke(budgetId: String): Result<Unit> {
        val results = listOf(
            ynabRepository.syncPayees(budgetId),
            ynabRepository.syncCategories(budgetId),
            ynabRepository.syncAccounts(budgetId),
        )
        val firstFailure = results.firstOrNull { it.isFailure }
        return firstFailure ?: Result.success(Unit)
    }
}
