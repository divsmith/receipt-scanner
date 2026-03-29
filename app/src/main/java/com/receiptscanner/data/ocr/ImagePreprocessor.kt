package com.receiptscanner.data.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight image preprocessing for receipt photos before OCR.
 *
 * Applies grayscale conversion and contrast enhancement to improve ML Kit's ability to read
 * faded ink, shadowed areas, and low-contrast thermal prints. The original bitmap is left
 * untouched — a new processed copy is returned.
 */
@Singleton
class ImagePreprocessor @Inject constructor() {

    /**
     * Returns a contrast-enhanced grayscale copy of [bitmap] optimized for text recognition.
     * Runs on [Dispatchers.IO] to avoid blocking the main thread.
     */
    suspend fun preprocess(bitmap: Bitmap): Bitmap = withContext(Dispatchers.IO) {
        val width = bitmap.width
        val height = bitmap.height

        // Create a mutable copy in ARGB_8888 for manipulation
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Step 1: Convert to grayscale with boosted contrast.
        // The color matrix converts RGB→grayscale and applies a contrast multiplier
        // (1.5x) with a brightness offset to pull out faded text.
        val contrast = 1.5f
        val offset = (-(128f * contrast) + 128f)
        val matrix = ColorMatrix().apply {
            setSaturation(0f) // grayscale
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, offset,
                    0f, contrast, 0f, 0f, offset,
                    0f, 0f, contrast, 0f, offset,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
            postConcat(contrastMatrix)
        }

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        output
    }
}
