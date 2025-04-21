package dev.hossain.trmnl.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
import dev.hossain.trmnl.data.AppConfig.DEFAULT_REFRESH_RATE_SEC
import dev.hossain.trmnl.data.ImageMetadata
import dev.hossain.trmnl.data.TrmnlDisplayRepository
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.ui.display.TrmnlMirrorDisplayScreen
import dev.hossain.trmnl.ui.settings.AppSettingsScreen.ValidationResult
import dev.hossain.trmnl.util.CoilRequestUtils
import dev.hossain.trmnl.util.TokenManager
import dev.hossain.trmnl.util.toColor
import dev.hossain.trmnl.util.toDisplayString
import dev.hossain.trmnl.util.toIcon
import dev.hossain.trmnl.work.TrmnlImageUpdateManager
import dev.hossain.trmnl.work.TrmnlWorkScheduler
import dev.hossain.trmnl.work.TrmnlWorkScheduler.Companion.IMAGE_REFRESH_PERIODIC_WORK_NAME
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Screen for configuring the TRMNL token and other things.
 */
@Parcelize
data class AppSettingsScreen(
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
            val refreshRateSecs: Long,
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

class AppSettingsPresenter
    @AssistedInject
    constructor(
        @Assisted private val navigator: Navigator,
        @Assisted private val screen: AppSettingsScreen,
        private val displayRepository: TrmnlDisplayRepository,
        private val tokenManager: TokenManager,
        private val trmnlWorkScheduler: TrmnlWorkScheduler,
        private val trmnlImageUpdateManager: TrmnlImageUpdateManager,
    ) : Presenter<AppSettingsScreen.State> {
        @Composable
        override fun present(): AppSettingsScreen.State {
            var accessToken by remember { mutableStateOf("") }
            var isLoading by remember { mutableStateOf(false) }
            var validationResult by remember { mutableStateOf<ValidationResult?>(null) }
            val scope = rememberCoroutineScope()

            // Load saved token if available
            LaunchedEffect(Unit) {
                tokenManager.accessTokenFlow.collect { savedToken ->
                    if (!savedToken.isNullOrBlank()) {
                        accessToken = savedToken
                    }
                }
            }

            return AppSettingsScreen.State(
                accessToken = accessToken,
                isLoading = isLoading,
                validationResult = validationResult,
                eventSink = { event ->
                    when (event) {
                        is AppSettingsScreen.Event.AccessTokenChanged -> {
                            accessToken = event.token
                            // Clear previous validation when token changes
                            validationResult = null
                        }

                        AppSettingsScreen.Event.ValidateToken -> {
                            scope.launch {
                                isLoading = true
                                validationResult = null

                                try {
                                    val response = displayRepository.getDisplayData(accessToken)

                                    if (response.status == 500) {
                                        // Handle explicit error response
                                        val errorMessage = response.error ?: "Device not found"
                                        validationResult = ValidationResult.Failure(errorMessage)
                                    } else if (response.imageUrl.isNotBlank()) {
                                        // Success case - we have an image URL
                                        trmnlImageUpdateManager.updateImage(
                                            ImageMetadata(
                                                url = response.imageUrl,
                                                timestamp = System.currentTimeMillis(),
                                                refreshRateSecs = response.refreshRateSecs,
                                            ),
                                        )
                                        validationResult =
                                            ValidationResult.Success(
                                                response.imageUrl,
                                                response.refreshRateSecs ?: DEFAULT_REFRESH_RATE_SEC,
                                            )
                                    } else {
                                        // No error but also no image URL
                                        validationResult = ValidationResult.Failure("No image URL received")
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    validationResult =
                                        ValidationResult.Failure(
                                            e.message ?: "Unknown network error",
                                        )
                                } finally {
                                    isLoading = false
                                }
                            }
                        }

                        AppSettingsScreen.Event.SaveAndContinue -> {
                            // Only save if validation was successful
                            val result = validationResult
                            if (result is ValidationResult.Success) {
                                scope.launch {
                                    tokenManager.saveAccessToken(accessToken)
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
                    .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
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

            // Use masked token for display but keep actual token for validation
            OutlinedTextField(
                value = state.accessToken,
                onValueChange = { state.eventSink(AppSettingsScreen.Event.AccessTokenChanged(it)) },
                label = { Text("Access Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation =
                    if (state.accessToken.length > 4) {
                        VisualTransformation { text ->
                            val maskedText =
                                buildString {
                                    text.text.forEachIndexed { index, char ->
                                        append(
                                            when {
                                                index < 2 || index >= text.text.length - 2 -> char
                                                else -> '*'
                                            },
                                        )
                                    }
                                }
                            TransformedText(
                                AnnotatedString(maskedText),
                                OffsetMapping.Identity,
                            )
                        }
                    } else {
                        VisualTransformation.None
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
            WorkScheduleStatusCard(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WorkScheduleStatusCard(
    modifier: Modifier = Modifier,
    workManager: WorkManager = WorkManager.getInstance(LocalContext.current),
    workName: String = IMAGE_REFRESH_PERIODIC_WORK_NAME,
) {
    val context = LocalContext.current
    var workInfo by remember { mutableStateOf<WorkInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Fetch work info
    LaunchedEffect(workName) {
        isLoading = true
        workManager
            .getWorkInfosForUniqueWorkLiveData(workName)
            .asFlow()
            .collect { workInfoList ->
                workInfo = workInfoList.firstOrNull()
                isLoading = false
            }
    }

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

            if (isLoading) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp),
                )
            } else if (workInfo == null) {
                Text(
                    text = "No scheduled refresh work found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = workInfo?.state.toIcon(),
                        contentDescription = null,
                        tint = workInfo?.state.toColor(),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Status: ${workInfo?.state.toDisplayString()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = workInfo?.state.toColor(),
                    )
                }

                // Show next schedule time if available
                val nextScheduleTimeMillis = workInfo?.nextScheduleTimeMillis ?: 0L
                if (nextScheduleTimeMillis > 0 && nextScheduleTimeMillis != Long.MAX_VALUE) {
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    val nextRunTime =
                        Instant
                            .ofEpochMilli(nextScheduleTimeMillis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(formatter)

                    val timeUntil = nextScheduleTimeMillis - System.currentTimeMillis()
                    val timeUntilText =
                        when {
                            timeUntil <= 0 -> "any moment now"
                            timeUntil < 60000 -> "in ${timeUntil / 1000} seconds"
                            timeUntil < 3600000 -> "in ${timeUntil / 60000} minutes"
                            else -> "in ${timeUntil / 3600000} hours"
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
                            text = "Next refresh: $timeUntilText",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Text(
                        text = "Scheduled for: $nextRunTime",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 28.dp, top = 2.dp),
                    )

                    // Add a button to cancel the work if it's scheduled
                    if (workInfo?.state == WorkInfo.State.ENQUEUED ||
                        workInfo?.state == WorkInfo.State.RUNNING ||
                        workInfo?.state == WorkInfo.State.BLOCKED
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    workManager.cancelUniqueWork(workName)
                                }
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
                }
            }
        }
    }
}
