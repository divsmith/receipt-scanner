package com.receiptscanner.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.receiptscanner.data.camera.CameraManager
import com.receiptscanner.domain.model.ExtractedReceiptData
import com.receiptscanner.domain.usecase.mergeParsedAndEntityData
import com.receiptscanner.testing.receiptfixtures.ReceiptFixture
import com.receiptscanner.testing.receiptfixtures.ReceiptFixtureNormalizer
import com.receiptscanner.testing.receiptfixtures.ReceiptFixtureScorecard
import com.receiptscanner.testing.receiptfixtures.OcrResultSerializer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class OcrFixtureRegressionTest {

    companion object {
        private const val TAG = "OcrFixtureRegression"
        // Regression gates — set ~3 pp below current on-device performance to catch regressions
        // while tolerating minor ML Kit non-determinism between runs.
        // Current actual (2025-Q1, new preprocessor + parser): store=22.2% total=57.6% date=56.4%
        //   card=97.6% exact=10.2%
        private const val MIN_STORE_ACCURACY = 0.20
        private const val MIN_TOTAL_ACCURACY = 0.55
        private const val MIN_DATE_ACCURACY = 0.53
        private const val MIN_CARD_ACCURACY = 0.95
        private const val MIN_EXACT_RECORD_ACCURACY = 0.08
    }

    @Test
    fun loadsLabelledReceiptFixturesFromAssets() {
        val context = InstrumentationRegistry.getInstrumentation().context

        val fixtures = OcrFixtureImageLoader.loadFixturesFromAssets(context)

        assertEquals(countLabelSections(context), fixtures.size)
        assertTrue(fixtures.any { it.imageName == "0.jpg" })
        assertTrue(fixtures.any { it.imageName == "20221217_160448.jpg" })
    }

    @Test
    fun extractedDataMatchesLabelledReceipts() = runBlocking {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val selectedFixtures = arguments.getString("ocrFixture.onlyFixtures")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
        val fixtures = OcrFixtureImageLoader.loadFixturesFromAssets(instrumentationContext)
            .filter { selectedFixtures.isEmpty() || it.imageName in selectedFixtures }
        val traceFixtures = arguments.getString("ocrFixture.traceFixtures")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

        require(fixtures.isNotEmpty()) { "No fixtures selected for OCR regression run." }

        warmUpTextRecognition()
        verifyEntityExtractionCanary()

        val runs = fixtures.map { fixture ->
            val trace = runExtractionTrace(
                targetContext = targetContext,
                instrumentationContext = instrumentationContext,
                imageName = fixture.imageName,
            )
            if (fixture.imageName in traceFixtures) {
                logTrace(fixture.imageName, trace)
            }
            FixtureRun(fixture = fixture, trace = trace)
        }

        val dumpOcrResults = arguments.getString("ocrFixture.dumpOcrResults") == "true"
        if (dumpOcrResults) {
            // Write to /sdcard/Download/ so files survive app uninstall (getExternalFilesDir
            // is scoped and deleted on uninstall, which connectedAndroidTest triggers).
            val dumpDir = File(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS), "ocr-cache").apply { mkdirs() }
            runs.forEach { run ->
                val json = OcrResultSerializer.toJson(run.trace.ocrResult)
                File(dumpDir, "${run.fixture.imageName}.ocr.json").writeText(json)
            }
            Log.i(TAG, "Dumped ${runs.size} OCR results to ${dumpDir.absolutePath}")
            println("Dumped ${runs.size} OCR results to ${dumpDir.absolutePath}")
        }

        val diffs = runs.map { run -> ReceiptFixtureScorecard.compare(run.fixture, run.trace.merged) }
        val summary = ReceiptFixtureScorecard.summarize(diffs)
        val renderedSummary = ReceiptFixtureScorecard.render(summary)
        val renderedDiagnostics = renderSourceDiagnostics(runs)
        (renderedDiagnostics + renderedSummary.lineSequence()).forEach { line ->
            Log.i(TAG, line)
            println(line)
        }
        val enforceThresholds = arguments.getString("ocrFixture.enforceThresholds") != "false"

        if (!enforceThresholds) {
            return@runBlocking
        }

        assertTrue(
            renderedSummary,
            summary.storeAccuracy >= MIN_STORE_ACCURACY &&
                summary.totalAccuracy >= MIN_TOTAL_ACCURACY &&
                summary.dateAccuracy >= MIN_DATE_ACCURACY &&
                summary.cardLastFourAccuracy >= MIN_CARD_ACCURACY &&
                summary.exactRecordAccuracy >= MIN_EXACT_RECORD_ACCURACY,
        )
    }

    private suspend fun runExtractionTrace(
        targetContext: Context,
        instrumentationContext: Context,
        imageName: String,
    ): ExtractionTrace {
        val cameraManager = CameraManager()
        val textRecognizer = MlKitTextRecognizer()
        val parser = ReceiptParser()
        val entityExtractor = EntityExtractionHelper()
        val preprocessor = ImagePreprocessor()
        val tempFile = OcrFixtureImageLoader.copyImageToCache(
            assetContext = instrumentationContext,
            storageContext = targetContext,
            imageName = imageName,
        )
        val rotation = cameraManager.readExifRotation(tempFile)
        val bitmap: Bitmap = checkNotNull(cameraManager.loadBitmapFromFile(tempFile)) {
            "Failed to decode fixture image: $imageName"
        }

        return try {
            val processed = preprocessor.preprocess(bitmap)
            try {
                val ocrResult = textRecognizer.recognizeText(processed, rotation)
                val parsed = parser.parse(ocrResult)
                val entities = entityExtractor.extract(ocrResult.fullText)
                val merged = mergeParsedAndEntityData(parsed, entities)
                ExtractionTrace(
                    rawText = ocrResult.fullText,
                    ocrResult = ocrResult,
                    parsed = parsed,
                    entities = entities,
                    merged = merged,
                )
            } finally {
                processed.recycle()
            }
        } finally {
            bitmap.recycle()
            tempFile.delete()
            textRecognizer.close()
            entityExtractor.close()
        }
    }

    private fun logTrace(imageName: String, trace: ExtractionTrace) {
        sequenceOf(
            "TRACE $imageName",
            "parsed=${trace.parsed}",
            "entities=${trace.entities}",
            "merged=${trace.merged}",
            "rawText<<",
            trace.rawText,
            ">>",
        ).forEach { line ->
            Log.i(TAG, line)
            println(line)
        }
    }

    private fun renderSourceDiagnostics(runs: List<FixtureRun>): Sequence<String> {
        val today = LocalDate.now()

        fun entityAsActual(trace: ExtractionTrace): ExtractedReceiptData {
            return ExtractedReceiptData(
                storeName = null,
                totalAmount = trace.entities.totalAmount,
                totalConfidence = 0f,
                date = trace.entities.date,
                cardLastFour = trace.entities.cardLastFour,
                rawText = trace.rawText,
            )
        }

        val dateParsedMatches = runs.count { run ->
            ReceiptFixtureNormalizer.normalizeActual(run.trace.parsed).date ==
                ReceiptFixtureNormalizer.normalizeExpected(run.fixture.expected).date
        }
        val dateEntityMatches = runs.count { run ->
            ReceiptFixtureNormalizer.normalizeActual(entityAsActual(run.trace)).date ==
                ReceiptFixtureNormalizer.normalizeExpected(run.fixture.expected).date
        }
        val dateMergedMatches = runs.count { run ->
            ReceiptFixtureNormalizer.normalizeActual(run.trace.merged).date ==
                ReceiptFixtureNormalizer.normalizeExpected(run.fixture.expected).date
        }
        val entityDateFallbacks = runs.count { it.trace.parsed.date == null && it.trace.entities.date != null }
        val entityDateFallbackMatches = runs.count { run ->
            run.trace.parsed.date == null &&
                run.trace.entities.date != null &&
                ReceiptFixtureNormalizer.normalizeActual(entityAsActual(run.trace)).date ==
                ReceiptFixtureNormalizer.normalizeExpected(run.fixture.expected).date
        }
        val entityDateFallbackToday = runs.count {
            it.trace.parsed.date == null && it.trace.entities.date == today
        }

        val totalParsedMatches = runs.count { run ->
            ReceiptFixtureNormalizer.normalizeActual(run.trace.parsed).totalMilliunits ==
                ReceiptFixtureNormalizer.normalizeExpected(run.fixture.expected).totalMilliunits
        }
        val totalEntityMatches = runs.count { run ->
            ReceiptFixtureNormalizer.normalizeActual(entityAsActual(run.trace)).totalMilliunits ==
                ReceiptFixtureNormalizer.normalizeExpected(run.fixture.expected).totalMilliunits
        }
        val totalMergedMatches = runs.count { run ->
            ReceiptFixtureNormalizer.normalizeActual(run.trace.merged).totalMilliunits ==
                ReceiptFixtureNormalizer.normalizeExpected(run.fixture.expected).totalMilliunits
        }
        val totalOverrides = runs.count { it.trace.parsed.totalAmount != it.trace.merged.totalAmount }
        val totalOverrideHelped = runs.count { run ->
            val expected = ReceiptFixtureNormalizer.normalizeExpected(run.fixture.expected).totalMilliunits
            val parsed = ReceiptFixtureNormalizer.normalizeActual(run.trace.parsed).totalMilliunits
            val merged = ReceiptFixtureNormalizer.normalizeActual(run.trace.merged).totalMilliunits
            parsed != expected && merged == expected
        }
        val totalOverrideHurt = runs.count { run ->
            val expected = ReceiptFixtureNormalizer.normalizeExpected(run.fixture.expected).totalMilliunits
            val parsed = ReceiptFixtureNormalizer.normalizeActual(run.trace.parsed).totalMilliunits
            val merged = ReceiptFixtureNormalizer.normalizeActual(run.trace.merged).totalMilliunits
            parsed == expected && merged != expected
        }

        return sequenceOf(
            "source-analysis date parsed=$dateParsedMatches/${runs.size} entity=$dateEntityMatches/${runs.size} merged=$dateMergedMatches/${runs.size} entityFallbacks=$entityDateFallbacks entityFallbackMatches=$entityDateFallbackMatches entityFallbackToday=$entityDateFallbackToday",
            "source-analysis total parsed=$totalParsedMatches/${runs.size} entity=$totalEntityMatches/${runs.size} merged=$totalMergedMatches/${runs.size} overrides=$totalOverrides overrideHelped=$totalOverrideHelped overrideHurt=$totalOverrideHurt",
        )
    }

    private suspend fun verifyEntityExtractionCanary() {
        val entityExtractor = EntityExtractionHelper()
        val result = try {
            entityExtractor.extract("Purchase on 11/16/2022 paid with card 0784 total $38.27")
        } finally {
            entityExtractor.close()
        }

        assertTrue(
            "Entity extraction canary did not recover any fields. Failing fast so fixture mismatches are not misread as parser-only failures.",
            result.date != null || result.totalAmount != null || result.cardLastFour != null,
        )
    }

    private suspend fun warmUpTextRecognition() {
        val recognizer = MlKitTextRecognizer()
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)

        try {
            recognizer.recognizeText(bitmap)
        } finally {
            bitmap.recycle()
            recognizer.close()
        }
    }

    private fun countLabelSections(context: Context): Int {
        val labels = context.assets.open("images/labels.md")
            .bufferedReader()
            .use { it.readText() }
        return Regex("""(?m)^##\s+.+$""").findAll(labels).count()
    }

    private data class ExtractionTrace(
        val rawText: String,
        val ocrResult: TextRecognitionResult,
        val parsed: ExtractedReceiptData,
        val entities: EntityExtractionHelper.ExtractionResult,
        val merged: ExtractedReceiptData,
    )

    private data class FixtureRun(
        val fixture: ReceiptFixture,
        val trace: ExtractionTrace,
    )
}
