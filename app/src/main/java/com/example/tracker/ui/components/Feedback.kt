package com.example.tracker.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 8dp spacing rhythm used across the app. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** Minimum touch target (Android). */
    val touch = 48.dp
}

/** App-wide snackbar host (set up once in AppNav's Scaffold). */
class Feedback(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    /** Short confirmation with an optional Undo action. */
    fun show(message: String, undoLabel: String? = null, long: Boolean = false, onUndo: (() -> Unit)? = null) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val r = host.showSnackbar(com.example.tracker.ui.i18n.tr(message), actionLabel = if (onUndo != null) com.example.tracker.ui.i18n.tr(undoLabel ?: "Undo") else null,
                withDismissAction = onUndo == null, duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onUndo?.invoke()
        }
    }
}

val LocalFeedback = staticCompositionLocalOf<Feedback?> { null }

/** The snackbar host of the main screen, for code that isn't inside a composable (callbacks, helpers). */
object AppFeedback { internal var host: Feedback? = null }

/**
 * Tell the user something. Uses the app's snackbar (themed, can sit above the bottom bar) when the main
 * screen is showing; falls back to a Toast from other windows (block screen, lock-screen overlays).
 */
fun showMessage(ctx: android.content.Context, message: String, long: Boolean = false) {
    val host = AppFeedback.host
    var c: android.content.Context? = ctx
    while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
    if (host != null && c is com.example.tracker.MainActivity) host.show(message, long = long)
    else android.widget.Toast.makeText(ctx, com.example.tracker.ui.i18n.tr(message),
        if (long) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
}
