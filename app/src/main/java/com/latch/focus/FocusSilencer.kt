package com.latch.focus

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.latch.latch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * During Focus, dismisses notifications from blocked apps (when "Hide their notifications" is on). Only clearable
 * notifications are touched, so ongoing ones like calls, navigation and media controls stay. Hidden notifications
 * aren't saved; the messages are still in the app when you open it after Focus.
 * https://developer.android.com/reference/android/service/notification/NotificationListenerService
 */
class FocusSilencer : NotificationListenerService() {
    private var scope: CoroutineScope? = null

    override fun onListenerConnected() {
        // Focus started while notifications were already waiting: clear them too.
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { s ->
            s.launch { latch.focus.state.distinctUntilChangedBy { it.active?.start to it.silence }.collect { sweep() } }
        }
    }

    override fun onListenerDisconnected() {
        scope?.cancel()
        scope = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = hideIfBlocked(sbn)

    private fun sweep() {
        runCatching { activeNotifications }.getOrNull()?.forEach { hideIfBlocked(it) }
    }

    private fun hideIfBlocked(sbn: StatusBarNotification) {
        val s = latch.focus.state.value
        val active = s.active ?: return
        if (!s.silence || !sbn.isClearable || sbn.packageName !in active.blocked) return
        runCatching { cancelNotification(sbn.key) }.onSuccess { latch.focus.countHidden() }
    }

    companion object {
        /** Whether the user has granted Latch notification access in system settings. */
        fun allowed(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
}
