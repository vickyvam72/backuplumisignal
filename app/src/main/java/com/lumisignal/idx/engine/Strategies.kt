package com.lumisignal.idx.engine

import com.lumisignal.idx.data.Bar
import com.lumisignal.idx.data.BrokerSummary
import com.lumisignal.idx.data.Check
import com.lumisignal.idx.data.StrategyEval
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Five setups tuned for the IDX, where most movers are thin, operator-driven ("gorengan") stocks.
 *
 * Every strategy shares the same quality gates that remove the classic gorengan traps:
 *  - enough liquidity to get out,
 *  - not already run up too far (late buyers are the exit liquidity),
 *  - the main buyer is not a retail broker (retail FOMO, not a bandar),
 *  - the bandar is not distributing over the last 5 sessions.
 * On top of that each strategy has a few REQUIRED setup rules and OPTIONAL rules that only score.
 */
enum class Strategy(
    val id: Int,
    val title: String,
    val tagline: String,
    val description: String,
    val needsRange: Boolean,
    val risk: String,
) {
    CLOSING_KUAT(
        1, "Closing Kuat Bandar", "Momentum lanjutan",
        "Naik 2% sampai di bawah ARA, ditutup kuat di atas MA20 dengan volume ≥1,5x, bandar (bukan ritel) menguasai pembelian hari itu dan tidak distribusi dalam 5 hari. Entry tidak mengejar close: ditunggu di area harga rata-rata transaksi hari sinyal.",
        false, "Sedang",
    ),
    BREAKOUT(
        2, "Breakout Volume", "Tembus atap dengan ledakan volume",
        "Close menembus high 10 hari dengan volume ≥2x dan candle kuat, harga di atas MA20, belum naik terlalu jauh, dan bandar 5 hari tidak distribusi. Entry di area retest level breakout.",
        false, "Sedang–tinggi",
    ),
    ARA_HUNTER(
        3, "ARA Hunter", "Lanjutan ARA & hampir ARA",
        "Ditutup di ARA atau sangat dekat ARA, bukan hari ke-3 beruntun, pembeli utama bukan ritel dan tidak ikut menjual, serta bandar 5 hari masih akumulasi. Pola gorengan yang sedang dikerek.",
        false, "Tinggi",
    ),
    PANTULAN(
        4, "Pantulan Modal Bandar", "Beli koreksi di harga bandar",
        "Bandar mengumpulkan barang selama 20 hari, harga koreksi ≥4% dengan volume sepi sampai ±5% dari harga rata-rata beli bandar, lalu muncul candle pembalikan. Stop loss paling dekat.",
        true, "Rendah–sedang",
    ),
    GORENGAN_BANGUN(
        5, "Gorengan Bangun", "Akumulasi lama, mulai dikerek",
        "Pembelian 20 hari terkonsentrasi di sedikit broker non-ritel, lalu hari ini muncul pemicu: naik ≥2%, volume ≥1,5x, ditutup kuat, harga belum lari jauh dari modal bandar.",
        true, "Tinggi",
    );

    /** Strategies that read the last 5 sessions of broker flow (distribution guard). */
    val needsRange5: Boolean get() = !needsRange

    companion object {
        fun byId(id: Int): Strategy = entries.firstOrNull { it.id == id } ?: CLOSING_KUAT
    }
}

/** Everything known about one stock at the reference close. Bars are completed sessions, oldest first. */
data class StockData(
    val symbol: String,
    val name: String = "",
    val bars: List<Bar>,
    val day: BrokerSummary? = null,
    val range20: BrokerSummary? = null,
    val range5: BrokerSummary? = null,
)

/** Retail-heavy brokers: when they lead the buying it is usually retail FOMO, not a bandar. */
val RETAIL_BROKERS = setOf("XL", "XC", "YP", "PD", "CC", "KK")

private class Stats(bars: List<Bar>) {
    val n = bars.size
    val t: Bar = bars.last()
    val p: Bar? = bars.getOrNull(n - 2)
    val prior: List<Bar> = if (n >= 2) bars.subList(max(0, n - 21), n - 1) else emptyList()
    val prior10: List<Bar> = if (n >= 2) bars.subList(max(0, n - 11), n - 1) else emptyList()
    val avgVol20: Double = if (prior.isEmpty()) 0.0 else prior.sumOf { it.volume } / prior.size
    val avgValue20: Double = if (prior.isEmpty()) 0.0 else prior.sumOf { it.value } / prior.size
    val volRatio: Double = when {
        avgVol20 > 0 -> t.volume / avgVol20
        t.volume > 0 -> 9.9
        else -> 0.0
    }
    val chg: Double = Market.changePct(t, p)
    val closePos: Double = Market.closePos(t)
    val ara: Boolean = Market.isAra(t, p)
    val arb: Boolean = Market.isArb(t, p)
    val araPct: Double = (p?.let { Market.araFraction(it.close) } ?: 0.25) * 100
    val window: Int = min(20, n)
    val tradedDays: Int = bars.takeLast(20).count { it.volume > 0 }
    val body: Double = abs(t.close - t.open)
    val lowerWick: Double = min(t.open, t.close) - t.low
    val upperWick: Double = t.high - max(t.open, t.close)
    val reversal: Boolean = t.close > t.open || lowerWick >= 2 * max(body, Market.tick(t.close))
    val closes = bars.map { it.close }
    val ma20: Double? = Market.sma(closes, min(20, n))
    val gain5: Double = bars.getOrNull(n - 6)?.let { (t.close / it.close - 1) * 100 } ?: 0.0
}

object StrategyEngine {

    /** Candidates going on to the broker-summary stage. */
    const val STAGE2_LIMIT = 50

    /** Near-ARA: at least 70% of the auto-rejection limit and closed at the high. */
    fun nearAra(bar: Bar, prev: Bar?): Boolean {
        if (prev == null) return false
        val chg = Market.changePct(bar, prev)
        return chg >= Market.araFraction(prev.close) * 100 * 0.7 && Market.closePos(bar) >= 0.85
    }

    /** Liquidity good enough to exit: average ≥ Rp300 jt/day or signal-day value ≥ Rp1 M. */
    private fun liquidEnough(st: Stats, relaxed: Boolean): Boolean =
        if (relaxed) st.avgValue20 >= 150e6 || st.t.value >= 500e6 else st.avgValue20 >= 300e6 || st.t.value >= 1e9

    // ---------------------------------------------------------------- prefilter (12 bars)

    /**
     * Cheap first pass on one page of daily bars, a little looser than the required rules so
     * short history does not hide a candidate. Returns a ranking score or null.
     */
    fun prefilter(s: Strategy, bars: List<Bar>): Double? {
        if (bars.size < 4) return null
        val st = Stats(bars)
        if (st.t.close < 50 || st.arb || st.t.volume <= 0) return null
        val relaxed = s == Strategy.GORENGAN_BANGUN || s == Strategy.PANTULAN
        if (!liquidEnough(st, relaxed) && st.t.value < 300e6) return null
        return when (s) {
            Strategy.CLOSING_KUAT ->
                if (st.chg >= 1.5 && !st.ara && st.closePos >= 0.65 && st.volRatio >= 1.3 && st.gain5 <= 40) st.volRatio + st.closePos else null
            Strategy.BREAKOUT -> {
                val ceiling = Market.highestHigh(st.prior10)
                if (st.volRatio >= 1.7 && ceiling > 0 && st.t.close > ceiling && st.closePos >= 0.5 && st.gain5 <= 40) st.volRatio else null
            }
            Strategy.ARA_HUNTER ->
                if (st.chg >= st.araPct * 0.65 && st.closePos >= 0.8 && st.gain5 <= 70) st.chg / st.araPct + min(st.volRatio, 10.0) / 10.0 else null
            Strategy.PANTULAN -> {
                val peak = Market.highestHigh(bars.takeLast(10))
                if (st.t.close <= peak * 0.965 && st.reversal) 1.0 + (1 - st.t.close / peak) else null
            }
            Strategy.GORENGAN_BANGUN ->
                if (st.chg >= 1.8 && st.volRatio >= 1.3 && st.closePos >= 0.55 && st.gain5 <= 40) st.volRatio + st.chg / 10.0 else null
        }
    }

    // ---------------------------------------------------------------- full evaluation

    fun evaluate(s: Strategy, d: StockData): StrategyEval {
        if (d.bars.size < 6) {
            return StrategyEval(
                s.id, false, 0,
                listOf(Check("Data harga cukup", "Hanya ${d.bars.size} sesi tersedia", false)),
            )
        }
        val st = Stats(d.bars)
        val checks = mutableListOf<Check>()
        val highlights = mutableListOf<String>()

        // ---- shared quality gates
        checks += Check("Harga minimal Rp50", "Close ${Fmt.price(st.t.close)}", st.t.close >= 50)
        val minShare = if (s == Strategy.GORENGAN_BANGUN || s == Strategy.ARA_HUNTER) 0.4 else 0.6
        val minDays = ceil(st.window * minShare).toInt()
        checks += Check(
            "Aktif ditransaksikan", "${st.tradedDays} dari ${st.window} hari terakhir ada transaksi (min. $minDays)",
            st.tradedDays >= minDays,
        )
        checks += Check("Bukan ARB", if (st.arb) "Ditutup ARB" else "Perubahan ${Fmt.pct(st.chg)}", !st.arb)
        val relaxed = s == Strategy.GORENGAN_BANGUN || s == Strategy.PANTULAN
        checks += Check(
            "Cukup likuid untuk keluar",
            "Rata-rata ${Fmt.idr(st.avgValue20)}/hari · hari sinyal ${Fmt.idr(st.t.value)} (min. ${if (relaxed) "150 jt / 500 jt" else "300 jt / 1 M"})",
            liquidEnough(st, relaxed),
        )
        if (s != Strategy.PANTULAN) {
            val maxRun = if (s == Strategy.ARA_HUNTER) 60.0 else 35.0
            checks += Check(
                "Belum lari terlalu jauh", "Naik ${Fmt.pct(st.gain5)} dalam 5 hari (maks. ${Fmt.num(maxRun, 0)}%)",
                st.gain5 <= maxRun,
            )
        }

        val score = when (s) {
            Strategy.CLOSING_KUAT -> closingKuat(st, d, checks, highlights)
            Strategy.BREAKOUT -> breakout(st, d, checks, highlights)
            Strategy.ARA_HUNTER -> araHunter(st, d, checks, highlights)
            Strategy.PANTULAN -> pantulan(st, d, checks, highlights)
            Strategy.GORENGAN_BANGUN -> gorengan(st, d, checks, highlights)
        }

        val passed = checks.filter { it.required }.all { it.pass }
        val lowLiq = st.avgValue20 < 1e9
        val liqNote = if (lowLiq) {
            "Nilai transaksi rata-rata ${Fmt.idr(st.avgValue20)}/hari. Perkiraan jumlah yang realistis untuk keluar ±${Fmt.lots(st.avgVol20 * 0.1)} lot per hari (10% volume rata-rata)."
        } else ""
        return StrategyEval(
            strategyId = s.id,
            passed = passed,
            score = score.roundToInt().coerceIn(0, 100),
            checks = checks,
            highlights = highlights,
            plan = if (passed) Planner.plan(s, d) else null,
            lowLiquidity = lowLiq,
            liquidityNote = liqNote,
        )
    }

    private fun topCodes(b: BrokerSummary, n: Int): String =
        b.buyers.take(n).joinToString(", ") { it.code }.ifBlank { "-" }

    private fun noBroker(checks: MutableList<Check>, label: String, required: Boolean) {
        checks += Check(label, "Data broker summary tidak tersedia", false, "bandar", required)
    }

    /** Average buy price of the main accumulator over the range. */
    fun bandarAvg(b: BrokerSummary?): Double {
        if (b == null || b.buyers.isEmpty()) return 0.0
        val first = b.buyers.first()
        if (first.avgPrice > 0) return first.avgPrice
        val top = b.buyers.take(3).filter { it.avgPrice > 0 && it.lots > 0 }
        val lots = top.sumOf { it.lots }
        return if (lots > 0) top.sumOf { it.avgPrice * it.lots } / lots else 0.0
    }

    /** Main buyer is not a retail broker. Returns 1.0 when clean, 0.0 when retail leads. */
    private fun bandarNotRetail(b: BrokerSummary?, checks: MutableList<Check>, required: Boolean, label: String = "Pembeli utama bukan ritel"): Double {
        if (b == null || b.buyers.isEmpty()) {
            noBroker(checks, label, required)
            return 0.0
        }
        val top = b.buyers.first().code
        val retailInTop3 = b.buyers.take(3).count { it.code in RETAIL_BROKERS }
        val ok = top !in RETAIL_BROKERS && retailInTop3 <= 1
        checks += Check(
            label,
            "Top buyer $top${if (top in RETAIL_BROKERS) " (ritel)" else ""} · $retailInTop3 broker ritel di top 3",
            ok, "bandar", required,
        )
        return if (ok) 1.0 else 0.0
    }

    /** The last 5 sessions do not show distribution. */
    private fun notDistributing(b: BrokerSummary?, checks: MutableList<Check>, required: Boolean): Double {
        if (b == null || b.buyers.isEmpty()) {
            noBroker(checks, "Bandar 5 hari tidak distribusi", required)
            return 0.0
        }
        val dist = b.mainLabel.contains("dist", ignoreCase = true)
        checks += Check(
            "Bandar 5 hari tidak distribusi", "Detector 5 hari: ${b.mainLabel} · top buyer ${topCodes(b, 3)}",
            !dist, "bandar", required,
        )
        return if (dist) 0.0 else 0.5 + 0.5 * b.accStrength()
    }

    // ---- S1 ---------------------------------------------------------------------
    private fun closingKuat(st: Stats, d: StockData, checks: MutableList<Check>, hl: MutableList<String>): Double {
        checks += Check(
            "Naik ≥2% & belum ARA", "Perubahan ${Fmt.pct(st.chg)} (batas ARA ±${Fmt.num(st.araPct, 0)}%)",
            st.chg >= 2.0 && !st.ara,
        )
        checks += Check(
            "Ditutup kuat, ekor atas pendek",
            "Close di ${Fmt.share(st.closePos)} range (min. 70%) · ekor atas ${Fmt.price(st.upperWick)}",
            st.closePos >= 0.7 && st.upperWick <= max(st.body, Market.tick(st.t.close) * 2),
        )
        checks += Check("Volume ≥1,5x rata-rata", "Volume ${Fmt.ratio(st.volRatio)} rata-rata 20 hari", st.volRatio >= 1.5)
        val ma = st.ma20
        checks += Check("Di atas MA20 (tren naik)", "Close ${Fmt.price(st.t.close)} vs MA20 ${Fmt.price(ma)}", ma != null && st.t.close > ma)
        val b = d.day
        var acc = 0.0
        var share = 0.0
        if (b == null || b.buyers.isEmpty()) {
            noBroker(checks, "Bandar menguasai pembelian", true)
        } else {
            acc = b.accStrength()
            share = b.buyShare(3)
            checks += Check(
                "Bandar menguasai pembelian",
                "Detector ${b.mainLabel} · top 3 (${topCodes(b, 3)}) = ${Fmt.share(share)} nilai beli",
                acc > 0 || share >= 0.5, "bandar",
            )
            hl += b.mainLabel
            hl += "Top buyer ${b.buyers.first().code}"
        }
        val clean = bandarNotRetail(b, checks, true)
        val five = notDistributing(d.range5, checks, true)
        checks += Check("Volume meledak ≥3x", "Volume ${Fmt.ratio(st.volRatio)}", st.volRatio >= 3.0, required = false)
        hl.add(0, Fmt.pct(st.chg))
        hl += "Vol ${Fmt.ratio(st.volRatio)}"
        val sweet = if (st.chg in 3.0..12.0) 1.0 else 0.6
        return 18 * min(st.volRatio / 4, 1.0) + 15 * st.closePos + 17 * min(share / 0.7, 1.0) + 20 * acc +
            10 * clean + 12 * five + 8 * sweet
    }

    // ---- S2 ---------------------------------------------------------------------
    private fun breakout(st: Stats, d: StockData, checks: MutableList<Check>, hl: MutableList<String>): Double {
        val n = d.bars.size
        val high10 = Market.highestHigh(st.prior10)
        val high20 = Market.highestHigh(st.prior)
        val prev5 = d.bars.subList(max(0, n - 6), n - 1)
        val prev5Vol = if (prev5.isEmpty()) 0.0 else prev5.sumOf { it.volume } / prev5.size
        val dryRatio = if (st.avgVol20 > 0) prev5Vol / st.avgVol20 else 1.0
        checks += Check(
            "Tembus high 10 hari", "Close ${Fmt.price(st.t.close)} vs high 10 hari ${Fmt.price(high10)}",
            high10 > 0 && st.t.close > high10,
        )
        checks += Check("Volume ≥2x rata-rata", "Volume ${Fmt.ratio(st.volRatio)} rata-rata 20 hari", st.volRatio >= 2.0)
        checks += Check("Candle kuat", "Close di ${Fmt.share(st.closePos)} range hari itu (min. 60%)", st.closePos >= 0.6 && st.chg > 0)
        val ma = st.ma20
        checks += Check("Di atas MA20 (tren naik)", "Close ${Fmt.price(st.t.close)} vs MA20 ${Fmt.price(ma)}", ma != null && st.t.close > ma)
        val five = notDistributing(d.range5, checks, true)
        checks += Check("Tembus high 20 hari", "High 20 hari ${Fmt.price(high20)}", high20 > 0 && st.t.close > high20, required = false)
        checks += Check(
            "Sebelumnya volume kering", "Volume 5 hari sebelumnya = ${Fmt.share(dryRatio)} rata-rata (ideal <80%)",
            dryRatio < 0.8, required = false,
        )
        val b = d.day
        var acc = 0.0
        if (b == null || b.buyers.isEmpty()) {
            noBroker(checks, "Dikonfirmasi akumulasi", false)
        } else {
            acc = b.accStrength()
            checks += Check("Dikonfirmasi akumulasi", "Detector ${b.mainLabel} · top buyer ${topCodes(b, 3)}", acc > 0, "bandar", false)
            hl += b.mainLabel
        }
        val clean = bandarNotRetail(b, checks, false)
        hl.add(0, "Breakout ${Fmt.price(high10)}")
        hl += "Vol ${Fmt.ratio(st.volRatio)}"
        val brk = if (high10 > 0) Market.clamp01((st.t.close / high10 - 1) / 0.08) else 0.0
        val big = if (high20 > 0 && st.t.close > high20) 1.0 else 0.0
        return 20 * min(st.volRatio / 5, 1.0) + 12 * Market.clamp01(1 - dryRatio) + 10 * brk + 8 * big + 12 * st.closePos +
            15 * acc + 10 * clean + 13 * five
    }

    // ---- S3 ---------------------------------------------------------------------
    private fun streak(bars: List<Bar>): Int {
        var c = 0
        for (i in bars.lastIndex downTo 1) {
            if (nearAra(bars[i], bars[i - 1])) c++ else break
        }
        return c
    }

    private fun araHunter(st: Stats, d: StockData, checks: MutableList<Check>, hl: MutableList<String>): Double {
        val near = nearAra(st.t, st.p)
        checks += Check(
            "Ditutup di/dekat ARA",
            "Naik ${Fmt.pct(st.chg)} dari batas ARA ${Fmt.num(st.araPct, 0)}%, close di ${Fmt.share(st.closePos)} range${if (st.ara) " · ARA" else ""}",
            near || st.ara,
        )
        val run = streak(d.bars)
        checks += Check("Bukan hari ke-3 beruntun", "Naik kencang $run hari berturut-turut", run <= 2)
        val b = d.day
        var acc = 0.0
        var sellerRatio = 0.0
        var top1 = 0.0
        if (b == null || b.buyers.isEmpty()) {
            noBroker(checks, "Pembeli utama tidak menjual", true)
        } else {
            acc = b.accStrength()
            top1 = b.buyShare(1)
            val topBuyer = b.buyers.first().code
            val topSellers = b.sellers.take(3).map { it.code }
            sellerRatio = if (b.buyerCount > 0) b.sellerCount.toDouble() / b.buyerCount else 0.0
            checks += Check(
                "Pembeli utama tidak menjual",
                "$topBuyer ${if (topBuyer in topSellers) "ADA" else "tidak ada"} di top 3 penjual",
                topBuyer !in topSellers, "bandar",
            )
            checks += Check("Detector akumulasi", "Detector ${b.mainLabel}", acc > 0, "bandar", false)
            checks += Check(
                "Penjual tersebar", "${b.sellerCount} penjual vs ${b.buyerCount} pembeli (ideal ≥1,5x)",
                sellerRatio >= 1.5, "bandar", false,
            )
            hl += "Top buyer $topBuyer"
            hl += b.mainLabel
        }
        val clean = bandarNotRetail(b, checks, true)
        val five = notDistributing(d.range5, checks, true)
        checks += Check("Volume ≥2x", "Volume ${Fmt.ratio(st.volRatio)}", st.volRatio >= 2.0, required = false)
        hl.add(0, if (st.ara) "ARA ${Fmt.pct(st.chg)}" else "Hampir ARA ${Fmt.pct(st.chg)}")
        return 20 * acc + 15 * min(sellerRatio / 3, 1.0) + 15 * min(top1 / 0.4, 1.0) + 12 * (if (run <= 1) 1.0 else 0.5) +
            8 * min(st.volRatio / 4, 1.0) + 8 * (if (st.ara) 1.0 else 0.6) + 10 * clean + 12 * five
    }

    // ---- S4 ---------------------------------------------------------------------
    private fun pantulan(st: Stats, d: StockData, checks: MutableList<Check>, hl: MutableList<String>): Double {
        val peak = Market.highestHigh(d.bars.takeLast(10))
        val pullback = if (peak > 0) (1 - st.t.close / peak) * 100 else 0.0
        checks += Check("Sedang koreksi", "Turun ${Fmt.num(pullback)}% dari puncak 10 hari (min. 4%)", pullback >= 4.0)
        val candle = when {
            st.t.close > st.t.open -> "Close > open"
            st.lowerWick >= 2 * max(st.body, Market.tick(st.t.close)) -> "Ekor bawah panjang"
            else -> "Belum ada pembalikan"
        }
        checks += Check("Candle pembalikan", candle, st.reversal)
        val n = d.bars.size
        val last3 = d.bars.subList(max(0, n - 4), n - 1)
        val quietRatio = if (st.avgVol20 > 0 && last3.isNotEmpty()) last3.sumOf { it.volume } / last3.size / st.avgVol20 else 1.0
        checks += Check(
            "Koreksi sepi (bukan dibanting)", "Volume 3 hari sebelumnya ${Fmt.share(quietRatio)} dari rata-rata (maks. 120%)",
            quietRatio < 1.2,
        )
        val b = d.range20
        var acc = 0.0
        var share = 0.0
        var dist = 99.0
        if (b == null || b.buyers.isEmpty()) {
            noBroker(checks, "Bandar akumulasi 20 hari", true)
        } else {
            acc = b.accStrength()
            share = b.buyShare(1)
            val avg = bandarAvg(b)
            val top = b.buyers.first().code
            dist = if (avg > 0) (st.t.close / avg - 1) * 100 else 99.0
            checks += Check(
                "Bandar akumulasi 20 hari", "Detector ${b.mainLabel} · $top menguasai ${Fmt.share(share)}",
                acc > 0 || share >= 0.3, "bandar",
            )
            checks += Check(
                "Dekat modal bandar (±5%)", "Close ${Fmt.price(st.t.close)} vs modal $top ${Fmt.price(avg)} (${Fmt.pct(dist)})",
                abs(dist) <= 5.0, "bandar",
            )
            hl += "Modal $top ${Fmt.price(avg)}"
            hl += b.mainLabel
        }
        val clean = bandarNotRetail(b, checks, true, "Akumulator bukan ritel")
        val todaySellers = d.day?.sellers?.take(3)?.map { it.code } ?: emptyList()
        val topCode = b?.buyers?.firstOrNull()?.code
        checks += Check(
            "Bandar tidak ikut jual hari ini",
            "Top 3 penjual hari ini: ${todaySellers.joinToString(", ").ifBlank { "-" }}",
            topCode == null || topCode !in todaySellers, "bandar", false,
        )
        hl.add(0, "Koreksi ${Fmt.num(pullback)}%")
        return 22 * acc + 15 * min(share / 0.5, 1.0) + 25 * Market.clamp01(1 - abs(dist) / 5.0) + 13 * Market.clamp01(1.2 - quietRatio) +
            10 * clean + 15 * (if (topCode != null && topCode !in todaySellers) 1.0 else 0.0)
    }

    // ---- S5 ---------------------------------------------------------------------
    private fun gorengan(st: Stats, d: StockData, checks: MutableList<Check>, hl: MutableList<String>): Double {
        checks += Check("Pemicu: naik ≥2%", "Perubahan ${Fmt.pct(st.chg)}", st.chg >= 2.0)
        checks += Check("Volume ≥1,5x rata-rata", "Volume ${Fmt.ratio(st.volRatio)} rata-rata 20 hari", st.volRatio >= 1.5)
        checks += Check("Ditutup kuat", "Close di ${Fmt.share(st.closePos)} range hari itu (min. 60%)", st.closePos >= 0.6)
        val b = d.range20
        var share1 = 0.0
        var share3 = 0.0
        var concentration = 0.0
        var dist = 99.0
        var same = false
        if (b == null || b.buyers.isEmpty()) {
            noBroker(checks, "Pembelian 20 hari terkonsentrasi", true)
        } else {
            share1 = b.buyShare(1)
            share3 = b.buyShare(3)
            concentration = if (b.buyerCount > 0) b.sellerCount.toDouble() / b.buyerCount else 0.0
            val top = b.buyers.first().code
            val avg = bandarAvg(b)
            dist = if (avg > 0) (st.t.close / avg - 1) * 100 else 99.0
            checks += Check(
                "Pembelian 20 hari terkonsentrasi", "$top ${Fmt.share(share1)} · top 3 (${topCodes(b, 3)}) ${Fmt.share(share3)}",
                share1 >= 0.3 || share3 >= 0.55, "bandar",
            )
            checks += Check(
                "Harga belum lari (≤20% di atas modal)", "Close ${Fmt.pct(dist)} dari modal $top ${Fmt.price(avg)}",
                dist <= 20.0, "bandar",
            )
            checks += Check(
                "Barang terkumpul ke sedikit tangan", "${b.sellerCount} penjual vs ${b.buyerCount} pembeli (ideal ≥2x)",
                concentration >= 2.0, "bandar", false,
            )
            val todayTop3 = d.day?.buyers?.take(3)?.map { it.code } ?: emptyList()
            same = top in todayTop3
            checks += Check(
                "Bandar yang sama masih membeli", "Top 3 pembeli hari ini: ${todayTop3.joinToString(", ").ifBlank { "-" }}",
                same, "bandar", false,
            )
            hl += "Bandar $top ${Fmt.share(share1)}"
            hl += "Modal ${Fmt.price(avg)}"
        }
        val clean = bandarNotRetail(b, checks, true, "Akumulator bukan ritel")
        hl.add(0, Fmt.pct(st.chg))
        hl += "Vol ${Fmt.ratio(st.volRatio)}"
        return 20 * min(max(share1 / 0.5, share3 / 0.8), 1.0) + 12 * min(concentration / 4, 1.0) + 15 * min(st.volRatio / 4, 1.0) +
            12 * st.closePos + 13 * Market.clamp01(1 - max(dist, 0.0) / 20.0) + 8 * min(st.chg / 10, 1.0) + 10 * clean +
            10 * (if (same) 1.0 else 0.0)
    }
}
