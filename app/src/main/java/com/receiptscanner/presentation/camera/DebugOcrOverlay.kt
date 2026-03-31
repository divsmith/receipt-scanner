package com.receiptscanner.presentation.camera

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.receiptscanner.data.ocr.BoundingBox
import com.receiptscanner.data.ocr.DebugOcrData
import com.receiptscanner.data.ocr.FieldSource
import com.receiptscanner.data.ocr.FieldType

private val fieldColors = mapOf(
    FieldType.STORE_NAME to Color(0xFF42A5F5),     // blue
    FieldType.TOTAL to Color(0xFF66BB6A),            // green
    FieldType.DATE to Color(0xFFFFA726),             // orange
    FieldType.CARD_LAST_FOUR to Color(0xFFEF5350),   // red
)

private val fieldLabels = mapOf(
    FieldType.STORE_NAME to "Store",
    FieldType.TOTAL to "Total",
    FieldType.DATE to "Date",
    FieldType.CARD_LAST_FOUR to "Card",
)

private val unmatched = Color(0x80BDBDBD) // gray, semi-transparent

@Composable
fun DebugOcrOverlay(
    debugData: DebugOcrData,
    onContinue: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = remember(debugData.imagePath) {
        BitmapFactory.decodeFile(debugData.imagePath)?.asImageBitmap()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (imageBitmap != null) {
            val fieldSourceBoxes = remember(debugData.fieldSources) {
                debugData.fieldSources.mapNotNull { source ->
                    source.textLine.boundingBox?.let { box -> source to box }
                }.toMap()
            }

            val matchedBoxes = remember(debugData.fieldSources) {
                debugData.fieldSources.mapNotNull { it.textLine.boundingBox }.toSet()
            }

            val allLineBoxes = remember(debugData.ocrResult) {
                debugData.ocrResult.blocks.flatMap { block ->
                    block.lines.mapNotNull { line ->
                        line.boundingBox?.let { box -> line.text to box }
                    }
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val imageWidth = imageBitmap.width.toFloat()
                val imageHeight = imageBitmap.height.toFloat()

                // Calculate scale to fit image in canvas while maintaining aspect ratio
                val scaleX = size.width / imageWidth
                val scaleY = size.height / imageHeight
                val scale = minOf(scaleX, scaleY)
                val offsetX = (size.width - imageWidth * scale) / 2f
                val offsetY = (size.height - imageHeight * scale) / 2f

                // Draw the receipt image
                drawImage(
                    image = imageBitmap,
                    dstSize = IntSize(
                        (imageWidth * scale).toInt(),
                        (imageHeight * scale).toInt(),
                    ),
                    dstOffset = androidx.compose.ui.unit.IntOffset(
                        offsetX.toInt(),
                        offsetY.toInt(),
                    ),
                )

                // Draw unmatched text line boxes (gray)
                allLineBoxes.forEach { (_, box) ->
                    if (box !in matchedBoxes) {
                        drawBoundingBox(box, unmatched, scale, offsetX, offsetY)
                    }
                }

                // Draw matched field boxes (colored) with labels
                fieldSourceBoxes.forEach { (source, box) ->
                    val color = fieldColors[source.fieldType] ?: unmatched
                    val label = fieldLabels[source.fieldType] ?: ""
                    drawBoundingBox(box, color, scale, offsetX, offsetY, strokeWidth = 3f)
                    drawFieldLabel(
                        label = "$label ${(source.confidence * 100).toInt()}%",
                        box = box,
                        color = color,
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                    )
                }
            }
        }

        // Legend + action buttons at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.8f))
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            // Color legend
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                fieldColors.forEach { (type, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(color),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            fieldLabels[type] ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }

            // Stats
            Text(
                "${debugData.ocrResult.blocks.sumOf { it.lines.size }} lines detected, " +
                    "${debugData.fieldSources.size} fields matched",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Text("Retake")
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Continue")
                }
            }
        }

        // Title bar
        Text(
            "OCR Debug",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
        )
    }
}

private fun DrawScope.drawBoundingBox(
    box: BoundingBox,
    color: Color,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    strokeWidth: Float = 1.5f,
) {
    val left = box.left * scale + offsetX
    val top = box.top * scale + offsetY
    val width = box.width() * scale
    val height = box.height() * scale

    drawRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Stroke(width = strokeWidth),
    )
}

private fun DrawScope.drawFieldLabel(
    label: String,
    box: BoundingBox,
    color: Color,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
) {
    val x = box.left * scale + offsetX
    val y = box.top * scale + offsetY - 4f // just above the box

    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.argb(
                (color.alpha * 255).toInt(),
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt(),
            )
            textSize = 28f
            isAntiAlias = true
            isFakeBoldText = true
            setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK)
        }
        canvas.nativeCanvas.drawText(label, x, y, paint)
    }
}
