package dev.hossain.trmnl.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data class representing the response from the TRMNL API for the current image.
 *
 * Sample response:
 * ```json
 * {
 *   "status": 200,
 *   "refresh_rate": 3590,
 *   "image_url": "https://usetrmnl.com/plugin-image.bmp",
 *   "filename": "plugin-image",
 *   "rendered_at": null
 * }
 */
@JsonClass(generateAdapter = true)
data class TrmnlCurrentImageResponse(
    val status: Int,
    @Json(name = "refresh_rate")
    val refreshRateSec: Long?,
    @Json(name = "image_url")
    val imageUrl: String?,
    val filename: String?,
    @Json(name = "rendered_at")
    val renderedAt: String?,
)
