package com.receiptscanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_cache")
data class CategoryCacheEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "category_group_id") val categoryGroupId: String,
    @ColumnInfo(name = "category_group_name") val categoryGroupName: String,
    val name: String,
    val hidden: Boolean,
    val budgeted: Long,
    val balance: Long,
    val deleted: Boolean,
)
