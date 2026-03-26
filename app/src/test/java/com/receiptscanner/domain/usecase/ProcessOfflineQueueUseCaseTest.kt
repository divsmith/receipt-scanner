package com.receiptscanner.domain.usecase

import com.receiptscanner.domain.model.ClearedStatus
import com.receiptscanner.domain.model.PendingStatus
import com.receiptscanner.domain.model.PendingTransaction
import com.receiptscanner.domain.model.Transaction
import com.receiptscanner.domain.repository.TransactionQueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ProcessOfflineQueueUseCaseTest {

    private lateinit var fakeYnabRepository: FakeYnabRepository
    private lateinit var fakeQueueRepository: FakeTransactionQueueRepository
    private lateinit var useCase: ProcessOfflineQueueUseCase

    @BeforeEach
    fun setUp() {
        fakeYnabRepository = FakeYnabRepository()
        fakeQueueRepository = FakeTransactionQueueRepository()
        useCase = ProcessOfflineQueueUseCase(fakeQueueRepository, fakeYnabRepository)
    }

    @Test
    fun `processes pending transactions successfully`() = runTest {
        val tx1 = makePendingTransaction(1L)
        val tx2 = makePendingTransaction(2L)
        fakeQueueRepository.addPending(tx1)
        fakeQueueRepository.addPending(tx2)
        fakeYnabRepository.createTransactionResult = Result.success("tx-id")

        val result = useCase("budget-1")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
        assertEquals(2, fakeQueueRepository.submittedIds.size)
        assertTrue(fakeQueueRepository.failedIds.isEmpty())
    }

    @Test
    fun `handles individual failures gracefully`() = runTest {
        val tx1 = makePendingTransaction(1L)
        val tx2 = makePendingTransaction(2L)
        fakeQueueRepository.addPending(tx1)
        fakeQueueRepository.addPending(tx2)

        // Fail on the first call, succeed on the second
        var callCount = 0
        fakeYnabRepository.createTransactionResult = Result.success("tx-id")
        // Override with a callable approach: fail first, succeed second
        fakeQueueRepository.addPending(makePendingTransaction(1L))
        fakeQueueRepository.addPending(makePendingTransaction(2L))
        fakeQueueRepository.clearAll()

        fakeQueueRepository.addPending(makePendingTransaction(1L))
        fakeQueueRepository.addPending(makePendingTransaction(2L))
        fakeYnabRepository.createTransactionResult = Result.failure(Exception("Network error"))

        val result = useCase("budget-1")

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        assertEquals(2, fakeQueueRepository.failedIds.size)
    }

    @Test
    fun `empty queue returns zero submitted`() = runTest {
        val result = useCase("budget-1")

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
    }

    @Test
    fun `skips non-PENDING transactions`() = runTest {
        val failedTx = makePendingTransaction(1L, PendingStatus.FAILED)
        val submittingTx = makePendingTransaction(2L, PendingStatus.SUBMITTING)
        fakeQueueRepository.addPending(failedTx)
        fakeQueueRepository.addPending(submittingTx)
        fakeYnabRepository.createTransactionResult = Result.success("tx-id")

        val result = useCase("budget-1")

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
    }

    @Test
    fun `mixed success and failure counts correctly`() = runTest {
        fakeQueueRepository.addPending(makePendingTransaction(1L))
        fakeYnabRepository.createTransactionResult = Result.success("tx-id")

        val result = useCase("budget-1")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        assertEquals(1, fakeQueueRepository.submittedIds.size)
    }

    private fun makePendingTransaction(
        id: Long,
        status: PendingStatus = PendingStatus.PENDING,
    ) = PendingTransaction(
        id = id,
        transaction = Transaction(
            accountId = "acc-1",
            date = LocalDate.of(2025, 1, 15),
            amount = -10000L,
            payeeName = "Test Store",
            cleared = ClearedStatus.UNCLEARED,
            approved = true,
        ),
        status = status,
        errorMessage = null,
        createdAt = System.currentTimeMillis(),
    )
}

/**
 * Fake TransactionQueueRepository for unit tests.
 */
class FakeTransactionQueueRepository : TransactionQueueRepository {

    private val pendingTransactions = mutableListOf<PendingTransaction>()
    val submittedIds = mutableListOf<Long>()
    val failedIds = mutableListOf<Long>()
    private var nextId = 100L

    fun addPending(tx: PendingTransaction) {
        pendingTransactions.add(tx)
    }

    fun clearAll() {
        pendingTransactions.clear()
        submittedIds.clear()
        failedIds.clear()
    }

    override suspend fun enqueue(transaction: Transaction): Result<Long> {
        val id = nextId++
        pendingTransactions.add(
            PendingTransaction(
                id = id,
                transaction = transaction,
                status = PendingStatus.PENDING,
                errorMessage = null,
                createdAt = System.currentTimeMillis(),
            )
        )
        return Result.success(id)
    }

    override fun getPendingTransactions(): Flow<List<PendingTransaction>> {
        return flowOf(pendingTransactions.toList())
    }

    override suspend fun markSubmitting(id: Long): Result<Unit> {
        val idx = pendingTransactions.indexOfFirst { it.id == id }
        if (idx >= 0) {
            pendingTransactions[idx] = pendingTransactions[idx].copy(status = PendingStatus.SUBMITTING)
        }
        return Result.success(Unit)
    }

    override suspend fun markSubmitted(id: Long): Result<Unit> {
        submittedIds.add(id)
        pendingTransactions.removeAll { it.id == id }
        return Result.success(Unit)
    }

    override suspend fun markFailed(id: Long, error: String): Result<Unit> {
        failedIds.add(id)
        val idx = pendingTransactions.indexOfFirst { it.id == id }
        if (idx >= 0) {
            pendingTransactions[idx] = pendingTransactions[idx].copy(
                status = PendingStatus.FAILED,
                errorMessage = error,
            )
        }
        return Result.success(Unit)
    }

    override suspend fun retryFailed(): Result<Unit> {
        pendingTransactions.replaceAll {
            if (it.status == PendingStatus.FAILED) it.copy(status = PendingStatus.PENDING, errorMessage = null)
            else it
        }
        return Result.success(Unit)
    }

    override fun getPendingCount(): Flow<Int> {
        return flowOf(pendingTransactions.count { it.status == PendingStatus.PENDING })
    }
}
