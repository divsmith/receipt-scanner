package com.receiptscanner.domain.model

data class PendingTransaction(
    val id: Long,
    val transaction: Transaction,
    val status: PendingStatus,
    val errorMessage: String?,
    val createdAt: Long,
)

enum class PendingStatus { PENDING, SUBMITTING, FAILED }
