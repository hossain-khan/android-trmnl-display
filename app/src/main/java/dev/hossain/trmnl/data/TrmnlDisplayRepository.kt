package dev.hossain.trmnl.data

import com.slack.eithernet.successOrNull
import com.squareup.anvil.annotations.optional.SingleIn
import dev.hossain.trmnl.data.DevConfig.FAKE_API_RESPONSE
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.network.TrmnlApiService
import timber.log.Timber
import javax.inject.Inject

/**
 * Repository class responsible for fetching and mapping display data.
 *
 * ⚠️ NOTE: [FAKE_API_RESPONSE] is set to `true` in debug builds, meaning it will
 * use mock data and avoid network calls. In release builds, it is set to `false`
 * to enable real API calls.
 *
 * You can override this behavior by updating [DevConfig.FAKE_API_RESPONSE] for local development.
 */
@SingleIn(AppScope::class)
class TrmnlDisplayRepository
    @Inject
    constructor(
        private val apiService: TrmnlApiService,
        private val imageMetadataStore: ImageMetadataStore,
    ) {
        private companion object {
            /**
             * 500 Internal Server Error - A generic error message, given when an unexpected
             * condition was encountered and no more specific message is suitable.
             */
            private const val HTTP_500 = 500
        }

        /**
         * Fetches display data for next plugin from the server using the provided access token.
         * If the app is in debug mode, it uses mock data instead.
         *
         * @param accessToken The access token for authentication.
         * @return A [TrmnlDisplayInfo] object containing the display data.
         */
        suspend fun getNextDisplayData(accessToken: String): TrmnlDisplayInfo {
            if (FAKE_API_RESPONSE) {
                // Avoid using real API in debug mode
                return fakeTrmnlDisplayInfo()
            }

            val response = apiService.getNextDisplayData(accessToken).successOrNull()

            // Map the response to the display info
            val displayInfo =
                TrmnlDisplayInfo(
                    status = response?.status ?: HTTP_500,
                    imageUrl = response?.imageUrl ?: "",
                    imageName = response?.imageName ?: "",
                    error = response?.error,
                    refreshRateSecs = response?.refreshRate,
                )

            // If response was successful and has an image URL, save to data store
            if (response?.status == 0 && displayInfo.imageUrl.isNotEmpty()) {
                imageMetadataStore.saveImageMetadata(
                    displayInfo.imageUrl,
                    displayInfo.refreshRateSecs,
                )
            }

            return displayInfo
        }

        /**
         * Fetches the current display data from the server using the provided access token.
         * If the app is in debug mode, it uses mock data instead.
         *
         * @param accessToken The access token for authentication.
         * @return A [TrmnlDisplayInfo] object containing the current display data.
         */
        suspend fun getCurrentDisplayData(accessToken: String): TrmnlDisplayInfo {
            if (FAKE_API_RESPONSE) {
                // Avoid using real API in debug mode
                return fakeTrmnlDisplayInfo()
            }

            val response = apiService.getCurrentDisplayData(accessToken).successOrNull()

            // Map the response to the display info
            val displayInfo =
                TrmnlDisplayInfo(
                    status = response?.status ?: HTTP_500,
                    imageUrl = response?.imageUrl ?: "",
                    imageName = response?.filename ?: "",
                    error = response?.error,
                    refreshRateSecs = response?.refreshRateSec,
                )

            // If response was successful and has an image URL, save to data store
            if (response?.status == 200 && displayInfo.imageUrl.isNotEmpty()) {
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
            val response = apiService.getLog("RANDOM-ID", "RANDOM-TOKEN").successOrNull()
            Timber.d("Device log status response: $response")

            // If we got 200 OK, we assume the server is up
            return response != null
        }

        /**
         * Generates fake display info for debugging purposes without wasting an API request.
         *
         * ℹ️ This is only used when [FAKE_API_RESPONSE] is set to `true`.
         */
        private suspend fun fakeTrmnlDisplayInfo(): TrmnlDisplayInfo {
            Timber.d("DEBUG: Using mock data for display info")
            val mockImageUrl = "https://picsum.photos/300/200?grayscale&time=${System.currentTimeMillis()}"
            val mockRefreshRate = 600L

            // Save mock data to the data store
            imageMetadataStore.saveImageMetadata(mockImageUrl, mockRefreshRate)

            return TrmnlDisplayInfo(
                status = 0,
                imageUrl = mockImageUrl,
                imageName = "picsum-mocked-image.bmp",
                error = null,
                refreshRateSecs = mockRefreshRate,
            )
        }
    }
