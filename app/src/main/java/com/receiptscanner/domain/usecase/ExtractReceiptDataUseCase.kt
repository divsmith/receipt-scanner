package com.receiptscanner.domain.usecase

import android.graphics.Bitmap
import com.receiptscanner.data.local.UserPreferencesManager
import com.receiptscanner.data.ocr.CloudOcrProvider
import com.receiptscanner.data.ocr.LocalOcrProvider
import com.receiptscanner.domain.model.ExtractedReceiptData
import com.receiptscanner.domain.model.OcrMode
import kotlinx.coroutines.flow.first
import javax.inject.Inject

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
}
