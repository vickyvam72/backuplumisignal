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

    /** One monitoring pass. Returns the number of signals whose status changed. */
    suspend fun runOnce(ctx: Context, forceDaily: Boolean = false): Int {
        val open = Graph.history.items.value.filter { it.isOpen }
        if (open.isEmpty() || Graph.session.access == null) return 0
        Graph.api.refreshIfNeeded()

        val symbols = open.map { it.symbol }.distinct()
        val now = Market.now()
        val today = Market.today()
        val quotes = mutableMapOf<String, Double>()
        val daily = mutableMapOf<String, List<Bar>>()

        if (Market.marketOpen(now)) {
            for (sym in symbols) {
                try {
                    Graph.api.quote(sym).price?.let { quotes[sym] = it }
                } catch (e: AuthExpiredException) {
                    throw e
                } catch (e: Exception) {
                }
            }
        }
        val needDaily = forceDaily || !Market.sessionInProgress(now) || open.any { it.trackBars.isEmpty() && it.refDate < today }
        val dailyKey = today + if (Market.sessionInProgress(now)) "-s" else "-c"
        if (needDaily && (forceDaily || Graph.cache.lastDailyKey != dailyKey)) {
            for (sym in symbols) {
                try {
                    daily[sym] = Market.completedOnly(Graph.api.historyPage(sym, 1).first)
                } catch (e: AuthExpiredException) {
                    throw e
                } catch (e: Exception) {
                }
            }
            Graph.cache.lastDailyKey = dailyKey
        }

        var changed = 0
        val updated = mutableListOf<Signal>()
        for (sig in open) {
            var s = sig
            daily[sig.symbol]?.let { bars ->
                val start = sig.startDate ?: sig.refDate
                val merged = (s.trackBars + bars.filter { it.date > start })
                    .associateBy { it.date }.values.sortedBy { it.date }
                s = s.copy(trackBars = merged)
                val liveDate = s.live?.date
                if (liveDate != null && merged.any { it.date >= liveDate }) s = s.copy(live = null)
            }
            quotes[sig.symbol]?.let { px ->
                val lv = s.live
                s = if (lv != null && lv.date == today) {
                    s.copy(live = lv.copy(high = maxOf(lv.high, px), low = minOf(lv.low, px), last = px, at = System.currentTimeMillis()))
                } else {
                    s.copy(live = LivePoint(today, px, px, px, px, System.currentTimeMillis()))
                }
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
