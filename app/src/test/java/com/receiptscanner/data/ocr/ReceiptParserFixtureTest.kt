package com.receiptscanner.data.ocr

import com.receiptscanner.testing.receiptfixtures.OcrResultSerializer
import com.receiptscanner.testing.receiptfixtures.ReceiptFixture
import com.receiptscanner.testing.receiptfixtures.ReceiptFixtureLabelsParser
import com.receiptscanner.testing.receiptfixtures.ReceiptFixtureScorecard
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import com.receiptscanner.domain.model.ExtractedReceiptData
import java.io.File

/**
 * JVM-only parser regression test that replays cached ML Kit OCR results (JSON) through
 * [ReceiptParser] without requiring an Android emulator. This enables sub-second iteration
 * on parsing heuristics.
 *
 * ## Workflow
 *
 * 1. Run the on-device [OcrFixtureRegressionTest][com.receiptscanner.data.ocr.OcrFixtureRegressionTest]
 *    with `ocrFixture.dumpOcrResults=true` to capture ML Kit output for each fixture image.
 * 2. Pull the OCR cache from device:
 *    `adb pull /sdcard/Android/data/com.receiptscanner/files/ocr-cache/ app/src/test/resources/ocr-cache/`
 * 3. Run this test: `./gradlew testDebugUnitTest --tests "com.receiptscanner.data.ocr.ReceiptParserFixtureTest"`
 *
 * The test is skipped (via JUnit [assumeTrue]) when the OCR cache directory is absent,
 * so it won't cause failures on CI or fresh checkouts without cached data.
 */
class ReceiptParserFixtureTest {

    companion object {
        private const val OCR_CACHE_DIR = "ocr-cache"
        private const val LABELS_FILE = "images/labels.md"
    }

    @Test
    fun parserAccuracyOnCachedOcrResults() {
        val cacheDir = resourceDir(OCR_CACHE_DIR)
        assumeTrue(
            cacheDir != null && cacheDir.isDirectory,
            "Skipping: no OCR cache found at src/test/resources/$OCR_CACHE_DIR. " +
                "Run the on-device OcrFixtureRegressionTest with ocrFixture.dumpOcrResults=true first.",
        )

        val labelsText = resourceText(LABELS_FILE)
            ?: error("Missing labels file: $LABELS_FILE")
        val fixtures = ReceiptFixtureLabelsParser.parse(labelsText)
            .associateBy { it.imageName }

        val cacheFiles = cacheDir!!.listFiles { f -> f.extension == "json" }
            ?.sortedBy { it.name }
            .orEmpty()

        assumeTrue(cacheFiles.isNotEmpty(), "No .json files in OCR cache directory.")

        val parser = ReceiptParser()
        val results = mutableListOf<FixtureResult>()
        val missingFixtures = mutableListOf<String>()

        for (file in cacheFiles) {
            val imageName = file.name.removeSuffix(".ocr.json")
            val fixture = fixtures[imageName]
            if (fixture == null) {
                missingFixtures.add(imageName)
                continue
            }

            val ocrResult = OcrResultSerializer.fromJson(file.readText())
            if (ocrResult == null) {
                System.err.println("WARN: Failed to parse OCR cache: ${file.name}")
                continue
            }

            val parsed = parser.parse(ocrResult)
            results.add(FixtureResult(fixture, parsed))
        }

        check(results.isNotEmpty()) { "No fixture results to evaluate." }

        val diffs = results.map { ReceiptFixtureScorecard.compare(it.fixture, it.parsed) }
        val summary = ReceiptFixtureScorecard.summarize(diffs)
        val rendered = ReceiptFixtureScorecard.render(summary, maxDiffs = 50)

        println("=== JVM Parser Fixture Test (${results.size} receipts, parser-only — no entity merge) ===")
        println(rendered)

        if (missingFixtures.isNotEmpty()) {
            println("\nWARN: ${missingFixtures.size} cached images had no matching label in labels.md")
        }

        println("\n--- Quick Summary ---")
        println("Store:  ${formatPct(summary.storeAccuracy)}")
        println("Total:  ${formatPct(summary.totalAccuracy)}")
        println("Date:   ${formatPct(summary.dateAccuracy)}")
        println("Card:   ${formatPct(summary.cardLastFourAccuracy)}")
        println("Exact:  ${formatPct(summary.exactRecordAccuracy)}")
    }

    private fun resourceDir(path: String): File? {
        val url = javaClass.classLoader?.getResource(path) ?: return null
        return File(url.toURI()).takeIf { it.isDirectory }
    }

    private fun resourceText(path: String): String? {
        return javaClass.classLoader?.getResourceAsStream(path)
            ?.bufferedReader()
            ?.use { it.readText() }
    }

    private fun formatPct(value: Double): String = "%.1f%%".format(value * 100)

    private data class FixtureResult(
        val fixture: ReceiptFixture,
        val parsed: ExtractedReceiptData,
    )
}
