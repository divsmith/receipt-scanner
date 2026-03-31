package com.receiptscanner.domain.usecase

import android.graphics.Bitmap
import com.receiptscanner.data.local.UserPreferencesManager
import com.receiptscanner.data.ocr.CloudOcrProvider
import com.receiptscanner.data.ocr.DebugOcrData
import com.receiptscanner.data.ocr.LocalOcrProvider
import com.receiptscanner.domain.model.ExtractedReceiptData
import com.receiptscanner.data.ocr.EntityExtractionHelper
import com.receiptscanner.domain.model.OcrMode
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import javax.inject.Inject

internal fun mergeParsedAndEntityData(
    parsed: ExtractedReceiptData,
    entities: EntityExtractionHelper.ExtractionResult,
): ExtractedReceiptData {
    val useEntityTotal = parsed.totalAmount == null && entities.totalAmount != null

    // ML Kit entity extraction sometimes returns today's date as a fallback when it cannot
    // resolve a date entity (e.g. numeric codes that loosely resemble dates). Reject any
    // entity-extracted date that is in the future — receipt dates are always today or earlier.
    val safeEntityDate = entities.date?.takeIf { !it.isAfter(LocalDate.now()) }

    return parsed.copy(
        date = parsed.date ?: safeEntityDate,
        totalAmount = if (useEntityTotal) entities.totalAmount ?: parsed.totalAmount else parsed.totalAmount,
        totalConfidence = if (useEntityTotal) 0.25f else parsed.totalConfidence,
        cardLastFour = parsed.cardLastFour ?: entities.cardLastFour,
    )
}

data class ExtractionResult(
    val data: ExtractedReceiptData,
    val debugOcrData: DebugOcrData? = null,
)

class ExtractReceiptDataUseCase @Inject constructor(
    private val localOcrProvider: LocalOcrProvider,
    private val cloudOcrProvider: CloudOcrProvider,
    private val userPreferencesManager: UserPreferencesManager,
) {
    suspend operator fun invoke(bitmap: Bitmap, rotationDegrees: Int = 0): Result<ExtractedReceiptData> {
        return when (userPreferencesManager.ocrMode.first()) {
            OcrMode.LOCAL -> localOcrProvider.extract(bitmap, rotationDegrees)
            OcrMode.CLOUD -> cloudOcrProvider.extract(bitmap, rotationDegrees)
        }
    }

    suspend fun invokeWithDebugInfo(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        imagePath: String,
    ): Result<ExtractionResult> {
        return when (userPreferencesManager.ocrMode.first()) {
            OcrMode.LOCAL -> localOcrProvider.extractWithDebugInfo(bitmap, rotationDegrees, imagePath)
                .map { (data, debug) -> ExtractionResult(data, debug) }
            OcrMode.CLOUD -> cloudOcrProvider.extract(bitmap, rotationDegrees)
                .map { data -> ExtractionResult(data, null) }
        }
    }
}
