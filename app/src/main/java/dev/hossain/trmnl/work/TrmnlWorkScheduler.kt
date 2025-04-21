package dev.hossain.trmnl.work

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkRequest
import com.squareup.anvil.annotations.optional.SingleIn
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.di.ApplicationContext
import dev.hossain.trmnl.util.TokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Manages the scheduling and execution of background work using WorkManager.
 * This includes scheduling periodic image refresh work and handling one-time work requests.
 *
 * @param context The application context.
 * @param tokenManager The token manager for managing authentication tokens.
 */
@SingleIn(AppScope::class)
class TrmnlWorkScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val tokenManager: TokenManager,
    ) {
        companion object {
            internal const val IMAGE_REFRESH_PERIODIC_WORK_NAME = "trmnl_image_refresh_work_periodic"
            internal const val IMAGE_REFRESH_PERIODIC_WORK_TAG = "trmnl_image_refresh_work_periodic_tag"
            internal const val IMAGE_REFRESH_ONETIME_WORK_NAME = "trmnl_image_refresh_work_onetime"
            internal const val IMAGE_REFRESH_ONETIME_WORK_TAG = "trmnl_image_refresh_work_onetime_tag"

            private const val WORK_MANAGER_MINIMUM_INTERVAL_MINUTES = 15L
        }

        /**
         * Schedule periodic image refresh work
         */
        fun scheduleImageRefreshWork(intervalSeconds: Long) {
            // Check if we already have work scheduled
            val workInfos =
                WorkManager
                    .getInstance(context)
                    .getWorkInfosForUniqueWork(IMAGE_REFRESH_PERIODIC_WORK_NAME)
                    .get()

            val existingWork = workInfos.firstOrNull()
            if (existingWork != null) {
                Timber.d("Existing work found: ${existingWork.state}")

                // Optional: Get the existing work details
                val nextScheduleTimeMillis = existingWork.nextScheduleTimeMillis
                val nextScheduleTime = java.time.Instant.ofEpochMilli(nextScheduleTimeMillis)
                Timber.d("Next schedule time: $nextScheduleTimeMillis ($nextScheduleTime)")
            } else {
                Timber.d("No existing work found, will create new work")
            }

            // Convert seconds to minutes and ensure minimum interval
            val intervalMinutes = (intervalSeconds / 60).coerceAtLeast(WORK_MANAGER_MINIMUM_INTERVAL_MINUTES)

            Timber.d("Scheduling work: $intervalSeconds seconds → $intervalMinutes minutes")

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
                        WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                        TimeUnit.MILLISECONDS,
                    ).addTag(IMAGE_REFRESH_PERIODIC_WORK_TAG)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                uniqueWorkName = IMAGE_REFRESH_PERIODIC_WORK_NAME,
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
                OneTimeWorkRequestBuilder<TrmnlImageRefreshWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                        TimeUnit.MILLISECONDS,
                    ).addTag(IMAGE_REFRESH_ONETIME_WORK_TAG)
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(
                    uniqueWorkName = IMAGE_REFRESH_ONETIME_WORK_NAME,
                    existingWorkPolicy = ExistingWorkPolicy.REPLACE,
                    request = workRequest,
                )
        }

        /**
         * Cancel scheduled image refresh work
         */
        fun cancelImageRefreshWork() {
            WorkManager.getInstance(context).cancelUniqueWork(IMAGE_REFRESH_PERIODIC_WORK_NAME)
        }

        /**
         * Checks if the image refresh work is already scheduled
         * @return Flow of Boolean that emits true if work is scheduled
         */
        fun isImageRefreshWorkScheduled(): Flow<Boolean> {
            val workQuery =
                WorkQuery.Builder
                    .fromUniqueWorkNames(listOf(IMAGE_REFRESH_PERIODIC_WORK_NAME))
                    .addStates(listOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED))
                    .build()

            return WorkManager
                .getInstance(context)
                .getWorkInfosLiveData(workQuery)
                .asFlow()
                .map { workInfoList -> workInfoList.isNotEmpty() }
        }

        /**
         * Synchronously checks if image refresh work is scheduled
         * @return true if work is scheduled
         */
        fun isImageRefreshWorkScheduledSync(): Boolean {
            val workInfos: List<WorkInfo> =
                WorkManager
                    .getInstance(context)
                    .getWorkInfosForUniqueWork(IMAGE_REFRESH_PERIODIC_WORK_NAME)
                    .get()

            return workInfos.any {
                it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.BLOCKED
            }
        }

        /**
         * Get the scheduled work info as a Flow to get updates on upcoming refresh job.
         */
        fun getScheduledWorkInfo(): Flow<WorkInfo?> =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(IMAGE_REFRESH_PERIODIC_WORK_NAME)
                .asFlow()
                .map { it.firstOrNull() }

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
