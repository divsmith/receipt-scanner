package com.receiptscanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.receiptscanner.data.local.dao.AccountCacheDao
import com.receiptscanner.data.local.dao.CategoryCacheDao
import com.receiptscanner.data.local.dao.PayeeCacheDao
import com.receiptscanner.data.local.dao.PendingTransactionDao
import com.receiptscanner.data.local.dao.ReceiptDao
import com.receiptscanner.data.local.dao.SyncMetadataDao
import com.receiptscanner.data.local.entity.AccountCacheEntity
import com.receiptscanner.data.local.entity.CategoryCacheEntity
import com.receiptscanner.data.local.entity.PayeeCacheEntity
import com.receiptscanner.data.local.entity.PendingTransactionEntity
import com.receiptscanner.data.local.entity.ReceiptEntity
import com.receiptscanner.data.local.entity.SyncMetadataEntity

@Database(
    entities = [
        PayeeCacheEntity::class,
        CategoryCacheEntity::class,
        AccountCacheEntity::class,
        PendingTransactionEntity::class,
        ReceiptEntity::class,
        SyncMetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun payeeCacheDao(): PayeeCacheDao
    abstract fun categoryCacheDao(): CategoryCacheDao
    abstract fun accountCacheDao(): AccountCacheDao
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun syncMetadataDao(): SyncMetadataDao
}
