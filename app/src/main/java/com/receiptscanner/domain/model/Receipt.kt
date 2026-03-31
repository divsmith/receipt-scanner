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
    val totalConfidence: Float = 0f,  // 0.0–1.0 confidence score for the extracted total
    val date: LocalDate?,
    val cardLastFour: String?,
    val rawText: String,
)
