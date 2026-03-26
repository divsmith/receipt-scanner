package com.receiptscanner.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PayeeDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "deleted") val deleted: Boolean,
)
