package com.example.tracker.data.calendar

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Minimal, dependency-free iCalendar (.ics, RFC 5545) writer/reader for your own events.
 * Supports: SUMMARY, DTSTART/DTEND (local with TZID, UTC "Z", or all-day VALUE=DATE), LOCATION, DESCRIPTION,
 * RRULE, EXDATE (skipped occurrences), RECURRENCE-ID overrides ("edited this one"), VALARM reminders.
 */
object Ics {
    private val ZONE: ZoneId = ZoneId.of("Asia/Dhaka")
    private val DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val D = DateTimeFormatter.BASIC_ISO_DATE

    data class Parsed(val event: CalEvent, val exceptions: List<EventException>)

    fun write(events: List<CalEvent>, exceptions: List<EventException>, stamp: LocalDateTime = LocalDateTime.now(ZONE)): String {
        val sb = StringBuilder()
        fun line(s: String) { fold(s).forEach { sb.append(it).append("\r\n") } }
        line("BEGIN:VCALENDAR"); line("VERSION:2.0"); line("PRODID:-//Dinrekha//Calendar//EN"); line("CALSCALE:GREGORIAN")
        events.forEach { ev ->
            val uid = "dinrekha-${ev.id}@local"
            val exs = exceptions.filter { it.eventId == ev.id }
            line("BEGIN:VEVENT")
            line("UID:$uid")
            line("DTSTAMP:${stamp.atZone(ZONE).withZoneSameInstant(ZoneOffset.UTC).format(DT)}Z")
            writeTimes(::line, ev.start, ev.end, ev.allDay)
            line("SUMMARY:${esc(ev.title)}")
            if (ev.location.isNotBlank()) line("LOCATION:${esc(ev.location)}")
            if (ev.notes.isNotBlank()) line("DESCRIPTION:${esc(ev.notes)}")
            ev.recurrence.toRrule()?.let { line("RRULE:$it") }
            exs.filter { it.cancelled }.forEach { ex ->
                line(if (ev.allDay) "EXDATE;VALUE=DATE:${ex.originalDate.format(D)}"
                    else "EXDATE;TZID=Asia/Dhaka:${ex.originalDate.atTime(ev.start.toLocalTime()).format(DT)}")
            }
            ev.reminders.forEach { m ->
                line("BEGIN:VALARM"); line("ACTION:DISPLAY"); line("DESCRIPTION:${esc(ev.title)}"); line("TRIGGER:-PT${m}M"); line("END:VALARM")
            }
            line("END:VEVENT")
            // overrides ("edit this occurrence only") as RECURRENCE-ID events
            exs.filter { !it.cancelled }.forEach { ex ->
                val s = ex.start ?: ex.originalDate.atTime(ev.start.toLocalTime())
                line("BEGIN:VEVENT"); line("UID:$uid")
                line("DTSTAMP:${stamp.atZone(ZONE).withZoneSameInstant(ZoneOffset.UTC).format(DT)}Z")
                line(if (ev.allDay) "RECURRENCE-ID;VALUE=DATE:${ex.originalDate.format(D)}"
                    else "RECURRENCE-ID;TZID=Asia/Dhaka:${ex.originalDate.atTime(ev.start.toLocalTime()).format(DT)}")
                writeTimes(::line, s, ex.end ?: s.plus(ev.duration), ev.allDay)
                line("SUMMARY:${esc(ex.title ?: ev.title)}")
                (ex.location ?: ev.location).takeIf { it.isNotBlank() }?.let { line("LOCATION:${esc(it)}") }
                (ex.notes ?: ev.notes).takeIf { it.isNotBlank() }?.let { line("DESCRIPTION:${esc(it)}") }
                line("END:VEVENT")
            }
        }
        line("END:VCALENDAR")
        return sb.toString()
    }

    private fun writeTimes(line: (String) -> Unit, start: LocalDateTime, end: LocalDateTime, allDay: Boolean) {
        if (allDay) {
            line("DTSTART;VALUE=DATE:${start.toLocalDate().format(D)}")
            line("DTEND;VALUE=DATE:${end.toLocalDate().coerceAtLeast(start.toLocalDate().plusDays(1)).format(D)}")
        } else {
            line("DTSTART;TZID=Asia/Dhaka:${start.format(DT)}")
            line("DTEND;TZID=Asia/Dhaka:${end.format(DT)}")
        }
    }

    private fun LocalDate.coerceAtLeast(o: LocalDate) = if (isBefore(o)) o else this

    /** Parses VEVENTs. Unknown properties are ignored. Events get id 0 (new); exceptions refer to the event by list index. */
    fun parse(text: String): List<Parsed> {
        val lines = unfold(text)
        val blocks = mutableListOf<MutableMap<String, MutableList<Pair<Map<String, String>, String>>>>()
        var cur: MutableMap<String, MutableList<Pair<Map<String, String>, String>>>? = null
        var inAlarm = false
        val alarms = mutableListOf<MutableList<Int>>()
        for (raw in lines) {
            val l = raw.trimEnd()
            when {
                l.equals("BEGIN:VEVENT", true) -> {
                    val block = mutableMapOf<String, MutableList<Pair<Map<String, String>, String>>>()
                    cur = block; blocks.add(block); alarms.add(mutableListOf())
                }
                l.equals("END:VEVENT", true) -> cur = null
                l.equals("BEGIN:VALARM", true) -> inAlarm = true
                l.equals("END:VALARM", true) -> inAlarm = false
                cur != null -> {
                    val (nameParams, value) = l.split(":", limit = 2).let { if (it.size == 2) it[0] to it[1] else continue }
                    val segs = nameParams.split(";")
                    val name = segs[0].uppercase()
                    val params = segs.drop(1).mapNotNull { p -> p.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] } }.toMap()
                    if (inAlarm) {
                        if (name == "TRIGGER") parseTrigger(value)?.let { alarms.last().add(it) }
                    } else cur.getOrPut(name) { mutableListOf() }.add(params to value)
                }
            }
        }
        // group by UID: base events (no RECURRENCE-ID) + overrides
        data class Raw(val props: Map<String, List<Pair<Map<String, String>, String>>>, val alarms: List<Int>)
        val raws = blocks.mapIndexed { i, b -> Raw(b, alarms[i]) }
        val bases = raws.filter { "RECURRENCE-ID" !in it.props }
        return bases.mapNotNull { r ->
            val (start, allDay) = time(r.props["DTSTART"]?.firstOrNull()) ?: return@mapNotNull null
            val end = time(r.props["DTEND"]?.firstOrNull())?.first ?: if (allDay) start.plusDays(1) else start.plusHours(1)
            val ev = CalEvent(
                title = unesc(r.props["SUMMARY"]?.firstOrNull()?.second ?: "(no title)"),
                start = start, end = end, allDay = allDay,
                location = unesc(r.props["LOCATION"]?.firstOrNull()?.second ?: ""),
                notes = unesc(r.props["DESCRIPTION"]?.firstOrNull()?.second ?: ""),
                recurrence = Recurrence.parse(r.props["RRULE"]?.firstOrNull()?.second),
                reminders = r.alarms.distinct().sorted(),
            )
            val uid = r.props["UID"]?.firstOrNull()?.second
            val exdates = r.props["EXDATE"].orEmpty().flatMap { (p, v) -> v.split(",").mapNotNull { time(p to it)?.first?.toLocalDate() } }
                .map { EventException(0, it, cancelled = true) }
            val overrides = raws.filter { o -> "RECURRENCE-ID" in o.props && uid != null && o.props["UID"]?.firstOrNull()?.second == uid }
                .mapNotNull { o ->
                    val orig = time(o.props["RECURRENCE-ID"]?.firstOrNull())?.first?.toLocalDate() ?: return@mapNotNull null
                    val s = time(o.props["DTSTART"]?.firstOrNull())?.first
                    EventException(0, orig, false,
                        title = o.props["SUMMARY"]?.firstOrNull()?.second?.let(::unesc),
                        start = s, end = time(o.props["DTEND"]?.firstOrNull())?.first,
                        location = o.props["LOCATION"]?.firstOrNull()?.second?.let(::unesc),
                        notes = o.props["DESCRIPTION"]?.firstOrNull()?.second?.let(::unesc))
                }
            Parsed(ev, exdates + overrides)
        }
    }

    /** DTSTART-like value → (local Dhaka time, isAllDay). */
    private fun time(p: Pair<Map<String, String>, String>?): Pair<LocalDateTime, Boolean>? {
        p ?: return null
        val (params, v) = p
        return runCatching {
            when {
                params["VALUE"] == "DATE" || v.length == 8 -> LocalDate.parse(v, D).atStartOfDay() to true
                v.endsWith("Z") -> LocalDateTime.parse(v.dropLast(1), DT).atZone(ZoneOffset.UTC).withZoneSameInstant(ZONE).toLocalDateTime() to false
                params["TZID"] != null -> LocalDateTime.parse(v, DT).atZone(runCatching { ZoneId.of(params["TZID"]) }.getOrDefault(ZONE))
                    .withZoneSameInstant(ZONE).toLocalDateTime() to false
                else -> LocalDateTime.parse(v, DT) to false
            }
        }.getOrNull()
    }

    /** "-PT15M", "-PT1H", "-P1D", "PT0S" → minutes before. */
    private fun parseTrigger(v: String): Int? {
        val m = Regex("^-?P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$").find(v.trim()) ?: return null
        val (d, h, min) = m.destructured.let { Triple(it.component1(), it.component2(), it.component3()) }
        return (d.toIntOrNull() ?: 0) * 1440 + (h.toIntOrNull() ?: 0) * 60 + (min.toIntOrNull() ?: 0)
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
    private fun unesc(s: String) = s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        text.replace("\r\n", "\n").split("\n").forEach { l ->
            if ((l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty()) out[out.lastIndex] = out.last() + l.substring(1) else out += l
        }
        return out
    }

    /** Lines longer than 75 octets are folded (continuation lines start with a space). */
    private fun fold(s: String): List<String> {
        if (s.toByteArray().size <= 75) return listOf(s)
        val out = mutableListOf<String>(); var cur = StringBuilder()
        s.forEach { ch ->
            if ((cur.toString() + ch).toByteArray().size > (if (out.isEmpty()) 75 else 74)) { out += cur.toString(); cur = StringBuilder() }
            cur.append(ch)
        }
        out += cur.toString()
        return out.mapIndexed { i, l -> if (i == 0) l else " $l" }
    }
}
