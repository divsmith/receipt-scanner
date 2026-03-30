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
    private val payableTotalLabelPattern = Regex(
        """(?i)^(?:grand\s+total|total|amount\s+due|balance(?:\s+due)?)(?:\s*\(?(?:incl(?:uded|\.?)?|including)\s+(?:(?:sales\s+)?tax|vat|gst|hst)\)?)?$"""
    )
    private val providerBrandLabelPattern = Regex("""(?i)^(?:visa|mastercard|mc|amex|discover)$""")
    private val guardedTenderLabelPattern = Regex("""(?i)^(?:debit|credit)$""")
    private val taxOnlyLabelPattern = Regex("""(?i)^(?:total\s+)?(?:(?:sales\s+)?tax|vat|gst|hst)$""")
    private val storeMetadataPattern = Regex(
        """(?i)(cashier|date\b|time\b|reg\b|trans\b|transaction|subtotal|total\b|tax\b|sale\b|purchase|auth\b|invoice|member#?|entry method|trace number|signature|approved|debit\b|credit\b|visa\b|mastercard\b|amex\b|discover\b)"""
    )
    private val storeBannerPattern = Regex(
        """(?i)(feedback|survey|respond\s+by|expir(?:es|ing)|points?\s+expir|chance|to\s+win|see\s+back|save\s+money|live\s+better|thank\s+you|tell\s+us|what\s+you\s+think)"""
    )
    private val streetAddressPattern = Regex(
        """(?i)\b\d+\s+(?:[a-z0-9.'-]+\s+){0,4}(?:st|street|ave|avenida|avenue|rd|road|blvd|boulevard|dr|drive|ln|lane|way|hwy|highway|pkwy|parkway|center|centre|ctr|plaza|plz)\b"""
    )
    private val cityStateZipPattern = Regex(
        """(?i)\b[A-Z][A-Z\s.'-]*,\s*[A-Z]{2}\s+\d{5}(?:-\d{4})?\b"""
    )
    private val storeStopWords = setOf(
        "a", "an", "and", "back", "for", "of", "on", "or", "our", "please",
        "receipt", "recelpt", "see", "tell", "the", "think", "to", "us", "what", "win", "you", "your",
    )
    private val numericDateWithYearPattern = Regex("""(\d{1,2})[/\-](\d{1,2})[/\-](\d{4})""")
    private val numericDateWithShortYearPattern = Regex("""(\d{1,2})[/\-](\d{1,2})[/\-](\d{2})(?!\d)""")
    private val isoDatePattern = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val monthNameDatePattern = Regex(
        """(?i)(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+(\d{1,2}),?\s+(\d{4})"""
    )
    private val dateLabelPattern = Regex("""(?i)\b(?:date|purchase\s+date|transaction\s+date)\b""")
    private val dateNoisePattern = Regex(
        """(?i)(feedback|survey|respond\s+by|expir(?:es|ing)|points?\s+expir|offer\s+ends|valid\s+through|reward)"""
    )

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

        val candidateLines = ocrResult.blocks
            .take(3)
            .flatMap { it.lines }
            .mapIndexedNotNull { index, line ->
                val text = line.text.trim()
                if (!isValidStoreNameLine(text)) return@mapIndexedNotNull null
                StoreCandidate(
                    text = text,
                    height = line.boundingBox?.height() ?: 0,
                    order = index,
                )
            }

        val bestCandidate = candidateLines.maxByOrNull(::scoreStoreCandidate)
        if (bestCandidate != null) {
            return cleanStoreName(bestCandidate.text)
        }

        return ocrResult.blocks
            .asSequence()
            .flatMap { it.lines.asSequence() }
            .map { it.text.trim() }
            .firstOrNull { it.isNotEmpty() }
    }

    /** Returns true if the line is a plausible store-name candidate (not noise/header text). */
    private fun isValidStoreNameLine(text: String): Boolean {
        if (text.length < 3) return false
        if (text.all { it.isDigit() || it == '-' || it == '/' || it == ' ' }) return false
        if (streetAddressPattern.containsMatchIn(text)) return false
        if (cityStateZipPattern.containsMatchIn(text)) return false
        if (Regex("""\b\d{3}[-.\s]?\d{3}[-.\s]?\d{4}\b""").containsMatchIn(text)) return false
        if (text.matches(Regex("""(?i)(receipt|transaction|welcome|thank\s*you|thanks|store\s*#\d*|address|phone|tel|www\.|http).*"""))) return false
        if (Regex("""(?i)(mgr\s*:|manager|see\s+back\s+of\s+receipt|your\s+chance|to\s+win\b|survey|feedback)""").containsMatchIn(text)) return false
        if (storeMetadataPattern.containsMatchIn(text)) return false
        if (storeBannerPattern.containsMatchIn(text)) return false

        val normalizedText = stripStoreNumberSuffix(text)
        val words = normalizedText.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val stopWordCount = words.count { it.lowercase() in storeStopWords }
        if (words.size >= 4 && stopWordCount * 2 >= words.size) return false

        val letters = normalizedText.count { it.isLetter() }
        val digits = normalizedText.count { it.isDigit() }
        if (letters > 0 && digits > 0 && digits * 2 >= letters) return false
        return true
    }

    private fun scoreStoreCandidate(candidate: StoreCandidate): Int {
        val normalizedText = stripStoreNumberSuffix(candidate.text)
        val words = normalizedText.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val letters = normalizedText.count { it.isLetter() }
        val digits = normalizedText.count { it.isDigit() }
        val stopWordCount = words.count { it.lowercase() in storeStopWords }

        var score = candidate.height * 2
        score += when (candidate.order) {
            0 -> 15
            1 -> 8
            2 -> 4
            else -> 0
        }
        score += when {
            words.size <= 3 -> 20
            words.size <= 5 -> 10
            else -> -10
        }
        if (digits == 0) score += 10 else score -= digits * 2
        if (letters > 0 && stopWordCount <= 1) score += 8
        if (words.size >= 4 && stopWordCount * 2 >= words.size) score -= 25
        return score
    }

    private fun stripStoreNumberSuffix(text: String): String {
        return text
            .replace(Regex("""\s*#\s*\d+\b"""), "")
            .replace(Regex("""\b(?:store|loc(?:ation)?)\s*#?\d+\b""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun cleanStoreName(raw: String): String {
        val cleaned = raw
            .replace(Regex("""\s*#\s*\d+\b"""), "")
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
     * Spatial total extraction: finds payable-total label lines, then searches nearby lines
     * (same row or immediately below, using bounding box coordinates) for a dollar amount.
     * Lines containing negative keywords are excluded.
     */
    internal fun extractTotalAmountSpatially(blocks: List<TextBlock>): ScoredAmount? {
        val allLines = blocks.flatMap { it.lines }
        if (allLines.all { it.boundingBox == null }) return null

        val totalLineCount = allLines.size

        for ((lineIndex, totalLine) in allLines.withIndex().toList().reversed()) {
            if (!isPayableTotalLabel(totalLine.text, lineIndex, totalLineCount)) continue

            var confidence = 0.40f // base keyword match

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

            val sameLineMatch = amountPattern.find(totalLine.text)
            if (sameLineMatch != null) {
                val amount = parseMilliunits(sameLineMatch.groupValues[1]) ?: continue
                if (totalLine.text.contains("$")) confidence += 0.05f
                return ScoredAmount(amount, confidence.coerceAtMost(1f))
            }

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

    private fun normalizedLabelText(text: String): String {
        return amountPattern.replace(text, " ")
            .replace(Regex("""[:\-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun isPayableTotalLabel(text: String, lineIndex: Int, totalLineCount: Int): Boolean {
        if (containsNegativeKeyword(text)) return false

        val labelText = normalizedLabelText(text)
        if (labelText.isBlank()) return false
        if (taxOnlyLabelPattern.matches(labelText)) return false
        if (payableTotalLabelPattern.matches(labelText)) return true
        if (providerBrandLabelPattern.matches(labelText)) return true

        return guardedTenderLabelPattern.matches(labelText) && lineIndex >= totalLineCount / 2
    }

    /**
     * Extract total amount by looking for payable-total labels like TOTAL, AMOUNT DUE,
     * BALANCE, or provider/tender rows followed by a dollar amount.
     * Takes the LAST matching label to avoid subtotals.
     */
    internal fun extractTotalAmountFromText(text: String, totalLineCount: Int): ScoredAmount? {
        val lines = text.lines()

        for ((lineIndex, line) in lines.withIndex().toList().reversed()) {
            if (!isPayableTotalLabel(line, lineIndex, totalLineCount)) continue

            val match = amountPattern.find(line) ?: continue
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
        val candidates = buildList {
            text.lines().forEachIndexed { lineIndex, line ->
                numericDateWithYearPattern.findAll(line).forEach { match ->
                    parseNumericDate(match, yearPattern = "yyyy")?.let { date ->
                        add(DateCandidate(date, scoreDateCandidate(line, lineIndex)))
                    }
                }
                numericDateWithShortYearPattern.findAll(line).forEach { match ->
                    parseNumericDate(match, yearPattern = "yy")?.let { date ->
                        add(DateCandidate(date, scoreDateCandidate(line, lineIndex)))
                    }
                }
                isoDatePattern.findAll(line).forEach { match ->
                    tryParse(match.value, "yyyy-MM-dd")?.let { date ->
                        add(DateCandidate(date, scoreDateCandidate(line, lineIndex) + 5))
                    }
                }
                monthNameDatePattern.findAll(line).forEach { match ->
                    parseMonthNameDate(match)?.let { date ->
                        add(DateCandidate(date, scoreDateCandidate(line, lineIndex)))
                    }
                }
            }
        }

        return candidates.maxByOrNull { it.score }?.date
    }

    private fun parseMonthName(name: String): Int? {
        return when (name.lowercase().take(3)) {
            "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4
            "may" -> 5; "jun" -> 6; "jul" -> 7; "aug" -> 8
            "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
            else -> null
        }
    }

    private fun tryParse(value: String, pattern: String): LocalDate? {
        return try {
            LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern))
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseNumericDate(match: MatchResult, yearPattern: String): LocalDate? {
        val first = match.groupValues[1].toInt()
        val second = match.groupValues[2].toInt()
        val normalized = match.value.replace("-", "/")

        return when {
            first > 12 -> tryParse(normalized, "d/M/$yearPattern")
            second > 12 -> tryParse(normalized, "M/d/$yearPattern")
            else -> tryParse(normalized, "M/d/$yearPattern") ?: tryParse(normalized, "d/M/$yearPattern")
        }
    }

    private fun parseMonthNameDate(match: MatchResult): LocalDate? {
        return try {
            val monthStr = match.groupValues[1]
            val day = match.groupValues[2].toInt()
            val year = match.groupValues[3].toInt()
            val month = parseMonthName(monthStr) ?: return null
            LocalDate.of(year, month, day)
        } catch (_: Exception) {
            null
        }
    }

    private fun scoreDateCandidate(line: String, lineIndex: Int): Int {
        var score = 0
        if (dateLabelPattern.containsMatchIn(line)) score += 100
        if (line.contains("time", ignoreCase = true)) score += 10
        if (dateNoisePattern.containsMatchIn(line)) score -= 80
        score += when {
            lineIndex < 5 -> 20
            lineIndex < 12 -> 10
            else -> 0
        }
        score -= lineIndex
        return score
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

private data class StoreCandidate(
    val text: String,
    val height: Int,
    val order: Int,
)

private data class DateCandidate(
    val date: LocalDate,
    val score: Int,
)
