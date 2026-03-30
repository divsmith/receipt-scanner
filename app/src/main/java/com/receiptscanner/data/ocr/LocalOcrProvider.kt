package com.receiptscanner.data.ocr

import android.graphics.Bitmap
import com.receiptscanner.domain.model.ExtractedReceiptData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalOcrProvider @Inject constructor(
    private val textRecognizer: MlKitTextRecognizer,
    private val receiptParser: ReceiptParser,
    private val entityExtractor: EntityExtractionHelper,
    private val imagePreprocessor: ImagePreprocessor,
) {
    suspend fun extract(bitmap: Bitmap, rotationDegrees: Int): Result<ExtractedReceiptData> {
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
