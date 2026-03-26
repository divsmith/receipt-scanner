package com.receiptscanner.domain.model

data class PayeeMatchResult(
    val payee: Payee,
    val confidence: Double,  // 0.0 to 1.0
)

data class CategorySuggestion(
    val category: Category,
    val frequency: Int,
    val confidence: Double,
)

data class AccountMatchResult(
    val account: Account,
    val matchedByCardNumber: Boolean,
)
