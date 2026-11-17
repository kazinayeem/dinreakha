package com.example.tracker.data.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class FocusRunTest {
    private val t0 = LocalDateTime.of(2026, 11, 18, 10, 30)
    private fun at(min: Long, sec: Long = 0) = t0.plusMinutes(min).plusSeconds(sec)

    @Test fun countsDownWhileRunning() {
        val r = FocusRun(FocusType.RESEARCH, 45, t0)
        assertEquals(Duration.ofMinutes(35), r.remaining(at(10)))
        assertEquals(at(45), r.endsAt(at(10)))
        assertTrue(r.isDone(at(45)))
        assertEquals(Duration.ZERO, r.remaining(at(60)))
    }

    @Test fun pauseFreezesAndResumeShiftsTheEnd() {
        val paused = FocusRun(FocusType.CODE, 25, t0).pause(at(10))
        assertEquals(Duration.ofMinutes(15), paused.remaining(at(20)))   // frozen while paused
        val resumed = paused.resume(at(20))                               // 10 min pause
        assertEquals(Duration.ofMinutes(15), resumed.remaining(at(20)))
        assertEquals(at(35), resumed.endsAt(at(20)))
        // second pause accumulates
        val again = resumed.pause(at(25)).resume(at(30))
        assertEquals(at(40), again.endsAt(at(30)))
        assertEquals(Duration.ofMinutes(15), again.elapsed(at(30)))
    }

    @Test fun pauseAndResumeAreIdempotent() {
        val r = FocusRun(FocusType.STUDY, 25, t0)
        assertEquals(r, r.resume(at(5)))
        val p = r.pause(at(5))
        assertEquals(p, p.pause(at(8)))
    }

    @Test fun extendAddsMinutes() {
        val r = FocusRun(FocusType.STUDY, 25, t0).extend(5)
        assertEquals(at(30), r.endsAt(at(1)))
    }

    @Test fun jsonRoundTrip() {
        val r = FocusRun(FocusType.RESEARCH, 90, t0, pausedMs = 60_000, pausedAt = at(20), dnd = true, title = "Research, Library")
        assertEquals(r, FocusRun.fromJson(r.toJson()))
        assertEquals(null, FocusRun.fromJson("not json"))
    }

    @Test fun habitTicksOnlyNearTheEnd() {
        val r = FocusRun(FocusType.STUDY, 45, t0)
        assertTrue(FocusMath.ticksHabit(r, at(45)))
        assertTrue(FocusMath.ticksHabit(r, at(36)))   // 80%
        assertFalse(FocusMath.ticksHabit(r, at(30)))  // stopped early
        assertFalse(FocusMath.ticksHabit(FocusRun(FocusType.OTHER, 25, t0), at(25))) // no habit for "Other"
        assertEquals(45, FocusMath.loggedMinutes(r, at(50)))
    }

    @Test fun minutesByTypeAndFormatting() {
        val s = listOf(
            FocusSessionInfo(1, FocusType.RESEARCH, t0, at(45), 45, ""),
            FocusSessionInfo(2, FocusType.RESEARCH, t0, at(30), 30, ""),
            FocusSessionInfo(3, FocusType.CODE, t0, at(90), 90, ""),
        )
        assertEquals(mapOf(FocusType.RESEARCH to 75, FocusType.CODE to 90), FocusMath.minutesByType(s))
        assertEquals("1h 15m", FocusMath.formatHm(75))
        assertEquals("2h", FocusMath.formatHm(120))
        assertEquals("25m", FocusMath.formatHm(25))
    }
}
