package com.receiptscanner.domain.usecase

import com.receiptscanner.domain.model.Account
import com.receiptscanner.domain.model.Budget
import com.receiptscanner.domain.model.CategoryGroup
import com.receiptscanner.domain.model.Payee
import com.receiptscanner.domain.model.Transaction
import com.receiptscanner.domain.repository.YnabRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MatchPayeeUseCaseTest {

    private lateinit var fakeRepository: FakeYnabRepository
    private lateinit var useCase: MatchPayeeUseCase

    private val samplePayees = listOf(
        Payee("1", "Walmart"),
        Payee("2", "Target"),
        Payee("3", "Costco Wholesale"),
        Payee("4", "Kroger"),
        Payee("5", "The Home Depot"),
        Payee("6", "Amazon"),
    )

    @BeforeEach
    fun setUp() {
        fakeRepository = FakeYnabRepository()
        useCase = MatchPayeeUseCase(fakeRepository)
    }

    @Test
    fun `exact match returns highest confidence`() = runTest {
        fakeRepository.payeesResult = Result.success(samplePayees)

        val results = useCase("Walmart", "budget1")

        assertTrue(results.isNotEmpty())
        assertEquals("Walmart", results.first().payee.name)
        assertTrue(results.first().confidence > 0.9)
    }

    @Test
    fun `case insensitive matching works`() = runTest {
        fakeRepository.payeesResult = Result.success(samplePayees)

        val results = useCase("WALMART", "budget1")

        assertTrue(results.isNotEmpty())
        assertEquals("Walmart", results.first().payee.name)
    }

    @Test
    fun `partial match returns results`() = runTest {
        fakeRepository.payeesResult = Result.success(samplePayees)

        val results = useCase("Costco", "budget1")

        assertTrue(results.isNotEmpty())
        assertEquals("Costco Wholesale", results.first().payee.name)
    }

    @Test
    fun `blank store name returns empty`() = runTest {
        fakeRepository.payeesResult = Result.success(samplePayees)

        val results = useCase("", "budget1")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `repository failure returns empty`() = runTest {
        fakeRepository.payeesResult = Result.failure(Exception("API error"))

        val results = useCase("Walmart", "budget1")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `results limited to 5`() = runTest {
        val manyPayees = (1..20).map { Payee("$it", "Store $it") }
        fakeRepository.payeesResult = Result.success(manyPayees)

        val results = useCase("Store", "budget1")

        assertTrue(results.size <= 5)
    }

    @Test
    fun `low confidence matches filtered out`() = runTest {
        fakeRepository.payeesResult = Result.success(samplePayees)

        val results = useCase("ZZZZZZZ", "budget1")

        assertTrue(results.isEmpty())
    }
}
