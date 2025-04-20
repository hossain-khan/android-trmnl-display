package dev.hossain.trmnl

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.remember
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.overlay.ContentWithOverlays
import com.slack.circuit.sharedelements.SharedElementTransitionLayout
import com.slack.circuitx.gesturenavigation.GestureNavigationDecorationFactory
import com.squareup.anvil.annotations.ContributesMultibinding
import dev.hossain.trmnl.data.ImageMetadata
import dev.hossain.trmnl.di.ActivityKey
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.di.ApplicationContext
import dev.hossain.trmnl.ui.display.TrmnlMirrorDisplayScreen
import dev.hossain.trmnl.ui.theme.CircuitAppTheme
import dev.hossain.trmnl.work.TrmnlImageRefreshWorker
import dev.hossain.trmnl.work.TrmnlImageUpdateManager
import dev.hossain.trmnl.work.TrmnlWorkManager.Companion.IMAGE_REFRESH_ONETIME_WORK_NAME
import dev.hossain.trmnl.work.TrmnlWorkManager.Companion.IMAGE_REFRESH_PERIODIC_WORK_NAME
import timber.log.Timber
import javax.inject.Inject

/**
 * Main activity for the TRMNL display mirror app.
 * This activity sets up the Circuit framework and handles navigation.
 */
@ContributesMultibinding(AppScope::class, boundType = Activity::class)
@ActivityKey(MainActivity::class)
class MainActivity
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val circuit: Circuit,
        private val trmnlImageUpdateManager: TrmnlImageUpdateManager,
    ) : ComponentActivity() {
        @OptIn(ExperimentalSharedTransitionApi::class)
        override fun onCreate(savedInstanceState: Bundle?) {
            enableEdgeToEdge()
            super.onCreate(savedInstanceState)

            // Setup listener for TRMNL display image updates
            listenForWorkUpdates()

            setContent {
                CircuitAppTheme {
                    // See https://slackhq.github.io/circuit/navigation/
                    val backStack = rememberSaveableBackStack(root = TrmnlMirrorDisplayScreen)
                    val navigator = rememberCircuitNavigator(backStack)

                    // See https://slackhq.github.io/circuit/circuit-content/
                    CircuitCompositionLocals(circuit) {
                        // See https://slackhq.github.io/circuit/shared-elements/
                        SharedElementTransitionLayout {
                            // See https://slackhq.github.io/circuit/overlays/
                            ContentWithOverlays {
                                NavigableCircuitContent(
                                    navigator = navigator,
                                    backStack = backStack,
                                    decoratorFactory =
                                        remember(navigator) {
                                            GestureNavigationDecorationFactory(onBackInvoked = navigator::pop)
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }

        /**
         * Sets up observers for WorkManager work updates.
         *
         * This function:
         * 1. Listens for periodic image refresh work results
         * 2. Listens for one-time image refresh work results
         * 3. Updates the application with new images when available
         * 4. Logs work status and errors
         */
        private fun listenForWorkUpdates() {
            // Listen for work results
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(IMAGE_REFRESH_PERIODIC_WORK_NAME)
                .observe(this) { workInfos ->
                    workInfos.forEach { workInfo ->
                        if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                            Timber.d("Periodic work succeeded: $workInfo")
                            val hasNewImage =
                                workInfo.outputData.getBoolean(
                                    TrmnlImageRefreshWorker.KEY_HAS_NEW_IMAGE,
                                    false,
                                )

                            if (hasNewImage) {
                                val newImageUrl =
                                    workInfo.outputData.getString(
                                        TrmnlImageRefreshWorker.KEY_NEW_IMAGE_URL,
                                    )

                                if (newImageUrl != null) {
                                    Timber.d("New image URL from periodic work: $newImageUrl")
                                    // Update the image URL via the manager
                                    trmnlImageUpdateManager.updateImage(
                                        ImageMetadata(
                                            url = newImageUrl,
                                            timestamp = System.currentTimeMillis(),
                                            refreshRateSecs = null,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

            // Also listen for one-time work results
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(IMAGE_REFRESH_ONETIME_WORK_NAME)
                .observe(this) { workInfos ->
                    workInfos.forEach { workInfo ->
                        if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                            Timber.d("One-time work succeeded: $workInfo")
                            val newImageUrl =
                                workInfo.outputData.getString(
                                    TrmnlImageRefreshWorker.KEY_NEW_IMAGE_URL,
                                )

                            if (newImageUrl != null) {
                                Timber.d("New image URL from one-time work: $newImageUrl")
                                trmnlImageUpdateManager.updateImage(
                                    ImageMetadata(
                                        url = newImageUrl,
                                        timestamp = System.currentTimeMillis(),
                                        refreshRateSecs = null,
                                    ),
                                )
                            }
                        } else if (workInfo.state == WorkInfo.State.FAILED) {
                            val error = workInfo.outputData.getString(TrmnlImageRefreshWorker.KEY_ERROR)
                            Timber.e("One-time work failed: $error")
                            // Optionally update UI with error state
                        }
                    }
                }
        }
    }
