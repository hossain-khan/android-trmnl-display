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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
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
import dev.hossain.trmnl.data.TrmnlDisplayRepository
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.ui.FullScreenMode
import dev.hossain.trmnl.ui.config.AppConfigScreen
import dev.hossain.trmnl.util.CoilRequestUtils
import dev.hossain.trmnl.util.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlin.coroutines.cancellation.CancellationException

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

        data object BackPressed : Event()
    }
}

class TrmnlMirrorDisplayPresenter
    @AssistedInject
    constructor(
        @Assisted private val navigator: Navigator,
        private val displayRepository: TrmnlDisplayRepository,
        private val tokenManager: TokenManager,
    ) : Presenter<TrmnlMirrorDisplayScreen.State> {
        @Composable
        override fun present(): TrmnlMirrorDisplayScreen.State {
            var imageUrl by remember { mutableStateOf<String?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            var error by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                loadImage(scope, tokenManager, displayRepository) { newImageUrl, newError ->
                    imageUrl = newImageUrl
                    error = newError
                    isLoading = false
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
                                            val response = displayRepository.getDisplayData(token)
                                            imageUrl = response.imageUrl

                                            if (response.status == 500) {
                                                error = response.error ?: "Unknown error"
                                            } else if (imageUrl.isNullOrEmpty()) {
                                                error = "No image URL provided in the response"
                                            }
                                        } else {
                                            error = "No access token found"
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    error = e.message ?: "Error refreshing display"
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
                    }
                },
            )
        }

        private suspend fun loadImage(
            scope: CoroutineScope,
            tokenManager: TokenManager,
            displayRepository: TrmnlDisplayRepository,
            onComplete: (String?, String?) -> Unit,
        ) {
            scope.launch {
                tokenManager.accessTokenFlow.collect { token ->
                    if (token.isNullOrBlank()) {
                        // No token stored, navigate to config screen
                        navigator.goTo(AppConfigScreen(returnToMirrorAfterSave = true))
                        return@collect
                    }

                    try {
                        val response = displayRepository.getDisplayData(token)
                        val imageUrl = response.imageUrl
                        var error: String? = null

                        if (response.status == 500) {
                            error = response.error ?: "Unknown error"
                        } else if (imageUrl.isNullOrEmpty()) {
                            error = "No image URL provided in the response"
                        }

                        onComplete(imageUrl, error)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        onComplete(null, e.message ?: "Unknown error")
                    }
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
) {
    // Apply fullscreen mode
    FullScreenMode(enabled = true)

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
                // Configure button with text
                ExtendedFloatingActionButton(
                    onClick = {
                        state.eventSink(TrmnlMirrorDisplayScreen.Event.ConfigureRequested)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                        )
                    },
                    text = { Text("Configure Token") },
                )

                // Refresh button with text
                ExtendedFloatingActionButton(
                    onClick = {
                        state.eventSink(TrmnlMirrorDisplayScreen.Event.RefreshRequested)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                        )
                    },
                    text = { Text("Refresh Image") },
                )
            }
        }
    }
}
