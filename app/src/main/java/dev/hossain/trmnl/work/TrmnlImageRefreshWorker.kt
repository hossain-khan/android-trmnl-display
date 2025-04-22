package dev.hossain.trmnl.work

import android.content.Context
import androidx.annotation.Keep
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.hossain.trmnl.MainActivity
import dev.hossain.trmnl.data.ImageMetadata
import dev.hossain.trmnl.data.TrmnlDisplayRepository
import dev.hossain.trmnl.data.log.TrmnlRefreshLogManager
import dev.hossain.trmnl.di.WorkerModule
import dev.hossain.trmnl.ui.display.TrmnlMirrorDisplayScreen
import dev.hossain.trmnl.util.TokenManager
import dev.hossain.trmnl.work.TrmnlImageRefreshWorker.RefreshWorkResult.FAILURE
import dev.hossain.trmnl.work.TrmnlImageRefreshWorker.RefreshWorkResult.SUCCESS
import dev.hossain.trmnl.work.TrmnlWorkScheduler.Companion.IMAGE_REFRESH_PERIODIC_WORK_TAG
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Worker to refresh the image displayed on the TRMNL mirror display.
 *
 * The worker result is observed in [MainActivity] and then updated via the [TrmnlImageUpdateManager].
 * Whenever the image is updated, the [TrmnlImageUpdateManager] will notify the observers.
 * In this case the [TrmnlMirrorDisplayScreen] will recompose and update the image.
 *
 * @see TrmnlImageUpdateManager
 * @see TrmnlMirrorDisplayScreen
 */
class TrmnlImageRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
    private val displayRepository: TrmnlDisplayRepository,
    private val tokenManager: TokenManager,
    private val refreshLogManager: TrmnlRefreshLogManager,
    private val trmnlWorkScheduler: TrmnlWorkScheduler,
    private val trmnlImageUpdateManager: TrmnlImageUpdateManager,
) : CoroutineWorker(appContext, params) {
    companion object {
        private const val TAG = "TrmnlWorker"

        const val KEY_REFRESH_RESULT = "refresh_result"
        const val KEY_NEW_IMAGE_URL = "new_image_url"
        const val KEY_ERROR_MESSAGE = "error_message"
    }

    @Keep
    enum class RefreshWorkResult {
        SUCCESS,
        FAILURE,
    }

    override suspend fun doWork(): Result {
        Timber.tag(TAG).d("Starting image refresh work ($tags)")
        try {
            // Get current token
            val token = tokenManager.accessTokenFlow.firstOrNull()

            if (token.isNullOrBlank()) {
                Timber.tag(TAG).w("Token is not set, skipping image refresh")
                refreshLogManager.addFailureLog("No access token found")
                return Result.failure(
                    workDataOf(
                        KEY_REFRESH_RESULT to FAILURE.name,
                        KEY_ERROR_MESSAGE to "No access token found",
                    ),
                )
            }

            // Fetch new display data
            val response = displayRepository.getDisplayData(token)

            // Check for errors
            if (response.status == 500) {
                Timber.tag(TAG).w("Failed to fetch display data: ${response.error}")
                refreshLogManager.addFailureLog(response.error ?: "Unknown server error")
                return Result.failure(
                    workDataOf(
                        KEY_REFRESH_RESULT to FAILURE.name,
                        KEY_ERROR_MESSAGE to (response.error ?: "Unknown server error"),
                    ),
                )
            }

            // Check if image URL is valid
            if (response.imageUrl.isEmpty()) {
                Timber.tag(TAG).w("No image URL provided in response")
                refreshLogManager.addFailureLog("No image URL provided in response")
                return Result.failure(
                    workDataOf(
                        KEY_REFRESH_RESULT to FAILURE.name,
                        KEY_ERROR_MESSAGE to "No image URL provided in response",
                    ),
                )
            }

            // ✅ Log success and update image
            refreshLogManager.addSuccessLog(response.imageUrl, response.imageName, response.refreshRateSecs)

            // Check if we should adapt refresh rate
            val refreshRate = response.refreshRateSecs
            refreshRate?.let { newRefreshRateSec ->
                if (tokenManager.shouldUpdateRefreshRate(newRefreshRateSec)) {
                    Timber.tag(TAG).d("Refresh rate changed, updating periodic work and saving new rate")
                    tokenManager.saveRefreshRateSeconds(newRefreshRateSec)
                    trmnlWorkScheduler.scheduleImageRefreshWork(newRefreshRateSec)
                } else {
                    Timber.tag(TAG).d("Refresh rate is unchanged, not updating")
                }
            }

            // Workaround for periodic work not updating correctly (might be 🐛 bug in library)
            conditionallyUpdateImageForPeriodicWork(tags, response.imageUrl)

            Timber.tag(TAG).i("Image refresh successful for work($tags), got new URL: ${response.imageUrl}")
            return Result.success(
                workDataOf(
                    KEY_REFRESH_RESULT to SUCCESS.name,
                    KEY_NEW_IMAGE_URL to response.imageUrl,
                ),
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during image refresh work($tags): ${e.message}")
            refreshLogManager.addFailureLog(e.message ?: "Unknown error during refresh")
            return Result.failure(
                workDataOf(
                    KEY_REFRESH_RESULT to FAILURE.name,
                    KEY_ERROR_MESSAGE to (e.message ?: "Unknown error during refresh"),
                ),
            )
        }
    }

    /**
     * There is potentially a bug where periodic work is not updated correctly.
     * This function will check if the image URL is different from the one in the store.
     *
     * https://stackoverflow.com/questions/51476480/workstatus-observer-always-in-enqueued-state
     */
    private fun conditionallyUpdateImageForPeriodicWork(
        tags: Set<String>,
        imageUrl: String,
    ) {
        if (tags.contains(IMAGE_REFRESH_PERIODIC_WORK_TAG)) {
            Timber.tag(TAG).d("Periodic work detected, updating image URL from result")
            trmnlImageUpdateManager.updateImage(
                ImageMetadata(
                    url = imageUrl,
                    timestamp = System.currentTimeMillis(),
                    refreshRateSecs = null,
                ),
            )
        }
    }

    /**
     * Factory class for creating instances of [TrmnlImageRefreshWorker] with additional dependency using DI.
     *
     * @see TrmnlWorkerFactory
     * @see WorkerModule
     */
    class Factory
        @Inject
        constructor(
            private val displayRepository: TrmnlDisplayRepository,
            private val tokenManager: TokenManager,
            private val refreshLogManager: TrmnlRefreshLogManager,
            private val trmnlWorkScheduler: TrmnlWorkScheduler,
            private val trmnlImageUpdateManager: TrmnlImageUpdateManager,
        ) {
            fun create(
                appContext: Context,
                params: WorkerParameters,
            ): TrmnlImageRefreshWorker =
                TrmnlImageRefreshWorker(
                    appContext = appContext,
                    params = params,
                    displayRepository = displayRepository,
                    tokenManager = tokenManager,
                    refreshLogManager = refreshLogManager,
                    trmnlWorkScheduler = trmnlWorkScheduler,
                    trmnlImageUpdateManager = trmnlImageUpdateManager,
                )
        }
}
