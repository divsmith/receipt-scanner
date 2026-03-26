package com.receiptscanner.domain.model

data class Account(
    val id: String,
    val name: String,
    val type: String,
    val onBudget: Boolean,
    val closed: Boolean,
    val balance: Long,
    val note: String?,
)
