package com.receiptscanner.presentation.camera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receiptscanner.data.camera.CameraManager
import com.receiptscanner.data.local.UserPreferencesManager
import com.receiptscanner.data.ocr.DocumentScannerHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraManager: CameraManager,
    private val userPreferencesManager: UserPreferencesManager,
    private val documentScannerHelper: DocumentScannerHelper,
) : ViewModel() {

    data class UiState(
        val isProcessing: Boolean = false,
        val error: String? = null,
        val isTorchEnabled: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Emits (imagePath, rotationDegrees) after a receipt image is ready for preview. */
    private val _navigateToPreview = MutableSharedFlow<Pair<String, Int>>()
    val navigateToPreview = _navigateToPreview.asSharedFlow()

    private val _launchDocumentScanner = MutableSharedFlow<IntentSender>()
    val launchDocumentScanner = _launchDocumentScanner.asSharedFlow()

    fun capturePhoto(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                val file = cameraManager.capturePhoto(context)
                val rotation = cameraManager.readExifRotation(file)
                _uiState.update { it.copy(isProcessing = false) }
                _navigateToPreview.emit(file.absolutePath to rotation)
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = e.message ?: "Capture failed") }
            }
        }
    }

    fun loadFromGallery(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                val bitmap = cameraManager.loadBitmapFromUri(context, uri)
                if (bitmap != null) {
                    val file = saveReceiptImage(context, bitmap, quality = 90)
                    bitmap.recycle()
                    _uiState.update { it.copy(isProcessing = false) }
                    // Gallery images are already rotated by the system; rotation = 0
                    _navigateToPreview.emit(file.absolutePath to 0)
                } else {
                    _uiState.update { it.copy(isProcessing = false, error = "Failed to load image") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = e.message ?: "Failed to load image") }
            }
        }
    }

    fun startDocumentScan(activity: Activity) {
        viewModelScope.launch {
            try {
                val intentSender = documentScannerHelper.getScanIntent(activity)
                _launchDocumentScanner.emit(intentSender)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Document scanner unavailable") }
            }
        }
    }

    fun handleDocumentScanResult(context: Context, resultCode: Int, data: Intent?) {
        val imageUri = documentScannerHelper.parseResult(resultCode, data) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                val bitmap = cameraManager.loadBitmapFromUri(context, imageUri)
                if (bitmap != null) {
                    val file = saveReceiptImage(context, bitmap, quality = 95)
                    bitmap.recycle()
                    _uiState.update { it.copy(isProcessing = false) }
                    // Scanner output is already corrected; no rotation needed
                    _navigateToPreview.emit(file.absolutePath to 0)
                } else {
                    _uiState.update { it.copy(isProcessing = false, error = "Failed to load scanned image") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = e.message ?: "Scan processing failed") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun toggleTorch() {
        val newEnabled = !_uiState.value.isTorchEnabled
        _uiState.update { it.copy(isTorchEnabled = newEnabled) }
        cameraManager.enableTorch(newEnabled)
    }

    fun getCameraManager(): CameraManager = cameraManager

    private fun saveReceiptImage(context: Context, bitmap: Bitmap, quality: Int): File {
        val storageDir = File(context.filesDir, "receipts")
        if (!storageDir.exists()) storageDir.mkdirs()
        val file = File(storageDir, "receipt_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return file
    }
}

