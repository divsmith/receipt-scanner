package com.receiptscanner.data.ocr

import com.receiptscanner.domain.model.ExtractedReceiptData
import com.receiptscanner.domain.util.MilliunitConverter

/**
 * Maps extracted receipt fields back to their source OCR TextLines
 * for debug visualization. Uses heuristic text matching — accurate enough
 * for bounding box overlays without modifying the core parser.
 */
object FieldSourceMapper {

    fun mapFieldSources(
        ocrResult: TextRecognitionResult,
        data: ExtractedReceiptData,
    ): List<FieldSource> {
        val allLines = ocrResult.blocks.flatMap { it.lines }
            .filter { it.boundingBox != null }
        val sources = mutableListOf<FieldSource>()

        data.storeName?.let { name ->
            findStoreNameLine(allLines, name)?.let {
                sources.add(FieldSource(FieldType.STORE_NAME, it, 1f))
            }
        }

        data.totalAmount?.let { millis ->
            findTotalLine(allLines, millis)?.let {
                sources.add(FieldSource(FieldType.TOTAL, it, data.totalConfidence))
            }
        }

        data.date?.let { date ->
            findDateLine(allLines, date)?.let {
                sources.add(FieldSource(FieldType.DATE, it, 1f))
            }
        }

        data.cardLastFour?.let { card ->
            findCardLine(allLines, card)?.let {
                sources.add(FieldSource(FieldType.CARD_LAST_FOUR, it, 1f))
            }
        }

        return sources
    }

    private fun findStoreNameLine(lines: List<TextLine>, storeName: String): TextLine? {
        val normalized = storeName.lowercase().trim()
        return lines.maxByOrNull { line ->
            val lineText = line.text.lowercase().trim()
            when {
                lineText == normalized -> 100
                lineText.contains(normalized) -> 80
                normalized.contains(lineText) && lineText.length >= 3 -> 60
                else -> {
                    val commonChars = lineText.toSet().intersect(normalized.toSet()).size
                    val maxLen = maxOf(lineText.length, normalized.length)
                    if (maxLen > 0) (commonChars * 40) / maxLen else 0
                }
            }
        }?.takeIf { line ->
            val lineText = line.text.lowercase().trim()
            lineText == normalized ||
                lineText.contains(normalized) ||
                normalized.contains(lineText) && lineText.length >= 3
        }
    }

    private fun findTotalLine(lines: List<TextLine>, milliunits: Long): TextLine? {
        val dollars = MilliunitConverter.milliunitsToDollars(milliunits)
        val formatted = dollars.toPlainString()
        // Try matching "$45.99", "45.99", "45. 99" (OCR-split decimal)
        return lines.find { line ->
            val clean = line.text.replace(" ", "")
            formatted in clean || "$$formatted" in clean
        }
    }

    private fun findDateLine(lines: List<TextLine>, date: java.time.LocalDate): TextLine? {
        val month = date.monthValue.toString().padStart(2, '0')
        val day = date.dayOfMonth.toString().padStart(2, '0')
        val year = date.year.toString()
        val shortYear = year.takeLast(2)

        // Match various date formats: MM/DD/YYYY, MM-DD-YY, YYYY-MM-DD, month names
        val monthName = date.month.name.take(3).lowercase()
            .replaceFirstChar { it.uppercase() }

        return lines.find { line ->
            val text = line.text.lowercase()
            // Numeric patterns
            text.contains("$month/$day/$year") ||
                text.contains("$month-$day-$year") ||
                text.contains("$month/$day/$shortYear") ||
                text.contains("$month-$day-$shortYear") ||
                text.contains("$year-$month-$day") ||
                // Month name patterns
                text.contains(monthName.lowercase()) && text.contains(day) && (text.contains(year) || text.contains(shortYear))
        }
    }

    private fun findCardLine(lines: List<TextLine>, cardLastFour: String): TextLine? {
        return lines.find { line ->
            cardLastFour in line.text
        }
    }
}

enum class FieldType {
    STORE_NAME,
    TOTAL,
    DATE,
    CARD_LAST_FOUR,
}

data class FieldSource(
    val fieldType: FieldType,
    val textLine: TextLine,
    val confidence: Float,
)

data class DebugOcrData(
    val ocrResult: TextRecognitionResult,
    val fieldSources: List<FieldSource>,
    val imagePath: String,
)
