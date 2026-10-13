package com.lumisignal.idx.engine

import com.lumisignal.idx.data.Bar
import com.lumisignal.idx.data.TradePlan
import kotlin.math.max
import kotlin.math.min

/**
 * Builds the trade plan. Stop losses come from price structure (the level that would prove the
 * idea wrong — signal-day low, breakout level, the bandar's average cost, the day's VWAP), not
 * from a fixed risk:reward multiple. A cap only stops a structural level that is absurdly far away.
 */
object Planner {

    private data class Level(val price: Double, val reason: String)

    fun plan(s: Strategy, d: StockData): TradePlan {
        val bars = d.bars
        val t = bars.last()
        val n = bars.size
        val prior = bars.subList(max(0, n - 21), n - 1)
        val c = t.close
        val tk = Market.tick(c)
        val swingLow5 = Market.lowestLow(bars.takeLast(5))

        var entryLow = c
        var entryHigh = c
        var cancel: Double? = null
        var cancelReason = ""
        val candidates = mutableListOf<Level>()
        var maxRisk = 0.08

        when (s) {
            Strategy.CLOSING_KUAT -> {
                entryLow = c
                entryHigh = Market.plusTicks(c, 1)
                cancel = Market.floorTick(c * 0.98)
                cancelReason = "Batal jika harga pembukaan turun >2% dari close"
                candidates += Level(Market.minusTicks(t.low), "Di bawah low hari sinyal ${Fmt.price(t.low)} — jika jebol, dorongan bandar hari itu gagal")
                val avg = d.day?.buyers?.firstOrNull()?.avgPrice ?: 0.0
                if (avg > 0 && avg < c) {
                    candidates += Level(Market.minusTicks(avg), "Di bawah harga rata-rata beli ${d.day?.buyers?.first()?.code} hari itu (${Fmt.price(avg)})")
                }
                candidates += Level(Market.minusTicks(swingLow5), "Di bawah low 5 hari terakhir (${Fmt.price(swingLow5)})")
            }
            Strategy.BREAKOUT -> {
                val high20 = Market.highestHigh(prior)
                entryLow = Market.ceilTick(min(high20, c))
                entryHigh = c
                cancel = Market.floorTick(high20)
                cancelReason = "Batal jika harga pembukaan kembali di bawah level breakout ${Fmt.price(high20)}"
                candidates += Level(Market.minusTicks(high20, 2), "Di bawah level breakout ${Fmt.price(high20)} — breakout gagal jika harga kembali ke bawahnya")
                candidates += Level(Market.minusTicks(t.low), "Di bawah low hari breakout (${Fmt.price(t.low)})")
            }
            Strategy.ARA_SEHAT -> {
                entryLow = c
                entryHigh = Market.floorTick(c * 1.05)
                cancel = c
                cancelReason = "Batal jika harga pembukaan di bawah close ARA (antrean beli ditarik)"
                maxRisk = 0.10
                if (t.average > 0 && t.average < c) {
                    candidates += Level(Market.minusTicks(t.average), "Di bawah harga rata-rata transaksi hari ARA (${Fmt.price(t.average)})")
                }
                val avg = d.day?.buyers?.firstOrNull()?.avgPrice ?: 0.0
                if (avg > 0 && avg < c) {
                    candidates += Level(Market.minusTicks(avg), "Di bawah modal ${d.day?.buyers?.first()?.code} hari ARA (${Fmt.price(avg)})")
                }
                candidates += Level(Market.minusTicks(t.open), "Di bawah harga pembukaan hari ARA (${Fmt.price(t.open)})")
            }
            Strategy.PANTULAN -> {
                val avg = StrategyEngine.bandarAvg(d.range20)
                entryLow = Market.floorTick(min(c, if (avg > 0) avg else c))
                entryHigh = Market.ceilTick(max(c, if (avg > 0) avg else c))
                cancel = Market.floorTick(t.low)
                cancelReason = "Batal jika harga pembukaan di bawah low candle pembalikan (${Fmt.price(t.low)})"
                maxRisk = 0.07
                if (avg > 0) {
                    candidates += Level(Market.floorTick(avg * 0.97), "3% di bawah modal bandar ${Fmt.price(avg)} — area yang seharusnya dijaga bandar")
                }
                candidates += Level(Market.minusTicks(swingLow5), "Di bawah low koreksi 5 hari (${Fmt.price(swingLow5)})")
            }
            Strategy.GORENGAN_BANGUN -> {
                val avg = StrategyEngine.bandarAvg(d.range20)
                entryLow = Market.minusTicks(c, 1)
                entryHigh = c
                cancel = Market.floorTick(c * 0.97)
                cancelReason = "Batal jika harga pembukaan turun >3% dari close"
                maxRisk = 0.10
                candidates += Level(Market.minusTicks(t.low), "Di bawah low hari pemicu (${Fmt.price(t.low)})")
                if (avg > 0 && avg < c) {
                    candidates += Level(Market.minusTicks(avg), "Di bawah modal bandar 20 hari (${Fmt.price(avg)})")
                }
            }
        }
        if (entryLow > entryHigh) {
            val tmp = entryLow
            entryLow = entryHigh
            entryHigh = tmp
        }

        val ref = entryHigh
        val minDist = max(0.02, 2 * tk / ref)
        val valid = candidates.filter { it.price > 0 && it.price < ref }
        val inRange = valid.filter { (ref - it.price) / ref in minDist..maxRisk }
        val sl: Level = when {
            inRange.isNotEmpty() -> inRange.maxBy { it.price }
            valid.isNotEmpty() && valid.all { (ref - it.price) / ref < minDist } -> {
                // Every structural level is too tight to survive normal noise: give it room.
                val lowest = valid.minBy { it.price }
                Level(Market.floorTick(ref * (1 - minDist)), lowest.reason + " — diberi ruang minimal ${Fmt.num(minDist * 100)}% agar tidak kena noise")
            }
            else -> Level(
                Market.floorTick(ref * (1 - maxRisk)),
                "Level struktur terlalu jauh, dibatasi kerugian maksimal ${Fmt.num(maxRisk * 100, 0)}% dari harga beli",
            )
        }

        val (tp1, tp2) = targets(s, bars, ref)
        return TradePlan(
            entryLow = entryLow,
            entryHigh = entryHigh,
            cancelBelow = cancel,
            cancelReason = cancelReason,
            stopLoss = sl.price,
            slReason = sl.reason,
            tp1 = tp1.price,
            tp1Reason = tp1.reason,
            tp2 = tp2.price,
            tp2Reason = tp2.reason,
            maxHoldDays = if (s == Strategy.PANTULAN) 10 else 7,
        )
    }

    /** Targets from the nearest resistances (swing highs), with ATR as a fallback. */
    private fun targets(s: Strategy, bars: List<Bar>, ref: Double): Pair<Level, Level> {
        val atr = max(Market.atr(bars.takeLast(15)), Market.tick(ref))
        val minGain = if (s == Strategy.ARA_SEHAT || s == Strategy.GORENGAN_BANGUN) 0.06 else 0.03
        val floor1 = ref * (1 + minGain)
        val window = bars.takeLast(60)
        val resistances = Market.swingHighs(window, 2)
            .filter { it > floor1 }
            .sorted()
            .fold(mutableListOf<Double>()) { acc, h ->
                if (acc.isEmpty() || h > acc.last() * 1.015) acc.add(h)
                acc
            }

        val tp1: Level = run {
            val r = resistances.firstOrNull()
            if (r != null && r - ref <= 3.5 * atr) {
                Level(Market.floorTick(r - Market.tick(r)), "Tepat di bawah resistance ${Fmt.price(r)} (puncak sebelumnya)")
            } else {
                val v = max(floor1, ref + 1.5 * atr)
                Level(Market.ceilTick(v), "Proyeksi 1,5x ATR (rata-rata gerak harian ${Fmt.price(atr)}) — belum ada resistance dekat")
            }
        }
        val tp2: Level = run {
            val r = resistances.firstOrNull { it > tp1.price * 1.02 }
            if (r != null && r - ref <= 6 * atr) {
                Level(Market.floorTick(r - Market.tick(r)), "Di bawah resistance berikutnya ${Fmt.price(r)}")
            } else {
                val hh = Market.highestHigh(window)
                if (hh > tp1.price * 1.02 && hh - ref <= 6 * atr) {
                    Level(Market.floorTick(hh - Market.tick(hh)), "Di bawah puncak tertinggi 60 hari ${Fmt.price(hh)}")
                } else {
                    Level(Market.ceilTick(max(tp1.price * 1.03, ref + 3 * atr)), "Proyeksi 3x ATR dari harga beli")
                }
            }
        }
        return tp1 to tp2
    }
}
