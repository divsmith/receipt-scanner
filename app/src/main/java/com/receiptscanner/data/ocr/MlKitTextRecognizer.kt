package com.receiptscanner.data.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class MlKitTextRecognizer @Inject constructor() {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(bitmap: Bitmap, rotationDegrees: Int = 0): TextRecognitionResult {
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (continuation.isActive) {
                        val blocks = visionText.textBlocks.map { block ->
                            TextBlock(
                                text = block.text,
                                lines = block.lines.map { line ->
                                    TextLine(
                                        text = line.text,
                                        boundingBox = line.boundingBox,
                                        confidence = line.confidence,
                                    )
                                },
                                boundingBox = block.boundingBox,
                            )
                        }
                        continuation.resume(
                            TextRecognitionResult(
                                fullText = visionText.text,
                                blocks = blocks,
                            )
                        )
                    }
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
        }
    }

    fun close() {
        recognizer.close()
    }
}

data class TextRecognitionResult(
    val fullText: String,
    val blocks: List<TextBlock>,
)

data class TextBlock(
    val text: String,
    val lines: List<TextLine>,
    val boundingBox: android.graphics.Rect?,
)

data class TextLine(
    val text: String,
    val boundingBox: android.graphics.Rect?,
    val confidence: Float?,
)
