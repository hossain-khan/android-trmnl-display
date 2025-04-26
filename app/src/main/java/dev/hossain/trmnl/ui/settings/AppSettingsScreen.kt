package dev.hossain.trmnl.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
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
import dev.hossain.trmnl.R
import dev.hossain.trmnl.data.AppConfig.DEFAULT_REFRESH_INTERVAL_SEC
import dev.hossain.trmnl.data.DevConfig
import dev.hossain.trmnl.data.TrmnlDisplayRepository
import dev.hossain.trmnl.data.TrmnlTokenDataStore
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.ui.display.TrmnlMirrorDisplayScreen
import dev.hossain.trmnl.ui.settings.AppSettingsScreen.ValidationResult
import dev.hossain.trmnl.ui.settings.AppSettingsScreen.ValidationResult.*
import dev.hossain.trmnl.util.CoilRequestUtils
import dev.hossain.trmnl.util.NextImageRefreshDisplayInfo
import dev.hossain.trmnl.util.isHttpError
import dev.hossain.trmnl.util.nextRunTime
import dev.hossain.trmnl.util.toColor
import dev.hossain.trmnl.util.toDisplayString
import dev.hossain.trmnl.util.toIcon
import dev.hossain.trmnl.work.TrmnlImageUpdateManager
import dev.hossain.trmnl.work.TrmnlWorkScheduler
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

/**
 * Screen for configuring the TRMNL mirror app settings.
 *
 * This screen allows users to set up and validate their TRMNL access token,
 * which is required to connect to the TRMNL API service. It displays validation
 * results, image previews when successful, and provides options to save the
 * configuration which in turn schedules refresh job using [TrmnlWorkScheduler].
 *
 * The screen can be configured to either return to the mirror display after
 * saving or to pop back to the previous screen.
 */
@Parcelize
data class AppSettingsScreen(
    val returnToMirrorAfterSave: Boolean = false,
) : Screen {
    data class State(
        val accessToken: String,
        val isLoading: Boolean = false,
        val validationResult: ValidationResult? = null,
        val nextRefreshJobInfo: NextImageRefreshDisplayInfo? = null,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed class ValidationResult {
        data class Success(
            val imageUrl: String,
            val refreshRateSecs: Long,
        ) : ValidationResult()

        data class Failure(
            val message: String,
        ) : ValidationResult()
    }

    /**
     * Events that can be triggered from the AppSettingsScreen UI.
     */
    sealed class Event : CircuitUiEvent {
        /**
         * Event triggered when the access token is changed.
         */
        data class AccessTokenChanged(
            val token: String,
        ) : Event()

        /**
         * Event triggered to validate the current access token.
         */
        data object ValidateToken : Event()

        /**
         * Event triggered to save the settings and continue to the next screen.
         */
        data object SaveAndContinue : Event()

        /**
         * Event triggered when the back button is pressed.
         */
        data object BackPressed : Event()

        /**
         * Event triggered to cancel the scheduled work.
         */
        data object CancelScheduledWork : Event()
    }
}

/**
 * Presenter for the [AppSettingsScreen].
 * Manages the screen's state and handles events from the UI.
 */
class AppSettingsPresenter
    @AssistedInject
    constructor(
        @Assisted private val navigator: Navigator,
        @Assisted private val screen: AppSettingsScreen,
        private val displayRepository: TrmnlDisplayRepository,
        private val trmnlTokenDataStore: TrmnlTokenDataStore,
        private val trmnlWorkScheduler: TrmnlWorkScheduler,
        private val trmnlImageUpdateManager: TrmnlImageUpdateManager,
    ) : Presenter<AppSettingsScreen.State> {
        @Composable
        override fun present(): AppSettingsScreen.State {
            var accessToken by remember { mutableStateOf("") }
            var isLoading by remember { mutableStateOf(false) }
            var validationResult by remember { mutableStateOf<ValidationResult?>(null) }
            val scope = rememberCoroutineScope()
            val focusManager = LocalFocusManager.current

            val nextRefreshInfo by produceState<NextImageRefreshDisplayInfo?>(null) {
                trmnlWorkScheduler.getScheduledWorkInfo().collect { workInfo ->
                    value = workInfo?.nextRunTime()
                }
            }

            // Load saved token if available
            LaunchedEffect(Unit) {
                trmnlTokenDataStore.accessTokenFlow.collect { savedToken ->
                    if (!savedToken.isNullOrBlank()) {
                        accessToken = savedToken
                    }
                }
            }

            return AppSettingsScreen.State(
                accessToken = accessToken,
                isLoading = isLoading,
                validationResult = validationResult,
                nextRefreshJobInfo = nextRefreshInfo,
                eventSink = { event ->
                    when (event) {
                        is AppSettingsScreen.Event.AccessTokenChanged -> {
                            accessToken = event.token
                            // Clear previous validation when token changes
                            validationResult = null
                        }

                        AppSettingsScreen.Event.ValidateToken -> {
                            scope.launch {
                                focusManager.clearFocus()
                                isLoading = true
                                validationResult = null

                                val response = displayRepository.getCurrentDisplayData(accessToken)

                                if (response.status.isHttpError()) {
                                    // Handle explicit error response
                                    val errorMessage = response.error ?: "Device not found"
                                    validationResult = Failure(errorMessage)
                                } else if (response.imageUrl.isNotBlank()) {
                                    // Success case - we have an image URL
                                    trmnlImageUpdateManager.updateImage(response.imageUrl, response.refreshIntervalSeconds)
                                    validationResult =
                                        Success(
                                            response.imageUrl,
                                            response.refreshIntervalSeconds ?: DEFAULT_REFRESH_INTERVAL_SEC,
                                        )
                                } else {
                                    // No error but also no image URL
                                    val errorMessage = response.error ?: ""
                                    validationResult = Failure("$errorMessage No image URL received.")
                                }
                                isLoading = false
                            }
                        }

                        AppSettingsScreen.Event.SaveAndContinue -> {
                            // Only save if validation was successful
                            val result = validationResult
                            if (result is ValidationResult.Success) {
                                scope.launch {
                                    trmnlTokenDataStore.saveAccessToken(accessToken)
                                    trmnlWorkScheduler.updateRefreshInterval(result.refreshRateSecs)

                                    if (screen.returnToMirrorAfterSave) {
                                        navigator.goTo(TrmnlMirrorDisplayScreen)
                                    } else {
                                        navigator.pop()
                                    }
                                }
                            }
                        }

                        AppSettingsScreen.Event.BackPressed -> {
                            navigator.pop()
                        }

                        AppSettingsScreen.Event.CancelScheduledWork -> {
                            trmnlWorkScheduler.cancelImageRefreshWork()
                        }
                    }
                },
            )
        }

        @CircuitInject(AppSettingsScreen::class, AppScope::class)
        @AssistedFactory
        fun interface Factory {
            fun create(
                navigator: Navigator,
                screen: AppSettingsScreen,
            ): AppSettingsPresenter
        }
    }

/**
 * Main Composable function for rendering the AppSettingsScreen.
 * Sets up the screen's structure including form, validation result display, and work schedule status.
 */
@CircuitInject(AppSettingsScreen::class, AppScope::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsContent(
    state: AppSettingsScreen.State,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val hasToken = state.accessToken.isNotBlank()
    val trmnlWorkScheduler = remember { TrmnlWorkScheduler(context, TrmnlTokenDataStore(context)) }

    // Control password visibility
    var passwordVisible by remember { mutableStateOf(false) }

    // Create masked version of the token for display
    val maskedToken =
        with(state.accessToken) {
            if (length > 4) {
                "${take(2)}${"*".repeat(length - 4)}${takeLast(2)}"
            } else {
                this // Don't mask if token is too short
            }
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("TRMNL Configuration") },
                navigationIcon = {
                    // Only show the back button if a token is already set
                    if (hasToken) {
                        IconButton(onClick = { state.eventSink(AppSettingsScreen.Event.BackPressed) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp)
                    .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (DevConfig.FAKE_API_RESPONSE) {
                // Show fake API banner when DevConfig.FAKE_API_RESPONSE is `true`
                FakeApiInfoBanner(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                )
            }

            Icon(
                painter = painterResource(R.drawable.trmnl_logo_plain),
                contentDescription = "TRMNL Logo",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .size(64.dp)
                        .padding(bottom = 16.dp),
            )

            Text(
                text = "Terminal API Configuration",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Password field with toggle visibility button
            OutlinedTextField(
                value = state.accessToken,
                onValueChange = { state.eventSink(AppSettingsScreen.Event.AccessTokenChanged(it)) },
                label = { Text("Access Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painter = painterResource(if (passwordVisible) R.drawable.visibility_off_24dp else R.drawable.visibility_24dp),
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { state.eventSink(AppSettingsScreen.Event.ValidateToken) },
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
                        is ValidationResult.Success -> {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "✅ Token Valid",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )

                                Text(
                                    "Token: $maskedToken",
                                    style = MaterialTheme.typography.bodyMedium,
                                )

                                // Image preview using Coil with improved caching
                                AsyncImage(
                                    model = CoilRequestUtils.createCachedImageRequest(context, result.imageUrl),
                                    contentDescription = "Preview image",
                                    contentScale = ContentScale.Fit,
                                    modifier =
                                        Modifier
                                            .size(240.dp)
                                            .padding(4.dp),
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { state.eventSink(AppSettingsScreen.Event.SaveAndContinue) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Save and Continue")
                                }
                            }
                        }
                        is ValidationResult.Failure -> {
                            // Error state remains the same
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

            Spacer(modifier = Modifier.height(24.dp))
            WorkScheduleStatusCard(state = state, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WorkScheduleStatusCard(
    state: AppSettingsScreen.State,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "TRMNL Display Image Refresh Schedule Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            val nextRefreshJobInfo: NextImageRefreshDisplayInfo? = state.nextRefreshJobInfo
            if (nextRefreshJobInfo != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = nextRefreshJobInfo.workerState.toIcon(),
                        contentDescription = null,
                        tint = nextRefreshJobInfo.workerState.toColor(),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Status: ${nextRefreshJobInfo.workerState.toDisplayString()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = nextRefreshJobInfo.workerState.toColor(),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Next refresh: ${nextRefreshJobInfo.timeUntilNextRefresh}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    text = "Scheduled for: ${nextRefreshJobInfo.nextRefreshOnDateTime}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 28.dp, top = 2.dp),
                )

                // Add a button to cancel the work if it's scheduled
                if (nextRefreshJobInfo.workerState == WorkInfo.State.ENQUEUED ||
                    nextRefreshJobInfo.workerState == WorkInfo.State.RUNNING ||
                    nextRefreshJobInfo.workerState == WorkInfo.State.BLOCKED
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            state.eventSink(AppSettingsScreen.Event.CancelScheduledWork)
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Cancel scheduled work",
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel Periodic Refresh Job")
                    }
                }
            } else {
                Text(
                    text = "No scheduled refresh work found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FakeApiInfoBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )

            Column {
                Text(
                    text = "Developer Mode - Using Fake API",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = "This app is currently using mock data instead of real API calls.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Text(
                    text = "Set `DevConfig.FAKE_API_RESPONSE` to `false` to use real API.",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}
