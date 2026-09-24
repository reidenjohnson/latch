package com.latch.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.latch.data.HistoryAction
import com.latch.data.HistoryEntry
import com.latch.data.HistoryStore
import com.latch.nfc.NfcController
import com.latch.nfc.Operation
import com.latch.ui.components.IconBadge
import com.latch.ui.components.ScreenHeader
import com.latch.ui.components.icon
import com.latch.ui.components.surfaceCardColors

@Composable
fun HistoryScreen(history: HistoryStore, nfc: NfcController) {
    val entries by history.entries.collectAsState()
    var selected by remember { mutableStateOf<HistoryEntry?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    if (entries.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            ScreenHeader("History", "Everything you've read and written")
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                IconBadge(Icons.Rounded.History, size = 72.dp)
                Spacer(Modifier.height(16.dp))
                Text("Nothing yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Tags you read and write show up here, so you can write them again later.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader("History", "Everything you've read and written", trailing = {
                TextButton(onClick = { confirmClear = true }) { Text("Clear") }
            })
        }
        items(entries, key = { it.id }) { e -> HistoryRow(e, onClick = { selected = e }) }
    }

    selected?.let { e ->
        val message = e.message()
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(e.summary, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            text = { Text(subtitle(e)) },
            confirmButton = {
                if (message != null) {
                    TextButton(onClick = {
                        selected = null
                        nfc.arm(Operation.Write(message, e.summary))
                    }) { Text("Write to a tag") }
                }
            },
            dismissButton = {
                TextButton(onClick = { history.remove(e.id); selected = null }) { Text("Delete") }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("This only clears Latch's list. Your tags aren't touched.") },
            confirmButton = { TextButton(onClick = { history.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryRow(e: HistoryEntry, onClick: () -> Unit) {
    Card(onClick = onClick, colors = surfaceCardColors()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(e.action.icon, size = 40.dp)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.summary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle(e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun subtitle(e: HistoryEntry): String {
    val verb = when (e.action) {
        HistoryAction.Read -> "Read"
        HistoryAction.Write -> "Wrote"
        HistoryAction.Erase -> "Erased"
        HistoryAction.Copy -> "Copied"
        HistoryAction.Lock -> "Locked"
    }
    val ago = DateUtils.getRelativeTimeSpanString(e.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    return listOfNotNull(verb, e.tagType, ago.toString()).joinToString(" · ")
}
