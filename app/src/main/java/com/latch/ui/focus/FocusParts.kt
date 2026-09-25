package com.latch.ui.focus

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val EMERGENCY_HOLD_SECONDS = 10

/** Current time, refreshed every second, for live timers. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1000 - now % 1000) }
    }
    return now
}

/**
 * Emergency unlock: press and hold for [EMERGENCY_HOLD_SECONDS]. Long enough to be a deliberate choice, and always
 * available, so a lost tag never locks anyone out (safety rule in CLAUDE.md).
 */
@Composable
fun HoldToUnlock(color: Color, track: Color, onColor: Color, modifier: Modifier = Modifier, onUnlock: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(shape).background(track)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val run = scope.launch {
                        progress.animateTo(1f, tween((EMERGENCY_HOLD_SECONDS * 1000 * (1 - progress.value)).toInt(), easing = LinearEasing))
                        onUnlock()
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f) {
                        run.cancel()
                        scope.launch { progress.animateTo(0f, tween(300)) }
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress.value).background(color))
        val left = (EMERGENCY_HOLD_SECONDS * (1 - progress.value)).toInt() + 1
        Text(
            if (progress.value == 0f) "Hold for emergency unlock" else "Keep holding… $left",
            style = MaterialTheme.typography.labelLarge,
            color = if (progress.value > 0.5f) onColor else color,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

fun goHome(context: Context) {
    context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
