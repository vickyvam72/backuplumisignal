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
import kotlinx.coroutines.sync.withLock

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
        SignalAlarm.schedule(ctx)
    }

    /** True when the 15-minute background audit is registered with the system. */
    fun isScheduled(ctx: Context): Boolean = try {
        WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(WORK_NAME).get()
            .any { !it.state.isFinished }
    } catch (e: Exception) {
        false
    }

    fun runNow(ctx: Context) {
        try {
            WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<MonitorWorker>().build())
        } catch (e: Exception) {
        }
    }

    /**
     * One monitoring pass. During the session it reads today's running daily bar (real open/high/low
     * so far), so a TP or SL touched between two checks is never missed. After the close it reads
     * the final bars once. Returns the number of signals whose status changed.
     */
    private val runLock = kotlinx.coroutines.sync.Mutex()

    /** One pass at a time (app, worker, alarm and the audit service can overlap). */
    suspend fun runOnce(ctx: Context, forceDaily: Boolean = false): Int = runLock.withLock {
        val n = runPass(ctx, forceDaily)
        dailyRecap(ctx)
        n
    }

    /**
     * Once per trading day after the close (from 16:20 WIB, after the final prices are in): holds, TP,
     * SL and cumulative P/L of the strategy history and of the portfolio.
     */
    private fun dailyRecap(ctx: Context) {
        try {
            val now = Market.now()
            val today = Market.today()
            if (!Market.isWeekday(now.toLocalDate()) || now.toLocalTime() < java.time.LocalTime.of(16, 20)) return
            if (Graph.cache.lastRecapDay == today) return
            val items = Graph.history.items.value
            // Only on a day the market traded (holidays have no bar for today).
            val traded = RadarRepo.day.value?.date == today || items.any { s -> s.trackBars.any { it.date == today } }
            if (!traded) return
            // Final prices of today must be in (the post-close fetch has run) unless nothing is open.
            if (items.any { it.isOpen } && Graph.cache.lastDailyKey != "$today-c") return
            Graph.cache.lastRecapDay = today
            val tier = com.lumisignal.idx.UiConfig.FIXED_TIER
            val strat = items.filter { it.source == com.lumisignal.idx.data.SOURCE_STRATEGY && it.radar?.tier == tier && it.radar?.combined == true }
            val porto = items.filter { it.source == com.lumisignal.idx.data.SOURCE_PORTO }
            fun line(name: String, list: List<Signal>): String? {
                if (list.isEmpty()) return null
                val st = com.lumisignal.idx.ui.components.winStats(list)
                return "$name: ${st.holding} hold · ${st.tp} TP · ${st.sl} SL · akumulasi P/L ${st.totalPnl?.let { com.lumisignal.idx.engine.Fmt.pct(it, decimals = 2) } ?: "–"}"
            }
            val lines = listOfNotNull(line("Riwayat Strategi", strat), line("Porto", porto))
            if (lines.isEmpty()) return
            Notifier.dailyRecap(ctx, "📊 Rekap pasar ${Market.prettyDate(today)}", lines.joinToString("\n"))
        } catch (e: Exception) {
        }
    }

    private suspend fun runPass(ctx: Context, forceDaily: Boolean): Int {
        checkRadar(ctx)
        if (Graph.session.access == null) return 0
        dailyTokenRefresh()
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
        // A position added after that fetch (never updated yet, or last updated before today) still gets one.
        val allFresh = targets.all { it.lastUpdate > 0 && Market.dayOf(it.lastUpdate) == today }
        if (!inSession && !forceDaily && Graph.cache.lastDailyKey == key && allFresh) return 0

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
            // Latest traded price, also for a position opened today after the bars it is tracked on.
            val lastBar = page?.lastOrNull()
            if (tracked.isOpen && lastBar != null && lastBar.date >= (tracked.entryDate ?: sig.refDate)) {
                tracked = tracked.copy(lastPrice = lastBar.close)
            }
            tracked = tracked.copy(lastUpdate = System.currentTimeMillis())
            if (tracked.status != sig.status || tracked.exitPrice != sig.exitPrice) {
                changed++
                // Notify only when bought (entry, TP & SL announced) and at TP or SL, for history and portfolio alike.
                if (tracked.status != sig.status &&
                    (tracked.status == com.lumisignal.idx.data.SignalStatus.TP ||
                        tracked.status == com.lumisignal.idx.data.SignalStatus.SL || tracked.status == com.lumisignal.idx.data.SignalStatus.ACTIVE)
                ) Notifier.statusChanged(ctx, tracked)
            }
            updated += tracked
        }
        Graph.history.upsert(updated)
        Graph.cache.lastMonitorRun = System.currentTimeMillis()
        return changed
    }

    /**
     * 17:00 WIB: the new buy signals are released. Download the radar (published ±16:30), put every pick
     * into the strategy history (audited with live prices from then on) and notify the user once.
     * Nothing happens before the first screening (connection check) has been done.
     */
    private suspend fun checkRadar(ctx: Context) {
        try {
            val now = Market.now()
            if (!RadarRepo.setupDone) return
            val d = if (RadarRepo.isStale(now)) RadarRepo.refresh() else RadarRepo.day.value ?: return
            if (d.date < RadarRepo.expectedDate(now)) return
            RadarPorto.saveAll(d)
            if (RadarRepo.notifiedDate == d.date) return
            RadarRepo.notifiedDate = d.date
            val tier = RadarRepo.tier.value
            val parts = listOf("profit", "winrate").map { j ->
                val top = d.board(tier, j)?.top5?.filter { it.valid }.orEmpty().joinToString(", ") { it.ticker }
                "${d.jenisLabel(j)}: ${top.ifBlank { "tidak ada sinyal" }}"
            }
            Notifier.radarReady(
                ctx,
                "Sinyal beli untuk ${Market.prettyDate(RadarPorto.nextSession(d.date))}",
                parts.joinToString(" · ") + ". Sudah masuk Riwayat Strategi.",
            )
        } catch (e: Exception) {
        }
    }

    /** Renews the Stockbit token once a day (refresh token), even when nothing is being tracked. */
    private suspend fun dailyTokenRefresh() {
        val today = Market.today()
        if (Graph.cache.lastTokenRefresh == today) return
        try {
            if (Graph.api.refreshNow()) Graph.cache.lastTokenRefresh = today
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
            AuditService.start(applicationContext)
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
