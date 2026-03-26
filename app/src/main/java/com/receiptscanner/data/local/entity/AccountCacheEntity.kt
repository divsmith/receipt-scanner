package com.receiptscanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account_cache")
data class AccountCacheEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    @ColumnInfo(name = "on_budget") val onBudget: Boolean,
    val closed: Boolean,
    val balance: Long,
    val note: String?,
    val deleted: Boolean,
)
