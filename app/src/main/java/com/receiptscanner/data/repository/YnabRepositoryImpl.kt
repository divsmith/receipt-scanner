package com.receiptscanner.data.repository

import com.receiptscanner.data.local.TokenProvider
import com.receiptscanner.data.local.dao.AccountCacheDao
import com.receiptscanner.data.local.dao.CategoryCacheDao
import com.receiptscanner.data.local.dao.PayeeCacheDao
import com.receiptscanner.data.local.dao.SyncMetadataDao
import com.receiptscanner.data.local.entity.AccountCacheEntity
import com.receiptscanner.data.local.entity.CategoryCacheEntity
import com.receiptscanner.data.local.entity.PayeeCacheEntity
import com.receiptscanner.data.local.entity.SyncMetadataEntity
import com.receiptscanner.data.remote.YnabApi
import com.receiptscanner.data.remote.dto.BudgetDto
import com.receiptscanner.data.remote.dto.CreateTransactionRequest
import com.receiptscanner.data.remote.dto.SaveTransactionDto
import com.receiptscanner.data.remote.dto.TransactionDto
import com.receiptscanner.domain.model.Account
import com.receiptscanner.domain.model.Budget
import com.receiptscanner.domain.model.Category
import com.receiptscanner.domain.model.CategoryGroup
import com.receiptscanner.domain.model.ClearedStatus
import com.receiptscanner.domain.model.Payee
import com.receiptscanner.domain.model.Transaction
import com.receiptscanner.domain.repository.YnabRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YnabRepositoryImpl @Inject constructor(
    private val api: YnabApi,
    private val tokenProvider: TokenProvider,
    private val payeeCacheDao: PayeeCacheDao,
    private val categoryCacheDao: CategoryCacheDao,
    private val accountCacheDao: AccountCacheDao,
    private val syncMetadataDao: SyncMetadataDao,
) : YnabRepository {

    private fun authHeader(): String {
        val token = tokenProvider.getToken()
            ?: throw IllegalStateException("No YNAB token set")
        return "Bearer $token"
    }

    override suspend fun getBudgets(): Result<List<Budget>> = runCatching {
        val response = api.getBudgets(authHeader())
        response.data.budgets.map { it.toDomain() }
    }

    override suspend fun getAccounts(budgetId: String): Result<List<Account>> = runCatching {
        val cached = accountCacheDao.getAllOpenNonDeleted()
        if (cached.isEmpty()) {
            syncAccounts(budgetId).getOrThrow()
        }
        accountCacheDao.getAllOpenNonDeleted().map { it.toDomain() }
    }

    override suspend fun getCategories(budgetId: String): Result<List<CategoryGroup>> = runCatching {
        val cached = categoryCacheDao.getAllNonDeleted()
        if (cached.isEmpty()) {
            syncCategories(budgetId).getOrThrow()
        }
        val entities = categoryCacheDao.getAllNonDeleted()
        entities.groupBy { it.categoryGroupId }.map { (groupId, cats) ->
            CategoryGroup(
                id = groupId,
                name = cats.first().categoryGroupName,
                hidden = false,
                categories = cats.map { it.toDomain() },
            )
        }
    }

    override suspend fun getPayees(budgetId: String): Result<List<Payee>> = runCatching {
        val cached = payeeCacheDao.getAllNonDeleted()
        if (cached.isEmpty()) {
            syncPayees(budgetId).getOrThrow()
        }
        payeeCacheDao.getAllNonDeleted().map { it.toDomain() }
    }

    override suspend fun getTransactions(
        budgetId: String,
        sinceDate: String?,
    ): Result<List<Transaction>> = runCatching {
        val response = api.getTransactions(authHeader(), budgetId, sinceDate)
        response.data.transactions.filter { !it.deleted }.map { it.toDomain() }
    }

    override suspend fun createTransaction(
        budgetId: String,
        transaction: Transaction,
    ): Result<String> = runCatching {
        val request = CreateTransactionRequest(
            transaction = SaveTransactionDto(
                accountId = transaction.accountId,
                date = transaction.date.toString(),
                amount = transaction.amount,
                payeeName = transaction.payeeName,
                payeeId = transaction.payeeId,
                categoryId = transaction.categoryId,
                memo = transaction.memo,
                cleared = transaction.cleared.name.lowercase(),
                approved = transaction.approved,
            ),
        )
        val response = api.createTransaction(authHeader(), budgetId, request)
        response.data.transactionIds.first()
    }

    override suspend fun syncPayees(budgetId: String): Result<Unit> = runCatching {
        val lastKnowledge = syncMetadataDao.getValueByKey("payees_$budgetId")
        val response = api.getPayees(authHeader(), budgetId, lastKnowledge)
        val entities = response.data.payees.map { it.toEntity() }
        payeeCacheDao.upsertAll(entities)
        syncMetadataDao.upsert(
            SyncMetadataEntity(
                key = "payees_$budgetId",
                value = response.data.serverKnowledge,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun syncCategories(budgetId: String): Result<Unit> = runCatching {
        val lastKnowledge = syncMetadataDao.getValueByKey("categories_$budgetId")
        val response = api.getCategories(authHeader(), budgetId, lastKnowledge)
        val entities = response.data.categoryGroups.flatMap { group ->
            group.categories.map { cat ->
                CategoryCacheEntity(
                    id = cat.id,
                    categoryGroupId = cat.categoryGroupId,
                    categoryGroupName = group.name,
                    name = cat.name,
                    hidden = cat.hidden,
                    budgeted = cat.budgeted,
                    balance = cat.balance,
                    deleted = cat.deleted,
                )
            }
        }
        categoryCacheDao.upsertAll(entities)
        syncMetadataDao.upsert(
            SyncMetadataEntity(
                key = "categories_$budgetId",
                value = response.data.serverKnowledge,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun syncAccounts(budgetId: String): Result<Unit> = runCatching {
        val lastKnowledge = syncMetadataDao.getValueByKey("accounts_$budgetId")
        val response = api.getAccounts(authHeader(), budgetId, lastKnowledge)
        val entities = response.data.accounts.map { it.toEntity() }
        accountCacheDao.upsertAll(entities)
        syncMetadataDao.upsert(
            SyncMetadataEntity(
                key = "accounts_$budgetId",
                value = response.data.serverKnowledge,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}

private fun BudgetDto.toDomain() = Budget(id = id, name = name)

private fun com.receiptscanner.data.remote.dto.PayeeDto.toEntity() =
    PayeeCacheEntity(id = id, name = name, deleted = deleted)

private fun PayeeCacheEntity.toDomain() = Payee(id = id, name = name)

private fun com.receiptscanner.data.remote.dto.AccountDto.toEntity() =
    AccountCacheEntity(
        id = id, name = name, type = type, onBudget = onBudget,
        closed = closed, balance = balance, note = note, deleted = deleted,
    )

private fun AccountCacheEntity.toDomain() = Account(
    id = id, name = name, type = type, onBudget = onBudget,
    closed = closed, balance = balance, note = note,
)

private fun CategoryCacheEntity.toDomain() = Category(
    id = id, categoryGroupId = categoryGroupId, name = name,
    hidden = hidden, budgeted = budgeted, balance = balance,
)

private fun TransactionDto.toDomain() = Transaction(
    id = id, accountId = accountId, date = LocalDate.parse(date),
    amount = amount, payeeName = payeeName, payeeId = payeeId,
    categoryId = categoryId, categoryName = categoryName, memo = memo,
    cleared = when (cleared.lowercase()) {
        "cleared" -> ClearedStatus.CLEARED
        "reconciled" -> ClearedStatus.RECONCILED
        else -> ClearedStatus.UNCLEARED
    },
    approved = approved,
)
