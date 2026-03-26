package com.receiptscanner.domain.repository

import com.receiptscanner.domain.model.PendingTransaction
import com.receiptscanner.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionQueueRepository {
    suspend fun enqueue(transaction: Transaction): Result<Long>
    fun getPendingTransactions(): Flow<List<PendingTransaction>>
    suspend fun markSubmitting(id: Long): Result<Unit>
    suspend fun markSubmitted(id: Long): Result<Unit>
    suspend fun markFailed(id: Long, error: String): Result<Unit>
    suspend fun retryFailed(): Result<Unit>
    /** Count of PENDING-only items (active queue). */
    fun getPendingCount(): Flow<Int>
    /** Count of PENDING + FAILED items (shown in settings retry panel). */
    fun getActionableCount(): Flow<Int>
    /** Reset SUBMITTING items older than [thresholdMs] back to PENDING so they can be retried. */
    suspend fun resetStuckSubmitting(thresholdMs: Long = 5 * 60 * 1000L): Result<Unit>
}
