package com.receiptscanner.domain.usecase

import com.receiptscanner.domain.model.Account
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MatchAccountUseCaseTest {

    private lateinit var fakeRepository: FakeYnabRepository
    private lateinit var useCase: MatchAccountUseCase

    private val accounts = listOf(
        Account("acc1", "Chase Checking", "checking", true, false, 100000, "Last 4: 4532"),
        Account("acc2", "Amex Gold", "creditCard", true, false, -50000, "Card ending 8901"),
        Account("acc3", "Savings", "savings", true, false, 500000, null),
        Account("acc4", "Closed Card", "creditCard", true, true, 0, "Last 4: 1111"),
    )

    @BeforeEach
    fun setUp() {
        fakeRepository = FakeYnabRepository()
        useCase = MatchAccountUseCase(fakeRepository)
        fakeRepository.accountsResult = Result.success(accounts)
    }

    @Test
    fun `matches by card last four in notes`() = runTest {
        val result = useCase("budget1", cardLastFour = "4532")

        assertNotNull(result)
        assertEquals("Chase Checking", result!!.account.name)
        assertTrue(result.matchedByCardNumber)
    }

    @Test
    fun `matches second account by different last four`() = runTest {
        val result = useCase("budget1", cardLastFour = "8901")

        assertNotNull(result)
        assertEquals("Amex Gold", result!!.account.name)
        assertTrue(result.matchedByCardNumber)
    }

    @Test
    fun `skips closed accounts`() = runTest {
        val result = useCase("budget1", cardLastFour = "1111")

        assertNotNull(result)
        assertNotEquals("Closed Card", result!!.account.name)
    }

    @Test
    fun `falls back to default account when no card match`() = runTest {
        val result = useCase("budget1", cardLastFour = "9999", defaultAccountId = "acc2")

        assertNotNull(result)
        assertEquals("Amex Gold", result!!.account.name)
        assertFalse(result.matchedByCardNumber)
    }

    @Test
    fun `falls back to first checking or credit account`() = runTest {
        val result = useCase("budget1", cardLastFour = null, defaultAccountId = null)

        assertNotNull(result)
        assertEquals("Chase Checking", result!!.account.name)
        assertFalse(result.matchedByCardNumber)
    }

    @Test
    fun `null card last four uses default`() = runTest {
        val result = useCase("budget1", cardLastFour = null, defaultAccountId = "acc1")

        assertNotNull(result)
        assertEquals("Chase Checking", result!!.account.name)
        assertFalse(result.matchedByCardNumber)
    }

    @Test
    fun `returns null on repository failure`() = runTest {
        fakeRepository.accountsResult = Result.failure(Exception("fail"))

        val result = useCase("budget1", cardLastFour = "4532")

        assertNull(result)
    }
}
