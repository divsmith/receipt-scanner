package com.receiptscanner.data.remote

import com.receiptscanner.data.remote.dto.BudgetsResponse
import com.receiptscanner.data.remote.dto.AccountsResponse
import com.receiptscanner.data.remote.dto.CategoriesResponse
import com.receiptscanner.data.remote.dto.PayeesResponse
import com.receiptscanner.data.remote.dto.TransactionsResponse
import com.receiptscanner.data.remote.dto.CreateTransactionRequest
import com.receiptscanner.data.remote.dto.CreateTransactionResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface YnabApi {

    @GET("budgets")
    suspend fun getBudgets(
        @Header("Authorization") authorization: String,
    ): BudgetsResponse

    @GET("budgets/{budget_id}/accounts")
    suspend fun getAccounts(
        @Header("Authorization") authorization: String,
        @Path("budget_id") budgetId: String,
        @Query("last_knowledge_of_server") lastKnowledge: Long? = null,
    ): AccountsResponse

    @GET("budgets/{budget_id}/categories")
    suspend fun getCategories(
        @Header("Authorization") authorization: String,
        @Path("budget_id") budgetId: String,
        @Query("last_knowledge_of_server") lastKnowledge: Long? = null,
    ): CategoriesResponse

    @GET("budgets/{budget_id}/payees")
    suspend fun getPayees(
        @Header("Authorization") authorization: String,
        @Path("budget_id") budgetId: String,
        @Query("last_knowledge_of_server") lastKnowledge: Long? = null,
    ): PayeesResponse

    @GET("budgets/{budget_id}/transactions")
    suspend fun getTransactions(
        @Header("Authorization") authorization: String,
        @Path("budget_id") budgetId: String,
        @Query("since_date") sinceDate: String? = null,
        @Query("last_knowledge_of_server") lastKnowledge: Long? = null,
    ): TransactionsResponse

    @POST("budgets/{budget_id}/transactions")
    suspend fun createTransaction(
        @Header("Authorization") authorization: String,
        @Path("budget_id") budgetId: String,
        @Body request: CreateTransactionRequest,
    ): CreateTransactionResponse
}
