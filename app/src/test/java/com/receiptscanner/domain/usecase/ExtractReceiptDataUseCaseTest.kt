package com.receiptscanner.domain.usecase

import android.graphics.Bitmap
import com.receiptscanner.data.local.UserPreferencesManager
import com.receiptscanner.data.ocr.CloudOcrProvider
import com.receiptscanner.data.ocr.EntityExtractionHelper
import com.receiptscanner.data.ocr.LocalOcrProvider
import com.receiptscanner.domain.model.ExtractedReceiptData
import com.receiptscanner.domain.model.OcrMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ExtractReceiptDataUseCaseTest {

    private val localOcrProvider: LocalOcrProvider = mockk()
    private val cloudOcrProvider: CloudOcrProvider = mockk()
    private val userPreferencesManager: UserPreferencesManager = mockk()
    private val bitmap: Bitmap = mockk(relaxed = true)

    private lateinit var useCase: ExtractReceiptDataUseCase

    @BeforeEach
    fun setUp() {
        useCase = ExtractReceiptDataUseCase(
            localOcrProvider = localOcrProvider,
            cloudOcrProvider = cloudOcrProvider,
            userPreferencesManager = userPreferencesManager,
        )
    }

    @Test
    fun `delegates to local provider when OCR mode is LOCAL`() = runTest {
        every { userPreferencesManager.ocrMode } returns flowOf(OcrMode.LOCAL)
        coEvery { localOcrProvider.extract(bitmap, 90) } returns Result.success(
            ExtractedReceiptData(
                storeName = "COSTCO",
                totalAmount = 38_270L,
                totalConfidence = 0.9f,
                date = LocalDate.of(2022, 11, 16),
                cardLastFour = null,
                rawText = "raw text",
            )
        )

        useCase(bitmap, rotationDegrees = 90)

        coVerify(exactly = 1) { localOcrProvider.extract(bitmap, 90) }
        coVerify(exactly = 0) { cloudOcrProvider.extract(any(), any()) }
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
