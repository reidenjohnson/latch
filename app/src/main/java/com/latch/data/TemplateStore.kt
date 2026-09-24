package com.latch.data

import android.content.Context
import com.latch.nfc.RecordSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class Template(val id: Long, val name: String, val specs: List<RecordSpec>)

/** Saved tag designs the user can write again anytime. */
class TemplateStore(context: Context) {
    private val file = File(context.filesDir, "templates.json")
    private val io = Executors.newSingleThreadExecutor()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Template>> = _items.asStateFlow()

    fun save(name: String, specs: List<RecordSpec>) {
        _items.update { listOf(Template(System.currentTimeMillis(), name.trim().ifEmpty { "Untitled" }, specs)) + it }
        persist()
    }

    fun remove(id: Long) { _items.update { l -> l.filterNot { it.id == id } }; persist() }

    private fun persist() {
        val snapshot = _items.value
        io.execute {
            val arr = JSONArray()
            snapshot.forEach { t ->
                arr.put(JSONObject().put("id", t.id).put("name", t.name).put("specs", RecordSpec.listToJson(t.specs)))
            }
            runCatching {
                val tmp = File(file.parentFile, "templates.json.tmp")
                tmp.writeText(arr.toString())
                tmp.renameTo(file)
            }
        }
    }

    private fun load(): List<Template> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Template(o.getLong("id"), o.getString("name"), RecordSpec.listFromJson(o.getString("specs")))
        }
    }.getOrDefault(emptyList())
}

/** Hands a tag's records from the Read tab to the Write tab ("Edit"). */
class DraftHandoff {
    val pending = MutableStateFlow<List<RecordSpec>?>(null)
}

/** Minimal RFC 4180 CSV parser: quoted fields, escaped quotes, CRLF or LF line endings. */
object Csv {
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row += field.toString(); field.clear() }
                '\r' -> Unit
                '\n' -> { row += field.toString(); field.clear(); rows += row; row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}
