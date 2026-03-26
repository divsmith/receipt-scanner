package com.receiptscanner.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MilliunitConverterTest {

    @Test
    fun `dollarsToMilliunits converts correctly`() {
        assertEquals(12393_0L, MilliunitConverter.dollarsToMilliunits(BigDecimal("123.93")))
    }

    @Test
    fun `milliunitsToDollars converts correctly`() {
        assertEquals(BigDecimal("123.93"), MilliunitConverter.milliunitsToDollars(123930L))
    }

    @Test
    fun `negative amounts convert correctly`() {
        assertEquals(-220L, MilliunitConverter.dollarsToMilliunits(BigDecimal("-0.22")))
    }

    @Test
    fun `formatMilliunits formats correctly`() {
        assertEquals("$123.93", MilliunitConverter.formatMilliunits(123930L))
    }
}
