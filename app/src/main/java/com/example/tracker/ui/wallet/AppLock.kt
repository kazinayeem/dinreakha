package com.example.tracker.ui.wallet

import androidx.compose.material.icons.rounded.Lock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import com.example.tracker.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.repo.LockScope

/** Unlock state for the wallet. Cleared when the app goes to the background (MainActivity.onStop). */
object AppLock {
    var unlocked by mutableStateOf(false)
    /** One prompt at a time: an app-wide and a wallet lock panel can both be on screen now. */
    internal var prompting = false
    /** False while the start-up intro is on screen: the unlock prompt waits for it. */
    var ready by mutableStateOf(false)
    fun lock() { unlocked = false }
}

/**
 * Shows [content] only after fingerprint / face / phone PIN, when that lock scope is on in Settings.
 *
 * Once unlocked, the content stays composed when the lock comes back (app went to the background):
 * the lock panel is drawn over it instead. Removing it used to throw away the navigation back stack,
 * half-typed forms, and pending results — a photo just taken, a backup file just picked.
 */
@Composable
fun LockedSection(scope: LockScope = LockScope.WALLET, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val settings by ctx.container.settings.settings.collectAsStateWithLifecycle(null)
    val s = settings ?: return
    val locked = scope in s.lockScopes && !AppLock.unlocked
    var shownOnce by androidx.compose.runtime.remember { mutableStateOf(false) }
    val show = shownOnce || !locked
    androidx.compose.runtime.SideEffect { if (!locked) shownOnce = true }

    Box(Modifier.fillMaxSize()) {
        if (show) {
            // Hidden from screen readers while covered; same slot either way, so its state is kept.
            Box(if (locked) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) { content() }
        }
        if (locked) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
            ) { LockPanel(scope) }
        }
    }
}

@Composable
private fun LockPanel(scope: LockScope) {
    val ctx = LocalContext.current
    val activity = ctx as? FragmentActivity
    var message by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }

    fun prompt() {
        val act = activity ?: run { AppLock.unlocked = true; return }
        val can = BiometricManager.from(act).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            // No screen lock set up on the phone: nothing to verify against.
            message = "Set a screen lock (PIN / fingerprint) on your phone to use the app lock."
            AppLock.unlocked = true
            return
        }
        if (AppLock.prompting) return
        AppLock.prompting = true
        BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                AppLock.prompting = false; AppLock.unlocked = true
            }
            override fun onAuthenticationError(code: Int, err: CharSequence) { AppLock.prompting = false; message = err.toString() }
        }).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Dinrekha")
                .setSubtitle("Fingerprint or phone PIN")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
        )
    }

    LaunchedEffect(AppLock.ready) { if (AppLock.ready) prompt() }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Lock, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Text("${scope.label} is locked", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
        Button(onClick = ::prompt, modifier = Modifier.padding(top = 20.dp).heightIn(min = 52.dp)) { Text("Unlock") }
    }
}
