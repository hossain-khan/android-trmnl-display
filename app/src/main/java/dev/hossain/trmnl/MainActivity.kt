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
import dev.hossain.trmnl.ui.theme.TrmnlDisplayAppTheme
import dev.hossain.trmnl.work.TrmnlImageRefreshWorker
import dev.hossain.trmnl.work.TrmnlImageUpdateManager
import dev.hossain.trmnl.work.TrmnlWorkScheduler.Companion.IMAGE_REFRESH_ONETIME_WORK_NAME
import dev.hossain.trmnl.work.TrmnlWorkScheduler.Companion.IMAGE_REFRESH_ONETIME_WORK_TAG
import dev.hossain.trmnl.work.TrmnlWorkScheduler.Companion.IMAGE_REFRESH_PERIODIC_WORK_NAME
import dev.hossain.trmnl.work.TrmnlWorkScheduler.Companion.IMAGE_REFRESH_PERIODIC_WORK_TAG
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
            listenForWorkUpdatesV2()

            setContent {
                TrmnlDisplayAppTheme {
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
            val workManager = WorkManager.getInstance(context)

            // Create a reusable observer function
            fun observeWork(workName: String) {
                workManager.getWorkInfosForUniqueWorkLiveData(workName).observe(this) { workInfos ->
                    // ⚠️ DEV NOTE: On app launch, previously ran work info is broadcasted here,
                    // so it may result in inconsistent behavior where it remembers last result.
                    workInfos.forEach { workInfo ->
                        when (workInfo.state) {
                            WorkInfo.State.SUCCEEDED, WorkInfo.State.ENQUEUED -> {
                                Timber.d("$workName work ${workInfo.state.name.lowercase()}: $workInfo")
                                val newImageUrl =
                                    workInfo.outputData.getString(
                                        TrmnlImageRefreshWorker.KEY_NEW_IMAGE_URL,
                                    )

                                if (newImageUrl != null) {
                                    Timber.i("New image URL from $workName: $newImageUrl")
                                    trmnlImageUpdateManager.updateImage(
                                        ImageMetadata(
                                            url = newImageUrl,
                                            timestamp = System.currentTimeMillis(),
                                            refreshRateSecs = null,
                                        ),
                                    )
                                }
                            }
                            WorkInfo.State.FAILED -> {
                                val error = workInfo.outputData.getString(TrmnlImageRefreshWorker.KEY_ERROR_MESSAGE)
                                Timber.e("$workName work failed: $error")
                                trmnlImageUpdateManager.updateImage(
                                    ImageMetadata(
                                        url = "",
                                        timestamp = System.currentTimeMillis(),
                                        refreshRateSecs = null,
                                        errorMessage = error,
                                    ),
                                )
                            }
                            else -> {
                                Timber.d("$workName work state updated: ${workInfo.state}")
                            }
                        }
                    }
                }
            }

            // Observe both work types
            observeWork(IMAGE_REFRESH_PERIODIC_WORK_NAME)
            observeWork(IMAGE_REFRESH_ONETIME_WORK_NAME)
        }

        private fun listenForWorkUpdatesV2() {
            val workManager = WorkManager.getInstance(context)

            // Observe work by tag instead of unique work name
            fun observeWorkByTag(tag: String) {
                workManager.getWorkInfosByTagLiveData(tag).observe(this) { workInfos ->
                    workInfos.forEach { workInfo ->
                        // Log detailed information about work state and data
                        Timber.d("$tag work state updated: ${workInfo.state}")
                        Timber.d("$tag work progress data: ${workInfo.progress.keyValueMap}")
                        Timber.d("$tag work output data: ${workInfo.outputData.keyValueMap}")

                        // First check in the progress data (for both periodic and one-time work)
                        val progressUrl = workInfo.progress.getString(TrmnlImageRefreshWorker.KEY_NEW_IMAGE_URL)
                        if (progressUrl != null) {
                            Timber.i("New image URL from $tag progress data: $progressUrl")
                            trmnlImageUpdateManager.updateImage(
                                ImageMetadata(
                                    url = progressUrl,
                                    timestamp = System.currentTimeMillis(),
                                    refreshRateSecs = null,
                                ),
                            )
                            return@forEach // Found valid data, no need to check further
                        }

                        // Then check in the output data (mainly for one-time work)
                        val outputUrl = workInfo.outputData.getString(TrmnlImageRefreshWorker.KEY_NEW_IMAGE_URL)
                        if (outputUrl != null) {
                            Timber.i("New image URL from $tag output data: $outputUrl")
                            trmnlImageUpdateManager.updateImage(
                                ImageMetadata(
                                    url = outputUrl,
                                    timestamp = System.currentTimeMillis(),
                                    refreshRateSecs = null,
                                ),
                            )
                            return@forEach // Found valid data, no need to check further
                        }

                        // Handle failure state
                        if (workInfo.state == WorkInfo.State.FAILED) {
                            val error = workInfo.outputData.getString(TrmnlImageRefreshWorker.KEY_ERROR_MESSAGE)
                            Timber.e("$tag work failed: $error")
                            trmnlImageUpdateManager.updateImage(
                                ImageMetadata(
                                    url = "",
                                    timestamp = System.currentTimeMillis(),
                                    refreshRateSecs = null,
                                    errorMessage = error,
                                ),
                            )
                        }
                    }
                }
            }

            // Observe both work types by their tags
            observeWorkByTag(IMAGE_REFRESH_PERIODIC_WORK_TAG)
            observeWorkByTag(IMAGE_REFRESH_ONETIME_WORK_TAG)
        }
    }
