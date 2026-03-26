package com.receiptscanner.domain.usecase

import com.receiptscanner.domain.model.Transaction
import com.receiptscanner.domain.repository.YnabRepository
import javax.inject.Inject

class SubmitTransactionUseCase @Inject constructor(
    private val ynabRepository: YnabRepository,
) {
    suspend operator fun invoke(
        budgetId: String,
        transaction: Transaction,
    ): Result<String> {
        return ynabRepository.createTransaction(budgetId, transaction)
    }
}
