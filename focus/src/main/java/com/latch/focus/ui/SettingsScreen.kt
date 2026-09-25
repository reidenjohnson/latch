package com.latch.focus.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.HealthAndSafety
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.latch.focus.block.Blocker
import com.latch.focus.block.Silencer
import com.latch.focus.data.AppState
import com.latch.focus.data.LatchTag
import com.latch.focus.data.ListType
import com.latch.focus.data.Mode
import com.latch.focus.engine.Alarms
import com.latch.focus.engine.Prefs
import com.latch.focus.engine.ThemeMode
import com.latch.focus.data.Rules
import com.latch.focus.latch
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.latch.focus.ui.theme.palette
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    state: AppState,
    onBack: () -> Unit,
    onEditMode: (String?) -> Unit,
    onPair: () -> Unit,
    onRenameTag: (String, String) -> Unit,
    onUnpair: (String) -> Unit,
    onTurnOnBlocking: () -> Unit,
) {
    val p = palette
    val context = LocalContext.current
    val locked = state.active != null
    val blockerOn by Blocker.running.collectAsState()
    val silenceOn by rememberOnResume { Silencer.allowed(context) }
    val notifOn = rememberOnResume {
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOn.value = it }
    var tagMenu by remember { mutableStateOf<LatchTag?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val theme by Prefs.theme.collectAsState()
    val haptics by Prefs.haptics.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = p.text) }
            LargeTitle("Settings", subtitle = if (locked) "Most settings are locked while you're latched." else null)
        }
        item {
            Section("Modes", "Each mode is its own list of apps, with its own color and schedules.") {
                state.modes.forEachIndexed { i, m ->
                    if (i > 0) RowDivider()
                    ListRow(m.name, icon = Icons.Rounded.Apps, tint = p.hue(m.hue), subtitle = modeLine(m), chevron = true, enabled = !locked) { onEditMode(m.id) }
                }
                RowDivider()
                ListRow("New mode", icon = Icons.Rounded.Add, tint = p.faint, titleColor = p.teal, enabled = !locked && state.modes.size < 10) { onEditMode(null) }
            }
        }
        item {
            Section("Your Latches", "Any paired tag latches and unlatches this phone. The same tag can be paired on two phones.") {
                state.tags.forEachIndexed { i, t ->
                    if (i > 0) RowDivider()
                    ListRow(
                        t.name, icon = LatchGlyph, tint = p.teal,
                        subtitle = "Paired ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t.pairedAt))}",
                        chevron = true, enabled = !locked,
                    ) { tagMenu = t }
                }
                if (state.tags.isNotEmpty()) RowDivider()
                ListRow("Pair a Latch", icon = Icons.Rounded.Add, tint = p.faint, titleColor = p.teal, enabled = !locked, onClick = onPair)
            }
        }
        item {
            Section("Appearance") {
                Box(Modifier.padding(12.dp)) {
                    Pills(ThemeMode.entries.map { it to it.label }, theme, p.teal) { Prefs.setTheme(context, it) }
                }
                RowDivider(16.dp)
                ListRow(
                    "Haptics", icon = Icons.Rounded.Vibration, tint = p.teal, subtitle = "A buzz when you latch, unlatch or pair.",
                    trailing = { Switch(haptics, { Prefs.setHaptics(context, it) }, colors = SwitchDefaults.colors(checkedTrackColor = p.teal)) },
                )
            }
        }
        item {
            Section("Permissions", "Only locking apps is required. Turning a permission off again is done in Android Settings.") {
                PermissionRow(Icons.Rounded.Shield, p.fern, "Lock apps", "Required. Sees which app opened, never what's on screen.", blockerOn) {
                    com.latch.focus.block.Grants.openBlocking(context)
                }
                RowDivider()
                PermissionRow(Icons.Rounded.NotificationsOff, p.teal, "Hide notifications", "For modes that hold back notifications from locked apps.", silenceOn) {
                    com.latch.focus.block.Grants.openNotifications(context)
                }
                RowDivider()
                PermissionRow(Icons.Rounded.Notifications, p.amber, "Latched notification", "A quiet timer while you're latched.", notifOn.value) {
                    if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        item {
            Section(
                "Safety",
                "Phone, Settings, your home screen, keyboards and emergency apps are never blocked, whatever a mode says. " +
                    "Uninstalling Latch removes every restriction.",
            ) {
                ListRow(
                    "Emergency unlock", icon = Icons.Rounded.HealthAndSafety, tint = p.brick,
                    subtitle = "Hold for $EMERGENCY_SECONDS seconds to unlatch without your tag.",
                    value = "${state.emergencyUnlocks} used",
                )
            }
        }
        item {
            Section("Data", if (locked) "Unlatch first to clear or reset." else "Everything lives on this phone only.") {
                ListRow("Clear activity history", icon = Icons.Rounded.DeleteSweep, tint = p.amber, enabled = !locked && state.sessions.isNotEmpty()) {
                    confirm = Confirm.ClearHistory
                }
                RowDivider()
                ListRow(
                    "Reset Latch", icon = Icons.Rounded.RestartAlt, tint = p.brick, titleColor = p.brick,
                    subtitle = "Forget modes, paired tags and history, and start setup over.", enabled = !locked,
                ) { confirm = Confirm.Reset }
            }
        }
        item {
            Section("Developer") {
                ListRow("Version", value = versionName(context))
                RowDivider(16.dp)
                ListRow("Replay setup", subtitle = "Show the first-run screens again. Keeps your data.", chevron = true, enabled = !locked) {
                    context.latch.replayOnboarding()
                }
                RowDivider(16.dp)
                ListRow("Blocking service", value = if (blockerOn) "Connected" else "Not running")
                RowDivider(16.dp)
                ListRow("Next wake-up", value = nextWake(state))
                RowDivider(16.dp)
                ListRow("App info", subtitle = "Permissions, storage, restricted settings.", chevron = true) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
            }
        }
        item {
            Section("About") {
                ListRow("Latch", subtitle = "Free and open source. No account, no tracking, nothing leaves your phone.")
            }
        }
    }

    when (confirm) {
        Confirm.ClearHistory -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Clear activity history?") },
            text = { Text("Your past sessions and stats are deleted. Modes and tags stay.") },
            confirmButton = { TextButton(onClick = { context.latch.clearHistory(); confirm = null }) { Text("Clear", color = p.brick) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
        Confirm.Reset -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Reset Latch?") },
            text = {
                Text(
                    "This forgets every mode, paired tag and session on this phone, and takes you back to setup. " +
                        "Your tags aren't changed, so you can pair them again. This can't be undone.",
                )
            },
            confirmButton = { TextButton(onClick = { context.latch.resetAll(); confirm = null }) { Text("Reset", color = p.brick) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
        null -> Unit
    }

    tagMenu?.let { t ->
        var name by remember(t) { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { tagMenu = null },
            title = { Text("Your Latch") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { onRenameTag(t.uid, name); tagMenu = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { onUnpair(t.uid); tagMenu = null }) { Text("Unpair", color = p.brick) } },
        )
    }
}

private enum class Confirm { ClearHistory, Reset }

private fun versionName(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

private fun nextWake(s: AppState): String =
    Rules.nextWake(s, System.currentTimeMillis())?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
    } ?: "None"

fun modeLine(m: Mode): String {
    val apps = when {
        m.type == ListType.Block -> if (m.apps.isEmpty()) "No apps yet" else "Blocks ${m.apps.size} app${if (m.apps.size == 1) "" else "s"}"
        m.apps.isEmpty() -> "Blocks everything but essentials"
        else -> "Allows only ${m.apps.size} app${if (m.apps.size == 1) "" else "s"}"
    }
    val on = m.schedules.count { it.enabled }
    return apps + if (on > 0) " · $on schedule${if (on == 1) "" else "s"}" else ""
}
