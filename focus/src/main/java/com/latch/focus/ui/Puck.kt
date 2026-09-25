package com.latch.focus.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.latch.focus.data.Active
import com.latch.focus.data.Hue
import com.latch.focus.data.Stats
import com.latch.focus.ui.theme.Type
import com.latch.focus.ui.theme.palette
import kotlinx.coroutines.launch

const val HOLD_TO_START_SECONDS = 5

/**
 * The on-screen Latch: the heart of the home screen. Idle, it's a quiet disc in the selected mode's hue that you can
 * press and hold to start without your tag. During a session it fills with the mode's color and shows the time.
 */
@Composable
fun Puck(
    active: Active?,
    hue: Hue,
    modeName: String,
    now: Long,
    modifier: Modifier = Modifier,
    diameter: Dp = 264.dp,
    onHoldStart: () -> Unit,
) {
    val p = palette
    val color = p.hue(active?.hue ?: hue)
    val breathe = rememberInfiniteTransition(label = "breathe")
    val glow by breathe.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow",
    )
    val hold = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(modifier.size(diameter + 40.dp), contentAlignment = Alignment.Center) {
        // Halo: breathes while a session runs, a faint static ring when idle.
        Canvas(Modifier.size(diameter + 40.dp)) {
            val r = size.minDimension / 2
            if (active != null) {
                drawCircle(color.copy(alpha = 0.10f + 0.10f * glow), radius = r * (0.93f + 0.07f * glow))
            } else {
                drawCircle(color.copy(alpha = 0.10f), radius = r - 2.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            }
        }
        val fill = if (active != null) Brush.radialGradient(listOf(p.solid(active.hue), p.deep(active.hue)))
        else Brush.linearGradient(listOf(p.surface, p.surface))
        Box(
            Modifier.size(diameter)
                .scale(1f - 0.03f * hold.value)
                .shadow(if (active != null) 24.dp else 14.dp, CircleShape, ambientColor = color, spotColor = color.copy(alpha = 0.5f))
                .clip(CircleShape)
                .background(fill)
                .pointerInput(active == null) {
                    if (active != null) return@pointerInput
                    detectTapGestures(onPress = {
                        val run = scope.launch {
                            hold.animateTo(1f, tween(HOLD_TO_START_SECONDS * 1000, easing = LinearEasing))
                            onHoldStart()
                            hold.snapTo(0f)
                        }
                        tryAwaitRelease()
                        if (hold.value < 1f) { run.cancel(); scope.launch { hold.animateTo(0f, tween(250)) } }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            if (active == null && hold.value > 0f) {
                Canvas(Modifier.size(diameter)) {
                    val w = 6.dp.toPx()
                    drawArc(
                        color, -90f, 360f * hold.value, useCenter = false,
                        topLeft = Offset(w, w), size = Size(size.width - 2 * w, size.height - 2 * w),
                        style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                if (active != null) {
                    Text(active.modeName.uppercase(), style = Type.caption, color = Color.White.copy(alpha = 0.8f))
                    val remaining = active.endsAt?.let { it - now }
                    Text(Stats.clock(remaining ?: (now - active.start)), style = Type.display, color = Color.White)
                    Text(if (remaining != null) "left" else "in focus", style = Type.footnote, color = Color.White.copy(alpha = 0.75f))
                } else {
                    Icon(Icons.Rounded.Nfc, null, tint = color, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(modeName, style = Type.title.copy(fontSize = 26.sp), color = p.text, textAlign = TextAlign.Center, maxLines = 1)
                    Text(
                        if (hold.value > 0f) "Keep holding…" else "Tap your Latch",
                        style = Type.footnote, color = p.dim,
                    )
                }
            }
        }
    }
}
