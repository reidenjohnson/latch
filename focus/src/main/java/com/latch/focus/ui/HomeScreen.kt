package com.latch.focus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.latch.focus.data.AppState
import com.latch.focus.data.Passcode
import com.latch.focus.data.Rules
import com.latch.focus.latch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.latch.focus.data.ListType
import com.latch.focus.ui.theme.Type
import com.latch.focus.ui.theme.palette
import java.text.DateFormat
import java.util.Date


@Composable
fun HomeScreen(
    state: AppState,
    blockerOn: Boolean,
    onActivity: () -> Unit,
    onSettings: () -> Unit,
    onSelectMode: (String) -> Unit,
    onNewMode: () -> Unit,
    onTimer: (Int?) -> Unit,
    onHoldStart: () -> Unit,
    onEmergency: () -> Unit,
    onTurnOnBlocking: () -> Unit,
    onPair: () -> Unit,
) {
    val p = palette
    val now = rememberNow()
    val active = state.active
    val mode = state.selectedMode
    var emergency by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // Top bar: wordmark and two quiet round buttons.
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Latch", style = Type.title, color = p.text, modifier = Modifier.weight(1f))
            RoundIcon(Icons.Rounded.BarChart, "Activity", onActivity)
            Spacer(Modifier.width(8.dp))
            RoundIcon(Icons.Rounded.Settings, "Settings", onSettings)
        }

        // Anything missing for blocking to actually work.
        Spacer(Modifier.height(12.dp))
        when {
            !blockerOn -> Notice(Icons.Rounded.Shield, p.amber, "App blocking is off", "Latch can't cover apps until it's on.", "Turn on", onTurnOnBlocking)
            state.tags.isEmpty() && active == null -> Notice(LatchGlyph, p.teal, "No Latch paired", "Pair a tag to latch and unlatch.", "Pair", onPair)
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Puck(active, mode?.hue ?: com.latch.focus.data.Hue.Teal, mode?.name ?: "Everyday", now, onHoldStart = onHoldStart)
                Spacer(Modifier.height(8.dp))
                AnimatedContent(active != null, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "hint") { on ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (on && active != null) {
                            Text("Tap your Latch to unlatch", style = Type.headline, color = p.text)
                            val ends = active.endsAt
                            Text(
                                buildString {
                                    append("${apps(active.blocked.size)} locked")
                                    if (ends != null) append(" · unlatches at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ends))}")
                                },
                                style = Type.footnote, color = p.dim,
                            )
                        } else {
                            Text(modeSummary(state), style = Type.footnote, color = p.dim, textAlign = TextAlign.Center)
                            Text("or press and hold to latch without it", style = Type.footnote, color = p.faint)
                        }
                    }
                }
            }
        }

        if (active == null) {
            Caption("Mode")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.modes.forEach { m ->
                    val on = m.id == mode?.id
                    val c = p.hue(m.hue)
                    Row(
                        Modifier.clip(CircleShape).background(if (on) c.copy(alpha = 0.14f) else p.surface)
                            .clickable { onSelectMode(m.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(c, 8.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(m.name, style = Type.callout, color = if (on) c else p.text)
                    }
                }
                Box(
                    Modifier.clip(CircleShape).background(p.surface).clickable(onClick = onNewMode).padding(10.dp),
                ) { Icon(Icons.Rounded.Add, "New mode", tint = p.dim, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            Caption("Unlatch")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Pills(
                    listOf(null to "When I tap", 30 to "30 min", 60 to "1 hour", 120 to "2 hours", 240 to "4 hours"),
                    state.timerMinutes, p.amber, onSelect = onTimer,
                )
            }
            Spacer(Modifier.height(24.dp))
        } else {
            Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                GhostButton("Need out sooner?", color = p.dim) { emergency = true }
            }
        }
    }

    if (emergency) WaysOutSheet(state, onDismiss = { emergency = false }) { emergency = false; onEmergency() }
}

private fun modeSummary(s: AppState): String {
    val m = s.selectedMode ?: return ""
    val n = m.apps.size
    return when {
        m.type == ListType.Block && n == 0 -> "No apps chosen for ${m.name} yet"
        m.type == ListType.Block -> "Blocks $n app${if (n == 1) "" else "s"}"
        n == 0 -> "Blocks everything but the essentials"
        else -> "Allows only $n app${if (n == 1) "" else "s"}"
    } + if (s.timerMinutes != null) " for ${com.latch.focus.data.Stats.format(s.timerMinutes * 60_000L)}" else ""
}

@Composable
private fun Caption(text: String) {
    Text(text.uppercase(), style = Type.caption, color = palette.faint, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(palette.surface).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = palette.text, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Notice(icon: ImageVector, color: androidx.compose.ui.graphics.Color, title: String, body: String, action: String, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.headline, color = p.text)
            Text(body, style = Type.footnote, color = p.dim)
        }
        Text(action, style = Type.headline, color = color, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * "Need out sooner?": every way out of a session besides the tag, from gentlest to bluntest.
 * 1. Unlatch later: pick an end time, at least an hour away (no instant gratification).
 * 2. Passcode, if one is set in Settings (for a partner or parent to let someone out).
 * 3. The emergency hold, always available (safety rule).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaysOutSheet(state: AppState, onDismiss: () -> Unit, onEmergency: () -> Unit) {
    val p = palette
    val context = LocalContext.current
    val active = state.active ?: return
    val now = System.currentTimeMillis()
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    // Only offer end times that are at least an hour out and sooner than the current end.
    val later = listOf(1, 2, 4).map { now + it * 3_600_000L }.filter { Rules.endLater(state, it, now) != null }
    var code by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.bg) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text("Need out sooner?", style = Type.title, color = p.text)
            Spacer(Modifier.height(4.dp))
            Text(
                "Calls, Settings and emergency apps are never locked, so you can always reach help.",
                style = Type.callout, color = p.dim,
            )
            Spacer(Modifier.height(20.dp))

            Section(
                "Unlatch later",
                if (later.isEmpty()) "It already unlatches within the hour." else "The earliest is an hour from now.",
            ) {
                later.forEachIndexed { i, at ->
                    if (i > 0) RowDivider()
                    val hours = (at - now + 60_000) / 3_600_000
                    ListRow(
                        "In $hours hour${if (hours == 1L) "" else "s"}", icon = Icons.Rounded.Schedule, tint = p.amber,
                        value = time.format(Date(at)),
                    ) { if (context.latch.endLater(at)) onDismiss() }
                }
                if (later.isEmpty()) {
                    ListRow("Unlatches at ${active.endsAt?.let { time.format(Date(it)) } ?: "—"}", icon = Icons.Rounded.Schedule, tint = p.amber)
                }
            }

            if (state.passcode != null) {
                Spacer(Modifier.height(20.dp))
                Section("Passcode", if (wrong) "That's not it. Try again." else "Whoever set the passcode can unlatch this phone.") {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            code, { code = it.take(Passcode.MAX_LENGTH); wrong = false },
                            singleLine = true, placeholder = { Text("Passcode") }, isError = wrong,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        TextButton(enabled = code.length >= Passcode.MIN_LENGTH, onClick = {
                            if (context.latch.unlockWithPasscode(code)) onDismiss() else { wrong = true; code = "" }
                        }) { Text("Unlock") }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            val left = Rules.emergencyLeft(state, now)
            val back = Rules.emergencyBackAt(state, now)
            Section(
                "Emergency",
                if (left > 0) "$left of ${Rules.EMERGENCY_PER_YEAR} left this year. Each one comes back a year after it's used."
                else "All ${Rules.EMERGENCY_PER_YEAR} are used. The next one comes back on " +
                    "${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(back ?: now))}. Uninstalling Latch always removes every lock.",
            ) {
                Box(Modifier.padding(12.dp)) {
                    // Press and hold, like latching without the tag, so a stray tap can't spend one.
                    if (left > 0) HoldBar("Hold to unlatch now", "Keep holding…", HOLD_TO_START_SECONDS, p.brick, onDone = onEmergency)
                    else InkButton("None left this year", enabled = false, color = p.brick.copy(alpha = 0.12f), onColor = p.brick) {}
                }
            }
        }
    }
}
