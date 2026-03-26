package com.receiptscanner.data.repository

import com.receiptscanner.data.local.TokenProvider
import com.receiptscanner.data.local.dao.AccountCacheDao
import com.receiptscanner.data.local.dao.CategoryCacheDao
import com.receiptscanner.data.local.dao.PayeeCacheDao
import com.receiptscanner.data.local.dao.SyncMetadataDao
import com.receiptscanner.data.local.entity.AccountCacheEntity
import com.receiptscanner.data.local.entity.CategoryCacheEntity
import com.receiptscanner.data.local.entity.PayeeCacheEntity
import com.receiptscanner.data.local.entity.SyncMetadataEntity
import com.receiptscanner.data.remote.YnabApi
import com.receiptscanner.data.remote.dto.AccountDto
import com.receiptscanner.data.remote.dto.AccountsData
import com.receiptscanner.data.remote.dto.AccountsResponse
import com.receiptscanner.data.remote.dto.BudgetDto
import com.receiptscanner.data.remote.dto.BudgetsData
import com.receiptscanner.data.remote.dto.BudgetsResponse
import com.receiptscanner.data.remote.dto.CategoryDto
import com.receiptscanner.data.remote.dto.CategoryGroupDto
import com.receiptscanner.data.remote.dto.CategoriesData
import com.receiptscanner.data.remote.dto.CategoriesResponse
import com.receiptscanner.data.remote.dto.CreateTransactionData
import com.receiptscanner.data.remote.dto.CreateTransactionResponse
import com.receiptscanner.data.remote.dto.PayeeDto
import com.receiptscanner.data.remote.dto.PayeesData
import com.receiptscanner.data.remote.dto.PayeesResponse
import com.receiptscanner.data.remote.dto.TransactionDto
import com.receiptscanner.data.remote.dto.TransactionsData
import com.receiptscanner.data.remote.dto.TransactionsResponse
import com.receiptscanner.domain.model.ClearedStatus
import com.receiptscanner.domain.model.Transaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class YnabRepositoryImplTest {

    private val api: YnabApi = mockk()
    private val tokenProvider: TokenProvider = mockk()
    private val payeeCacheDao: PayeeCacheDao = mockk(relaxUnitFun = true)
    private val categoryCacheDao: CategoryCacheDao = mockk(relaxUnitFun = true)
    private val accountCacheDao: AccountCacheDao = mockk(relaxUnitFun = true)
    private val syncMetadataDao: SyncMetadataDao = mockk(relaxUnitFun = true)

    private lateinit var repository: YnabRepositoryImpl

    @BeforeEach
    fun setup() {
        coEvery { tokenProvider.getToken() } returns "test-token"
        repository = YnabRepositoryImpl(
            api = api,
            tokenProvider = tokenProvider,
            payeeCacheDao = payeeCacheDao,
            categoryCacheDao = categoryCacheDao,
            accountCacheDao = accountCacheDao,
            syncMetadataDao = syncMetadataDao,
        )
    }

    @Nested
    inner class GetBudgets {
        @Test
        fun `returns mapped budgets from API`() = runTest {
            coEvery { api.getBudgets("Bearer test-token") } returns BudgetsResponse(
                data = BudgetsData(
                    budgets = listOf(
                        BudgetDto(id = "b1", name = "My Budget", lastModifiedOn = null),
                    ),
                ),
            )

            val result = repository.getBudgets()

            assertTrue(result.isSuccess)
            val budgets = result.getOrThrow()
            assertEquals(1, budgets.size)
            assertEquals("b1", budgets[0].id)
            assertEquals("My Budget", budgets[0].name)
        }

        @Test
        fun `returns failure when API throws`() = runTest {
            coEvery { api.getBudgets(any()) } throws RuntimeException("Network error")

            val result = repository.getBudgets()

            assertTrue(result.isFailure)
            assertEquals("Network error", result.exceptionOrNull()?.message)
        }

        @Test
        fun `returns failure when no token set`() = runTest {
            coEvery { tokenProvider.getToken() } returns null

            val result = repository.getBudgets()

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }
    }

    @Nested
    inner class SyncPayees {
        @Test
        fun `fetches payees and upserts into cache`() = runTest {
            coEvery { syncMetadataDao.getValueByKey("payees_b1") } returns null
            coEvery { api.getPayees("Bearer test-token", "b1", null) } returns PayeesResponse(
                data = PayeesData(
                    payees = listOf(
                        PayeeDto(id = "p1", name = "Walmart", deleted = false),
                        PayeeDto(id = "p2", name = "Target", deleted = false),
                    ),
                    serverKnowledge = 42,
                ),
            )

            val result = repository.syncPayees("b1")

            assertTrue(result.isSuccess)

            val payeeSlot = slot<List<PayeeCacheEntity>>()
            coVerify { payeeCacheDao.upsertAll(capture(payeeSlot)) }
            assertEquals(2, payeeSlot.captured.size)
            assertEquals("Walmart", payeeSlot.captured[0].name)

            val metadataSlot = slot<SyncMetadataEntity>()
            coVerify { syncMetadataDao.upsert(capture(metadataSlot)) }
            assertEquals("payees_b1", metadataSlot.captured.key)
            assertEquals(42L, metadataSlot.captured.value)
        }

        @Test
        fun `passes last knowledge for delta sync`() = runTest {
            coEvery { syncMetadataDao.getValueByKey("payees_b1") } returns 30L
            coEvery { api.getPayees("Bearer test-token", "b1", 30L) } returns PayeesResponse(
                data = PayeesData(
                    payees = listOf(PayeeDto(id = "p3", name = "New Store", deleted = false)),
                    serverKnowledge = 50,
                ),
            )

            repository.syncPayees("b1")

            coVerify { api.getPayees("Bearer test-token", "b1", 30L) }
        }
    }

    @Nested
    inner class SyncCategories {
        @Test
        fun `fetches categories and flattens into cache entities`() = runTest {
            coEvery { syncMetadataDao.getValueByKey("categories_b1") } returns null
            coEvery { api.getCategories("Bearer test-token", "b1", null) } returns CategoriesResponse(
                data = CategoriesData(
                    categoryGroups = listOf(
                        CategoryGroupDto(
                            id = "grp1",
                            name = "Bills",
                            hidden = false,
                            deleted = false,
                            categories = listOf(
                                CategoryDto(
                                    id = "cat1", categoryGroupId = "grp1", name = "Rent",
                                    hidden = false, budgeted = 1200000, balance = 1200000, deleted = false,
                                ),
                                CategoryDto(
                                    id = "cat2", categoryGroupId = "grp1", name = "Electric",
                                    hidden = false, budgeted = 100000, balance = 50000, deleted = false,
                                ),
                            ),
                        ),
                    ),
                    serverKnowledge = 55,
                ),
            )

            val result = repository.syncCategories("b1")

            assertTrue(result.isSuccess)

            val slot = slot<List<CategoryCacheEntity>>()
            coVerify { categoryCacheDao.upsertAll(capture(slot)) }
            assertEquals(2, slot.captured.size)
            assertEquals("Rent", slot.captured[0].name)
            assertEquals("Bills", slot.captured[0].categoryGroupName)
            assertEquals("grp1", slot.captured[0].categoryGroupId)
        }
    }

    @Nested
    inner class SyncAccounts {
        @Test
        fun `fetches accounts and upserts into cache`() = runTest {
            coEvery { syncMetadataDao.getValueByKey("accounts_b1") } returns null
            coEvery { api.getAccounts("Bearer test-token", "b1", null) } returns AccountsResponse(
                data = AccountsData(
                    accounts = listOf(
                        AccountDto(
                            id = "a1", name = "Checking", type = "checking",
                            onBudget = true, closed = false, balance = 500000,
                            note = "main", deleted = false,
                        ),
                    ),
                    serverKnowledge = 10,
                ),
            )

            val result = repository.syncAccounts("b1")

            assertTrue(result.isSuccess)

            val slot = slot<List<AccountCacheEntity>>()
            coVerify { accountCacheDao.upsertAll(capture(slot)) }
            assertEquals(1, slot.captured.size)
            assertEquals("Checking", slot.captured[0].name)
            assertEquals(500000L, slot.captured[0].balance)
        }

        @Test
        fun `passes last knowledge for delta sync`() = runTest {
            coEvery { syncMetadataDao.getValueByKey("accounts_b1") } returns 5L
            coEvery { api.getAccounts("Bearer test-token", "b1", 5L) } returns AccountsResponse(
                data = AccountsData(
                    accounts = emptyList(),
                    serverKnowledge = 10,
                ),
            )

            repository.syncAccounts("b1")

            coVerify { api.getAccounts("Bearer test-token", "b1", 5L) }
        }
    }

    @Nested
    inner class GetPayees {
        @Test
        fun `returns cached payees when available`() = runTest {
            coEvery { payeeCacheDao.getAllNonDeleted() } returns listOf(
                PayeeCacheEntity(id = "p1", name = "Walmart", deleted = false),
            )

            val result = repository.getPayees("b1")

            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrThrow().size)
            assertEquals("Walmart", result.getOrThrow()[0].name)
            coVerify(exactly = 0) { api.getPayees(any(), any(), any()) }
        }

        @Test
        fun `syncs when cache is empty then returns cached`() = runTest {
            // First call returns empty, after sync returns data
            coEvery { payeeCacheDao.getAllNonDeleted() } returnsMany listOf(
                emptyList(),
                listOf(PayeeCacheEntity(id = "p1", name = "Walmart", deleted = false)),
            )
            coEvery { syncMetadataDao.getValueByKey("payees_b1") } returns null
            coEvery { api.getPayees("Bearer test-token", "b1", null) } returns PayeesResponse(
                data = PayeesData(
                    payees = listOf(PayeeDto(id = "p1", name = "Walmart", deleted = false)),
                    serverKnowledge = 1,
                ),
            )

            val result = repository.getPayees("b1")

            assertTrue(result.isSuccess)
            coVerify { api.getPayees("Bearer test-token", "b1", null) }
        }
    }

    @Nested
    inner class GetTransactions {
        @Test
        fun `returns mapped transactions from API, filtering deleted`() = runTest {
            coEvery { api.getTransactions("Bearer test-token", "b1", null, null) } returns TransactionsResponse(
                data = TransactionsData(
                    transactions = listOf(
                        TransactionDto(
                            id = "tx1", date = "2024-03-15", amount = -50000,
                            memo = "Groceries", cleared = "cleared", approved = true,
                            accountId = "a1", accountName = "Checking",
                            payeeId = "p1", payeeName = "Walmart",
                            categoryId = "c1", categoryName = "Food",
                            deleted = false,
                        ),
                        TransactionDto(
                            id = "tx2", date = "2024-03-14", amount = -10000,
                            memo = null, cleared = "uncleared", approved = false,
                            accountId = "a1", accountName = "Checking",
                            payeeId = null, payeeName = null,
                            categoryId = null, categoryName = null,
                            deleted = true,
                        ),
                    ),
                    serverKnowledge = 99,
                ),
            )

            val result = repository.getTransactions("b1")

            assertTrue(result.isSuccess)
            val transactions = result.getOrThrow()
            assertEquals(1, transactions.size)
            assertEquals("tx1", transactions[0].id)
            assertEquals(LocalDate.of(2024, 3, 15), transactions[0].date)
            assertEquals(-50000L, transactions[0].amount)
            assertEquals("Walmart", transactions[0].payeeName)
            assertEquals(ClearedStatus.CLEARED, transactions[0].cleared)
        }

        @Test
        fun `passes sinceDate parameter`() = runTest {
            coEvery { api.getTransactions("Bearer test-token", "b1", "2024-01-01", null) } returns TransactionsResponse(
                data = TransactionsData(
                    transactions = emptyList(),
                    serverKnowledge = 1,
                ),
            )

            repository.getTransactions("b1", "2024-01-01")

            coVerify { api.getTransactions("Bearer test-token", "b1", "2024-01-01", null) }
        }
    }

    @Nested
    inner class CreateTransaction {
        @Test
        fun `creates transaction and returns id`() = runTest {
            coEvery { api.createTransaction("Bearer test-token", "b1", any()) } returns CreateTransactionResponse(
                data = CreateTransactionData(
                    transactionIds = listOf("new-tx-1"),
                    transaction = null,
                ),
            )

            val tx = Transaction(
                accountId = "a1",
                date = LocalDate.of(2024, 3, 15),
                amount = -25000,
                payeeName = "Starbucks",
                categoryId = "c1",
                memo = "Coffee",
                cleared = ClearedStatus.UNCLEARED,
                approved = true,
            )

            val result = repository.createTransaction("b1", tx)

            assertTrue(result.isSuccess)
            assertEquals("new-tx-1", result.getOrThrow())
        }

        @Test
        fun `returns failure on API error`() = runTest {
            coEvery { api.createTransaction(any(), any(), any()) } throws RuntimeException("Server error")

            val tx = Transaction(
                accountId = "a1",
                date = LocalDate.of(2024, 3, 15),
                amount = -25000,
                payeeName = "Store",
            )

            val result = repository.createTransaction("b1", tx)

            assertTrue(result.isFailure)
        }
    }
}
