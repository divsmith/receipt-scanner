package com.receiptscanner.domain.usecase

import android.graphics.Bitmap
import com.receiptscanner.data.ocr.EntityExtractionHelper
import com.receiptscanner.data.ocr.MlKitTextRecognizer
import com.receiptscanner.data.ocr.ReceiptParser
import com.receiptscanner.domain.model.ExtractedReceiptData
import javax.inject.Inject

class ExtractReceiptDataUseCase @Inject constructor(
    private val textRecognizer: MlKitTextRecognizer,
    private val receiptParser: ReceiptParser,
    private val entityExtractor: EntityExtractionHelper,
) {
    suspend operator fun invoke(bitmap: Bitmap, rotationDegrees: Int = 0): Result<ExtractedReceiptData> {
        return try {
            val ocrResult = textRecognizer.recognizeText(bitmap, rotationDegrees)
            val parsed = receiptParser.parse(ocrResult)

            // Use ML Kit Entity Extraction as a secondary validation pass. It runs
            // concurrently-safe here since both are suspend funs on the calling coroutine.
            // Entity results only override parser results when the parser found nothing.
            val entities = entityExtractor.extract(ocrResult.fullText)

            val merged = parsed.copy(
                date = parsed.date ?: entities.date,
                totalAmount = parsed.totalAmount ?: entities.totalAmount,
                cardLastFour = parsed.cardLastFour ?: entities.cardLastFour,
            )

            Result.success(merged)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
