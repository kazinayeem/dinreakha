package com.example.tracker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.data.repo.QuoteCategory
import com.example.tracker.data.repo.QuoteLanguage
import com.example.tracker.data.repo.ThemeMode
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.example.tracker.alarm.Notifications
import com.example.tracker.ui.nav.AppNav
import com.example.tracker.ui.splash.AnimatedSplash
import com.example.tracker.ui.theme.TrackerTheme
import com.example.tracker.ui.wallet.AppLock

/** Single activity. FragmentActivity because BiometricPrompt (wallet lock) needs it. */
class MainActivity : FragmentActivity() {

    private var pendingRoute by mutableStateOf<String?>(null)
    private var sharedUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash (brand colour + mark), then the animated Compose intro. Fade the system mark out
        // instead of letting it vanish in one frame.
        installSplashScreen().setOnExitAnimationListener { p ->
            p.view.animate().alpha(0f).setDuration(180L).withEndAction { p.remove() }.start()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val coldStart = savedInstanceState == null
        // Only on a fresh start: after rotation / process restore the same launch intent comes back,
        // and re-handling it would yank the user back to that screen (or re-open the share capture).
        if (coldStart) {
            pendingRoute = intent?.getStringExtra(Notifications.EXTRA_ROUTE)
            handleShareIntent(intent)
        }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(null)
            val mode = settings?.themeMode ?: ThemeMode.SYSTEM
            val dark = when (mode) {
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            var showIntro by rememberSaveable { mutableStateOf(coldStart) }
            var introGone by remember { mutableStateOf(!showIntro) }
            // Status/navigation bar icons follow the APP theme (not the phone's), so they stay visible —
            // and stay light while the dark intro is on screen.
            val barsDark = dark || !introGone
            androidx.compose.runtime.LaunchedEffect(barsDark) {
                val t = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (barsDark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
                    navigationBarStyle = if (barsDark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
                )
            }
            TrackerTheme(
                themeMode = mode,
                paletteKey = settings?.palette ?: "dinrekha",
            ) {
                // The app is composed UNDER the intro from the first frame, so by the time the intro
                // leaves, Today is loaded and laid out. The intro then fades (and drifts up a little)
                // to reveal it — no cross-fade of two half-drawn screens, no content popping in after.
                val introAlpha = remember { Animatable(if (showIntro) 1f else 0f) }
                androidx.compose.runtime.LaunchedEffect(showIntro) {
                    if (!showIntro) {
                        AppLock.ready = true // unlock prompt only once the intro is out of the way
                        introAlpha.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
                        introGone = true
                    }
                }
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        AppNav(
                            pendingRoute = pendingRoute,
                            sharedUris = sharedUris,
                            onRouteHandled = {
                                pendingRoute = null
                                sharedUris = emptyList()
                            },
                        )
                        if (!introGone) {
                            Box(
                                Modifier.fillMaxSize().graphicsLayer {
                                    alpha = introAlpha.value
                                    val k = 1f + (1f - introAlpha.value) * 0.04f
                                    scaleX = k; scaleY = k
                                },
                            ) {
                                AnimatedSplash(
                                    category = settings?.quoteCategory ?: QuoteCategory.ALL,
                                    language = settings?.quoteLanguage ?: QuoteLanguage.BOTH,
                                    onFinished = { showIntro = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifications.EXTRA_ROUTE)?.let { pendingRoute = it }
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type
        if ((action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) && type?.startsWith("image/") == true) {
            val uris = mutableListOf<Uri>()
            if (action == Intent.ACTION_SEND) {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { uris.add(it) }
            } else if (action == Intent.ACTION_SEND_MULTIPLE) {
                val list: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                list?.let { uris.addAll(it) }
            }
            if (uris.isNotEmpty()) {
                sharedUris = uris
                pendingRoute = "notes/capture"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        container.rescheduleAsync() // "reschedule on app open"
    }

    override fun onStop() {
        super.onStop()
        AppLock.lock()
    }
}
