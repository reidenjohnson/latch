package com.latch.ui.read

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.latch.nfc.NfcController
import com.latch.nfc.Operation
import com.latch.nfc.ParsedRecord
import com.latch.nfc.RecordSpec
import com.latch.nfc.Records
import com.latch.nfc.SecretBox
import com.latch.nfc.TagSnapshot
import com.latch.ui.components.DetailRow
import com.latch.ui.components.IconBadge
import com.latch.ui.components.ProblemCard
import com.latch.ui.components.PulseRing
import com.latch.ui.components.ScreenHeader
import com.latch.ui.components.icon
import com.latch.ui.components.accent
import com.latch.ui.theme.Accent
import com.latch.ui.theme.extras
import com.latch.data.HistoryStore
import com.latch.ui.components.GradientPanel
import com.latch.ui.components.LatchCard
import android.text.format.DateUtils
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.ui.text.style.TextOverflow
import com.latch.ui.components.surfaceCardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReadScreen(
    nfc: NfcController,
    history: HistoryStore,
    onWrite: () -> Unit,
    onBlueprints: () -> Unit,
    onHistory: () -> Unit,
    onEdit: (List<RecordSpec>) -> Unit,
) {
    val snapshot by nfc.lastRead.collectAsState()
    val problem by nfc.readProblem.collectAsState()
    val context = LocalContext.current
    val s = snapshot

    if (s == null) {
        ReadHome(nfc, history, problem, onWrite, onBlueprints, onHistory)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("Read", "See what's on any tag", overline = "Scanned", accent = Accent.teal, trailing = {
                TextButton(onClick = nfc::clearRead) { Text("Clear", color = Accent.teal) }
            })
        }
        item { ReadyPill() }
        problem?.let { p -> item { ProblemCard(p) } }
        item { TagBanner(s) }
        when {
            !s.supported -> item {
                NoticeCard(
                    Icons.Rounded.Block, "Latch can't use this one",
                    "It looks like a bank, transit or access card. Latch works with NFC stickers, cards and key fobs.",
                )
            }
            s.isBlank -> item {
                NoticeCard(Icons.Rounded.CropFree, "This tag is blank", "There's nothing on it yet.") {
                    Button(onClick = onWrite) { Text("Write something") }
                }
            }
            else -> {
                itemsIndexed(s.records) { i, r -> RecordCard(r, emphasized = i == 0) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = { onEdit(Records.import(s.message!!, context)) },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Edit & rewrite")
                        }
                        OutlinedButton(
                            onClick = { nfc.arm(Operation.CopyTarget(s.message!!, s.summary, s.uid)) },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Copy to tag")
                        }
                    }
                }
            }
        }
        item { TechnicalCard(s) }
    }
}

/** The idle Read tab: a hero scan panel, quick actions, and recent tags. */
@Composable
private fun ReadHome(
    nfc: NfcController,
    history: HistoryStore,
    problem: com.latch.nfc.Problem?,
    onWrite: () -> Unit,
    onBlueprints: () -> Unit,
    onHistory: () -> Unit,
) {
    val entries by history.entries.collectAsState()
    val recent = entries.take(3)
    val x = extras
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Latch Tags", "Read, write and protect NFC tags", overline = "Ready", accent = Accent.teal) }
        problem?.let { p -> item { ProblemCard(p) } }
        item {
            GradientPanel(x.tealGradient, Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PulseRing(Icons.Rounded.Nfc, color = Color.White, onColor = x.tealGradient.last(), diameter = 180.dp)
                    Spacer(Modifier.height(18.dp))
                    Text("Ready to scan", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Hold a tag to the back of your phone. If nothing happens, slide it around slowly until you feel a buzz.",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f), textAlign = TextAlign.Center,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.Rounded.Edit, "Write", Accent.fern, Modifier.weight(1f), onWrite)
                QuickAction(Icons.Rounded.AutoAwesome, "Blueprints", Accent.amber, Modifier.weight(1f), onBlueprints)
                QuickAction(Icons.Rounded.DocumentScanner, "Scan many", Accent.teal, Modifier.weight(1f)) { nfc.arm(Operation.ReadMany()) }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onHistory) { Text("See all", color = Accent.teal) }
                }
            }
            items(recent, key = { it.id }) { e ->
                LatchCard {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(e.action.icon, size = 36.dp, tint = e.action.accent)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.summary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(e.action.name, e.tagType, DateUtils.getRelativeTimeSpanString(e.time).toString()).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    LatchCard(modifier, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon, tint = color, size = 40.dp)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ReadyPill() {
    val t = rememberInfiniteTransition(label = "ready")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(Accent.teal))
            Spacer(Modifier.size(10.dp))
            Text("Ready. Tap another tag anytime.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** One slim banner: chip name, memory used, and small attribute chips. The tag's content gets the big cards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagBanner(s: TagSnapshot) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Nfc, contentDescription = null, tint = Accent.teal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(s.chip ?: s.typeLabel, style = MaterialTheme.typography.titleSmall)
                s.capacity?.let { cap ->
                    Spacer(Modifier.weight(1f))
                    Text("${s.used} / $cap bytes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (s.writable) {
                    true -> StatusChip("Writable", Accent.fern)
                    false -> StatusChip("Locked", Accent.brick)
                    null -> if (s.supported) StatusChip("Not formatted", Accent.amber)
                }
                val n = s.ntag
                when (n?.genuine) {
                    true -> StatusChip("Genuine NXP", Accent.fern)
                    false -> StatusChip("Signature failed", Accent.brick)
                    null -> Unit
                }
                n?.config?.let { c ->
                    if (c.passwordProtected) StatusChip(if (c.readProtected) "Password: read+write" else "Password", Accent.amber)
                    if (c.mirrorEnabled) StatusChip("Live link", Accent.amber)
                }
                n?.counter?.let { StatusChip("$it scans", Accent.teal) }
                if (s.chip != null) StatusChip(s.typeLabel, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.capacity?.let { cap ->
                LinearProgressIndicator(
                    progress = { (s.used.toFloat() / cap).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f), contentColor = color) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun TechnicalCard(s: TagSnapshot) {
    var open by rememberSaveable { mutableStateOf(false) }
    LatchCard {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Technical details", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(open) {
                SelectionContainer {
                    Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
                        DetailRow("UID", s.uid)
                        DetailRow("Technologies", s.techs.joinToString(", "))
                        s.atqa?.let { DetailRow("ATQA", it) }
                        s.sak?.let { DetailRow("SAK", it) }
                        s.maxTransceive?.let { DetailRow("Max transceive", "$it bytes") }
                        s.capacity?.let { DetailRow("NDEF size", "${s.used} / $it bytes") }
                        s.ntag?.config?.let { c ->
                            DetailRow("AUTH0", "%02Xh".format(c.auth0))
                            DetailRow("ACCESS", "%02Xh".format(c.access))
                            DetailRow("Counter", if (c.counterEnabled) "On" else "Off")
                        }
                        s.records.forEachIndexed { i, r ->
                            r.raw?.let { raw ->
                                Spacer(Modifier.height(10.dp))
                                Text("Record ${i + 1}", style = MaterialTheme.typography.labelLarge)
                                DetailRow("TNF", raw.tnf.toString())
                                DetailRow("Type", raw.type)
                                DetailRow("Payload", "${raw.payloadSize} bytes")
                                Text(
                                    raw.payloadHex, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordCard(r: ParsedRecord, emphasized: Boolean = false) {
    val context = LocalContext.current
    var reveal by rememberSaveable(r.secret) { mutableStateOf(false) }
    var unlocking by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf<String?>(null) }
    LatchCard(emphasized = emphasized) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(r.kind.icon, tint = r.kind.accent)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = r.kind.accent)
                    SelectionContainer {
                        Text(unlocked ?: r.value, style = if (emphasized) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (r.details.isNotEmpty() || r.secret != null) {
                Spacer(Modifier.height(10.dp))
                r.details.forEach { (k, v) -> DetailRow(k, v) }
                r.secret?.let { secret ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DetailRow("Password", if (reveal) secret else "•".repeat(10), Modifier.weight(1f))
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(
                                if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (reveal) "Hide password" else "Show password",
                            )
                        }
                    }
                }
            }
            val canOpen = r.openUri != null || r.appPackage != null
            val copyText = unlocked ?: r.copyText
            if (canOpen || copyText != null || (r.sealed != null && unlocked == null)) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.sealed != null && unlocked == null) {
                        FilledTonalButton(onClick = { unlocking = true }) {
                            Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Unlock")
                        }
                    }
                    if (canOpen) {
                        FilledTonalButton(onClick = { open(context, r) }) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Open")
                        }
                    }
                    copyText?.let { text ->
                        AssistChip(onClick = { copy(context, text) }, label = { Text(if (r.secret != null) "Copy password" else "Copy") },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)) })
                        if (r.secret == null && r.sealed == null) {
                            AssistChip(onClick = { share(context, text) }, label = { Text("Share") },
                                leadingIcon = { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)) })
                        }
                    }
                }
            }
        }
    }
    if (unlocking && r.sealed != null) {
        UnlockDialog(r.sealed, onDismiss = { unlocking = false }, onUnlocked = { unlocked = it; unlocking = false })
    }
}

@Composable
private fun UnlockDialog(sealed: ByteArray, onDismiss: () -> Unit, onUnlocked: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlock note") },
        text = {
            Column {
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it; error = null }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), isError = error != null,
                    supportingText = error?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth(),
                )
                if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = pw.isNotEmpty() && !busy, onClick = {
                busy = true
                scope.launch {
                    // Key derivation is deliberately slow (600k PBKDF2 rounds), so it runs off the main thread.
                    val result = withContext(Dispatchers.Default) { runCatching { SecretBox.open(sealed, pw) } }
                    busy = false
                    result.fold(onUnlocked) { error = if (it is SecretBox.WrongPassword) "Wrong password" else "This note is damaged" }
                }
            }) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NoticeCard(icon: ImageVector, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    LatchCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon, size = 56.dp)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            action?.let { Spacer(Modifier.height(14.dp)); it() }
        }
    }
}

private fun open(context: Context, r: ParsedRecord) {
    val intent = r.appPackage?.let { pkg ->
        context.packageManager.getLaunchIntentForPackage(pkg)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
    } ?: Intent(Intent.ACTION_VIEW, r.openUri)
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone can open that.", Toast.LENGTH_SHORT).show()
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Latch", text))
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
