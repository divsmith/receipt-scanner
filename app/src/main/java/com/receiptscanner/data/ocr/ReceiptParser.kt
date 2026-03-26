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
        val totalAmount = extractTotalAmount(ocrResult.fullText)
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
     * Extract store name: typically the first meaningful text block at the top of the receipt.
     * Skip very short lines (likely "STORE #123" numbers) and look for the first substantial block.
     */
    internal fun extractStoreName(ocrResult: TextRecognitionResult): String? {
        if (ocrResult.blocks.isEmpty()) return null

        for (block in ocrResult.blocks.take(3)) {
            val firstLine = block.lines.firstOrNull()?.text?.trim() ?: continue
            if (firstLine.length < 3) continue
            if (firstLine.all { it.isDigit() || it == '-' || it == '/' }) continue
            if (firstLine.matches(Regex("(?i)(receipt|transaction|welcome|thank you|thanks).*"))) continue
            return cleanStoreName(firstLine)
        }
        return ocrResult.blocks.firstOrNull()?.lines?.firstOrNull()?.text?.trim()
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
