package com.latch.ui.write

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.latch.data.Csv
import com.latch.data.DraftHandoff
import com.latch.data.Template
import com.latch.data.TemplateStore
import com.latch.nfc.BatchJob
import com.latch.nfc.Field
import com.latch.nfc.Input
import com.latch.nfc.NfcController
import com.latch.nfc.Operation
import com.latch.nfc.RecordSpec
import com.latch.nfc.RecordType
import com.latch.nfc.Records
import com.latch.nfc.TagSizes
import com.latch.ui.components.IconBadge
import com.latch.ui.components.ScreenHeader
import com.latch.ui.components.icon
import com.latch.ui.components.surfaceCardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Route {
    data object Home : Route
    data object AllBlueprints : Route
    data class Compose(val title: String, val blueprint: Blueprint? = null) : Route
    data object Batch : Route
}

@Composable
fun WriteScreen(nfc: NfcController, templates: TemplateStore, handoff: DraftHandoff) {
    var route by remember { mutableStateOf<Route>(Route.Home) }
    var draft by remember { mutableStateOf(listOf<RecordSpec>()) }
    var autoArm by remember { mutableStateOf(false) }
    val pending by handoff.pending.collectAsState()

    LaunchedEffect(pending) {
        pending?.let { specs ->
            draft = specs.ifEmpty { listOf(RecordSpec(RecordType.Text)) }
            route = Route.Compose("Edit tag")
            handoff.pending.value = null
        }
    }

    BackHandler(enabled = route != Route.Home) {
        route = when (route) {
            Route.Batch -> Route.Compose("Write many")
            else -> Route.Home
        }
    }

    AnimatedContent(route, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "write") { r ->
        when (r) {
            Route.Home -> WriteHome(
                templates = templates,
                onType = { draft = listOf(RecordSpec(it)); route = Route.Compose(it.title) },
                onBlueprint = { b -> draft = b.specs; autoArm = true; route = Route.Compose(b.title, b) },
                onAllBlueprints = { route = Route.AllBlueprints },
                onTemplate = { t -> draft = t.specs; route = Route.Compose(t.name) },
            )
            Route.AllBlueprints -> BlueprintGallery(
                onBack = { route = Route.Home },
                onPick = { b -> draft = b.specs; autoArm = true; route = Route.Compose(b.title, b) },
            )
            is Route.Compose -> Composer(
                title = r.title, blueprint = r.blueprint, draft = draft, onDraft = { draft = it },
                nfc = nfc, templates = templates, autoArm = autoArm, onAutoArmUsed = { autoArm = false },
                onBack = { route = Route.Home }, onBatch = { route = Route.Batch },
            )
            Route.Batch -> BatchSetup(draft, nfc, onBack = { route = Route.Compose("Write many") })
        }
    }
}

// ---------------------------------------------------------------- Home

@Composable
private fun WriteHome(
    templates: TemplateStore,
    onType: (RecordType) -> Unit,
    onBlueprint: (Blueprint) -> Unit,
    onAllBlueprints: () -> Unit,
    onTemplate: (Template) -> Unit,
) {
    val saved by templates.items.collectAsState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ScreenHeader("Write", "Build a tag, or start from a blueprint") }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle("Blueprints", "Ready-made ideas. Tap one and you're halfway done.", action = "See all", onAction = onAllBlueprints)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(blueprints.take(8)) { b -> BlueprintCard(b, Modifier.width(210.dp)) { onBlueprint(b) } }
            }
        }
        if (saved.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Saved", "Your designs, ready to write again.") }
            items(saved, span = { GridItemSpan(maxLineSpan) }) { t -> SavedRow(t, onOpen = { onTemplate(t) }, onDelete = { templates.remove(t.id) }) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Start from scratch", "Pick what the tag should do.") }
        items(RecordType.entries) { t ->
            Card(onClick = { onType(t) }, colors = surfaceCardColors()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    IconBadge(t.icon)
                    Spacer(Modifier.height(14.dp))
                    Text(t.title, style = MaterialTheme.typography.titleMedium)
                    Text(t.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun BlueprintCard(b: Blueprint, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, colors = surfaceCardColors(), modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            IconBadge(b.icon)
            Spacer(Modifier.height(12.dp))
            Text(b.title, style = MaterialTheme.typography.titleMedium)
            Text(
                b.pitch, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis, minLines = 3,
            )
        }
    }
}

@Composable
private fun SavedRow(t: Template, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onOpen, colors = surfaceCardColors()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Bookmark, size = 40.dp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    t.specs.joinToString(" + ") { it.type.title }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete ${t.name}") }
        }
    }
}

@Composable
private fun BlueprintGallery(onBack: () -> Unit, onPick: (Blueprint) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Column {
                    Text("Blueprints", style = MaterialTheme.typography.headlineSmall)
                    Text("Tap one to fill in the blanks and write.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        BlueprintGroup.entries.forEach { g ->
            val inGroup = blueprints.filter { it.group == g }
            if (inGroup.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text(g.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
                items(inGroup) { b -> BlueprintCard(b) { onPick(b) } }
            }
        }
    }
}

// ---------------------------------------------------------------- Composer

@Composable
private fun Composer(
    title: String,
    blueprint: Blueprint?,
    draft: List<RecordSpec>,
    onDraft: (List<RecordSpec>) -> Unit,
    nfc: NfcController,
    templates: TemplateStore,
    autoArm: Boolean,
    onAutoArmUsed: () -> Unit,
    onBack: () -> Unit,
    onBatch: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var building by remember { mutableStateOf(false) }
    val errors = remember(draft) { draft.map { Records.validate(it) } }
    val valid = draft.isNotEmpty() && errors.all { it.isEmpty() }
    val estimate = remember(draft, valid) { if (valid) runCatching { Records.message(draft, estimateOnly = true).byteArrayLength }.getOrNull() else null }

    val write: () -> Unit = {
        building = true
        scope.launch {
            // Building can be slow (locked notes derive a key), so it runs off the main thread.
            val message = withContext(Dispatchers.Default) { runCatching { Records.message(draft) }.getOrNull() }
            building = false
            message?.let { nfc.arm(Operation.Write(it, label(draft), mirror = Records.mirrorOf(draft))) }
        }
    }
    // Blueprints that need nothing personal go straight to the scan: "tap it and it writes".
    LaunchedEffect(Unit) { if (autoArm) { onAutoArmUsed(); if (valid) write() } }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                blueprint?.let { Text(it.pitch, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = { saving = true }, enabled = valid) { Icon(Icons.Rounded.BookmarkAdd, contentDescription = "Save design") }
        }
        // Single-record blueprints already get the record's own tip, so only multi-record ones need the overview.
        blueprint?.takeIf { it.specs.size > 1 }?.let { Tip(it.how) }

        draft.forEachIndexed { i, spec ->
            RecordEditor(
                spec = spec, errors = errors[i], index = i, count = draft.size,
                onChange = { new -> onDraft(draft.toMutableList().also { it[i] = new }) },
                onRemove = { onDraft(draft.toMutableList().also { it.removeAt(i) }) },
            )
        }
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Add another record")
        }
        if (draft.size > 1) Tip("When tapped outside Latch, phones act on the first record. Latch and NFC reader apps show them all.")
        SizeMeter(estimate)
        Button(onClick = write, enabled = valid && !building, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            if (building) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("Write to tag", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(onClick = onBatch, enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Write many tags…") }
        Spacer(Modifier.height(24.dp))
    }

    if (picking) TypePicker(onDismiss = { picking = false }, onPick = { onDraft(draft + RecordSpec(it)); picking = false })
    if (saving) SaveDialog(suggested = title, onDismiss = { saving = false }, onSave = { templates.save(it, draft); saving = false })
}

private fun label(draft: List<RecordSpec>) =
    Records.describe(draft.first()) + if (draft.size > 1) " (+${draft.size - 1} more)" else ""

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordEditor(
    spec: RecordSpec, errors: Map<String, String>, index: Int, count: Int,
    onChange: (RecordSpec) -> Unit, onRemove: () -> Unit,
) {
    var showSecret by remember { mutableStateOf(false) }
    var pickingApp by remember { mutableStateOf(false) }
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(spec.type.icon, size = 36.dp)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(spec.type.title, style = MaterialTheme.typography.titleMedium)
                    if (count > 1) Text("Record ${index + 1} of $count", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (count > 1) IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "Remove record") }
            }
            Records.visibleFields(spec).forEach { f ->
                val err = errors[f.key]?.takeIf { it.isNotEmpty() }
                when {
                    f.options != null -> {
                        Text(f.label, style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            f.options.forEach { (value, label) ->
                                FilterChip(selected = spec.value(f.key) == value, onClick = { onChange(spec.copy(values = spec.values + (f.key to value))) }, label = { Text(label) })
                            }
                        }
                    }
                    spec.type == RecordType.App && f.key == "package" -> {
                        OutlinedButton(onClick = { pickingApp = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(spec.value("label").ifEmpty { spec.value("package") }.ifEmpty { "Choose an app" })
                        }
                    }
                    else -> FieldInput(f, spec.value(f.key), err, showSecret, onToggleSecret = { showSecret = !showSecret }) {
                        onChange(spec.copy(values = spec.values + (f.key to it)))
                    }
                }
            }
            tipFor(spec)?.let { Tip(it) }
        }
    }
    if (pickingApp) AppPicker(onDismiss = { pickingApp = false }) { pkg, label ->
        onChange(spec.copy(values = spec.values + ("package" to pkg) + ("label" to label)))
        pickingApp = false
    }
}

@Composable
private fun FieldInput(f: Field, value: String, error: String?, showSecret: Boolean, onToggleSecret: () -> Unit, onValue: (String) -> Unit) {
    val secret = f.input == Input.Password
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(if (f.required) f.label else "${f.label} (optional)") },
        placeholder = if (f.hint.isNotEmpty()) ({ Text(f.hint) }) else null,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = f.input != Input.Multiline,
        minLines = if (f.input == Input.Multiline) 3 else 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = when (f.input) {
                Input.Url -> KeyboardType.Uri
                Input.Phone -> KeyboardType.Phone
                Input.Email -> KeyboardType.Email
                Input.Password -> KeyboardType.Password
                Input.Number -> KeyboardType.Number
                else -> KeyboardType.Text
            },
            imeAction = if (f.input == Input.Multiline) ImeAction.Default else ImeAction.Next,
        ),
        visualTransformation = if (secret && !showSecret) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) ({
            IconButton(onClick = onToggleSecret) {
                Icon(if (showSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Show password")
            }
        }) else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** What happens when someone taps the tag. Only states behavior that's documented or verified (see sources in nfc/). */
private fun tipFor(spec: RecordSpec): String? = when (spec.type) {
    RecordType.Link -> "Opens in the phone's browser. The other phone doesn't need any app."
    RecordType.Social -> "Opens the profile in their browser, or in the app if they have it."
    RecordType.WiFi -> "Android offers to connect when the tag is tapped. The password is stored as plain text, so anyone " +
        "with an NFC app can read it. Android's tag reader has no WPA3-only option. Pick WPA2/WPA3 for WPA2 or mixed-mode routers."
    RecordType.Contact -> "Saved as a standard vCard contact card."
    RecordType.Text -> "Plain text. Phones usually need an NFC app, like Latch, to show it."
    RecordType.Location -> if (spec.value("mode") == "directions") "Opens Google Maps directions (in the browser if Maps isn't installed)."
    else "Opens the phone's map app."
    RecordType.Sms, RecordType.Email -> "Opens a draft. Nothing is sent until they press send."
    RecordType.Phone -> "Opens the dialer with the number filled in. Nothing is called until they press call."
    RecordType.App -> "Opens the app. If it isn't installed, the Play Store page opens instead."
    RecordType.Bluetooth -> "Find the address in the device's manual or in Android's Bluetooth settings (Device details)."
    RecordType.Emergency -> "Any NFC reader app shows this info. Anyone who taps it can read it, so only include what you're comfortable sharing."
    RecordType.Secret -> "AES-256 encrypted. Only Latch can open it, and only with this password. There's no way to recover a forgotten password."
    RecordType.LiveLink -> "Needs an NTAG213/215/216. The chip rewrites the end of the link with its live ID and/or scan count every time it's read."
    RecordType.Custom -> "For developers: writes any MIME or NFC Forum external record."
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

@Composable
private fun TypePicker(onDismiss: () -> Unit, onPick: (RecordType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a record") },
        text = {
            LazyColumn {
                items(RecordType.entries) { t ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(t) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(t.icon, size = 36.dp)
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text(t.title, style = MaterialTheme.typography.titleSmall)
                            Text(t.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SaveDialog(suggested: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(suggested) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this design") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- Batch

private enum class BatchMode(val label: String) { Same("Same on every tag"), Numbered("Numbered"), Csv("From a spreadsheet (CSV)") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchSetup(draft: List<RecordSpec>, nfc: NfcController, onBack: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(BatchMode.Same) }
    var count by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("1") }
    var lockAfter by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<List<String>>?>(null) }
    var csvError by remember { mutableStateOf<String?>(null) }
    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val parsed = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { Csv.parse(it.readText()) } }.getOrNull()
        if (parsed == null || parsed.size < 2) { rows = null; csvError = "Couldn't find a header row plus at least one data row." }
        else { rows = parsed; csvError = null }
    }
    val usesN = draft.any { s -> s.values.values.any { "{n}" in it } }
    val header = rows?.first().orEmpty()
    val dataRows = rows?.drop(1).orEmpty()

    val ready = when (mode) {
        BatchMode.Same -> true
        BatchMode.Numbered -> usesN && start.toIntOrNull() != null
        BatchMode.Csv -> dataRows.isNotEmpty()
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Column {
                Text("Write many tags", style = MaterialTheme.typography.headlineSmall)
                Text(label(draft), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BatchMode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) }) }
        }
        when (mode) {
            BatchMode.Same -> Tip("Writes the same thing to every tag you tap. Great for a stack of Wi-Fi or business-card tags.")
            BatchMode.Numbered -> {
                Tip("Put {n} anywhere in your fields (like \"Table {n}\" or example.com/item/{n}). Each tag gets the next number.")
                if (!usesN) Text("None of your fields contain {n} yet. Go back and add it.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = start, onValueChange = { start = it.filter(Char::isDigit) }, label = { Text("Start at") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
            BatchMode.Csv -> {
                Tip("The first row is column names. Use {ColumnName} in your fields, and each row becomes one tag.")
                OutlinedButton(onClick = { pickCsv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (rows == null) "Choose CSV file" else "${dataRows.size} rows · columns: ${header.joinToString()}")
                }
                csvError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (mode != BatchMode.Csv) {
            OutlinedTextField(value = count, onValueChange = { count = it.filter(Char::isDigit) }, label = { Text("How many tags? (optional)") },
                supportingText = { Text("Leave blank to keep going until you tap Finish.") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth().clickable { lockAfter = !lockAfter }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = lockAfter, onCheckedChange = { lockAfter = it })
            Column {
                Text("Lock each tag after writing", style = MaterialTheme.typography.titleSmall)
                Text("Permanent. They can never be changed again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Button(
            enabled = ready,
            onClick = {
                val n0 = start.toIntOrNull() ?: 1
                val vars: (Int) -> Map<String, String> = when (mode) {
                    BatchMode.Same -> { _ -> emptyMap() }
                    BatchMode.Numbered -> { i -> mapOf("n" to (n0 + i).toString()) }
                    BatchMode.Csv -> { i -> header.mapIndexed { c, h -> h.trim() to dataRows[i].getOrElse(c) { "" } }.toMap() + ("n" to (i + 1).toString()) }
                }
                val specsFor = { i: Int -> draft.map { Records.substitute(it, vars(i)) } }
                nfc.arm(Operation.Batch(BatchJob(
                    title = "Writing tags",
                    total = if (mode == BatchMode.Csv) dataRows.size else count.toIntOrNull()?.takeIf { it > 0 },
                    lockAfter = lockAfter,
                    mirror = Records.mirrorOf(draft),
                    messageFor = { i -> Records.message(specsFor(i)) },
                    labelFor = { i -> label(specsFor(i)) },
                )))
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Start", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- App picker

private data class AppInfo(val label: String, val pkg: String, val icon: ImageBitmap?)

@Composable
private fun AppPicker(onDismiss: () -> Unit, onPick: (String, String) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(context) } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                    Text("Choose an app", style = MaterialTheme.typography.headlineSmall)
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val list = apps
                if (list == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
                } else {
                    val filtered = list.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(filtered, key = { it.pkg }) { app ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(app.pkg, app.label) }.padding(vertical = 10.dp, horizontal = 4.dp),
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
            AppInfo(ri.loadLabel(pm).toString(), pkg, runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull())
        }
        .sortedBy { it.label.lowercase() }
}
