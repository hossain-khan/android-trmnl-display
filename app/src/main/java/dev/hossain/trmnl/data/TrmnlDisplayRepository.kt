package dev.hossain.trmnl.data

import com.squareup.anvil.annotations.optional.SingleIn
import dev.hossain.trmnl.data.AppConfig.FAKE_API_RESPONSE
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.network.TrmnlApiService
import timber.log.Timber
import javax.inject.Inject

/**
 * Repository class responsible for fetching and mapping display data.
 *
 * ⚠️ NOTE: By default, [FAKE_API_RESPONSE] is set to `true` and it will
 * not make any network calls. Set it to `false` to enable real API calls.
 */
@SingleIn(AppScope::class)
class TrmnlDisplayRepository
    @Inject
    constructor(
        private val apiService: TrmnlApiService,
        private val imageMetadataStore: ImageMetadataStore,
    ) {
        /**
         * Fetches display data from the server using the provided access token.
         * If the app is in debug mode, it uses mock data instead.
         *
         * @param accessToken The access token for authentication.
         * @return A [TrmnlDisplayInfo] object containing the display data.
         */
        suspend fun getDisplayData(accessToken: String): TrmnlDisplayInfo {
            if (FAKE_API_RESPONSE) {
                // Avoid using real API in debug mode
                return fakeTrmnlDisplayInfo()
            }

            val response = apiService.getDisplayData(accessToken)

            // Map the response to the display info
            val displayInfo =
                TrmnlDisplayInfo(
                    status = response.status,
                    imageUrl = response.imageUrl ?: "",
                    error = response.error,
                    refreshRateSecs = response.refreshRate,
                )

            // If response was successful and has an image URL, save to data store
            if (response.status == 0 && displayInfo.imageUrl.isNotEmpty()) {
                imageMetadataStore.saveImageMetadata(
                    displayInfo.imageUrl,
                    displayInfo.refreshRateSecs,
                )
            }

            return displayInfo
        }

        /**
         * Check the server status by making a request to the log endpoint.
         */
        suspend fun checkServerStatus(): Boolean {
            val response = apiService.getLog("RANDOM-ID", "RANDOM-TOKEN")
            Timber.d("Device log status response: $response")

            // If we got 200 OK, we assume the server is up
            return true
        }

        /**
         * Generates fake display info for debugging purposes without wasting an API request.
         *
         * ℹ️ This is only used when [FAKE_API_RESPONSE] is set to `true`.
         */
        private suspend fun fakeTrmnlDisplayInfo(): TrmnlDisplayInfo {
            Timber.d("DEBUG: Using mock data for display info")
            val mockImageUrl = "https://picsum.photos/300/200?grayscale"
            val mockRefreshRate = 600L

            // Save mock data to the data store
            imageMetadataStore.saveImageMetadata(mockImageUrl, mockRefreshRate)

            return TrmnlDisplayInfo(
                status = 0,
                imageUrl = mockImageUrl,
                error = null,
                refreshRateSecs = mockRefreshRate,
            )
        }
    }
