package com.receiptscanner.domain.model

import java.time.LocalDate

data class Transaction(
    val id: String? = null,
    val accountId: String,
    val date: LocalDate,
    val amount: Long,         // in milliunits (negative for outflow)
    val payeeName: String?,
    val payeeId: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val memo: String? = null,
    val cleared: ClearedStatus = ClearedStatus.UNCLEARED,
    val approved: Boolean = true,
)

enum class ClearedStatus {
    CLEARED, UNCLEARED, RECONCILED
}
