package com.receiptscanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.receiptscanner.data.local.entity.ReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReceiptEntity)

    @Query("SELECT * FROM receipts ORDER BY created_at DESC")
    fun getAllOrderedByCreatedAt(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getById(id: String): ReceiptEntity?

    @Query("UPDATE receipts SET transaction_id = :transactionId, status = :status WHERE id = :id")
    suspend fun updateTransactionIdAndStatus(id: String, transactionId: String?, status: String)

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteById(id: String)
}
