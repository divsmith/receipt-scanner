package com.receiptscanner.data.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import com.receiptscanner.data.remote.copilot.CopilotApiService
import com.receiptscanner.domain.model.ExtractedReceiptData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudOcrProvider @Inject constructor(
    private val copilotApiService: CopilotApiService,
) {
    suspend fun extract(bitmap: Bitmap, rotationDegrees: Int): Result<ExtractedReceiptData> {
        return try {
            val base64 = withContext(Dispatchers.Default) {
                val rotated = applyRotation(bitmap, rotationDegrees)
                try {
                    bitmapToBase64Jpeg(rotated)
                } finally {
                    if (rotated !== bitmap) rotated.recycle()
                }
            }
            copilotApiService.extractReceiptData(base64)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun applyRotation(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap, quality: Int = 85): String {
        val resized = ensureMaxDimension(bitmap, MAX_IMAGE_DIMENSION)
        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        if (resized !== bitmap) resized.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun ensureMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxSide
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    companion object {
        private const val MAX_IMAGE_DIMENSION = 2048
    }
}
