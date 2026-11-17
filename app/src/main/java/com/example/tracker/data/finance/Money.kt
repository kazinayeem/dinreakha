package com.example.tracker.data.finance

import kotlin.math.abs

/** BDT helpers. Amounts are Long paisa; text conversion never goes through Float/Double. */
object Money {
    const val SYMBOL = "৳"

    /**
     * "1,250.5" → 125050. Accepts commas, ৳, spaces, a leading '-', up to 2 decimals.
     * Returns null for anything else.
     */
    fun parse(input: String): Long? {
        val s = input.replace(",", "").replace(SYMBOL, "").replace(" ", "").trim()
        if (s.isEmpty()) return null
        val negative = s.startsWith("-")
        val body = s.removePrefix("-")
        val parts = body.split(".")
        if (parts.size > 2) return null
        val whole = parts[0].ifEmpty { "0" }
        val frac = parts.getOrNull(1) ?: ""
        if (!whole.all(Char::isDigit) || !frac.all(Char::isDigit) || frac.length > 2) return null
        if (whole.length > 13) return null
        val paisa = whole.toLong() * 100 + frac.padEnd(2, '0').toLong()
        return if (negative) -paisa else paisa
    }

    /** 125050 → "৳1,250.50"; 120000 → "৳1,200" (whole taka drop ".00"). */
    fun format(paisa: Long, showSign: Boolean = false): String {
        val sign = when {
            paisa < 0 -> "−"
            showSign && paisa > 0 -> "+"
            else -> ""
        }
        val a = abs(paisa)
        val taka = groupBd(a / 100)
        val p = a % 100
        return if (p == 0L) "$sign$SYMBOL$taka" else "$sign$SYMBOL$taka.${p.toString().padStart(2, '0')}"
    }

    /** Plain editable text: 125050 → "1250.5", 120000 → "1200". */
    fun toInput(paisa: Long): String {
        val a = abs(paisa)
        val p = a % 100
        val base = (if (paisa < 0) "-" else "") + (a / 100)
        return when {
            p == 0L -> base
            p % 10 == 0L -> "$base.${p / 10}"
            else -> "$base.${p.toString().padStart(2, '0')}"
        }
    }

    /** Bangladeshi/Indian digit grouping: 12,34,567. */
    fun groupBd(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3)
        val groups = rest.reversed().chunked(2).joinToString(",").reversed()
        return "$groups,$last3"
    }
}
