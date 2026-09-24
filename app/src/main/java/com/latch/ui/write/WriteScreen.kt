package com.latch.ui.write

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.latch.nfc.NdefParser
import com.latch.nfc.NfcController
import com.latch.nfc.Operation
import com.latch.nfc.Payloads
import com.latch.nfc.TagSizes
import com.latch.nfc.WifiSecurity
import com.latch.ui.components.IconBadge
import com.latch.ui.components.ScreenHeader
import com.latch.ui.components.surfaceCardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WriteScreen(nfc: NfcController) {
    var kind by rememberSaveable { mutableStateOf<WriteKind?>(null) }
    BackHandler(enabled = kind != null) { kind = null }
    AnimatedContent(kind, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "write") { k ->
        when (k) {
            null -> WriteCatalog(onPick = { kind = it })
            WriteKind.App -> AppPicker(nfc, onBack = { kind = null })
            else -> WriteForm(k, nfc, onBack = { kind = null })
        }
    }
}

@Composable
private fun WriteCatalog(onPick: (WriteKind) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ScreenHeader("Write", "Pick what the tag should do") }
        items(WriteKind.entries) { k ->
            Card(onClick = { onPick(k) }, colors = surfaceCardColors()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    IconBadge(k.icon)
                    Spacer(Modifier.height(14.dp))
                    Text(k.title, style = MaterialTheme.typography.titleMedium)
                    Text(k.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FormHeader(kind: WriteKind, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        Spacer(Modifier.size(4.dp))
        IconBadge(kind.icon, size = 40.dp)
        Spacer(Modifier.size(12.dp))
        Column {
            Text(kind.title, style = MaterialTheme.typography.headlineSmall)
            Text(kind.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WriteForm(kind: WriteKind, nfc: NfcController, onBack: () -> Unit) {
    val context = LocalContext.current
    var values by rememberSaveable(kind) { mutableStateOf(mapOf<String, String>()) }
    var security by rememberSaveable(kind) { mutableStateOf(WifiSecurity.Wpa2) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val validation = remember(values, security) { kind.build(values, security) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FormHeader(kind, onBack)
        if (kind == WriteKind.WiFi) {
            Text("Security", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 4.dp)) {
                WifiSecurity.entries.forEach { s ->
                    FilterChip(selected = security == s, onClick = { security = s }, label = { Text(s.label) })
                }
            }
        }
        kind.fields.forEach { f ->
            if (kind == WriteKind.WiFi && f.key == "password" && security == WifiSecurity.Open) return@forEach
            val error = (validation as? Validation.Invalid)?.fieldErrors?.get(f.key)?.takeIf { it.isNotEmpty() }
            OutlinedTextField(
                value = values[f.key].orEmpty(),
                onValueChange = { values = values + (f.key to it) },
                label = { Text(if (f.required) f.label else "${f.label} (optional)") },
                placeholder = if (f.hint.isNotEmpty()) ({ Text(f.hint) }) else null,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                singleLine = !f.multiline,
                minLines = if (f.multiline) 3 else 1,
                keyboardOptions = KeyboardOptions(keyboardType = f.keyboard, imeAction = if (f.multiline) ImeAction.Default else ImeAction.Next),
                visualTransformation = if (f.secret && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = if (f.secret) ({
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Show password")
                    }
                }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        kind.tip?.let { Tip(it) }
        val message = (validation as? Validation.Ok)?.message
        SizeMeter(message?.byteArrayLength)
        Button(
            onClick = { message?.let { nfc.arm(Operation.Write(it, NdefParser.summary(it, context))) } },
            enabled = message != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Write to tag", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Tip(text: String) {
    Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SizeMeter(bytes: Int?) {
    val text = when {
        bytes == null -> "Fill in the fields to see how much space it needs"
        else -> when (val fit = TagSizes.smallestFit(bytes)) {
            null -> "$bytes bytes. Too big for NTAG213/215/216 tags"
            else -> if (fit.first == "NTAG213") "$bytes bytes. Fits every common tag (NTAG213 and up)"
            else "$bytes bytes. Needs an ${fit.first} or bigger"
        }
    }
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class AppInfo(val label: String, val pkg: String, val icon: ImageBitmap?)

@Composable
private fun AppPicker(nfc: NfcController, onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(context) } }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        FormHeader(WriteKind.App, onBack)
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Search apps") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Tip("Tapping the tag opens the app. If it isn't installed, the Play Store page opens instead.")
        Spacer(Modifier.height(8.dp))
        val list = apps
        if (list == null) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
        } else {
            val filtered = list.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.pkg }) { app ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { nfc.arm(Operation.Write(Payloads.app(app.pkg), "App · ${app.label}")) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
                        Spacer(Modifier.size(14.dp))
                        Column {
                            Text(app.label, style = MaterialTheme.typography.titleMedium)
                            Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun loadApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo.packageName to it }
        .distinctBy { it.first }
        .filter { it.first != context.packageName }
        .map { (pkg, ri) ->
            AppInfo(
                label = ri.loadLabel(pm).toString(),
                pkg = pkg,
                icon = runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
            )
        }
        .sortedBy { it.label.lowercase() }
}
