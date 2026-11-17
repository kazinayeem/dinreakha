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
    fun show(message: String, undoLabel: String? = null, onUndo: (() -> Unit)? = null) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val r = host.showSnackbar(com.example.tracker.ui.i18n.tr(message), actionLabel = if (onUndo != null) com.example.tracker.ui.i18n.tr(undoLabel ?: "Undo") else null,
                withDismissAction = onUndo == null, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onUndo?.invoke()
        }
    }
}

val LocalFeedback = staticCompositionLocalOf<Feedback?> { null }
