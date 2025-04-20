package dev.hossain.trmnl.data

import java.time.Instant

/**
 * Data class to store information about the last retrieved image
 */
data class ImageMetadata(
    val url: String,
    val timestamp: Long = Instant.now().toEpochMilli(),
    val refreshRateSecs: Long? = null,
    /**
     * (OPTIONAL) Error message if the image retrieval failed
     */
    val errorMessage: String? = null,
)
