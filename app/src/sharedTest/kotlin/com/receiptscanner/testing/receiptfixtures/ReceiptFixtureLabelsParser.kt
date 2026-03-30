package com.receiptscanner.testing.receiptfixtures

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object ReceiptFixtureLabelsParser {

    private val sectionPattern = Regex(
        pattern = """(?ms)^## ([^\n]+)\n\n\| Field \| Value \|\n\|---\|---\|\n(.*?)(?=^---\s*$|\z)"""
    )

    fun parse(markdown: String): List<ReceiptFixture> {
        return sectionPattern.findAll(markdown)
            .map { match ->
                val imageName = match.groupValues[1].trim()
                val fields = parseFields(match.groupValues[2])

                ReceiptFixture(
                    imageName = imageName,
                    expected = ReceiptFixtureExpectation(
                        store = fields["Store"].toNullIfNotAvailable(),
                        totalLabel = fields["Total"].toNullIfNotAvailable(),
                        date = fields["Date"].toNullIfNotAvailable()?.let(::parseDate),
                        cardLastFour = fields["Card Last 4"].toNullIfNotAvailable(),
                    ),
                )
            }
            .toList()
    }

    private fun parseFields(tableBody: String): Map<String, String> {
        return tableBody
            .lineSequence()
            .filter { it.startsWith("|") }
            .associate { row ->
                val columns = row.trim('|').split('|').map { it.trim() }
                require(columns.size == 2) { "Malformed label row: $row" }
                columns[0] to columns[1]
            }
    }

    private fun String?.toNullIfNotAvailable(): String? {
        return this
            ?.trim()
            ?.takeUnless { it.equals("N/A", ignoreCase = true) }
    }

    private fun parseDate(value: String): LocalDate {
        val normalized = value.replace("-", "/")
        return tryParse(normalized, "M/d/yyyy")
            ?: tryParse(normalized, "M/d/yy")
            ?: tryParse(normalized, "d/M/yyyy")
            ?: tryParse(normalized, "d/M/yy")
            ?: error("Unsupported label date format: $value")
    }

    private fun tryParse(value: String, pattern: String): LocalDate? {
        return try {
            LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern))
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
