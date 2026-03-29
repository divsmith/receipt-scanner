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

    /**
     * Keywords that indicate a line is promotional, informational, or otherwise NOT a transaction
     * total. Matched case-insensitively anywhere in the line text.
     */
    private val negativeKeywords = listOf(
        "savings", "saved", "you saved", "annual savings",
        "discount", "coupon", "reward", "loyalty", "points",
        "change due", "change", "cash back", "cashback",
        "credit applied", "refund", "void",
        "tip suggestion", "suggested tip",
    )

    private val negativePattern = Regex(
        negativeKeywords.joinToString("|") { Regex.escape(it) },
        RegexOption.IGNORE_CASE,
    )

    /**
     * Total keyword pattern with word boundaries to avoid matching "SUBTOTAL".
     * Uses negative lookbehind/lookahead to enforce whole-word matching on "total".
     */
    private val totalKeyword = Regex(
        """(?i)(?<!\w)(?:grand\s*)?total(?!\w)|amount\s*due|balance\s*due"""
    )

    private val amountPattern = Regex("""\$?\s*([\d,]+\.\d{2})""")

    fun parse(ocrResult: TextRecognitionResult): ExtractedReceiptData {
        val storeName = extractStoreName(ocrResult)
        val totalResult = extractTotalWithConfidence(ocrResult)
        val date = extractDate(ocrResult.fullText)
        val cardLastFour = extractCardLastFour(ocrResult.fullText)

        return ExtractedReceiptData(
            storeName = storeName,
            totalAmount = totalResult?.amount,
            totalConfidence = totalResult?.confidence ?: 0f,
            date = date,
            cardLastFour = cardLastFour,
            rawText = ocrResult.fullText,
        )
    }

    /**
     * Master total extraction that tries spatial analysis first, then text-based keyword search,
     * then a context-aware fallback. Returns the best candidate with a confidence score.
     */
    internal fun extractTotalWithConfidence(ocrResult: TextRecognitionResult): ScoredAmount? {
        val allLines = ocrResult.blocks.flatMap { it.lines }
        val totalLineCount = allLines.size

        // 1. Try spatial extraction (highest confidence source)
        val spatialResult = extractTotalAmountSpatially(ocrResult.blocks)
        if (spatialResult != null) return spatialResult

        // 2. Try keyword-based text extraction
        val textResult = extractTotalAmountFromText(ocrResult.fullText, totalLineCount)
        if (textResult != null) return textResult

        // 3. Context-aware fallback (lower confidence)
        return extractFallbackAmount(ocrResult)
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
            // Only strip "STORE" when it's followed by a number/hash (e.g., "STORE #123")
            .replace(Regex("\\bSTORE\\s*#?\\d+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bLOC(ATION)?\\s*#?\\d+", RegexOption.IGNORE_CASE), "")
            .trim()
            .replace(Regex("\\s+"), " ")
        return cleaned.ifEmpty { raw.trim() }
    }

    /** Returns true if the line text contains a negative/promotional keyword. */
    internal fun containsNegativeKeyword(text: String): Boolean {
        return negativePattern.containsMatchIn(text)
    }

    /**
     * Spatial total extraction: finds lines containing a TOTAL keyword, then searches nearby
     * lines (same row or immediately below, using bounding box coordinates) for a dollar amount.
     * Lines containing negative keywords are excluded.
     */
    internal fun extractTotalAmountSpatially(blocks: List<TextBlock>): ScoredAmount? {
        val allLines = blocks.flatMap { it.lines }
        if (allLines.all { it.boundingBox == null }) return null

        // Find total keyword lines, excluding those with negative keywords
        val totalLines = allLines
            .filter { totalKeyword.containsMatchIn(it.text) && !containsNegativeKeyword(it.text) }
            .reversed() // search from bottom for the final total

        val totalLineCount = allLines.size

        for (totalLine in totalLines) {
            var confidence = 0.40f // base keyword match
            val lineIndex = allLines.indexOf(totalLine)

            // Position bonus: bottom third of receipt
            if (totalLineCount > 0 && lineIndex >= totalLineCount * 2 / 3) {
                confidence += 0.15f
            } else if (totalLineCount > 0 && lineIndex >= totalLineCount / 2) {
                confidence += 0.05f
            }

            // "GRAND" prefix bonus
            if (totalLine.text.contains("grand", ignoreCase = true)) {
                confidence += 0.10f
            }

            // OCR confidence bonus
            if ((totalLine.confidence ?: 0f) >= 0.85f) {
                confidence += 0.10f
            }

            // 1. Check if the total amount is on the same line as the keyword.
            val sameLineMatch = amountPattern.find(totalLine.text)
            if (sameLineMatch != null) {
                val amount = parseMilliunits(sameLineMatch.groupValues[1]) ?: continue
                // Dollar sign bonus
                if (totalLine.text.contains("$")) confidence += 0.05f
                return ScoredAmount(amount, confidence.coerceAtMost(1f))
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
                if (match != null) {
                    val amount = parseMilliunits(match.groupValues[1]) ?: continue
                    confidence += 0.15f // spatial alignment bonus
                    if (nearbyAmount.text.contains("$")) confidence += 0.05f
                    return ScoredAmount(amount, confidence.coerceAtMost(1f))
                }
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
     * Lines containing negative keywords are skipped.
     */
    internal fun extractTotalAmountFromText(text: String, totalLineCount: Int): ScoredAmount? {
        val lines = text.lines()

        val totalPatterns = listOf(
            Regex("(?i)(?<!\\w)(?:GRAND\\s*)?TOTAL\\s*:?\\s*\\$?\\s*([\\d,]+\\.\\d{2})"),
            Regex("(?i)(?<!\\w)AMOUNT\\s*DUE\\s*:?\\s*\\$?\\s*([\\d,]+\\.\\d{2})"),
            Regex("(?i)(?<!\\w)BALANCE\\s*DUE\\s*:?\\s*\\$?\\s*([\\d,]+\\.\\d{2})"),
        )

        // Search from bottom up (the final total is usually near the bottom)
        for ((lineIndex, line) in lines.withIndex().toList().reversed()) {
            // Skip lines with promotional/negative keywords
            if (containsNegativeKeyword(line)) continue

            for (pattern in totalPatterns) {
                val match = pattern.find(line) ?: continue
                val amountStr = match.groupValues[1].replace(",", "")
                val amount = try {
                    MilliunitConverter.dollarsToMilliunits(BigDecimal(amountStr))
                } catch (e: NumberFormatException) {
                    continue
                }

                var confidence = 0.40f // keyword match
                if (totalLineCount > 0 && lineIndex >= totalLineCount * 2 / 3) confidence += 0.15f
                else if (totalLineCount > 0 && lineIndex >= totalLineCount / 2) confidence += 0.05f
                if (line.contains("grand", ignoreCase = true)) confidence += 0.10f
                if (line.contains("$")) confidence += 0.05f
                return ScoredAmount(amount, confidence.coerceAtMost(1f))
            }
        }

        return null
    }

    /**
     * Kept for backward compatibility. Delegates to the text-based extraction without confidence.
     */
    internal fun extractTotalAmount(text: String): Long? {
        return extractTotalAmountFromText(text, text.lines().size)?.amount
    }

    /**
     * Context-aware fallback when no total keyword is found. Instead of blindly taking the
     * largest amount, applies filtering and position-based scoring.
     */
    internal fun extractFallbackAmount(ocrResult: TextRecognitionResult): ScoredAmount? {
        val allLines = ocrResult.blocks.flatMap { it.lines }
        val totalLineCount = allLines.size

        data class Candidate(val amount: Long, val lineIndex: Int, val hasDollarSign: Boolean, val lineText: String)

        val candidates = mutableListOf<Candidate>()

        for ((lineIndex, line) in allLines.withIndex()) {
            // Skip lines with negative/promotional keywords
            if (containsNegativeKeyword(line.text)) continue

            // Skip lines that look like individual items (long description + small amount)
            // These typically have significant non-numeric text before the amount
            val trimmed = line.text.trim()
            val amountMatch = amountPattern.find(trimmed) ?: continue
            val textBeforeAmount = trimmed.substring(0, amountMatch.range.first).trim()

            // If there's a long description before the amount and it doesn't look like a total line,
            // it's probably a line item — skip it for total detection
            val looksLikeLineItem = textBeforeAmount.length > 10 &&
                !totalKeyword.containsMatchIn(textBeforeAmount)
            if (looksLikeLineItem) continue

            val amount = parseMilliunits(amountMatch.groupValues[1]) ?: continue
            candidates.add(Candidate(amount, lineIndex, trimmed.contains("$"), trimmed))
        }

        if (candidates.isEmpty()) return null

        // Score each candidate: position + amount magnitude (mild preference for larger)
        val maxAmount = candidates.maxOf { it.amount }
        val best = candidates.maxByOrNull { candidate ->
            var score = 0.0
            // Position: bottom third gets a big boost
            if (totalLineCount > 0 && candidate.lineIndex >= totalLineCount * 2 / 3) score += 3.0
            else if (totalLineCount > 0 && candidate.lineIndex >= totalLineCount / 2) score += 1.0
            // Mild preference for larger amounts (normalized 0–1)
            if (maxAmount > 0) score += (candidate.amount.toDouble() / maxAmount) * 1.0
            // Dollar sign
            if (candidate.hasDollarSign) score += 0.5
            score
        } ?: return null

        // Fallback confidence is always lower since no keyword was found
        var confidence = 0.15f
        if (totalLineCount > 0 && best.lineIndex >= totalLineCount * 2 / 3) confidence += 0.10f
        if (best.hasDollarSign) confidence += 0.05f

        return ScoredAmount(best.amount, confidence.coerceAtMost(1f))
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

/** A dollar amount with an associated confidence score (0.0–1.0). */
data class ScoredAmount(
    val amount: Long,
    val confidence: Float,
)
