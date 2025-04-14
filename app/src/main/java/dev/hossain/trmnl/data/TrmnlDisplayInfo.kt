package dev.hossain.trmnl.data

/**
 * Represents the display information for the TRMNL.
 * @see [TrmnlDisplayRepository]
 */
data class TrmnlDisplayInfo(
    val status: Int,
    val imageUrl: String,
    val error: String? = null,
)
