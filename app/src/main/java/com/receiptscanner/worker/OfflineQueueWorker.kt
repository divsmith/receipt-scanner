package com.receiptscanner.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.receiptscanner.data.local.UserPreferencesManager
import com.receiptscanner.domain.usecase.ProcessOfflineQueueUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class OfflineQueueWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val processOfflineQueueUseCase: ProcessOfflineQueueUseCase,
    private val userPreferencesManager: UserPreferencesManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val budgetId = userPreferencesManager.getBudgetId()
            ?: return Result.failure()

        return processOfflineQueueUseCase(budgetId).fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            },
        )
    }

    companion object {
        const val WORK_NAME = "offline_queue_sync"
        private const val MAX_RETRIES = 3

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<OfflineQueueWorker>(
                15, TimeUnit.MINUTES,
            ).setConstraints(networkConstraints).build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueOneShot(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<OfflineQueueWorker>()
                .setConstraints(networkConstraints)
                .build()
            workManager.enqueueUniqueWork(
                "${WORK_NAME}_oneshot",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
