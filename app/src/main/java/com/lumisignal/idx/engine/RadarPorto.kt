package com.lumisignal.idx.engine

import com.lumisignal.idx.Graph
import com.lumisignal.idx.data.Check
import com.lumisignal.idx.data.RadarDay
import com.lumisignal.idx.data.RadarPick
import com.lumisignal.idx.data.RadarTag
import com.lumisignal.idx.data.SOURCE_PORTO
import com.lumisignal.idx.data.Signal
import com.lumisignal.idx.data.SignalStatus
import com.lumisignal.idx.data.StrategyEval
import com.lumisignal.idx.data.TradePlan

/** Radar picks → portfolio positions (tracked on the phone with live Stockbit prices). */
object RadarPorto {

    fun key(date: String, tier: String, jenis: String, sid: Int, ticker: String, combined: Boolean) =
        "R-$date-$tier-$jenis-${if (combined) "G" else "S"}$sid-$ticker"

    /** Existing portfolio entry made from this pick, if any. */
    fun find(date: String, tier: String, jenis: String, sid: Int, ticker: String, combined: Boolean): Signal? {
        val k = key(date, tier, jenis, sid, ticker, combined)
        return Graph.history.items.value.firstOrNull { it.source == SOURCE_PORTO && it.linkedId == k }
    }

    fun plan(day: RadarDay, tier: String, jenis: String, sid: Int, pick: RadarPick): TradePlan {
        val spec = day.spec(tier, jenis, sid)
        val top = pick.entryHigh
        return TradePlan(
            entryLow = top,
            entryHigh = top,
            cancelBelow = null,
            cancelReason = "Order berlaku 1 sesi: beli di harga pembukaan bila ≤ ${Fmt.price(top)}, kalau dibuka lebih tinggi pasang antrean beli di ${Fmt.price(top)}. Tidak terisi = batal.",
            stopLoss = pick.sl ?: 0.0,
            slReason = slReason(spec?.exitText ?: ""),
            tp = pick.tp ?: 0.0,
            tpReason = tpReason(spec?.exitText ?: ""),
            maxHoldDays = pick.hold,
            rule = pick.rule,
            entrySessions = 1,
        )
    }

    private fun tpReason(exit: String): String {
        val t = exit.split(",").firstOrNull { it.contains("TP") }?.trim().orEmpty()
        return (if (t.isNotBlank()) "$t. " else "") + "Dihitung ulang dari harga beli sebenarnya, dibulatkan ke atas sesuai fraksi harga."
    }

    private fun slReason(exit: String): String {
        val t = exit.split(",").filter { it.contains("SL") || it.contains("low") || it.contains("maks 3x") }.joinToString(",") { it.trim() }
        return (if (t.isNotBlank()) "$t. " else "") + "Dihitung ulang dari harga beli sebenarnya, dibulatkan ke bawah sesuai fraksi harga."
    }

    fun add(day: RadarDay, tier: String, jenis: String, sid: Int, pick: RadarPick, combined: Boolean, customEntry: Double?): Signal {
        val meta = day.strategy(sid)
        val spec = day.spec(tier, jenis, sid)
        val plan = plan(day, tier, jenis, sid, pick)
        val today = Market.today()
        val start = if (customEntry != null) {
            maxOf(day.date, if (Market.sessionInProgress()) previousIso(today) else today)
        } else null
        val k = key(day.date, tier, jenis, sid, pick.ticker, combined)
        val sig = Signal(
            id = "P-$k-${System.currentTimeMillis()}",
            source = SOURCE_PORTO,
            symbol = pick.ticker,
            name = pick.name,
            strategyId = sid,
            refDate = day.date,
            createdAt = System.currentTimeMillis(),
            close = pick.close,
            changePct = pick.chg,
            eval = StrategyEval(
                strategyId = sid, passed = true, score = 0,
                checks = pick.why.map { Check(it, "", true) },
                highlights = pick.why,
                plan = plan,
            ),
            bars = day.barsOf(pick.ticker),
            linkedId = k,
            customEntry = customEntry,
            startDate = start,
            status = if (customEntry != null) SignalStatus.ACTIVE else SignalStatus.WAITING,
            lastPrice = pick.close,
            radar = RadarTag(
                jenis = jenis, tier = tier, sid = sid, name = meta?.name ?: "Strategi $sid",
                icon = meta?.icon ?: "", color = meta?.color ?: "#3D8BFF", combined = combined,
                why = pick.why, exitText = spec?.exitText ?: "", regText = spec?.regText ?: "", desc = meta?.desc ?: "",
            ),
        )
        val tracked = Tracker.track(sig)
        Graph.history.upsert(listOf(tracked))
        return tracked
    }

    /** Portfolio entry from a manual stock check (plan from support/resistance). */
    fun addManual(symbol: String, name: String, refDate: String, bars: List<com.lumisignal.idx.data.Bar>, plan: TradePlan, why: List<String>, customEntry: Double?): Signal {
        val today = Market.today()
        val start = if (customEntry != null) maxOf(refDate, if (Market.sessionInProgress()) previousIso(today) else today) else null
        val t = bars.last()
        val prev = bars.getOrNull(bars.size - 2)
        val k = "M-$refDate-$symbol"
        val sig = Signal(
            id = "P-$k-${System.currentTimeMillis()}",
            source = SOURCE_PORTO,
            symbol = symbol,
            name = name,
            strategyId = 0,
            refDate = refDate,
            createdAt = System.currentTimeMillis(),
            close = t.close,
            changePct = Market.changePct(t, prev),
            eval = StrategyEval(strategyId = 0, passed = true, score = 0, checks = why.map { Check(it, "", true) }, highlights = why, plan = plan),
            bars = bars.takeLast(60),
            linkedId = k,
            customEntry = customEntry,
            startDate = start,
            status = if (customEntry != null) SignalStatus.ACTIVE else SignalStatus.WAITING,
            lastPrice = t.close,
            radar = Levels.MANUAL_TAG.copy(why = why, exitText = "TP di resistance, SL di bawah support, maks 7 hari"),
        )
        val tracked = Tracker.track(sig)
        Graph.history.upsert(listOf(tracked))
        return tracked
    }

    fun findManual(refDate: String, symbol: String): Signal? =
        Graph.history.items.value.firstOrNull { it.source == SOURCE_PORTO && it.linkedId == "M-$refDate-$symbol" }

    private fun previousIso(iso: String): String = try {
        java.time.LocalDate.parse(iso).minusDays(1).toString()
    } catch (e: Exception) {
        iso
    }

    /** Next weekday after [iso] (the session in which the radar's buy orders run). */
    fun nextSession(iso: String): String = try {
        var d = java.time.LocalDate.parse(iso).plusDays(1)
        while (d.dayOfWeek == java.time.DayOfWeek.SATURDAY || d.dayOfWeek == java.time.DayOfWeek.SUNDAY) d = d.plusDays(1)
        d.toString()
    } catch (e: Exception) {
        iso
    }
}
