package dev.hossain.trmnl.ui.refreshlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import dev.hossain.trmnl.BuildConfig
import dev.hossain.trmnl.data.log.TrmnlRefreshLog
import dev.hossain.trmnl.data.log.TrmnlRefreshLogManager
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.work.TrmnlWorkManager
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A screen that displays the refresh logs of the TRMNL display.
 * This is meant to validate how often the refresh rate is set and when the image is updated.
 *
 * The screen provides functionality to:
 * - View chronological logs of display refresh attempts
 * - See detailed information about successful and failed refresh operations
 * - Clear logs (via action button)
 * - Add test logs (debug builds only)
 */
@Parcelize
data object DisplayRefreshLogScreen : Screen {
    /**
     * Represents the UI state for the [DisplayRefreshLogScreen].
     */
    data class State(
        val logs: List<TrmnlRefreshLog>,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    /**
     * Events that can be triggered from the DisplayRefreshLogScreen UI.
     */
    sealed class Event : CircuitUiEvent {
        /**
         * Event triggered when the user presses the back button.
         */
        data object BackPressed : Event()

        /**
         * Event triggered when the user requests to clear all logs.
         */
        data object ClearLogs : Event()

        /**
         * Event triggered when a test success log should be added (debug only).
         */
        data object AddSuccessLog : Event()

        /**
         * Event triggered when a test failure log should be added (debug only).
         */
        data object AddFailLog : Event()

        /**
         * Event triggered when the refresh worker should be started (debug only).
         */
        data object StartRefreshWorker : Event()
    }
}

/**
 * Presenter for the DisplayRefreshLogScreen.
 * Manages the screen's state and handles events from the UI.
 */
class DisplayRefreshLogPresenter
    @AssistedInject
    constructor(
        @Assisted private val navigator: Navigator,
        private val activityLogManager: TrmnlRefreshLogManager,
        private val trmnlWorkManager: TrmnlWorkManager,
    ) : Presenter<DisplayRefreshLogScreen.State> {
        /**
         * Creates and returns the state for the DisplayRefreshLogScreen.
         * Collects logs from the log manager and sets up event handling.
         *
         * @return The current UI state.
         */
        @Composable
        override fun present(): DisplayRefreshLogScreen.State {
            val logs by activityLogManager.logsFlow.collectAsState(initial = emptyList())
            val scope = rememberCoroutineScope()

            return DisplayRefreshLogScreen.State(
                logs = logs,
                eventSink = { event ->
                    when (event) {
                        DisplayRefreshLogScreen.Event.BackPressed -> navigator.pop()
                        DisplayRefreshLogScreen.Event.ClearLogs -> {
                            scope.launch {
                                activityLogManager.clearLogs()
                            }
                        }

                        DisplayRefreshLogScreen.Event.AddFailLog -> {
                            scope.launch {
                                activityLogManager.addLog(
                                    TrmnlRefreshLog.createFailure(error = "Test failure"),
                                )
                            }
                        }
                        DisplayRefreshLogScreen.Event.AddSuccessLog -> {
                            scope.launch {
                                activityLogManager.addLog(
                                    TrmnlRefreshLog.createSuccess(
                                        imageUrl = "https://debug.example.com/image.png",
                                        refreshRateSeconds = 300L,
                                    ),
                                )
                            }
                        }

                        DisplayRefreshLogScreen.Event.StartRefreshWorker -> {
                            trmnlWorkManager.startOneTimeImageRefreshWork()
                        }
                    }
                },
            )
        }

        /**
         * Factory interface for creating DisplayRefreshLogPresenter instances.
         */
        @CircuitInject(DisplayRefreshLogScreen::class, AppScope::class)
        @AssistedFactory
        fun interface Factory {
            fun create(navigator: Navigator): DisplayRefreshLogPresenter
        }
    }

/**
 * Main composable function for rendering the DisplayRefreshLogScreen.
 * Sets up the screen's structure including toolbar, log list, and debug controls.
 */
@CircuitInject(DisplayRefreshLogScreen::class, AppScope::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayRefreshLogContent(
    state: DisplayRefreshLogScreen.State,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Display Refresh Logs") },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(DisplayRefreshLogScreen.Event.BackPressed) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { state.eventSink(DisplayRefreshLogScreen.Event.ClearLogs) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear logs")
                    }
                },
            )
        },
        bottomBar = {
            // Debug controls only visible in debug builds
            if (BuildConfig.DEBUG) {
                DebugControls(
                    onAddSuccessLog = {
                        state.eventSink(DisplayRefreshLogScreen.Event.AddSuccessLog)
                    },
                    onAddFailLog = {
                        state.eventSink(DisplayRefreshLogScreen.Event.AddFailLog)
                    },
                    onStartRefreshWorker = {
                        state.eventSink(DisplayRefreshLogScreen.Event.StartRefreshWorker)
                    },
                    // Use a modifier that takes navigation bar padding into account
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        },
        // Use WindowInsets.navigationBars to ensure content doesn't overlap with the navigation bar
        contentWindowInsets = WindowInsets.navigationBars,
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            if (state.logs.isEmpty()) {
                Text(
                    text = "No logs available",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(state.logs) { log ->
                        LogItem(log = log)
                    }
                }
            }
        }
    }
}

/**
 * Composable function that renders a single log entry as a card.
 * Displays different information based on whether the log represents a success or failure.
 */
@Composable
private fun LogItem(
    log: TrmnlRefreshLog,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Format timestamp
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(log.timestamp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                )

                Text(
                    text = if (log.success) "✅ Success" else "❌ Failed",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (log.success) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            if (log.success) {
                Text(
                    text = "Image URL:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = log.imageUrl ?: "N/A",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = "Refresh Rate: ${log.refreshRateSeconds ?: "N/A"} seconds",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Text(
                    text = "Error:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = log.error ?: "Unknown error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Debug controls for manually adding test logs. Only visible in debug builds.
 */
@Composable
private fun DebugControls(
    onAddSuccessLog: () -> Unit,
    onAddFailLog: () -> Unit,
    onStartRefreshWorker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Text(
            text = "Debug Controls (for testing)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(
                onClick = onAddSuccessLog,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            ) {
                Text("Add Success Log")
            }

            Button(
                onClick = onAddFailLog,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text("Add Fail Log")
            }
        }
        Button(
            onClick = onStartRefreshWorker,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                ),
        ) {
            Text("Start Refresh Worker")
        }
    }
}
