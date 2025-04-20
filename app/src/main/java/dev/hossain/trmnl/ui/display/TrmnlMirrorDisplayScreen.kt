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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.ui.FullScreenMode
import dev.hossain.trmnl.ui.config.AppConfigScreen
import dev.hossain.trmnl.ui.refreshlog.DisplayRefreshLogScreen
import dev.hossain.trmnl.util.CoilRequestUtils
import dev.hossain.trmnl.util.TokenManager
import dev.hossain.trmnl.work.TrmnlImageUpdateManager
import dev.hossain.trmnl.work.TrmnlWorkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber

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
        private val tokenManager: TokenManager,
        private val trmnlWorkManager: TrmnlWorkManager,
        private val imageMetadataStore: ImageMetadataStore,
        private val trmnlImageUpdateManager: TrmnlImageUpdateManager,
    ) : Presenter<TrmnlMirrorDisplayScreen.State> {
        @Composable
        override fun present(): TrmnlMirrorDisplayScreen.State {
            var imageUrl by remember { mutableStateOf<String?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            var error by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            // Collect updates from the image update manager to get the latest image URL
            // Latest image URL is received from WorkManager work requests.
            LaunchedEffect(Unit) {
                trmnlImageUpdateManager.imageUpdateFlow.collect { imageMetadata ->
                    Timber.d("Received new image URL from TRMNL Image Update Manager: $imageMetadata")
                    if (imageMetadata != null) {
                        imageUrl = imageMetadata.url
                        isLoading = false
                        error = null
                    } else {
                        Timber.w("Failed to get cached image URL from TRMNL Image Update Manager `imageUpdateFlow`")
                        isLoading = true
                        error = "Failed to load image"
                    }
                }
            }

            // Initialize by checking token and starting one-time work if needed
            LaunchedEffect(Unit) {
                val token = tokenManager.accessTokenFlow.firstOrNull()
                if (token.isNullOrBlank()) {
                    Timber.d("No access token found, navigating to configuration screen")
                    navigator.goTo(AppConfigScreen(returnToMirrorAfterSave = true))
                    return@LaunchedEffect
                }

                // Check if we have a cached image
                val hasValidImage = imageMetadataStore.hasValidImageUrlFlow.firstOrNull() ?: false
                if (hasValidImage) {
                    // Initial loading state will be updated when imageUpdateFlow emits
                    Timber.d("Valid cached image URL exists in ImageMetadataStore")
                    trmnlImageUpdateManager.initialize()
                } else {
                    // No valid image, start a refresh work
                    Timber.d("No valid cached image, starting one-time refresh work")
                    trmnlWorkManager.startOneTimeImageRefreshWork()
                }
            }

            return TrmnlMirrorDisplayScreen.State(
                imageUrl = imageUrl,
                isLoading = isLoading,
                error = error,
                eventSink = { event ->
                    when (event) {
                        TrmnlMirrorDisplayScreen.Event.RefreshRequested -> {
                            // Simply trigger the worker for refresh
                            scope.launch {
                                isLoading = true
                                error = null

                                if (tokenManager.hasTokenSync()) {
                                    Timber.d("Manually refreshing via WorkManager")
                                    trmnlWorkManager.startOneTimeImageRefreshWork()
                                } else {
                                    error = "No access token found"
                                    isLoading = false
                                    Timber.w("Refresh failed: No access token found")
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
) {
    val context = LocalContext.current

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
            AsyncImage(
                model = CoilRequestUtils.createCachedImageRequest(context, state.imageUrl),
                contentDescription = "Terminal Display",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Floating action buttons that appear when controls are visible
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            OverlaySettingsView(state)
        }
    }
}

@Composable
private fun OverlaySettingsView(
    state: TrmnlMirrorDisplayScreen.State,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
) {
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

    Card(
        modifier =
            Modifier
                .padding(16.dp),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 4.dp,
            ),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Display Configurations",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

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
                        "Configure API Token",
                        style = fabTextStyle,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )

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
                        "Refresh TRMNL Image",
                        style = fabTextStyle,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )

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
