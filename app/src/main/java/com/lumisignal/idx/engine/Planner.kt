package com.lumisignal.idx.engine

import com.lumisignal.idx.data.Bar
import com.lumisignal.idx.data.TradePlan
import kotlin.math.max
import kotlin.math.min

/**
 * Builds the trade plan: one entry area, one stop loss, one take profit.
 * The stop loss sits below the price structure that proves the idea wrong (signal-day low,
 * breakout level, the bandar's average cost, the day's VWAP) — not a fixed risk:reward multiple.
 * A cap only stops a structural level that is unreasonably far away.
 */
object Planner {

    private data class Level(val price: Double, val reason: String)

    fun plan(s: Strategy, d: StockData): TradePlan {
        val bars = d.bars
        val t = bars.last()
        val n = bars.size
        val prior10 = bars.subList(max(0, n - 11), n - 1)
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
                entryLow = Market.minusTicks(c, 1)
                entryHigh = Market.plusTicks(c, 1)
                cancel = Market.floorTick(c * 0.97)
                cancelReason = "Batal jika harga pembukaan turun >3% dari close"
                candidates += Level(Market.minusTicks(t.low), "Di bawah low hari sinyal ${Fmt.price(t.low)} — jika jebol, dorongan bandar hari itu gagal")
                val top = d.day?.buyers?.firstOrNull()
                if (top != null && top.avgPrice > 0 && top.avgPrice < c) {
                    candidates += Level(Market.minusTicks(top.avgPrice), "Di bawah harga rata-rata beli ${top.code} hari itu (${Fmt.price(top.avgPrice)})")
                }
                candidates += Level(Market.minusTicks(swingLow5), "Di bawah low 5 hari terakhir (${Fmt.price(swingLow5)})")
            }
            Strategy.BREAKOUT -> {
                val ceiling = Market.highestHigh(prior10)
                entryLow = Market.ceilTick(min(ceiling, c))
                entryHigh = c
                cancel = Market.floorTick(ceiling)
                cancelReason = "Batal jika harga pembukaan kembali di bawah level breakout ${Fmt.price(ceiling)}"
                candidates += Level(Market.minusTicks(ceiling, 2), "Di bawah level breakout ${Fmt.price(ceiling)} — breakout gagal jika harga kembali ke bawahnya")
                candidates += Level(Market.minusTicks(t.low), "Di bawah low hari breakout (${Fmt.price(t.low)})")
            }
            Strategy.ARA_HUNTER -> {
                val atAra = Market.isAra(t, bars.getOrNull(n - 2))
                entryLow = c
                entryHigh = Market.floorTick(c * 1.04)
                cancel = if (atAra) c else Market.floorTick(c * 0.98)
                cancelReason = if (atAra) "Batal jika harga pembukaan di bawah close ARA (antrean beli ditarik)"
                else "Batal jika harga pembukaan turun >2% dari close"
                maxRisk = 0.10
                if (t.average > 0 && t.average < c) {
                    candidates += Level(Market.minusTicks(t.average), "Di bawah harga rata-rata transaksi hari sinyal (${Fmt.price(t.average)})")
                }
                val top = d.day?.buyers?.firstOrNull()
                if (top != null && top.avgPrice > 0 && top.avgPrice < c) {
                    candidates += Level(Market.minusTicks(top.avgPrice), "Di bawah modal ${top.code} hari sinyal (${Fmt.price(top.avgPrice)})")
                }
                candidates += Level(Market.minusTicks(t.open), "Di bawah harga pembukaan hari sinyal (${Fmt.price(t.open)})")
            }
            Strategy.PANTULAN -> {
                val avg = StrategyEngine.bandarAvg(d.range20)
                entryLow = Market.floorTick(min(c, if (avg > 0) avg else c))
                entryHigh = Market.ceilTick(max(c, if (avg > 0) avg else c))
                cancel = Market.floorTick(t.low)
                cancelReason = "Batal jika harga pembukaan di bawah low candle pembalikan (${Fmt.price(t.low)})"
                maxRisk = 0.07
                if (avg > 0) {
                    candidates += Level(Market.floorTick(avg * 0.96), "4% di bawah modal bandar ${Fmt.price(avg)} — area yang seharusnya dijaga bandar")
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
        val minDist = max(0.025, 2 * tk / ref)
        val valid = candidates.filter { it.price > 0 && it.price < ref }
        val inRange = valid.filter { (ref - it.price) / ref in minDist..maxRisk }
        val sl: Level = when {
            inRange.isNotEmpty() -> inRange.maxBy { it.price }
            valid.isNotEmpty() && valid.all { (ref - it.price) / ref < minDist } -> {
                val lowest = valid.minBy { it.price }
                Level(Market.floorTick(ref * (1 - minDist)), lowest.reason + " — diberi ruang minimal ${Fmt.num(minDist * 100)}% agar tidak kena noise")
            }
            else -> Level(
                Market.floorTick(ref * (1 - maxRisk)),
                "Level struktur terlalu jauh, dibatasi kerugian maksimal ${Fmt.num(maxRisk * 100, 0)}% dari harga beli",
            )
        }

        val tp = target(s, bars, ref)
        return TradePlan(
            entryLow = entryLow,
            entryHigh = entryHigh,
            cancelBelow = cancel,
            cancelReason = cancelReason,
            stopLoss = sl.price,
            slReason = sl.reason,
            tp = tp.price,
            tpReason = tp.reason,
            maxHoldDays = if (s == Strategy.PANTULAN) 10 else 7,
        )
    }

    /** Single target: the nearest resistance (a previous peak) when it is reachable, else an ATR projection. */
    private fun target(s: Strategy, bars: List<Bar>, ref: Double): Level {
        val atr = max(Market.atr(bars.takeLast(15)), Market.tick(ref))
        val minGain = when (s) {
            Strategy.ARA_HUNTER, Strategy.GORENGAN_BANGUN -> 0.06
            Strategy.BREAKOUT -> 0.05
            else -> 0.04
        }
        val floor = ref * (1 + minGain)
        val resistance = Market.swingHighs(bars.takeLast(60), 2)
            .filter { it > floor }
            .minOrNull()
        if (resistance != null && resistance - ref <= 3.5 * atr) {
            return Level(
                Market.floorTick(resistance - Market.tick(resistance)),
                "Tepat di bawah resistance ${Fmt.price(resistance)} (puncak sebelumnya) — titik jual yang realistis",
            )
        }
        val v = max(floor, ref + 1.5 * atr)
        return Level(
            Market.ceilTick(v),
            "Proyeksi 1,5x ATR (rata-rata gerak harian ${Fmt.price(atr)}) karena belum ada resistance dekat",
        )
    }
}
