package com.receiptscanner.domain.usecase

import com.receiptscanner.domain.model.Category
import com.receiptscanner.domain.model.CategoryGroup
import com.receiptscanner.domain.model.Transaction
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SuggestCategoryUseCaseTest {

    private lateinit var fakeRepository: FakeYnabRepository
    private lateinit var useCase: SuggestCategoryUseCase

    private val groceryCat = Category("cat1", "grp1", "Groceries", false, 50000, 25000)
    private val diningCat = Category("cat2", "grp1", "Dining Out", false, 30000, 15000)
    private val gasCat = Category("cat3", "grp2", "Gas", false, 20000, 10000)

    private val categoryGroups = listOf(
        CategoryGroup("grp1", "Frequent", false, listOf(groceryCat, diningCat)),
        CategoryGroup("grp2", "Auto", false, listOf(gasCat)),
    )

    private val transactions = listOf(
        Transaction(accountId = "acc1", date = LocalDate.now(), amount = -10000, payeeName = "Walmart", payeeId = "p1", categoryId = "cat1"),
        Transaction(accountId = "acc1", date = LocalDate.now(), amount = -15000, payeeName = "Walmart", payeeId = "p1", categoryId = "cat1"),
        Transaction(accountId = "acc1", date = LocalDate.now(), amount = -5000, payeeName = "Walmart", payeeId = "p1", categoryId = "cat2"),
        Transaction(accountId = "acc1", date = LocalDate.now(), amount = -8000, payeeName = "Shell", payeeId = "p2", categoryId = "cat3"),
    )

    @BeforeEach
    fun setUp() {
        fakeRepository = FakeYnabRepository()
        useCase = SuggestCategoryUseCase(fakeRepository)
        fakeRepository.transactionsResult = Result.success(transactions)
        fakeRepository.categoriesResult = Result.success(categoryGroups)
    }

    @Test
    fun `suggests most frequent category for payee`() = runTest {
        val suggestions = useCase("budget1", payeeId = "p1")

        assertTrue(suggestions.isNotEmpty())
        assertEquals("Groceries", suggestions.first().category.name)
        assertEquals(2, suggestions.first().frequency)
    }

    @Test
    fun `falls back to global frequency with no payee`() = runTest {
        val suggestions = useCase("budget1")

        assertTrue(suggestions.isNotEmpty())
        assertEquals("Groceries", suggestions.first().category.name)
    }

    @Test
    fun `matches by payee name when no payee id`() = runTest {
        val suggestions = useCase("budget1", payeeName = "Walmart")

        assertTrue(suggestions.isNotEmpty())
        assertEquals("Groceries", suggestions.first().category.name)
    }

    @Test
    fun `returns empty on repository failure`() = runTest {
        fakeRepository.transactionsResult = Result.failure(Exception("fail"))

        val suggestions = useCase("budget1", payeeId = "p1")

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `limits to 5 suggestions`() = runTest {
        val suggestions = useCase("budget1")
        assertTrue(suggestions.size <= 5)
    }
}
