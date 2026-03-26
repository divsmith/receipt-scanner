package com.receiptscanner.domain.model

data class CategoryGroup(
    val id: String,
    val name: String,
    val hidden: Boolean,
    val categories: List<Category>,
)

data class Category(
    val id: String,
    val categoryGroupId: String,
    val name: String,
    val hidden: Boolean,
    val budgeted: Long,
    val balance: Long,
)
