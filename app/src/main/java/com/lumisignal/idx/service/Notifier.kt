package com.lumisignal.idx.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lumisignal.idx.MainActivity
import com.lumisignal.idx.R
import com.lumisignal.idx.data.Signal
import com.lumisignal.idx.data.SignalStatus
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.Tracker

object Notifier {
    const val CH_SIGNALS = "lumi_signals"
    const val CH_SCAN = "lumi_scan"
    const val SCAN_NOTIF_ID = 4101
    const val EXTRA_OPEN_SIGNAL = "open_signal"
    const val EXTRA_OPEN_SCAN = "open_scan"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_SIGNALS, "Sinyal & pemantauan", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Radar Besok terbit, dan saat harga posisi menyentuh entry, TP, atau SL"
            },
        )
        // The old foreground "screening" channel is gone: screening now runs on the server.
        nm.deleteNotificationChannel(CH_SCAN)
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openIntent(ctx: Context, key: String, value: String, req: Int): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(key, value)
        }
        return PendingIntent.getActivity(ctx, req, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun radarReady(ctx: Context, title: String, text: String) {
        if (!canNotify(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_SIGNALS)
            .setSmallIcon(R.drawable.ic_stat_lumi)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFF3D8BFF.toInt())
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, EXTRA_OPEN_SCAN, "1", 2))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(SCAN_NOTIF_ID + 1, n)
        } catch (e: SecurityException) {
        }
    }

    fun statusChanged(ctx: Context, sig: Signal) {
        if (!canNotify(ctx)) return
        val porto = if (sig.source == "PORTO") " · Porto" else ""
        val emoji = when (sig.status) {
            SignalStatus.TP -> "🎯"
            SignalStatus.SL -> "🛑"
            SignalStatus.ACTIVE -> "✅"
            SignalStatus.CANCELLED -> "✖️"
            else -> "🔔"
        }
        val title = "$emoji ${sig.symbol} — ${Tracker.label(sig.status)}$porto"
        val text = "${sig.statusNote}. Harga terakhir ${Fmt.price(sig.lastPrice)}."
        val n = NotificationCompat.Builder(ctx, CH_SIGNALS)
            .setSmallIcon(R.drawable.ic_stat_lumi)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFF3D8BFF.toInt())
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, EXTRA_OPEN_SIGNAL, sig.id, sig.id.hashCode()))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(sig.id.hashCode(), n)
        } catch (e: SecurityException) {
        }
    }

    fun loginNeeded(ctx: Context) {
        if (!canNotify(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_SIGNALS)
            .setSmallIcon(R.drawable.ic_stat_lumi)
            .setContentTitle("Sesi Stockbit berakhir")
            .setContentText("Buka Lumi Signal dan login ulang agar pemantauan sinyal tetap berjalan.")
            .setColor(0xFF3D8BFF.toInt())
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, EXTRA_OPEN_SCAN, "0", 3))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(9001, n)
        } catch (e: SecurityException) {
        }
    }
}
