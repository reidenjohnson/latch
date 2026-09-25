package com.latch.focus

import com.latch.focus.data.AppState
import com.latch.focus.data.Change
import com.latch.focus.data.EndReason
import com.latch.focus.data.Hue
import com.latch.focus.data.LatchTag
import com.latch.focus.data.ListType
import com.latch.focus.data.Mode
import com.latch.focus.data.Rules
import com.latch.focus.data.Schedule
import com.latch.focus.data.Stats
import com.latch.focus.data.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class RulesTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()
    // 2026-09-21 is a Monday.
    private val monday9 = at(2026, 9, 21, 9)
    private val block: (Mode) -> Set<String> = { it.apps }

    private val work = Mode("w", "Work", Hue.Teal, ListType.Block, setOf("insta", "tiktok"),
        schedules = listOf(Schedule("s", setOf(DayOfWeek.MONDAY), 9 * 60, 17 * 60)))
    private val base = AppState(onboarded = true, tags = listOf(LatchTag("AA", "Desk", 0)), modes = listOf(work), selectedModeId = "w")

    @Test fun tapStartsThenEnds() {
        val (s1, c1) = Rules.tap(base, "AA", 1000, block)
        assertTrue(c1 is Change.Started)
        assertEquals(setOf("insta", "tiktok"), s1.active!!.blocked)
        val (s2, c2) = Rules.tap(s1, "AA", 61_000, block)
        assertTrue(c2 is Change.Ended)
        assertNull(s2.active)
        assertEquals(60_000, s2.sessions.single().length)
        assertEquals(EndReason.Tag, s2.sessions.single().endReason)
    }

    @Test fun unpairedTagNeverLocks() {
        val (s, c) = Rules.tap(base, "BB", 1000, block)
        assertEquals(Change.NotPaired, c)
        assertNull(s.active)
    }

    @Test fun emptyBlockListDoesNotStart() {
        val s = base.copy(modes = listOf(work.copy(apps = emptySet())))
        assertEquals(Change.NoMode, Rules.tap(s, "AA", 0, block).second)
    }

    @Test fun timerEndsOnTick() {
        val (s1, _) = Rules.tap(base.copy(timerMinutes = 30), "AA", 0, block)
        assertEquals(30 * 60_000L, s1.active!!.endsAt)
        assertNotNull(Rules.tick(s1, 29 * 60_000L, block, zone).first.active)
        val (s2, c) = Rules.tick(s1, 31 * 60_000L, block, zone)
        assertNull(s2.active)
        assertEquals(EndReason.Timer, (c as Change.Ended).session.endReason)
        assertEquals(30 * 60_000L, c.session.end) // ends at the timer, not when the tick happened to run
    }

    @Test fun scheduleStartsAndEnds() {
        val (s1, c1) = Rules.tick(base, monday9 + 60_000, block, zone)
        assertTrue(c1 is Change.Started)
        assertEquals(Trigger.Schedule, s1.active!!.trigger)
        assertEquals(at(2026, 9, 21, 17), s1.active!!.endsAt)
        val (s2, c2) = Rules.tick(s1, at(2026, 9, 21, 17, 1), block, zone)
        assertNull(s2.active)
        assertEquals(EndReason.Schedule, (c2 as Change.Ended).session.endReason)
    }

    @Test fun endingAScheduleEarlySkipsTheRestOfTheWindow() {
        val (s1, _) = Rules.tick(base, monday9, block, zone)
        val (s2, _) = Rules.tap(s1, "AA", monday9 + 3_600_000, block)
        assertNull(Rules.tick(s2, monday9 + 3_600_000 + 60_000, block, zone).first.active)
        // Next Monday it runs again.
        assertNotNull(Rules.tick(s2, monday9 + 7 * 86_400_000L, block, zone).first.active)
    }

    @Test fun overnightWindowCrossesMidnight() {
        val sleep = Schedule("n", setOf(DayOfWeek.MONDAY), 22 * 60, 7 * 60)
        assertNotNull(sleep.windowAt(at(2026, 9, 22, 3), zone)) // Tuesday 3 AM, started Monday night
        assertNull(sleep.windowAt(at(2026, 9, 22, 8), zone))
        assertNull(sleep.windowAt(at(2026, 9, 21, 3), zone)) // Monday 3 AM: Sunday isn't a start day
    }

    @Test fun nextWakeIsTheNearestBoundary() {
        assertEquals(monday9, Rules.nextWake(base, monday9 - 1000, zone))
        assertEquals(at(2026, 9, 21, 17), Rules.nextWake(base, monday9 + 1000, zone))
    }

    @Test fun statsClipToRanges() {
        val (s1, _) = Rules.tap(base, "AA", 0, block)
        val (s2, _) = Rules.tap(s1, "AA", 2 * 3_600_000L, block)
        assertEquals(3_600_000L, Stats.total(s2, 3_600_000L, 10 * 3_600_000L))
        assertEquals("1h 12m", Stats.format(72 * 60_000L))
        assertEquals("1:12:04", Stats.clock((72 * 60 + 4) * 1000L))
        assertEquals("9:05 PM", Stats.timeOfDay(21 * 60 + 5, false))
    }
}
