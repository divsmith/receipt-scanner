package com.receiptscanner.data.repository

import com.receiptscanner.data.local.dao.PendingTransactionDao
import com.receiptscanner.data.local.entity.PendingTransactionEntity
import com.receiptscanner.domain.model.ClearedStatus
import com.receiptscanner.domain.model.PendingStatus
import com.receiptscanner.domain.model.PendingTransaction
import com.receiptscanner.domain.model.Transaction
import com.receiptscanner.domain.repository.TransactionQueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionQueueRepositoryImpl @Inject constructor(
    private val pendingTransactionDao: PendingTransactionDao,
) : TransactionQueueRepository {

    override suspend fun enqueue(transaction: Transaction): Result<Long> = runCatching {
        val entity = PendingTransactionEntity(
            accountId = transaction.accountId,
            date = transaction.date.toString(),
            amount = transaction.amount,
            payeeName = transaction.payeeName,
            payeeId = transaction.payeeId,
            categoryId = transaction.categoryId,
            memo = transaction.memo,
            cleared = transaction.cleared.name.lowercase(),
            approved = transaction.approved,
            status = PendingStatus.PENDING.name,
            errorMessage = null,
            createdAt = System.currentTimeMillis(),
        )
        pendingTransactionDao.insert(entity)
    }

    override fun getPendingTransactions(): Flow<List<PendingTransaction>> {
        return pendingTransactionDao.getAllPending().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun markSubmitting(id: Long): Result<Unit> = runCatching {
        pendingTransactionDao.updateStatus(id, PendingStatus.SUBMITTING.name)
    }

    override suspend fun markSubmitted(id: Long): Result<Unit> = runCatching {
        pendingTransactionDao.deleteById(id)
    }

    override suspend fun markFailed(id: Long, error: String): Result<Unit> = runCatching {
        pendingTransactionDao.updateStatus(id, PendingStatus.FAILED.name, error)
    }

    override suspend fun retryFailed(): Result<Unit> = runCatching {
        pendingTransactionDao.retryAllFailed()
    }

    override fun getPendingCount(): Flow<Int> {
        return pendingTransactionDao.countPending()
    }
}

private fun PendingTransactionEntity.toDomain() = PendingTransaction(
    id = id,
    transaction = Transaction(
        accountId = accountId,
        date = LocalDate.parse(date),
        amount = amount,
        payeeName = payeeName,
        payeeId = payeeId,
        categoryId = categoryId,
        memo = memo,
        cleared = when (cleared) {
            "cleared" -> ClearedStatus.CLEARED
            "reconciled" -> ClearedStatus.RECONCILED
            else -> ClearedStatus.UNCLEARED
        },
        approved = approved,
    ),
    status = PendingStatus.valueOf(status),
    errorMessage = errorMessage,
    createdAt = createdAt,
)
