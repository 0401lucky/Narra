package com.example.myapplication.system.moments

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.myapplication.ChatApplication
import java.util.concurrent.TimeUnit

class MomentsAutoGenerationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ChatApplication ?: return Result.failure()
        return runCatching {
            app.appGraph.momentsGenerationCoordinator.generateDueAssistantPosts(maxPosts = 2)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val UniqueWorkName = "moments_auto_generation"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<MomentsAutoGenerationWorker>(
                repeatInterval = 3,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UniqueWorkName,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
