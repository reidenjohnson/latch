package com.latch.focus

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.latch.MainActivity
import com.latch.R

/** The "In Focus" notification with a running timer. Optional: it only shows if notifications are allowed. */
class FocusNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(active: ActiveFocus) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Focus", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while Focus is on"
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_FOCUS, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_focus)
            .setContentTitle("In Focus")
            .setContentText("${active.blocked.size} apps blocked. Tap your tag to end.")
            .setWhen(active.start)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(open)
            .build()
        runCatching { manager.notify(ID, n) }
    }

    fun cancel() = manager.cancel(ID)

    private companion object {
        const val CHANNEL = "focus"
        const val ID = 1
    }
}
