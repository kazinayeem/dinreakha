package com.example.tracker.data.schedule

import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * One switch for 12-hour (AM/PM) or 24-hour time, used by every screen, notification and picker.
 * Set from Settings → General; SettingsRepository keeps it in sync.
 */
object TimeFormat {
    @Volatile var use24h: Boolean = true

    private val H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    val formatter: DateTimeFormatter get() = if (use24h) H24 else H12

    fun format(t: TemporalAccessor): String = formatter.format(t)
}
