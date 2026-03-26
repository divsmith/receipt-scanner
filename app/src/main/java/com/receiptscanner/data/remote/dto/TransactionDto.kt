package com.receiptscanner.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TransactionDto(
    @Json(name = "id") val id: String,
    @Json(name = "date") val date: String,
    @Json(name = "amount") val amount: Long,
    @Json(name = "memo") val memo: String?,
    @Json(name = "cleared") val cleared: String,
    @Json(name = "approved") val approved: Boolean,
    @Json(name = "account_id") val accountId: String,
    @Json(name = "account_name") val accountName: String?,
    @Json(name = "payee_id") val payeeId: String?,
    @Json(name = "payee_name") val payeeName: String?,
    @Json(name = "category_id") val categoryId: String?,
    @Json(name = "category_name") val categoryName: String?,
    @Json(name = "deleted") val deleted: Boolean,
)

@JsonClass(generateAdapter = true)
data class SaveTransactionDto(
    @Json(name = "account_id") val accountId: String,
    @Json(name = "date") val date: String,
    @Json(name = "amount") val amount: Long,
    @Json(name = "payee_name") val payeeName: String? = null,
    @Json(name = "payee_id") val payeeId: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "memo") val memo: String? = null,
    @Json(name = "cleared") val cleared: String = "uncleared",
    @Json(name = "approved") val approved: Boolean = true,
)
