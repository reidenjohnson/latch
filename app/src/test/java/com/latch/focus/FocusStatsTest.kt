package com.latch.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusStatsTest {
    private val h = 3_600_000L
    private val m = 60_000L

    @Test fun formatsDurations() {
        assertEquals("30s", FocusStats.format(30_000))
        assertEquals("45m", FocusStats.format(45 * m))
        assertEquals("1h", FocusStats.format(h))
        assertEquals("1h 12m", FocusStats.format(h + 12 * m + 59_000))
    }

    @Test fun formatsClock() {
        assertEquals("0:05", FocusStats.clock(5_000))
        assertEquals("12:00", FocusStats.clock(12 * m))
        assertEquals("1:12:04", FocusStats.clock(h + 12 * m + 4_000))
    }

    @Test fun totalClipsSessionsToTheRange() {
        val from = 10 * h
        val s = FocusState(
            sessions = listOf(
                FocusSession(start = 9 * h, end = 11 * h, emergency = false), // half inside
                FocusSession(start = 12 * h, end = 13 * h, emergency = true),  // fully inside
                FocusSession(start = 5 * h, end = 6 * h, emergency = false),   // before the range
            ),
            active = ActiveFocus(start = 14 * h, blocked = emptySet()),
        )
        assertEquals(h + h + 30 * m, FocusStats.total(s, from, now = 14 * h + 30 * m))
    }

    @Test fun longestIgnoresRunningSession() {
        val s = FocusState(sessions = listOf(FocusSession(0, 2 * h, false), FocusSession(0, h, false)), active = ActiveFocus(0, emptySet()))
        assertEquals(2 * h, FocusStats.longest(s))
    }

    @Test fun allowOnlyAlwaysHasApps() {
        assertEquals(false, FocusState().hasApps)
        assertEquals(true, FocusState(mode = FocusMode.AllowOnly).hasApps)
    }
}
