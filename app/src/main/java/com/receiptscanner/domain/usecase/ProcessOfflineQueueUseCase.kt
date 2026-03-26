package com.receiptscanner.domain.usecase

import com.receiptscanner.domain.model.PendingStatus
import com.receiptscanner.domain.repository.TransactionQueueRepository
import com.receiptscanner.domain.repository.YnabRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ProcessOfflineQueueUseCase @Inject constructor(
    private val queueRepository: TransactionQueueRepository,
    private val ynabRepository: YnabRepository,
) {
    suspend operator fun invoke(budgetId: String): Result<Int> {
        return try {
            var submitted = 0
            val pendingList = queueRepository.getPendingTransactions().first()

            for (pendingTx in pendingList) {
                if (pendingTx.status != PendingStatus.PENDING) continue

                queueRepository.markSubmitting(pendingTx.id)
                val result = ynabRepository.createTransaction(budgetId, pendingTx.transaction)
                result.fold(
                    onSuccess = {
                        queueRepository.markSubmitted(pendingTx.id)
                        submitted++
                    },
                    onFailure = { e ->
                        queueRepository.markFailed(pendingTx.id, e.message ?: "Unknown error")
                    },
                )
            }
            Result.success(submitted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
