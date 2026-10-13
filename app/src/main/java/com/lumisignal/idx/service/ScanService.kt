package com.lumisignal.idx.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lumisignal.idx.Graph
import com.lumisignal.idx.engine.ScanManager
import com.lumisignal.idx.engine.Scanner
import com.lumisignal.idx.engine.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Keeps the process alive while a screening runs, with a progress notification. */
class ScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Graph.init(applicationContext)
        val strategy = Strategy.byId(intent?.getIntExtra(EXTRA_STRATEGY, 1) ?: 1)
        val notif = Notifier.scanProgress(this, "Screening ${strategy.title}", "Menyiapkan…", 0, 0)
        try {
            ServiceCompat.startForeground(this, Notifier.SCAN_NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: Exception) {
            // Foreground start can be refused (e.g. background start restrictions); run anyway.
        }
        if (scanJob?.isActive == true) return START_NOT_STICKY

        val progressJob = scope.launch {
            ScanManager.state.collectLatest { st ->
                if (st.running && Notifier.canNotify(this@ScanService)) {
                    val n = Notifier.scanProgress(
                        this@ScanService, "Screening ${strategy.title}", "${st.phase} · ${st.message}", st.done, st.total,
                    )
                    try {
                        NotificationManagerCompat.from(this@ScanService).notify(Notifier.SCAN_NOTIF_ID, n)
                    } catch (e: SecurityException) {
                    }
                }
            }
        }
        scanJob = scope.launch {
            try {
                val ids = Scanner.run(strategy)
                Notifier.scanDone(
                    this@ScanService,
                    "Screening selesai · ${strategy.title}",
                    if (ids.isEmpty()) "Tidak ada saham yang memenuhi semua syarat hari ini."
                    else "${ids.size} saham terbaik: ${ids.joinToString(", ") { id -> id.split("-").getOrNull(1) ?: id }}",
                )
                try {
                    // Check fresh signals right away: during the session today's high/low may already hit TP/SL.
                    Monitor.runOnce(applicationContext, forceDaily = true)
                } catch (e: Exception) {
                }
            } catch (e: Exception) {
                Notifier.scanDone(this@ScanService, "Screening gagal", e.message ?: "Terjadi kesalahan")
            } finally {
                progressJob.cancel()
                ServiceCompat.stopForeground(this@ScanService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_STRATEGY = "strategy"

        fun start(ctx: Context, strategy: Strategy) {
            if (ScanManager.state.value.running) return
            ScanManager.update { it.copy(running = true, strategyId = strategy.id, phase = "Memulai", error = null, resultIds = emptyList()) }
            val i = Intent(ctx, ScanService::class.java).putExtra(EXTRA_STRATEGY, strategy.id)
            ContextCompat.startForegroundService(ctx, i)
        }
    }
}
