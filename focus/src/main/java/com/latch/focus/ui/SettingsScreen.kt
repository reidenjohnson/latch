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
import androidx.compose.material.icons.rounded.Nfc
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
    val exactOn by rememberOnResume { Alarms.exactAllowed(context) }
    val notifOn = rememberOnResume {
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOn.value = it }
    var tagMenu by remember { mutableStateOf<LatchTag?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = p.text) }
            LargeTitle("Settings", subtitle = if (locked) "Most settings are locked while a session runs." else null)
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
            Section("Your Latches", "Any paired tag starts and ends sessions. Pairing the same tag on two phones is fine.") {
                state.tags.forEachIndexed { i, t ->
                    if (i > 0) RowDivider()
                    ListRow(
                        t.name, icon = Icons.Rounded.Nfc, tint = p.teal,
                        subtitle = "Paired ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t.pairedAt))}",
                        chevron = true, enabled = !locked,
                    ) { tagMenu = t }
                }
                if (state.tags.isNotEmpty()) RowDivider()
                ListRow("Pair a Latch", icon = Icons.Rounded.Add, tint = p.faint, titleColor = p.teal, enabled = !locked, onClick = onPair)
            }
        }
        item {
            Section("Permissions", "Only app blocking is required. The rest make Latch nicer.") {
                ListRow("App blocking", icon = Icons.Rounded.Shield, tint = p.fern, value = if (blockerOn) "On" else "Off", chevron = !blockerOn) {
                    if (!blockerOn) onTurnOnBlocking() else context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                RowDivider()
                ListRow("Hide notifications", icon = Icons.Rounded.NotificationsOff, tint = p.teal, subtitle = "Lets modes hold back notifications from blocked apps.", value = if (silenceOn) "On" else "Off", chevron = true) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                RowDivider()
                ListRow("On-time schedules", icon = Icons.Rounded.Alarm, tint = p.amber, subtitle = "Without it, schedules and timers can start or end a few minutes late.", value = if (exactOn) "On" else "Off", chevron = true) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
                RowDivider()
                ListRow("Session notification", icon = Icons.Rounded.Notifications, tint = p.amber, subtitle = "A quiet timer while a session runs.", value = if (notifOn.value) "On" else "Off", chevron = !notifOn.value) {
                    if (!notifOn.value && Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                    subtitle = "Hold for $EMERGENCY_SECONDS seconds during a session to end it.",
                    value = "${state.emergencyUnlocks} used",
                )
            }
        }
        item {
            Section("About") {
                ListRow("Latch", subtitle = "Free and open source. No account, no tracking, nothing leaves your phone.")
            }
        }
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

fun modeLine(m: Mode): String {
    val apps = when {
        m.type == ListType.Block -> if (m.apps.isEmpty()) "No apps yet" else "Blocks ${m.apps.size} app${if (m.apps.size == 1) "" else "s"}"
        m.apps.isEmpty() -> "Blocks everything but essentials"
        else -> "Allows only ${m.apps.size} app${if (m.apps.size == 1) "" else "s"}"
    }
    val on = m.schedules.count { it.enabled }
    return apps + if (on > 0) " · $on schedule${if (on == 1) "" else "s"}" else ""
}
