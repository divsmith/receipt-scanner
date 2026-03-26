package com.receiptscanner.domain.usecase

import android.graphics.Bitmap
import com.receiptscanner.data.ocr.MlKitTextRecognizer
import com.receiptscanner.data.ocr.ReceiptParser
import com.receiptscanner.domain.model.ExtractedReceiptData
import javax.inject.Inject

class ExtractReceiptDataUseCase @Inject constructor(
    private val textRecognizer: MlKitTextRecognizer,
    private val receiptParser: ReceiptParser,
) {
    suspend operator fun invoke(bitmap: Bitmap): Result<ExtractedReceiptData> {
        return try {
            val ocrResult = textRecognizer.recognizeText(bitmap)
            val extractedData = receiptParser.parse(ocrResult)
            Result.success(extractedData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
