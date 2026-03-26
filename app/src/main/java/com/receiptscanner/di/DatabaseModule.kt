package com.receiptscanner.di

import android.content.Context
import androidx.room.Room
import com.receiptscanner.data.local.AppDatabase
import com.receiptscanner.data.local.TokenProvider
import com.receiptscanner.data.local.TokenProviderImpl
import com.receiptscanner.data.local.dao.AccountCacheDao
import com.receiptscanner.data.local.dao.CategoryCacheDao
import com.receiptscanner.data.local.dao.PayeeCacheDao
import com.receiptscanner.data.local.dao.PendingTransactionDao
import com.receiptscanner.data.local.dao.ReceiptDao
import com.receiptscanner.data.local.dao.SyncMetadataDao
import com.receiptscanner.data.repository.ReceiptRepositoryImpl
import com.receiptscanner.data.repository.TransactionQueueRepositoryImpl
import com.receiptscanner.data.repository.YnabRepositoryImpl
import com.receiptscanner.domain.repository.ReceiptRepository
import com.receiptscanner.domain.repository.TransactionQueueRepository
import com.receiptscanner.domain.repository.YnabRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "receipt_scanner.db",
        ).build()
    }

    @Provides
    fun providePayeeCacheDao(db: AppDatabase): PayeeCacheDao = db.payeeCacheDao()

    @Provides
    fun provideCategoryCacheDao(db: AppDatabase): CategoryCacheDao = db.categoryCacheDao()

    @Provides
    fun provideAccountCacheDao(db: AppDatabase): AccountCacheDao = db.accountCacheDao()

    @Provides
    fun providePendingTransactionDao(db: AppDatabase): PendingTransactionDao = db.pendingTransactionDao()

    @Provides
    fun provideReceiptDao(db: AppDatabase): ReceiptDao = db.receiptDao()

    @Provides
    fun provideSyncMetadataDao(db: AppDatabase): SyncMetadataDao = db.syncMetadataDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {

    @Binds
    @Singleton
    abstract fun bindYnabRepository(impl: YnabRepositoryImpl): YnabRepository

    @Binds
    @Singleton
    abstract fun bindReceiptRepository(impl: ReceiptRepositoryImpl): ReceiptRepository

    @Binds
    @Singleton
    abstract fun bindTransactionQueueRepository(impl: TransactionQueueRepositoryImpl): TransactionQueueRepository

    @Binds
    @Singleton
    abstract fun bindTokenProvider(impl: TokenProviderImpl): TokenProvider
}
