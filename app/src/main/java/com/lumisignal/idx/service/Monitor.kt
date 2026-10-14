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
import com.lumisignal.idx.engine.RadarRepo
import com.lumisignal.idx.engine.RadarPorto
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
        checkRadar(ctx)
        if (Graph.session.access == null) return 0
        val now = Market.now()
        val today = Market.today()
        // Open signals, plus those closed today: a same-day TP/SL can still be corrected with the
        // minute path while today's intraday data is available.
        val targets = Graph.history.items.value.filter { it.isOpen || it.exitDate == today }
        if (targets.isEmpty()) return 0
        Graph.api.refreshIfNeeded()

        val inSession = Market.sessionInProgress(now)
        val key = today + if (inSession) "-s" else "-c"
        // Outside trading hours one fetch per day is enough; during the session fetch every pass.
        if (!inSession && !forceDaily && Graph.cache.lastDailyKey == key) return 0

        val pages = mutableMapOf<String, List<Bar>>()
        for (sym in targets.map { it.symbol }.distinct()) {
            try {
                pages[sym] = Graph.api.historyPage(sym, 1).first
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: Exception) {
            }
        }
        Graph.cache.lastDailyKey = key

        val paths = mutableMapOf<String, List<Double>?>()
        suspend fun pathFor(sym: String): List<Double>? = paths.getOrPut(sym) {
            try {
                Graph.api.intradayPath(sym).takeIf { it.size >= 2 }
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }

        var changed = 0
        val updated = mutableListOf<Signal>()
        for (sig in targets) {
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
            var tracked = Tracker.track(s)
            // Today's bar touched both SL and TP: settle the order with today's minute path.
            val todayBar = page?.lastOrNull()?.takeIf { it.date == today }
            if (todayBar != null && Tracker.ambiguous(s, todayBar) && today !in s.dayPaths &&
                (tracked.exitDate == today || tracked.isOpen)
            ) {
                val path = pathFor(sig.symbol)
                if (path != null) {
                    s = s.copy(dayPaths = s.dayPaths + (today to path))
                    tracked = Tracker.track(s)
                }
            }
            tracked = tracked.copy(lastUpdate = System.currentTimeMillis())
            if (tracked.status != sig.status || tracked.exitPrice != sig.exitPrice) {
                changed++
                // Portfolio positions notify every change; screening results only when TP or SL is hit.
                if (tracked.status != sig.status &&
                    (sig.source == com.lumisignal.idx.data.SOURCE_PORTO || tracked.status == com.lumisignal.idx.data.SignalStatus.TP || tracked.status == com.lumisignal.idx.data.SignalStatus.SL)
                ) Notifier.statusChanged(ctx, tracked)
            }
            updated += tracked
        }
        Graph.history.upsert(updated)
        Graph.cache.lastMonitorRun = System.currentTimeMillis()
        return changed
    }

    /**
     * After the close the server publishes the new Radar Besok (±16:30 WIB). Download it once it is due
     * and tell the user, so the results are ready before they open the app.
     */
    private suspend fun checkRadar(ctx: Context) {
        try {
            val now = Market.now()
            if (!RadarRepo.isStale(now)) return
            val d = RadarRepo.refresh()
            RadarPorto.saveAll(d)
            if (d.date >= RadarRepo.expectedDate(now) && RadarRepo.notifiedDate != d.date) {
                RadarRepo.notifiedDate = d.date
                val tier = RadarRepo.tier.value
                val parts = listOf("profit", "winrate").map { j ->
                    val top = d.board(tier, j)?.top5?.filter { it.valid }.orEmpty().joinToString(", ") { it.ticker }
                    "${d.jenisLabel(j)}: ${top.ifBlank { "tidak ada sinyal" }}"
                }
                Notifier.radarReady(
                    ctx,
                    "Radar Besok siap · closing ${Market.shortDate(d.date)}",
                    "${com.lumisignal.idx.UiConfig.TOP5_TITLE} — " + parts.joinToString(" · "),
                )
            }
        } catch (e: Exception) {
        }
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
