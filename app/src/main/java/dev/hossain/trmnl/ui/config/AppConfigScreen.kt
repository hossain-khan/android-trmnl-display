package dev.hossain.trmnl.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import dev.hossain.trmnl.ui.display.TrmnlMirrorDisplayScreen
import dev.hossain.trmnl.util.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppConfigScreen(
    val returnToMirrorAfterSave: Boolean = false,
) : Screen {
    data class State(
        val accessToken: String,
        val isLoading: Boolean = false,
        val validationResult: ValidationResult? = null,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed class ValidationResult {
        data class Success(
            val imageUrl: String,
        ) : ValidationResult()

        data class Failure(
            val message: String,
        ) : ValidationResult()
    }

    sealed class Event : CircuitUiEvent {
        data class AccessTokenChanged(
            val token: String,
        ) : Event()

        data object ValidateToken : Event()

        data object SaveAndContinue : Event()

        data object BackPressed : Event()
    }
}

class AppConfigPresenter
    @AssistedInject
    constructor(
        @Assisted private val navigator: Navigator,
        @Assisted private val screen: AppConfigScreen,
        private val displayRepository: TrmnlDisplayRepository,
        private val tokenManager: TokenManager,
    ) : Presenter<AppConfigScreen.State> {
        @Composable
        override fun present(): AppConfigScreen.State {
            var accessToken by remember { mutableStateOf("") }
            var isLoading by remember { mutableStateOf(false) }
            var validationResult by remember { mutableStateOf<AppConfigScreen.ValidationResult?>(null) }
            val scope = rememberCoroutineScope()

            // Load saved token if available
            LaunchedEffect(Unit) {
                tokenManager.accessTokenFlow.collect { savedToken ->
                    if (!savedToken.isNullOrBlank()) {
                        accessToken = savedToken
                    }
                }
            }

            return AppConfigScreen.State(
                accessToken = accessToken,
                isLoading = isLoading,
                validationResult = validationResult,
                eventSink = { event ->
                    when (event) {
                        is AppConfigScreen.Event.AccessTokenChanged -> {
                            accessToken = event.token
                            // Clear previous validation when token changes
                            validationResult = null
                        }

                        AppConfigScreen.Event.ValidateToken -> {
                            scope.launch {
                                isLoading = true
                                validationResult = null

                                try {
                                    val response = displayRepository.getDisplayData(accessToken)

                                    if (response.status == 500) {
                                        // Handle explicit error response
                                        val errorMessage = response.error ?: "Device not found"
                                        validationResult = AppConfigScreen.ValidationResult.Failure(errorMessage)
                                    } else if (response.imageUrl != null) {
                                        // Success case - we have an image URL
                                        validationResult = AppConfigScreen.ValidationResult.Success(response.imageUrl)
                                    } else {
                                        // No error but also no image URL
                                        validationResult = AppConfigScreen.ValidationResult.Failure("No image URL received")
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    validationResult =
                                        AppConfigScreen.ValidationResult.Failure(
                                            e.message ?: "Unknown network error",
                                        )
                                } finally {
                                    isLoading = false
                                }
                            }
                        }

                        AppConfigScreen.Event.SaveAndContinue -> {
                            // Only save if validation was successful
                            if (validationResult is AppConfigScreen.ValidationResult.Success) {
                                scope.launch {
                                    tokenManager.saveAccessToken(accessToken)

                                    if (screen.returnToMirrorAfterSave) {
                                        navigator.goTo(TrmnlMirrorDisplayScreen())
                                    } else {
                                        navigator.pop()
                                    }
                                }
                            }
                        }

                        AppConfigScreen.Event.BackPressed -> {
                            navigator.pop()
                        }
                    }
                },
            )
        }

        @CircuitInject(AppConfigScreen::class, AppScope::class)
        @AssistedFactory
        fun interface Factory {
            fun create(
                navigator: Navigator,
                screen: AppConfigScreen,
            ): AppConfigPresenter
        }
    }

@CircuitInject(AppConfigScreen::class, AppScope::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppConfigContent(
    state: AppConfigScreen.State,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("TRMNL Configuration") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Terminal API Configuration",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = state.accessToken,
                    onValueChange = { state.eventSink(AppConfigScreen.Event.AccessTokenChanged(it)) },
                    label = { Text("Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { state.eventSink(AppConfigScreen.Event.ValidateToken) },
                    enabled = state.accessToken.isNotBlank() && !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Validate Token")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Show validation result
                state.validationResult?.let { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        when (result) {
                            is AppConfigScreen.ValidationResult.Success -> {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        "✅ Token Valid",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Image URL: ${result.imageUrl}")
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { state.eventSink(AppConfigScreen.Event.SaveAndContinue) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Save and Continue")
                                    }
                                }
                            }
                            is AppConfigScreen.ValidationResult.Failure -> {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        "❌ Validation Failed",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        result.message,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            }
        }
    }
}
