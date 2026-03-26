package com.receiptscanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.receiptscanner.data.local.entity.PayeeCacheEntity

@Dao
interface PayeeCacheDao {
    @Upsert
    suspend fun upsertAll(payees: List<PayeeCacheEntity>)

    @Query("SELECT * FROM payee_cache WHERE deleted = 0 ORDER BY name ASC")
    suspend fun getAllNonDeleted(): List<PayeeCacheEntity>

    @Query("SELECT * FROM payee_cache WHERE deleted = 0 AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchByName(query: String): List<PayeeCacheEntity>

    @Query("DELETE FROM payee_cache WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM payee_cache")
    suspend fun clearAll()
}
