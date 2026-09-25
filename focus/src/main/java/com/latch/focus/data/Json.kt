package com.latch.focus.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

/** Saves [AppState] as one small JSON file. Unknown or missing fields fall back to defaults, never crash. */
object Json {
    fun write(s: AppState): String = JSONObject()
        .put("v", 1)
        .put("onboarded", s.onboarded)
        .put("tags", arr(s.tags) { JSONObject().put("uid", it.uid).put("name", it.name).put("pairedAt", it.pairedAt) })
        .put("modes", arr(s.modes, ::mode))
        .put("selected", s.selectedModeId ?: JSONObject.NULL)
        .put("timer", s.timerMinutes ?: JSONObject.NULL)
        .put("active", s.active?.let(::active) ?: JSONObject.NULL)
        .put("sessions", arr(s.sessions, ::session))
        .put("skips", JSONObject(s.skips.mapValues { it.value }))
        .put("passcode", s.passcode ?: JSONObject.NULL)
        .toString()

    fun read(text: String): AppState {
        val o = JSONObject(text)
        return AppState(
            onboarded = o.optBoolean("onboarded"),
            tags = list(o.optJSONArray("tags")) { LatchTag(it.getString("uid"), it.getString("name"), it.getLong("pairedAt")) },
            modes = list(o.optJSONArray("modes"), ::mode),
            selectedModeId = o.optStringOrNull("selected"),
            timerMinutes = if (o.isNull("timer") || !o.has("timer")) null else o.getInt("timer"),
            active = o.optJSONObject("active")?.let(::active),
            sessions = list(o.optJSONArray("sessions"), ::session),
            skips = o.optJSONObject("skips")?.let { sk -> sk.keys().asSequence().associateWith { sk.getLong(it) } }.orEmpty(),
            passcode = o.optStringOrNull("passcode"),
        )
    }

    private fun mode(m: Mode) = JSONObject()
        .put("id", m.id).put("name", m.name).put("hue", m.hue.name).put("type", m.type.name)
        .put("apps", JSONArray(m.apps.toList())).put("silence", m.silence)
        .put("schedules", arr(m.schedules) {
            JSONObject().put("id", it.id).put("days", JSONArray(it.days.map { d -> d.value }))
                .put("start", it.startMin).put("end", it.endMin).put("enabled", it.enabled)
        })

    private fun mode(o: JSONObject) = Mode(
        id = o.getString("id"),
        name = o.getString("name"),
        hue = enumOr(o.optString("hue"), Hue.Teal),
        type = enumOr(o.optString("type"), ListType.Block),
        apps = strings(o.optJSONArray("apps")),
        silence = o.optBoolean("silence"),
        schedules = list(o.optJSONArray("schedules")) {
            Schedule(
                id = it.getString("id"),
                days = ints(it.optJSONArray("days")).map { d -> DayOfWeek.of(d) }.toSet(),
                startMin = it.getInt("start"), endMin = it.getInt("end"), enabled = it.optBoolean("enabled", true),
            )
        },
    )

    private fun active(a: Active) = JSONObject()
        .put("modeId", a.modeId).put("modeName", a.modeName).put("hue", a.hue.name).put("start", a.start)
        .put("endsAt", a.endsAt ?: JSONObject.NULL).put("blocked", JSONArray(a.blocked.toList())).put("silence", a.silence)
        .put("trigger", a.trigger.name).put("scheduleId", a.scheduleId ?: JSONObject.NULL).put("hidden", a.hidden)

    private fun active(o: JSONObject) = Active(
        modeId = o.getString("modeId"), modeName = o.getString("modeName"), hue = enumOr(o.optString("hue"), Hue.Teal),
        start = o.getLong("start"), endsAt = if (o.isNull("endsAt")) null else o.getLong("endsAt"),
        blocked = strings(o.optJSONArray("blocked")), silence = o.optBoolean("silence"),
        trigger = enumOr(o.optString("trigger"), Trigger.Tag), scheduleId = o.optStringOrNull("scheduleId"), hidden = o.optInt("hidden"),
    )

    private fun session(x: Session) = JSONObject()
        .put("modeId", x.modeId).put("modeName", x.modeName).put("hue", x.hue.name).put("start", x.start).put("end", x.end)
        .put("trigger", x.trigger.name).put("reason", x.endReason.name).put("hidden", x.hidden)

    private fun session(o: JSONObject) = Session(
        modeId = o.getString("modeId"), modeName = o.getString("modeName"), hue = enumOr(o.optString("hue"), Hue.Teal),
        start = o.getLong("start"), end = o.getLong("end"), trigger = enumOr(o.optString("trigger"), Trigger.Tag),
        endReason = enumOr(o.optString("reason"), EndReason.Tag), hidden = o.optInt("hidden"),
    )

    private fun <T> arr(items: List<T>, f: (T) -> JSONObject) = JSONArray().also { a -> items.forEach { a.put(f(it)) } }
    private fun <T> list(a: JSONArray?, f: (JSONObject) -> T): List<T> =
        (0 until (a?.length() ?: 0)).mapNotNull { runCatching { f(a!!.getJSONObject(it)) }.getOrNull() }
    private fun strings(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }.toSet()
    private fun ints(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getInt(it) }
    private fun JSONObject.optStringOrNull(k: String) = if (!has(k) || isNull(k)) null else getString(k)
    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        runCatching { enumValueOf<E>(name) }.getOrDefault(fallback)
}
