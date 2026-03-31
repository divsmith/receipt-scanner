package com.receiptscanner.data.ocr

import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps the ML Kit Document Scanner API, which provides a complete scanning flow
 * with perspective correction, shadow removal, auto-rotation, and cropping.
 *
 * The scanner provides its own camera UI via an [IntentSender]. Callers launch the
 * intent and pass the result back via [parseResult].
 */
@Singleton
class DocumentScannerHelper @Inject constructor() {

    private val options = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(1)
        .setResultFormats(RESULT_FORMAT_JPEG)
        .setScannerMode(SCANNER_MODE_BASE_WITH_FILTER)
        .build()

    private val scanner = GmsDocumentScanning.getClient(options)

    /**
     * Returns an [IntentSender] that launches the document scanner UI.
     * The caller should use [androidx.activity.result.ActivityResultLauncher] with
     * [androidx.activity.result.IntentSenderRequest] to start it.
     */
    suspend fun getScanIntent(activity: Activity): IntentSender {
        return suspendCancellableCoroutine { continuation ->
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    if (continuation.isActive) {
                        continuation.resume(intentSender)
                    }
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
        }
    }

    /**
     * Parses the result from the document scanner activity.
     * Returns the URI of the scanned JPEG image, or null if scanning was cancelled.
     */
    fun parseResult(resultCode: Int, data: Intent?): android.net.Uri? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        val result = GmsDocumentScanningResult.fromActivityResultIntent(data)
        return result?.pages?.firstOrNull()?.imageUri
    }
}
