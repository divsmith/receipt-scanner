package com.receiptscanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.receiptscanner.data.local.entity.AccountCacheEntity

@Dao
interface AccountCacheDao {
    @Upsert
    suspend fun upsertAll(accounts: List<AccountCacheEntity>)

    @Query("SELECT * FROM account_cache WHERE deleted = 0 AND closed = 0 ORDER BY name ASC")
    suspend fun getAllOpenNonDeleted(): List<AccountCacheEntity>

    @Query("SELECT * FROM account_cache WHERE id = :id")
    suspend fun getById(id: String): AccountCacheEntity?

    @Query("SELECT * FROM account_cache WHERE deleted = 0 AND note LIKE '%' || :text || '%'")
    suspend fun findByNoteContaining(text: String): List<AccountCacheEntity>

    @Query("DELETE FROM account_cache")
    suspend fun clearAll()
}
