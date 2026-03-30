package com.receiptscanner.domain.usecase

import android.graphics.Bitmap
import com.receiptscanner.data.ocr.EntityExtractionHelper
import com.receiptscanner.data.ocr.ImagePreprocessor
import com.receiptscanner.data.ocr.MlKitTextRecognizer
import com.receiptscanner.data.ocr.ReceiptParser
import com.receiptscanner.data.ocr.TextRecognitionResult
import com.receiptscanner.domain.model.ExtractedReceiptData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ExtractReceiptDataUseCaseTest {

    private val textRecognizer: MlKitTextRecognizer = mockk()
    private val receiptParser: ReceiptParser = mockk()
    private val entityExtractor: EntityExtractionHelper = mockk()
    private val imagePreprocessor: ImagePreprocessor = mockk()
    private val bitmap: Bitmap = mockk(relaxed = true)
    private val processedBitmap: Bitmap = mockk(relaxed = true)

    private lateinit var useCase: ExtractReceiptDataUseCase

    @BeforeEach
    fun setUp() {
        useCase = ExtractReceiptDataUseCase(
            textRecognizer = textRecognizer,
            receiptParser = receiptParser,
            entityExtractor = entityExtractor,
            imagePreprocessor = imagePreprocessor,
        )
    }

    @Test
    fun `keeps parsed total when entity extractor disagrees`() = runTest {
        val ocrResult = TextRecognitionResult(
            fullText = "raw text",
            blocks = emptyList(),
        )
        val parsed = ExtractedReceiptData(
            storeName = "COSTGO.",
            totalAmount = 38_270L,
            totalConfidence = 0.2f,
            date = LocalDate.of(2022, 11, 16),
            cardLastFour = null,
            rawText = ocrResult.fullText,
        )

        coEvery { imagePreprocessor.preprocess(bitmap) } returns processedBitmap
        coEvery { textRecognizer.recognizeText(processedBitmap, 90) } returns ocrResult
        every { receiptParser.parse(ocrResult) } returns parsed
        coEvery { entityExtractor.extract(ocrResult.fullText) } returns EntityExtractionHelper.ExtractionResult(
            date = LocalDate.of(2026, 3, 30),
            totalAmount = 3_819_000L,
            cardLastFour = null,
        )

        val result = useCase(bitmap, rotationDegrees = 90)

        assertTrue(result.isSuccess)
        assertEquals(38_270L, result.getOrThrow().totalAmount)
        assertEquals(0.2f, result.getOrThrow().totalConfidence)
    }

    @Test
    fun `mergeParsedAndEntityData keeps parsed total when entity extractor disagrees`() {
        val parsed = ExtractedReceiptData(
            storeName = "COSTCO",
            totalAmount = 38_270L,
            totalConfidence = 0.2f,
            date = LocalDate.of(2022, 11, 16),
            cardLastFour = null,
            rawText = "raw text",
        )
        val entities = EntityExtractionHelper.ExtractionResult(
            date = LocalDate.of(2026, 3, 30),
            totalAmount = 3_819_000L,
            cardLastFour = null,
        )

        val merged = mergeParsedAndEntityData(parsed, entities)

        assertEquals(38_270L, merged.totalAmount)
        assertEquals(0.2f, merged.totalConfidence)
    }

    @Test
    fun `mergeParsedAndEntityData fills missing total from entity extractor`() {
        val parsed = ExtractedReceiptData(
            storeName = "COSTCO",
            totalAmount = null,
            totalConfidence = 0.1f,
            date = LocalDate.of(2022, 11, 16),
            cardLastFour = null,
            rawText = "raw text",
        )
        val entities = EntityExtractionHelper.ExtractionResult(
            date = LocalDate.of(2022, 11, 16),
            totalAmount = 38_270L,
            cardLastFour = "0784",
        )

        val merged = mergeParsedAndEntityData(parsed, entities)

        assertEquals(38_270L, merged.totalAmount)
        assertEquals(0.25f, merged.totalConfidence)
        assertEquals("0784", merged.cardLastFour)
    }
}
