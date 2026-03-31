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
        """(?i)(?<!\w)(?:grand\s*)?total(?!\w)|amount\s*due|balance\s*due|net\s*(?:total|amount|due)|pay\s*this\s*amount"""
    )

    private val amountPattern = Regex("""\$?\s*([\d,]+\.\s*\d{2}|\d+,\d{2})(?!\d)""")
    private val payableTotalLabelPattern = Regex(
        """(?i)^(?:grand\s+total|total(?:\s+(?:amount|due|purchase|sale))?|amount\s+due|balance(?:\s+due)?|net\s+(?:total|amount|due)|pay\s+this\s+amount)(?:\s*\(?(?:incl(?:uded|\.?)?|including)\s+(?:(?:sales\s+)?tax|vat|gst|hst)\)?)?$"""
    )
    private val providerBrandLabelPattern = Regex("""(?i)^(?:visa|mastercard|mc|amex|discover)$""")
    private val guardedTenderLabelPattern = Regex("""(?i)^(?:debit|credit)$""")
    private val tenderLinePattern = Regex(
        """(?i)\b(cash|visa|mastercard|mc|amex|discover|debit|credit|tender|payment|check\b|auth\s*#?|approval|approved|balance\s+due|amount\s+tend)"""
    )
    private val taxOnlyLabelPattern = Regex("""(?i)^(?:total\s+)?(?:(?:sales\s+)?tax|vat|gst|hst)$""")
    private val storeMetadataPattern = Regex(
        """(?i)(cashier|date\b|time\b|reg\b|trans\b|transaction|subtotal|total\b|tax\b|sale\b|purchase|auth\b|invoice|member#?|entry method|trace number|signature|approved|debit\b|credit\b|visa\b|mastercard\b|amex\b|discover\b|tender\b|tend\b|payment\b)"""
    )
    private val storeBannerPattern = Regex(
        """(?i)(feedback|survey|respond\s+by|expir(?:es|ing)|points?\s+expir|chance|to\s+win|see\s+back|save\s+money|live\s+better|thank\s+you|tell\s+us|what\s+you\s+think|join\s+us|bottomless|brunch|happy\s+hour|special\s+offer|free\s+wifi|scan\s+(?:this|here|to)|download\s+our\s+app)"""
    )
    private val phonePattern = Regex("""\b\d{3}[-.\s]?\d{3}[-.\s]?\d{4}\b""")
    private val storeHeaderNoisePattern = Regex(
        """(?i)^(receipt|transaction|welcome|thank\s*you|thanks|store\s*#\d*|address|phone|tel\s*:|fax\s*:|www\.|http)"""
    )
    private val storeStaffPattern = Regex(
        """(?i)(^server\b|^guest\s*:?\s*\d|^guests?\s*:?\s*\d|^table\b|^ticket\b|^order\s*:?\s*#?\d|^order\s*#\s*:|^customer\s+name\b|^host\b|^entered\s+by|^reprint|^paid\b|^check\s*#|^cashier\s*:|^mgr\s*:|^manager\b|^items?\s+sold|^see\s+back\s+of\s+receipt|^your\s+chance|^to\s+win\b|^ID\s*[\$#:]|^welcome\b|^join\s+us\b)"""
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
    private val numericDateWithYearPattern = Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})""")
    private val numericDateWithShortYearPattern = Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2})(?!\d)""")
    private val isoDatePattern = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    // Separators are optional between month-name and day (handles "Jun22 18"),
    // and between the optional comma and year (handles "JANUARY 30,2018").
    // (?!\d) after day prevents "January 2014" from matching day=20, year=14.
    // Year accepts 2–4 digits; 2-digit years are normalized to 20xx in parseMonthNameDate.
    private val monthNameDatePattern = Regex(
        """(?i)(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)[/\-\s]*(\d{1,2})(?!\d),?[/\-\s]*(\d{2,4})(?!\d)"""
    )
    // Separator between day and month-name is optional (handles "16Feb 19", "25AUG 17").
    // Year accepts 2–4 digits; 2-digit years are normalized in parseDayFirstMonthNameDate.
    private val dayFirstMonthNamePattern = Regex(
        """(?i)\b(\d{1,2})[/\-\s]*(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)[/\-\s]+(\d{2,4})(?!\d)"""
    )
    // Compact format: "Jul15'17" or "Jul 15'17" — abbreviated month, day, apostrophe + 2-digit year
    private val compactMonthDatePattern = Regex(
        """(?i)(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s*(\d{1,2})[']\s*(\d{2})(?!\d)"""
    )
    private val dateLabelPattern = Regex("""(?i)\b(?:date|purchase\s+date|transaction\s+date)\b""")
    private val dateNoisePattern = Regex(
        """(?i)(feedback|survey|respond\s+by|expir(?:es|ing)|points?\s+expir|offer\s+ends|valid\s+through|reward|on\s+or\s+after|purchases?\s+made\s+(?:on|before|after)|returns?\s+(?:only|are|will)|for\s+returns?)"""
    )
    private val amPmTimePattern = Regex("""\d{1,2}:\d{2}(?::\d{2})?\s*(?:AM|PM)""", RegexOption.IGNORE_CASE)

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
     * box (largest rendered font) in the first several blocks — that line is almost always the
     * store header/logo text. Falls back to the first meaningful text line if no bounding boxes
     * are available (e.g., in unit tests).
     */
    internal fun extractStoreName(ocrResult: TextRecognitionResult): String? {
        if (ocrResult.blocks.isEmpty()) return null

        val candidateLines = ocrResult.blocks
            .take(8)
            .flatMap { it.lines }
            .mapIndexedNotNull { index, line ->
                val text = line.text.trim()
                if (!isValidStoreNameLine(text)) return@mapIndexedNotNull null
                StoreCandidate(
                    text = text,
                    height = line.boundingBox?.height() ?: 0,
                    confidence = line.confidence ?: 0f,
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
            .firstOrNull { it.isNotEmpty() && it.length >= 3 }
    }

    /** Returns true if the line is a plausible store-name candidate (not noise/header text). */
    private fun isValidStoreNameLine(text: String): Boolean {
        if (text.length < 3) return false
        if (text.all { it.isDigit() || it == '-' || it == '/' || it == ' ' }) return false
        if (streetAddressPattern.containsMatchIn(text)) return false
        if (cityStateZipPattern.containsMatchIn(text)) return false
        if (phonePattern.containsMatchIn(text)) return false
        if (storeHeaderNoisePattern.containsMatchIn(text)) return false
        if (storeStaffPattern.containsMatchIn(text)) return false
        if (storeMetadataPattern.containsMatchIn(text)) return false
        if (storeBannerPattern.containsMatchIn(text)) return false
        // Filter lines that start with "email" or "e-mail" label (e.g. "email : bergspar@telkomsa.net")
        if (Regex("""(?i)^\s*e[- ]?mail\s*[:\s]""").containsMatchIn(text)) return false
        // Filter email addresses (handles cases even without a label prefix)
        if (Regex("""[\w.+-]+@[\w.-]+\.\w{2,}""").containsMatchIn(text)) return false
        // Filter lines containing web domains (e.g. "walmart.com", "TractorSupply.com")
        if (Regex("""(?i)\b\w{3,}\.(?:com|net|org|co|io|gov)\b""").containsMatchIn(text)) return false
        // Filter shopping-center name lines (e.g. "THE BRICKYARD SHOP. CNTR")
        if (Regex("""(?i)\bshop(?:ping)?\s*\.?\s*(?:ctr|cntr|center|centre)\b""").containsMatchIn(text)) return false
        // Filter line items: lines with dollar amounts (e.g., "KIRKLAND DETERGENT $18.99")
        if (amountPattern.containsMatchIn(text)) return false
        // Filter lines that start with a quantity + item (menu lines like "1 Onion Naan")
        if (Regex("""^\d+\s+[A-Z]""").containsMatchIn(text) && text.length > 8) return false

        // Filter lines that are mostly dashes/symbols (decorative separators)
        val alphaNum = text.count { it.isLetterOrDigit() }
        if (alphaNum > 0 && text.length > 5 && alphaNum.toFloat() / text.length < 0.4f) return false

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

        var score = 0

        // Height bonus: capped to prevent tall non-store lines from dominating
        score += minOf(candidate.height, 40)

        // Position is the strongest signal: store names are near the top
        score += when {
            candidate.order == 0 -> 30
            candidate.order == 1 -> 25
            candidate.order <= 3 -> 18
            candidate.order <= 6 -> 10
            candidate.order <= 10 -> 4
            else -> 0
        }

        // Word count: use meaningful words (3+ chars) so OCR noise fragments like "ky", "otl"
        // don't inflate the count and penalise a real store name ("Walmart ky 2, otl" → 1 real word)
        val meaningfulWords = words.filter { w -> w.any { it.isLetter() } && w.count { it.isLetter() } >= 3 }
        score += when {
            meaningfulWords.size in 1..3 -> 15
            meaningfulWords.size in 4..5 -> 8
            else -> -15
        }

        // Penalize digit-heavy content
        if (digits == 0) score += 10 else score -= digits * 3

        // Meaningful words bonus
        if (letters > 0 && stopWordCount <= 1) score += 8
        if (words.size >= 4 && stopWordCount * 2 >= words.size) score -= 25

        // OCR confidence: low confidence lines are less reliable
        if (candidate.confidence < 0.40f) score -= 20
        else if (candidate.confidence >= 0.75f) score += 5

        // Penalize very short text (likely OCR fragments)
        if (letters <= 3) score -= 15

        // Penalize lines that look like they contain a date/time
        if (Regex("""\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}""").containsMatchIn(candidate.text)) score -= 20
        if (Regex("""\d{1,2}:\d{2}""").containsMatchIn(candidate.text)) score -= 15

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
                if (amount == 0L) continue // $0.00 is a column header or change-due, not a total
                if (totalLine.text.contains("$")) confidence += 0.05f
                return ScoredAmount(amount, confidence.coerceAtMost(1f))
            }

            val totalBox = totalLine.boundingBox ?: continue
            val totalMidY = (totalBox.top + totalBox.bottom) / 2
            val lineHeight = totalBox.height().coerceAtLeast(1)

            // Card-brand / tender labels (Visa, Debit, etc.) only pair with amounts on the same row.
            // Allowing isBelow would pick up CHANGE DUE amounts that sit below a tender row.
            val labelNorm = normalizedLabelText(totalLine.text)
            val isTenderLabel = providerBrandLabelPattern.matches(labelNorm) ||
                guardedTenderLabelPattern.matches(labelNorm)

            val candidateLines = allLines
                .filter { candidate ->
                    val box = candidate.boundingBox ?: return@filter false
                    val candidateMidY = (box.top + box.bottom) / 2
                    // Same row: right of the label, vertically aligned
                    val isRightOf = box.left > totalBox.right && kotlin.math.abs(candidateMidY - totalMidY) < lineHeight
                    // Below: within 2 line-heights (not allowed for tender/card-brand labels)
                    val isBelow = !isTenderLabel &&
                        box.top >= totalBox.bottom && box.top <= totalBox.bottom + lineHeight * 2
                    if (!(isRightOf || isBelow)) return@filter false
                    if (!amountPattern.containsMatchIn(candidate.text)) return@filter false
                    // When the amount is directly below the label, verify no tender/negative-keyword
                    // label occupies the same row to the left (e.g. "CASH TEND" row in a two-column receipt).
                    if (isBelow && !isRightOf) {
                        val candidateMidY2 = (box.top + box.bottom) / 2
                        val hasTenderLabel = allLines.any { labelLine ->
                            val labelBox = labelLine.boundingBox ?: return@any false
                            val labelMidY = (labelBox.top + labelBox.bottom) / 2
                            labelBox.right <= box.left &&
                                kotlin.math.abs(labelMidY - candidateMidY2) < lineHeight &&
                                (tenderLinePattern.containsMatchIn(labelLine.text) ||
                                    containsNegativeKeyword(labelLine.text))
                        }
                        if (hasTenderLabel) return@filter false
                    }
                    true
                }

            // Prefer same-row (right-of) candidates over below candidates: "TOTAL: $X" is more
            // reliable than a label above a column of tip suggestions.
            val sameRowCandidates = candidateLines.filter { candidate ->
                val box = candidate.boundingBox!!
                val candidateMidY = (box.top + box.bottom) / 2
                box.left > totalBox.right && kotlin.math.abs(candidateMidY - totalMidY) < lineHeight
            }
            val nearbyAmount = (sameRowCandidates.ifEmpty { candidateLines })
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
                    if (amount == 0L) continue // $0.00 is not a real total
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
            MilliunitConverter.dollarsToMilliunits(BigDecimal(normalizeDecimalSeparator(amountStr)))
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Converts an extracted amount string to a normalized decimal string.
     *  Handles US thousands-comma ("1,234.56" → "1234.56"),
     *  European comma-decimal ("146,73" → "146.73"), and
     *  OCR-split decimals ("80. 45" → "80.45"). */
    private fun normalizeDecimalSeparator(amountStr: String): String {
        val trimmed = amountStr.trim().replace(Regex("\\s+"), "")
        return if ('.' in trimmed) {
            trimmed.replace(",", "")
        } else {
            // Treat trailing NNN,DD as European decimal when no period is present
            trimmed.replace(",", ".")
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

            // Try same-line amount
            val match = amountPattern.find(line) ?: continue
            val amountLine = line
            val amountMatch = match

            val amountStr = normalizeDecimalSeparator(amountMatch.groupValues[1])
            val amount = try {
                MilliunitConverter.dollarsToMilliunits(BigDecimal(amountStr))
            } catch (e: NumberFormatException) {
                continue
            }

            var confidence = 0.40f // keyword match
            if (totalLineCount > 0 && lineIndex >= totalLineCount * 2 / 3) confidence += 0.15f
            else if (totalLineCount > 0 && lineIndex >= totalLineCount / 2) confidence += 0.05f
            if (line.contains("grand", ignoreCase = true)) confidence += 0.10f
            if (amountLine.contains("$")) confidence += 0.05f
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
     * Extract date from receipt text. Tries multiple common formats and scores candidates
     * by context (presence of "date" label, proximity to beginning of receipt, noise avoidance).
     */
    internal fun extractDate(text: String): LocalDate? {
        val lines = text.lines()
        val candidates = buildList {
            lines.forEachIndexed { lineIndex, line ->
                numericDateWithYearPattern.findAll(line).forEach { match ->
                    parseNumericDate(match, yearPattern = "yyyy")?.let { date ->
                        if (isReasonableDate(date)) {
                            add(DateCandidate(date, scoreDateCandidate(line, lineIndex, lines)))
                        }
                    }
                }
                numericDateWithShortYearPattern.findAll(line).forEach { match ->
                    parseNumericDate(match, yearPattern = "yy")?.let { date ->
                        if (isReasonableDate(date)) {
                            add(DateCandidate(date, scoreDateCandidate(line, lineIndex, lines)))
                        }
                    }
                }
                isoDatePattern.findAll(line).forEach { match ->
                    tryParse(match.value, "yyyy-MM-dd")?.let { date ->
                        if (isReasonableDate(date)) {
                            add(DateCandidate(date, scoreDateCandidate(line, lineIndex, lines) + 5))
                        }
                    }
                }
                monthNameDatePattern.findAll(line).forEach { match ->
                    parseMonthNameDate(match)?.let { date ->
                        if (isReasonableDate(date)) {
                            add(DateCandidate(date, scoreDateCandidate(line, lineIndex, lines)))
                        }
                    }
                }
                dayFirstMonthNamePattern.findAll(line).forEach { match ->
                    parseDayFirstMonthNameDate(match)?.let { date ->
                        if (isReasonableDate(date)) {
                            add(DateCandidate(date, scoreDateCandidate(line, lineIndex, lines)))
                        }
                    }
                }
                compactMonthDatePattern.findAll(line).forEach { match ->
                    parseCompactMonthDate(match)?.let { date ->
                        if (isReasonableDate(date)) {
                            add(DateCandidate(date, scoreDateCandidate(line, lineIndex, lines)))
                        }
                    }
                }
            }
        }

        return candidates.maxByOrNull { it.score }?.date
    }

    /** Reject dates that are unreasonably far in the past or in the future. */
    private fun isReasonableDate(date: LocalDate): Boolean {
        val now = LocalDate.now()
        return !date.isAfter(now.plusDays(1)) && date.year >= 2000
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
        // Normalize all separators (-, .) to / so DateTimeFormatter pattern "M/d/yy" always works
        val normalized = match.value.replace(Regex("""[.\-]"""), "/")

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
            val rawYear = match.groupValues[3].toInt()
            val year = if (rawYear < 100) 2000 + rawYear else rawYear
            val month = parseMonthName(monthStr) ?: return null
            LocalDate.of(year, month, day)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDayFirstMonthNameDate(match: MatchResult): LocalDate? {
        return try {
            val day = match.groupValues[1].toInt()
            val monthStr = match.groupValues[2]
            val rawYear = match.groupValues[3].toInt()
            val year = if (rawYear < 100) 2000 + rawYear else rawYear
            val month = parseMonthName(monthStr) ?: return null
            LocalDate.of(year, month, day)
        } catch (_: Exception) {
            null
        }
    }

    private fun scoreDateCandidate(line: String, lineIndex: Int, lines: List<String> = emptyList()): Int {
        var score = 0
        if (dateLabelPattern.containsMatchIn(line)) score += 100
        if (line.contains("time", ignoreCase = true)) score += 10
        // AM/PM clock time on the same line (e.g. "02/10/2021 07:10 PM") is a strong date signal
        if (amPmTimePattern.containsMatchIn(line)) score += 10
        if (dateNoisePattern.containsMatchIn(line)) score -= 80
        // Adjacent "Date" label: receipts often print the label on the line before or after the value
        val prevLine = if (lineIndex > 0) lines[lineIndex - 1] else ""
        val nextLine = if (lineIndex < lines.size - 1) lines[lineIndex + 1] else ""
        if (dateLabelPattern.containsMatchIn(prevLine) || dateLabelPattern.containsMatchIn(nextLine)) score += 60
        // Mild preference for dates near top or bottom of receipt (header/footer)
        score += when {
            lineIndex < 5 -> 15
            lineIndex < 15 -> 5
            else -> 3
        }
        return score
    }

    private fun parseCompactMonthDate(match: MatchResult): LocalDate? {
        return try {
            val month = parseMonthName(match.groupValues[1]) ?: return null
            val day = match.groupValues[2].toInt()
            val shortYear = match.groupValues[3].toInt()
            val year = if (shortYear >= 100) shortYear else 2000 + shortYear
            LocalDate.of(year, month, day)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract the last 4 digits of the payment card number.
     * Receipts typically show these as: ****1234, xxxx1234, XXXX1234,
     * "ending in 1234", "card ...1234", VISA ****1234, etc.
     */
    internal fun extractCardLastFour(text: String): String? {
        // Normalize spaced digits inside masked account numbers: "XXXX34 26" → "XXXX3426"
        val normalizedText = text.replace(Regex("""([*xX]{4,}\d+)\s+(\d+)"""), "$1$2")

        val patterns = listOf(
            Regex("[*xX]{4}\\s*(\\d{4})"),
            Regex("[*xX]{2,}\\s*(\\d{4})"),
            Regex("[.…]{2,}\\s*(\\d{4})"),
            Regex("[-]{4,}\\s*(\\d{4})"),
            Regex("(?i)ending\\s+in\\s+(\\d{4})"),
            Regex("(?i)card\\s*:?\\s*\\*{0,4}\\s*(\\d{4})"),
            Regex("(?i)(?:VISA|MASTERCARD|MC|AMEX|DISCOVER|DEBIT|CREDIT)(?:\\s+(?:CREDIT|DEBIT))?[-\\s]+\\*{0,4}\\s*(\\d{4})"),
            Regex("(?i)account\\s*[:#]?\\s*[xX*]*\\s*(\\d{4})(?!\\d)"),
        )

        for (pattern in patterns) {
            val match = pattern.find(normalizedText)
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
    val confidence: Float,
    val order: Int,
)

private data class DateCandidate(
    val date: LocalDate,
    val score: Int,
)
