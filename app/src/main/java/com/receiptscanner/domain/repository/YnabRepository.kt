package com.receiptscanner.domain.repository

import com.receiptscanner.domain.model.Account
import com.receiptscanner.domain.model.Budget
import com.receiptscanner.domain.model.CategoryGroup
import com.receiptscanner.domain.model.Payee
import com.receiptscanner.domain.model.Transaction

interface YnabRepository {
    suspend fun getBudgets(): Result<List<Budget>>
    suspend fun getAccounts(budgetId: String): Result<List<Account>>
    suspend fun getCategories(budgetId: String): Result<List<CategoryGroup>>
    suspend fun getPayees(budgetId: String): Result<List<Payee>>
    suspend fun getTransactions(budgetId: String, sinceDate: String? = null): Result<List<Transaction>>
    suspend fun createTransaction(budgetId: String, transaction: Transaction): Result<String>
    suspend fun syncPayees(budgetId: String): Result<Unit>
    suspend fun syncCategories(budgetId: String): Result<Unit>
    suspend fun syncAccounts(budgetId: String): Result<Unit>
}
