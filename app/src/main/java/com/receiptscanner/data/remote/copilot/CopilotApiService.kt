package com.receiptscanner.data.remote.copilot

import com.receiptscanner.domain.model.ExtractedReceiptData
import com.receiptscanner.domain.util.MilliunitConverter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CopilotApiService @Inject constructor(
    private val copilotTokenProvider: CopilotTokenProvider,
    private val moshi: Moshi,
) {
    // Visible for testing: allows overriding the API URL in tests
    internal var apiUrl: String = API_URL

    constructor(
        copilotTokenProvider: CopilotTokenProvider,
        moshi: Moshi,
        apiUrl: String,
    ) : this(copilotTokenProvider, moshi) {
        this.apiUrl = apiUrl
    }
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                redactHeader("Authorization")
                level = HttpLoggingInterceptor.Level.BASIC
            },
        )
        .build()

    suspend fun extractReceiptData(imageBase64: String): Result<ExtractedReceiptData> =
        withContext(Dispatchers.IO) {
            try {
                val token = copilotTokenProvider.getToken()
                    ?: return@withContext Result.failure(
                        CopilotApiException("GitHub token not configured. Set it in Settings.", 0)
                    )

                val requestJson = buildRequestJson(imageBase64)
                val request = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .post(requestJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.awaitCall(request)
                response.use { parseResponse(it) }
            } catch (e: CopilotApiException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(
                    CopilotApiException("Network error. Check your connection or switch to Local OCR.", 0, e)
                )
            } catch (e: Exception) {
                Result.failure(
                    CopilotApiException("Unexpected error: ${e.message}", 0, e)
                )
            }
        }

    private fun buildRequestJson(imageBase64: String): String {
        val buffer = okio.Buffer()
        val writer = com.squareup.moshi.JsonWriter.of(buffer)
        writer.beginObject()
        writer.name("model").value(MODEL)
        writer.name("temperature").value(0.0)
        writer.name("max_tokens").value(1024)
        writer.name("messages")
        writer.beginArray()

        // System message
        writer.beginObject()
        writer.name("role").value("system")
        writer.name("content").value(SYSTEM_PROMPT)
        writer.endObject()

        // User message with image
        writer.beginObject()
        writer.name("role").value("user")
        writer.name("content")
        writer.beginArray()

        // Text part
        writer.beginObject()
        writer.name("type").value("text")
        writer.name("text").value("Extract the receipt data from this image.")
        writer.endObject()

        // Image part
        writer.beginObject()
        writer.name("type").value("image_url")
        writer.name("image_url")
        writer.beginObject()
        writer.name("url").value("data:image/jpeg;base64,$imageBase64")
        writer.endObject()
        writer.endObject()

        writer.endArray()
        writer.endObject()

        writer.endArray()
        writer.endObject()
        writer.close()
        return buffer.readUtf8()
    }

    private fun parseResponse(response: Response): Result<ExtractedReceiptData> {
        val body = response.body?.string()
            ?: return Result.failure(CopilotApiException("Empty response from server", response.code))

        if (!response.isSuccessful) {
            val message = when (response.code) {
                401, 403 -> "GitHub token is invalid or expired. Update it in Settings."
                429 -> "Rate limit exceeded. Try again in a moment."
                else -> "Server error (${response.code}). Try again later."
            }
            return Result.failure(CopilotApiException(message, response.code))
        }

        return try {
            val completionAdapter = moshi.adapter(ChatCompletionResponse::class.java)
            val completion = completionAdapter.fromJson(body)
                ?: return Result.failure(CopilotApiException("Could not parse API response", response.code))

            val content = completion.choices.firstOrNull()?.message?.content
                ?: return Result.failure(CopilotApiException("No content in API response", response.code))

            // Strip markdown code fences if the model wraps the JSON
            val cleanedContent = content.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```")
                .trim()

            val extractionAdapter = moshi.adapter(ReceiptExtractionResponse::class.java)
            val extraction = extractionAdapter.fromJson(cleanedContent)
                ?: return Result.failure(CopilotApiException("Could not parse receipt data from model response", response.code))

            val totalMilliunits = extraction.totalAmount?.let { amountStr ->
                try {
                    MilliunitConverter.dollarsToMilliunits(BigDecimal(amountStr))
                } catch (_: NumberFormatException) {
                    null
                }
            }

            val date = extraction.date?.let { dateStr ->
                try {
                    LocalDate.parse(dateStr)
                } catch (_: DateTimeParseException) {
                    null
                }
            }

            Result.success(
                ExtractedReceiptData(
                    storeName = extraction.storeName,
                    totalAmount = totalMilliunits,
                    totalConfidence = (extraction.confidence ?: 0.0).toFloat(),
                    date = date,
                    cardLastFour = extraction.cardLastFour,
                    rawText = content,
                ),
            )
        } catch (e: Exception) {
            Result.failure(
                CopilotApiException("Failed to parse model response: ${e.message}", response.code, e)
            )
        }
    }

    companion object {
        private const val API_URL = "https://models.inference.ai.azure.com/chat/completions"
        private const val MODEL = "gpt-5-mini"

        private val SYSTEM_PROMPT = """
            You are a receipt data extraction system. Analyze the receipt image and extract structured data.
            Return ONLY a valid JSON object with these exact fields:

            {"store_name": "string or null", "total_amount": "string or null", "date": "string or null", "card_last_four": "string or null", "confidence": number}

            Rules:
            - store_name: The merchant or store name as printed on the receipt
            - total_amount: The final amount paid as a decimal string (e.g. "12.99"), after tax, tips, and discounts
            - date: The transaction date in YYYY-MM-DD format
            - card_last_four: The last 4 digits of the payment card if visible on the receipt
            - confidence: Your confidence in the total_amount extraction, from 0.0 to 1.0
            - Set any field to null if it cannot be reliably determined from the image
            - Return ONLY the JSON object, with no markdown formatting, no code fences, and no explanation
        """.trimIndent()
    }
}

class CopilotApiException(
    message: String,
    val httpCode: Int,
    cause: Throwable? = null,
) : Exception(message, cause)

private suspend fun OkHttpClient.awaitCall(request: Request): Response =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response)
                } else {
                    response.close()
                }
            }
        })
    }
