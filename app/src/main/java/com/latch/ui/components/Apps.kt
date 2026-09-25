package com.latch.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** [suggested] = likely a time sink (see [isDistracting]). Used to pre-check Focus's app picker. */
data class AppInfo(val label: String, val pkg: String, val icon: ImageBitmap?, val suggested: Boolean = false)

/** Apps with a home-screen icon, minus Latch, sorted by name. Slow (loads icons), so call it off the main thread. */
fun loadApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo.packageName to it }
        .distinctBy { it.first }
        .filter { it.first != context.packageName }
        .map { (pkg, ri) ->
            AppInfo(
                ri.loadLabel(pm).toString(), pkg,
                runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                isDistracting(ri.activityInfo.applicationInfo),
            )
        }
        .sortedBy { it.label.lowercase() }
}

/**
 * Apps declare a category in their manifest (android:appCategory), which Android exposes as
 * ApplicationInfo.category: https://developer.android.com/reference/android/content/pm/ApplicationInfo#category
 * Many big apps don't declare one, so a short list of well-known package names backs it up.
 */
private fun isDistracting(info: ApplicationInfo): Boolean =
    info.packageName in KNOWN || info.category in setOf(
        ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_VIDEO,
        ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_NEWS,
    )

private val KNOWN = setOf(
    "com.instagram.android", "com.instagram.barcelona", // Instagram, Threads
    "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", // TikTok
    "com.facebook.katana", "com.facebook.lite",
    "com.twitter.android", "com.snapchat.android", "com.reddit.frontpage", "com.pinterest",
    "com.google.android.youtube", "com.netflix.mediaclient", "tv.twitch.android.app",
    "com.discord", "com.tumblr", "com.bereal.ft",
)
