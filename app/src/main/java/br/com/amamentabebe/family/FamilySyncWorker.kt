package br.com.amamentabebe.family

import android.content.Context
import androidx.work.*
import br.com.amamentabebe.AmamentaApplication
import br.com.amamentabebe.alarm.AlarmScheduler
import br.com.amamentabebe.domain.FeedingCalculator
import java.util.concurrent.TimeUnit

class FamilySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AmamentaApplication
        if (app.family.selection() == null) return Result.success()
        val before = app.repository.latest()
        return try {
            app.familySync.sync()
            Result.success()
        } catch (_: Exception) { if (runAttemptCount < 5) Result.retry() else Result.failure() }
        finally {
            val latest = app.repository.latest()
            // Imported deletions and new feedings affect the reminder; diapers never do.
            val scheduler = AlarmScheduler(app, app.settings)
            if (before != latest) {
                if (latest != null) scheduler.schedule(FeedingCalculator.nextTime(latest.timeMillis, app.repository.intervalMinutes())) else scheduler.cancel()
            }
        }
    }
    companion object {
        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun enqueue(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork("family-sync-now", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<FamilySyncWorker>().setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        fun schedule(context: Context) = WorkManager.getInstance(context).enqueueUniquePeriodicWork("family-sync-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FamilySyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build())
    }
}
