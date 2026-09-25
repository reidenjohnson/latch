package com.latch.focus.block

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.latch.focus.data.Hue
import com.latch.focus.data.Stats
import com.latch.focus.latch
import com.latch.focus.ui.EmergencySheet
import com.latch.focus.ui.GhostButton
import com.latch.focus.ui.LatchGlyph
import com.latch.focus.ui.Words
import com.latch.focus.ui.rememberWord
import com.latch.focus.ui.goHome
import com.latch.focus.ui.rememberNow
import com.latch.focus.ui.theme.LatchTheme
import com.latch.focus.ui.theme.Type
import com.latch.focus.ui.theme.palette

/**
 * Covers a blocked app while the phone is latched. Deliberately quiet, like a phone that's asleep: flat Carbon, one
 * big number, the app shown small and gray, and one line telling you how to get it back. The mode's hue appears only
 * as a thin ring. Back and "Home" both leave to the home screen, never to the blocked app.
 */
class BlockedActivity : ComponentActivity() {
    private var pkg by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always dark, whatever the phone's theme: light status bar icons on Carbon.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        pkg = intent.getStringExtra(EXTRA_PACKAGE)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        setContent { LatchTheme { Blocked() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pkg = intent.getStringExtra(EXTRA_PACKAGE)
    }

    private fun leave() { goHome(this); finish() }

    @Composable
    private fun Blocked() {
        val p = palette
        val state by latch.state.collectAsState()
        val active = state.active
        LaunchedEffect(active) { if (active == null) finish() } // unlatched: get out of the way
        val now = rememberNow()
        var emergency by rememberSaveable { mutableStateOf(false) }
        val app = remember(pkg) {
            pkg?.let { name ->
                runCatching {
                    val info = packageManager.getApplicationInfo(name, 0)
                    packageManager.getApplicationLabel(info).toString() to packageManager.getApplicationIcon(info).toBitmap(120, 120).asImageBitmap()
                }.getOrNull()
            }
        }
        val hue = p.solid(active?.hue ?: Hue.Teal)
        val ink = Color(0xFF111312)
        val white = Color(0xFFF1F1EE)

        Box(Modifier.fillMaxSize().background(ink)) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Who's covering the app, so it never feels like you were thrown back into Latch.
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(LatchGlyph, null, tint = white.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("LATCH", style = Type.caption, color = white.copy(alpha = 0.5f))
                }
                Spacer(Modifier.weight(1f))

                // The hero is the app you tried to open: its icon, grayed out, in a thin breathing ring.
                Box(contentAlignment = Alignment.Center) {
                    BreathingRing(hue)
                    app?.second?.let {
                        Image(
                            it, null,
                            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).alpha(0.85f),
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(app?.first ?: "This app", style = Type.largeTitle.copy(fontSize = 40.sp, lineHeight = 44.sp), color = white)
                Spacer(Modifier.height(4.dp))
                Text(
                    rememberWord(Words.locked, pkg),
                    style = Type.body, color = white.copy(alpha = 0.55f),
                )

                Spacer(Modifier.weight(1.2f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(LatchGlyph, null, tint = hue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tap your Latch to open ${app?.first ?: "it"}", style = Type.callout, color = white.copy(alpha = 0.85f))
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    GhostButton("Emergency", color = white.copy(alpha = 0.4f)) { emergency = true }
                    GhostButton("Home", color = white.copy(alpha = 0.85f), onClick = ::leave)
                }
            }
        }
        if (emergency) {
            EmergencySheet(state.emergencyUnlocks, onDismiss = { emergency = false }) {
                emergency = false
                latch.emergencyUnlock()
            }
        }
    }

    @Composable
    private fun BreathingRing(color: Color) {
        val t = rememberInfiniteTransition(label = "breathe")
        val a by t.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
        Canvas(Modifier.size(190.dp)) {
            drawCircle(color.copy(alpha = a), radius = size.minDimension / 2 - 2.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
    }
}
