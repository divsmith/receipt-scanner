package com.receiptscanner.data.repository

import com.receiptscanner.data.local.dao.ReceiptDao
import com.receiptscanner.data.local.entity.ReceiptEntity
import com.receiptscanner.domain.model.ExtractedReceiptData
import com.receiptscanner.domain.model.Receipt
import com.receiptscanner.domain.repository.ReceiptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptRepositoryImpl @Inject constructor(
    private val receiptDao: ReceiptDao,
) : ReceiptRepository {

    override suspend fun saveReceipt(receipt: Receipt): Result<String> = runCatching {
        receiptDao.insert(receipt.toEntity())
        receipt.id
    }

    override fun getAllReceipts(): Flow<List<Receipt>> {
        return receiptDao.getAllOrderedByCreatedAt().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getReceiptById(id: String): Result<Receipt?> = runCatching {
        receiptDao.getById(id)?.toDomain()
    }

    override suspend fun updateReceiptStatus(
        id: String,
        transactionId: String?,
        status: String,
    ): Result<Unit> = runCatching {
        receiptDao.updateTransactionIdAndStatus(id, transactionId, status)
    }

    override suspend fun deleteReceipt(id: String): Result<Unit> = runCatching {
        receiptDao.deleteById(id)
    }
}

private fun Receipt.toEntity() = ReceiptEntity(
    id = id,
    imagePath = imagePath,
    storeName = extractedData?.storeName,
    totalAmount = extractedData?.totalAmount,
    date = extractedData?.date?.toString(),
    cardLastFour = extractedData?.cardLastFour,
    rawOcrText = extractedData?.rawText,
    transactionId = null,
    status = "SCANNED",
    createdAt = createdAt,
)

private fun ReceiptEntity.toDomain() = Receipt(
    id = id,
    imagePath = imagePath,
    extractedData = ExtractedReceiptData(
        storeName = storeName,
        totalAmount = totalAmount,
        date = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        cardLastFour = cardLastFour,
        rawText = rawOcrText ?: "",
    ),
    createdAt = createdAt,
)
