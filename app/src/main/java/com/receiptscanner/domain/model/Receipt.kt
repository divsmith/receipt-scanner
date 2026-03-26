package com.receiptscanner.domain.model

import java.time.LocalDate

data class Receipt(
    val id: String,
    val imagePath: String,
    val extractedData: ExtractedReceiptData?,
    val createdAt: Long = System.currentTimeMillis(),
)

data class ExtractedReceiptData(
    val storeName: String?,
    val totalAmount: Long?,  // in milliunits
    val date: LocalDate?,
    val cardLastFour: String?,
    val rawText: String,
)
