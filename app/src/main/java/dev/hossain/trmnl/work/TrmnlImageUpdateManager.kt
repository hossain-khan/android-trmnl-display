package dev.hossain.trmnl.work

import com.squareup.anvil.annotations.optional.SingleIn
import dev.hossain.trmnl.data.ImageMetadata
import dev.hossain.trmnl.data.ImageMetadataStore
import dev.hossain.trmnl.di.AppScope
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Manages the image update process for the TRMNL mirror display.
 * Acts as a central source of truth for image updates from WorkManager.
 */
@SingleIn(AppScope::class)
class TrmnlImageUpdateManager
    @Inject
    constructor(
        private val imageMetadataStore: ImageMetadataStore,
    ) {
        private val _imageUpdateFlow = MutableStateFlow<ImageMetadata?>(null)
        val imageUpdateFlow: StateFlow<ImageMetadata?> = _imageUpdateFlow.asStateFlow()

        // To support the hacky workaround of using a map to store the image URL and timestamp
        // This will ensure that the `updateImage` method only accepts updates with newer timestamps
        // See https://github.com/hossain-khan/android-trmnl-display/pull/63#issuecomment-2817278344
        private val imageUpdateHistory = mutableMapOf<String, Long>()

        /**
         * Updates the image URL and notifies observers through the flow.
         * Only accepts updates with newer timestamps than the current image.
         * @param imageMetadata The new image URL with additional metadata
         */
        fun updateImage(imageMetadata: ImageMetadata) {
            val lastUpdatedImageMetadata = _imageUpdateFlow.value
            val lastUpdatedTimestamp = lastUpdatedImageMetadata?.timestamp ?: 0L
            val timestampForNewImageUpdate = imageUpdateHistory[imageMetadata.url] ?: Long.MAX_VALUE

            if (timestampForNewImageUpdate > lastUpdatedTimestamp) {
                Timber.d("Updating image URL in TrmnlImageUpdateManager: $imageMetadata")
                imageUpdateHistory[imageMetadata.url] = imageMetadata.timestamp
                _imageUpdateFlow.value = imageMetadata
            } else {
                Timber.w("Discarded older image update: $imageMetadata")
            }
        }

        /**
         * Initialize the manager with the last cached image URL if available
         */
        suspend fun initialize() {
            imageMetadataStore.imageMetadataFlow.collect { metadata ->
                if (metadata != null && _imageUpdateFlow.value == null) {
                    Timber.d("Initializing image URL from ImageMetadataStore cache: ${metadata.url}")
                    _imageUpdateFlow.value = metadata
                }
            }
        }
    }
