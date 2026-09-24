package com.latch.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.latch.nfc.NfcController
import com.latch.nfc.Operation
import com.latch.nfc.PasswordKey
import com.latch.nfc.RawTech
import com.latch.nfc.Records
import com.latch.ui.components.IconBadge
import com.latch.ui.components.ScreenHeader
import com.latch.ui.components.surfaceCardColors

private enum class Dialog { Lock, SetPassword, RemovePassword, Counter, Raw }

@Composable
fun ToolsScreen(nfc: NfcController) {
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader("Tools", "Everything, free. No subscriptions.") }
        item { Section("Everyday") }
        item { ToolCard(Icons.Rounded.ContentCopy, "Copy a tag", "Tap the original, then a new tag. Everything is copied over.") { nfc.arm(Operation.CopySource) } }
        item { ToolCard(Icons.Rounded.DocumentScanner, "Scan many", "Tap tag after tag, then export the list as a spreadsheet.") { nfc.arm(Operation.ReadMany()) } }
        item { ToolCard(Icons.Rounded.DeleteSweep, "Erase a tag", "Wipe it clean so you can reuse it.") { nfc.arm(Operation.Erase()) } }

        item { Section("Protect") }
        item { ToolCard(Icons.Rounded.Password, "Password-protect", "Anyone can still read it, but only you can change or erase it.") { dialog = Dialog.SetPassword } }
        item { ToolCard(Icons.Rounded.LockOpen, "Remove a password", "Open a protected tag back up.") { dialog = Dialog.RemovePassword } }
        item {
            ToolCard(Icons.Rounded.Lock, "Lock forever", "Make it permanently read-only. Can never be undone.", tint = MaterialTheme.colorScheme.error) {
                dialog = Dialog.Lock
            }
        }

        item { Section("Advanced") }
        item { ToolCard(Icons.Rounded.Insights, "Scan counter", "Have the chip count how many times it's been read.") { dialog = Dialog.Counter } }
        item { ToolCard(Icons.Rounded.Memory, "Read memory", "See every page of the chip in hex, then share or save it.") { nfc.arm(Operation.Dump) } }
        item { ToolCard(Icons.Rounded.Terminal, "Send commands", "Talk to the chip directly with raw hex commands.") { dialog = Dialog.Raw } }
        item { TipsCard() }
    }

    when (dialog) {
        Dialog.Lock -> LockDialog(onDismiss = { dialog = null }) { dialog = null; nfc.arm(Operation.MakeReadOnly) }
        Dialog.SetPassword -> SetPasswordDialog(onDismiss = { dialog = null }) { dialog = null; nfc.arm(Operation.SetPassword(it)) }
        Dialog.RemovePassword -> PasswordDialog(
            title = "Remove password", body = "Enter the tag's current password, then tap the tag.", confirm = "Continue",
            onDismiss = { dialog = null },
        ) { dialog = null; nfc.arm(Operation.RemovePassword(it)) }
        Dialog.Counter -> CounterDialog(onDismiss = { dialog = null }) { enable -> dialog = null; nfc.arm(Operation.Counter(enable)) }
        Dialog.Raw -> RawDialog(onDismiss = { dialog = null }) { tech, cmds -> dialog = null; nfc.arm(Operation.Raw(tech, cmds)) }
        null -> Unit
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
}

@Composable
private fun ToolCard(icon: ImageVector, title: String, body: String, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Card(onClick = onClick, colors = surfaceCardColors()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Card(colors = surfaceCardColors(), modifier = Modifier.padding(top = 8.dp)) {
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
            ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                Text("Want it reversible? Use a password instead.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().clickable { understood = !understood }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = understood, onCheckedChange = { understood = it })
                    Text("I understand this can't be undone")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = understood, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Lock next tag")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SetPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val mismatch = again.isNotEmpty() && pw != again
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Password, contentDescription = null) },
        title = { Text("Password-protect a tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Anyone can still read the tag. Changing or erasing it will need this password. Works on NTAG213/215/216.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = pw, onValueChange = { pw = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = again, onValueChange = { again = it }, label = { Text("Again") }, singleLine = true,
                    isError = mismatch, supportingText = if (mismatch) ({ Text("Doesn't match") }) else null,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Text(
                    if (PasswordKey.isRawHex(pw)) "8 hex digits are used as-is, so other NFC apps can unlock it with the same code."
                    else "Tip: an 8-digit hex code (like 1A2B3C4D) also works in other NFC apps. Anything else only unlocks in Latch.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Wrong guesses never lock the tag, and a forgotten password only stops changes. Reading still works.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = pw.length >= 4 && pw == again, onClick = { onConfirm(pw) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body)
                OutlinedTextField(value = pw, onValueChange = { pw = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = pw.isNotEmpty(), onClick = { onConfirm(pw) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CounterDialog(onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Insights, contentDescription = null) },
        title = { Text("Scan counter") },
        text = {
            Text("NTAG213/215/216 chips have a built-in counter that goes up by one each time a phone reads the tag. " +
                "Turn it on, then scan the tag on the Read tab to see the count. It can't be reset, and it stops at 16,777,215.")
        },
        confirmButton = { TextButton(onClick = { onConfirm(true) }) { Text("Turn on") } },
        dismissButton = { TextButton(onClick = { onConfirm(false) }) { Text("Turn off") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RawDialog(onDismiss: () -> Unit, onConfirm: (RawTech, List<ByteArray>) -> Unit) {
    var tech by remember { mutableStateOf(RawTech.NfcA) }
    var text by remember { mutableStateOf("60") }
    val cmds = text.lines().filter { it.isNotBlank() }.map { Records.hexOrNull(it) }
    val valid = cmds.isNotEmpty() && cmds.all { it != null && it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
        title = { Text("Send commands") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RawTech.entries.forEach { t -> FilterChip(selected = tech == t, onClick = { tech = t }, label = { Text(t.name) }) }
                }
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, label = { Text("One hex command per line") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    minLines = 4, isError = !valid, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Example for NTAG: 60 = GET_VERSION, 30 04 = read pages 4-7. Advanced: a wrong WRITE or lock command " +
                        "can permanently lock a tag. It can't affect your phone.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(tech, cmds.map { it!! }) }) { Text("Send on next tap") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
