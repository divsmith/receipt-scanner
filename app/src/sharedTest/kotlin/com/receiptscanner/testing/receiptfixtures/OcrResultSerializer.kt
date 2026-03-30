package com.receiptscanner.testing.receiptfixtures

import com.receiptscanner.data.ocr.TextRecognitionResult
import com.squareup.moshi.Moshi

/**
 * Serializes [TextRecognitionResult] to/from JSON so that ML Kit output captured on-device
 * can be replayed in fast JVM-only parser tests without an emulator.
 */
object OcrResultSerializer {

    private val moshi: Moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(TextRecognitionResult::class.java).indent("  ")

    /** Serialize an OCR result to a pretty-printed JSON string. */
    fun toJson(result: TextRecognitionResult): String = adapter.toJson(result)

    /** Deserialize an OCR result from a JSON string. Returns null if parsing fails. */
    fun fromJson(json: String): TextRecognitionResult? = adapter.fromJson(json)
}
