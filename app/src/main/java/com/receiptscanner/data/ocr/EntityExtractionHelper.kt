package com.receiptscanner.data.ocr

import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.entityextraction.MoneyEntity
import com.google.mlkit.nl.entityextraction.PaymentCardEntity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Wraps the ML Kit Entity Extraction API to pull structured data (dates, monetary amounts,
 * payment-card suffixes) directly from raw receipt text.
 *
 * The model is downloaded on first use and cached locally thereafter. All operations are
 * best-effort: if the model is unavailable or a network error occurs, null is returned so the
 * caller can fall back to the regex-based [ReceiptParser].
 */
@Singleton
class EntityExtractionHelper @Inject constructor() {

    data class ExtractionResult(
        val date: LocalDate? = null,
        val totalAmount: Long? = null,
        val cardLastFour: String? = null,
    )

    private val extractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
    )

    /**
     * Annotates [text] with ML Kit and returns the best entity matches.
     * Returns an empty [ExtractionResult] on any failure so the caller can fall back gracefully.
     */
    suspend fun extract(text: String): ExtractionResult {
        return try {
            ensureModelDownloaded()
            annotate(text)
        } catch (_: Exception) {
            ExtractionResult()
        }
    }

    /** Downloads the entity extraction model if it is not already present on the device. */
    private suspend fun ensureModelDownloaded(): Unit = suspendCancellableCoroutine { cont ->
        extractor.downloadModelIfNeeded()
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resume(Unit) } // Non-fatal: annotate will also fail cleanly
    }

    private suspend fun annotate(text: String): ExtractionResult = suspendCancellableCoroutine { cont ->
        val params = EntityExtractionParams.Builder(text).build()
        extractor.annotate(params)
            .addOnSuccessListener { annotations ->
                var date: LocalDate? = null
                var totalAmount: Long? = null
                var cardLastFour: String? = null

                for (annotation in annotations) {
                    for (entity in annotation.entities) {
                        when (entity.type) {
                            Entity.TYPE_DATE_TIME -> {
                                if (date == null) {
                                    val millis = (entity as? DateTimeEntity)?.timestampMillis
                                    if (millis != null) {
                                        date = Instant.ofEpochMilli(millis)
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDate()
                                    }
                                }
                            }
                            Entity.TYPE_MONEY -> {
                                val money = entity as? MoneyEntity ?: continue
                                // Take the largest money entity as the probable total.
                                val fractional = money.fractionalPart  // Int (non-nullable)
                                val whole = money.integerPart          // Int (non-nullable)
                                // Convert to milliunits: (whole * 100 + fractional) * 10
                                val milliUnits = (whole.toLong() * 100 + fractional) * 10L
                                if (totalAmount == null || milliUnits > totalAmount!!) {
                                    totalAmount = milliUnits
                                }
                            }
                            Entity.TYPE_PAYMENT_CARD -> {
                                if (cardLastFour == null) {
                                    val card = entity as? PaymentCardEntity ?: continue
                                    val number = card.paymentCardNumber ?: continue
                                    if (number.length >= 4) {
                                        cardLastFour = number.takeLast(4)
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }
                }

                cont.resume(ExtractionResult(date = date, totalAmount = totalAmount, cardLastFour = cardLastFour))
            }
            .addOnFailureListener {
                cont.resume(ExtractionResult())
            }
    }

    fun close() {
        extractor.close()
    }
}
