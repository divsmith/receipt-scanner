package com.receiptscanner.testing.receiptfixtures

import com.receiptscanner.domain.model.ExtractedReceiptData

data class ReceiptFixtureDiff(
    val imageName: String,
    val expected: NormalizedReceiptData,
    val actual: NormalizedReceiptData,
    val mismatchedFields: List<String>,
) {
    val isExactMatch: Boolean
        get() = mismatchedFields.isEmpty()
}

data class ReceiptFixtureSummary(
    val diffs: List<ReceiptFixtureDiff>,
    val storeAccuracy: Double,
    val totalAccuracy: Double,
    val dateAccuracy: Double,
    val cardLastFourAccuracy: Double,
    val exactRecordAccuracy: Double,
)

object ReceiptFixtureScorecard {

    fun compare(fixture: ReceiptFixture, actual: ExtractedReceiptData): ReceiptFixtureDiff {
        val normalizedExpected = ReceiptFixtureNormalizer.normalizeExpected(fixture.expected)
        val normalizedActual = ReceiptFixtureNormalizer.normalizeActual(actual)

        return ReceiptFixtureDiff(
            imageName = fixture.imageName,
            expected = normalizedExpected,
            actual = normalizedActual,
            mismatchedFields = buildList {
                if (normalizedExpected.storeKey != normalizedActual.storeKey) add("store")
                if (normalizedExpected.totalMilliunits != normalizedActual.totalMilliunits) add("total")
                if (normalizedExpected.date != normalizedActual.date) add("date")
                if (normalizedExpected.cardLastFour != normalizedActual.cardLastFour) add("cardLastFour")
            },
        )
    }

    fun summarize(diffs: List<ReceiptFixtureDiff>): ReceiptFixtureSummary {
        require(diffs.isNotEmpty()) { "Cannot summarize an empty diff set." }

        val totalCount = diffs.size.toDouble()
        fun accuracyFor(fieldName: String): Double {
            return diffs.count { fieldName !in it.mismatchedFields } / totalCount
        }

        return ReceiptFixtureSummary(
            diffs = diffs,
            storeAccuracy = accuracyFor("store"),
            totalAccuracy = accuracyFor("total"),
            dateAccuracy = accuracyFor("date"),
            cardLastFourAccuracy = accuracyFor("cardLastFour"),
            exactRecordAccuracy = diffs.count { it.isExactMatch } / totalCount,
        )
    }

    fun render(summary: ReceiptFixtureSummary, maxDiffs: Int = 25): String {
        val mismatches = summary.diffs.filterNot { it.isExactMatch }
        val lines = mutableListOf(
            "OCR fixture summary",
            "store=${formatPercent(summary.storeAccuracy)} total=${formatPercent(summary.totalAccuracy)} date=${formatPercent(summary.dateAccuracy)} card=${formatPercent(summary.cardLastFourAccuracy)} exact=${formatPercent(summary.exactRecordAccuracy)}",
            "mismatch-counts store=${summary.diffs.count { "store" in it.mismatchedFields }} total=${summary.diffs.count { "total" in it.mismatchedFields }} date=${summary.diffs.count { "date" in it.mismatchedFields }} card=${summary.diffs.count { "cardLastFour" in it.mismatchedFields }} records=${mismatches.size}",
        )

        mismatches
            .take(maxDiffs)
            .forEach { diff ->
                lines += buildString {
                    append(diff.imageName)
                    append(" mismatched: ")
                    append(diff.mismatchedFields.joinToString(", "))
                    append(" | expected=")
                    append(diff.expected)
                    append(" | actual=")
                    append(diff.actual)
                }
            }

        val omittedCount = mismatches.size - maxDiffs
        if (omittedCount > 0) {
            lines += "... $omittedCount more mismatched receipts omitted"
        }

        return lines.joinToString("\n")
    }

    private fun formatPercent(value: Double): String = "%.1f%%".format(value * 100)
}
