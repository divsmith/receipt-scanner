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
        fun `falls back to largest dollar amount`() {
            val text = "Item 1 $5.00\nItem 2 $8.00\nItem 3 $12.50\n$25.50"
            val expected = MilliunitConverter.dollarsToMilliunits(BigDecimal("25.50"))
            assertEquals(expected, parser.extractTotalAmount(text))
        }

        @Test
        fun `returns null for no amounts`() {
            assertNull(parser.extractTotalAmount("No amounts here"))
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
    }
}
