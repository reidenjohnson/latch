package com.latch.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.latch.nfc.NfcController
import com.latch.nfc.Operation
import com.latch.ui.components.IconBadge
import com.latch.ui.components.ScreenHeader
import com.latch.ui.components.surfaceCardColors

@Composable
fun ToolsScreen(nfc: NfcController) {
    var confirmLock by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Tools", "Copy, wipe or lock tags") }
        item {
            ToolCard(Icons.Rounded.ContentCopy, "Copy a tag", "Tap the original, then tap a new tag. Everything is copied over.") {
                nfc.arm(Operation.CopySource)
            }
        }
        item {
            ToolCard(Icons.Rounded.DeleteSweep, "Erase a tag", "Wipe it clean so you can reuse it.") {
                nfc.arm(Operation.Erase)
            }
        }
        item {
            ToolCard(
                Icons.Rounded.Lock, "Lock a tag", "Make it read-only forever. It can never be changed again.",
                tint = MaterialTheme.colorScheme.error,
            ) { confirmLock = true }
        }
        item { TipsCard() }
    }

    if (confirmLock) LockDialog(onDismiss = { confirmLock = false }, onConfirm = {
        confirmLock = false
        nfc.arm(Operation.MakeReadOnly)
    })
}

@Composable
private fun ToolCard(icon: ImageVector, title: String, body: String, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Card(onClick = onClick, colors = surfaceCardColors()) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint = tint)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TipsCard() {
    Card(colors = surfaceCardColors()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(10.dp))
                Text("For reliable taps", style = MaterialTheme.typography.titleMedium)
            }
            listOf(
                "Hold still until you feel the buzz. Pulling away mid-write can leave a tag half-written. Just tap again.",
                "Thick cases and metal get in the way. On metal surfaces, use \"on-metal\" (anti-metal) tags.",
                "NTAG213, NTAG215 and NTAG216 are the most common NFC stickers. They hold 144, 496 and 872 bytes of data.",
                "Every write is read back and checked, so \"verified\" means the tag really holds what you wrote.",
            ).forEach {
                Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LockDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var understood by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Lock a tag for good?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Locking is permanent. No app, including Latch, can ever change or erase the tag again. It can still be read.")
                Row(
                    Modifier.fillMaxWidth().clickable { understood = !understood },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = understood, onCheckedChange = { understood = it })
                    Text("I understand this can't be undone")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm, enabled = understood,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Lock next tag") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
