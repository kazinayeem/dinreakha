package com.example.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Meaning colours (good / warning / bad) in one place, with a darker shade on light surfaces and a
 * lighter one on dark surfaces so text and icons stay readable. Screens use these instead of raw hex.
 */
object StatusColors {
    private val dark: Boolean @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

    /** Done, on track, under the limit. */
    val good: Color @Composable @ReadOnlyComposable get() = if (dark) Color(0xFF34D399) else Color(0xFF0B7A57)
    /** Close to a limit, late, needs attention. */
    val warn: Color @Composable @ReadOnlyComposable get() = if (dark) Color(0xFFF2A93B) else Color(0xFFA35A00)
    /** Over the limit, blocked, failed. */
    val bad: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
}

/** Other apps' brand colours — fixed on purpose, only for their own logos/labels. */
object BrandColors {
    val Instagram = Color(0xFFE1306C)
    val YouTube = Color(0xFFFF0000)
    val Facebook = Color(0xFF1877F2)
}

/** Categorical palette for charts, tuned to read on both light and dark backgrounds. */
val ChartPalette = listOf(
    Color(0xFF5B8DEF), Color(0xFF2FB38A), Color(0xFFF2A93B), Color(0xFFE0605E), Color(0xFF9B7BE8),
    Color(0xFF3FB5C9), Color(0xFFE681B5), Color(0xFF8DA34B), Color(0xFFB98B5C), Color(0xFF7C8AA5),
)
