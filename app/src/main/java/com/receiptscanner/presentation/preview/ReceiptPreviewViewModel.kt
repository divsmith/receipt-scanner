package com.receiptscanner.presentation.preview

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receiptscanner.data.camera.CameraManager
import com.receiptscanner.data.local.UserPreferencesManager
import com.receiptscanner.data.ocr.DebugOcrData
import com.receiptscanner.data.ocr.LocalOcrProvider
import com.receiptscanner.domain.model.CloudOcrResolution
import com.receiptscanner.domain.model.OcrMode
import com.receiptscanner.domain.model.Receipt
import com.receiptscanner.domain.repository.ReceiptRepository
import com.receiptscanner.domain.usecase.ExtractReceiptDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ReceiptPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cameraManager: CameraManager,
    private val extractReceiptDataUseCase: ExtractReceiptDataUseCase,
    private val localOcrProvider: LocalOcrProvider,
    private val receiptRepository: ReceiptRepository,
    private val userPreferencesManager: UserPreferencesManager,
) : ViewModel() {

    data class UiState(
        val isLoadingImage: Boolean = true,
        val isProcessing: Boolean = false,
        val error: String? = null,
        val ocrMode: OcrMode = OcrMode.LOCAL,
        val selectedResolution: CloudOcrResolution = CloudOcrResolution.FULL,
        val previewBitmap: ImageBitmap? = null,
        val previewWidth: Int = 0,
        val previewHeight: Int = 0,
        val estimatedKb: Int = 0,
        val debugOcrData: DebugOcrData? = null,
        val pendingReceiptId: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _navigateToReview = MutableSharedFlow<String>()
    val navigateToReview = _navigateToReview.asSharedFlow()

    private val imagePath: String = checkNotNull(savedStateHandle["imagePath"])
    private val rotationDegrees: Int = checkNotNull(savedStateHandle["rotationDegrees"])

    /** The full-resolution source bitmap, held for the lifetime of this ViewModel. */
    private var originalBitmap: Bitmap? = null

    init {
        loadImageAndPreferences()
    }

    private fun loadImageAndPreferences() {
        viewModelScope.launch {
            val ocrMode = userPreferencesManager.ocrMode.first()
            val savedResolution = userPreferencesManager.cloudOcrResolution.first()

            _uiState.update {
                it.copy(
                    ocrMode = ocrMode,
                    selectedResolution = savedResolution,
                )
            }

            val bitmap = withContext(Dispatchers.IO) {
                cameraManager.loadBitmapFromFile(File(imagePath))
            }

            if (bitmap == null) {
                _uiState.update { it.copy(isLoadingImage = false, error = "Failed to load receipt image") }
                return@launch
            }

            originalBitmap = bitmap
            updatePreviewBitmap(bitmap, ocrMode, savedResolution)
        }
    }

    fun selectResolution(resolution: CloudOcrResolution) {
        viewModelScope.launch {
            userPreferencesManager.saveCloudOcrResolution(resolution)
            _uiState.update { it.copy(selectedResolution = resolution) }
            val bitmap = originalBitmap ?: return@launch
            updatePreviewBitmap(bitmap, _uiState.value.ocrMode, resolution)
        }
    }

    fun processReceipt() {
        val bitmap = originalBitmap ?: run {
            _uiState.update { it.copy(error = "Image not loaded") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            val debugMode = userPreferencesManager.debugModeEnabled.first()
            if (debugMode) {
                localOcrProvider.extractWithDebugInfo(bitmap, rotationDegrees, imagePath).fold(
                    onSuccess = { (data, debugData) ->
                        val receipt = Receipt(
                            id = UUID.randomUUID().toString(),
                            imagePath = imagePath,
                            extractedData = data,
                        )
                        receiptRepository.saveReceipt(receipt).fold(
                            onSuccess = { receiptId ->
                                _uiState.update {
                                    it.copy(
                                        isProcessing = false,
                                        debugOcrData = debugData,
                                        pendingReceiptId = receiptId,
                                    )
                                }
                            },
                            onFailure = { e ->
                                _uiState.update { it.copy(isProcessing = false, error = e.message ?: "Failed to save receipt") }
                            },
                        )
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isProcessing = false, error = e.message ?: "OCR failed") }
                    },
                )
            } else {
                extractReceiptDataUseCase(bitmap, rotationDegrees).fold(
                    onSuccess = { extractedData ->
                        val receipt = Receipt(
                            id = UUID.randomUUID().toString(),
                            imagePath = imagePath,
                            extractedData = extractedData,
                        )
                        receiptRepository.saveReceipt(receipt).fold(
                            onSuccess = { receiptId ->
                                _uiState.update { it.copy(isProcessing = false) }
                                _navigateToReview.emit(receiptId)
                            },
                            onFailure = { e ->
                                _uiState.update { it.copy(isProcessing = false, error = e.message ?: "Failed to save receipt") }
                            },
                        )
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isProcessing = false, error = e.message ?: "OCR failed") }
                    },
                )
            }
        }
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

    override fun onCleared() {
        super.onCleared()
        originalBitmap?.recycle()
        originalBitmap = null
    }

    private suspend fun updatePreviewBitmap(
        source: Bitmap,
        ocrMode: OcrMode,
        resolution: CloudOcrResolution,
    ) {
        val (previewBitmap, width, height) = withContext(Dispatchers.Default) {
            val scaled = if (ocrMode == OcrMode.CLOUD) {
                scaleBitmap(source, resolution.maxDimension)
            } else {
                source
            }
            Triple(scaled.asImageBitmap(), scaled.width, scaled.height)
        }
        val estimatedKb = estimateJpegKb(width, height)
        _uiState.update {
            it.copy(
                isLoadingImage = false,
                previewBitmap = previewBitmap,
                previewWidth = width,
                previewHeight = height,
                estimatedKb = estimatedKb,
            )
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxSide
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Rough estimate of a JPEG file size (in KB) at 85% quality.
     * Uses a 0.3 bits-per-pixel approximation suitable for receipts.
     */
    private fun estimateJpegKb(width: Int, height: Int): Int {
        val bitsPerPixel = 0.30
        return ((width * height * bitsPerPixel) / 8 / 1024).toInt().coerceAtLeast(1)
    }
}
