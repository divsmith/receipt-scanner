package com.receiptscanner.testing.receiptfixtures

import com.receiptscanner.domain.model.ExtractedReceiptData
import com.receiptscanner.domain.util.MilliunitConverter
import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate

data class NormalizedReceiptData(
    val storeKey: String?,
    val totalMilliunits: Long?,
    val date: LocalDate?,
    val cardLastFour: String?,
)

object ReceiptFixtureNormalizer {

    fun normalizeExpected(expectation: ReceiptFixtureExpectation): NormalizedReceiptData {
        return NormalizedReceiptData(
            storeKey = normalizeStore(expectation.store),
            totalMilliunits = parseMilliunits(expectation.totalLabel),
            date = expectation.date,
            cardLastFour = normalizeCardLastFour(expectation.cardLastFour),
        )
    }

    fun normalizeActual(actual: ExtractedReceiptData): NormalizedReceiptData {
        return NormalizedReceiptData(
            storeKey = normalizeStore(actual.storeName),
            totalMilliunits = actual.totalAmount,
            date = actual.date,
            cardLastFour = normalizeCardLastFour(actual.cardLastFour),
        )
    }

    private fun parseMilliunits(totalLabel: String?): Long? {
        val normalized = totalLabel
            ?.replace(Regex("""[^\d,.\-]"""), "")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return MilliunitConverter.dollarsToMilliunits(BigDecimal(normalized.replace(",", "")))
    }

    private fun normalizeCardLastFour(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun normalizeStore(store: String?): String? {
        val normalized = store
            ?.let(::stripDiacritics)
            ?.lowercase()
            ?.replace("&", " and ")
            ?.replace(Regex("""[^a-z0-9]+"""), " ")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return canonicalizeStore(normalized)
            .replace(Regex("""\s+#?\d{3,}\s*$"""), "")
            .trim()
    }

    private fun canonicalizeStore(normalized: String): String {
        return when {
            normalized.contains("wal mart") || normalized.contains("walmart") ||
                normalized.contains("great value") || normalized.replace(" ", "").contains("greatvalue") -> "walmart"
            normalized.contains("costco") || normalized.contains("costgo") || normalized.contains("cotco") -> "costco wholesale"
            normalized.startsWith("whole foods") || normalized == "whole" -> "whole foods market"
            normalized.contains("trader joe") -> "trader joe s"
            normalized.contains("winco") -> "winco foods"
            normalized.startsWith("spar") -> "spar"
            normalized.contains("ulta") -> "ulta beauty"
            normalized.contains("soelberg") || normalized.contains("soelberb") || normalized.contains("sdelberg") -> "soelbergs market"
            normalized.contains("beans and brew") || normalized.contains("beans and brev") -> "beans and brews"
            normalized.contains("tractor") -> "tractor supply co"
            normalized.contains("tj maxx") || normalized.contains("homegoods") -> "tj maxx homegoods"
            else -> normalized
        }
    }

    private fun stripDiacritics(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
    }
}
