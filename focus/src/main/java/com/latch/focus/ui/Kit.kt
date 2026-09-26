package com.latch.focus.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.latch.focus.ui.theme.Type
import com.latch.focus.ui.theme.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Layout

/** Apple-style large title, left-aligned, with optional trailing actions. */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.largeTitle, color = palette.text)
            subtitle?.let { Text(it, style = Type.callout, color = palette.dim) }
        }
        trailing()
    }
}

/** An inset grouped section: small caps header, a rounded white block of rows, and an optional footnote. */
@Composable
fun Section(header: String? = null, footer: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        header?.let {
            Text(it.uppercase(), style = Type.caption, color = palette.faint, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface), content = content)
        footer?.let {
            Text(it, style = Type.footnote, color = palette.faint, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        }
    }
}

/** A hairline between rows, inset to line up with the row text (iOS style). */
@Composable
fun RowDivider(inset: Dp = 60.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(0.6.dp).background(palette.line))
}

/** A settings-style row: colored icon tile, title, optional subtitle, value and trailing content. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = palette.teal,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = palette.text,
    chevron: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    // Last on purpose: `ListRow("Title") { ... }` must mean "on tap". When `trailing` was last, that block ran as
    // composable content on every recomposition, which reopened dialogs and launched system settings by itself.
    onClick: (() -> Unit)? = null,
) {
    var m = modifier.fillMaxWidth().heightIn(min = 52.dp)
    if (onClick != null) m = m.clickable(enabled = enabled, onClick = onClick)
    Row(m.alpha(if (enabled) 1f else 0.45f).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            leading != null -> { leading(); Spacer(Modifier.width(14.dp)) }
            icon != null -> { IconTile(icon, tint); Spacer(Modifier.width(14.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = Type.footnote, color = palette.dim, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        value?.let { Text(it, style = Type.body, color = palette.dim, modifier = Modifier.padding(start = 8.dp)) }
        trailing?.invoke()
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.faint, modifier = Modifier.padding(start = 4.dp))
    }
}

/** A small rounded-square icon tile, filled with a hue (like iOS Settings). */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 30.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(tint), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

// ---------------------------------------------------------------- Buttons

/** The main call to action: a full-width ink pill. */
@Composable
fun InkButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = palette.ink, onColor: Color = palette.onInk, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(color)
            .alpha(if (enabled) 1f else 0.4f).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Type.headline, color = onColor) }
}

/** A quiet text button for secondary actions. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, color: Color = palette.dim, onClick: () -> Unit) {
    Box(modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Type.callout, color = color)
    }
}

/** A row of pill options, one selected. */
@Composable
fun <T> Pills(options: List<Pair<T, String>>, selected: T, color: Color, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.clip(CircleShape)
                    .background(if (on) color.copy(alpha = 0.14f) else palette.surface)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text(label, style = Type.callout.copy(fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else null), color = if (on) color else palette.dim) }
        }
    }
}

/**
 * Press and hold to confirm, with the same feel as holding the on-screen Latch ([Puck]): a buzz that swells, a
 * tight side-to-side hum, then a thud and a pop. The bar fills over [seconds]; letting go early resets it.
 * Used for the emergency unlock.
 */
@Composable
fun HoldBar(label: String, holdingLabel: String, seconds: Int, color: Color, modifier: Modifier = Modifier, track: Color = color.copy(alpha = 0.12f), onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    var holding by remember { mutableStateOf(false) }
    var jitter by remember { mutableStateOf(0f) }
    LaunchedEffect(holding) {
        if (!holding) { jitter = 0f; return@LaunchedEffect }
        var phase = 0.0
        var last = -1L
        while (holding) {
            withFrameMillis { t ->
                val dt = if (last < 0) 0L else t - last
                last = t
                val k = progress.value * progress.value
                phase += 2 * Math.PI * (18 + 22 * k) * dt / 1000.0
                jitter = (kotlin.math.sin(phase) * (0.4f + 2.6f * k)).toFloat()
            }
        }
        jitter = 0f
    }
    val k = progress.value * progress.value
    val burst = if (pop.value in 0.001f..0.999f) 1 - pop.value else 0f
    Box(
        modifier.fillMaxWidth().height(56.dp)
            .offset { IntOffset(jitter.dp.roundToPx(), 0) }
            .scale(1f + 0.03f * k + 0.06f * burst)
            .clip(CircleShape).background(track)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    holding = true
                    Buzz.ramp(context, (seconds * 1000 * (1 - progress.value)).toInt())
                    val run = scope.launch {
                        progress.animateTo(1f, tween((seconds * 1000 * (1 - progress.value)).toInt(), easing = LinearEasing))
                        holding = false
                        Buzz.stop(context)
                        Buzz.thud(context)
                        launch { pop.snapTo(0f); pop.animateTo(1f, tween(650, easing = FastOutSlowInEasing)); pop.snapTo(0f) }
                        done()
                        progress.snapTo(0f)
                    }
                    tryAwaitRelease()
                    holding = false
                    if (progress.value < 1f) { run.cancel(); Buzz.stop(context); scope.launch { progress.animateTo(0f, tween(250)) } }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress.value).background(color))
        val left = (seconds * (1 - progress.value)).toInt() + 1
        Text(
            if (progress.value == 0f) label else "$holdingLabel $left",
            style = Type.headline, color = if (progress.value > 0.5f) Color.White else color,
        )
    }
}

// ---------------------------------------------------------------- Helpers

/** Current time, refreshed every second, for live timers. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000 - now % 1000) } }
    return now
}

/** A permission-style check, re-run whenever the screen resumes (the user may have changed it in Settings). */
@Composable
fun rememberOnResume(check: () -> Boolean): MutableState<Boolean> {
    val value = remember { mutableStateOf(check()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) value.value = check() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return value
}

fun goHome(context: Context) {
    context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** "1 app", "12 apps". */
fun apps(n: Int) = if (n == 1) "1 app" else "$n apps"

/** Where confirmations sit: near the top of the screen, just below the status bar. */
val BANNER_TOP = 40.dp
