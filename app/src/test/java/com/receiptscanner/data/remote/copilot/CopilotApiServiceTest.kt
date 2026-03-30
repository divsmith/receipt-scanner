package com.receiptscanner.data.remote.copilot

import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CopilotApiServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: CopilotApiService
    private val moshi = Moshi.Builder().build()
    private var storedToken: String? = "ghp_test_token_12345"

    private val fakeTokenProvider = object : CopilotTokenProvider {
        override fun getToken(): String? = storedToken
        override fun setToken(token: String?) { storedToken = token }
    }

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        storedToken = "ghp_test_token_12345"
        service = CopilotApiService(
            copilotTokenProvider = fakeTokenProvider,
            moshi = moshi,
            apiUrl = mockWebServer.url("/chat/completions").toString(),
        )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `successful extraction parses all fields`() = runTest {
        val responseJson = """
            {
                "choices": [{
                    "message": {
                        "content": "{\"store_name\": \"TRADER JOE'S\", \"total_amount\": \"42.99\", \"date\": \"2024-11-15\", \"card_last_four\": \"1234\", \"confidence\": 0.95}"
                    }
                }]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = service.extractReceiptData("dGVzdA==")
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("TRADER JOE'S", data.storeName)
        assertEquals(42990L, data.totalAmount) // $42.99 = 42990 milliunits
        assertEquals("2024-11-15", data.date.toString())
        assertEquals("1234", data.cardLastFour)
        assertEquals(0.95f, data.totalConfidence)
    }

    @Test
    fun `extraction with null fields`() = runTest {
        val responseJson = """
            {
                "choices": [{
                    "message": {
                        "content": "{\"store_name\": null, \"total_amount\": \"15.00\", \"date\": null, \"card_last_four\": null, \"confidence\": 0.7}"
                    }
                }]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = service.extractReceiptData("dGVzdA==")
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertNull(data.storeName)
        assertEquals(15000L, data.totalAmount)
        assertNull(data.date)
        assertNull(data.cardLastFour)
    }

    @Test
    fun `extraction strips markdown code fences`() = runTest {
        val responseJson = """
            {
                "choices": [{
                    "message": {
                        "content": "```json\n{\"store_name\": \"Target\", \"total_amount\": \"9.99\", \"date\": \"2024-01-01\", \"card_last_four\": null, \"confidence\": 0.9}\n```"
                    }
                }]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = service.extractReceiptData("dGVzdA==")
        assertTrue(result.isSuccess)
        assertEquals("Target", result.getOrThrow().storeName)
    }

    @Test
    fun `401 returns auth error`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val result = service.extractReceiptData("dGVzdA==")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as CopilotApiException
        assertEquals(401, exception.httpCode)
        assertTrue(exception.message!!.contains("invalid or expired"))
    }

    @Test
    fun `429 returns rate limit error`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(429).setBody("Rate limited"))

        val result = service.extractReceiptData("dGVzdA==")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as CopilotApiException
        assertEquals(429, exception.httpCode)
        assertTrue(exception.message!!.contains("Rate limit"))
    }

    @Test
    fun `missing token returns configuration error`() = runTest {
        storedToken = null

        val result = service.extractReceiptData("dGVzdA==")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as CopilotApiException
        assertTrue(exception.message!!.contains("not configured"))
    }

    @Test
    fun `request sends correct authorization header`() = runTest {
        val responseJson = """
            {
                "choices": [{
                    "message": {
                        "content": "{\"store_name\": null, \"total_amount\": null, \"date\": null, \"card_last_four\": null, \"confidence\": 0.0}"
                    }
                }]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        service.extractReceiptData("dGVzdA==")

        val request = mockWebServer.takeRequest()
        assertEquals("Bearer ghp_test_token_12345", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
    }

    @Test
    fun `request sends correct model in body`() = runTest {
        val responseJson = """
            {
                "choices": [{
                    "message": {
                        "content": "{\"store_name\": null, \"total_amount\": null, \"date\": null, \"card_last_four\": null, \"confidence\": 0.0}"
                    }
                }]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        service.extractReceiptData("dGVzdA==")

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"model\":\"gpt-5-mini\""))
    }

    @Test
    fun `malformed model response returns parse error`() = runTest {
        val responseJson = """
            {
                "choices": [{
                    "message": {
                        "content": "I can see a receipt but I'm not sure what to extract."
                    }
                }]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = service.extractReceiptData("dGVzdA==")
        assertTrue(result.isFailure)
    }

    @Test
    fun `empty choices returns error`() = runTest {
        val responseJson = """{"choices": []}"""

        mockWebServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = service.extractReceiptData("dGVzdA==")
        assertTrue(result.isFailure)
    }

    @Test
    fun `server error returns generic error`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = service.extractReceiptData("dGVzdA==")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as CopilotApiException
        assertEquals(500, exception.httpCode)
    }
}
