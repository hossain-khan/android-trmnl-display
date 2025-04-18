package dev.hossain.trmnl.data.log

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class TrmnlRefreshLog(
    @Json(name = "timestamp") val timestamp: Long,
    @Json(name = "imageUrl") val imageUrl: String?,
    @Json(name = "refreshRateSeconds") val refreshRateSeconds: Long?,
    @Json(name = "success") val success: Boolean,
    @Json(name = "error") val error: String? = null,
) {
    companion object {
        fun createSuccess(
            imageUrl: String,
            refreshRateSeconds: Long?,
        ): TrmnlRefreshLog =
            TrmnlRefreshLog(
                timestamp = Instant.now().toEpochMilli(),
                imageUrl = imageUrl,
                refreshRateSeconds = refreshRateSeconds,
                success = true,
            )

        fun createFailure(error: String): TrmnlRefreshLog =
            TrmnlRefreshLog(
                timestamp = Instant.now().toEpochMilli(),
                imageUrl = null,
                refreshRateSeconds = null,
                success = false,
                error = error,
            )
    }
}
