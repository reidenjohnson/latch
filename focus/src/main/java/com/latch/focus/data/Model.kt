package com.latch.focus.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Each mode wears one of the four Kairos hues, so the app's color comes from what you're doing. */
enum class Hue { Teal, Fern, Amber, Brick }

enum class ListType {
    /** Block the chosen apps, allow everything else. */
    Block,
    /** Allow only the chosen apps, block everything else. */
    Allow,
}

/**
 * A recurring window, e.g. weekdays 9:00–17:00. [startMin]/[endMin] are minutes after midnight. If the end is at or
 * before the start, the window runs past midnight into the next day (22:00–07:00). [days] are the days it *starts* on.
 */
data class Schedule(
    val id: String,
    val days: Set<DayOfWeek>,
    val startMin: Int,
    val endMin: Int,
    val enabled: Boolean = true,
) {
    private val crossesMidnight get() = endMin <= startMin

    /** The window (start, end in epoch ms) that contains [now], or null. */
    fun windowAt(now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long>? {
        if (!enabled || days.isEmpty()) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        // A window that started yesterday can still be running today if it crosses midnight.
        for (day in listOf(today, today.minusDays(1))) {
            val w = windowOn(day, zone) ?: continue
            if (now >= w.first && now < w.second) return w
        }
        return null
    }

    /** The next time after [now] that this schedule starts or ends, or null if it never does. */
    fun nextBoundary(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!enabled || days.isEmpty()) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return (-1L..7L).mapNotNull { windowOn(today.plusDays(it), zone) }
            .flatMap { listOf(it.first, it.second) }
            .filter { it > now }
            .minOrNull()
    }

    private fun windowOn(day: LocalDate, zone: ZoneId): Pair<Long, Long>? {
        if (day.dayOfWeek !in days) return null
        val start = day.atStartOfDay(zone).plusMinutes(startMin.toLong())
        val endDay = if (crossesMidnight) day.plusDays(1) else day
        val end = endDay.atStartOfDay(zone).plusMinutes(endMin.toLong())
        return start.toInstant().toEpochMilli() to end.toInstant().toEpochMilli()
    }
}

data class Mode(
    val id: String,
    val name: String,
    val hue: Hue,
    val type: ListType = ListType.Block,
    val apps: Set<String> = emptySet(),
    /** Hide notifications from blocked apps during this mode (needs notification access). */
    val silence: Boolean = false,
    val schedules: List<Schedule> = emptyList(),
) {
    /** Allow-lists are always usable: with nothing picked, only the always-allowed essentials stay open. */
    val ready: Boolean get() = type == ListType.Allow || apps.isNotEmpty()
}

data class LatchTag(val uid: String, val name: String, val pairedAt: Long)

enum class Trigger { Tag, Hold, Schedule }
enum class EndReason { Tag, Timer, Schedule, Emergency, Passcode }

/** A running session. The mode is copied in, so editing modes can't change a session that's already going. */
data class Active(
    val modeId: String,
    val modeName: String,
    val hue: Hue,
    val start: Long,
    /** When it ends by itself (timer or schedule), or null if only a tag ends it. */
    val endsAt: Long?,
    val blocked: Set<String>,
    val silence: Boolean,
    val trigger: Trigger,
    val scheduleId: String? = null,
    val hidden: Int = 0,
)

data class Session(
    val modeId: String,
    val modeName: String,
    val hue: Hue,
    val start: Long,
    val end: Long,
    val trigger: Trigger,
    val endReason: EndReason,
    val hidden: Int = 0,
) {
    val length: Long get() = end - start
}

data class AppState(
    val onboarded: Boolean = false,
    val tags: List<LatchTag> = emptyList(),
    val modes: List<Mode> = emptyList(),
    val selectedModeId: String? = null,
    /** Length for the next manual session in minutes, or null for "until I tap my Latch". */
    val timerMinutes: Int? = null,
    val active: Active? = null,
    val sessions: List<Session> = emptyList(),
    /** Schedule windows the user ended early, by schedule id → window end. They don't restart until then. */
    val skips: Map<String, Long> = emptyMap(),
    /** Optional unlock passcode, as a salted hash (see [Passcode]). Null = none set. */
    val passcode: String? = null,
) {
    fun isPaired(uid: String) = tags.any { it.uid == uid }
    val selectedMode: Mode? get() = modes.firstOrNull { it.id == selectedModeId } ?: modes.firstOrNull()
    fun mode(id: String?) = modes.firstOrNull { it.id == id }
    val emergencyUnlocks: Int get() = sessions.count { it.endReason == EndReason.Emergency }
}
