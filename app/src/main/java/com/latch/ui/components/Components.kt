package com.latch.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.latch.data.HistoryAction
import com.latch.nfc.Operation
import com.latch.nfc.Problem
import com.latch.nfc.RecordKind

val RecordKind.icon: ImageVector
    get() = when (this) {
        RecordKind.Link -> Icons.Rounded.Link
        RecordKind.Text -> Icons.AutoMirrored.Rounded.Notes
        RecordKind.WiFi -> Icons.Rounded.Wifi
        RecordKind.Contact -> Icons.Rounded.ContactPage
        RecordKind.Phone -> Icons.Rounded.Call
        RecordKind.Sms -> Icons.Rounded.Sms
        RecordKind.Email -> Icons.Rounded.Email
        RecordKind.Location -> Icons.Rounded.Place
        RecordKind.App -> Icons.Rounded.Apps
        RecordKind.Data -> Icons.Rounded.DataObject
        RecordKind.Empty -> Icons.Rounded.CropFree
        RecordKind.Unknown -> Icons.Rounded.QuestionMark
    }

val HistoryAction.icon: ImageVector
    get() = when (this) {
        HistoryAction.Read -> Icons.Rounded.Nfc
        HistoryAction.Write -> Icons.Rounded.Edit
        HistoryAction.Erase -> Icons.Rounded.DeleteSweep
        HistoryAction.Copy -> Icons.Rounded.ContentCopy
        HistoryAction.Lock -> Icons.Rounded.Lock
    }

val Operation.icon: ImageVector
    get() = when (this) {
        is Operation.Write -> Icons.Rounded.Edit
        Operation.Erase -> Icons.Rounded.DeleteSweep
        Operation.CopySource, is Operation.CopyTarget -> Icons.Rounded.ContentCopy
        Operation.MakeReadOnly -> Icons.Rounded.Lock
    }

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

/** Icon inside a softly tinted circle. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 44.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** The "ready to scan" animation: rings ripple out from a solid center. */
@Composable
fun PulseRing(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    onColor: Color = MaterialTheme.colorScheme.onPrimary,
    diameter: Dp = 200.dp,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val core = size.minDimension * 0.25f
            val max = size.minDimension / 2f
            for (i in 0 until 3) {
                val f = (phase + i / 3f) % 1f
                drawCircle(
                    color = color.copy(alpha = (1f - f) * 0.45f),
                    radius = core + (max - core) * f,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            drawCircle(color.copy(alpha = 0.12f), radius = core * 1.3f)
        }
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(diameter * 0.5f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = onColor, modifier = Modifier.size(diameter * 0.2f))
            }
        }
    }
}

@Composable
fun ProblemCard(problem: Problem, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Column {
                Text(problem.title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(2.dp))
                Text(problem.detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** A label/value row used inside record cards. */
@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun surfaceCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    contentColor = MaterialTheme.colorScheme.onSurface,
)
