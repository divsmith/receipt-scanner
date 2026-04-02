package com.receiptscanner.presentation.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptscanner.domain.model.CloudOcrResolution
import com.receiptscanner.domain.model.OcrMode
import com.receiptscanner.presentation.camera.DebugOcrOverlay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReceiptPreviewScreen(
    onNavigateToReview: (String) -> Unit,
    onRetake: () -> Unit,
    viewModel: ReceiptPreviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.navigateToReview.collectLatest { receiptId ->
            onNavigateToReview(receiptId)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Debug overlay takes over the full screen when active
    if (uiState.debugOcrData != null) {
        DebugOcrOverlay(
            debugData = uiState.debugOcrData!!,
            onContinue = { viewModel.continueFromDebugOverlay() },
            onRetake = { viewModel.retakeFromDebugOverlay() },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Receipt") },
                navigationIcon = {
                    IconButton(onClick = onRetake) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retake")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Receipt image ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    uiState.isLoadingImage -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(64.dp)
                                .size(48.dp),
                        )
                    }
                    uiState.previewBitmap != null -> {
                        // Render the scaled-down bitmap at full width so pixelation
                        // is visible at lower resolution tiers.
                        Image(
                            painter = BitmapPainter(
                                image = uiState.previewBitmap!!,
                                // NearestNeighbor makes the pixel degradation explicit
                                filterQuality = FilterQuality.None,
                            ),
                            contentDescription = "Receipt preview",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> {
                        Text(
                            text = "Image unavailable",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                }
            }

            // ── Cloud OCR resolution picker (cloud mode only) ────────────────
            if (uiState.ocrMode == OcrMode.CLOUD && !uiState.isLoadingImage) {
                HorizontalDivider()

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Cloud OCR Resolution",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Lower resolution sends less data and processes faster.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CloudOcrResolution.entries.forEach { tier ->
                            FilterChip(
                                selected = uiState.selectedResolution == tier,
                                onClick = { viewModel.selectResolution(tier) },
                                label = { Text(tier.label) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (uiState.previewWidth > 0) {
                        Text(
                            text = "~${uiState.estimatedKb} KB  ·  ${uiState.previewWidth}×${uiState.previewHeight} px  ·  ${uiState.selectedResolution.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider()
            }

            // ── Action buttons ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isProcessing,
                ) {
                    Text("Retake")
                }
                Button(
                    onClick = { viewModel.processReceipt() },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoadingImage && !uiState.isProcessing,
                ) {
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning…")
                    } else {
                        Text("Scan")
                    }
                }
            }
        }
    }
}
