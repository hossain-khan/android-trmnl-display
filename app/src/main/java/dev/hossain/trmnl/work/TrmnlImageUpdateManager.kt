package dev.hossain.trmnl.work

import com.squareup.anvil.annotations.optional.SingleIn
import dev.hossain.trmnl.di.AppScope
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Manages the image update process for the TRMNL mirror display.
 */
@SingleIn(AppScope::class)
class TrmnlImageUpdateManager
    @Inject
    constructor() {
        private val _imageUpdateFlow = MutableStateFlow<String?>(null)
        val imageUpdateFlow: StateFlow<String?> = _imageUpdateFlow.asStateFlow()

        fun updateImage(newImageUrl: String) {
            val currentImageUrl = _imageUpdateFlow.value

            if (currentImageUrl == newImageUrl) {
                Timber.i("Image URL is the same, no update needed.")
                return
            }

            Timber.i("Updating image URL from $currentImageUrl to $newImageUrl")
            _imageUpdateFlow.value = newImageUrl
        }
    }
