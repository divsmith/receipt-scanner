package com.receiptscanner.data.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Maximum dimension (px) to which bitmaps are downsampled before OCR. Prevents OOM on high-MP cameras. */
private const val MAX_BITMAP_DIMENSION = 4096

@Singleton
class CameraManager @Inject constructor() {

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    suspend fun initialize(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ) {
        val provider = suspendCancellableCoroutine<ProcessCameraProvider> { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    if (continuation.isActive) continuation.resume(future.get())
                },
                ContextCompat.getMainExecutor(context),
            )
        }

        cameraProvider = provider
        provider.unbindAll()

        val preview = Preview.Builder()
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        camera = provider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture,
        )
    }

    suspend fun capturePhoto(context: Context): File {
        val imageCapture = imageCapture ?: throw IllegalStateException("Camera not initialized")

        val photoFile = createPhotoFile(context)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        return suspendCancellableCoroutine { continuation ->
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        if (continuation.isActive) continuation.resume(photoFile)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        photoFile.delete()
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                },
            )
        }
    }

    /**
     * Decodes a JPEG file into a Bitmap, downsampling to [MAX_BITMAP_DIMENSION] on the IO
     * dispatcher to avoid OOM and UI jank on high-megapixel cameras.
     */
    suspend fun loadBitmapFromFile(file: File): Bitmap? = withContext(Dispatchers.IO) {
        decodeSampled(file.absolutePath, null, null)
    }

    /**
     * Decodes an image from a content URI, downsampling to [MAX_BITMAP_DIMENSION] on the IO
     * dispatcher to avoid OOM and UI jank with large gallery images.
     */
    suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            // Buffer the stream so we can read twice (once for bounds, once for pixels)
            val bytes = stream.readBytes()
            decodeSampled(null, null, bytes)
        }
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
        camera = null
    }

    /** Turns the camera torch (flashlight) on or off. No-op if the device has no torch. */
    fun enableTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    /** Returns true if the current camera has a torch (flashlight) available. */
    fun hasTorch(): Boolean = camera?.cameraInfo?.hasFlashUnit() == true

    /**
     * Reads the EXIF rotation from a JPEG file and returns the matching rotation in degrees
     * for use with [MlKitTextRecognizer.recognizeText].
     */
    fun readExifRotation(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun createPhotoFile(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = File(context.filesDir, "receipts")
        if (!storageDir.exists()) storageDir.mkdirs()
        return File(storageDir, "receipt_${timestamp}.jpg")
    }

    private fun decodeSampled(path: String?, file: File?, bytes: ByteArray?): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        when {
            path != null -> BitmapFactory.decodeFile(path, options)
            bytes != null -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            else -> return null
        }

        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight)
        options.inJustDecodeBounds = false

        return when {
            path != null -> BitmapFactory.decodeFile(path, options)
            bytes != null -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            else -> null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var inSampleSize = 1
        if (height > MAX_BITMAP_DIMENSION || width > MAX_BITMAP_DIMENSION) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (
                halfHeight / inSampleSize >= MAX_BITMAP_DIMENSION &&
                halfWidth / inSampleSize >= MAX_BITMAP_DIMENSION
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
