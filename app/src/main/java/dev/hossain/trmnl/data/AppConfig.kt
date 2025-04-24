package dev.hossain.trmnl.data

/**
 * Global application configuration.
 */
object AppConfig {
    /**
     * Default display refresh rate in case the server does not provide one.
     */
    const val DEFAULT_REFRESH_INTERVAL_SEC: Long = 7_200L // 2 hours

    /**
     * When loading current image of the TRMNL, we add this delay before fetching
     * the image allowing the server to render the image and save it in cloud.
     */
    const val EXTRA_REFRESH_WAIT_TIME_SEC: Long = 60L // 60 seconds
}
