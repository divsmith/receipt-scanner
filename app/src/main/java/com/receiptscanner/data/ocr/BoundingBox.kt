package com.receiptscanner.data.ocr

/**
 * A platform-independent bounding box for OCR text lines. Replaces [android.graphics.Rect]
 * in the OCR model layer so parsing logic is testable without Android framework classes.
 */
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun height(): Int = bottom - top
    fun width(): Int = right - left
}
