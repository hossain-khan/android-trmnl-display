package dev.hossain.trmnl.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.squareup.anvil.annotations.optional.SingleIn
import dev.hossain.trmnl.data.AppConfig.DEFAULT_REFRESH_RATE_SEC
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.di.ApplicationContext
import dev.hossain.trmnl.util.TokenManager
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@SingleIn(AppScope::class)
class TrmnlWorkManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val tokenManager: TokenManager,
    ) {
        companion object {
            internal const val IMAGE_REFRESH_WORK_NAME = "trmnl_image_refresh_work"
        }

        /**
         * Schedule periodic image refresh work
         */
        fun scheduleImageRefreshWork(intervalSeconds: Long? = null) {
            // Use provided value or fall back to stored value or default
            val effectiveInterval =
                intervalSeconds
                    ?: tokenManager.getRefreshRateSecondsSync()
                    ?: DEFAULT_REFRESH_RATE_SEC

            // Convert seconds to minutes and ensure minimum interval
            val intervalMinutes = (effectiveInterval / 60).coerceAtLeast(15)

            Timber.d("Scheduling work: $effectiveInterval seconds → $intervalMinutes minutes")

            if (tokenManager.hasTokenSync().not()) {
                Timber.w("Token not set, skipping image refresh work scheduling")
                return
            }

            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val periodicWorkRequest =
                PeriodicWorkRequestBuilder<TrmnlImageRefreshWorker>(
                    repeatInterval = intervalMinutes,
                    repeatIntervalTimeUnit = TimeUnit.MINUTES,
                ).setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS,
                    ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                uniqueWorkName = IMAGE_REFRESH_WORK_NAME,
                existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
                request = periodicWorkRequest,
            )
        }

        /**
         * Start a one-time image refresh work immediately
         */
        fun startOneTimeImageRefreshWork() {
            Timber.d("Starting one-time image refresh work")

            if (tokenManager.hasTokenSync().not()) {
                Timber.w("Token not set, skipping one-time image refresh work")
                return
            }

            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val workRequest =
                androidx.work
                    .OneTimeWorkRequestBuilder<TrmnlImageRefreshWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                        TimeUnit.MILLISECONDS,
                    ).build()

            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(
                    uniqueWorkName = IMAGE_REFRESH_WORK_NAME,
                    existingWorkPolicy = ExistingWorkPolicy.REPLACE,
                    request = workRequest,
                )
        }

        /**
         * Cancel scheduled image refresh work
         */
        fun cancelImageRefreshWork() {
            WorkManager.getInstance(context).cancelUniqueWork(IMAGE_REFRESH_WORK_NAME)
        }

        /**
         * Update the refresh interval based on server response
         */
        suspend fun updateRefreshInterval(newIntervalSeconds: Long) {
            Timber.d("Updating refresh interval to $newIntervalSeconds seconds")

            // Save the refresh rate to TokenManager
            tokenManager.saveRefreshRateSeconds(newIntervalSeconds)

            // Reschedule with new interval
            scheduleImageRefreshWork(newIntervalSeconds)
        }
    }
