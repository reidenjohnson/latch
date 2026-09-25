package com.latch.focus

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import com.latch.focus.block.Blocker
import com.latch.focus.data.Change
import com.latch.focus.data.Stats
import com.latch.focus.nfc.PairState
import com.latch.focus.nfc.TapNote
import com.latch.focus.ui.ActivityScreen
import com.latch.focus.ui.GhostButton
import com.latch.focus.ui.HomeScreen
import com.latch.focus.ui.ModeEditor
import com.latch.focus.ui.Onboarding
import com.latch.focus.ui.PairVisual
import com.latch.focus.ui.SettingsScreen
import com.latch.focus.ui.theme.LatchTheme
import com.latch.focus.ui.theme.palette
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var adapter: NfcAdapter? = null
    /** Set when "Use this Latch on my phone" was tapped on someone else's tag. */
    private var pairUid by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        adapter = NfcAdapter.getDefaultAdapter(this)
        pairUid = intent.getStringExtra(EXTRA_PAIR_UID)
        setContent { LatchTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_PAIR_UID)?.let { pairUid = it }
    }

    /** While Latch is open, reader mode sends every tag straight here instead of through Android's dispatcher. */
    override fun onResume() {
        super.onResume()
        latch.tick()
        val a = adapter ?: return
        if (!a.isEnabled) return
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        runCatching { a.enableReaderMode(this, { tagReader.onTag(it) }, flags, null) }
    }

    override fun onPause() {
        super.onPause()
        runCatching { adapter?.disableReaderMode(this) }
    }

    private enum class Screen { Home, Activity, Settings, Mode }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Root() {
        val p = palette
        val engine = latch
        val state by engine.state.collectAsState()
        val blockerOn by Blocker.running.collectAsState()
        val pair by tagReader.pair.collectAsState()
        val event by engine.events.collectAsState()
        val note by tagReader.note.collectAsState()
        var screen by rememberSaveable { mutableStateOf(Screen.Home) }
        var editingMode by rememberSaveable { mutableStateOf<String?>(null) }
        var pairing by rememberSaveable { mutableStateOf(false) }
        var blockingInfo by rememberSaveable { mutableStateOf(false) }
        var silenceInfo by rememberSaveable { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        var seenEvent by rememberSaveable { mutableStateOf(0L) }
        var seenNote by rememberSaveable { mutableStateOf(0L) }

        LaunchedEffect(event?.first) {
            val (stamp, c) = event ?: return@LaunchedEffect
            if (stamp == seenEvent) return@LaunchedEffect
            seenEvent = stamp
            screen = Screen.Home
            if (c is Change.Ended) snackbar.showSnackbar("You stayed focused for ${Stats.format(c.session.length)}")
        }
        LaunchedEffect(note?.first) {
            val (stamp, n) = note ?: return@LaunchedEffect
            if (stamp == seenNote) return@LaunchedEffect
            seenNote = stamp
            if (n == TapNote.Unpaired) {
                val r = snackbar.showSnackbar("That tag isn't paired with this phone", actionLabel = "Pair it")
                if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) { tagReader.startPairing(); pairing = true }
            }
        }

        Box(Modifier.fillMaxSize().background(p.bg)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (!state.onboarded) {
                    Onboarding(state, tagReader, onSaveMode = engine::saveMode, onFinish = engine::finishOnboarding)
                } else {
                    BackHandler(screen != Screen.Home) { screen = if (screen == Screen.Mode) Screen.Settings else Screen.Home }
                    AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { s ->
                        when (s) {
                            Screen.Home -> HomeScreen(
                                state, blockerOn,
                                onActivity = { screen = Screen.Activity },
                                onSettings = { screen = Screen.Settings },
                                onSelectMode = engine::select,
                                onNewMode = { editingMode = null; screen = Screen.Mode },
                                onTimer = engine::setTimer,
                                onHoldStart = {
                                    if (engine.startByHold() == Change.NoMode) {
                                        editingMode = state.selectedMode?.id; screen = Screen.Mode
                                    }
                                },
                                onEmergency = { engine.emergencyUnlock() },
                                onTurnOnBlocking = { blockingInfo = true },
                                onPair = { tagReader.startPairing(); pairing = true },
                            )
                            Screen.Activity -> ActivityScreen(state) { screen = Screen.Home }
                            Screen.Settings -> SettingsScreen(
                                state,
                                onBack = { screen = Screen.Home },
                                onEditMode = { editingMode = it; screen = Screen.Mode },
                                onPair = { tagReader.startPairing(); pairing = true },
                                onRenameTag = engine::renameTag,
                                onUnpair = engine::unpair,
                                onTurnOnBlocking = { blockingInfo = true },
                            )
                            Screen.Mode -> ModeEditor(
                                initial = state.mode(editingMode),
                                usedHues = state.modes.map { it.hue }.toSet(),
                                canDelete = state.modes.size > 1,
                                onSave = { engine.saveMode(it); engine.select(it.id); screen = Screen.Settings },
                                onDelete = { engine.deleteMode(it); screen = Screen.Settings },
                                onCancel = { screen = Screen.Settings },
                                onNeedSilenceAccess = { silenceInfo = true },
                            )
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }

        if (pairing) {
            LaunchedEffect(pair) { if (pair is PairState.Paired) { delay(1600); tagReader.cancelPairing(); pairing = false } }
            ModalBottomSheet(
                onDismissRequest = { tagReader.cancelPairing(); pairing = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.bg,
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PairVisual(pair)
                    GhostButton("Cancel") { tagReader.cancelPairing(); pairing = false }
                }
            }
        }

        pairUid?.let { uid ->
            AlertDialog(
                onDismissRequest = { pairUid = null },
                title = { Text("Use this Latch?") },
                text = { Text("It will start and end sessions on this phone too. It keeps working for whoever else uses it.") },
                confirmButton = { TextButton(onClick = { engine.pair(uid); engine.finishOnboarding(); pairUid = null }) { Text("Pair") } },
                dismissButton = { TextButton(onClick = { pairUid = null }) { Text("Not now") } },
            )
        }

        if (blockingInfo) {
            AlertDialog(
                onDismissRequest = { blockingInfo = false },
                title = { Text("Turn on app blocking") },
                text = {
                    Text(
                        "Latch uses Android's Accessibility setting only to see which app just opened, so it can cover the ones you chose. " +
                            "It never reads your screen or what you type, and nothing leaves your phone.\n\n" +
                            "On the next screen, find Latch (maybe under \"Installed apps\") and turn it on.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        blockingInfo = false
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("Open settings") }
                },
                dismissButton = { TextButton(onClick = { blockingInfo = false }) { Text("Not now") } },
            )
        }

        if (silenceInfo) {
            AlertDialog(
                onDismissRequest = { silenceInfo = false },
                title = { Text("Allow notification access") },
                text = {
                    Text(
                        "To hold back notifications from blocked apps, Android needs you to give Latch notification access. " +
                            "Latch only checks which app a notification is from. Calls and alarms are never touched.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        silenceInfo = false
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) { Text("Open settings") }
                },
                dismissButton = { TextButton(onClick = { silenceInfo = false }) { Text("Not now") } },
            )
        }
    }

    companion object {
        const val EXTRA_PAIR_UID = "pair_uid"
    }
}
