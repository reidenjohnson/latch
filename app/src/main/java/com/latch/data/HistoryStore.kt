package com.latch.data

import android.content.Context
import android.nfc.NdefMessage
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

enum class HistoryAction { Read, Write, Erase, Copy, Lock }

data class HistoryEntry(
    val id: Long,
    val time: Long,
    val action: HistoryAction,
    val summary: String,
    val tagType: String?,
    /** The NDEF bytes involved, base64. Lets "Write again" rebuild the exact same tag. */
    val messageB64: String?,
) {
    fun message(): NdefMessage? = messageB64?.let {
        runCatching { NdefMessage(Base64.decode(it, Base64.NO_WRAP)) }.getOrNull()
    }
}

/** Recent reads and writes, kept in a small JSON file in app storage. */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "history.json")
    private val io = Executors.newSingleThreadExecutor()
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    fun add(action: HistoryAction, summary: String, tagType: String?, message: NdefMessage?) {
        val now = System.currentTimeMillis()
        val entry = HistoryEntry(
            id = now * 1000 + (0..999).random(),
            time = now,
            action = action,
            summary = summary,
            tagType = tagType,
            messageB64 = message?.let { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) },
        )
        _entries.update { (listOf(entry) + it).take(MAX) }
        persist()
    }

    fun remove(id: Long) { _entries.update { list -> list.filterNot { it.id == id } }; persist() }
    fun clear() { _entries.value = emptyList(); persist() }

    private fun persist() {
        val snapshot = _entries.value
        io.execute {
            val arr = JSONArray()
            snapshot.forEach { e ->
                arr.put(JSONObject().apply {
                    put("id", e.id); put("time", e.time); put("action", e.action.name)
                    put("summary", e.summary); put("tagType", e.tagType ?: JSONObject.NULL)
                    put("message", e.messageB64 ?: JSONObject.NULL)
                })
            }
            runCatching {
                val tmp = File(file.parentFile, "history.json.tmp")
                tmp.writeText(arr.toString())
                tmp.renameTo(file)
            }
        }
    }

    private fun load(): List<HistoryEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val action = runCatching { HistoryAction.valueOf(o.getString("action")) }.getOrNull() ?: return@mapNotNull null
            HistoryEntry(
                id = o.getLong("id"),
                time = o.getLong("time"),
                action = action,
                summary = o.getString("summary"),
                tagType = o.optString("tagType").takeIf { !o.isNull("tagType") && it.isNotEmpty() },
                messageB64 = o.optString("message").takeIf { !o.isNull("message") && it.isNotEmpty() },
            )
        }
    }.getOrDefault(emptyList())

    private companion object { const val MAX = 200 }
}
