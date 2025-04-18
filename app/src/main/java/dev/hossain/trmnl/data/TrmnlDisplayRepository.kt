package dev.hossain.trmnl.data

import com.squareup.anvil.annotations.optional.SingleIn
import dev.hossain.trmnl.BuildConfig
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.network.TrmnlApiService
import timber.log.Timber
import javax.inject.Inject

/**
 * Repository class responsible for fetching and mapping display data.
 */
@SingleIn(AppScope::class)
class TrmnlDisplayRepository
    @Inject
    constructor(
        private val apiService: TrmnlApiService,
    ) {
        suspend fun getDisplayData(accessToken: String): TrmnlDisplayInfo {
            if (BuildConfig.DEBUG) {
                // Avoid using real API in debug mode
                Timber.d("DEBUG: Using mock data for display info")
                return TrmnlDisplayInfo(
                    status = 0,
                    imageUrl = "https://picsum.photos/300/200?grayscale",
                    error = null,
                    refreshRateSecs = 600L,
                )
            }

            val response = apiService.getDisplayData(accessToken)
            // Map the response to the display info
            return TrmnlDisplayInfo(
                status = response.status,
                imageUrl = response.imageUrl ?: "",
                error = response.error,
                refreshRateSecs = response.refreshRate,
            )
        }

        suspend fun checkServerStatus(): Boolean {
            val response = apiService.getLog("RANDOM-ID", "RANDOM-TOKEN")
            Timber.d("Device log status response: $response")

            // If we got 200 OK, we assume the server is up
            return true
        }
    }
