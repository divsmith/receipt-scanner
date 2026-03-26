package com.receiptscanner.data.remote

import com.receiptscanner.data.remote.dto.BudgetsResponse
import com.receiptscanner.data.remote.dto.AccountsResponse
import com.receiptscanner.data.remote.dto.CategoriesResponse
import com.receiptscanner.data.remote.dto.CreateTransactionRequest
import com.receiptscanner.data.remote.dto.CreateTransactionResponse
import com.receiptscanner.data.remote.dto.PayeesResponse
import com.receiptscanner.data.remote.dto.SaveTransactionDto
import com.receiptscanner.data.remote.dto.TransactionsResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class YnabApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: YnabApi

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val client = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder().build()

        api = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/v1/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YnabApi::class.java)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // --- getBudgets ---

    @Test
    fun `getBudgets returns parsed budgets`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "data": {
                            "budgets": [
                                { "id": "budget-1", "name": "My Budget", "last_modified_on": "2024-01-01T00:00:00+00:00" },
                                { "id": "budget-2", "name": "Shared Budget", "last_modified_on": null }
                            ]
                        }
                    }
                    """,
                ),
        )

        val response = api.getBudgets("Bearer test-token")

        assertEquals(2, response.data.budgets.size)
        assertEquals("budget-1", response.data.budgets[0].id)
        assertEquals("My Budget", response.data.budgets[0].name)
        assertEquals("budget-2", response.data.budgets[1].id)

        val request = mockWebServer.takeRequest()
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertEquals("/v1/budgets", request.path)
    }

    // --- getAccounts ---

    @Test
    fun `getAccounts returns parsed accounts with server knowledge`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "data": {
                            "accounts": [
                                {
                                    "id": "acc-1", "name": "Checking", "type": "checking",
                                    "on_budget": true, "closed": false, "balance": 500000,
                                    "note": "main account", "deleted": false
                                }
                            ],
                            "server_knowledge": 42
                        }
                    }
                    """,
                ),
        )

        val response = api.getAccounts("Bearer tk", "budget-1")

        assertEquals(1, response.data.accounts.size)
        assertEquals("acc-1", response.data.accounts[0].id)
        assertEquals("Checking", response.data.accounts[0].name)
        assertEquals(500000L, response.data.accounts[0].balance)
        assertEquals(42L, response.data.serverKnowledge)

        val request = mockWebServer.takeRequest()
        assertEquals("/v1/budgets/budget-1/accounts", request.path)
    }

    @Test
    fun `getAccounts passes delta knowledge parameter`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "data": {
                            "accounts": [],
                            "server_knowledge": 100
                        }
                    }
                    """,
                ),
        )

        api.getAccounts("Bearer tk", "b1", lastKnowledge = 42)

        val request = mockWebServer.takeRequest()
        assertTrue(request.path!!.contains("last_knowledge_of_server=42"))
    }

    // --- getCategories ---

    @Test
    fun `getCategories returns category groups with nested categories`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "data": {
                            "category_groups": [
                                {
                                    "id": "group-1", "name": "Bills", "hidden": false, "deleted": false,
                                    "categories": [
                                        {
                                            "id": "cat-1", "category_group_id": "group-1", "name": "Rent",
                                            "hidden": false, "budgeted": 1200000, "balance": 1200000, "deleted": false
                                        }
                                    ]
                                }
                            ],
                            "server_knowledge": 55
                        }
                    }
                    """,
                ),
        )

        val response = api.getCategories("Bearer tk", "b1")

        assertEquals(1, response.data.categoryGroups.size)
        assertEquals("Bills", response.data.categoryGroups[0].name)
        assertEquals(1, response.data.categoryGroups[0].categories.size)
        assertEquals("Rent", response.data.categoryGroups[0].categories[0].name)
        assertEquals(1200000L, response.data.categoryGroups[0].categories[0].budgeted)
        assertEquals(55L, response.data.serverKnowledge)
    }

    // --- getPayees ---

    @Test
    fun `getPayees returns parsed payees`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "data": {
                            "payees": [
                                { "id": "p-1", "name": "Walmart", "deleted": false },
                                { "id": "p-2", "name": "Old Store", "deleted": true }
                            ],
                            "server_knowledge": 10
                        }
                    }
                    """,
                ),
        )

        val response = api.getPayees("Bearer tk", "b1")

        assertEquals(2, response.data.payees.size)
        assertEquals("Walmart", response.data.payees[0].name)
        assertTrue(response.data.payees[1].deleted)
        assertEquals(10L, response.data.serverKnowledge)
    }

    @Test
    fun `getPayees passes delta knowledge parameter`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"data": {"payees": [], "server_knowledge": 20}}""",
                ),
        )

        api.getPayees("Bearer tk", "b1", lastKnowledge = 10)

        val request = mockWebServer.takeRequest()
        assertTrue(request.path!!.contains("last_knowledge_of_server=10"))
    }

    // --- getTransactions ---

    @Test
    fun `getTransactions returns parsed transactions`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "data": {
                            "transactions": [
                                {
                                    "id": "tx-1", "date": "2024-03-15", "amount": -50000,
                                    "memo": "Groceries", "cleared": "cleared", "approved": true,
                                    "account_id": "acc-1", "account_name": "Checking",
                                    "payee_id": "p-1", "payee_name": "Walmart",
                                    "category_id": "cat-1", "category_name": "Food",
                                    "deleted": false
                                }
                            ],
                            "server_knowledge": 99
                        }
                    }
                    """,
                ),
        )

        val response = api.getTransactions("Bearer tk", "b1")

        assertEquals(1, response.data.transactions.size)
        val tx = response.data.transactions[0]
        assertEquals("tx-1", tx.id)
        assertEquals(-50000L, tx.amount)
        assertEquals("Walmart", tx.payeeName)
        assertEquals("cleared", tx.cleared)
    }

    @Test
    fun `getTransactions passes sinceDate filter`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"data": {"transactions": [], "server_knowledge": 1}}""",
                ),
        )

        api.getTransactions("Bearer tk", "b1", sinceDate = "2024-01-01")

        val request = mockWebServer.takeRequest()
        assertTrue(request.path!!.contains("since_date=2024-01-01"))
    }

    // --- createTransaction ---

    @Test
    fun `createTransaction sends correct request body and returns id`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "data": {
                            "transaction_ids": ["new-tx-1"],
                            "transaction": {
                                "id": "new-tx-1", "date": "2024-03-15", "amount": -25000,
                                "memo": "Coffee", "cleared": "uncleared", "approved": true,
                                "account_id": "acc-1", "account_name": "Checking",
                                "payee_id": null, "payee_name": "Starbucks",
                                "category_id": "cat-2", "category_name": "Dining Out",
                                "deleted": false
                            }
                        }
                    }
                    """,
                ),
        )

        val request = CreateTransactionRequest(
            transaction = SaveTransactionDto(
                accountId = "acc-1",
                date = "2024-03-15",
                amount = -25000,
                payeeName = "Starbucks",
                categoryId = "cat-2",
                memo = "Coffee",
            ),
        )

        val response = api.createTransaction("Bearer tk", "b1", request)

        assertEquals("new-tx-1", response.data.transactionIds[0])
        assertNotNull(response.data.transaction)

        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.contains("/v1/budgets/b1/transactions"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"account_id\":\"acc-1\""))
        assertTrue(body.contains("\"amount\":-25000"))
    }

    // --- Error responses ---

    @Test
    fun `401 Unauthorized throws HttpException`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": {"id": "401", "name": "unauthorized", "detail": "unauthorized"}}"""),
        )

        val exception = assertThrows<HttpException> {
            api.getBudgets("Bearer invalid-token")
        }
        assertEquals(401, exception.code())
    }

    @Test
    fun `429 Rate Limit throws HttpException`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error": {"id": "429", "name": "too_many_requests", "detail": "Rate limit exceeded"}}"""),
        )

        val exception = assertThrows<HttpException> {
            api.getBudgets("Bearer test-token")
        }
        assertEquals(429, exception.code())
    }

    @Test
    fun `500 Server Error throws HttpException`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error": {"id": "500", "name": "internal_server_error", "detail": "Something went wrong"}}"""),
        )

        val exception = assertThrows<HttpException> {
            api.getPayees("Bearer tk", "b1")
        }
        assertEquals(500, exception.code())
    }
}
