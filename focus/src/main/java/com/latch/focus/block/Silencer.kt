package com.latch.focus.block

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.latch.focus.latch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * During a session whose mode has "Hide notifications" on, clears notifications from blocked apps. Only clearable
 * ones are touched, so calls, navigation and media controls stay. The messages are still in the app afterwards.
 * https://developer.android.com/reference/android/service/notification/NotificationListenerService
 */
class Silencer : NotificationListenerService() {
    private var scope: CoroutineScope? = null

    override fun onListenerConnected() {
        Grants.granted(this, Grants.Kind.Notifications)
        // A session just started: clear what's already waiting, too.
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { s ->
            s.launch { latch.state.distinctUntilChangedBy { it.active?.start }.collect { sweep() } }
        }
    }

    override fun onListenerDisconnected() {
        scope?.cancel()
        scope = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = hide(sbn)

    private fun sweep() {
        runCatching { activeNotifications }.getOrNull()?.forEach(::hide)
    }

    private fun hide(sbn: StatusBarNotification) {
        val active = latch.state.value.active ?: return
        if (!active.silence || !sbn.isClearable || sbn.packageName !in active.blocked) return
        runCatching { cancelNotification(sbn.key) }.onSuccess { latch.countHidden() }
    }

    companion object {
        fun allowed(context: Context): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
}
