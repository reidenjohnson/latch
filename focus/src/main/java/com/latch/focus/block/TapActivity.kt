package com.latch.focus.block

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.latch.focus.MainActivity
import com.latch.focus.data.Change
import com.latch.focus.data.Hue
import com.latch.focus.data.Stats
import com.latch.focus.latch
import com.latch.focus.nfc.TagReader
import com.latch.focus.ui.IconTile
import com.latch.focus.ui.InkButton
import com.latch.focus.ui.theme.LatchTheme
import com.latch.focus.ui.theme.Type
import com.latch.focus.ui.theme.palette
import kotlinx.coroutines.delay

/**
 * Opened by Android when a Latch tag is tapped and Latch isn't on screen (the tag's first record matches this
 * activity's NDEF_DISCOVERED filter). A paired tag starts or ends a session and shows a short card.
 * An unpaired tag (someone else's Latch) never locks anything; it explains itself and offers to pair.
 */
class TapActivity : ComponentActivity() {
    private var change by mutableStateOf<Change?>(null)
    private var uid by mutableStateOf<String?>(null)

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
        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: run { finish(); return }
        uid = TagReader.uid(tag)
        change = latch.tap(uid!!)
        val ok = change is Change.Started || change is Change.Ended
        runCatching {
            val v = getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            v?.vibrate(
                if (ok) android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_DOUBLE_CLICK)
                else android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK),
            )
        }
    }

    private fun openApp(pairUid: String? = null) {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply { if (pairUid != null) putExtra(MainActivity.EXTRA_PAIR_UID, pairUid) },
        )
        finish()
    }

    @Composable
    private fun Card() {
        val c = change ?: return
        val p = palette
        var shown by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(if (shown) 1f else 0.9f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "in")
        val quick = c is Change.Started || c is Change.Ended
        LaunchedEffect(c) {
            shown = true
            if (quick) { delay(1900); finish() }
        }
        Box(
            Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { finish() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth().scale(scale)
                    .clip(RoundedCornerShape(28.dp)).background(p.bg).padding(22.dp),
            ) {
                when (c) {
                    is Change.Started -> Header(Icons.Rounded.Check, p.solid(c.active.hue), "${c.active.modeName} is on",
                        "${c.active.blocked.size} apps blocked. Tap your Latch again to end it.")
                    is Change.Ended -> Header(Icons.Rounded.LockOpen, p.solid(Hue.Fern), "Nice work",
                        "You stayed focused for ${Stats.format(c.session.length)}." +
                            if (c.session.hidden > 0) " ${c.session.hidden} notifications were held back." else "")
                    Change.NotPaired -> {
                        Header(Icons.Rounded.Nfc, p.teal, "This is a Latch",
                            "Tap it to lock distracting apps, and tap again to get them back. It isn't paired with this phone, so nothing was locked.")
                        Spacer(Modifier.height(18.dp))
                        InkButton("Use this Latch on my phone") { openApp(uid) }
                    }
                    Change.NoMode -> {
                        Header(Icons.Rounded.PriorityHigh, p.amber, "Pick apps first", "Your mode doesn't have any apps to block yet.")
                        Spacer(Modifier.height(18.dp))
                        InkButton("Open Latch") { openApp() }
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, title: String, body: String) {
        val p = palette
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, color, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = Type.title, color = p.text)
                Text(body, style = Type.callout, color = p.dim)
            }
        }
    }
}
