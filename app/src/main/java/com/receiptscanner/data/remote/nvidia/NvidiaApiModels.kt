package com.receiptscanner.data.remote.nvidia

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// --- Completions response ---

@JsonClass(generateAdapter = true)
data class NvidiaCompletionResponse(
    val choices: List<NvidiaChoice>,
)

@JsonClass(generateAdapter = true)
data class NvidiaChoice(
    val message: NvidiaMessage,
)

@JsonClass(generateAdapter = true)
data class NvidiaMessage(
    val content: String?,
)

// --- Receipt extraction result (parsed from model's JSON output) ---

@JsonClass(generateAdapter = true)
data class NvidiaReceiptExtraction(
    @Json(name = "store_name") val storeName: String?,
    @Json(name = "total_amount") val totalAmount: String?,
    val date: String?,
    @Json(name = "card_last_four") val cardLastFour: String?,
    val confidence: Double?,
)
