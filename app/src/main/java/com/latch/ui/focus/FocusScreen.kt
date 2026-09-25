package com.latch.ui.focus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.latch.focus.Essentials
import com.latch.focus.FocusBlocker
import com.latch.focus.FocusSilencer
import androidx.compose.material3.Switch
import androidx.compose.material.icons.rounded.NotificationsOff
import com.latch.focus.FocusManager
import com.latch.focus.FocusMode
import com.latch.focus.FocusSession
import com.latch.focus.FocusState
import com.latch.focus.FocusStats
import com.latch.focus.FocusTag
import com.latch.nfc.NfcController
import com.latch.nfc.Operation
import com.latch.ui.components.AppInfo
import com.latch.ui.components.GradientPanel
import com.latch.ui.components.IconBadge
import com.latch.ui.components.LatchCard
import com.latch.ui.components.ScreenHeader
import com.latch.ui.components.loadApps
import com.latch.ui.theme.Accent
import com.latch.ui.theme.extras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

@Composable
fun FocusScreen(focus: FocusManager, nfc: NfcController) {
    val context = LocalContext.current
    val state by focus.state.collectAsState()
    val blockerOn by FocusBlocker.running.collectAsState()
    val active = state.active
    var picking by rememberSaveable { mutableStateOf(false) }
    var disclosure by rememberSaveable { mutableStateOf(false) }
    val apps by rememberApps()
    val notificationsAllowed = rememberOnResume { notificationsAllowed(context) }
    val silenceAllowed = rememberOnResume { FocusSilencer.allowed(context) }
    var silenceDisclosure by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Focus", "Tap your tag to lock away distractions", overline = "Focus", accent = Accent.fern) }

        val ready = state.tags.isNotEmpty() && state.hasApps && blockerOn
        item {
            when {
                active != null -> ActiveHero(state)
                ready -> ReadyHero(state, apps)
                else -> SetupCard(
                    state, blockerOn,
                    onPair = { nfc.arm(Operation.PairFocus()) },
                    onApps = { picking = true },
                    onBlocker = { disclosure = true },
                )
            }
        }
        if (active != null) {
            item {
                HoldToUnlock(
                    color = Accent.amber, track = Accent.amber.copy(alpha = 0.14f), onColor = Color.White,
                    onUnlock = { focus.emergencyUnlock() },
                )
            }
        }
        if (ready && !notificationsAllowed.value && Build.VERSION.SDK_INT >= 33) {
            item { NotificationsCard(onAllowed = { notificationsAllowed.value = true }) }
        }
        if (state.tags.isNotEmpty() && !blockerOn) {
            item { BlockerOffCard { disclosure = true } }
        }

        item {
            AppsCard(
                state, apps, locked = active != null, onEdit = { picking = true }, onMode = focus::setMode,
                silenceAllowed = silenceAllowed.value,
                onSilence = { on ->
                    focus.setSilence(on)
                    if (on && !silenceAllowed.value) silenceDisclosure = true
                },
            )
        }
        item {
            TagsCard(
                state, locked = active != null,
                onPair = { nfc.arm(Operation.PairFocus()) }, onUnpair = focus::unpair, onRename = focus::rename,
            )
        }
        if (state.sessions.isNotEmpty() || active != null) {
            item { StatsCard(state) }
            items(state.sessions.take(10), key = { it.start }) { SessionRow(it) }
        }
    }

    if (picking) {
        AppsPicker(
            state, apps,
            onDismiss = { picking = false },
            onSave = { focus.setApps(it); picking = false },
        )
    }
    if (silenceDisclosure) {
        AlertDialog(
            onDismissRequest = { silenceDisclosure = false },
            icon = { Icon(Icons.Rounded.NotificationsOff, contentDescription = null) },
            title = { Text("Hide notifications from blocked apps") },
            text = {
                Text(
                    "During Focus, Latch clears new notifications from the apps it blocks, so they can't pull you back in. " +
                        "Calls, alarms and media controls are never touched. The messages are still in the app afterwards.\n\n" +
                        "Android calls this \"notification access\". Latch only checks which app a notification is from, " +
                        "and nothing leaves your phone. On the next screen, turn on Latch.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    silenceDisclosure = false
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { silenceDisclosure = false }) { Text("Not now") } },
        )
    }
    if (disclosure) {
        AlertDialog(
            onDismissRequest = { disclosure = false },
            icon = { Icon(Icons.Rounded.Shield, contentDescription = null) },
            title = { Text("Turn on app blocking") },
            text = {
                Text(
                    "Latch uses Android's Accessibility setting only to see which app just opened, so it can cover the apps " +
                        "you chose while Focus is on.\n\nIt never reads what's on your screen or what you type, and nothing " +
                        "leaves your phone.\n\nOn the next screen, open Latch (it may be under \"Installed apps\") and turn it on.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    disclosure = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Not now") } },
        )
    }
}

// ---------------------------------------------------------------- Heroes

@Composable
private fun ActiveHero(state: FocusState) {
    val active = state.active ?: return
    val now = rememberNow()
    GradientPanel(extras.fernGradient, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            HeroOverline("In Focus")
            Text(FocusStats.clock(now - active.start), style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(
                "Since ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(active.start))} · ${active.blocked.size} apps blocked",
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f),
            )
            Spacer(Modifier.height(18.dp))
            TapHint("Tap your tag to end Focus")
        }
    }
}

@Composable
private fun ReadyHero(state: FocusState, apps: List<AppInfo>?) {
    GradientPanel(extras.tealGradient, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            HeroOverline("Ready")
            Text("Tap your tag to focus", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(
                when (state.mode) {
                    FocusMode.Block -> "${state.apps.size} app${if (state.apps.size == 1) "" else "s"} will be blocked until you tap it again."
                    FocusMode.AllowOnly -> "Everything except ${state.apps.size} app${if (state.apps.size == 1) "" else "s"} (plus calls and Settings) will be blocked."
                },
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f),
            )
            val icons = apps?.filter { it.pkg in state.apps }.orEmpty().take(8)
            if (icons.isNotEmpty() && state.mode == FocusMode.Block) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    icons.forEach { a -> a.icon?.let { Image(it, contentDescription = a.label, modifier = Modifier.size(28.dp)) } }
                }
            }
            Spacer(Modifier.height(18.dp))
            TapHint("Works with Latch closed, too")
        }
    }
}

@Composable
private fun HeroOverline(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun TapHint(text: String) {
    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.16f)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Nfc, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

// ---------------------------------------------------------------- Setup

@Composable
private fun SetupCard(state: FocusState, blockerOn: Boolean, onPair: () -> Unit, onApps: () -> Unit, onBlocker: () -> Unit) {
    LatchCard(emphasized = true) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Set up Focus", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pick the apps that pull you in, then lock them behind a tag you keep somewhere else. " +
                    "To get them back, you have to walk over and tap it.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SetupStep(1, "Pair a tag", "Any NFC sticker or card works. It's rewritten as your Focus tag.", state.tags.isNotEmpty(), "Pair", onPair)
            SetupStep(2, "Choose apps to block", "Social media, games, anything that eats your time.", state.hasApps, "Choose", onApps)
            SetupStep(3, "Turn on blocking", "One Android setting lets Latch cover blocked apps.", blockerOn, "Turn on", onBlocker)
        }
    }
}

@Composable
private fun SetupStep(n: Int, title: String, body: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, contentDescription = null,
            tint = if (done) Accent.fern else MaterialTheme.colorScheme.outline, modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$n. $title", style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!done) {
            Spacer(Modifier.size(8.dp))
            FilledTonalButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun BlockerOffCard(onTurnOn: () -> Unit) {
    LatchCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Shield, tint = Accent.amber)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Blocking is off", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Focus can start, but apps won't be covered until it's on.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onTurnOn) { Text("Turn on") }
        }
    }
}

@Composable
private fun NotificationsCard(onAllowed: () -> Unit) {
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) onAllowed() }
    LatchCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Notifications, tint = Accent.teal)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Show a Focus timer", style = MaterialTheme.typography.titleMedium)
                Text("A quiet notification while Focus is on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow") }
        }
    }
}

// ---------------------------------------------------------------- Settings cards

@Composable
private fun AppsCard(
    state: FocusState, apps: List<AppInfo>?, locked: Boolean, onEdit: () -> Unit, onMode: (FocusMode) -> Unit,
    silenceAllowed: Boolean, onSilence: (Boolean) -> Unit,
) {
    LatchCard {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Apps, tint = Accent.teal, size = 40.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Apps", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (locked) "Locked while Focus is on" else when (state.mode) {
                            FocusMode.Block -> if (state.apps.isEmpty()) "None chosen yet" else "${state.apps.size} blocked during Focus"
                            FocusMode.AllowOnly -> "${state.apps.size} allowed, everything else blocked"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onEdit, enabled = !locked) { Text(if (state.apps.isEmpty()) "Choose" else "Edit") }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.alpha(if (locked) 0.5f else 1f)) {
                ModeChip("Block these", state.mode == FocusMode.Block, !locked) { onMode(FocusMode.Block) }
                ModeChip("Allow only these", state.mode == FocusMode.AllowOnly, !locked) { onMode(FocusMode.AllowOnly) }
            }
            val chosen = apps?.filter { it.pkg in state.apps }.orEmpty()
            if (chosen.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    chosen.take(9).forEach { a -> a.icon?.let { Image(it, contentDescription = a.label, modifier = Modifier.size(30.dp)) } }
                    if (chosen.size > 9) Text("+${chosen.size - 9}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            val needsAccess = state.silence && !silenceAllowed
            Row(Modifier.fillMaxWidth().alpha(if (locked) 0.5f else 1f), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hide their notifications", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (needsAccess) "Needs notification access. Tap the switch to set it up."
                        else "Clears new ones during Focus. Calls and alarms still come through.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (needsAccess) Accent.amber else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Switch(
                    checked = state.silence && silenceAllowed, enabled = !locked,
                    onCheckedChange = { on -> onSilence(if (needsAccess) true else on) },
                )
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, enabled = enabled, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Composable
private fun TagsCard(state: FocusState, locked: Boolean, onPair: () -> Unit, onUnpair: (String) -> Unit, onRename: (String, String) -> Unit) {
    var renaming by remember { mutableStateOf<FocusTag?>(null) }
    var unpairing by remember { mutableStateOf<FocusTag?>(null) }
    LatchCard {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Nfc, tint = Accent.fern, size = 40.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Focus tags", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (locked) "Locked while Focus is on" else if (state.tags.isEmpty()) "No tag paired yet" else "Any of these starts and ends Focus",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onPair, enabled = !locked) { Text(if (state.tags.isEmpty()) "Pair" else "Add") }
            }
            state.tags.forEach { t ->
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(52.dp, 1.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Paired ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t.pairedAt))} · ${t.uid}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !locked) { Icon(Icons.Rounded.MoreVert, contentDescription = "Options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = t })
                            DropdownMenuItem(text = { Text("Unpair") }, onClick = { menu = false; unpairing = t })
                        }
                    }
                }
            }
        }
    }
    renaming?.let { t ->
        var name by remember(t) { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename tag") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Desk, Kitchen, Car…") }) },
            confirmButton = { Button(onClick = { onRename(t.uid, name); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    unpairing?.let { t ->
        AlertDialog(
            onDismissRequest = { unpairing = null },
            title = { Text("Unpair ${t.name}?") },
            text = { Text("It won't start or end Focus anymore. The tag itself isn't changed, so you can pair it again later.") },
            confirmButton = { Button(onClick = { onUnpair(t.uid); unpairing = null }) { Text("Unpair") } },
            dismissButton = { TextButton(onClick = { unpairing = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- Stats

@Composable
private fun StatsCard(state: FocusState) {
    val now = rememberNow()
    LatchCard {
        Column(Modifier.padding(18.dp)) {
            Text("Time in Focus", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Today", FocusStats.format(FocusStats.total(state, FocusStats.startOfToday(now), now)), Accent.fern, Modifier.weight(1f))
                Stat("This week", FocusStats.format(FocusStats.total(state, FocusStats.startOfWeek(now), now)), Accent.teal, Modifier.weight(1f))
                Stat("Longest", FocusStats.format(FocusStats.longest(state)), Accent.amber, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SessionRow(s: FocusSession) {
    val day = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(s.start))
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.SelfImprovement, contentDescription = null, tint = if (s.emergency) Accent.amber else Accent.fern, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$day · ${time.format(Date(s.start))} – ${time.format(Date(s.end))}", style = MaterialTheme.typography.bodyMedium)
            if (s.emergency) Text("Ended with emergency unlock", style = MaterialTheme.typography.bodySmall, color = Accent.amber)
            if (s.hidden > 0) {
                Text(
                    "${s.hidden} notification${if (s.hidden == 1) "" else "s"} hidden",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(FocusStats.format(s.length), style = MaterialTheme.typography.titleSmall)
    }
}

// ---------------------------------------------------------------- App picker

@Composable
private fun AppsPicker(state: FocusState, apps: List<AppInfo>?, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    val context = LocalContext.current
    // First time in block mode: start with likely time sinks already checked, so setup is usually just "Save".
    val firstPick = state.apps.isEmpty() && state.mode == FocusMode.Block
    var chosen by remember { mutableStateOf(state.apps) }
    var prefilled by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var essentials by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) { essentials = withContext(Dispatchers.IO) { Essentials.of(context) } }
    LaunchedEffect(apps, essentials) {
        if (firstPick && !prefilled && apps != null && essentials.isNotEmpty()) {
            chosen = apps.filter { it.suggested && it.pkg !in essentials }.map { it.pkg }.toSet()
            prefilled = true
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(if (state.mode == FocusMode.Block) "Apps to block" else "Apps to allow", style = MaterialTheme.typography.headlineSmall)
                        Text("${chosen.size} selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { onSave(chosen - essentials) }) { Text("Save") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") } },
                    placeholder = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (apps == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
                } else {
                    val matches = apps.filter { query.isBlank() || it.label.contains(query, true) }
                    // Block mode: likely time sinks up top, then the full list. Allow-only mode: just the full list.
                    val suggested = if (state.mode == FocusMode.Block) matches.filter { it.suggested && it.pkg !in essentials } else emptyList()
                    val rest = matches - suggested.toSet()
                    val toggle: (AppInfo) -> Unit = { app -> chosen = if (app.pkg in chosen) chosen - app.pkg else chosen + app.pkg }
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        if (suggested.isNotEmpty()) {
                            item(key = "h-suggested") { PickerHeader("Suggested", "Social, video and game apps on your phone") }
                            items(suggested, key = { "s-" + it.pkg }) { app -> PickerRow(app, app.pkg in chosen, false, toggle) }
                            item(key = "h-all") { PickerHeader("All apps", null) }
                        }
                        items(rest, key = { it.pkg }) { app ->
                            val always = app.pkg in essentials
                            PickerRow(app, app.pkg in chosen && !always, always, toggle)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerHeader(title: String, subtitle: String?) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp, bottom = 6.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Accent.teal)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun PickerRow(app: AppInfo, checked: Boolean, always: Boolean, onToggle: (AppInfo) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = !always) { onToggle(app) }
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .alpha(if (always) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium)
            if (always) Text("Always allowed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, onCheckedChange = null, enabled = !always)
    }
}

// ---------------------------------------------------------------- Helpers

@Composable
private fun rememberApps(): androidx.compose.runtime.State<List<AppInfo>?> {
    val context = LocalContext.current
    val apps = remember { mutableStateOf<List<AppInfo>?>(null) }
    LaunchedEffect(Unit) { apps.value = withContext(Dispatchers.IO) { loadApps(context) } }
    return apps
}

private fun notificationsAllowed(context: android.content.Context) = Build.VERSION.SDK_INT < 33 ||
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** A permission-style check, re-run on resume since the user can change it in system settings. */
@Composable
private fun rememberOnResume(check: () -> Boolean): androidx.compose.runtime.MutableState<Boolean> {
    val value = remember { mutableStateOf(check()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) value.value = check() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return value
}
