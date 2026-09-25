package com.latch.focus

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.Executors

enum class FocusMode { Block, AllowOnly }

data class FocusTag(val uid: String, val name: String, val pairedAt: Long)

data class FocusSession(val start: Long, val end: Long, val emergency: Boolean) {
    val length: Long get() = end - start
}

/** A running Focus session. [blocked] is fixed when it starts, so editing settings can't change it mid-session. */
data class ActiveFocus(val start: Long, val blocked: Set<String>)

data class FocusState(
    val tags: List<FocusTag> = emptyList(),
    val apps: Set<String> = emptySet(),
    val mode: FocusMode = FocusMode.Block,
    val active: ActiveFocus? = null,
    val sessions: List<FocusSession> = emptyList(),
) {
    fun isPaired(uid: String) = tags.any { it.uid == uid }
    val hasApps: Boolean get() = mode == FocusMode.AllowOnly || apps.isNotEmpty()
}

sealed interface FocusResult {
    data class Started(val blockedCount: Int, val blockerOn: Boolean) : FocusResult
    data class Ended(val session: FocusSession) : FocusResult
    data object NotPaired : FocusResult
    data object NoApps : FocusResult
}

/**
 * Owns Focus: paired tags, the app list, the running session and past sessions. Everything is kept in memory and
 * saved to a small JSON file, so a session survives the app being closed or the phone restarting.
 */
class FocusManager(private val context: Context) {
    private val file = File(context.filesDir, "focus.json")
    private val io = Executors.newSingleThreadExecutor()
    private val notifier = FocusNotifier(context)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    init {
        _state.value.active?.let { notifier.show(it) }
    }

    /** Tapping a paired tag starts Focus, or ends it if it's already on. */
    @Synchronized
    fun toggle(uid: String): FocusResult {
        val s = _state.value
        if (!s.isPaired(uid)) return FocusResult.NotPaired
        val active = s.active
        return if (active != null) end(emergency = false) else start()
    }

    @Synchronized
    fun emergencyUnlock(): FocusResult? = if (_state.value.active != null) end(emergency = true) else null

    private fun start(): FocusResult {
        val s = _state.value
        if (!s.hasApps) return FocusResult.NoApps
        val never = Essentials.of(context)
        val blocked = when (s.mode) {
            FocusMode.Block -> s.apps - never
            FocusMode.AllowOnly -> Essentials.launchable(context) - s.apps - never
        }
        val active = ActiveFocus(System.currentTimeMillis(), blocked)
        commit { it.copy(active = active) }
        notifier.show(active)
        return FocusResult.Started(blocked.size, FocusBlocker.running.value)
    }

    private fun end(emergency: Boolean): FocusResult {
        val active = _state.value.active ?: return FocusResult.NotPaired
        val session = FocusSession(active.start, System.currentTimeMillis(), emergency)
        commit { it.copy(active = null, sessions = (listOf(session) + it.sessions).take(MAX_SESSIONS)) }
        notifier.cancel()
        return FocusResult.Ended(session)
    }

    // Settings. The UI disables these during a session, and they're refused here too, so Focus can't be edited away.
    fun pair(uid: String) {
        if (_state.value.isPaired(uid)) return
        commit { it.copy(tags = it.tags + FocusTag(uid, "Tag ${it.tags.size + 1}", System.currentTimeMillis())) }
    }
    fun unpair(uid: String) = edit { it.copy(tags = it.tags.filterNot { t -> t.uid == uid }) }
    fun rename(uid: String, name: String) = edit { s -> s.copy(tags = s.tags.map { if (it.uid == uid) it.copy(name = name.trim().ifEmpty { it.name }) else it }) }
    fun setApps(apps: Set<String>) = edit { it.copy(apps = apps) }
    fun setMode(mode: FocusMode) = edit { it.copy(mode = mode) }
    fun clearSessions() = edit { it.copy(sessions = emptyList()) }

    private fun edit(change: (FocusState) -> FocusState) {
        if (_state.value.active != null) return
        commit(change)
    }

    private fun commit(change: (FocusState) -> FocusState) {
        _state.update(change)
        val snapshot = _state.value
        io.execute { save(snapshot) }
    }

    private fun save(s: FocusState) {
        val o = JSONObject()
            .put("tags", JSONArray().also { a -> s.tags.forEach { a.put(JSONObject().put("uid", it.uid).put("name", it.name).put("pairedAt", it.pairedAt)) } })
            .put("apps", JSONArray(s.apps.toList()))
            .put("mode", s.mode.name)
            .put("active", s.active?.let { JSONObject().put("start", it.start).put("blocked", JSONArray(it.blocked.toList())) } ?: JSONObject.NULL)
            .put("sessions", JSONArray().also { a -> s.sessions.forEach { a.put(JSONObject().put("start", it.start).put("end", it.end).put("emergency", it.emergency)) } })
        runCatching {
            val tmp = File(file.parentFile, "focus.json.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file)
        }
    }

    private fun load(): FocusState = runCatching {
        if (!file.exists()) return FocusState()
        val o = JSONObject(file.readText())
        fun strings(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }.toSet()
        val tags = o.optJSONArray("tags")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.map { FocusTag(it.getString("uid"), it.getString("name"), it.getLong("pairedAt")) }
        }.orEmpty()
        val sessions = o.optJSONArray("sessions")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.map { FocusSession(it.getLong("start"), it.getLong("end"), it.optBoolean("emergency")) }
        }.orEmpty()
        FocusState(
            tags = tags,
            apps = strings(o.optJSONArray("apps")),
            mode = runCatching { FocusMode.valueOf(o.getString("mode")) }.getOrDefault(FocusMode.Block),
            active = o.optJSONObject("active")?.let { ActiveFocus(it.getLong("start"), strings(it.optJSONArray("blocked"))) },
            sessions = sessions,
        )
    }.getOrDefault(FocusState())

    private companion object { const val MAX_SESSIONS = 500 }
}

/** Totals for the stats card. Sessions that cross midnight only count the part inside the range. */
object FocusStats {
    fun startOfToday(now: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun startOfWeek(now: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = startOfToday(now); set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        if (timeInMillis > now) add(Calendar.WEEK_OF_YEAR, -1)
    }.timeInMillis

    fun total(s: FocusState, from: Long, now: Long = System.currentTimeMillis()): Long {
        val spans = s.sessions.map { it.start to it.end } + listOfNotNull(s.active?.let { it.start to now })
        return spans.sumOf { (a, b) -> (minOf(b, now) - maxOf(a, from)).coerceAtLeast(0) }
    }

    fun longest(s: FocusState): Long = s.sessions.maxOfOrNull { it.length } ?: 0

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

    /** "1:12:04" for the live timer. */
    fun clock(ms: Long): String {
        val t = (ms / 1000).coerceAtLeast(0)
        return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
    }
}
