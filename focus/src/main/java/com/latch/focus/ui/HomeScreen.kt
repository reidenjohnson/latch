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
import androidx.compose.material.icons.rounded.Nfc
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
import com.latch.focus.data.ListType
import com.latch.focus.ui.theme.Type
import com.latch.focus.ui.theme.palette
import java.text.DateFormat
import java.util.Date

const val EMERGENCY_SECONDS = 10

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
            state.tags.isEmpty() && active == null -> Notice(Icons.Rounded.Nfc, p.teal, "No Latch paired", "Pair a tag to start and end sessions.", "Pair", onPair)
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Puck(active, mode?.hue ?: com.latch.focus.data.Hue.Teal, mode?.name ?: "Focus", now, onHoldStart = onHoldStart)
                Spacer(Modifier.height(8.dp))
                AnimatedContent(active != null, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "hint") { on ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (on && active != null) {
                            Text("Tap your Latch to end", style = Type.headline, color = p.text)
                            val ends = active.endsAt
                            Text(
                                buildString {
                                    append("${active.blocked.size} apps blocked")
                                    if (ends != null) append(" · ends at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ends))}")
                                },
                                style = Type.footnote, color = p.dim,
                            )
                        } else {
                            Text(modeSummary(state), style = Type.footnote, color = p.dim, textAlign = TextAlign.Center)
                            Text("or press and hold to start without it", style = Type.footnote, color = p.faint)
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
            Caption("Ends")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Pills(
                    listOf(null to "When I tap", 30 to "30 min", 60 to "1 hour", 120 to "2 hours", 240 to "4 hours"),
                    state.timerMinutes, p.amber, onSelect = onTimer,
                )
            }
            Spacer(Modifier.height(24.dp))
        } else {
            Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                GhostButton("Emergency unlock", color = p.brick) { emergency = true }
            }
        }
    }

    if (emergency) EmergencySheet(state.emergencyUnlocks, onDismiss = { emergency = false }) { emergency = false; onEmergency() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencySheet(used: Int, onDismiss: () -> Unit, onUnlock: () -> Unit) {
    val p = palette
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.bg) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconTile(Icons.Rounded.HealthAndSafety, p.brick, 52.dp)
            Spacer(Modifier.height(16.dp))
            Text("Emergency unlock", style = Type.title, color = p.text)
            Spacer(Modifier.height(6.dp))
            Text(
                "Lost your Latch, or really need your apps? Hold the button for $EMERGENCY_SECONDS seconds to end this session. " +
                    "Calls, Settings and emergency apps are never blocked, so you don't need this to reach help.",
                style = Type.callout, color = p.dim, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (used == 0) "You haven't used it yet." else "You've used it $used time${if (used == 1) "" else "s"}.",
                style = Type.footnote, color = p.faint,
            )
            Spacer(Modifier.height(24.dp))
            HoldBar("Hold to unlock", "Keep holding…", EMERGENCY_SECONDS, p.brick, onDone = onUnlock)
            Spacer(Modifier.height(8.dp))
            GhostButton("Never mind", onClick = onDismiss)
        }
    }
}
