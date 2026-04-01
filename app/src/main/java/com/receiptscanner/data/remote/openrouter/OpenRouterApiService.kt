package com.receiptscanner.data.remote.openrouter

import com.receiptscanner.domain.model.ExtractedReceiptData
import com.receiptscanner.domain.model.OpenRouterModel
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
class OpenRouterApiService @Inject constructor(
    private val tokenProvider: OpenRouterTokenProvider,
    private val moshi: Moshi,
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                redactHeader("Authorization")
                level = HttpLoggingInterceptor.Level.BASIC
            },
        )
        .build()

    suspend fun getModels(): Result<List<OpenRouterModel>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/models")
                .get()
                .build()

            val response = client.awaitCall(request)
            response.use { parseModelsResponse(it) }
        } catch (e: IOException) {
            Result.failure(OpenRouterApiException("Network error fetching models: ${e.message}", 0, e))
        } catch (e: Exception) {
            Result.failure(OpenRouterApiException("Error fetching models: ${e.message}", 0, e))
        }
    }

    suspend fun extractReceiptData(imageBase64: String, modelId: String): Result<ExtractedReceiptData> =
        withContext(Dispatchers.IO) {
            try {
                val token = tokenProvider.getToken()
                    ?: return@withContext Result.failure(
                        OpenRouterApiException("OpenRouter API key not configured. Set it in Settings.", 0)
                    )

                val requestJson = buildExtractionRequest(imageBase64, modelId)
                val request = Request.Builder()
                    .url("$BASE_URL/chat/completions")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/receipt-scanner")
                    .addHeader("X-OpenRouter-Title", "Receipt Scanner")
                    .post(requestJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.awaitCall(request)
                response.use { parseExtractionResponse(it) }
            } catch (e: OpenRouterApiException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(
                    OpenRouterApiException("Network error. Check your connection or switch to Local OCR.", 0, e)
                )
            } catch (e: Exception) {
                Result.failure(
                    OpenRouterApiException("Unexpected error: ${e.message}", 0, e)
                )
            }
        }

    private fun parseModelsResponse(response: Response): Result<List<OpenRouterModel>> {
        val body = response.body?.string()
            ?: return Result.failure(OpenRouterApiException("Empty response from models API", response.code))

        if (!response.isSuccessful) {
            return Result.failure(OpenRouterApiException("Failed to fetch models (${response.code})", response.code))
        }

        return try {
            val adapter = moshi.adapter(ModelListResponse::class.java)
            val listResponse = adapter.fromJson(body)
                ?: return Result.failure(OpenRouterApiException("Could not parse models response", response.code))

            val freeVisionModels = listResponse.data
                .filter { model ->
                    model.pricing.prompt == "0" &&
                        model.architecture.inputModalities.contains("image")
                }
                .map { OpenRouterModel(id = it.id, name = it.name) }
                .sortedBy { it.name }

            Result.success(freeVisionModels)
        } catch (e: Exception) {
            Result.failure(OpenRouterApiException("Failed to parse models: ${e.message}", response.code, e))
        }
    }

    private fun parseExtractionResponse(response: Response): Result<ExtractedReceiptData> {
        val body = response.body?.string()
            ?: return Result.failure(OpenRouterApiException("Empty response from server", response.code))

        if (!response.isSuccessful) {
            val message = when (response.code) {
                401, 403 -> "OpenRouter API key is invalid or expired. Update it in Settings."
                402 -> "OpenRouter account has insufficient credits."
                429 -> "Rate limit exceeded. Try again in a moment."
                else -> "Server error (${response.code}). Try again later."
            }
            return Result.failure(OpenRouterApiException(message, response.code))
        }

        return try {
            val completionAdapter = moshi.adapter(OpenRouterCompletionResponse::class.java)
            val completion = completionAdapter.fromJson(body)
                ?: return Result.failure(OpenRouterApiException("Could not parse API response", response.code))

            val content = completion.choices.firstOrNull()?.message?.content
                ?: return Result.failure(OpenRouterApiException("No content in API response", response.code))

            val cleanedContent = content.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```")
                .trim()

            val extractionAdapter = moshi.adapter(OpenRouterReceiptExtraction::class.java)
            val extraction = extractionAdapter.fromJson(cleanedContent)
                ?: return Result.failure(OpenRouterApiException("Could not parse receipt data from model response", response.code))

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
                OpenRouterApiException("Failed to parse model response: ${e.message}", response.code, e)
            )
        }
    }

    private fun buildExtractionRequest(imageBase64: String, modelId: String): String {
        val buffer = okio.Buffer()
        val writer = com.squareup.moshi.JsonWriter.of(buffer)
        writer.beginObject()
        writer.name("model").value(modelId)
        writer.name("temperature").value(0.0)
        writer.name("max_tokens").value(1024)
        writer.name("messages")
        writer.beginArray()

        writer.beginObject()
        writer.name("role").value("system")
        writer.name("content").value(SYSTEM_PROMPT)
        writer.endObject()

        writer.beginObject()
        writer.name("role").value("user")
        writer.name("content")
        writer.beginArray()

        writer.beginObject()
        writer.name("type").value("text")
        writer.name("text").value("Extract the receipt data from this image.")
        writer.endObject()

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

    companion object {
        private const val BASE_URL = "https://openrouter.ai/api/v1"

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

class OpenRouterApiException(
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
