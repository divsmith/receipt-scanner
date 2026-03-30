package com.receiptscanner.testing.receiptfixtures

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object ReceiptFixtureLabelsParser {

    private val headingPattern = Regex("""(?m)^##\s+(.+?)\s*$""")
    private val separatorPattern = Regex("""^\|\s*:?-+\s*\|\s*:?-+\s*\|$""")

    fun parse(markdown: String): List<ReceiptFixture> {
        val headings = headingPattern.findAll(markdown).toList()

        return headings.mapIndexed { index, heading ->
            val imageName = heading.groupValues[1].trim()
            val sectionStart = heading.range.last + 1
            val sectionEnd = headings.getOrNull(index + 1)?.range?.first ?: markdown.length
            val fields = parseFields(
                imageName = imageName,
                sectionBody = markdown.substring(sectionStart, sectionEnd),
            )

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

    private fun parseFields(imageName: String, sectionBody: String): Map<String, String> {
        val rows = sectionBody
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("|") }
            .filterNot { row ->
                row.equals("| Field | Value |", ignoreCase = true) || separatorPattern.matches(row)
            }
            .toList()

        require(rows.isNotEmpty()) { "No label rows found for $imageName" }

        return rows
            .associate { row ->
                val columns = row.trim('|').split('|', limit = 2).map { it.trim() }
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
