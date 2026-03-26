package com.receiptscanner.domain.repository

import com.receiptscanner.domain.model.Receipt
import kotlinx.coroutines.flow.Flow

interface ReceiptRepository {
    suspend fun saveReceipt(receipt: Receipt): Result<String>
    fun getAllReceipts(): Flow<List<Receipt>>
    suspend fun getReceiptById(id: String): Result<Receipt?>
    suspend fun updateReceiptStatus(id: String, transactionId: String?, status: String): Result<Unit>
    suspend fun deleteReceipt(id: String): Result<Unit>
}
