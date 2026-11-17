package com.example.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** WCAG contrast ratio between two colours (1 … 21). */
fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/**
 * Same hue, adjusted until it reaches [min] contrast against [bg]: darker on light backgrounds,
 * lighter on dark ones. Colours that already pass are returned unchanged.
 */
fun Color.readableOn(bg: Color, min: Float = 4.5f): Color {
    if (contrast(this, bg) >= min) return this
    val target = if (bg.luminance() > 0.5f) Color.Black else Color.White
    var t = 0.08f
    while (t < 1f) {
        val c = lerp(this, target, t)
        if (contrast(c, bg) >= min) return c.copy(alpha = alpha)
        t += 0.06f
    }
    return target.copy(alpha = alpha)
}

/**
 * Readable version of an accent colour for text and icons on the current theme's cards.
 * Measured against `surfaceContainerHigh` — the darkest common card in light mode and the lightest in
 * dark mode — so it passes on every surface the app uses.
 */
@Composable
@ReadOnlyComposable
fun Color.readable(min: Float = 4.5f): Color = readableOn(MaterialTheme.colorScheme.surfaceContainerHigh, min)
