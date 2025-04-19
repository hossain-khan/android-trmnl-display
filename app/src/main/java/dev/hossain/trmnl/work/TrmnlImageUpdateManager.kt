package dev.hossain.trmnl.work

import com.squareup.anvil.annotations.optional.SingleIn
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
        private val _imageUpdateFlow = MutableStateFlow<String?>(null)
        val imageUpdateFlow: StateFlow<String?> = _imageUpdateFlow.asStateFlow()

        /**
         * Updates the image URL and notifies observers through the flow
         * @param imageUrl The new image URL
         */
        fun updateImage(imageUrl: String) {
            Timber.d("Updating image URL in TrmnlImageUpdateManager: $imageUrl")
            _imageUpdateFlow.value = imageUrl
        }

        /**
         * Initialize the manager with the last cached image URL if available
         */
        suspend fun initialize() {
            imageMetadataStore.imageMetadataFlow.collect { metadata ->
                if (metadata != null && _imageUpdateFlow.value == null) {
                    Timber.d("Initializing image URL from cache: ${metadata.url}")
                    _imageUpdateFlow.value = metadata.url
                }
            }
        }
    }
