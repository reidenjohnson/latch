package com.latch.focus

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.latch.MainActivity
import com.latch.latch
import com.latch.nfc.Haptics
import com.latch.nfc.TagIo
import com.latch.ui.components.GradientPanel
import com.latch.ui.theme.LatchTheme
import com.latch.ui.theme.extras
import kotlinx.coroutines.delay

/**
 * Opened by Android when a Latch Focus tag is tapped while Latch isn't on screen (the tag's external record matches
 * this activity's NDEF_DISCOVERED filter). Toggles Focus, shows a short confirmation card, then gets out of the way.
 */
class FocusTapActivity : ComponentActivity() {

    private var result by mutableStateOf<FocusResult?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent { LatchTheme { Card() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
        if (tag == null) { finish(); return }
        val r = latch.focus.toggle(TagIo.uid(tag))
        val haptics = Haptics(this)
        if (r is FocusResult.Started || r is FocusResult.Ended) haptics.success() else haptics.error()
        result = r
    }

    @Composable
    private fun Card() {
        val r = result ?: return
        var shown by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(if (shown) 1f else 0.85f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "card")
        LaunchedEffect(r) {
            shown = true
            delay(if (r is FocusResult.Started || r is FocusResult.Ended) 1800 else 4000)
            finish()
        }
        val x = extras
        val (colors, title, detail) = when (r) {
            is FocusResult.Started -> Triple(
                x.fernGradient, "Focus on",
                "${r.blockedCount} apps blocked. Tap your tag again to end." +
                    if (!r.blockerOn) "\nBlocking is turned off. Open Latch to turn it on." else "",
            )
            is FocusResult.Ended -> Triple(x.tealGradient, "Focus off", "You stayed focused for ${FocusStats.format(r.session.length)}." +
                if (r.session.hidden > 0) "\n${r.session.hidden} notification${if (r.session.hidden == 1) "" else "s"} hidden." else "")
            FocusResult.NotPaired -> Triple(x.amberGradient, "Not your Focus tag", "This tag isn't paired with this phone. Pair it on Latch's Focus tab.")
            FocusResult.NoApps -> Triple(x.amberGradient, "No apps to block yet", "Open Latch and choose which apps Focus blocks.")
        }
        Box(
            Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { finish() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            GradientPanel(colors, Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().scale(scale)) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (r) {
                                is FocusResult.Started -> Icons.Rounded.SelfImprovement
                                is FocusResult.Ended -> Icons.Rounded.CheckCircle
                                else -> Icons.Rounded.ErrorOutline
                            },
                            contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                    if (r is FocusResult.NotPaired || r is FocusResult.NoApps || (r is FocusResult.Started && !r.blockerOn)) {
                        TextButton(onClick = {
                            startActivity(Intent(this@FocusTapActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_FOCUS, true))
                            finish()
                        }) { Text("Open Latch", color = Color.White) }
                    }
                }
            }
        }
    }
}
