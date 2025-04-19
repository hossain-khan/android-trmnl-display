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
import dev.hossain.trmnl.di.ActivityKey
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.di.ApplicationContext
import dev.hossain.trmnl.ui.display.TrmnlMirrorDisplayScreen
import dev.hossain.trmnl.ui.theme.CircuitAppTheme
import dev.hossain.trmnl.work.TrmnlImageRefreshWorker
import dev.hossain.trmnl.work.TrmnlImageUpdateManager
import dev.hossain.trmnl.work.TrmnlWorkManager.Companion.IMAGE_REFRESH_PERIODIC_WORK_NAME
import timber.log.Timber
import javax.inject.Inject

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

        private fun listenForWorkUpdates() {
            // Listen for work results
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(IMAGE_REFRESH_PERIODIC_WORK_NAME)
                .observe(this) { workInfos ->
                    workInfos.forEach { workInfo ->
                        Timber.d("Received WorkInfo: $workInfo")
                        if (workInfo.state == WorkInfo.State.SUCCEEDED) {
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
                                    Timber.d("New image URL: $newImageUrl")
                                    // Update the image URL in your UI
                                    trmnlImageUpdateManager.updateImage(newImageUrl)
                                }
                            }
                        }
                    }
                }
        }
    }
