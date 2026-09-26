package com.latch.focus.data

import java.time.ZoneId

/** What a state change did, so the UI can confirm it ("Focus on", "You stayed focused for 1h 12m"). */
sealed interface Change {
    data class Started(val active: Active) : Change
    data class Ended(val session: Session) : Change
    data object NotPaired : Change
    data object NoMode : Change
}

/**
 * The pure rules of Latch, with no Android in them, so they're unit-tested. [blockedFor] turns a mode into the
 * exact package set to block (it needs the phone's app list, so the caller provides it).
 */
object Rules {

    fun start(
        s: AppState, mode: Mode, trigger: Trigger, now: Long, blockedFor: (Mode) -> Set<String>,
        endsAt: Long? = null, scheduleId: String? = null,
    ): Pair<AppState, Change> {
        if (s.active != null) return s to Change.Started(s.active)
        if (!mode.ready) return s to Change.NoMode
        val active = Active(
            modeId = mode.id, modeName = mode.name, hue = mode.hue, start = now, endsAt = endsAt,
            blocked = blockedFor(mode), silence = mode.silence, trigger = trigger, scheduleId = scheduleId,
        )
        return s.copy(active = active) to Change.Started(active)
    }

    fun end(s: AppState, reason: EndReason, now: Long): Pair<AppState, Change?> {
        val a = s.active ?: return s to null
        val session = Session(a.modeId, a.modeName, a.hue, a.start, now, a.trigger, reason, a.hidden)
        // Ending a scheduled session early skips the rest of that window, so it doesn't instantly start again.
        val skips = if (a.scheduleId != null && a.endsAt != null && now < a.endsAt) s.skips + (a.scheduleId to a.endsAt) else s.skips
        return s.copy(active = null, sessions = (listOf(session) + s.sessions).take(MAX_SESSIONS), skips = skips) to Change.Ended(session)
    }

    /** Emergency unlocks allowed in any rolling year. Uninstalling Latch is always the last way out. */
    const val EMERGENCY_PER_YEAR = 5
    const val YEAR_MS = 365L * 24 * 60 * 60_000L

    fun emergencyLeft(s: AppState, now: Long): Int =
        (EMERGENCY_PER_YEAR - s.emergencyUses.count { it > now - YEAR_MS }).coerceAtLeast(0)

    /** When the next emergency unlock comes back, if they're all used up. */
    fun emergencyBackAt(s: AppState, now: Long): Long? =
        if (emergencyLeft(s, now) > 0) null else s.emergencyUses.filter { it > now - YEAR_MS }.minOrNull()?.plus(YEAR_MS)

    /** Unlatch without the tag, if one of this year's emergency unlocks is left. Otherwise nothing changes. */
    fun emergency(s: AppState, now: Long): Pair<AppState, Change?> {
        if (s.active == null || emergencyLeft(s, now) == 0) return s to null
        val (next, c) = end(s, EndReason.Emergency, now)
        return next.copy(emergencyUses = (listOf(now) + s.emergencyUses).filter { it > now - YEAR_MS }) to c
    }

    /** The earliest an "unlatch later" can be: an hour from now. No instant gratification. */
    const val MIN_LATER_MS = 60 * 60_000L

    /**
     * Unlatch later: set the running session to end at [endsAt]. Only allowed at least [MIN_LATER_MS] from now, and
     * only to make a session shorter (or give an open-ended one an end), never longer. Returns null if not allowed.
     */
    fun endLater(s: AppState, endsAt: Long, now: Long): AppState? {
        val a = s.active ?: return null
        if (endsAt < now + MIN_LATER_MS) return null
        if (a.endsAt != null && endsAt >= a.endsAt) return null
        // A shortened scheduled session mustn't restart when it ends: skip the rest of its window.
        val skips = if (a.scheduleId != null && a.endsAt != null) s.skips + (a.scheduleId to a.endsAt) else s.skips
        return s.copy(active = a.copy(endsAt = endsAt), skips = skips)
    }

    /** A paired tag was tapped: end the session if one's running, otherwise start the selected mode. */
    fun tap(s: AppState, uid: String, now: Long, blockedFor: (Mode) -> Set<String>): Pair<AppState, Change?> {
        if (!s.isPaired(uid)) return s to Change.NotPaired
        if (s.active != null) return end(s, EndReason.Tag, now)
        val mode = s.selectedMode ?: return s to Change.NoMode
        return start(s, mode, Trigger.Tag, now, blockedFor, endsAt = s.timerMinutes?.let { now + it * 60_000L })
    }

    /**
     * Brings the state up to date with the clock: ends sessions whose time is up, and starts a scheduled mode if
     * one of its windows is open (and wasn't skipped). Safe to call as often as you like.
     */
    fun tick(s: AppState, now: Long, blockedFor: (Mode) -> Set<String>, zone: ZoneId = ZoneId.systemDefault()): Pair<AppState, Change?> {
        var state = s.copy(skips = s.skips.filterValues { it > now })
        var change: Change? = null
        val a = state.active
        if (a?.endsAt != null && now >= a.endsAt) {
            val (next, c) = end(state, if (a.scheduleId != null) EndReason.Schedule else EndReason.Timer, a.endsAt)
            state = next; change = c
        }
        if (state.active == null) {
            for (mode in state.modes) {
                if (!mode.ready) continue
                for (sch in mode.schedules) {
                    val w = sch.windowAt(now, zone) ?: continue
                    if ((state.skips[sch.id] ?: 0) >= w.second) continue
                    val (next, c) = start(state, mode, Trigger.Schedule, now, blockedFor, endsAt = w.second, scheduleId = sch.id)
                    return next to c
                }
            }
        }
        return state to change
    }

    /** The next moment [tick] needs to run: a session's end or any schedule's start or end. */
    fun nextWake(s: AppState, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? =
        (listOfNotNull(s.active?.endsAt) + s.modes.flatMap { m -> m.schedules.mapNotNull { it.nextBoundary(now, zone) } })
            .filter { it > now }
            .minOrNull()

    const val MAX_SESSIONS = 2000
}
