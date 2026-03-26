package com.receiptscanner.domain.usecase

import com.receiptscanner.domain.model.Account
import com.receiptscanner.domain.model.Budget
import com.receiptscanner.domain.model.CategoryGroup
import com.receiptscanner.domain.model.Payee
import com.receiptscanner.domain.model.Transaction
import com.receiptscanner.domain.repository.YnabRepository

/**
 * Fake YnabRepository for unit tests, avoiding MockK's kotlin.Result inline class issue.
 */
open class FakeYnabRepository : YnabRepository {

    var budgetsResult: Result<List<Budget>> = Result.success(emptyList())
    var accountsResult: Result<List<Account>> = Result.success(emptyList())
    var categoriesResult: Result<List<CategoryGroup>> = Result.success(emptyList())
    var payeesResult: Result<List<Payee>> = Result.success(emptyList())
    var transactionsResult: Result<List<Transaction>> = Result.success(emptyList())
    var createTransactionResult: Result<String> = Result.success("new-tx-id")
    var syncPayeesResult: Result<Unit> = Result.success(Unit)
    var syncCategoriesResult: Result<Unit> = Result.success(Unit)
    var syncAccountsResult: Result<Unit> = Result.success(Unit)

    override suspend fun getBudgets(): Result<List<Budget>> = budgetsResult

    override suspend fun getAccounts(budgetId: String): Result<List<Account>> = accountsResult

    override suspend fun getCategories(budgetId: String): Result<List<CategoryGroup>> = categoriesResult

    override suspend fun getPayees(budgetId: String): Result<List<Payee>> = payeesResult

    override suspend fun getTransactions(budgetId: String, sinceDate: String?): Result<List<Transaction>> = transactionsResult

    override suspend fun createTransaction(budgetId: String, transaction: Transaction): Result<String> = createTransactionResult

    override suspend fun syncPayees(budgetId: String): Result<Unit> = syncPayeesResult

    override suspend fun syncCategories(budgetId: String): Result<Unit> = syncCategoriesResult

    override suspend fun syncAccounts(budgetId: String): Result<Unit> = syncAccountsResult
}
