package com.latch.focus.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.latch.focus.block.Blocker
import com.latch.focus.data.AppState
import com.latch.focus.data.ListType
import com.latch.focus.data.Mode
import com.latch.focus.nfc.PairState
import com.latch.focus.nfc.TagReader
import com.latch.focus.ui.theme.Type
import com.latch.focus.ui.theme.palette

private enum class Step { Hero, Apps, Pair, Blocking, Done }

/** First run: what Latch is, pick apps, pair a tag, turn on blocking. Everything after the hero can be skipped. */
@Composable
fun Onboarding(state: AppState, tags: TagReader, onSaveMode: (Mode) -> Unit, onFinish: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(Step.Hero) }
    BackHandler(step != Step.Hero) { step = Step.entries[step.ordinal - 1] }
    AnimatedContent(
        step,
        transitionSpec = {
            val fwd = targetState.ordinal > initialState.ordinal
            (slideInHorizontally { if (fwd) it / 4 else -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { if (fwd) -it / 4 else it / 4 } + fadeOut())
        },
        label = "onboarding",
    ) { s ->
        when (s) {
            Step.Hero -> Hero { step = Step.Apps }
            Step.Apps -> AppsStep(state, onSaveMode) { step = Step.Pair }
            Step.Pair -> PairStep(tags) { step = Step.Blocking }
            Step.Blocking -> BlockingStep { step = Step.Done }
            Step.Done -> DoneStep(onFinish)
        }
    }
}

// ---------------------------------------------------------------- Hero

@Composable
private fun Hero(onStart: () -> Unit) {
    val p = palette
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HeroMark() }
        Spacer(Modifier.height(28.dp))
        Text("Latch", style = Type.largeTitle.copy(fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-2).sp), color = p.text)
        Text("Your phone, on your terms.", style = Type.title.copy(fontSize = 22.sp), color = p.dim)
        Spacer(Modifier.height(28.dp))
        Feature(LatchGlyph, p.teal, "Tap to latch", "Tap your Latch and the apps that pull you in are locked.")
        Feature(Icons.Rounded.LockOpen, p.fern, "Tap to unlatch", "To get them back, walk over and tap it again.")
        Feature(Icons.Rounded.Schedule, p.amber, "Runs on a schedule", "Latch automatically for work hours or bedtime, if you like.")
        Feature(Icons.Rounded.HealthAndSafety, p.brick, "Always a way out", "Calls, Settings and an emergency unlock always work.")
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(24.dp))
        InkButton("Get started", onClick = onStart)
        Text(
            "Free, no account, and nothing leaves your phone.",
            style = Type.footnote, color = p.faint, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
        )
    }
}

/** Four slow-turning arcs, one per Kairos hue, around the Latch disc. */
@Composable
private fun HeroMark() {
    val p = palette
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "a")
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(210.dp)) {
            val w = 7.dp.toPx()
            listOf(p.teal, p.fern, p.amber, p.brick).forEachIndexed { i, c ->
                drawArc(
                    c, angle + i * 90f + 8f, 74f, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(w, w),
                    size = androidx.compose.ui.geometry.Size(size.width - 2 * w, size.height - 2 * w),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        Box(Modifier.size(150.dp).clip(CircleShape).background(p.surface), contentAlignment = Alignment.Center) {
            Icon(LatchGlyph, null, tint = p.text, modifier = Modifier.size(52.dp))
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, color: Color, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        IconTile(icon, color, 36.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = Type.headline, color = palette.text)
            Text(body, style = Type.callout, color = palette.dim)
        }
    }
}

// ---------------------------------------------------------------- Steps

@Composable
private fun StepHeader(n: Int, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp)) {
        Text("STEP $n OF 3", style = Type.caption, color = palette.faint)
        Spacer(Modifier.height(6.dp))
        Text(title, style = Type.largeTitle, color = palette.text)
        Spacer(Modifier.height(6.dp))
        Text(body, style = Type.callout, color = palette.dim)
    }
}

@Composable
private fun AppsStep(state: AppState, onSaveMode: (Mode) -> Unit, onNext: () -> Unit) {
    val p = palette
    val mode = state.selectedMode ?: return
    val apps by rememberApps()
    val essentials by rememberEssentials()
    var chosen by remember { mutableStateOf(mode.apps) }
    var prefilled by rememberSaveable { mutableStateOf(mode.apps.isNotEmpty()) }
    var query by remember { mutableStateOf("") }
    // Start with the likely time sinks already checked, so this step is usually just "Continue".
    LaunchedEffect(apps, essentials) {
        val list = apps
        if (!prefilled && list != null && essentials.isNotEmpty()) {
            chosen = list.filter { it.suggested && it.pkg !in essentials }.map { it.pkg }.toSet()
            prefilled = true
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        StepHeader(1, "What pulls you in?", "We checked the usual suspects. Change anything you like.")
        SearchField(query, { query = it })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            appPickerItems(apps, essentials, ListType.Block, chosen, query, p.teal) { pkg -> chosen = if (pkg in chosen) chosen - pkg else chosen + pkg }
        }
        InkButton(if (chosen.isEmpty()) "Pick at least one app" else "Block ${chosen.size} app${if (chosen.size == 1) "" else "s"}", enabled = chosen.isNotEmpty()) {
            onSaveMode(mode.copy(apps = chosen - essentials, type = ListType.Block))
            onNext()
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PairStep(tags: TagReader, onNext: () -> Unit) {
    val pair by tags.pair.collectAsState()
    LaunchedEffect(Unit) { if (pair !is PairState.Paired) tags.startPairing() }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        StepHeader(2, "Pair your Latch", "Any NFC sticker, card or key fob works. Hold it to the back of your phone, near the top. Anything already on it is replaced.")
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { PairVisual(pair) }
        if (pair is PairState.Paired) {
            InkButton("Continue") { tags.cancelPairing(); onNext() }
        } else {
            GhostButton("Skip for now", modifier = Modifier.align(Alignment.CenterHorizontally)) { tags.cancelPairing(); onNext() }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Waiting: a teal pulse. Paired: a fern check. Failed: the reason, and it keeps waiting for another tap. */
@Composable
fun PairVisual(pair: PairState) {
    val p = palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val color = when (pair) {
            is PairState.Paired -> p.fern
            is PairState.Failed -> p.brick
            else -> p.teal
        }
        val t = rememberInfiniteTransition(label = "pulse")
        val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "ph")
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            if (pair !is PairState.Paired) {
                Canvas(Modifier.size(220.dp)) {
                    for (i in 0 until 3) {
                        val f = (phase + i / 3f) % 1f
                        drawCircle(color.copy(alpha = (1 - f) * 0.35f), radius = size.minDimension / 4 + size.minDimension / 4 * f, style = Stroke(2.dp.toPx()))
                    }
                }
            }
            Box(Modifier.size(110.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Icon(
                    when (pair) { is PairState.Paired -> Icons.Rounded.Check; is PairState.Failed -> Icons.Rounded.PriorityHigh; else -> LatchGlyph },
                    null, tint = Color.White, modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val (title, body) = when (pair) {
            is PairState.Paired -> "Paired" to if (pair.lockedTag) "That tag is read-only, so it works while Latch is open." else "Tap it anytime, even with Latch closed."
            is PairState.Failed -> pair.title to "${pair.detail} Then tap again."
            else -> "Ready to pair" to "Hold your tag to the back of your phone."
        }
        Text(title, style = Type.title, color = p.text)
        Text(body, style = Type.callout, color = p.dim, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun BlockingStep(onNext: () -> Unit) {
    val p = palette
    val context = LocalContext.current
    val on by Blocker.running.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        StepHeader(3, "Let Latch lock apps", "Android asks you to turn this on yourself, in Accessibility settings.")
        Section {
            ListRow("Sees which app just opened", icon = Icons.Rounded.Shield, tint = p.teal, subtitle = "So it can cover the ones you chose.")
            RowDivider()
            ListRow("Never reads your screen", icon = Icons.Rounded.VisibilityOff, tint = p.fern, subtitle = "Or what you type. Nothing leaves your phone.")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "On the next screen, open \"Installed apps\" (it may be named differently on your phone), choose Latch and turn it on. " +
                "If Android says it's a restricted setting, open Latch's App info, tap ⋮ and choose \"Allow restricted settings\", then try again.",
            style = Type.footnote, color = p.dim, modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        if (on) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, null, tint = p.fern)
                Spacer(Modifier.width(6.dp))
                Text("App blocking is on", style = Type.headline, color = p.fern)
            }
            InkButton("Continue", onClick = onNext)
        } else {
            InkButton("Open settings") {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            GhostButton("Skip for now", modifier = Modifier.align(Alignment.CenterHorizontally), onClick = onNext)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DoneStep(onFinish: () -> Unit) {
    val p = palette
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onFinish() }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        IconTile(Icons.Rounded.Check, p.fern, 72.dp)
        Spacer(Modifier.height(20.dp))
        Text("You're set", style = Type.largeTitle, color = p.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Stick your Latch somewhere you have to walk to: the kitchen, your desk, by the door. Tap it when you want your time back.",
            style = Type.callout, color = p.dim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1.2f))
        InkButton("Start using Latch") {
            // The session timer notification is optional; ask once, and go on either way.
            if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else onFinish()
        }
        Spacer(Modifier.height(16.dp))
    }
}
