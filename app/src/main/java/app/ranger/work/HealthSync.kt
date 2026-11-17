package app.ranger.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.ranger.container
import app.ranger.data.Health
import app.ranger.ru
import app.ranger.ui.sheets.syncHealth
import java.util.concurrent.TimeUnit

/**
 * Reads Health Connect every hour or so while the app is closed, so new runs, steps and the morning pulse are
 * already in the journal when it opens. Only with background reading turned on in the settings and the
 * Health Connect permission for it; without either it does nothing.
 */
class HealthSync(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val s = ctx.container.settings.now()
        if (!s.healthConnect || !s.hcBackground || !Health.available(ctx)) return Result.success()
        if (Health.BACKGROUND !in Health.granted(ctx)) return Result.success()
        runCatching { syncHealth(ctx, ctx.ru) }
        return Result.success()
    }

    companion object {
        private const val NAME = "health-sync"

        /** Starts or stops the hourly read to match the settings; called when the app starts and when the switch flips. */
        suspend fun plan(ctx: Context) {
            val s = ctx.container.settings.now()
            val wm = WorkManager.getInstance(ctx)
            if (s.healthConnect && s.hcBackground && Health.BACKGROUND in Health.granted(ctx)) {
                // the app has just read on opening, so the first background read waits a period too
                val req = PeriodicWorkRequestBuilder<HealthSync>(1, TimeUnit.HOURS)
                    .setInitialDelay(1, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build()
                wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
            } else wm.cancelUniqueWork(NAME)
        }
    }
}
