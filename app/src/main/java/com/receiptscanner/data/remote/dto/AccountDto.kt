package com.receiptscanner.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AccountDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "type") val type: String,
    @Json(name = "on_budget") val onBudget: Boolean,
    @Json(name = "closed") val closed: Boolean,
    @Json(name = "balance") val balance: Long,
    @Json(name = "note") val note: String?,
    @Json(name = "deleted") val deleted: Boolean,
)
