package dev.hossain.trmnl.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
                tokenManager.accessTokenFlow.collect { token ->
                    if (token.isNullOrBlank()) {
                        // No token stored, navigate to config screen
                        navigator.goTo(AppConfigScreen(returnToMirrorAfterSave = true))
                        return@collect
                    }

                    try {
                        isLoading = true
                        error = null

                        val response = displayRepository.getDisplayData(token)
                        imageUrl = response.imageUrl

                        if (imageUrl == null) {
                            error = "No image URL provided in the response"
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "Unknown error"
                    } finally {
                        isLoading = false
                    }
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
                                    val token =
                                        tokenManager.accessTokenFlow.collect { token ->
                                            if (!token.isNullOrBlank()) {
                                                val response = displayRepository.getDisplayData(token)
                                                imageUrl = response.imageUrl

                                                if (imageUrl == null) {
                                                    error = "No image URL provided in the response"
                                                }
                                            } else {
                                                error = "No access token found"
                                            }
                                            isLoading = false
                                        }
                                } catch (e: Exception) {
                                    error = e.message ?: "Error refreshing display"
                                    isLoading = false
                                }
                            }
                        }
                        TrmnlMirrorDisplayScreen.Event.BackPressed -> {
                            navigator.pop()
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
    // Apply fullscreen mode
    FullScreenMode(enabled = true)

    Box(
        modifier = modifier.fillMaxSize(),
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
    }
}
