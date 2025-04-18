package dev.hossain.trmnl.data.log

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TrmnlActivityLogs(
    val logs: List<TrmnlActivityLog> = emptyList(),
)
