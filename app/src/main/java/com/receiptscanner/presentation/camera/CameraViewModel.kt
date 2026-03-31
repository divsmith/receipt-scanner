package com.receiptscanner.presentation.camera

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receiptscanner.data.camera.CameraManager
import com.receiptscanner.data.local.UserPreferencesManager
import com.receiptscanner.data.ocr.DebugOcrData
import com.receiptscanner.data.ocr.LocalOcrProvider
import com.receiptscanner.domain.model.ExtractedReceiptData
import com.receiptscanner.domain.model.Receipt
import com.receiptscanner.domain.repository.ReceiptRepository
import com.receiptscanner.domain.usecase.ExtractReceiptDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val extractReceiptDataUseCase: ExtractReceiptDataUseCase,
    private val localOcrProvider: LocalOcrProvider,
    private val receiptRepository: ReceiptRepository,
    private val cameraManager: CameraManager,
    private val userPreferencesManager: UserPreferencesManager,
) : ViewModel() {

    data class UiState(
        val isProcessing: Boolean = false,
        val error: String? = null,
        val isTorchEnabled: Boolean = false,
        val debugOcrData: DebugOcrData? = null,
        val pendingReceiptId: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _navigateToReview = MutableSharedFlow<String>()
    val navigateToReview = _navigateToReview.asSharedFlow()

    fun capturePhoto(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                val file = cameraManager.capturePhoto(context)
                val rotation = cameraManager.readExifRotation(file)
                val bitmap = cameraManager.loadBitmapFromFile(file)
                if (bitmap != null) {
                    processImage(bitmap, file.absolutePath, rotation)
                } else {
                    _uiState.update { it.copy(isProcessing = false, error = "Failed to load captured image") }
                }
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
                    // Save the gallery image to app storage
                    val storageDir = File(context.filesDir, "receipts")
                    if (!storageDir.exists()) storageDir.mkdirs()
                    val file = File(storageDir, "receipt_${System.currentTimeMillis()}.jpg")
                    file.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    // Gallery images are already rotated by the system; rotation = 0
                    processImage(bitmap, file.absolutePath, 0)
                } else {
                    _uiState.update { it.copy(isProcessing = false, error = "Failed to load image") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = e.message ?: "Failed to load image") }
            }
        }
    }

    private suspend fun processImage(bitmap: Bitmap, imagePath: String, rotationDegrees: Int = 0) {
        val debugMode = userPreferencesManager.debugModeEnabled.first()

        if (debugMode) {
            localOcrProvider.extractWithDebugInfo(bitmap, rotationDegrees, imagePath).fold(
                onSuccess = { (data, debugData) ->
                    saveAndNavigateOrShowDebug(data, imagePath, debugData)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isProcessing = false, error = e.message ?: "OCR failed") }
                },
            )
        } else {
            extractReceiptDataUseCase(bitmap, rotationDegrees).fold(
                onSuccess = { extractedData ->
                    saveAndNavigateOrShowDebug(extractedData, imagePath, null)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isProcessing = false, error = e.message ?: "OCR failed") }
                },
            )
        }
    }

    private suspend fun saveAndNavigateOrShowDebug(
        extractedData: ExtractedReceiptData,
        imagePath: String,
        debugOcrData: DebugOcrData?,
    ) {
        val receipt = Receipt(
            id = UUID.randomUUID().toString(),
            imagePath = imagePath,
            extractedData = extractedData,
        )
        receiptRepository.saveReceipt(receipt).fold(
            onSuccess = { receiptId ->
                if (debugOcrData != null) {
                    // Show debug overlay; user will tap "Continue" to proceed
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            debugOcrData = debugOcrData,
                            pendingReceiptId = receiptId,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isProcessing = false) }
                    _navigateToReview.emit(receiptId)
                }
            },
            onFailure = { e ->
                _uiState.update { it.copy(isProcessing = false, error = e.message ?: "Failed to save receipt") }
            },
        )
    }

    fun continueFromDebugOverlay() {
        val receiptId = _uiState.value.pendingReceiptId
        _uiState.update { it.copy(debugOcrData = null, pendingReceiptId = null) }
        if (receiptId != null) {
            viewModelScope.launch {
                _navigateToReview.emit(receiptId)
            }
        }
    }

    fun retakeFromDebugOverlay() {
        _uiState.update { it.copy(debugOcrData = null, pendingReceiptId = null) }
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
}
