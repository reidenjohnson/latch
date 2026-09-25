package com.latch.focus.block

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Nfc
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.latch.focus.data.Hue
import com.latch.focus.data.Stats
import com.latch.focus.latch
import com.latch.focus.ui.EmergencySheet
import com.latch.focus.ui.GhostButton
import com.latch.focus.ui.InkButton
import com.latch.focus.ui.goHome
import com.latch.focus.ui.rememberNow
import com.latch.focus.ui.theme.LatchTheme
import com.latch.focus.ui.theme.Type
import com.latch.focus.ui.theme.palette

/** Covers a blocked app during a session. Back and "Go home" both leave to the home screen, never to the blocked app. */
class BlockedActivity : ComponentActivity() {
    private var pkg by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        LaunchedEffect(active) { if (active == null) finish() } // session ended: get out of the way
        val now = rememberNow()
        var emergency by rememberSaveable { mutableStateOf(false) }
        val app = remember(pkg) {
            pkg?.let { name ->
                runCatching {
                    val info = packageManager.getApplicationInfo(name, 0)
                    packageManager.getApplicationLabel(info).toString() to packageManager.getApplicationIcon(info).toBitmap(160, 160).asImageBitmap()
                }.getOrNull()
            }
        }
        val hue = active?.hue ?: Hue.Teal
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.solid(hue), p.deep(hue), Color(0xFF101211))))) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                app?.second?.let {
                    Image(it, null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
                    Spacer(Modifier.height(20.dp))
                }
                Text(
                    if (app != null) "${app.first} is locked" else "This app is locked",
                    style = Type.largeTitle, color = Color.White, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                if (active != null) {
                    val left = active.endsAt?.let { it - now }
                    Text(
                        "${active.modeName} · " + if (left != null) "${Stats.clock(left)} left" else "${Stats.clock(now - active.start)} so far",
                        style = Type.headline.copy(fontFeatureSettings = "tnum"), color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Spacer(Modifier.height(28.dp))
                Icon(Icons.Rounded.Nfc, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(6.dp))
                Text("Tap your Latch to unlock", style = Type.callout, color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.weight(1.3f))
                InkButton("Go to home screen", color = Color.White, onColor = p.deep(hue), onClick = ::leave)
                Spacer(Modifier.height(4.dp))
                GhostButton("Emergency unlock", color = Color.White.copy(alpha = 0.7f)) { emergency = true }
            }
        }
        if (emergency) {
            EmergencySheet(state.emergencyUnlocks, onDismiss = { emergency = false }) {
                emergency = false
                latch.emergencyUnlock()
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
    }
}
