package com.latch.focus.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.latch.focus.block.Silencer
import com.latch.focus.data.Hue
import com.latch.focus.data.ListType
import com.latch.focus.data.Mode
import com.latch.focus.data.Schedule
import com.latch.focus.data.Stats
import com.latch.focus.ui.theme.Type
import com.latch.focus.ui.theme.palette
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/** Create or edit a mode: name, color, block/allow list, apps, notification hiding and schedules. */
@Composable
fun ModeEditor(
    initial: Mode?,
    usedHues: Set<Hue>,
    canDelete: Boolean,
    onSave: (Mode) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit,
    onNeedSilenceAccess: () -> Unit,
) {
    val p = palette
    val context = LocalContext.current
    // A new mode takes the first hue not already used, so modes spread across the palette.
    var mode by remember {
        mutableStateOf(initial ?: Mode(UUID.randomUUID().toString(), "", Hue.entries.firstOrNull { it !in usedHues } ?: Hue.Teal))
    }
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Schedule?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val apps = rememberApps().value
    val color = p.hue(mode.hue)

    if (picking) {
        AppPickerScreen(mode.type, mode.apps, color, onDone = { mode = mode.copy(apps = it); picking = false }, onCancel = { picking = false })
        return
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton("Cancel", onClick = onCancel)
            Spacer(Modifier.weight(1f))
            GhostButton("Save", color = if (mode.name.isNotBlank()) color else p.faint) {
                if (mode.name.isNotBlank()) onSave(mode.copy(name = mode.name.trim()))
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                // Name, big and editable, in the mode's color.
                Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    if (mode.name.isEmpty()) Text("Name this mode", style = Type.largeTitle, color = p.faint)
                    BasicTextField(
                        mode.name, { mode = mode.copy(name = it.take(24)) }, singleLine = true,
                        textStyle = Type.largeTitle.copy(color = p.text), cursorBrush = SolidColor(color), modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Work, Sleep, Family time…", style = Type.callout, color = p.faint)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Hue.entries.forEach { h ->
                        val c = p.hue(h)
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(c)
                                .then(if (h == mode.hue) Modifier.border(3.dp, p.bg, CircleShape) else Modifier)
                                .clickable { mode = mode.copy(hue = h) },
                            contentAlignment = Alignment.Center,
                        ) { if (h == mode.hue) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
            item {
                Section(footer = if (mode.type == ListType.Block) "Everything else stays open." else "Everything else is blocked, except the essentials.") {
                    Box(Modifier.padding(12.dp)) {
                        Pills(listOf(ListType.Block to "Block these apps", ListType.Allow to "Allow only these"), mode.type, color) {
                            mode = mode.copy(type = it)
                        }
                    }
                    RowDivider(16.dp)
                    val chosen = apps?.filter { it.pkg in mode.apps }.orEmpty()
                    ListRow(
                        if (mode.apps.isEmpty()) "Choose apps" else "${mode.apps.size} app${if (mode.apps.size == 1) "" else "s"}",
                        icon = Icons.Rounded.Apps, tint = color, chevron = true, onClick = { picking = true },
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                chosen.take(4).forEach { a -> a.icon?.let { Image(it, null, Modifier.size(24.dp).clip(CircleShape)) } }
                            }
                        },
                    )
                }
            }
            item {
                Section(footer = "Latch clears new notifications from blocked apps during this mode. Calls and alarms still come through.") {
                    ListRow(
                        "Hide notifications", icon = Icons.Rounded.NotificationsOff, tint = p.teal,
                        trailing = {
                            Switch(
                                checked = mode.silence,
                                onCheckedChange = { on ->
                                    mode = mode.copy(silence = on)
                                    if (on && !Silencer.allowed(context)) onNeedSilenceAccess()
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = color),
                            )
                        },
                    )
                }
            }
            item {
                val is24 = DateFormat.is24HourFormat(context)
                Section("Schedules", "Starts on its own at these times. Tap your Latch to end one early.") {
                    mode.schedules.forEachIndexed { i, s ->
                        if (i > 0) RowDivider()
                        ListRow(
                            "${Stats.timeOfDay(s.startMin, is24)} – ${Stats.timeOfDay(s.endMin, is24)}",
                            icon = Icons.Rounded.Schedule, tint = p.amber, subtitle = daysLabel(s.days),
                            onClick = { editing = s },
                            trailing = {
                                Switch(
                                    s.enabled, { on -> mode = mode.copy(schedules = mode.schedules.map { if (it.id == s.id) it.copy(enabled = on) else it }) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = p.amber),
                                )
                            },
                        )
                    }
                    if (mode.schedules.isNotEmpty()) RowDivider()
                    ListRow("Add a schedule", icon = Icons.Rounded.Add, tint = p.faint, titleColor = p.amber) {
                        editing = Schedule(UUID.randomUUID().toString(), WEEKDAYS, 9 * 60, 17 * 60)
                    }
                }
            }
            if (initial != null && canDelete) {
                item {
                    Section {
                        ListRow("Delete mode", titleColor = p.brick, onClick = { confirmDelete = true })
                    }
                }
            }
        }
    }

    editing?.let { s ->
        ScheduleSheet(
            s, isNew = mode.schedules.none { it.id == s.id },
            onDismiss = { editing = null },
            onSave = { saved ->
                mode = mode.copy(schedules = if (mode.schedules.any { it.id == saved.id }) mode.schedules.map { if (it.id == saved.id) saved else it } else mode.schedules + saved)
                editing = null
            },
            onDelete = { mode = mode.copy(schedules = mode.schedules.filterNot { it.id == s.id }); editing = null },
        )
    }
    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${initial.name}?") },
            text = { Text("Its apps and schedules are removed. Past sessions stay in Activity.") },
            confirmButton = { TextButton(onClick = { onDelete(initial.id) }) { Text("Delete", color = p.brick) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private fun daysLabel(days: Set<DayOfWeek>): String = when (days) {
    DayOfWeek.entries.toSet() -> "Every day"
    WEEKDAYS -> "Weekdays"
    WEEKEND -> "Weekends"
    else -> DayOfWeek.entries.filter { it in days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSheet(initial: Schedule, isNew: Boolean, onDismiss: () -> Unit, onSave: (Schedule) -> Unit, onDelete: () -> Unit) {
    val p = palette
    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    var s by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.bg) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(if (isNew) "New schedule" else "Schedule", style = Type.title, color = p.text)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DayOfWeek.entries.forEach { d ->
                    val on = d in s.days
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(if (on) p.amber else p.surface)
                            .clickable { s = s.copy(days = if (on) s.days - d else s.days + d) },
                        contentAlignment = Alignment.Center,
                    ) { Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = Type.headline, color = if (on) Color.White else p.dim) }
                }
            }
            Section {
                ListRow("Starts", value = Stats.timeOfDay(s.startMin, is24), chevron = true) { picking = true }
                RowDivider(16.dp)
                ListRow("Ends", value = Stats.timeOfDay(s.endMin, is24) + if (s.endMin <= s.startMin) " (next day)" else "", chevron = true) { picking = false }
            }
            InkButton("Save", enabled = s.days.isNotEmpty() && s.startMin != s.endMin) { onSave(s) }
            if (!isNew) GhostButton("Delete schedule", color = p.brick, modifier = Modifier.align(Alignment.CenterHorizontally), onClick = onDelete)
        }
    }
    picking?.let { start ->
        val current = if (start) s.startMin else s.endMin
        val state = rememberTimePickerState(current / 60, current % 60, is24)
        AlertDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    val m = state.hour * 60 + state.minute
                    s = if (start) s.copy(startMin = m) else s.copy(endMin = m)
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (start) "Starts at" else "Ends at", style = Type.headline, color = p.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    TimePicker(state)
                }
            },
        )
    }
}
