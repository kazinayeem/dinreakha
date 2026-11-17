package com.example.tracker.blocker

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.example.tracker.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A tiny, non-touchable pill on the right edge of the screen while Facebook, Instagram or YouTube is
 * in front: "12m" over "34 ▶" — time in that app today and reels/shorts watched today.
 * Uses TYPE_ACCESSIBILITY_OVERLAY, so it needs no extra permission beyond the accessibility service.
 * Turn off with the "Side counter" switch on the App Blocker screen (settings key [KEY]).
 */
class UsageOverlay(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val usedMsToday: suspend (String) -> Long,
    private val reelsToday: suspend (String) -> Int,
) {
    companion object {
        const val KEY = "usage_overlay"

        fun platformOf(pkg: String): String? = when {
            pkg.startsWith("com.facebook.katana") || pkg.startsWith("com.facebook.lite") -> "facebook"
            pkg.startsWith("com.instagram") -> "instagram"
            pkg.startsWith("com.google.android.youtube") -> "youtube"
            else -> null
        }

        fun format(usedMs: Long, reels: Int): String {
            val m = usedMs / 60_000
            val time = if (m >= 60) "${m / 60}h${(m % 60).toString().padStart(2, '0')}" else "${m}m"
            return "$time\n$reels ▶"
        }
    }

    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private var pkg: String? = null
    private var ticker: Job? = null

    /** Called for every foreground change seen by the accessibility service. */
    fun onForeground(newPkg: String) {
        if (newPkg == service.packageName && view != null) return
        val platform = platformOf(newPkg)
        if (platform == null) { hide(); return }
        if (newPkg == pkg && view != null) return
        pkg = newPkg
        scope.launch {
            val on = runCatching { service.applicationContext.container.db.settingsDao().get(KEY) }.getOrNull() != "0"
            withContext(Dispatchers.Main) { if (on && pkg == newPkg) show() else if (!on) hide() }
        }
    }

    fun refreshSoon() { scope.launch { update() } }

    private fun show() {
        if (view == null) {
            val dp = service.resources.displayMetrics.density
            val tv = TextView(service).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setLineSpacing(0f, 0.95f)
                setPadding((6 * dp).toInt(), (5 * dp).toInt(), (5 * dp).toInt(), (5 * dp).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.argb(150, 20, 20, 28))
                    cornerRadii = floatArrayOf(10 * dp, 10 * dp, 0f, 0f, 0f, 0f, 10 * dp, 10 * dp)
                }
                alpha = 0.85f
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                y = (-60 * dp).toInt() // a little above centre, clear of the reel's like/comment column
            }
            runCatching { wm.addView(tv, lp); view = tv }.onFailure { return }
        }
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                update()
                delay(5_000)
            }
        }
    }

    private suspend fun update() {
        val p = pkg ?: return
        val platform = platformOf(p) ?: return
        val text = runCatching { format(usedMsToday(p), reelsToday(platform)) }.getOrNull() ?: return
        withContext(Dispatchers.Main) { view?.text = text }
    }

    fun hide() {
        ticker?.cancel(); ticker = null
        pkg = null
        val v = view ?: return
        view = null
        scope.launch(Dispatchers.Main) { runCatching { wm.removeView(v) } }
    }
}
