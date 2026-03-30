package com.receiptscanner.data.ocr

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.receiptscanner.data.camera.CameraManager
import com.receiptscanner.domain.model.ExtractedReceiptData
import com.receiptscanner.domain.usecase.ExtractReceiptDataUseCase
import com.receiptscanner.testing.receiptfixtures.ReceiptFixtureScorecard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrFixtureRegressionTest {

    companion object {
        private const val MIN_STORE_ACCURACY = 0.60
        private const val MIN_TOTAL_ACCURACY = 0.65
        private const val MIN_DATE_ACCURACY = 0.65
        private const val MIN_CARD_ACCURACY = 0.75
        private const val MIN_EXACT_RECORD_ACCURACY = 0.20
    }

    @Test
    fun loadsLabelledReceiptFixturesFromAssets() {
        val context = InstrumentationRegistry.getInstrumentation().context

        val fixtures = OcrFixtureImageLoader.loadFixturesFromAssets(context)

        assertEquals(34, fixtures.size)
        assertTrue(fixtures.any { it.imageName == "0.jpg" })
        assertTrue(fixtures.any { it.imageName == "20221217_160448.jpg" })
    }

    @Test
    fun extractedDataMatchesLabelledReceipts() = runBlocking {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtures = OcrFixtureImageLoader.loadFixturesFromAssets(instrumentationContext)

        warmUpTextRecognition()
        verifyEntityExtractionCanary()

        val diffs = fixtures.map { fixture ->
            val actual = runRealExtraction(
                targetContext = targetContext,
                instrumentationContext = instrumentationContext,
                imageName = fixture.imageName,
            )
            ReceiptFixtureScorecard.compare(fixture, actual)
        }
        val summary = ReceiptFixtureScorecard.summarize(diffs)

        assertTrue(
            ReceiptFixtureScorecard.render(summary),
            summary.storeAccuracy >= MIN_STORE_ACCURACY &&
                summary.totalAccuracy >= MIN_TOTAL_ACCURACY &&
                summary.dateAccuracy >= MIN_DATE_ACCURACY &&
                summary.cardLastFourAccuracy >= MIN_CARD_ACCURACY &&
                summary.exactRecordAccuracy >= MIN_EXACT_RECORD_ACCURACY,
        )
    }

    private suspend fun runRealExtraction(
        targetContext: Context,
        instrumentationContext: Context,
        imageName: String,
    ): ExtractedReceiptData {
        val cameraManager = CameraManager()
        val textRecognizer = MlKitTextRecognizer()
        val parser = ReceiptParser()
        val entityExtractor = EntityExtractionHelper()
        val preprocessor = ImagePreprocessor()
        val useCase = ExtractReceiptDataUseCase(
            textRecognizer = textRecognizer,
            receiptParser = parser,
            entityExtractor = entityExtractor,
            imagePreprocessor = preprocessor,
        )
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
            useCase(bitmap, rotation).getOrThrow()
        } finally {
            bitmap.recycle()
            tempFile.delete()
            textRecognizer.close()
            entityExtractor.close()
        }
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
}
