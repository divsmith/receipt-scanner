package com.receiptscanner.data.ocr

import com.receiptscanner.domain.model.ExtractedReceiptData
import com.receiptscanner.domain.util.MilliunitConverter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptParser @Inject constructor() {

    fun parse(ocrResult: TextRecognitionResult): ExtractedReceiptData {
        val storeName = extractStoreName(ocrResult)
        val totalAmount = extractTotalAmountSpatially(ocrResult.blocks)
            ?: extractTotalAmount(ocrResult.fullText)
        val date = extractDate(ocrResult.fullText)
        val cardLastFour = extractCardLastFour(ocrResult.fullText)

        return ExtractedReceiptData(
            storeName = storeName,
            totalAmount = totalAmount,
            date = date,
            cardLastFour = cardLastFour,
            rawText = ocrResult.fullText,
        )
    }

    /**
     * Extract store name using spatial analysis. Prioritises the line with the tallest bounding
     * box (largest rendered font) in the first few blocks — that line is almost always the store
     * header/logo text. Falls back to the first meaningful text line if no bounding boxes are
     * available (e.g., in unit tests).
     */
    internal fun extractStoreName(ocrResult: TextRecognitionResult): String? {
        if (ocrResult.blocks.isEmpty()) return null

        val candidateBlocks = ocrResult.blocks.take(3)

        // Collect all candidate lines with their bounding-box heights.
        val linesWithHeight = candidateBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val text = line.text.trim()
                if (!isValidStoreNameLine(text)) return@mapNotNull null
                val height = line.boundingBox?.height() ?: 0
                Pair(text, height)
            }

        // Prefer the tallest line (largest font = most prominent text = store name).
        val byHeight = linesWithHeight.maxByOrNull { it.second }
        if (byHeight != null && byHeight.second > 0) {
            return cleanStoreName(byHeight.first)
        }

        // No bounding boxes — fall back to the first valid line in the first 3 blocks.
        for (block in candidateBlocks) {
            val firstLine = block.lines.firstOrNull()?.text?.trim() ?: continue
            if (!isValidStoreNameLine(firstLine)) continue
            return cleanStoreName(firstLine)
        }

        return ocrResult.blocks.firstOrNull()?.lines?.firstOrNull()?.text?.trim()
    }

    /** Returns true if the line is a plausible store-name candidate (not noise/header text). */
    private fun isValidStoreNameLine(text: String): Boolean {
        if (text.length < 3) return false
        if (text.all { it.isDigit() || it == '-' || it == '/' || it == ' ' }) return false
        if (text.matches(Regex("""(?i)(receipt|transaction|welcome|thank\s*you|thanks|store\s*#\d*|address|phone|tel|www\.|http).*"""))) return false
        return true
    }

    private fun cleanStoreName(raw: String): String {
        val cleaned = raw
            .replace(Regex("#\\d+"), "")
            .replace(Regex("\\bSTORE\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bLOC(ATION)?\\b", RegexOption.IGNORE_CASE), "")
            .trim()
            .replace(Regex("\\s+"), " ")
        return cleaned.ifEmpty { raw.trim() }
    }

    /**
     * Spatial total extraction: finds lines containing a TOTAL keyword, then searches nearby
     * lines (same row or immediately below, using bounding box coordinates) for a dollar amount.
     * This avoids picking up sub-totals that appear earlier in the receipt.
     *
     * Returns null if no spatial match is found; the caller should then fall back to
     * [extractTotalAmount].
     */
    internal fun extractTotalAmountSpatially(blocks: List<TextBlock>): Long? {
        val allLines = blocks.flatMap { it.lines }
        if (allLines.all { it.boundingBox == null }) return null

        val totalKeyword = Regex("""(?i)(?:grand\s*)?total|amount\s*due|balance\s*due""")
        val amountPattern = Regex("""\$?\s*([\d,]+\.\d{2})""")

        // Find all lines that contain a total keyword (search from the bottom for the final total).
        val totalLines = allLines.filter { totalKeyword.containsMatchIn(it.text) }.reversed()

        for (totalLine in totalLines) {
            // 1. Check if the total amount is on the same line as the keyword.
            val sameLineMatch = amountPattern.find(totalLine.text)
            if (sameLineMatch != null) {
                return parseMilliunits(sameLineMatch.groupValues[1])
            }

            // 2. Look for an amount on a nearby line (to the right or directly below).
            val totalBox = totalLine.boundingBox ?: continue
            val totalMidY = (totalBox.top + totalBox.bottom) / 2

            val nearbyAmount = allLines
                .filter { candidate ->
                    val box = candidate.boundingBox ?: return@filter false
                    val candidateMidY = (box.top + box.bottom) / 2
                    val isRightOf = box.left > totalBox.right && kotlin.math.abs(candidateMidY - totalMidY) < totalBox.height()
                    val isBelow = box.top >= totalBox.bottom && box.top <= totalBox.bottom + totalBox.height() * 2
                    (isRightOf || isBelow) && amountPattern.containsMatchIn(candidate.text)
                }
                .minByOrNull { candidate ->
                    val box = candidate.boundingBox!!
                    val dx = maxOf(0, box.left - totalBox.right)
                    val dy = maxOf(0, box.top - totalBox.bottom)
                    dx + dy
                }

            if (nearbyAmount != null) {
                val match = amountPattern.find(nearbyAmount.text)
                if (match != null) return parseMilliunits(match.groupValues[1])
            }
        }

        return null
    }

    private fun parseMilliunits(amountStr: String): Long? {
        return try {
            MilliunitConverter.dollarsToMilliunits(BigDecimal(amountStr.replace(",", "")))
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Extract total amount by looking for keywords like TOTAL, AMOUNT DUE, BALANCE DUE
     * followed by a dollar amount. Takes the LAST matching "total" to avoid subtotals.
     */
    internal fun extractTotalAmount(text: String): Long? {
        val lines = text.lines()

        val totalPatterns = listOf(
            Regex("(?i)(?:GRAND\\s*)?TOTAL\\s*:?\\s*\\$?\\s*([\\d,]+\\.\\d{2})"),
            Regex("(?i)AMOUNT\\s*DUE\\s*:?\\s*\\$?\\s*([\\d,]+\\.\\d{2})"),
            Regex("(?i)BALANCE\\s*DUE\\s*:?\\s*\\$?\\s*([\\d,]+\\.\\d{2})"),
            Regex("(?i)TOTAL\\s+\\$?\\s*([\\d,]+\\.\\d{2})"),
            Regex("(?i)(?:^|\\s)TOTAL\\s*\\$?([\\d,]+\\.\\d{2})"),
        )

        // Search from bottom up (the final total is usually near the bottom)
        for (line in lines.reversed()) {
            for (pattern in totalPatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val amountStr = match.groupValues[1].replace(",", "")
                    return try {
                        val dollars = BigDecimal(amountStr)
                        MilliunitConverter.dollarsToMilliunits(dollars)
                    } catch (e: NumberFormatException) {
                        null
                    }
                }
            }
        }

        // Fallback: look for the largest dollar amount on the receipt
        val allAmounts = Regex("\\$\\s*([\\d,]+\\.\\d{2})")
            .findAll(text)
            .mapNotNull { match ->
                try {
                    BigDecimal(match.groupValues[1].replace(",", ""))
                } catch (e: NumberFormatException) {
                    null
                }
            }
            .toList()

        return allAmounts.maxOrNull()?.let { MilliunitConverter.dollarsToMilliunits(it) }
    }

    /**
     * Extract date from receipt text. Tries multiple common formats.
     */
    internal fun extractDate(text: String): LocalDate? {
        val datePatterns = listOf(
            // MM/DD/YYYY or MM-DD-YYYY
            Regex("(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{4})") to "M/d/yyyy",
            // MM/DD/YY or MM-DD-YY
            Regex("(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{2})(?!\\d)") to "M/d/yy",
            // YYYY-MM-DD (ISO format)
            Regex("(\\d{4})-(\\d{2})-(\\d{2})") to "yyyy-MM-dd",
            // Month DD, YYYY (e.g., "Jan 15, 2024" or "January 15, 2024")
            Regex("(?i)(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+(\\d{1,2}),?\\s+(\\d{4})") to null,
        )

        for ((pattern, format) in datePatterns) {
            val match = pattern.find(text) ?: continue
            try {
                if (format != null) {
                    val dateStr = match.value.replace("-", "/")
                    val formatter = DateTimeFormatter.ofPattern(format.replace("-", "/"))
                    return LocalDate.parse(dateStr, formatter)
                } else {
                    // Handle month name format
                    val monthStr = match.groupValues[1]
                    val day = match.groupValues[2].toInt()
                    val year = match.groupValues[3].toInt()
                    val month = parseMonthName(monthStr) ?: continue
                    return LocalDate.of(year, month, day)
                }
            } catch (e: DateTimeParseException) {
                continue
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    private fun parseMonthName(name: String): Int? {
        return when (name.lowercase().take(3)) {
            "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4
            "may" -> 5; "jun" -> 6; "jul" -> 7; "aug" -> 8
            "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
            else -> null
        }
    }

    /**
     * Extract the last 4 digits of the payment card number.
     * Receipts typically show these as: ****1234, xxxx1234, XXXX1234,
     * "ending in 1234", "card ...1234", VISA ****1234, etc.
     */
    internal fun extractCardLastFour(text: String): String? {
        val patterns = listOf(
            Regex("[*xX]{4}\\s*(\\d{4})"),
            Regex("[.…]{2,}\\s*(\\d{4})"),
            Regex("(?i)ending\\s+in\\s+(\\d{4})"),
            Regex("(?i)card\\s*:?\\s*\\*{0,4}\\s*(\\d{4})"),
            Regex("(?i)(?:VISA|MASTERCARD|MC|AMEX|DISCOVER|DEBIT|CREDIT)\\s+\\*{0,4}\\s*(\\d{4})"),
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }
}
