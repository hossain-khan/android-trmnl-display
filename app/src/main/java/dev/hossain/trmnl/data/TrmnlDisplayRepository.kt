package dev.hossain.trmnl.data

import com.squareup.anvil.annotations.optional.SingleIn
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.network.TrmnlApiService
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
            val response = apiService.getDisplayData(accessToken)
            // Map the response to the display info
            return TrmnlDisplayInfo(
                status = response.status,
                imageUrl = response.imageUrl ?: "",
                error = response.error,
            )
        }
    }
