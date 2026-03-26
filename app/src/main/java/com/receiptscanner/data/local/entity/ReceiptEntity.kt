package com.receiptscanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "store_name") val storeName: String?,
    @ColumnInfo(name = "total_amount") val totalAmount: Long?,
    val date: String?,
    @ColumnInfo(name = "card_last_four") val cardLastFour: String?,
    @ColumnInfo(name = "raw_ocr_text") val rawOcrText: String?,
    @ColumnInfo(name = "transaction_id") val transactionId: String?,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
