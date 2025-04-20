package dev.hossain.trmnl.data

/**
 * Global application configuration.
 */
object AppConfig {
    /**
     * Default display refresh rate in case the server does not provide one.
     */
    const val DEFAULT_REFRESH_RATE_SEC: Long = 7_200L // 2 hours

    /**
     * Fake API response for local development and testing purposes.
     */
    const val FAKE_API_RESPONSE = false
}
