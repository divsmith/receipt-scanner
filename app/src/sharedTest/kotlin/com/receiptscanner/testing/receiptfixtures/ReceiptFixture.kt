package com.receiptscanner.testing.receiptfixtures

import java.time.LocalDate

data class ReceiptFixture(
    val imageName: String,
    val expected: ReceiptFixtureExpectation,
)

data class ReceiptFixtureExpectation(
    val store: String?,
    val totalLabel: String?,
    val date: LocalDate?,
    val cardLastFour: String?,
)
