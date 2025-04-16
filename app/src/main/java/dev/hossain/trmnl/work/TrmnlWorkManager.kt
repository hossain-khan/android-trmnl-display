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
        private val workerFactory: TrmnlImageRefreshWorker.Factory,
    ) {
        private val workManager = WorkManager.getInstance(context)

        companion object {
            private const val IMAGE_REFRESH_WORK_NAME = "trmnl_image_refresh_work"
            private const val DEFAULT_REFRESH_INTERVAL_MINUTES = 60L
        }

        /**
         * Schedule periodic image refresh work
         */
        fun scheduleImageRefreshWork(intervalMinutes: Long = DEFAULT_REFRESH_INTERVAL_MINUTES) {
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

            workManager.enqueueUniquePeriodicWork(
                IMAGE_REFRESH_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest,
            )
        }

        /**
         * Cancel scheduled image refresh work
         */
        fun cancelImageRefreshWork() {
            workManager.cancelUniqueWork(IMAGE_REFRESH_WORK_NAME)
        }

        /**
         * Update the refresh interval based on server response
         */
        fun updateRefreshInterval(newIntervalSeconds: Int) {
            // Convert seconds to minutes and ensure minimum interval
            val intervalMinutes = (newIntervalSeconds / 60).coerceAtLeast(15).toLong()

            // Reschedule with new interval
            scheduleImageRefreshWork(intervalMinutes)
        }
    }
