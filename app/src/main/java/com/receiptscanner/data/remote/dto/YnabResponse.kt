package com.receiptscanner.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BudgetsResponse(
    @Json(name = "data") val data: BudgetsData,
)

@JsonClass(generateAdapter = true)
data class BudgetsData(
    @Json(name = "budgets") val budgets: List<BudgetDto>,
)

@JsonClass(generateAdapter = true)
data class AccountsResponse(
    @Json(name = "data") val data: AccountsData,
)

@JsonClass(generateAdapter = true)
data class AccountsData(
    @Json(name = "accounts") val accounts: List<AccountDto>,
    @Json(name = "server_knowledge") val serverKnowledge: Long,
)

@JsonClass(generateAdapter = true)
data class CategoriesResponse(
    @Json(name = "data") val data: CategoriesData,
)

@JsonClass(generateAdapter = true)
data class CategoriesData(
    @Json(name = "category_groups") val categoryGroups: List<CategoryGroupDto>,
    @Json(name = "server_knowledge") val serverKnowledge: Long,
)

@JsonClass(generateAdapter = true)
data class PayeesResponse(
    @Json(name = "data") val data: PayeesData,
)

@JsonClass(generateAdapter = true)
data class PayeesData(
    @Json(name = "payees") val payees: List<PayeeDto>,
    @Json(name = "server_knowledge") val serverKnowledge: Long,
)

@JsonClass(generateAdapter = true)
data class TransactionsResponse(
    @Json(name = "data") val data: TransactionsData,
)

@JsonClass(generateAdapter = true)
data class TransactionsData(
    @Json(name = "transactions") val transactions: List<TransactionDto>,
    @Json(name = "server_knowledge") val serverKnowledge: Long,
)

@JsonClass(generateAdapter = true)
data class CreateTransactionRequest(
    @Json(name = "transaction") val transaction: SaveTransactionDto,
)

@JsonClass(generateAdapter = true)
data class CreateTransactionResponse(
    @Json(name = "data") val data: CreateTransactionData,
)

@JsonClass(generateAdapter = true)
data class CreateTransactionData(
    @Json(name = "transaction_ids") val transactionIds: List<String>,
    @Json(name = "transaction") val transaction: TransactionDto?,
)
