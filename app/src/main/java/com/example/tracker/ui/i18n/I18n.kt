package com.example.tracker.ui.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage(val code: String, val label: String) { ENGLISH("en", "English"), BANGLA("bn", "বাংলা") }

/**
 * App-wide English / Bangla switch.
 *
 * Every screen draws text through [Text] in this package, which calls [tr]: exact sentences come from
 * [BanglaStrings.EXACT], sentences with numbers or names from [BanglaStrings.PATTERNS], then month and
 * weekday names are swapped and digits become Bangla (১২৩). Anything not in the dictionary stays English,
 * so a missing translation can never break a screen.
 */
object I18n {
    /** Compose state, so every visible Text redraws the moment the language changes. */
    var language by mutableStateOf(AppLanguage.ENGLISH)
        private set
    @Volatile var banglaDigits: Boolean = true
        private set

    fun set(lang: AppLanguage, digits: Boolean = banglaDigits) {
        if (lang == language && digits == banglaDigits) return
        synchronized(cache) { cache.clear() }
        banglaDigits = digits
        language = lang
    }

    val isBangla get() = language == AppLanguage.BANGLA

    // Small LRU: timers and money change every second, so don't let the cache grow forever.
    private val cache = object : LinkedHashMap<String, String>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 1500
    }

    fun tr(s: String): String {
        if (language == AppLanguage.ENGLISH || s.isEmpty()) return s
        synchronized(cache) { cache[s] }?.let { return it }
        val out = translate(s)
        synchronized(cache) { cache[s] = out }
        return out
    }

    /** Pure translation (no cache) — also used by tests. */
    fun translate(s: String, digits: Boolean = banglaDigits): String {
        val trimmed = s.trim()
        var r: String = BanglaStrings.EXACT[s] ?: (BanglaStrings.EXACT[trimmed] ?: BanglaStrings.EXACT[trimmed.uppercase()])?.let { s.replace(trimmed, it) } ?: run {
            var hit: String? = null
            for ((re, rep) in BanglaStrings.PATTERNS) {
                val m = re.matchEntire(trimmed) ?: continue
                hit = s.replace(trimmed, m.groupValues.drop(1).foldIndexed(rep) { i, acc, g -> acc.replace("\$${i + 1}", BanglaStrings.EXACT[g] ?: g) })
                break
            }
            hit ?: s
        }
        r = BanglaStrings.words(r)
        return if (digits) bnDigits(r) else r
    }

    fun bnDigits(s: String): String {
        if (s.none { it in '0'..'9' }) return s
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in '0'..'9') '০' + (c - '0') else c)
        return sb.toString()
    }
}

/** Shorthand for non-Text places (toasts, notifications, widgets). */
fun tr(s: String) = I18n.tr(s)
