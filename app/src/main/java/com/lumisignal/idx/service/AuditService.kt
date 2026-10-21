package com.lumisignal.idx.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lumisignal.idx.Graph
import com.lumisignal.idx.MainActivity
import com.lumisignal.idx.R
import com.lumisignal.idx.data.AuthExpiredException
import com.lumisignal.idx.engine.Market
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Price audit every minute during the trading session, also with the app closed: a foreground
 * service with a quiet ongoing notification. It stops by itself after the session ends and is started
 * again by the 08:50 alarm, the 15-minute worker, or opening the app.
 */
class AuditService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Graph.init(applicationContext)
        Notifier.createChannels(this)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, notification("Menyiapkan audit harga…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notification("Menyiapkan audit harga…"))
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) loop = scope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        val fmt = SimpleDateFormat("HH:mm", Locale("id", "ID"))
        while (scope.isActive && shouldRun()) {
            try {
                Monitor.runOnce(applicationContext)
            } catch (e: AuthExpiredException) {
                val today = Market.today()
                if (Graph.cache.loginNotifiedDay != today) {
                    Graph.cache.loginNotifiedDay = today
                    Notifier.loginNeeded(applicationContext)
                }
                break
            } catch (e: Exception) {
            }
            val open = Graph.history.items.value.count { it.isOpen }
            update("$open saham dipantau · terakhir dicek ${fmt.format(Date())} WIB")
            delay(60_000)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 9001, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifier.CH_AUDIT)
            .setSmallIcon(R.drawable.ic_stat_lumi)
            .setContentTitle("Audit harga berjalan · tiap 1 menit")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setColor(0xFF3D8BFF.toInt())
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setShowWhen(false)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun update(text: String) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, notification(text))
        } catch (e: SecurityException) {
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 4201

        /** Trading session, logged in, and something to audit. */
        fun shouldRun(): Boolean =
            Market.sessionInProgress() && Graph.session.access != null && Graph.history.items.value.any { it.isOpen }

        fun start(ctx: Context) {
            try {
                Graph.init(ctx.applicationContext)
                if (!shouldRun()) return
                ContextCompat.startForegroundService(ctx, Intent(ctx, AuditService::class.java))
            } catch (e: Exception) {
                // Android may refuse a start from the background; the 15-minute worker keeps auditing.
            }
        }
    }
}
