package com.lumisignal.idx.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lumisignal.idx.Graph
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.RadarRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek

/**
 * Wakes the app at 17:00 WIB on trading days so the buy signals arrive on time (the 15-minute
 * background audit alone could be up to 15 minutes late). Re-armed after every alarm and after a reboot.
 */
class SignalAlarm : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        schedule(ctx)
        if (intent.action == ACTION_AUDIT) {
            AuditService.start(ctx)
            return
        }
        if (intent.action != ACTION) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Graph.init(ctx)
                withTimeoutOrNull(50_000) { Monitor.runOnce(ctx) }
            } catch (e: Exception) {
            } finally {
                // Backup pass through WorkManager (network constraint, retries).
                Monitor.runNow(ctx)
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION = "com.lumisignal.idx.SIGNAL_1700"
        private const val ACTION_AUDIT = "com.lumisignal.idx.AUDIT_0850"

        /** Next [at] WIB on a weekday, in epoch millis. */
        private fun nextTrigger(at: java.time.LocalTime = RadarRepo.READY_AT): Long {
            val now = Market.now()
            var t = now.toLocalDate().atTime(at).atZone(now.zone)
            if (!t.isAfter(now)) t = t.plusDays(1)
            while (t.dayOfWeek == DayOfWeek.SATURDAY || t.dayOfWeek == DayOfWeek.SUNDAY) t = t.plusDays(1)
            return t.toInstant().toEpochMilli()
        }

        fun schedule(ctx: Context) {
            // 17:00 buy signals, and 08:50 to start the 1-minute price audit for the session.
            arm(ctx, 1700, ACTION, nextTrigger())
            arm(ctx, 850, ACTION_AUDIT, nextTrigger(java.time.LocalTime.of(8, 50)))
        }

        private fun arm(ctx: Context, code: Int, action: String, at: Long) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pi = PendingIntent.getBroadcast(
                ctx, code, Intent(ctx, SignalAlarm::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            try {
                if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }
}
