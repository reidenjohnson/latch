package com.latch.focus.block

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.latch.focus.MainActivity

/**
 * The special permissions Latch needs, and the most direct way Android allows to grant each one.
 *
 * Neither can be a yes/no popup; Android only lets the user turn them on in Settings:
 * - App blocking (Accessibility): there's no public intent for one app's page, so this opens the Accessibility list.
 * - Hiding notifications: ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS (API 30+) opens Latch's own switch.
 *   https://developer.android.com/reference/android/provider/Settings#ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS
 *
 * To save the user finding their way back, [expect] remembers what we sent them to turn on, and the service calls
 * [granted] when Android connects it, which brings Latch back to the front.
 */
object Grants {
    enum class Kind { Blocking, Notifications }

    @Volatile private var waitingFor: Kind? = null

    fun openBlocking(context: Context) {
        waitingFor = Kind.Blocking
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openNotifications(context: Context) {
        waitingFor = Kind.Notifications
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, Silencer::class.java).flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Some phones don't have the per-app page; fall back to the full list.
        runCatching { context.startActivity(intent) }.onFailure {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Called by a service when Android connects it. If we sent the user to turn it on, bring them back. */
    fun granted(context: Context, kind: Kind) {
        if (waitingFor != kind) return
        waitingFor = null
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    }
}
