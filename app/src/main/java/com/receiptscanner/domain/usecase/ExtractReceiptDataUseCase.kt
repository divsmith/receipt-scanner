package com.receiptscanner.domain.usecase

import android.graphics.Bitmap
import com.receiptscanner.data.ocr.EntityExtractionHelper
import com.receiptscanner.data.ocr.MlKitTextRecognizer
import com.receiptscanner.data.ocr.ReceiptParser
import com.receiptscanner.data.ocr.ImagePreprocessor
import com.receiptscanner.domain.model.ExtractedReceiptData
import javax.inject.Inject

internal fun mergeParsedAndEntityData(
    parsed: ExtractedReceiptData,
    entities: EntityExtractionHelper.ExtractionResult,
): ExtractedReceiptData {
    val useEntityTotal = parsed.totalAmount == null && entities.totalAmount != null

    return parsed.copy(
        date = parsed.date ?: entities.date,
        totalAmount = if (useEntityTotal) entities.totalAmount ?: parsed.totalAmount else parsed.totalAmount,
        totalConfidence = if (useEntityTotal) 0.25f else parsed.totalConfidence,
        cardLastFour = parsed.cardLastFour ?: entities.cardLastFour,
    )
}

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
                val merged = mergeParsedAndEntityData(parsed, entities)

                Result.success(merged)
            } finally {
                processed.recycle()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
