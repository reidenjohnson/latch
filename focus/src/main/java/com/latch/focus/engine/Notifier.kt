package com.latch.focus.engine

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.latch.focus.MainActivity
import com.latch.focus.R
import com.latch.focus.data.Active

/** The quiet "In Focus" notification with a live timer. Shown only if notifications are allowed. */
class Notifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun update(active: Active?) {
        if (active == null) { manager.cancel(ID); return }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Focus session", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while a session is running"
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val ends = active.endsAt
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_latch)
            .setContentTitle("${active.modeName} is on")
            .setContentText(if (ends != null) "Ends on its own, or tap your Latch." else "Tap your Latch to end it.")
            .setWhen(ends ?: active.start)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(ends != null)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(open)
            .build()
        runCatching { manager.notify(ID, n) }
    }

    private companion object {
        const val CHANNEL = "session"
        const val ID = 1
    }
}
