package com.latch.focus.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.latch.focus.data.AppState
import com.latch.focus.data.Feedback
import com.latch.focus.data.FeedbackKind
import com.latch.focus.engine.CrashLog
import com.latch.focus.latch
import com.latch.focus.ui.theme.Type
import com.latch.focus.ui.theme.palette
import java.text.DateFormat
import java.util.Date

private enum class Tab(val label: String) { All("All"), Bugs("Bugs"), Features("Features"), Errors("Errors") }

/**
 * Bug reports, feature requests and crash logs, all kept on this phone. Write one at the top, filter with the tabs,
 * tap an entry to read it in full, mark it done or delete it. Errors are crashes Latch caught ([CrashLog]).
 */
@Composable
fun FeedbackScreen(state: AppState, onBack: () -> Unit) {
    val p = palette
    val context = LocalContext.current
    val crashes by CrashLog.crashes.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.All) }
    var kind by rememberSaveable { mutableStateOf(FeedbackKind.Bug) }
    var text by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<Feedback?>(null) }
    var openCrash by remember { mutableStateOf<CrashLog.Crash?>(null) }
    var copied by remember { mutableStateOf(false) }

    val shown = when (tab) {
        Tab.All -> state.feedback
        Tab.Bugs -> state.feedback.filter { it.kind == FeedbackKind.Bug }
        Tab.Features -> state.feedback.filter { it.kind == FeedbackKind.Feature }
        Tab.Errors -> emptyList()
    }.sortedBy { it.done } // open ones first, newest first within each (the list is stored newest first)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = p.text) }
            LargeTitle("Feedback", subtitle = if (copied) "Copied. Paste it anywhere." else "Bugs, ideas and errors. Saved on this phone.") {
                IconButton(onClick = { copy(context, report(state.feedback, crashes)); copied = true }) {
                    Icon(Icons.Rounded.ContentCopy, "Copy everything", tint = p.dim)
                }
            }
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Pills(
                    Tab.entries.map { t ->
                        val n = when (t) {
                            Tab.All -> state.feedback.count { !it.done }
                            Tab.Bugs -> state.feedback.count { !it.done && it.kind == FeedbackKind.Bug }
                            Tab.Features -> state.feedback.count { !it.done && it.kind == FeedbackKind.Feature }
                            Tab.Errors -> crashes.size
                        }
                        t to if (n > 0) "${t.label} $n" else t.label
                    },
                    tab, p.teal,
                ) { tab = it }
            }
        }

        if (tab != Tab.Errors) {
            item {
                Section("New") {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Pills(FeedbackKind.entries.map { it to it.label }, kind, kindColor(kind)) { kind = it }
                        OutlinedTextField(
                            text, { text = it },
                            placeholder = {
                                Text(
                                    when (kind) {
                                        FeedbackKind.Bug -> "What happened? What did you expect?"
                                        FeedbackKind.Feature -> "What would you like Latch to do?"
                                        FeedbackKind.Other -> "Anything on your mind"
                                    },
                                )
                            },
                            minLines = 3,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        InkButton("Save", enabled = text.isNotBlank()) {
                            context.latch.addFeedback(kind, text)
                            text = ""
                        }
                    }
                }
            }
            item {
                if (shown.isEmpty()) {
                    Empty(if (tab == Tab.All) "Nothing yet. Bugs and ideas you save show up here." else "No ${tab.label.lowercase()} yet.")
                } else {
                    Section(footer = "Tap one to read it, mark it done or delete it.") {
                        shown.forEachIndexed { i, f ->
                            if (i > 0) RowDivider()
                            ListRow(
                                f.text.lineSequence().first(), icon = kindIcon(f.kind), tint = if (f.done) p.faint else kindColor(f.kind),
                                titleColor = if (f.done) p.dim else p.text,
                                subtitle = listOfNotNull(f.kind.label, date(f.at), "Done".takeIf { f.done }, "Crash attached".takeIf { f.error != null }).joinToString(" · "),
                                trailing = if (f.done) ({ Icon(Icons.Rounded.CheckCircle, "Done", tint = p.fern) }) else null,
                            ) { open = f }
                        }
                    }
                }
            }
        } else {
            item {
                if (crashes.isEmpty()) {
                    Empty("No crashes. If Latch ever closes on its own, the error shows up here.")
                } else {
                    Section(footer = "Latch saves the last ${20} crashes. Tap one to see it or turn it into a bug report.") {
                        crashes.forEachIndexed { i, c ->
                            if (i > 0) RowDivider()
                            ListRow(c.summary, icon = Icons.Rounded.ErrorOutline, tint = p.brick, subtitle = date(c.at)) { openCrash = c }
                        }
                    }
                }
            }
        }
    }

    open?.let { f ->
        AlertDialog(
            onDismissRequest = { open = null },
            icon = { Icon(kindIcon(f.kind), null, tint = kindColor(f.kind)) },
            title = { Text(f.kind.label) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer { Text(f.text, style = Type.body, color = p.text) }
                    Spacer(Modifier.height(12.dp))
                    Text("${date(f.at)} · ${f.device}", style = Type.footnote, color = p.dim)
                    f.error?.let { Spacer(Modifier.height(12.dp)); Trace(it) }
                }
            },
            confirmButton = {
                TextButton(onClick = { context.latch.setFeedbackDone(f.id, !f.done); open = null }) { Text(if (f.done) "Reopen" else "Mark done") }
            },
            dismissButton = {
                TextButton(onClick = { context.latch.deleteFeedback(f.id); open = null }) { Text("Delete", color = p.brick) }
            },
        )
    }

    openCrash?.let { c ->
        AlertDialog(
            onDismissRequest = { openCrash = null },
            title = { Text("Crash") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("${date(c.at)} · ${c.device}", style = Type.footnote, color = p.dim)
                    Spacer(Modifier.height(10.dp))
                    Trace(c.trace)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.latch.addFeedback(FeedbackKind.Bug, "Latch crashed: ${c.summary}", error = "${c.device}\n${c.trace}")
                    CrashLog.delete(c); openCrash = null; tab = Tab.Bugs
                }) { Text("Report as bug") }
            },
            dismissButton = {
                TextButton(onClick = { CrashLog.delete(c); openCrash = null }) { Text("Delete", color = p.brick) }
            },
        )
    }
}

@Composable
private fun Trace(trace: String) {
    SelectionContainer {
        Text(trace, style = Type.footnote.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp), color = palette.dim)
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, style = Type.callout, color = palette.dim, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp))
}

@Composable
private fun kindColor(k: FeedbackKind): Color = when (k) {
    FeedbackKind.Bug -> palette.brick
    FeedbackKind.Feature -> palette.amber
    FeedbackKind.Other -> palette.teal
}

private fun kindIcon(k: FeedbackKind) = when (k) {
    FeedbackKind.Bug -> Icons.Rounded.BugReport
    FeedbackKind.Feature -> Icons.Rounded.Lightbulb
    FeedbackKind.Other -> Icons.Rounded.ChatBubbleOutline
}

private fun date(at: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at))

/** Everything as plain text, for "Copy everything". */
private fun report(feedback: List<Feedback>, crashes: List<CrashLog.Crash>): String = buildString {
    feedback.forEach { f ->
        append("[${f.kind.label}${if (f.done) ", done" else ""}] ${date(f.at)} · ${f.device}\n${f.text}\n")
        f.error?.let { append(it).append('\n') }
        append('\n')
    }
    crashes.forEach { c -> append("[Crash] ${date(c.at)} · ${c.device}\n${c.trace}\n\n") }
    if (isEmpty()) append("No feedback or crashes.")
}

private fun copy(context: android.content.Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Latch feedback", text))
}
