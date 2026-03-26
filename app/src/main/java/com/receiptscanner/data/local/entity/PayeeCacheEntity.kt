package com.receiptscanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payee_cache")
data class PayeeCacheEntity(
    @PrimaryKey val id: String,
    val name: String,
    val deleted: Boolean,
)
