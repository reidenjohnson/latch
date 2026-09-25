package com.latch.focus

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/**
 * Apps Focus never blocks, whatever the user picks: Latch, the phone/dialer, Settings, the home screen, the system
 * UI, keyboards and anything emergency-related. This is a safety rule (see CLAUDE.md), so it's resolved from the
 * phone itself at the moment a session starts rather than from a hard-coded list.
 */
object Essentials {
    fun of(context: Context): Set<String> {
        val pm = context.packageManager
        val out = mutableSetOf(context.packageName, "com.android.systemui")
        fun resolve(intent: Intent) = pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
        runCatching { out += resolve(Intent(Intent.ACTION_DIAL)) }
        runCatching { out += resolve(Intent(Settings.ACTION_SETTINGS)) }
        runCatching { out += resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
        runCatching {
            val telecom = context.getSystemService(TelecomManager::class.java)
            telecom?.defaultDialerPackage?.let { out += it }
            telecom?.systemDialerPackage?.let { out += it }
        }
        runCatching {
            context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { out += it.packageName }
        }
        // Emergency apps (SOS, medical info) differ by maker. Matching by name can only ever allow more, never block more.
        runCatching { out += launchable(context).filter { it.contains("emergency", ignoreCase = true) } }
        return out
    }

    /** Every app with a home-screen icon. */
    fun launchable(context: Context): Set<String> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.toSet()
    }
}
