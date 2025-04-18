package dev.hossain.trmnl.work

import com.squareup.anvil.annotations.optional.SingleIn
import dev.hossain.trmnl.di.AppScope
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SingleIn(AppScope::class)
class TrmnlImageUpdateManager
    @Inject
    constructor() {
        private val _imageUpdateFlow = MutableStateFlow<String?>(null)
        val imageUpdateFlow: StateFlow<String?> = _imageUpdateFlow.asStateFlow()

        fun updateImage(newImageUrl: String) {
            _imageUpdateFlow.value = newImageUrl
        }
    }
