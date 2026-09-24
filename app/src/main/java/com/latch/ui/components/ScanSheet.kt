package com.latch.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.latch.nfc.Operation
import com.latch.nfc.Report
import com.latch.nfc.SheetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The bottom sheet shown while an operation waits for a tag, fails, or succeeds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(
    state: SheetState,
    onDismiss: () -> Unit,
    onPassword: (String) -> Unit,
    onFinish: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Always report the close, even if the hide animation is interrupted. A stale "done" state blocked reads once.
    val close: () -> Unit = { scope.launch { try { sheetState.hide() } finally { onDismiss() } } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                is SheetState.Waiting -> Waiting(state, close, onPassword, onFinish)
                is SheetState.Done -> Done(state, close)
                SheetState.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun Waiting(state: SheetState.Waiting, onCancel: () -> Unit, onPassword: (String) -> Unit, onFinish: () -> Unit) {
    val (title, subtitle) = copyFor(state.op)
    val failed = state.problem != null
    PulseRing(
        icon = state.op.icon,
        diameter = 168.dp,
        color = if (failed) MaterialTheme.colorScheme.error else state.op.accent,
        onColor = MaterialTheme.colorScheme.surface,
    )
    Spacer(Modifier.height(4.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    labelFor(state.op)?.let { label ->
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
    state.note?.let {
        Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
    }
    state.problem?.let { problem ->
        Spacer(Modifier.height(4.dp))
        ProblemCard(problem)
        if (problem.needsPassword) {
            var pw by remember { mutableStateOf("") }
            OutlinedTextField(
                value = pw, onValueChange = { pw = it }, label = { Text("Tag password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onPassword(pw) }, enabled = pw.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Use this password") }
        } else {
            Text("Tap the tag again to retry.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(8.dp))
    val canFinish = (state.op as? Operation.Batch)?.done?.let { it > 0 } == true || (state.op as? Operation.ReadMany)?.rows?.isNotEmpty() == true
    if (canFinish) {
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Finish") }
    }
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Cancel") }
}

@Composable
private fun Done(state: SheetState.Done, onClose: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (shown) 1f else 0.5f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "check")
    LaunchedEffect(state) {
        shown = true
        if (state.report == null) { delay(2600); onClose() } // reports stay open so they can be shared
    }
    Icon(
        Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, // Fern = success
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).size(88.dp).scale(scale),
    )
    Text(state.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    state.detail?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
    state.report?.let { ReportBox(it) }
    Spacer(Modifier.height(12.dp))
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
}

@Composable
fun ReportBox(report: Report) {
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(report.mime)) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(report.text.toByteArray()) } }
    }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                report.text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()).padding(12.dp),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report.text)
            context.startActivity(Intent.createChooser(send, null))
        }) { Text("Share") }
        OutlinedButton(onClick = { save.launch(report.fileName) }) { Text("Save file") }
    }
}

private fun copyFor(op: Operation): Pair<String, String> = when (op) {
    is Operation.Write -> "Ready to write" to "Hold your phone near the tag."
    is Operation.Erase -> "Ready to erase" to "Everything on the next tag you tap will be wiped."
    Operation.CopySource -> "Tap the original" to "Hold your phone near the tag you want to copy."
    is Operation.CopyTarget -> "Now tap the new tag" to "Got it. Hold your phone near the tag to copy onto."
    Operation.MakeReadOnly -> "Ready to lock" to "The next tag you tap will be locked for good."
    is Operation.Batch -> op.job.title to (op.job.total?.let { "Tag ${op.done + 1} of $it. Tap each tag in turn." } ?: "Tap tags one after another. Finish when you're done.")
    is Operation.ReadMany -> "Scanning many" to "Tap tags one after another. Finish to export the list."
    Operation.Dump -> "Read memory" to "Tap the tag to read every page."
    is Operation.SetPassword -> "Ready to set password" to "Tap the tag you want to protect."
    is Operation.RemovePassword -> "Ready to remove password" to "Tap the protected tag."
    is Operation.Counter -> (if (op.enable) "Turn on scan counter" else "Turn off scan counter") to "Tap an NTAG213/215/216 tag."
    is Operation.Raw -> "Ready to send" to "${op.commands.size} command${if (op.commands.size == 1) "" else "s"} over ${op.tech.name}. Tap the tag."
}

private fun labelFor(op: Operation): String? = when (op) {
    is Operation.Write -> op.label
    is Operation.CopyTarget -> op.label
    is Operation.Batch -> if (op.finished) null else op.job.labelFor(op.done)
    else -> null
}
