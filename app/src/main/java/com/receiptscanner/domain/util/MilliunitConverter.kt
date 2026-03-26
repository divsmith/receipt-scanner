package com.receiptscanner.domain.util

import java.math.BigDecimal
import java.math.RoundingMode

object MilliunitConverter {
    fun dollarsToMilliunits(dollars: BigDecimal): Long {
        return dollars.multiply(BigDecimal(1000))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    fun milliunitsToDollars(milliunits: Long): BigDecimal {
        return BigDecimal(milliunits)
            .divide(BigDecimal(1000), 2, RoundingMode.HALF_UP)
    }

    fun formatMilliunits(milliunits: Long): String {
        val dollars = milliunitsToDollars(milliunits)
        return "$${dollars.toPlainString()}"
    }
}
