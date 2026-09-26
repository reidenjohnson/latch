package com.latch.focus.engine

import android.content.Context
import com.latch.focus.data.AppState
import com.latch.focus.data.Change
import com.latch.focus.data.EndReason
import com.latch.focus.data.Feedback
import com.latch.focus.data.FeedbackKind
import com.latch.focus.data.Hue
import com.latch.focus.data.Json
import com.latch.focus.data.LatchTag
import com.latch.focus.data.ListType
import com.latch.focus.data.Mode
import com.latch.focus.data.Passcode
import com.latch.focus.data.Rules
import com.latch.focus.data.Trigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The single owner of Latch's state. Wraps the pure [Rules] with persistence (focus.json), the Focus notification
 * and the wake-up alarm. Every change goes through [apply], so those three always stay in sync.
 */
class Engine(private val context: Context) {
    private val file = File(context.filesDir, "latch.json")
    private val io = Executors.newSingleThreadExecutor()
    private val notifier = Notifier(context)
    private var lastWake: Long? = -1L
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** The latest start/end, for the in-app confirmation. The pair's first value is a unique stamp. */
    private val _events = MutableStateFlow<Pair<Long, Change>?>(null)
    val events: StateFlow<Pair<Long, Change>?> = _events.asStateFlow()

    private val blockedFor: (Mode) -> Set<String> = { mode ->
        val never = Essentials.of(context)
        when (mode.type) {
            ListType.Block -> mode.apps - never
            ListType.Allow -> Essentials.launchable(context) - mode.apps - never
        }
    }

    init {
        _state.value = withDefaultMode(_state.value)
        tick()
    }

    /** Every install needs at least one mode. It used to be called "Focus", which is Apple's word, so rename that one. */
    private fun withDefaultMode(s: AppState): AppState {
        if (s.modes.isEmpty()) {
            val first = Mode(UUID.randomUUID().toString(), "Everyday", Hue.Teal)
            return s.copy(modes = listOf(first), selectedModeId = first.id)
        }
        return s.copy(
            modes = s.modes.map { if (it.name == "Focus") it.copy(name = "Everyday") else it },
            active = s.active?.let { if (it.modeName == "Focus") it.copy(modeName = "Everyday") else it },
        )
    }

    // ---- Sessions

    fun tap(uid: String): Change? = apply { Rules.tap(it, uid, now(), blockedFor) }

    /** Start without the tag (press and hold on the home screen). Ending still needs the tag, a timer or emergency. */
    fun startByHold(): Change? = apply { s ->
        val mode = s.selectedMode ?: return@apply s to Change.NoMode
        Rules.start(s, mode, Trigger.Hold, now(), blockedFor, endsAt = s.timerMinutes?.let { now() + it * 60_000L })
    }

    /** Unlatch without the tag. Limited to [Rules.EMERGENCY_PER_YEAR] a year; returns null once they're used up. */
    fun emergencyUnlock(): Change? = apply { Rules.emergency(it, now()) }

    // ---- Feedback. Allowed while latched: a bug can show up mid-session.

    fun addFeedback(kind: FeedbackKind, text: String, error: String? = null) = update { s ->
        s.copy(feedback = listOf(Feedback(UUID.randomUUID().toString(), kind, text.trim(), now(), CrashLog.device(context), error = error)) + s.feedback)
    }
    fun setFeedbackDone(id: String, done: Boolean) = update { s -> s.copy(feedback = s.feedback.map { if (it.id == id) it.copy(done = done) else it }) }
    fun deleteFeedback(id: String) = update { s -> s.copy(feedback = s.feedback.filterNot { it.id == id }) }

    /** Unlatch with the passcode instead of the tag. Returns false (and changes nothing) if it's wrong. */
    fun unlockWithPasscode(code: String): Boolean {
        if (!Passcode.matches(code, _state.value.passcode)) return false
        apply { Rules.end(it, EndReason.Passcode, now()) }
        return true
    }

    /** Unlatch later, at least an hour from now. Returns false if that isn't allowed (see [Rules.endLater]). */
    fun endLater(endsAt: Long): Boolean {
        var ok = false
        apply { s -> val next = Rules.endLater(s, endsAt, now()); ok = next != null; (next ?: s) to null }
        return ok
    }

    /** Set, change or remove (null) the passcode. Refused while latched, like other settings. */
    fun setPasscode(code: String?) = edit { it.copy(passcode = code?.let(Passcode::hash)) }

    fun tick(): Change? = apply { Rules.tick(it, now(), blockedFor) }

    fun countHidden() = update { s -> s.copy(active = s.active?.let { it.copy(hidden = it.hidden + 1) }) }

    // ---- Settings. Refused while a session is running, so Focus can't be edited away mid-session.

    fun finishOnboarding() = update { it.copy(onboarded = true) }
    fun pair(uid: String) = edit { s -> if (s.isPaired(uid)) s else s.copy(tags = s.tags + LatchTag(uid, if (s.tags.isEmpty()) "My Latch" else "Latch ${s.tags.size + 1}", now())) }
    fun unpair(uid: String) = edit { s -> s.copy(tags = s.tags.filterNot { it.uid == uid }) }
    fun renameTag(uid: String, name: String) = edit { s -> s.copy(tags = s.tags.map { if (it.uid == uid && name.isNotBlank()) it.copy(name = name.trim()) else it }) }
    fun select(modeId: String) = update { if (it.active == null) it.copy(selectedModeId = modeId) else it }
    fun setTimer(minutes: Int?) = update { if (it.active == null) it.copy(timerMinutes = minutes) else it }
    fun saveMode(mode: Mode) = edit { s ->
        val exists = s.modes.any { it.id == mode.id }
        s.copy(modes = if (exists) s.modes.map { if (it.id == mode.id) mode else it } else s.modes + mode)
    }
    fun deleteMode(id: String) = edit { s ->
        if (s.modes.size <= 1) s else s.copy(modes = s.modes.filterNot { it.id == id }, selectedModeId = s.selectedModeId.takeIf { it != id })
    }
    fun clearHistory() = edit { it.copy(sessions = emptyList()) }

    /** Show the first-run screens again. Keeps modes, tags and history. */
    fun replayOnboarding() = edit { it.copy(onboarded = false) }

    /**
     * Start over: forgets modes, paired tags and history, and goes back to first-run setup. Refused while latched,
     * so it can't be used to skip the tag (the emergency unlock is the way out). Needs the passcode if one is set.
     * Theme and haptics are kept, and so are this year's emergency unlocks (a reset mustn't refill them) and feedback.
     */
    fun resetAll(code: String?): Boolean {
        if (locked) return false
        val s0 = _state.value
        if (s0.passcode != null && (code == null || !Passcode.matches(code, s0.passcode))) return false
        apply { s -> withDefaultMode(AppState(emergencyUses = s.emergencyUses, feedback = s.feedback)) to null }
        return true
    }

    val locked: Boolean get() = _state.value.active != null

    // ---- Plumbing

    private fun edit(change: (AppState) -> AppState) = update { if (it.active != null) it else change(it) }

    private fun update(change: (AppState) -> AppState) { apply { change(it) to null } }

    @Synchronized
    private fun apply(step: (AppState) -> Pair<AppState, Change?>): Change? {
        val before = _state.value
        val (after, change) = step(before)
        if (after != before) {
            _state.value = after
            io.execute { save(after) }
            if (after.active != before.active) notifier.update(after.active)
        }
        // The blocker ticks on every app switch, so only touch AlarmManager when the wake time actually changes.
        val wake = Rules.nextWake(after, now())
        if (wake != lastWake) { Alarms.schedule(context, wake); lastWake = wake }
        if (change is Change.Started && before.active == null || change is Change.Ended) _events.value = System.nanoTime() to change!!
        return change
    }

    private fun save(s: AppState) = runCatching {
        val tmp = File(file.parentFile, "latch.json.tmp")
        tmp.writeText(Json.write(s))
        tmp.renameTo(file)
    }

    private fun load(): AppState = runCatching { if (file.exists()) Json.read(file.readText()) else AppState() }.getOrDefault(AppState())

    private fun now() = System.currentTimeMillis()
}
