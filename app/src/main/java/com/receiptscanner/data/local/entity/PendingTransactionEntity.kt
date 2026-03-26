package com.receiptscanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_transactions")
data class PendingTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "account_id") val accountId: String,
    val date: String,
    val amount: Long,
    @ColumnInfo(name = "payee_name") val payeeName: String?,
    @ColumnInfo(name = "payee_id") val payeeId: String?,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    val memo: String?,
    val cleared: String,
    val approved: Boolean,
    val status: String,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
