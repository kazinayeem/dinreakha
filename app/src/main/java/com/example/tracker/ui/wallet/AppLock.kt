package com.example.tracker.ui.wallet

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
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
    fun lock() { unlocked = false }
}

/** Shows [content] only after fingerprint / face / phone PIN, when that lock scope is on in Settings. */
@Composable
fun LockedSection(scope: LockScope = LockScope.WALLET, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val settings by ctx.container.settings.settings.collectAsStateWithLifecycle(null)
    val s = settings ?: return
    if (scope !in s.lockScopes || AppLock.unlocked) { content(); return }

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
        BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { AppLock.unlocked = true }
            override fun onAuthenticationError(code: Int, err: CharSequence) { message = err.toString() }
        }).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Dinrekha")
                .setSubtitle("Fingerprint or phone PIN")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
        )
    }

    LaunchedEffect(Unit) { prompt() }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🔒", style = MaterialTheme.typography.displayMedium)
        Text("${scope.label} is locked", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
        Button(onClick = ::prompt, modifier = Modifier.padding(top = 20.dp).heightIn(min = 52.dp)) { Text("Unlock") }
    }
}
