package com.receiptscanner.data.ocr

import com.squareup.moshi.JsonClass

/**
 * A platform-independent bounding box for OCR text lines. Replaces [android.graphics.Rect]
 * in the OCR model layer so parsing logic is testable without Android framework classes.
 */
@JsonClass(generateAdapter = true)
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun height(): Int = bottom - top
    fun width(): Int = right - left
}
