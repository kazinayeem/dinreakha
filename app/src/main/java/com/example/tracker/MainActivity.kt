package com.example.tracker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
        installSplashScreen() // system splash (brand colour + mark), then the animated Compose intro
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
            // Status/navigation bar icons follow the APP theme (not the phone's), so they stay visible.
            androidx.compose.runtime.LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
                    navigationBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
                )
            }
            TrackerTheme(
                themeMode = mode,
                paletteKey = settings?.palette ?: "dinrekha",
            ) {
                var showIntro by rememberSaveable { mutableStateOf(coldStart) }
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnimatedContent(
                        targetState = showIntro,
                        transitionSpec = {
                            (fadeIn(tween(400, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.98f, animationSpec = tween(400, easing = FastOutSlowInEasing)))
                                .togetherWith(
                                    fadeOut(tween(320, easing = FastOutSlowInEasing)) + scaleOut(targetScale = 1.02f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                                )
                        },
                        label = "intro",
                    ) { intro ->
                        if (intro) {
                            AnimatedSplash(
                                category = settings?.quoteCategory ?: QuoteCategory.ALL,
                                language = settings?.quoteLanguage ?: QuoteLanguage.BOTH,
                                onFinished = { showIntro = false },
                            )
                        } else {
                            AppNav(
                                pendingRoute = pendingRoute,
                                sharedUris = sharedUris,
                                onRouteHandled = {
                                    pendingRoute = null
                                    sharedUris = emptyList()
                                },
                            )
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
