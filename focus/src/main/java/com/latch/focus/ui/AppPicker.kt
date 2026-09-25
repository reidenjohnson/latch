package com.latch.focus.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.latch.focus.data.ListType
import com.latch.focus.engine.Essentials
import com.latch.focus.ui.theme.Type
import com.latch.focus.ui.theme.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(val label: String, val pkg: String, val icon: ImageBitmap?, val suggested: Boolean)

/** Apps with a home-screen icon, minus Latch. Loads icons, so it runs off the main thread. */
@Composable
fun rememberApps(): State<List<AppInfo>?> {
    val context = LocalContext.current
    val apps = remember { mutableStateOf<List<AppInfo>?>(null) }
    LaunchedEffect(Unit) { apps.value = withContext(Dispatchers.IO) { loadApps(context) } }
    return apps
}

@Composable
fun rememberEssentials(): State<Set<String>> {
    val context = LocalContext.current
    val set = remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) { set.value = withContext(Dispatchers.IO) { Essentials.of(context) } }
    return set
}

fun loadApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != context.packageName }
        .map { ri ->
            AppInfo(
                ri.loadLabel(pm).toString(), ri.activityInfo.packageName,
                runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                isTimeSink(ri.activityInfo.applicationInfo),
            )
        }
        .sortedBy { it.label.lowercase() }
}

/**
 * Games are found by category (ApplicationInfo.category, set by the app or its installer):
 * https://developer.android.com/reference/android/content/pm/ApplicationInfo#category
 * Other categories are too broad to trust. On Reiden's S23 (2026-09-25), CATEGORY_SOCIAL also covered Chrome,
 * Gmail, Messages and WhatsApp, and CATEGORY_VIDEO covered a video editor. So social and streaming apps come
 * from a list of well-known package names instead.
 */
private fun isTimeSink(info: ApplicationInfo): Boolean =
    info.category == ApplicationInfo.CATEGORY_GAME || info.packageName in KNOWN

private val KNOWN = setOf(
    // Social
    "com.instagram.android", "com.instagram.barcelona", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
    "com.facebook.katana", "com.facebook.lite", "com.twitter.android", "com.snapchat.android", "com.reddit.frontpage",
    "com.pinterest", "com.tumblr", "com.bereal.ft", "com.discord",
    // Video
    "com.google.android.youtube", "tv.twitch.android.app", "com.netflix.mediaclient", "com.hulu.plus",
    "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient", "com.wbd.stream",
)

/**
 * The app list used in onboarding and the mode editor: a search field, "Suggested" time sinks on top (block lists
 * only), then every app. Essentials are shown but can't be picked.
 */
fun LazyListScope.appPickerItems(
    apps: List<AppInfo>?,
    essentials: Set<String>,
    type: ListType,
    chosen: Set<String>,
    query: String,
    accent: Color,
    onToggle: (String) -> Unit,
) {
    if (apps == null) {
        item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        return
    }
    val matches = apps.filter { query.isBlank() || it.label.contains(query, true) }
    val suggested = if (type == ListType.Block) matches.filter { it.suggested && it.pkg !in essentials } else emptyList()
    val rest = matches - suggested.toSet()
    if (suggested.isNotEmpty()) {
        item(key = "h-s") { PickerHeader("Suggested", "Social, video and game apps on this phone") }
        item(key = "g-s") { AppGroup(suggested, chosen, essentials, accent, onToggle) }
        item(key = "h-a") { PickerHeader("All apps", null) }
    }
    item(key = "g-a") { AppGroup(rest, chosen, essentials, accent, onToggle) }
}

@Composable
private fun AppGroup(apps: List<AppInfo>, chosen: Set<String>, essentials: Set<String>, accent: Color, onToggle: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface)) {
        apps.forEachIndexed { i, app ->
            if (i > 0) RowDivider(68.dp)
            val always = app.pkg in essentials
            ListRow(
                app.label,
                subtitle = if (always) "Always allowed" else null,
                enabled = !always,
                onClick = { onToggle(app.pkg) },
                leading = { app.icon?.let { Image(it, null, Modifier.size(38.dp)) } ?: Box(Modifier.size(38.dp)) },
                trailing = { Check(app.pkg in chosen && !always, accent) },
            )
        }
    }
}

@Composable
private fun Check(on: Boolean, accent: Color) {
    val p = palette
    Box(
        Modifier.size(26.dp).clip(CircleShape).background(if (on) accent else Color.Transparent)
            .then(if (on) Modifier else Modifier.background(p.surface2)),
        contentAlignment = Alignment.Center,
    ) { if (on) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun PickerHeader(title: String, subtitle: String?) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, bottom = 8.dp)) {
        Text(title.uppercase(), style = Type.caption, color = palette.faint)
        subtitle?.let { Text(it, style = Type.footnote, color = palette.faint) }
    }
}

@Composable
fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = palette
    Row(
        modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(p.surface2).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = p.faint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search apps", style = Type.body, color = p.faint)
            BasicTextField(query, onChange, singleLine = true, textStyle = Type.body.copy(color = p.text), cursorBrush = SolidColor(p.teal), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Full-screen picker used from the mode editor. */
@Composable
fun AppPickerScreen(type: ListType, initial: Set<String>, accent: Color, onDone: (Set<String>) -> Unit, onCancel: () -> Unit) {
    val p = palette
    val apps by rememberApps()
    val essentials by rememberEssentials()
    var chosen by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(p.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton("Cancel", onClick = onCancel)
            Spacer(Modifier.weight(1f))
            GhostButton("Done", color = accent) { onDone(chosen - essentials) }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            LargeTitle(if (type == ListType.Block) "Apps to block" else "Apps to allow", subtitle = "${chosen.size} selected")
            SearchField(query, { query = it })
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.alpha(1f),
        ) {
            appPickerItems(apps, essentials, type, chosen, query, accent) { pkg -> chosen = if (pkg in chosen) chosen - pkg else chosen + pkg }
        }
    }
}
