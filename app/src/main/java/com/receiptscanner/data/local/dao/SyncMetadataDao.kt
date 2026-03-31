package com.receiptscanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.receiptscanner.data.local.entity.SyncMetadataEntity

@Dao
interface SyncMetadataDao {
    @Query("SELECT value FROM sync_metadata WHERE `key` = :key")
    suspend fun getValueByKey(key: String): Long?

    @Upsert
    suspend fun upsert(entity: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata")
    suspend fun clearAll()
}
