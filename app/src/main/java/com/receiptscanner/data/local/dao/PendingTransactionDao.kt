package com.receiptscanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.receiptscanner.data.local.entity.PendingTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTransactionDao {
    @Insert
    suspend fun insert(entity: PendingTransactionEntity): Long

    @Query("SELECT * FROM pending_transactions WHERE status IN ('PENDING', 'SUBMITTING', 'FAILED') ORDER BY created_at ASC")
    fun getAllPending(): Flow<List<PendingTransactionEntity>>

    @Query("SELECT * FROM pending_transactions WHERE id = :id")
    suspend fun getById(id: Long): PendingTransactionEntity?

    @Query("UPDATE pending_transactions SET status = :status, error_message = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, errorMessage: String? = null)

    @Query("DELETE FROM pending_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM pending_transactions WHERE status = 'PENDING'")
    fun countPending(): Flow<Int>

    @Query("UPDATE pending_transactions SET status = 'PENDING', error_message = NULL WHERE status = 'FAILED'")
    suspend fun retryAllFailed()
}
