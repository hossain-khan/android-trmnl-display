package dev.hossain.trmnl.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.hossain.trmnl.data.TrmnlDisplayRepository
import dev.hossain.trmnl.util.TokenManager
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Worker to refresh the image displayed on the TRMNL.
 */
class TrmnlImageRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
    private val displayRepository: TrmnlDisplayRepository,
    private val tokenManager: TokenManager,
) : CoroutineWorker(appContext, params) {
    companion object {
        const val KEY_REFRESH_RESULT = "refresh_result"
        const val KEY_NEW_IMAGE_URL = "new_image_url"
        const val KEY_ERROR = "error"
        const val KEY_HAS_NEW_IMAGE = "has_new_image"
    }

    override suspend fun doWork(): Result {
        try {
            // Get current token
            val token = tokenManager.accessTokenFlow.firstOrNull()

            if (token.isNullOrBlank()) {
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
                return Result.failure(
                    workDataOf(
                        KEY_REFRESH_RESULT to "failure",
                        KEY_ERROR to (response.error ?: "Unknown server error"),
                    ),
                )
            }

            // Check if image URL is valid
            if (response.imageUrl.isNullOrEmpty()) {
                return Result.failure(
                    workDataOf(
                        KEY_REFRESH_RESULT to "failure",
                        KEY_ERROR to "No image URL provided in response",
                    ),
                )
            }

            // Check if we should adapt refresh rate
            val refreshRate = response.refreshRateSecs
            refreshRate?.let {
                // You can reschedule the worker with this new interval
                // (implementation in WorkManager setup)
            }

            return Result.success(
                workDataOf(
                    KEY_REFRESH_RESULT to "success",
                    KEY_NEW_IMAGE_URL to response.imageUrl,
                    KEY_HAS_NEW_IMAGE to true,
                ),
            )
        } catch (e: Exception) {
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
        ) {
            fun create(
                appContext: Context,
                params: WorkerParameters,
            ): TrmnlImageRefreshWorker =
                TrmnlImageRefreshWorker(
                    appContext,
                    params,
                    displayRepository,
                    tokenManager,
                )
        }
}
