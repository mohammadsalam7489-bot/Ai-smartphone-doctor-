package com.example.data.maintenance

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.R
import com.example.data.privacy.PrivacyRedactor
import com.example.data.storage.SafeStorageManager
import java.util.concurrent.TimeUnit

data class MaintenanceScheduleStatus(
    val isScheduled: Boolean,
    val workStateName: String,
    val lastRunEpochMs: Long,
    val lastBytesFreed: Long,
    val notificationsPermitted: Boolean,
    val activeConstraintsSummary: String
)

/**
 * Cat 3 (#6, #8), Cat 6 (#8) & Cat 9 (#1..#7):
 * Constraint-aware idempotent CoroutineWorker for safe own-app housekeeping & retention expiry.
 */
class OwnAppMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Bound retries cleanly so failed workers never loop indefinitely (Cat 3 #7)
        if (runAttemptCount > 3) {
            return Result.failure(workDataOf("error" to "Exceeded max safe retry count (3)"))
        }

        return try {
            val report = SafeStorageManager.runFullOwnAppHousekeeping(applicationContext)
            val privacyClean = PrivacyRedactor.purgeExpiredDiagnosticArtifacts(applicationContext)

            val totalFreed = report.bytesFreed + privacyClean.freedBytes
            val prefs = applicationContext.getSharedPreferences(PREF_MAINTENANCE, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_LAST_RUN_MS, System.currentTimeMillis())
                .putLong(KEY_LAST_FREED_BYTES, totalFreed)
                .putBoolean(KEY_LAST_VERIFIED, report.verifiedClean && privacyClean.verifiedClean)
                .apply()

            // Cancel any stale active-job progress notification once work completes (Cat 9 #2)
            MaintenanceWorkScheduler.cancelActiveProgressNotification(applicationContext)

            Result.success(
                workDataOf(
                    "bytes_freed" to totalFreed,
                    "temp_removed" to report.tempFilesRemoved,
                    "verified_clean" to (report.verifiedClean && privacyClean.verifiedClean)
                )
            )
        } catch (t: Throwable) {
            PrivacyRedactor.safeLogWarn("OwnAppMaintenanceWorker", "Maintenance failed cleanly", t)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val PREF_MAINTENANCE = "ai_doctor_maintenance_state"
        const val KEY_LAST_RUN_MS = "last_run_ms"
        const val KEY_LAST_FREED_BYTES = "last_freed_bytes"
        const val KEY_LAST_VERIFIED = "last_verified"
    }
}

/**
 * Play-Compliant WorkManager Scheduler (Blueprint Section 2 & Categories 3, 6, 9).
 * - Uses unique periodic work (`ExistingPeriodicWorkPolicy.KEEP`) to prevent duplicate sync workers.
 * - Uses battery-not-low constraints (never tries to bypass Doze/App Standby illegally).
 * - Uses stable notification IDs, immutable PendingIntents, and graceful POST_NOTIFICATIONS handling.
 */
object MaintenanceWorkScheduler {

    const val PERIODIC_MAINTENANCE_WORK_NAME = "ai_doctor_periodic_maintenance"
    const val ONE_TIME_CLEANUP_WORK_NAME = "ai_doctor_onetime_cleanup"
    const val MAINTENANCE_CHANNEL_ID = "ai_doctor_maintenance_channel"
    const val STABLE_PROGRESS_NOTIFICATION_ID = 7101
    const val STABLE_SUMMARY_NOTIFICATION_ID = 7102

    fun schedulePeriodicMaintenance(
        context: Context,
        requireUnmeteredNetworkForFeed: Boolean = false
    ): Boolean {
        return runCatching {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(
                    if (requireUnmeteredNetworkForFeed) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED
                )
                .build()

            val request = PeriodicWorkRequestBuilder<OwnAppMaintenanceWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .addTag(PERIODIC_MAINTENANCE_WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_MAINTENANCE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            true
        }.getOrDefault(false)
    }

    fun enqueueOneTimeMaintenance(context: Context): java.util.UUID? {
        return runCatching {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<OwnAppMaintenanceWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(ONE_TIME_CLEANUP_WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_CLEANUP_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
            request.id
        }.getOrNull()
    }

    fun inspectStatus(context: Context): MaintenanceScheduleStatus {
        val prefs = context.getSharedPreferences(OwnAppMaintenanceWorker.PREF_MAINTENANCE, Context.MODE_PRIVATE)
        val lastRun = prefs.getLong(OwnAppMaintenanceWorker.KEY_LAST_RUN_MS, 0L)
        val lastFreed = prefs.getLong(OwnAppMaintenanceWorker.KEY_LAST_FREED_BYTES, 0L)

        val workInfos = runCatching {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(PERIODIC_MAINTENANCE_WORK_NAME)
                .get()
        }.getOrDefault(emptyList())

        val activeInfo = workInfos.firstOrNull {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
        } ?: workInfos.firstOrNull()

        val notifsPermitted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true

        return MaintenanceScheduleStatus(
            isScheduled = activeInfo != null && (activeInfo.state == WorkInfo.State.ENQUEUED || activeInfo.state == WorkInfo.State.RUNNING),
            workStateName = activeInfo?.state?.name ?: "NOT_SCHEDULED",
            lastRunEpochMs = lastRun,
            lastBytesFreed = lastFreed,
            notificationsPermitted = notifsPermitted,
            activeConstraintsSummary = "BatteryNotLow=true • UniqueKeep • Backoff=EXPONENTIAL"
        )
    }

    fun cancelActiveProgressNotification(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(STABLE_PROGRESS_NOTIFICATION_ID)
        }
    }

    /**
     * Cat 9 (#1, #5, #6): Safe notification helper with stable notification ID, explicit target screen,
     * FLAG_IMMUTABLE PendingIntent, and zero repeated permission nagging if denied.
     */
    fun postSummaryNotificationIfAllowed(
        context: Context,
        title: String,
        body: String
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.createNotificationChannel(
                    NotificationChannel(
                        MAINTENANCE_CHANNEL_ID,
                        "App Maintenance & Diagnostics",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_tab", "diagnostics")
            }
            val pi = PendingIntent.getActivity(
                context,
                STABLE_SUMMARY_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, MAINTENANCE_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            NotificationManagerCompat.from(context).notify(STABLE_SUMMARY_NOTIFICATION_ID, notif)
            true
        }.getOrDefault(false)
    }
}
