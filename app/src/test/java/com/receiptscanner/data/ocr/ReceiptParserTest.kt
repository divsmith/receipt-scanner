package com.receiptscanner.data.ocr

import com.receiptscanner.domain.util.MilliunitConverter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class ReceiptParserTest {

    private lateinit var parser: ReceiptParser

    @BeforeEach
    fun setUp() {
        parser = ReceiptParser()
    }

    /**
     * Builds an [OcrResult] where every line is its own block and no spatial data is provided.
     * Used for tests that exercise text-only (fallback) logic.
     */
    private fun makeOcrResult(vararg lines: String): TextRecognitionResult {
        val blocks = lines.map { line ->
            TextBlock(
                text = line,
                lines = listOf(TextLine(text = line, boundingBox = null, confidence = 0.95f)),
                boundingBox = null,
            )
        }
        return TextRecognitionResult(
            fullText = lines.joinToString("\n"),
            blocks = blocks,
        )
    }

    /**
     * Builds a receipt whose lines have realistic bounding boxes laid out as a single-column
     * receipt printed at Y=0 downwards, with a configurable line height (font size proxy).
     *
     * @param items List of (text, lineHeight) pairs. Wider lines are treated as larger font.
     */
    private fun makeOcrResultWithBounds(vararg items: Pair<String, Int>): TextRecognitionResult {
        var currentY = 0
        val lines = items.map { (text, height) ->
            val box = BoundingBox(0, currentY, 400, currentY + height)
            currentY += height + 4 // 4px gap between lines
            TextLine(text = text, boundingBox = box, confidence = 0.95f)
        }
        val block = TextBlock(
            text = lines.joinToString("\n") { it.text },
            lines = lines,
            boundingBox = lines.first().boundingBox,
        )
        return TextRecognitionResult(
            fullText = lines.joinToString("\n") { it.text },
            blocks = listOf(block),
        )
    }

    /**
     * Builds a two-column layout typical of a receipt's totals section, where keyword labels
     * appear on the left and amounts appear spatially to the right on the same row.
     *
     * @param rows List of (labelLine, amountLine) tuples where each line already has a box set.
     */
    private fun makeTwoColumnOcrResult(rows: List<Pair<TextLine, TextLine>>): TextRecognitionResult {
        val allLines = rows.flatMap { (label, amount) -> listOf(label, amount) }
        val block = TextBlock(
            text = allLines.joinToString("\n") { it.text },
            lines = allLines,
            boundingBox = null,
        )
        return TextRecognitionResult(
            fullText = allLines.joinToString("\n") { it.text },
            blocks = listOf(block),
        )
    }

    @Nested
    inner class StoreNameExtraction {
        @Test
        fun `extracts store name from first block`() {
            val result = makeOcrResult("WALMART", "123 Main St", "TOTAL $45.67")
            assertEquals("WALMART", parser.extractStoreName(result))
        }

        @Test
        fun `skips receipt header words`() {
            val result = makeOcrResult("RECEIPT", "TARGET", "123 Main St")
            assertEquals("TARGET", parser.extractStoreName(result))
        }

        @Test
        fun `removes store numbers`() {
            val result = makeOcrResult("KROGER #1234", "123 Main St")
            assertEquals("KROGER", parser.extractStoreName(result))
        }

        @Test
        fun `returns null for empty result`() {
            val result = TextRecognitionResult("", emptyList())
            assertNull(parser.extractStoreName(result))
        }

        @Test
        fun `skips pure number lines`() {
            val result = makeOcrResult("12345", "COSTCO", "Items below")
            assertEquals("COSTCO", parser.extractStoreName(result))
        }

        @Test
        fun `skips address-like noise`() {
            val result = makeOcrResult("www.mystore.com", "MY STORE", "123 Main St")
            assertEquals("MY STORE", parser.extractStoreName(result))
        }

        @Test
        fun `spatial - prefers tallest line as store name hero text`() {
            // Large header (height=40) should win over smaller address text (height=14)
            val result = makeOcrResultWithBounds(
                "123 Main St" to 14,
                "WHOLE FOODS MARKET" to 40,
                "Organic Groceries" to 14,
            )
            assertEquals("WHOLE FOODS MARKET", parser.extractStoreName(result))
        }

        @Test
        fun `spatial - falls back to first valid line when no bounding boxes`() {
            val result = makeOcrResult("TRADER JOE'S", "123 Market Ln")
            assertEquals("TRADER JOE'S", parser.extractStoreName(result))
        }

        @Test
        fun `spatial - hero text is filtered when it is header noise`() {
            // Even if "RECEIPT" is printed largest, it should be skipped
            val result = makeOcrResultWithBounds(
                "RECEIPT" to 40,
                "SAFEWAY" to 30,
                "Your neighbourhood store" to 10,
            )
            assertEquals("SAFEWAY", parser.extractStoreName(result))
        }
    }

    @Nested
    inner class TotalAmountExtraction {
        @Test
        fun `extracts simple total`() {
            val text = "Subtotal $10.00\nTax $0.80\nTOTAL $10.80"
            val expected = MilliunitConverter.dollarsToMilliunits(BigDecimal("10.80"))
            assertEquals(expected, parser.extractTotalAmount(text))
        }

        @Test
        fun `extracts total with dollar sign`() {
            val text = "TOTAL: $45.67"
            val expected = MilliunitConverter.dollarsToMilliunits(BigDecimal("45.67"))
            assertEquals(expected, parser.extractTotalAmount(text))
        }

        @Test
        fun `extracts grand total`() {
            val text = "Subtotal $40.00\nGRAND TOTAL $42.50"
            val expected = MilliunitConverter.dollarsToMilliunits(BigDecimal("42.50"))
            assertEquals(expected, parser.extractTotalAmount(text))
        }

        @Test
        fun `extracts amount due`() {
            val text = "AMOUNT DUE: $15.99"
            val expected = MilliunitConverter.dollarsToMilliunits(BigDecimal("15.99"))
            assertEquals(expected, parser.extractTotalAmount(text))
        }

        @Test
        fun `extracts balance due`() {
            val text = "BALANCE DUE $123.45"
            val expected = MilliunitConverter.dollarsToMilliunits(BigDecimal("123.45"))
            assertEquals(expected, parser.extractTotalAmount(text))
        }

        @Test
        fun `handles comma-separated amounts`() {
            val text = "TOTAL $1,234.56"
            val expected = MilliunitConverter.dollarsToMilliunits(BigDecimal("1234.56"))
            assertEquals(expected, parser.extractTotalAmount(text))
        }

        @Test
        fun `prefers total over subtotal - takes last match from bottom`() {
            val text = "SUBTOTAL $40.00\nTax $3.20\nTOTAL $43.20"
            val expected = MilliunitConverter.dollarsToMilliunits(BigDecimal("43.20"))
            assertEquals(expected, parser.extractTotalAmount(text))
        }

        @Test
        fun `returns null for no amounts`() {
            assertNull(parser.extractTotalAmount("No amounts here"))
        }

        @Test
        fun `does not match SUBTOTAL as TOTAL`() {
            // Only "SUBTOTAL $40.00" present — should NOT match via TOTAL keyword
            val text = "SUBTOTAL $40.00\nTax $3.20"
            // The word-boundary regex should not match "TOTAL" inside "SUBTOTAL"
            assertNull(parser.extractTotalAmount(text))
        }

        @Test
        fun `skips savings lines in keyword search`() {
            val text = "SUBTOTAL $20.00\nANNUAL SAVINGS $127.54\nTOTAL $22.00"
            val expected = MilliunitConverter.dollarsToMilliunits(BigDecimal("22.00"))
            assertEquals(expected, parser.extractTotalAmount(text))
        }
    }

    @Nested
    inner class NegativeKeywordFiltering {
        @Test
        fun `detects savings keyword`() {
            assertTrue(parser.containsNegativeKeyword("ANNUAL SAVINGS $127.54"))
        }

        @Test
        fun `detects you saved keyword`() {
            assertTrue(parser.containsNegativeKeyword("YOU SAVED $42.00"))
        }

        @Test
        fun `detects discount keyword`() {
            assertTrue(parser.containsNegativeKeyword("DISCOUNT -$5.00"))
        }

        @Test
        fun `detects change due keyword`() {
            assertTrue(parser.containsNegativeKeyword("CHANGE DUE $7.53"))
        }

        @Test
        fun `does not flag total line`() {
            assertFalse(parser.containsNegativeKeyword("TOTAL $43.20"))
        }

        @Test
        fun `does not flag regular item`() {
            assertFalse(parser.containsNegativeKeyword("MILK 2% $3.99"))
        }

        @Test
        fun `case insensitive`() {
            assertTrue(parser.containsNegativeKeyword("Annual Savings $50.00"))
        }
    }

    @Nested
    inner class SpatialTotalAmountExtraction {

        @Test
        fun `spatial - finds amount on same line as TOTAL keyword`() {
            val totalLine = TextLine(
                text = "TOTAL $54.32",
                boundingBox = BoundingBox(0, 200, 400, 220),
                confidence = 0.95f,
            )
            val block = TextBlock(text = "TOTAL $54.32", lines = listOf(totalLine), boundingBox = null)
            val result = TextRecognitionResult(fullText = "TOTAL $54.32", blocks = listOf(block))
            val expected = MilliunitConverter.dollarsToMilliunits(BigDecimal("54.32"))
            assertEquals(expected, parser.extractTotalAmountSpatially(result.blocks)?.amount)
        }

        @Test
        fun `spatial - finds amount to the right of TOTAL label in two-column layout`() {
            val labelLine = TextLine(
                text = "TOTAL",
                boundingBox = BoundingBox(0, 300, 150, 320),
                confidence = 0.95f,
            )
            val amountLine = TextLine(
                text = "$78.90",
                boundingBox = BoundingBox(250, 300, 400, 320),
                confidence = 0.95f,
            )
            val rows = listOf(labelLine to amountLine)
            val ocrResult = makeTwoColumnOcrResult(rows)
            val expected = MilliunitConverter.dollarsToMilliunits(BigDecimal("78.90"))
            assertEquals(expected, parser.extractTotalAmountSpatially(ocrResult.blocks)?.amount)
        }

        @Test
        fun `spatial - finds amount directly below TOTAL label`() {
            val labelLine = TextLine(
                text = "TOTAL",
                boundingBox = BoundingBox(0, 300, 150, 320),
                confidence = 0.95f,
            )
            val amountLine = TextLine(
                text = "$99.99",
                boundingBox = BoundingBox(0, 325, 150, 345),
                confidence = 0.95f,
            )
            val block = TextBlock(
                text = "TOTAL\n$99.99",
                lines = listOf(labelLine, amountLine),
                boundingBox = null,
            )
            val ocrResult = TextRecognitionResult(fullText = "TOTAL\n$99.99", blocks = listOf(block))
            val expected = MilliunitConverter.dollarsToMilliunits(BigDecimal("99.99"))
            assertEquals(expected, parser.extractTotalAmountSpatially(ocrResult.blocks)?.amount)
        }

        @Test
        fun `spatial - returns null when no bounding boxes available`() {
            val result = makeOcrResult("TOTAL", "$50.00")
            // No bounding boxes → should return null so caller falls back to text search
            assertNull(parser.extractTotalAmountSpatially(result.blocks))
        }

        @Test
        fun `spatial - prefers final TOTAL over subtotal when multiple exist`() {
            val subtotalLabel = TextLine("SUBTOTAL", BoundingBox(0, 200, 150, 220), 0.95f)
            val subtotalAmount = TextLine("$40.00", BoundingBox(250, 200, 400, 220), 0.95f)
            val totalLabel = TextLine("TOTAL", BoundingBox(0, 260, 150, 280), 0.95f)
            val totalAmount = TextLine("$43.20", BoundingBox(250, 260, 400, 280), 0.95f)

            val block = TextBlock(
                text = "SUBTOTAL\n$40.00\nTOTAL\n$43.20",
                lines = listOf(subtotalLabel, subtotalAmount, totalLabel, totalAmount),
                boundingBox = null,
            )
            val ocrResult = TextRecognitionResult(
                fullText = "SUBTOTAL $40.00\nTOTAL $43.20",
                blocks = listOf(block),
            )
            val expected = MilliunitConverter.dollarsToMilliunits(BigDecimal("43.20"))
            assertEquals(expected, parser.extractTotalAmountSpatially(ocrResult.blocks)?.amount)
        }

        @Test
        fun `spatial - skips savings line even when it contains total-like text`() {
            val savingsLine = TextLine("TOTAL SAVINGS", BoundingBox(0, 300, 200, 320), 0.95f)
            val savingsAmount = TextLine("$50.00", BoundingBox(250, 300, 400, 320), 0.95f)
            val totalLine = TextLine("TOTAL $25.50", BoundingBox(0, 340, 400, 360), 0.95f)

            val block = TextBlock(
                text = "TOTAL SAVINGS\n$50.00\nTOTAL $25.50",
                lines = listOf(savingsLine, savingsAmount, totalLine),
                boundingBox = null,
            )
            val ocrResult = TextRecognitionResult(
                fullText = "TOTAL SAVINGS $50.00\nTOTAL $25.50",
                blocks = listOf(block),
            )
            val expected = MilliunitConverter.dollarsToMilliunits(BigDecimal("25.50"))
            assertEquals(expected, parser.extractTotalAmountSpatially(ocrResult.blocks)?.amount)
        }
    }

    @Nested
    inner class ConfidenceScoring {
        @Test
        fun `keyword match with bottom position gets high confidence`() {
            val result = makeOcrResultWithBounds(
                "MILK $3.99" to 14,
                "EGGS $4.29" to 14,
                "SUBTOTAL $8.28" to 14,
                "TAX $0.66" to 14,
                "TOTAL $8.94" to 14,
            )
            val scored = parser.extractTotalWithConfidence(result)
            assertNotNull(scored)
            assertTrue(scored!!.confidence >= 0.50f, "Expected confidence >= 0.50 but was ${scored.confidence}")
        }

        @Test
        fun `grand total gets higher confidence than plain total`() {
            val plainResult = makeOcrResult("TOTAL $43.20")
            val grandResult = makeOcrResult("GRAND TOTAL $43.20")
            val plain = parser.extractTotalWithConfidence(plainResult)
            val grand = parser.extractTotalWithConfidence(grandResult)
            assertNotNull(plain)
            assertNotNull(grand)
            assertTrue(
                grand!!.confidence > plain!!.confidence,
                "GRAND TOTAL (${grand.confidence}) should have higher confidence than TOTAL (${plain.confidence})"
            )
        }

        @Test
        fun `fallback amount gets low confidence`() {
            // No TOTAL keyword, just bare amounts
            val result = makeOcrResult("$5.00", "$8.00", "$12.50")
            val scored = parser.extractTotalWithConfidence(result)
            assertNotNull(scored)
            assertTrue(scored!!.confidence < 0.40f, "Fallback confidence should be < 0.40 but was ${scored.confidence}")
        }

        @Test
        fun `returns null when only promotional amounts present`() {
            val result = makeOcrResult("YOU SAVED $42.00", "ANNUAL SAVINGS $127.54")
            val scored = parser.extractTotalWithConfidence(result)
            // Should return null because all amounts are on negative keyword lines
            assertNull(scored)
        }
    }

    @Nested
    inner class FallbackStrategy {
        @Test
        fun `fallback excludes savings amounts`() {
            val result = makeOcrResult(
                "ITEM A $5.00",
                "ANNUAL SAVINGS $127.54",
                "$25.50",
            )
            val scored = parser.extractFallbackAmount(result)
            assertNotNull(scored)
            // Should NOT be $127.54 (savings), should be $25.50 (last bare amount)
            assertNotEquals(
                MilliunitConverter.dollarsToMilliunits(BigDecimal("127.54")),
                scored!!.amount,
            )
        }

        @Test
        fun `fallback prefers bottom of receipt`() {
            // Two bare amounts — bottom one should be preferred
            val result = makeOcrResultWithBounds(
                "$15.00" to 14,
                "Some text" to 14,
                "More text" to 14,
                "Even more" to 14,
                "$18.00" to 14,
            )
            val scored = parser.extractFallbackAmount(result)
            assertNotNull(scored)
            assertEquals(
                MilliunitConverter.dollarsToMilliunits(BigDecimal("18.00")),
                scored!!.amount,
            )
        }
    }

    @Nested
    inner class DateExtraction {
        @Test
        fun `extracts MM-DD-YYYY date`() {
            val text = "Date: 03/15/2024\nSome other text"
            assertEquals(LocalDate.of(2024, 3, 15), parser.extractDate(text))
        }

        @Test
        fun `extracts MM-DD-YY date`() {
            val text = "03/15/24 12:30 PM"
            assertEquals(LocalDate.of(2024, 3, 15), parser.extractDate(text))
        }

        @Test
        fun `extracts ISO date`() {
            val text = "Transaction date: 2024-03-15"
            assertEquals(LocalDate.of(2024, 3, 15), parser.extractDate(text))
        }

        @Test
        fun `extracts month name date`() {
            val text = "March 15, 2024"
            assertEquals(LocalDate.of(2024, 3, 15), parser.extractDate(text))
        }

        @Test
        fun `extracts abbreviated month name`() {
            val text = "Mar 15, 2024"
            assertEquals(LocalDate.of(2024, 3, 15), parser.extractDate(text))
        }

        @Test
        fun `returns null for no date`() {
            assertNull(parser.extractDate("No date here"))
        }

        @Test
        fun `extracts date with dashes`() {
            val text = "Date: 03-15-2024"
            assertEquals(LocalDate.of(2024, 3, 15), parser.extractDate(text))
        }
    }

    @Nested
    inner class CardLastFourExtraction {
        @Test
        fun `extracts from asterisk pattern`() {
            assertEquals("1234", parser.extractCardLastFour("VISA ****1234"))
        }

        @Test
        fun `extracts from x pattern`() {
            assertEquals("5678", parser.extractCardLastFour("Card: xxxx5678"))
        }

        @Test
        fun `extracts from dots pattern`() {
            assertEquals("9012", parser.extractCardLastFour("...9012"))
        }

        @Test
        fun `extracts from ending in pattern`() {
            assertEquals("3456", parser.extractCardLastFour("Card ending in 3456"))
        }

        @Test
        fun `extracts from card type prefix`() {
            assertEquals("7890", parser.extractCardLastFour("MASTERCARD 7890"))
        }

        @Test
        fun `extracts DEBIT card`() {
            assertEquals("4321", parser.extractCardLastFour("DEBIT ****4321"))
        }

        @Test
        fun `returns null when no card info`() {
            assertNull(parser.extractCardLastFour("Just some text"))
        }
    }

    @Nested
    inner class FullParsingIntegration {
        @Test
        fun `parses complete grocery receipt`() {
            val result = makeOcrResult(
                "WALMART",
                "SUPERCENTER",
                "123 Main St, Anytown, US",
                "",
                "GROCERY",
                "MILK 2% $3.99",
                "BREAD $2.49",
                "EGGS LARGE $4.29",
                "",
                "SUBTOTAL $10.77",
                "TAX $0.86",
                "TOTAL $11.63",
                "",
                "VISA ****4532",
                "03/15/2024 14:30",
            )

            val data = parser.parse(result)

            assertEquals("WALMART", data.storeName)
            assertEquals(
                MilliunitConverter.dollarsToMilliunits(BigDecimal("11.63")),
                data.totalAmount,
            )
            assertEquals(LocalDate.of(2024, 3, 15), data.date)
            assertEquals("4532", data.cardLastFour)
            assertTrue(data.rawText.contains("WALMART"))
            assertTrue(data.totalConfidence > 0f, "Should have non-zero confidence")
        }

        @Test
        fun `parses restaurant receipt`() {
            val result = makeOcrResult(
                "THE OLIVE GARDEN",
                "ITALIAN RESTAURANT",
                "",
                "Server: Mike",
                "Table 12",
                "",
                "Chicken Parm $16.99",
                "Pasta $12.99",
                "Drinks $8.00",
                "",
                "Subtotal $37.98",
                "Tax $3.04",
                "TOTAL $41.02",
                "Card ending in 8901",
            )

            val data = parser.parse(result)

            assertEquals("THE OLIVE GARDEN", data.storeName)
            assertEquals(
                MilliunitConverter.dollarsToMilliunits(BigDecimal("41.02")),
                data.totalAmount,
            )
            assertEquals("8901", data.cardLastFour)
        }

        @Test
        fun `handles minimal receipt`() {
            val result = makeOcrResult("STORE", "$25.00")

            val data = parser.parse(result)

            assertEquals("STORE", data.storeName)
            assertEquals(
                MilliunitConverter.dollarsToMilliunits(BigDecimal("25.00")),
                data.totalAmount,
            )
        }

        @Test
        fun `parses receipt with multi-line header and grand total keyword`() {
            val result = makeOcrResult(
                "COSTCO WHOLESALE",
                "Membership Warehouse",
                "1234 Bulk Ave",
                "Phone: 555-0100",
                "",
                "KIRKLAND DETERGENT    $18.99",
                "ORGANIC CHICKEN 2PK   $22.49",
                "MIXED NUTS 3LB        $14.99",
                "",
                "SUBTOTAL              $56.47",
                "TAX                    $4.52",
                "GRAND TOTAL           $60.99",
                "",
                "MASTERCARD ****7890",
                "Date: 01/20/2025",
            )

            val data = parser.parse(result)

            assertEquals("COSTCO WHOLESALE", data.storeName)
            assertEquals(
                MilliunitConverter.dollarsToMilliunits(BigDecimal("60.99")),
                data.totalAmount,
            )
            assertEquals("7890", data.cardLastFour)
            assertEquals(LocalDate.of(2025, 1, 20), data.date)
        }

        @Test
        fun `parses pharmacy receipt with balance due`() {
            val result = makeOcrResult(
                "CVS PHARMACY",
                "Health & Wellness",
                "",
                "PRESCRIPTION 1        $12.00",
                "VITAMINS               $8.49",
                "INSURANCE DISCOUNT    -$5.00",
                "",
                "BALANCE DUE           $15.49",
                "",
                "AMEX ending in 3344",
                "February 5, 2025",
            )

            val data = parser.parse(result)

            assertEquals("CVS PHARMACY", data.storeName)
            assertEquals(
                MilliunitConverter.dollarsToMilliunits(BigDecimal("15.49")),
                data.totalAmount,
            )
            assertEquals("3344", data.cardLastFour)
            assertEquals(LocalDate.of(2025, 2, 5), data.date)
        }

        @Test
        fun `receipt with savings as largest amount picks correct total`() {
            val result = makeOcrResult(
                "GROCERY MART",
                "",
                "ITEM A $5.00",
                "ITEM B $8.00",
                "",
                "SUBTOTAL $13.00",
                "TAX $1.04",
                "TOTAL $14.04",
                "",
                "ANNUAL SAVINGS $127.54",
                "THANK YOU FOR SHOPPING",
            )

            val data = parser.parse(result)

            assertEquals(
                MilliunitConverter.dollarsToMilliunits(BigDecimal("14.04")),
                data.totalAmount,
                "Should pick TOTAL $14.04, not ANNUAL SAVINGS $127.54",
            )
        }

        @Test
        fun `receipt with you saved line does not affect total`() {
            val result = makeOcrResult(
                "SUPERSTORE",
                "",
                "PRODUCE $7.99",
                "DAIRY $4.50",
                "",
                "TOTAL $12.49",
                "YOU SAVED $3.00 TODAY",
            )

            val data = parser.parse(result)

            assertEquals(
                MilliunitConverter.dollarsToMilliunits(BigDecimal("12.49")),
                data.totalAmount,
            )
        }
    }
}
