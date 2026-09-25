package com.latch.focus.engine

import android.content.Context
import com.latch.focus.data.AppState
import com.latch.focus.data.Change
import com.latch.focus.data.EndReason
import com.latch.focus.data.Hue
import com.latch.focus.data.Json
import com.latch.focus.data.LatchTag
import com.latch.focus.data.ListType
import com.latch.focus.data.Mode
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
        if (_state.value.modes.isEmpty()) {
            val first = Mode(UUID.randomUUID().toString(), "Focus", Hue.Teal)
            _state.value = _state.value.copy(modes = listOf(first), selectedModeId = first.id)
        }
        tick()
    }

    // ---- Sessions

    fun tap(uid: String): Change? = apply { Rules.tap(it, uid, now(), blockedFor) }

    /** Start without the tag (press and hold on the home screen). Ending still needs the tag, a timer or emergency. */
    fun startByHold(): Change? = apply { s ->
        val mode = s.selectedMode ?: return@apply s to Change.NoMode
        Rules.start(s, mode, Trigger.Hold, now(), blockedFor, endsAt = s.timerMinutes?.let { now() + it * 60_000L })
    }

    fun emergencyUnlock(): Change? = apply { Rules.end(it, EndReason.Emergency, now()) }

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
