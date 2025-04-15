package dev.hossain.trmnl.ui

import android.app.Activity
import android.content.Context
import android.os.PowerManager
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * A utility composable that handles fullscreen mode for the current screen.
 * Hides system bars (status bar and navigation bar) when active.
 *
 * It also keeps the screen awake, useful for showing TRMNL screens and updates.
 */
@Composable
fun FullScreenMode(
    enabled: Boolean = true,
    keepScreenOn: Boolean = false,
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    // Use a PowerManager wake lock with stronger flags for e-ink devices
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val wakeLock =
        remember {
            powerManager.newWakeLock(
                // ⚠️ WARNING: Both these flags are deprecated.
                // ⛔️ If you ever plan to go to Google Play, these should be removed ⛔️
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "dev.hossain.trmnl:WakeLock",
            )
        }

    DisposableEffect(enabled, keepScreenOn) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        if (enabled) {
            // Make content draw behind system bars
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // Hide system bars
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Apply multiple methods to keep screen on for compatibility with e-ink devices
        if (keepScreenOn) {
            // Method 1: Window flag
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Method 2: PowerManager wake lock
            if (!wakeLock.isHeld) {
                // Acquire the wake lock with a timeout to prevent it from being held indefinitely
                // Once testing is complete, we can remove the timeout
                wakeLock.acquire(10 * 60 * 1000L) // Testing timeout of 10 minutes
            }
        }

        onDispose {
            if (enabled) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }

            if (keepScreenOn) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (wakeLock.isHeld) {
                    try {
                        wakeLock.release()
                    } catch (e: Exception) {
                        // Ignore if already released
                    }
                }
            }
        }
    }
}
