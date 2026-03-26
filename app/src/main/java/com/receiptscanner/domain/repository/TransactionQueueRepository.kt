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
    fun getPendingCount(): Flow<Int>
}
