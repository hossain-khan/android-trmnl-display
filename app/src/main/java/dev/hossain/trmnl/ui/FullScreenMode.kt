package dev.hossain.trmnl.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * A utility composable that handles fullscreen mode for the current screen.
 * Hides system bars (status bar and navigation bar) when active.
 */
@Composable
fun FullScreenMode(enabled: Boolean = true) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    DisposableEffect(enabled) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        if (enabled) {
            // Make content draw behind system bars
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // Hide system bars
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            // Keep screen on
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            if (enabled) {
                // Restore normal UI visibility when the composable leaves composition
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}
