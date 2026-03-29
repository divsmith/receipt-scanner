package com.receiptscanner.domain.usecase

import android.graphics.Bitmap
import com.receiptscanner.data.ocr.EntityExtractionHelper
import com.receiptscanner.data.ocr.MlKitTextRecognizer
import com.receiptscanner.data.ocr.ReceiptParser
import com.receiptscanner.data.ocr.ImagePreprocessor
import com.receiptscanner.domain.model.ExtractedReceiptData
import javax.inject.Inject

class ExtractReceiptDataUseCase @Inject constructor(
    private val textRecognizer: MlKitTextRecognizer,
    private val receiptParser: ReceiptParser,
    private val entityExtractor: EntityExtractionHelper,
    private val imagePreprocessor: ImagePreprocessor,
) {
    suspend operator fun invoke(bitmap: Bitmap, rotationDegrees: Int = 0): Result<ExtractedReceiptData> {
        return try {
            val processed = imagePreprocessor.preprocess(bitmap)
            try {
                val ocrResult = textRecognizer.recognizeText(processed, rotationDegrees)
                val parsed = receiptParser.parse(ocrResult)

                val entities = entityExtractor.extract(ocrResult.fullText)

                val useEntityTotal = parsed.totalAmount == null ||
                    (parsed.totalConfidence < 0.3f && entities.totalAmount != null)

                val merged = parsed.copy(
                    date = parsed.date ?: entities.date,
                    totalAmount = if (useEntityTotal) entities.totalAmount ?: parsed.totalAmount else parsed.totalAmount,
                    totalConfidence = if (useEntityTotal && entities.totalAmount != null) 0.25f else parsed.totalConfidence,
                    cardLastFour = parsed.cardLastFour ?: entities.cardLastFour,
                )

                Result.success(merged)
            } finally {
                processed.recycle()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
