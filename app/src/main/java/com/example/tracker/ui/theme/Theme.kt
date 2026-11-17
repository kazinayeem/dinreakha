package com.example.tracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tracker.data.repo.ThemeMode

// Dinrekha brand colours (logo, splash, wallet card)
val BrandNight = Color(0xFF101633)
val BrandIndigo = Color(0xFF1B2463)
val BrandTeal = Color(0xFF0E7C7B)
val BrandMint = Color(0xFF7CF5C8)
val Income = Color(0xFF2FA46B)
val Expense = Color(0xFFE0605E)
val Warn = Color(0xFFF2A93B)

/**
 * A selectable colour theme. Popular choices: Material You (from your wallpaper, Android 12+),
 * the community palettes Catppuccin, Dracula and Nord, and a few classic single-hue themes.
 */
data class Palette(
    val key: String,
    val name: String,
    /** Swatches shown in the picker. */
    val preview: List<Color>,
    val light: ColorScheme?,
    val dark: ColorScheme?,
    val dynamic: Boolean = false,
)

private fun onColor(c: Color) = if (c.luminance() > 0.45f) Color(0xFF121212) else Color.White

/** Builds a full Material 3 scheme from a few hand-picked colours. */
private fun scheme(
    dark: Boolean,
    primary: Color, secondary: Color, tertiary: Color,
    background: Color, surfaceVariant: Color, onBackground: Color,
    error: Color = if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A),
): ColorScheme {
    val container = { c: Color -> lerp(background, c, if (dark) 0.35f else 0.22f) }
    val onContainer = { c: Color -> lerp(c, onBackground, if (dark) 0.25f else 0.55f) }
    val step = { f: Float -> lerp(background, surfaceVariant, f) }
    return if (dark) darkColorScheme(
        primary = primary, onPrimary = onColor(primary),
        primaryContainer = container(primary), onPrimaryContainer = onContainer(primary),
        secondary = secondary, onSecondary = onColor(secondary),
        secondaryContainer = container(secondary), onSecondaryContainer = onContainer(secondary),
        tertiary = tertiary, onTertiary = onColor(tertiary),
        tertiaryContainer = container(tertiary), onTertiaryContainer = onContainer(tertiary),
        background = background, onBackground = onBackground,
        surface = background, onSurface = onBackground,
        surfaceVariant = surfaceVariant, onSurfaceVariant = lerp(onBackground, surfaceVariant, 0.25f),
        surfaceContainerLowest = lerp(background, Color.Black, 0.2f),
        surfaceContainerLow = step(0.35f), surfaceContainer = step(0.55f),
        surfaceContainerHigh = step(0.75f), surfaceContainerHighest = step(1f),
        outline = lerp(onBackground, background, 0.55f), outlineVariant = lerp(surfaceVariant, onBackground, 0.12f),
        error = error,
    ) else lightColorScheme(
        primary = primary, onPrimary = onColor(primary),
        primaryContainer = container(primary), onPrimaryContainer = onContainer(primary),
        secondary = secondary, onSecondary = onColor(secondary),
        secondaryContainer = container(secondary), onSecondaryContainer = onContainer(secondary),
        tertiary = tertiary, onTertiary = onColor(tertiary),
        tertiaryContainer = container(tertiary), onTertiaryContainer = onContainer(tertiary),
        background = background, onBackground = onBackground,
        surface = background, onSurface = onBackground,
        surfaceVariant = surfaceVariant, onSurfaceVariant = lerp(onBackground, surfaceVariant, 0.3f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = step(0.3f), surfaceContainer = step(0.5f),
        surfaceContainerHigh = step(0.7f), surfaceContainerHighest = step(0.9f),
        outline = lerp(onBackground, background, 0.5f), outlineVariant = lerp(surfaceVariant, onBackground, 0.1f),
        error = error,
    )
}

/** Simple palette: one accent family on neutral light/dark backgrounds. */
private fun hue(key: String, name: String, lightP: Color, darkP: Color, second: Color, third: Color,
                darkBg: Color = Color(0xFF111318), lightBg: Color = Color(0xFFF8F9FC)) = Palette(
    key, name, listOf(darkP, lightP, second, third),
    light = scheme(false, lightP, lerp(second, Color.Black, 0.25f), lerp(third, Color.Black, 0.25f),
        lightBg, lerp(lightBg, lightP, 0.12f), Color(0xFF1A1C20)),
    dark = scheme(true, darkP, second, third, darkBg, lerp(darkBg, darkP, 0.18f), Color(0xFFE3E4EA)),
)

val PALETTES: List<Palette> = listOf(
    Palette("dinrekha", "Dinrekha (default)", listOf(BrandIndigo, BrandTeal, BrandMint, Color(0xFFFFC27A)),
        light = scheme(false, BrandTeal, BrandIndigo, Color(0xFF8C5A00), Color(0xFFF6F7FC), Color(0xFFE1E3F0), Color(0xFF181B2C)),
        dark = scheme(true, BrandMint, Color(0xFF9DB4FF), Color(0xFFFFC27A), BrandNight, Color(0xFF283058), Color(0xFFE4E7F5))),
    Palette("material_you", "Material You (wallpaper)", listOf(Color(0xFF6750A4), Color(0xFF625B71), Color(0xFF7D5260), Color(0xFFB3261E)),
        light = null, dark = null, dynamic = true),
    // Catppuccin: Latte (light) / Mocha (dark) — mauve, blue, peach
    Palette("catppuccin", "Catppuccin", listOf(Color(0xFF1E1E2E), Color(0xFFCBA6F7), Color(0xFF89B4FA), Color(0xFFFAB387)),
        light = scheme(false, Color(0xFF8839EF), Color(0xFF1E66F5), Color(0xFFFE640B), Color(0xFFEFF1F5), Color(0xFFCCD0DA), Color(0xFF4C4F69)),
        dark = scheme(true, Color(0xFFCBA6F7), Color(0xFF89B4FA), Color(0xFFFAB387), Color(0xFF1E1E2E), Color(0xFF45475A), Color(0xFFCDD6F4))),
    // Dracula (dark) with its "Alucard" light variant — purple, pink, cyan
    Palette("dracula", "Dracula", listOf(Color(0xFF282A36), Color(0xFFBD93F9), Color(0xFFFF79C6), Color(0xFF8BE9FD)),
        light = scheme(false, Color(0xFF644AC9), Color(0xFFA3144D), Color(0xFF036A96), Color(0xFFFFFBEB), Color(0xFFCFCFDE), Color(0xFF1F1F1F)),
        dark = scheme(true, Color(0xFFBD93F9), Color(0xFFFF79C6), Color(0xFF8BE9FD), Color(0xFF282A36), Color(0xFF44475A), Color(0xFFF8F8F2))),
    // Nord: Snow Storm (light) / Polar Night (dark) — frost blues, aurora green
    Palette("nord", "Nord", listOf(Color(0xFF2E3440), Color(0xFF88C0D0), Color(0xFF81A1C1), Color(0xFFA3BE8C)),
        light = scheme(false, Color(0xFF5E81AC), Color(0xFF4C566A), Color(0xFF8FA36F), Color(0xFFECEFF4), Color(0xFFD8DEE9), Color(0xFF2E3440)),
        dark = scheme(true, Color(0xFF88C0D0), Color(0xFF81A1C1), Color(0xFFA3BE8C), Color(0xFF2E3440), Color(0xFF434C5E), Color(0xFFECEFF4))),
    hue("ocean", "Ocean blue", Color(0xFF1565C0), Color(0xFF90CAF9), Color(0xFF80DEEA), Color(0xFFFFCC80)),
    hue("forest", "Forest green", Color(0xFF2E7D32), Color(0xFFA5D6A7), Color(0xFFC5E1A5), Color(0xFFFFE082)),
    hue("sunset", "Sunset", Color(0xFFD84315), Color(0xFFFFAB91), Color(0xFFFFCC80), Color(0xFFF48FB1),
        darkBg = Color(0xFF1A1210)),
    hue("lavender", "Lavender", Color(0xFF6A4FB3), Color(0xFFD0BCFF), Color(0xFFB39DDB), Color(0xFFF8BBD0)),
    hue("rose", "Rose", Color(0xFFC2185B), Color(0xFFF8BBD0), Color(0xFFFFAB91), Color(0xFFCE93D8),
        darkBg = Color(0xFF1A1114)),
    hue("mono", "Monochrome", Color(0xFF303030), Color(0xFFE0E0E0), Color(0xFFB0B0B0), Color(0xFF9E9E9E),
        darkBg = Color(0xFF0E0E0E), lightBg = Color(0xFFFAFAFA)),
)

fun paletteFor(key: String) = PALETTES.firstOrNull { it.key == key } ?: PALETTES.first()

@Composable
fun TrackerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    paletteKey: String = "dinrekha",
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = paletteFor(paletteKey)
    val colors = if (palette.dynamic && Build.VERSION.SDK_INT >= 31) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        val p = if (palette.dynamic) PALETTES.first() else palette
        if (dark) p.dark!! else p.light!!
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
