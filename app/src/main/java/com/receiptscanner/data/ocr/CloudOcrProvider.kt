package com.receiptscanner.data.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import com.receiptscanner.data.local.UserPreferencesManager
import com.receiptscanner.data.remote.copilot.CopilotApiService
import com.receiptscanner.data.remote.openrouter.OpenRouterApiService
import com.receiptscanner.domain.model.CloudOcrProviderType
import com.receiptscanner.domain.model.ExtractedReceiptData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudOcrProvider @Inject constructor(
    private val copilotApiService: CopilotApiService,
    private val openRouterApiService: OpenRouterApiService,
    private val userPreferencesManager: UserPreferencesManager,
) {
    suspend fun extract(bitmap: Bitmap, rotationDegrees: Int): Result<ExtractedReceiptData> {
        return try {
            val base64 = withContext(Dispatchers.Default) {
                val rotated = applyRotation(bitmap, rotationDegrees)
                try {
                    val resized = resizeForCloudOcr(rotated)
                    try {
                        bitmapToBase64Jpeg(resized)
                    } finally {
                        if (resized !== rotated) resized.recycle()
                    }
                } finally {
                    if (rotated !== bitmap) rotated.recycle()
                }
            }

            when (userPreferencesManager.cloudOcrProviderType.first()) {
                CloudOcrProviderType.COPILOT -> copilotApiService.extractReceiptData(base64)
                CloudOcrProviderType.OPENROUTER -> {
                    val modelId = userPreferencesManager.openRouterModelId.first()
                        ?: return Result.failure(
                            IllegalStateException("No OpenRouter model selected. Choose one in Settings.")
                        )
                    openRouterApiService.extractReceiptData(base64, modelId)
                }
            }
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
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private suspend fun resizeForCloudOcr(bitmap: Bitmap): Bitmap {
        val maxDimension = userPreferencesManager.cloudOcrResolution.first().maxDimension
        return ensureMaxDimension(bitmap, maxDimension)
    }

    private fun ensureMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxSide
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
