package com.latch.focus.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.latch.focus.data.AppState
import com.latch.focus.data.EndReason
import com.latch.focus.data.Hue
import com.latch.focus.data.Session
import com.latch.focus.data.Stats
import com.latch.focus.data.Trigger
import com.latch.focus.ui.theme.Type
import com.latch.focus.ui.theme.palette
import java.text.DateFormat
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(state: AppState, onBack: () -> Unit) {
    val p = palette
    val now = rememberNow()
    val week = Stats.lastSevenDays(state, now)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = p.text) }
            LargeTitle("Activity")
        }
        item {
            // Hero: today, big, with the week chart underneath.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.surface).padding(20.dp)) {
                Text("TODAY", style = Type.caption, color = p.faint)
                Text(Stats.format(Stats.total(state, Stats.dayStart(Stats.today(now)), now)), style = Type.display.copy(fontSize = 48.sp), color = p.text)
                Text("This week ${Stats.format(Stats.total(state, Stats.weekStart(now), now))}", style = Type.callout, color = p.dim)
                Spacer(Modifier.height(20.dp))
                WeekChart(week)
                Spacer(Modifier.height(10.dp))
                Legend(state)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("This month", Stats.format(Stats.total(state, Stats.monthStart(now), now)), p.teal, Modifier.weight(1f))
                    Tile("All time", Stats.format(Stats.total(state, 0, now)), p.fern, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val streak = Stats.streak(state, now)
                    Tile("Streak", "$streak day${if (streak == 1) "" else "s"}", p.amber, Modifier.weight(1f))
                    Tile("Average", Stats.format(Stats.average(state)), p.text, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Longest", Stats.format(Stats.longest(state)), p.text, Modifier.weight(1f))
                    Tile("Emergency unlocks", "${state.emergencyUnlocks}", p.brick, Modifier.weight(1f))
                }
            }
        }
        if (state.sessions.isNotEmpty()) {
            item {
                Section("Recent") {
                    state.sessions.take(25).forEachIndexed { i, s ->
                        if (i > 0) RowDivider(40.dp)
                        SessionRow(s)
                    }
                }
            }
        } else {
            item {
                Text(
                    "Your time off will show up here. Tap your Latch to start.",
                    style = Type.callout, color = p.faint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
    }
}

/** Seven stacked bars, one color per mode hue, with day initials underneath. */
@Composable
private fun WeekChart(week: List<Pair<java.time.LocalDate, Map<Hue, Long>>>) {
    val p = palette
    val max = week.maxOf { it.second.values.sum() }.coerceAtLeast(60 * 60_000L) // at least a 1-hour scale
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val slot = size.width / week.size
        val bw = slot * 0.46f
        week.forEachIndexed { i, (_, byHue) ->
            val x = i * slot + (slot - bw) / 2
            drawRoundRect(p.surface2, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2))
            var top = size.height
            Hue.entries.forEach { h ->
                val v = byHue[h] ?: 0
                if (v <= 0) return@forEach
                val hgt = (v.toFloat() / max) * size.height
                top -= hgt
                drawRoundRect(p.hue(h), Offset(x, top), Size(bw, hgt), CornerRadius(bw / 2))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        week.forEachIndexed { i, (day, _) ->
            Text(
                day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = Type.caption, color = if (i == week.lastIndex) p.text else p.faint,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Legend(state: AppState) {
    val p = palette
    val hues = state.modes.distinctBy { it.hue }.take(4)
    if (hues.size < 2) return
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        hues.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(p.hue(m.hue), 8.dp)
                Spacer(Modifier.padding(start = 6.dp))
                Text(m.name, style = Type.footnote, color = p.dim)
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, color: Color, modifier: Modifier) {
    val p = palette
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(p.surface).padding(16.dp)) {
        Text(value, style = Type.number, color = color, maxLines = 1)
        Text(label, style = Type.footnote, color = p.dim)
    }
}

@Composable
private fun SessionRow(s: Session) {
    val p = palette
    val day = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(s.start))
    val t = DateFormat.getTimeInstance(DateFormat.SHORT)
    val how = buildList {
        when (s.trigger) { Trigger.Schedule -> add("Scheduled"); Trigger.Hold -> add("Started without tag"); Trigger.Tag -> Unit }
        when (s.endReason) { EndReason.Emergency -> add("Emergency unlock"); EndReason.Passcode -> add("Passcode"); EndReason.Timer -> add("Timer"); else -> Unit }
        if (s.hidden > 0) add("${s.hidden} notifications held")
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 14.dp)) { Dot(p.hue(s.hue)) }
        Column(Modifier.weight(1f)) {
            Text(s.modeName, style = Type.body, color = p.text)
            Text(
                "$day · ${t.format(Date(s.start))}–${t.format(Date(s.end))}" + if (how.isNotEmpty()) "\n" + how.joinToString(" · ") else "",
                style = Type.footnote, color = if (s.endReason == EndReason.Emergency) p.brick else p.dim,
            )
        }
        Text(Stats.format(s.length), style = Type.headline, color = p.text)
    }
}
