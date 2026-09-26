package com.latch.focus.engine

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps the last few crashes on the phone (crashes.json) so they show up in Feedback → Errors. Nothing is sent
 * anywhere. Installed first thing in [com.latch.focus.App], and it hands off to Android's own handler afterwards.
 */
object CrashLog {
    data class Crash(val at: Long, val summary: String, val trace: String, val device: String)

    private const val MAX = 20
    private lateinit var file: File
    private val _crashes = MutableStateFlow<List<Crash>>(emptyList())
    val crashes: StateFlow<List<Crash>> = _crashes.asStateFlow()

    fun install(context: Context) {
        file = File(context.filesDir, "crashes.json")
        _crashes.value = runCatching { read(file.readText()) }.getOrDefault(emptyList())
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val crash = Crash(System.currentTimeMillis(), "${e.javaClass.simpleName}: ${e.message ?: ""}".trim(), e.stackTraceToString(), device(context))
                save(listOf(crash) + _crashes.value)
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun delete(crash: Crash) = save(_crashes.value - crash)

    /** "Latch 0.1.0 · SM-S711U · Android 16", attached to every report. */
    fun device(context: Context): String {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        return "Latch $version · ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
    }

    private fun save(list: List<Crash>) {
        val kept = list.take(MAX)
        _crashes.value = kept
        val a = JSONArray()
        kept.forEach { a.put(JSONObject().put("at", it.at).put("summary", it.summary).put("trace", it.trace).put("device", it.device)) }
        runCatching { file.writeText(a.toString()) }
    }

    private fun read(text: String): List<Crash> {
        val a = JSONArray(text)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Crash(o.getLong("at"), o.optString("summary"), o.optString("trace"), o.optString("device"))
        }
    }
}
