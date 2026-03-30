package com.receiptscanner.testing.receiptfixtures

import com.receiptscanner.domain.model.ExtractedReceiptData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ReceiptFixtureLabelsParserTest {

    @Test
    fun `parses every labelled receipt expectation`() {
        val fixtures = ReceiptFixtureLabelsParser.parse(
            loadResourceText("images/labels.md")
        )

        assertEquals(34, fixtures.size)
        assertEquals("Walmart", fixtures.first { it.imageName == "0.jpg" }.expected.store)
        assertEquals(LocalDate.of(2021, 2, 23), fixtures.first { it.imageName == "10.jpg" }.expected.date)
        assertNull(fixtures.first { it.imageName == "11.jpg" }.expected.date)
        assertEquals("0784", fixtures.first { it.imageName == "20221116_161833.jpg" }.expected.cardLastFour)
    }

    @Test
    fun `normalizes labels into comparable expectations`() {
        val normalized = ReceiptFixtureNormalizer.normalizeExpected(
            ReceiptFixtureExpectation(
                store = "Whole Foods Market (Sharon Rd.)",
                totalLabel = "$45.44",
                date = null,
                cardLastFour = null,
            )
        )

        assertEquals("whole foods market", normalized.storeKey)
        assertEquals(45440L, normalized.totalMilliunits)
        assertNull(normalized.date)
    }

    @Test
    fun `canonicalizes common OCR store variants to merchant keys`() {
        assertEquals(
            "walmart",
            ReceiptFixtureNormalizer.normalizeActual(
                ExtractedReceiptData(
                    storeName = "WAL·MART >%",
                    totalAmount = null,
                    totalConfidence = 0f,
                    date = null,
                    cardLastFour = null,
                    rawText = "raw",
                )
            ).storeKey
        )
        assertEquals(
            "costco wholesale",
            ReceiptFixtureNormalizer.normalizeActual(
                ExtractedReceiptData(
                    storeName = "Costgo",
                    totalAmount = null,
                    totalConfidence = 0f,
                    date = null,
                    cardLastFour = null,
                    rawText = "raw",
                )
            ).storeKey
        )
        assertEquals(
            "soelbergs market",
            ReceiptFixtureNormalizer.normalizeActual(
                ExtractedReceiptData(
                    storeName = "Soelberbs",
                    totalAmount = null,
                    totalConfidence = 0f,
                    date = null,
                    cardLastFour = null,
                    rawText = "raw",
                )
            ).storeKey
        )
    }

    @Test
    fun `compares expected labels to extracted data and reports mismatches`() {
        val diff = ReceiptFixtureScorecard.compare(
            fixture = ReceiptFixture(
                imageName = "11.jpg",
                expected = ReceiptFixtureExpectation(
                    store = "Whole Foods Market (Sharon Rd.)",
                    totalLabel = "$45.44",
                    date = null,
                    cardLastFour = null,
                ),
            ),
            actual = ExtractedReceiptData(
                storeName = "Whole Foods",
                totalAmount = 45440L,
                totalConfidence = 0.9f,
                date = LocalDate.of(2021, 2, 10),
                cardLastFour = "1234",
                rawText = "raw",
            ),
        )

        assertFalse(diff.isExactMatch)
        assertEquals(listOf("date", "cardLastFour"), diff.mismatchedFields)
    }

    @Test
    fun `summarizes aggregate field and record accuracy`() {
        val exact = ReceiptFixtureScorecard.compare(
            fixture = ReceiptFixture(
                imageName = "0.jpg",
                expected = ReceiptFixtureExpectation(
                    store = "Walmart",
                    totalLabel = "$5.11",
                    date = LocalDate.of(2010, 8, 20),
                    cardLastFour = null,
                ),
            ),
            actual = ExtractedReceiptData(
                storeName = "Walmart",
                totalAmount = 5110L,
                totalConfidence = 0.9f,
                date = LocalDate.of(2010, 8, 20),
                cardLastFour = null,
                rawText = "raw",
            ),
        )
        val mismatch = ReceiptFixtureScorecard.compare(
            fixture = ReceiptFixture(
                imageName = "11.jpg",
                expected = ReceiptFixtureExpectation(
                    store = "Whole Foods Market (Sharon Rd.)",
                    totalLabel = "$45.44",
                    date = null,
                    cardLastFour = null,
                ),
            ),
            actual = ExtractedReceiptData(
                storeName = "Whole Foods",
                totalAmount = 45440L,
                totalConfidence = 0.9f,
                date = LocalDate.of(2021, 2, 10),
                cardLastFour = "1234",
                rawText = "raw",
            ),
        )

        val summary = ReceiptFixtureScorecard.summarize(listOf(exact, mismatch))

        assertEquals(1.0, summary.storeAccuracy)
        assertEquals(1.0, summary.totalAccuracy)
        assertEquals(0.5, summary.dateAccuracy)
        assertEquals(0.5, summary.cardLastFourAccuracy)
        assertEquals(0.5, summary.exactRecordAccuracy)
    }

    private fun loadResourceText(path: String): String {
        return checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Missing test resource: $path"
        }.bufferedReader().use { it.readText() }
    }
}
