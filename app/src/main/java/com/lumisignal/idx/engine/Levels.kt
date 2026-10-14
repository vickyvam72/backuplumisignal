package com.lumisignal.idx.engine

import com.lumisignal.idx.data.Bar
import com.lumisignal.idx.data.ExitRule
import com.lumisignal.idx.data.RadarTag
import com.lumisignal.idx.data.TradePlan
import kotlin.math.floor
import kotlin.math.max

/** One support or resistance level. */
data class SrLevel(
    val price: Double,
    /** How many swing points / extremes formed this level. */
    val touches: Int,
    /** "pivot" (swing high/low area) or "atr" (projection when price has no structure on that side). */
    val source: String,
) {
    val note: String
        get() = if (source == "atr") "Proyeksi volatilitas (belum ada titik balik di area ini)"
        else if (touches >= 2) "Titik balik $touches kali" else "Titik balik sebelumnya"
}

data class SupportResistance(val supports: List<SrLevel>, val resistances: List<SrLevel>, val atr: Double)

/**
 * Support & resistance from the daily chart: swing lows/highs (3 bars on each side) and the 20/60-day
 * extremes are clustered into price areas; the two nearest areas below the close are supports, the two
 * nearest above are resistances. Where there is no structure (new high / new low) a volatility
 * projection is used instead.
 */
object Levels {

    fun of(bars: List<Bar>): SupportResistance {
        val b = bars.takeLast(120)
        if (b.size < 5) return SupportResistance(emptyList(), emptyList(), 0.0)
        val c = b.last().close
        val atr = max(Market.atr(b.takeLast(15)), Market.tick(c))
        val k = 3
        val pts = mutableListOf<Double>()
        for (i in k until b.size - 1) {
            val hiOk = (i - k..minOf(b.lastIndex, i + k)).all { j -> j == i || b[j].high <= b[i].high }
            val loOk = (i - k..minOf(b.lastIndex, i + k)).all { j -> j == i || b[j].low >= b[i].low }
            if (hiOk) pts += b[i].high
            if (loOk) pts += b[i].low
        }
        for (n in listOf(20, 60)) {
            val w = b.dropLast(1).takeLast(n)
            if (w.isNotEmpty()) {
                pts += w.maxOf { it.high }
                pts += w.minOf { it.low }
            }
        }
        // cluster nearby points into one area
        val width = max(c * 0.012, atr * 0.5)
        val clusters = mutableListOf<MutableList<Double>>()
        for (p in pts.sorted()) {
            val last = clusters.lastOrNull()
            if (last != null && p - last.average() <= width) last += p else clusters += mutableListOf(p)
        }
        val areas = clusters.map { SrLevel(snap(it.average()), it.size, "pivot") }
        val gapMin = max(c * 0.004, Market.tick(c))
        val sup = areas.filter { it.price < c - gapMin }.sortedByDescending { it.price }.take(2).toMutableList()
        val res = areas.filter { it.price > c + gapMin }.sortedBy { it.price }.take(2).toMutableList()
        var m = 1.5
        while (sup.size < 2) {
            val p = snap(minOf(sup.lastOrNull()?.price ?: c, c) - m * atr)
            if (p > 0) sup += SrLevel(p, 0, "atr") else break
            m += 1.5
        }
        m = 1.5
        while (res.size < 2) {
            res += SrLevel(snap(maxOf(res.lastOrNull()?.price ?: c, c) + m * atr), 0, "atr")
            m += 1.5
        }
        return SupportResistance(sup, res, atr)
    }

    private fun snap(p: Double): Double {
        if (p <= 0) return 0.0
        val t = Market.tick(p)
        return Math.round(p / t) * t
    }

    /** Top of the buy zone: close +1% rounded down to a price fraction (never below the close). */
    fun zoneTop(close: Double): Double {
        val top = close * 1.01
        val t = Market.tick(top)
        return max(floor(top / t) * t, close)
    }

    /**
     * Plan for a stock checked by hand: buy up to close +1%, stop just under support 1 (3–12% away),
     * target the first resistance at least 3% away (max 30%), hold up to 7 sessions. TP/SL follow the
     * real entry price like every radar signal.
     */
    fun manualPlan(bars: List<Bar>, sr: SupportResistance): TradePlan {
        val c = bars.last().close
        val top = zoneTop(c)
        val s1 = sr.supports.firstOrNull()
        val slPx = s1?.let { Market.minusTicks(it.price) }
        val r = sr.resistances.firstOrNull { it.price >= top * 1.03 } ?: sr.resistances.lastOrNull()
        val rule = ExitRule(
            tp = 0.08, sl = 0.07, hold = 7,
            slPx = slPx, tpPx = r?.price,
            slCap = listOf(0.03, 0.12), tpCap = listOf(0.03, 0.30),
        )
        val (tp, sl) = rule.levels(top) ?: (Market.ceilTick(top * 1.08) to Market.floorTick(top * 0.93))
        val tpIdx = sr.resistances.indexOf(r) + 1
        return TradePlan(
            entryLow = top,
            entryHigh = top,
            cancelReason = "Beli di harga pembukaan bila ≤ ${Fmt.price(top)}, kalau dibuka lebih tinggi antre di ${Fmt.price(top)}.",
            stopLoss = sl,
            slReason = if (s1 != null) "Di bawah support 1 (${Fmt.price(s1.price)}), jarak 3–12% dari harga beli." else "7% di bawah harga beli.",
            tp = tp,
            tpReason = if (r != null) "Di resistance $tpIdx (${Fmt.price(r.price)}), minimal +3% dan maksimal +30%." else "+8% dari harga beli.",
            maxHoldDays = 7,
            rule = rule,
            entrySessions = 2,
        )
    }

    val MANUAL_TAG = RadarTag(jenis = "manual", tier = "", sid = 0, name = "Analisa manual", icon = "target", color = "#9EDCFF")
}
