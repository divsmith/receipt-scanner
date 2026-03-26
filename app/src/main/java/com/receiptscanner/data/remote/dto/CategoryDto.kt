package com.receiptscanner.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CategoryGroupDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "hidden") val hidden: Boolean,
    @Json(name = "deleted") val deleted: Boolean,
    @Json(name = "categories") val categories: List<CategoryDto>,
)

@JsonClass(generateAdapter = true)
data class CategoryDto(
    @Json(name = "id") val id: String,
    @Json(name = "category_group_id") val categoryGroupId: String,
    @Json(name = "name") val name: String,
    @Json(name = "hidden") val hidden: Boolean,
    @Json(name = "budgeted") val budgeted: Long,
    @Json(name = "balance") val balance: Long,
    @Json(name = "deleted") val deleted: Boolean,
)
