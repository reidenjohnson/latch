package com.latch.focus.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.latch.focus.latch

/**
 * One alarm at a time, set for the next moment something should happen (a timer ends, a schedule starts or ends).
 * When it fires, the engine ticks. Latch doesn't ask for exact-alarm access (one less permission), so Android may
 * deliver it a few minutes late. That's fine: the blocker also ticks every time an app opens, so a schedule is
 * enforced the moment you open anything, and a late alarm never lets a blocked app through.
 */
object Alarms {
    fun schedule(context: Context, at: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) { am.cancel(pi); return }
        runCatching {
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun exactAllowed(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.latch.tick()
    }
}

/** After a restart, re-arm the alarm and catch up (a schedule may have started while the phone was off). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.latch.tick()
        }
    }
}
