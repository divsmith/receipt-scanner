package com.receiptscanner.domain.usecase

import com.receiptscanner.domain.model.ClearedStatus
import com.receiptscanner.domain.model.Transaction
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SubmitTransactionUseCaseTest {

    private lateinit var fakeYnabRepository: FakeYnabRepository
    private lateinit var useCase: SubmitTransactionUseCase

    private val sampleTransaction = Transaction(
        accountId = "acc-1",
        date = LocalDate.of(2025, 1, 15),
        amount = -15000L,
        payeeName = "Test Store",
        payeeId = "payee-1",
        categoryId = "cat-1",
        memo = "Test memo",
        cleared = ClearedStatus.UNCLEARED,
        approved = true,
    )

    @BeforeEach
    fun setUp() {
        fakeYnabRepository = FakeYnabRepository()
        useCase = SubmitTransactionUseCase(fakeYnabRepository)
    }

    @Test
    fun `successful submission returns transaction id`() = runTest {
        fakeYnabRepository.createTransactionResult = Result.success("new-tx-123")

        val result = useCase("budget-1", sampleTransaction)

        assertTrue(result.isSuccess)
        assertEquals("new-tx-123", result.getOrNull())
    }

    @Test
    fun `api failure returns error result`() = runTest {
        fakeYnabRepository.createTransactionResult = Result.failure(Exception("Unauthorized"))

        val result = useCase("budget-1", sampleTransaction)

        assertTrue(result.isFailure)
        assertEquals("Unauthorized", result.exceptionOrNull()?.message)
    }

    @Test
    fun `network error returns failure result`() = runTest {
        fakeYnabRepository.createTransactionResult = Result.failure(
            java.io.IOException("No internet connection")
        )

        val result = useCase("budget-1", sampleTransaction)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.io.IOException)
    }

    @Test
    fun `submits with correct budget id`() = runTest {
        var capturedBudgetId: String? = null
        val trackingRepo = object : FakeYnabRepository() {
            override suspend fun createTransaction(budgetId: String, transaction: Transaction): Result<String> {
                capturedBudgetId = budgetId
                return Result.success("tx-id")
            }
        }
        val trackingUseCase = SubmitTransactionUseCase(trackingRepo)

        trackingUseCase("my-budget-id", sampleTransaction)

        assertEquals("my-budget-id", capturedBudgetId)
    }
}
