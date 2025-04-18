package dev.hossain.trmnl.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.hossain.trmnl.data.TrmnlDisplayRepository
import dev.hossain.trmnl.data.log.TrmnlRefreshLogManager
import dev.hossain.trmnl.util.TokenManager
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Worker to refresh the image displayed on the TRMNL.
 */
class TrmnlImageRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
    private val displayRepository: TrmnlDisplayRepository,
    private val tokenManager: TokenManager,
    private val refreshLogManager: TrmnlRefreshLogManager,
) : CoroutineWorker(appContext, params) {
    companion object {
        private const val TAG = "TrmnlWorker"

        const val KEY_REFRESH_RESULT = "refresh_result"
        const val KEY_NEW_IMAGE_URL = "new_image_url"
        const val KEY_ERROR = "error"
        const val KEY_HAS_NEW_IMAGE = "has_new_image"
    }

    override suspend fun doWork(): Result {
        Timber.tag(TAG).d("Starting image refresh work")
        try {
            // Get current token
            val token = tokenManager.accessTokenFlow.firstOrNull()

            if (token.isNullOrBlank()) {
                Timber.tag(TAG).w("Token is not set, skipping image refresh")
                refreshLogManager.addFailureLog("No access token found")
                return Result.failure(
                    workDataOf(
                        KEY_REFRESH_RESULT to "failure",
                        KEY_ERROR to "No access token found",
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
                        KEY_REFRESH_RESULT to "failure",
                        KEY_ERROR to (response.error ?: "Unknown server error"),
                    ),
                )
            }

            // Check if image URL is valid
            if (response.imageUrl.isEmpty()) {
                Timber.tag(TAG).w("No image URL provided in response")
                refreshLogManager.addFailureLog("No image URL provided in response")
                return Result.failure(
                    workDataOf(
                        KEY_REFRESH_RESULT to "failure",
                        KEY_ERROR to "No image URL provided in response",
                    ),
                )
            }

            // Log success
            refreshLogManager.addSuccessLog(response.imageUrl, response.refreshRateSecs)

            // Check if we should adapt refresh rate
            val refreshRate = response.refreshRateSecs
            refreshRate?.let {
                Timber.tag(TAG).d("Adapting refresh rate to $refreshRate seconds")
                // You can reschedule the worker with this new interval
                // (implementation in WorkManager setup)
            }

            Timber.tag(TAG).i("Image refresh successful, new URL: ${response.imageUrl}")
            return Result.success(
                workDataOf(
                    KEY_REFRESH_RESULT to "success",
                    KEY_NEW_IMAGE_URL to response.imageUrl,
                    KEY_HAS_NEW_IMAGE to true,
                ),
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during image refresh: ${e.message}")
            refreshLogManager.addFailureLog(e.message ?: "Unknown error during refresh")
            return Result.failure(
                workDataOf(
                    KEY_REFRESH_RESULT to "failure",
                    KEY_ERROR to (e.message ?: "Unknown error during refresh"),
                ),
            )
        }
    }

    class Factory
        @Inject
        constructor(
            private val displayRepository: TrmnlDisplayRepository,
            private val tokenManager: TokenManager,
            private val refreshLogManager: TrmnlRefreshLogManager,
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
                )
        }
}
