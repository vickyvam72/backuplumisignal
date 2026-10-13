package com.lumisignal.idx.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lumisignal.idx.Graph
import com.lumisignal.idx.data.AuthExpiredException
import com.lumisignal.idx.data.Bar
import com.lumisignal.idx.data.LivePoint
import com.lumisignal.idx.data.Signal
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.Tracker
import java.util.concurrent.TimeUnit

/**
 * Background price monitor for every open signal (strategy history and portfolio): live quotes
 * during trading hours, daily bars after the close, then a status replay and a notification
 * whenever entry, TP or SL is touched.
 */
object Monitor {
    private const val WORK_NAME = "lumi_monitor"

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<MonitorWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun runNow(ctx: Context) {
        WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<MonitorWorker>().build())
    }

    /**
     * One monitoring pass. During the session it reads today's running daily bar (real open/high/low
     * so far), so a TP or SL touched between two checks is never missed. After the close it reads
     * the final bars once. Returns the number of signals whose status changed.
     */
    suspend fun runOnce(ctx: Context, forceDaily: Boolean = false): Int {
        val open = Graph.history.items.value.filter { it.isOpen }
        if (open.isEmpty() || Graph.session.access == null) return 0
        Graph.api.refreshIfNeeded()

        val now = Market.now()
        val today = Market.today()
        val inSession = Market.sessionInProgress(now)
        val key = today + if (inSession) "-s" else "-c"
        // Outside trading hours one fetch per day is enough; during the session fetch every pass.
        if (!inSession && !forceDaily && Graph.cache.lastDailyKey == key) return 0

        val symbols = open.map { it.symbol }.distinct()
        val pages = mutableMapOf<String, List<Bar>>()
        for (sym in symbols) {
            try {
                pages[sym] = Graph.api.historyPage(sym, 1).first
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: Exception) {
            }
        }
        Graph.cache.lastDailyKey = key

        var changed = 0
        val updated = mutableListOf<Signal>()
        for (sig in open) {
            var s = sig
            val page = pages[sig.symbol]
            if (page != null) {
                val start = sig.startDate ?: sig.refDate
                val completed = Market.completedOnly(page, now).filter { it.date > start }
                val merged = (s.trackBars + completed).associateBy { it.date }.values.sortedBy { it.date }
                val running = page.lastOrNull()?.takeIf { inSession && it.date == today && it.date > start }
                s = s.copy(
                    trackBars = merged,
                    live = running?.let { LivePoint(it.date, it.open, it.high, it.low, it.close, System.currentTimeMillis()) },
                )
            }
            val tracked = Tracker.track(s).copy(lastUpdate = System.currentTimeMillis())
            if (tracked.status != sig.status) {
                changed++
                Notifier.statusChanged(ctx, tracked)
            }
            updated += tracked
        }
        Graph.history.upsert(updated)
        Graph.cache.lastMonitorRun = System.currentTimeMillis()
        return changed
    }

    /** Runs a pass in the app's foreground at most every few minutes (no button needed). */
    suspend fun refreshIfStale(ctx: Context, minGapMs: Long = 3 * 60 * 1000L) {
        if (System.currentTimeMillis() - Graph.cache.lastMonitorRun < minGapMs) return
        try {
            runOnce(ctx)
        } catch (e: Exception) {
        }
    }
}

class MonitorWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        Graph.init(applicationContext)
        return try {
            Monitor.runOnce(applicationContext)
            Result.success()
        } catch (e: AuthExpiredException) {
            val today = Market.today()
            if (Graph.cache.loginNotifiedDay != today) {
                Graph.cache.loginNotifiedDay = today
                Notifier.loginNeeded(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
