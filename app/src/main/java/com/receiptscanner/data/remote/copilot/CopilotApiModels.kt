package com.receiptscanner.data.remote.copilot

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// --- Response models (parsed from the GitHub Models API) ---

@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    val choices: List<Choice>,
)

@JsonClass(generateAdapter = true)
data class Choice(
    val message: ResponseMessage,
)

@JsonClass(generateAdapter = true)
data class ResponseMessage(
    val content: String?,
)

// --- Receipt extraction result (parsed from the model's JSON output) ---

@JsonClass(generateAdapter = true)
data class ReceiptExtractionResponse(
    @Json(name = "store_name") val storeName: String?,
    @Json(name = "total_amount") val totalAmount: String?,
    val date: String?,
    @Json(name = "card_last_four") val cardLastFour: String?,
    val confidence: Double?,
)
