package dev.hossain.trmnl.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
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
        fun scheduleImageRefreshWork(intervalSeconds: Long = DEFAULT_REFRESH_RATE_SEC) {
            // Convert seconds to minutes and ensure minimum interval
            val intervalMinutes = (intervalSeconds / 60).coerceAtLeast(15).toLong()

            if (tokenManager.hasTokenSync().not()) {
                Timber.w("Token not set, skipping image refresh work scheduling")
                return
            } else {
                Timber.d("Scheduling image refresh work with interval: $intervalMinutes minutes")
            }

            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val workRequest =
                PeriodicWorkRequestBuilder<TrmnlImageRefreshWorker>(
                    intervalMinutes,
                    TimeUnit.MINUTES,
                ).setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                        TimeUnit.MILLISECONDS,
                    ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                IMAGE_REFRESH_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest,
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

            WorkManager.getInstance(context).enqueue(workRequest)
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
        fun updateRefreshInterval(newIntervalSeconds: Long) {
            Timber.d("Updating refresh interval to $newIntervalSeconds seconds")
            // Convert seconds to minutes and ensure minimum interval
            val intervalMinutes = (newIntervalSeconds / 60).coerceAtLeast(15).toLong()

            // Reschedule with new interval
            scheduleImageRefreshWork(intervalMinutes)
        }
    }
