package com.latch.ui.read

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.latch.nfc.NfcController
import com.latch.nfc.Operation
import com.latch.nfc.ParsedRecord
import com.latch.nfc.TagSnapshot
import com.latch.ui.components.DetailRow
import com.latch.ui.components.IconBadge
import com.latch.ui.components.ProblemCard
import com.latch.ui.components.PulseRing
import com.latch.ui.components.ScreenHeader
import com.latch.ui.components.icon
import com.latch.ui.components.surfaceCardColors

@Composable
fun ReadScreen(nfc: NfcController, onWrite: () -> Unit) {
    val snapshot by nfc.lastRead.collectAsState()
    val problem by nfc.readProblem.collectAsState()
    val s = snapshot

    if (s == null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            ScreenHeader("Read", "See what's on any tag")
            problem?.let { ProblemCard(it, Modifier.padding(top = 8.dp)) }
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PulseRing(Icons.Rounded.Nfc)
                Spacer(Modifier.height(28.dp))
                Text("Hold a tag to your phone", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Touch it to the back of your phone. If nothing happens, slide it around slowly until you feel a buzz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("Read", "See what's on any tag", trailing = {
                TextButton(onClick = nfc::clearRead) { Text("Clear") }
            })
        }
        item { ReadyPill() }
        problem?.let { p -> item { ProblemCard(p) } }
        item { TagInfoCard(s) }
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
                items(s.records) { RecordCard(it) }
                item {
                    OutlinedButton(
                        onClick = { nfc.arm(Operation.CopyTarget(s.message!!, s.summary, s.uid)) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Copy to another tag")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyPill() {
    val t = rememberInfiniteTransition(label = "ready")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.size(10.dp))
            Text(
                "Ready. Tap another tag anytime.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun TagInfoCard(s: TagSnapshot) {
    Card(colors = surfaceCardColors()) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.chip ?: s.typeLabel, style = MaterialTheme.typography.titleLarge)
                    if (s.chip != null) {
                        Text(s.typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when (s.writable) {
                    true -> StatusChip("Writable", MaterialTheme.colorScheme.tertiary)
                    false -> StatusChip("Locked", MaterialTheme.colorScheme.error)
                    null -> if (s.supported) StatusChip("Not formatted", MaterialTheme.colorScheme.primary)
                }
            }
            s.capacity?.let { cap ->
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { (s.used.toFloat() / cap).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${s.used} of $cap bytes used", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            SelectionContainer {
                Text(
                    "ID  ${s.uid}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.15f), contentColor = color) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
private fun RecordCard(r: ParsedRecord) {
    val context = LocalContext.current
    var reveal by rememberSaveable(r.secret) { mutableStateOf(false) }
    Card(colors = surfaceCardColors()) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(r.kind.icon)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    SelectionContainer { Text(r.value, style = MaterialTheme.typography.titleMedium) }
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
            if (canOpen || r.copyText != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canOpen) {
                        FilledTonalButton(onClick = { open(context, r) }) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Open")
                        }
                    }
                    r.copyText?.let { text ->
                        AssistChip(onClick = { copy(context, text) }, label = { Text(if (r.secret != null) "Copy password" else "Copy") },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)) })
                        if (r.secret == null) {
                            AssistChip(onClick = { share(context, text) }, label = { Text("Share") },
                                leadingIcon = { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    Card(colors = surfaceCardColors()) {
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
