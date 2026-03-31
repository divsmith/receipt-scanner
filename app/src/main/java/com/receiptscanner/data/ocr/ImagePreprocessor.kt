package com.receiptscanner.data.ocr

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Image preprocessing pipeline for receipt photos before OCR.
 *
 * Two-stage pipeline to maximize ML Kit text recognition accuracy:
 *
 * 1. **Resolution normalization** — scales the image so the longer dimension is [TARGET_LONG_SIDE].
 *    Most corpus images are 640×640 or smaller; upscaling raises line-height from ~8 px to ~20 px,
 *    which dramatically improves ML Kit's character-level accuracy. Large camera photos are
 *    downscaled to reduce memory pressure without losing readable detail.
 *
 * 2. **Adaptive grayscale contrast stretch** — converts to grayscale then stretches the tonal
 *    range based on each image's actual 2nd–98th percentile luminance distribution. Adapts
 *    automatically to washed-out thermal prints, shadowed areas, and fluorescent-lit counters
 *    rather than applying a fixed multiplier that may clip or under-enhance.
 *
 * **Preprocessing experiments (all net-negative on 450-fixture corpus):**
 * - Laplacian sharpening (k = 0.5): Date −5 %, Total +1.5 % — ringing on thin date separators.
 * - Unsharp mask (σ ≈ 1.2 px, amount = 0.25): Date −3.1 %, Total −0.6 % — same ringing pattern
 *   at lower severity but still net-negative. The 5×5 Gaussian kernel narrows inter-character
 *   gaps on dense thermal-printer fonts, confusing ML Kit's line segmentation.
 * - 3×3 Median filter: Date −4.0 %, Card −1.6 %, Exact −2.7 % — removes fine detail that ML Kit
 *   relies on for small characters (dates, card digits). Salt-and-pepper noise is rare enough in
 *   the corpus that the cure is worse than the disease.
 *
 * The original [Bitmap] is not recycled; callers remain responsible for its lifecycle.
 */
@Singleton
class ImagePreprocessor @Inject constructor() {

    companion object {
        // Normalize the longer image dimension to this size. 1 600 px gives ML Kit enough
        // vertical resolution to read even dense thermal-printer fonts (~20 px per line for
        // a 60-line receipt) while keeping memory well within Android's typical heap limits.
        private const val TARGET_LONG_SIDE = 1600

        // Histogram percentile clip points for contrast stretching.
        // 2 % clips specular highlights / dark borders that would otherwise pin the stretch range.
        private const val HISTOGRAM_LOW_PCT = 0.02f
        private const val HISTOGRAM_HIGH_PCT = 0.98f

        // Minimum tonal range after percentile clipping. Prevents extreme quantisation when the
        // image is nearly uniform (e.g. all-white blank area accidentally captured).
        private const val MIN_CONTRAST_RANGE = 32
    }

    /**
     * Returns a contrast-enhanced grayscale copy of [bitmap] optimized for text recognition.
     * Runs on [Dispatchers.IO] to avoid blocking the main thread.
     */
    suspend fun preprocess(bitmap: Bitmap): Bitmap = withContext(Dispatchers.IO) {
        val scaled = normalizeResolution(bitmap)
        try {
            adaptiveGrayscaleContrast(scaled)
        } finally {
            // Only recycle the intermediate bitmap; the original is the caller's responsibility.
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    // ── Resolution normalization ─────────────────────────────────────────────────────────────────

    private fun normalizeResolution(bitmap: Bitmap): Bitmap {
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide == TARGET_LONG_SIDE) return bitmap

        val scale = TARGET_LONG_SIDE.toFloat() / longSide
        val newWidth = (bitmap.width * scale).roundToInt()
        val newHeight = (bitmap.height * scale).roundToInt()
        // filter=true requests bilinear interpolation — better quality than nearest-neighbour.
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // ── Adaptive grayscale contrast stretch ──────────────────────────────────────────────────────

    private fun adaptiveGrayscaleContrast(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Single pass: convert to grayscale and build a 256-bin histogram.
        val gray = IntArray(totalPixels)
        val histogram = IntArray(256)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // BT.601 luminance coefficients.
            val luma = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
            gray[i] = luma
            histogram[luma]++
        }

        // Find low/high clip thresholds by walking the cumulative histogram.
        val lowCount = (totalPixels * HISTOGRAM_LOW_PCT).toInt()
        val highCount = (totalPixels * HISTOGRAM_HIGH_PCT).toInt()
        var low = 0
        var high = 255
        var cumulative = 0
        var lowFound = false
        for (v in 0..255) {
            cumulative += histogram[v]
            if (!lowFound && cumulative >= lowCount) {
                low = v
                lowFound = true
            }
            if (cumulative >= highCount) {
                high = v
                break
            }
        }

        // Guarantee a minimum tonal range so we don't over-quantise uniform images.
        val range = (high - low).coerceAtLeast(MIN_CONTRAST_RANGE)
        high = (low + range).coerceAtMost(255)

        // Second pass: apply linear stretch and write output as grayscale ARGB.
        val stretchScale = 255f / range
        val output = IntArray(totalPixels)
        for (i in output.indices) {
            val v = ((gray[i] - low) * stretchScale).toInt().coerceIn(0, 255)
            output[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(output, 0, width, 0, 0, width, height)
        return result
    }

}
