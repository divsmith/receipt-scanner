package com.receiptscanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.receiptscanner.data.local.entity.CategoryCacheEntity

@Dao
interface CategoryCacheDao {
    @Upsert
    suspend fun upsertAll(categories: List<CategoryCacheEntity>)

    @Query("SELECT * FROM category_cache WHERE deleted = 0 ORDER BY category_group_name ASC, name ASC")
    suspend fun getAllNonDeleted(): List<CategoryCacheEntity>

    @Query("SELECT * FROM category_cache WHERE deleted = 0 AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchByName(query: String): List<CategoryCacheEntity>

    @Query("DELETE FROM category_cache")
    suspend fun clearAll()
}
