package com.latch.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.SelfImprovement
import com.latch.ui.focus.FocusScreen
import com.latch.focus.FocusResult
import com.latch.focus.FocusStats
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import com.latch.ui.write.WriteState
import com.latch.ui.theme.extras
import com.latch.ui.theme.screenWash
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.latch.LatchApplication
import com.latch.nfc.NfcAvailability
import com.latch.nfc.SheetState
import com.latch.ui.components.ScanSheet
import com.latch.ui.history.HistoryScreen
import com.latch.ui.read.ReadScreen
import com.latch.ui.tools.ToolsScreen
import com.latch.ui.write.WriteScreen

enum class Tab(val label: String, val icon: ImageVector) {
    Read("Read", Icons.Rounded.Nfc),
    Write("Write", Icons.Rounded.EditNote),
    Focus("Focus", Icons.Rounded.SelfImprovement),
    Tools("Tools", Icons.Rounded.Handyman),
    History("History", Icons.Rounded.History),
}

@Composable
fun LatchRoot(app: LatchApplication, focusRequest: Int = 0) {
    val nfc = app.nfc
    var tab by rememberSaveable { mutableStateOf(Tab.Read) }
    val sheet by nfc.sheet.collectAsState()
    val availability by nfc.availability.collectAsState()
    val lastRead by nfc.lastRead.collectAsState()
    val readProblem by nfc.readProblem.collectAsState()
    val writeState = remember { WriteState() } // lives here so switching tabs never wipes a half-built tag
    val snackbar = remember { SnackbarHostState() }
    val focusEvent by nfc.focusEvent.collectAsState()

    LaunchedEffect(focusRequest) { if (focusRequest > 0) tab = Tab.Focus }
    // A Focus tag tapped inside Latch: jump to Focus and say what happened.
    var handledFocusEvent by rememberSaveable { mutableStateOf(0L) } // so a rotation doesn't replay the last one
    LaunchedEffect(focusEvent?.first) {
        val (at, r) = focusEvent ?: return@LaunchedEffect
        if (at == handledFocusEvent) return@LaunchedEffect
        handledFocusEvent = at
        tab = Tab.Focus
        snackbar.showSnackbar(
            when (r) {
                is FocusResult.Started -> "Focus on · ${r.blockedCount} apps blocked"
                is FocusResult.Ended -> "Focus off · ${FocusStats.format(r.session.length)}"
                FocusResult.NoApps -> "Choose apps to block first"
                FocusResult.NotPaired -> "That tag isn't paired"
            },
        )
    }

    // A tag read while you're on another tab: tell the user and offer to jump to it.
    LaunchedEffect(lastRead?.time) {
        val s = lastRead ?: return@LaunchedEffect
        if (tab != Tab.Read) {
            val r = snackbar.showSnackbar("Read: ${s.summary}", actionLabel = "View", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) tab = Tab.Read
        }
    }
    LaunchedEffect(readProblem) {
        val p = readProblem ?: return@LaunchedEffect
        if (tab != Tab.Read) snackbar.showSnackbar("${p.title}. ${p.detail}")
    }

    Scaffold(
        modifier = Modifier.background(screenWash()),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            // Kairos's brand moment: solid Fern pill, near-white icon.
                            indicatorColor = extras.seg,
                            selectedIconColor = extras.onSeg,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (availability != NfcAvailability.On) NfcBanner(availability)
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.Read -> ReadScreen(
                        nfc, app.history,
                        onWrite = { tab = Tab.Write },
                        onBlueprints = { writeState.openBlueprints(); tab = Tab.Write },
                        onHistory = { tab = Tab.History },
                        onEdit = { specs -> app.handoff.pending.value = specs; tab = Tab.Write },
                    )
                    Tab.Write -> WriteScreen(writeState, nfc, app.templates, app.handoff)
                    Tab.Focus -> FocusScreen(app.focus, nfc)
                    Tab.Tools -> ToolsScreen(nfc)
                    Tab.History -> HistoryScreen(app.history, nfc)
                }
            }
        }
    }

    if (sheet !is SheetState.Hidden) {
        ScanSheet(sheet, onDismiss = nfc::dismiss, onPassword = nfc::providePassword, onFinish = nfc::finish)
    }
}

@Composable
private fun NfcBanner(availability: NfcAvailability) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (availability == NfcAvailability.Off) "NFC is turned off" else "This phone doesn't have NFC",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (availability == NfcAvailability.Off) {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Turn on") }
            }
        }
    }
}
