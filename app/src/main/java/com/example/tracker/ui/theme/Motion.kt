package com.example.tracker.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The app's motion timing — four durations instead of seventeen. Exits are quicker than entrances;
 * only big "hero" moves (the sun on the Dayline) get the long one.
 */
object Motion {
    /** Exits and tiny state changes. */
    const val QUICK = 120
    /** Entrances, colour and size changes. */
    const val STANDARD = 220
    /** Delay that lets the old screen leave before the new one arrives. */
    const val STAGGER = 60
    /** Progress bars and counters filling up. */
    const val SLOW = 600
    /** One large move across the screen (the sun rising on the Dayline). */
    const val HERO = 1000
    /** One turn of a loading spinner. */
    const val SPIN = 1000

    /** True when the phone's "Remove animations" setting is on (animator duration scale = 0). */
    @Composable
    fun reduced(): Boolean {
        val ctx = LocalContext.current
        return remember {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    }
}
