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

enum class Strategy(
    val id: Int,
    val title: String,
    val tagline: String,
    val description: String,
    val needsRange: Boolean,
    val risk: String,
) {
    CLOSING_KUAT(
        1, "Closing Kuat + Bandar Masuk", "Momentum lanjutan",
        "Saham naik 2–15%, ditutup dekat harga tertinggi dengan volume ≥2x rata-rata, dan broker summary hari itu menunjukkan akumulasi yang dikuasai segelintir pembeli. Bandar masih membeli sampai penutupan, sehingga harga cenderung dilanjutkan besok.",
        false, "Sedang",
    ),
    BREAKOUT(
        2, "Breakout dari Tidur", "Bangun dari konsolidasi",
        "Setelah 5 hari volume kering, hari ini volume meledak ≥3x dan harga ditutup menembus high 20 hari, dikonfirmasi akumulasi bandar. Hari pertama bangun sering diikuti hari kedua dan ketiga.",
        false, "Sedang–tinggi",
    ),
    ARA_SEHAT(
        3, "ARA, Bandar Belum Keluar", "Lanjutan ARA yang sehat",
        "Saham ditutup ARA, pembeli utama tidak ikut menjual, dan barang dijual oleh banyak broker kecil. ARA hari ketiga berturut-turut atau lebih dibuang karena rawan distribusi.",
        false, "Tinggi",
    ),
    PANTULAN(
        4, "Pantulan di Modal Bandar", "Beli saat koreksi sehat",
        "Bandar terlihat mengumpulkan barang selama 20 hari, harga koreksi dengan volume sepi sampai ke sekitar harga rata-rata beli bandar, lalu muncul candle pembalikan. Stop loss paling dekat, rasio untung-rugi paling bagus.",
        true, "Rendah–sedang",
    ),
    GORENGAN_BANGUN(
        5, "Gorengan Bangun", "Akumulasi lama, mulai bergerak",
        "Satu broker menguasai pembelian selama 20 hari dan barang terkumpul ke sedikit tangan. Hari ini muncul pemicu: volume ≥2x, naik ≥3%, ditutup kuat, dan harga masih dekat modal bandar.",
        true, "Tinggi",
    );

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
)

private class Stats(bars: List<Bar>) {
    val n = bars.size
    val t: Bar = bars.last()
    val p: Bar? = bars.getOrNull(n - 2)
    val prior: List<Bar> = if (n >= 2) bars.subList(max(0, n - 21), n - 1) else emptyList()
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
    val window: Int = min(20, n)
    val tradedDays: Int = bars.takeLast(20).count { it.volume > 0 }
    val body: Double = abs(t.close - t.open)
    val lowerWick: Double = min(t.open, t.close) - t.low
    val reversal: Boolean = t.close > t.open || lowerWick >= 2 * max(body, Market.tick(t.close))
}

object StrategyEngine {

    // ---------------------------------------------------------------- prefilter (12 bars)

    /**
     * Cheap first pass on one page of daily bars. Thresholds are a little looser than the full
     * rules so short history does not hide a real candidate. Returns a ranking score or null.
     */
    fun prefilter(s: Strategy, bars: List<Bar>): Double? {
        if (bars.size < 4) return null
        val st = Stats(bars)
        if (st.t.close < 50 || st.arb || st.t.volume <= 0) return null
        return when (s) {
            Strategy.CLOSING_KUAT ->
                if (st.chg in 1.5..15.5 && !st.ara && st.closePos >= 0.75 && st.volRatio >= 1.6) st.volRatio else null
            Strategy.BREAKOUT -> {
                val priorHigh = Market.highestHigh(st.prior)
                if (st.volRatio >= 2.4 && st.t.close > priorHigh * 0.995) st.volRatio else null
            }
            Strategy.ARA_SEHAT -> if (st.ara) 1.0 + st.chg / 100.0 + min(st.volRatio, 10.0) / 10.0 else null
            Strategy.PANTULAN -> {
                val peak = Market.highestHigh(bars.takeLast(10))
                if (st.t.close <= peak * 0.96 && st.reversal) 1.0 + (1 - st.t.close / peak) else null
            }
            Strategy.GORENGAN_BANGUN ->
                if (st.chg >= 2.5 && st.volRatio >= 1.6 && st.closePos >= 0.6) st.volRatio else null
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

        // Common filters
        checks += Check("Harga minimal Rp50", "Close ${Fmt.price(st.t.close)}", st.t.close >= 50)
        val minDays = if (s == Strategy.GORENGAN_BANGUN) ceil(st.window * 0.5).toInt() else ceil(st.window * 0.75).toInt()
        checks += Check(
            "Aktif ditransaksikan", "${st.tradedDays} dari ${st.window} hari terakhir ada transaksi (min. $minDays)",
            st.tradedDays >= minDays,
        )
        checks += Check("Bukan ARB", if (st.arb) "Ditutup ARB" else "Perubahan ${Fmt.pct(st.chg)}", !st.arb)

        var score = 0.0
        when (s) {
            Strategy.CLOSING_KUAT -> score = closingKuat(st, d, checks, highlights)
            Strategy.BREAKOUT -> score = breakout(st, d, checks, highlights)
            Strategy.ARA_SEHAT -> score = araSehat(st, d, checks, highlights)
            Strategy.PANTULAN -> score = pantulan(st, d, checks, highlights)
            Strategy.GORENGAN_BANGUN -> score = gorengan(st, d, checks, highlights)
        }

        val passed = checks.all { it.pass }
        val lowLiq = st.avgValue20 < 1e9
        val liqNote = if (lowLiq) {
            "Nilai transaksi rata-rata ${Fmt.idr(st.avgValue20)}/hari. Perkiraan jumlah yang realistis untuk keluar ±${Fmt.lots(st.avgVol20 * 0.1)} lot per hari (10% volume rata-rata)."
        } else ""
        val plan = if (passed) Planner.plan(s, d) else null
        return StrategyEval(
            strategyId = s.id,
            passed = passed,
            score = score.roundToInt().coerceIn(0, 100),
            checks = checks,
            highlights = highlights,
            plan = plan,
            lowLiquidity = lowLiq,
            liquidityNote = liqNote,
        )
    }

    private fun brokerMissing(checks: MutableList<Check>, what: String) {
        checks += Check(what, "Data broker summary tidak tersedia", false, "bandar")
    }

    private fun topCodes(b: BrokerSummary, n: Int): String =
        b.buyers.take(n).joinToString(", ") { it.code }.ifBlank { "-" }

    private fun closingKuat(st: Stats, d: StockData, checks: MutableList<Check>, hl: MutableList<String>): Double {
        checks += Check(
            "Naik 2–15% & belum ARA", "Perubahan ${Fmt.pct(st.chg)}${if (st.ara) " (ARA)" else ""}",
            st.chg >= 2.0 && st.chg < 15.0 && !st.ara,
        )
        checks += Check(
            "Ditutup dekat high", "Close di ${Fmt.share(st.closePos)} range hari itu (min. 80%)", st.closePos >= 0.8,
        )
        checks += Check("Volume ≥2x rata-rata", "Volume ${Fmt.ratio(st.volRatio)} rata-rata 20 hari", st.volRatio >= 2.0)
        val b = d.day
        var acc = 0.0
        var share = 0.0
        if (b == null || b.buyers.isEmpty()) {
            brokerMissing(checks, "Bandar akumulasi")
        } else {
            acc = b.accStrength()
            share = b.buyShare(3)
            checks += Check("Bandar akumulasi", "Detector: ${b.mainLabel}", acc > 0, "bandar")
            checks += Check(
                "Top 3 buyer ≥40%", "${topCodes(b, 3)} menguasai ${Fmt.share(share)} nilai beli", share >= 0.4, "bandar",
            )
            hl += b.mainLabel
            hl += "Top buyer ${b.buyers.first().code}"
        }
        hl.add(0, Fmt.pct(st.chg))
        hl += "Vol ${Fmt.ratio(st.volRatio)}"
        val sweet = if (st.chg in 3.0..9.0) 1.0 else 0.6
        return 25 * min(st.volRatio / 4, 1.0) + 20 * st.closePos + 25 * min(share / 0.7, 1.0) + 20 * acc + 10 * sweet
    }

    private fun breakout(st: Stats, d: StockData, checks: MutableList<Check>, hl: MutableList<String>): Double {
        val n = d.bars.size
        val prev5 = d.bars.subList(max(0, n - 6), n - 1)
        val prev5Vol = if (prev5.isEmpty()) 0.0 else prev5.sumOf { it.volume } / prev5.size
        val dryRatio = if (st.avgVol20 > 0) prev5Vol / st.avgVol20 else 1.0
        val high20 = Market.highestHigh(st.prior)
        checks += Check("Volume kering 5 hari", "Rata-rata 5 hari = ${Fmt.share(dryRatio)} dari rata-rata 20 hari (maks. 60%)", dryRatio < 0.6)
        checks += Check("Volume meledak ≥3x", "Volume ${Fmt.ratio(st.volRatio)} rata-rata 20 hari", st.volRatio >= 3.0)
        checks += Check(
            "Tembus high 20 hari", "Close ${Fmt.price(st.t.close)} vs high 20 hari ${Fmt.price(high20)}",
            high20 > 0 && st.t.close > high20,
        )
        val b = d.day
        var acc = 0.0
        if (b == null || b.buyers.isEmpty()) {
            brokerMissing(checks, "Dikonfirmasi akumulasi")
        } else {
            acc = b.accStrength()
            checks += Check("Dikonfirmasi akumulasi", "Detector: ${b.mainLabel} · top buyer ${topCodes(b, 3)}", acc > 0, "bandar")
            hl += b.mainLabel
        }
        hl.add(0, "Breakout ${Fmt.price(high20)}")
        hl += "Vol ${Fmt.ratio(st.volRatio)}"
        val brk = if (high20 > 0) Market.clamp01((st.t.close / high20 - 1) / 0.08) else 0.0
        return 30 * min(st.volRatio / 6, 1.0) + 25 * Market.clamp01(1 - dryRatio / 0.6) + 25 * brk + 20 * acc
    }

    private fun araStreak(bars: List<Bar>): Int {
        var c = 0
        for (i in bars.lastIndex downTo 1) {
            if (Market.isAra(bars[i], bars[i - 1])) c++ else break
        }
        return c
    }

    private fun araSehat(st: Stats, d: StockData, checks: MutableList<Check>, hl: MutableList<String>): Double {
        checks += Check("Ditutup ARA", "Perubahan ${Fmt.pct(st.chg)}, close = high", st.ara)
        val streak = araStreak(d.bars)
        checks += Check("Bukan ARA ke-3 berturut-turut", "ARA $streak hari berturut-turut", streak in 1..2)
        val b = d.day
        var acc = 0.0
        var sellerRatio = 0.0
        var top1 = 0.0
        if (b == null || b.buyers.isEmpty()) {
            brokerMissing(checks, "Bandar akumulasi")
        } else {
            acc = b.accStrength()
            top1 = b.buyShare(1)
            val topBuyer = b.buyers.first().code
            val topSellers = b.sellers.take(5).map { it.code }
            sellerRatio = if (b.buyerCount > 0) b.sellerCount.toDouble() / b.buyerCount else 0.0
            checks += Check("Bandar akumulasi", "Detector: ${b.mainLabel}", acc > 0, "bandar")
            checks += Check(
                "Pembeli utama tidak menjual", "$topBuyer ${if (topBuyer in topSellers) "ADA" else "tidak ada"} di top 5 seller",
                topBuyer !in topSellers, "bandar",
            )
            checks += Check(
                "Penjual tersebar (≥2x pembeli)", "${b.sellerCount} penjual vs ${b.buyerCount} pembeli",
                sellerRatio >= 2.0, "bandar",
            )
            hl += "Top buyer $topBuyer"
            hl += b.mainLabel
        }
        hl.add(0, "ARA ${Fmt.pct(st.chg)}")
        return 30 * acc + 25 * min(sellerRatio / 4, 1.0) + 25 * min(top1 / 0.5, 1.0) + 20 * (if (streak == 1) 1.0 else 0.5)
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

    private fun pantulan(st: Stats, d: StockData, checks: MutableList<Check>, hl: MutableList<String>): Double {
        val peak = Market.highestHigh(d.bars.takeLast(10))
        val pullback = if (peak > 0) (1 - st.t.close / peak) * 100 else 0.0
        checks += Check("Sedang koreksi", "Turun ${Fmt.num(pullback)}% dari puncak 10 hari (min. 5%)", pullback >= 5.0)
        val n = d.bars.size
        val last3 = d.bars.subList(max(0, n - 4), n - 1)
        val quietRatio = if (st.avgVol20 > 0 && last3.isNotEmpty()) last3.sumOf { it.volume } / last3.size / st.avgVol20 else 1.0
        checks += Check("Koreksi sepi", "Volume 3 hari sebelumnya ${Fmt.share(quietRatio)} dari rata-rata (maks. 100%)", quietRatio < 1.0)
        val candle = when {
            st.t.close > st.t.open -> "Close > open"
            st.lowerWick >= 2 * max(st.body, Market.tick(st.t.close)) -> "Ekor bawah panjang"
            else -> "Belum ada pembalikan"
        }
        checks += Check("Candle pembalikan", candle, st.reversal)
        val b = d.range20
        var acc = 0.0
        var share = 0.0
        var dist = 99.0
        if (b == null || b.buyers.isEmpty()) {
            brokerMissing(checks, "Akumulasi 20 hari")
        } else {
            acc = b.accStrength()
            share = b.buyShare(1)
            val avg = bandarAvg(b)
            dist = if (avg > 0) (st.t.close / avg - 1) * 100 else 99.0
            checks += Check("Akumulasi 20 hari", "Detector 20 hari: ${b.mainLabel}", acc > 0, "bandar")
            checks += Check(
                "Top buyer ≥30% (20 hari)", "${b.buyers.first().code} menguasai ${Fmt.share(share)} nilai beli", share >= 0.3, "bandar",
            )
            checks += Check(
                "Dekat modal bandar (±3%)", "Close ${Fmt.price(st.t.close)} vs modal ${b.buyers.first().code} ${Fmt.price(avg)} (${Fmt.pct(dist)})",
                abs(dist) <= 3.0, "bandar",
            )
            hl += "Modal ${b.buyers.first().code} ${Fmt.price(avg)}"
            hl += b.mainLabel
        }
        hl.add(0, "Koreksi ${Fmt.num(pullback)}%")
        return 25 * acc + 25 * min(share / 0.5, 1.0) + 30 * Market.clamp01(1 - abs(dist) / 3.0) + 20 * Market.clamp01(1 - quietRatio)
    }

    private fun gorengan(st: Stats, d: StockData, checks: MutableList<Check>, hl: MutableList<String>): Double {
        checks += Check("Volume ≥2x rata-rata", "Volume ${Fmt.ratio(st.volRatio)} rata-rata 20 hari", st.volRatio >= 2.0)
        checks += Check("Naik minimal 3%", "Perubahan ${Fmt.pct(st.chg)}", st.chg >= 3.0)
        checks += Check("Ditutup kuat", "Close di ${Fmt.share(st.closePos)} range hari itu (min. 70%)", st.closePos >= 0.7)
        val b = d.range20
        var share = 0.0
        var concentration = 0.0
        var dist = 99.0
        if (b == null || b.buyers.isEmpty()) {
            brokerMissing(checks, "Akumulasi terkonsentrasi 20 hari")
        } else {
            share = b.buyShare(1)
            concentration = if (b.buyerCount > 0) b.sellerCount.toDouble() / b.buyerCount else 0.0
            val top = b.buyers.first().code
            val avg = bandarAvg(b)
            dist = if (avg > 0) (st.t.close / avg - 1) * 100 else 99.0
            checks += Check(
                "Top buyer ≥40% (20 hari)", "$top menguasai ${Fmt.share(share)} nilai beli", share >= 0.4, "bandar",
            )
            checks += Check(
                "Barang terkumpul (penjual ≥3x pembeli)", "${b.sellerCount} penjual vs ${b.buyerCount} pembeli",
                concentration >= 3.0, "bandar",
            )
            val todayTop3 = d.day?.buyers?.take(3)?.map { it.code } ?: emptyList()
            checks += Check(
                "Broker yang sama masih membeli", "Top 3 buyer hari ini: ${todayTop3.joinToString(", ").ifBlank { "-" }}",
                top in todayTop3, "bandar",
            )
            checks += Check(
                "Harga belum lari (≤15% di atas modal)", "Close ${Fmt.pct(dist)} dari modal $top ${Fmt.price(avg)}",
                dist <= 15.0, "bandar",
            )
            hl += "Bandar $top ${Fmt.share(share)}"
            hl += "Modal ${Fmt.price(avg)}"
        }
        hl.add(0, Fmt.pct(st.chg))
        hl += "Vol ${Fmt.ratio(st.volRatio)}"
        return 30 * min(share / 0.6, 1.0) + 20 * min(concentration / 5, 1.0) + 20 * min(st.volRatio / 4, 1.0) +
            15 * st.closePos + 15 * Market.clamp01(1 - max(dist, 0.0) / 15.0)
    }
}
