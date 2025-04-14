package dev.hossain.trmnl.data

data class TrmnlDisplayInfo(
    val status: Int,
    val imageUrl: String,
    val error: String? = null,
)
