package dev.hossain.trmnl.ui.display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import coil3.compose.AsyncImage
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dev.hossain.trmnl.data.ImageMetadataStore
import dev.hossain.trmnl.data.TrmnlDisplayRepository
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.ui.FullScreenMode
import dev.hossain.trmnl.ui.config.AppConfigScreen
import dev.hossain.trmnl.ui.refreshlog.DisplayRefreshLogScreen
import dev.hossain.trmnl.util.CoilRequestUtils
import dev.hossain.trmnl.util.TokenManager
import dev.hossain.trmnl.work.TrmnlWorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

/**
 * This is the full screen view to show TRMNL's bitmap image for other e-ink display,
 * or any other devices like phone or tablet.
 */
@Parcelize
data object TrmnlMirrorDisplayScreen : Screen {
    data class State(
        val imageUrl: String?,
        val isLoading: Boolean = false,
        val error: String? = null,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed class Event : CircuitUiEvent {
        data object RefreshRequested : Event()

        data object ConfigureRequested : Event()

        data object ViewLogsRequested : Event()

        data object BackPressed : Event()
    }
}

class TrmnlMirrorDisplayPresenter
    @AssistedInject
    constructor(
        @Assisted private val navigator: Navigator,
        private val displayRepository: TrmnlDisplayRepository,
        private val tokenManager: TokenManager,
        private val trmnlWorkManager: TrmnlWorkManager,
        private val imageMetadataStore: ImageMetadataStore,
    ) : Presenter<TrmnlMirrorDisplayScreen.State> {
        @Composable
        override fun present(): TrmnlMirrorDisplayScreen.State {
            var imageUrl by remember { mutableStateOf<String?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            var error by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                loadImage(scope, tokenManager, displayRepository, imageMetadataStore) { newImageUrl, newError ->
                    imageUrl = newImageUrl
                    error = newError
                    isLoading = false
                }
            }

            LaunchedEffect(Unit) {
                val isScheduled = trmnlWorkManager.isImageRefreshWorkScheduledSync()
                Timber.d("Is image refresh work scheduled: $isScheduled")

                // Re-schedules periodic work for image refresh, just in case it was not scheduled
                val refreshValue = tokenManager.refreshRateSecondsFlow.firstOrNull()
                if (refreshValue != null) {
                    Timber.d("Scheduling image refresh work with interval: $refreshValue seconds")
                    trmnlWorkManager.scheduleImageRefreshWork(refreshValue)
                } else {
                    Timber.d("No image refresh rate found, NOT scheduling work")
                }
            }

            return TrmnlMirrorDisplayScreen.State(
                imageUrl = imageUrl,
                isLoading = isLoading,
                error = error,
                eventSink = { event ->
                    when (event) {
                        TrmnlMirrorDisplayScreen.Event.RefreshRequested -> {
                            // Trigger a refresh
                            scope.launch {
                                isLoading = true
                                error = null

                                try {
                                    tokenManager.accessTokenFlow.firstOrNull()?.let { token ->
                                        if (token.isNotBlank()) {
                                            Timber.d("Manually refreshing display data from API")
                                            val response = displayRepository.getDisplayData(token)
                                            imageUrl = response.imageUrl

                                            if (response.status == 500) {
                                                error = response.error ?: "Unknown error"
                                            } else if (imageUrl.isNullOrEmpty()) {
                                                error = "No image URL provided in the response"
                                            }
                                        } else {
                                            error = "No access token found"
                                            Timber.w("Refresh failed: No access token found")
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    error = e.message ?: "Error refreshing display"
                                    Timber.e(e, "Error refreshing display data")
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                        TrmnlMirrorDisplayScreen.Event.ConfigureRequested -> {
                            navigator.goTo(AppConfigScreen(returnToMirrorAfterSave = true))
                        }
                        TrmnlMirrorDisplayScreen.Event.BackPressed -> {
                            navigator.pop()
                        }
                        TrmnlMirrorDisplayScreen.Event.ViewLogsRequested -> {
                            navigator.goTo(DisplayRefreshLogScreen)
                        }
                    }
                },
            )
        }

        private suspend fun loadImage(
            scope: CoroutineScope,
            tokenManager: TokenManager,
            displayRepository: TrmnlDisplayRepository,
            imageMetadataStore: ImageMetadataStore,
            onComplete: (String?, String?) -> Unit,
        ) {
            scope.launch {
                // First check if there's a valid token
                val token = tokenManager.accessTokenFlow.firstOrNull()
                if (token.isNullOrBlank()) {
                    // No token stored, navigate to config screen
                    Timber.d("No access token found, navigating to configuration screen")
                    navigator.goTo(AppConfigScreen(returnToMirrorAfterSave = true))
                    return@launch
                }

                try {
                    // Check if we have a valid cached image URL
                    val hasValidImage = imageMetadataStore.hasValidImageUrlFlow.firstOrNull() ?: false

                    if (hasValidImage) {
                        // We have a valid cached image, use it
                        val metadata = imageMetadataStore.imageMetadataFlow.firstOrNull()
                        if (metadata != null) {
                            Timber.d("Using cached valid image URL: ${metadata.url}")
                            onComplete(metadata.url, null)
                            return@launch
                        }
                    }

                    // No valid cached image, fetch from API
                    Timber.d("No valid cached image found, fetching from API")
                    val response = displayRepository.getDisplayData(token)
                    val imageUrl = response.imageUrl
                    var error: String? = null

                    if (response.status == 500) {
                        error = response.error ?: "Unknown error"
                        Timber.e("API error (status 500): $error")
                    } else if (imageUrl.isNullOrEmpty()) {
                        error = "No image URL provided in the response"
                        Timber.e("API returned empty image URL")
                    } else {
                        Timber.d("Successfully fetched new image from API: $imageUrl")
                    }

                    onComplete(imageUrl, error)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error loading image")
                    onComplete(null, e.message ?: "Unknown error")
                }
            }
        }

        @CircuitInject(TrmnlMirrorDisplayScreen::class, AppScope::class)
        @AssistedFactory
        fun interface Factory {
            fun create(navigator: Navigator): TrmnlMirrorDisplayPresenter
        }
    }

@CircuitInject(TrmnlMirrorDisplayScreen::class, AppScope::class)
@Composable
fun TrmnlMirrorDisplayContent(
    state: TrmnlMirrorDisplayScreen.State,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
) {
    // Apply fullscreen mode and keep screen on
    FullScreenMode(enabled = true, keepScreenOn = true)

    // State to track if controls are visible
    var controlsVisible by remember { mutableStateOf(false) }

    // Auto-hide timer
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(3000) // Hide controls after 3 seconds
            controlsVisible = false
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // No visual indication when clicked
                ) {
                    controlsVisible = !controlsVisible
                },
        contentAlignment = Alignment.Center,
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
        } else if (state.error != null) {
            Text(text = "Error: ${state.error}")
        } else {
            val context = LocalContext.current
            AsyncImage(
                model = CoilRequestUtils.createCachedImageRequest(context, state.imageUrl),
                contentDescription = "Terminal Display",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Shows larger button on tablets
        // https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes
        // https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes
        val isExpandedWidth =
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) ||
                windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

        // Choose text style based on window width
        val fabTextStyle =
            if (isExpandedWidth) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.bodyLarge
            }

        // Floating action buttons that appear when controls are visible
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        state.eventSink(TrmnlMirrorDisplayScreen.Event.ConfigureRequested)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = if (isExpandedWidth) Modifier.size(32.dp) else Modifier,
                        )
                    },
                    text = {
                        Text(
                            "Configure Token",
                            style = fabTextStyle,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                )

                Spacer(modifier = Modifier.size(8.dp))

                ExtendedFloatingActionButton(
                    onClick = {
                        state.eventSink(TrmnlMirrorDisplayScreen.Event.RefreshRequested)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = if (isExpandedWidth) Modifier.size(32.dp) else Modifier,
                        )
                    },
                    text = {
                        Text(
                            "Refresh Image",
                            style = fabTextStyle,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                )

                Spacer(modifier = Modifier.size(8.dp))

                ExtendedFloatingActionButton(
                    onClick = {
                        state.eventSink(TrmnlMirrorDisplayScreen.Event.ViewLogsRequested)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = if (isExpandedWidth) Modifier.size(32.dp) else Modifier,
                        )
                    },
                    text = {
                        Text(
                            "View Refresh Logs",
                            style = fabTextStyle,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                )
            }
        }
    }
}
