package com.latch.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.latch.nfc.Operation
import com.latch.nfc.SheetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The bottom sheet shown while an operation waits for a tag, fails, or succeeds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(state: SheetState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                is SheetState.Waiting -> Waiting(state, close)
                is SheetState.Done -> Done(state, close)
                SheetState.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun Waiting(state: SheetState.Waiting, onCancel: () -> Unit) {
    val (title, subtitle) = copyFor(state.op)
    val failed = state.problem != null
    PulseRing(
        icon = state.op.icon,
        diameter = 180.dp,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        onColor = if (failed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
    )
    Spacer(Modifier.height(4.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        subtitle, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
    )
    labelFor(state.op)?.let { label ->
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
    state.problem?.let {
        Spacer(Modifier.height(4.dp))
        ProblemCard(it)
        Text(
            "Tap the tag again to retry.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Cancel") }
}

@Composable
private fun Done(state: SheetState.Done, onClose: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (shown) 1f else 0.5f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "check",
    )
    LaunchedEffect(state) {
        shown = true
        delay(2600)
        onClose()
    }
    Icon(
        Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).size(96.dp).scale(scale),
    )
    Text(state.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    state.detail?.let {
        Text(
            it, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(12.dp))
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
}

private fun copyFor(op: Operation): Pair<String, String> = when (op) {
    is Operation.Write -> "Ready to write" to "Hold your phone near the tag."
    Operation.Erase -> "Ready to erase" to "Everything on the next tag you tap will be wiped."
    Operation.CopySource -> "Tap the original" to "Hold your phone near the tag you want to copy."
    is Operation.CopyTarget -> "Now tap the new tag" to "Got it. Hold your phone near the tag to copy onto."
    Operation.MakeReadOnly -> "Ready to lock" to "The next tag you tap will be locked for good."
}

private fun labelFor(op: Operation): String? = when (op) {
    is Operation.Write -> op.label
    is Operation.CopyTarget -> op.label
    else -> null
}
