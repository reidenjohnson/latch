package com.latch.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.latch.latch
import com.latch.ui.focus.HoldToUnlock
import com.latch.ui.focus.goHome
import com.latch.ui.focus.rememberNow
import com.latch.ui.theme.LatchTheme
import com.latch.ui.theme.extras

/** Covers a blocked app during Focus. Back and "Go home" both leave to the home screen, never to the blocked app. */
class BlockedActivity : ComponentActivity() {

    private var pkg by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pkg = intent.getStringExtra(EXTRA_PACKAGE)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        setContent { LatchTheme { Blocked(pkg, onHome = ::leave, onEmergency = { latch.focus.emergencyUnlock(); finish() }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pkg = intent.getStringExtra(EXTRA_PACKAGE)
    }

    private fun leave() {
        goHome(this)
        finish()
    }

    @Composable
    private fun Blocked(pkg: String?, onHome: () -> Unit, onEmergency: () -> Unit) {
        val state by latch.focus.state.collectAsState()
        val active = state.active
        // Focus ended (tag tapped, or emergency unlock): get out of the way.
        LaunchedEffect(active) { if (active == null) finish() }
        val now = rememberNow()
        val app = remember(pkg) {
            pkg?.let { p ->
                runCatching {
                    val info = packageManager.getApplicationInfo(p, 0)
                    packageManager.getApplicationLabel(info).toString() to packageManager.getApplicationIcon(info).toBitmap(144, 144).asImageBitmap()
                }.getOrNull()
            }
        }
        val fern = extras.fernGradient
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(fern.first(), fern.last(), Color(0xFF1B2415))))) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Spacer(Modifier.weight(1f))
                app?.second?.let { Image(it, contentDescription = null, modifier = Modifier.size(64.dp)) }
                Text(
                    if (app != null) "${app.first} is blocked" else "This app is blocked",
                    style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                )
                Text("You're in Focus", style = MaterialTheme.typography.headlineLarge, color = Color.White, textAlign = TextAlign.Center)
                if (active != null) {
                    Text(
                        com.latch.focus.FocusStats.clock(now - active.start),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 56.sp), color = Color.White,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Icon(Icons.Rounded.Nfc, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(28.dp))
                Text(
                    "Tap your Latch tag to end Focus.",
                    style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.weight(1.2f))
                Button(
                    onClick = onHome,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = fern.last()),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Go to home screen", style = MaterialTheme.typography.titleMedium) }
                HoldToUnlock(
                    color = extras.amberGradient.first(), track = Color.White.copy(alpha = 0.12f), onColor = Color.White,
                    onUnlock = onEmergency,
                )
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
    }
}
