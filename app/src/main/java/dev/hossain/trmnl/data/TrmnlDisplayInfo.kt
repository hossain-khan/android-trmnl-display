package dev.hossain.trmnl.data

import dev.hossain.trmnl.data.AppConfig.DEFAULT_REFRESH_RATE_SEC

/**
 * Represents the display information for the TRMNL.
 * @see [TrmnlDisplayRepository]
 */
data class TrmnlDisplayInfo(
    val status: Int,
    val imageUrl: String,
    val error: String? = null,
    val refreshRateSecs: Long? = DEFAULT_REFRESH_RATE_SEC,
)
