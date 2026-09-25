package com.latch.focus.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Time-in-Focus math for the Activity screen. Sessions crossing a boundary only count the part inside the range. */
object Stats {
    fun total(s: AppState, from: Long, to: Long): Long {
        val spans = s.sessions.map { it.start to it.end } + listOfNotNull(s.active?.let { it.start to to })
        return spans.sumOf { (a, b) -> (minOf(b, to) - maxOf(a, from)).coerceAtLeast(0) }
    }

    fun dayStart(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    fun today(now: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    fun weekStart(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        dayStart(today(now, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), zone)
    fun monthStart(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long = dayStart(today(now, zone).withDayOfMonth(1), zone)

    /** Minutes in Focus for each of the last 7 days (oldest first), split by hue, for the stacked week chart. */
    fun lastSevenDays(s: AppState, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, Map<Hue, Long>>> {
        val today = today(now, zone)
        return (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            val from = dayStart(day, zone)
            val to = minOf(dayStart(day.plusDays(1), zone), now)
            val byHue = Hue.entries.associateWith { hue ->
                val spans = s.sessions.filter { it.hue == hue }.map { it.start to it.end } +
                    listOfNotNull(s.active?.takeIf { it.hue == hue }?.let { it.start to now })
                spans.sumOf { (a, b) -> (minOf(b, to) - maxOf(a, from)).coerceAtLeast(0) }
            }
            day to byHue
        }
    }

    /** Consecutive days, ending today or yesterday, with at least one finished session. */
    fun streak(s: AppState, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val days = s.sessions.map { today(it.start, zone) }.toSet() + listOfNotNull(s.active?.let { today(it.start, zone) })
        var day = today(now, zone)
        if (day !in days) day = day.minusDays(1)
        var n = 0
        while (day in days) { n++; day = day.minusDays(1) }
        return n
    }

    fun average(s: AppState): Long = if (s.sessions.isEmpty()) 0 else s.sessions.sumOf { it.length } / s.sessions.size
    fun longest(s: AppState): Long = s.sessions.maxOfOrNull { it.length } ?: 0

    /** "1h 12m", "45m", "30s". */
    fun format(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> if (m > 0) "${h}h ${m}m" else "${h}h"
            totalMin > 0 -> "${m}m"
            else -> "${(ms / 1000).coerceAtLeast(0)}s"
        }
    }

    /** "1:12:04" for live timers. */
    fun clock(ms: Long): String {
        val t = (ms / 1000).coerceAtLeast(0)
        return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
    }

    /** "9:00 AM" style label for minutes after midnight, in the phone's 12/24-hour format. */
    fun timeOfDay(min: Int, is24: Boolean): String {
        val h = min / 60 % 24
        val m = min % 60
        return if (is24) "%d:%02d".format(h, m) else "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
    }
}
