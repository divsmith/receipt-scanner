package com.receiptscanner.data.remote.openrouter

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// --- Models list response ---

@JsonClass(generateAdapter = true)
data class ModelListResponse(
    val data: List<ModelItem>,
)

@JsonClass(generateAdapter = true)
data class ModelItem(
    val id: String,
    val name: String,
    val pricing: ModelPricing,
    val architecture: ModelArchitecture,
)

@JsonClass(generateAdapter = true)
data class ModelPricing(
    val prompt: String,
    val completion: String?,
    val image: String?,
)

@JsonClass(generateAdapter = true)
data class ModelArchitecture(
    @Json(name = "input_modalities") val inputModalities: List<String>,
    @Json(name = "output_modalities") val outputModalities: List<String>,
)

// --- Completions response ---

@JsonClass(generateAdapter = true)
data class OpenRouterCompletionResponse(
    val choices: List<OpenRouterChoice>,
)

@JsonClass(generateAdapter = true)
data class OpenRouterChoice(
    val message: OpenRouterMessage,
)

@JsonClass(generateAdapter = true)
data class OpenRouterMessage(
    val content: String?,
)

// --- Receipt extraction result (parsed from model's JSON output) ---

@JsonClass(generateAdapter = true)
data class OpenRouterReceiptExtraction(
    @Json(name = "store_name") val storeName: String?,
    @Json(name = "total_amount") val totalAmount: String?,
    val date: String?,
    @Json(name = "card_last_four") val cardLastFour: String?,
    val confidence: Double?,
)
