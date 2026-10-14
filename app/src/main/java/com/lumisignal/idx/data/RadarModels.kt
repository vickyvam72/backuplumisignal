package com.lumisignal.idx.data

import com.lumisignal.idx.engine.Market
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Exit rule of one radar signal: enough to rebuild TP/SL from the real entry price, exactly like the
 * backtest (TP rounded up and SL rounded down to IDX price fractions, SL ≤ maxRatio × TP when set).
 */
@Serializable
data class ExitRule(
    val tp: Double? = null,
    val sl: Double? = null,
    val hold: Int = 7,
    @SerialName("sl_px") val slPx: Double? = null,
    @SerialName("tp_px") val tpPx: Double? = null,
    @SerialName("sl_cap") val slCap: List<Double> = listOf(0.03, 0.10),
    @SerialName("tp_cap") val tpCap: List<Double> = listOf(0.03, 0.25),
    @SerialName("max_ratio") val maxRatio: Double? = null,
) {
    /** (take profit, stop loss) prices for a buy at [entry], or null when the rule is incomplete. */
    fun levels(entry: Double): Pair<Double, Double>? {
        if (entry <= 0) return null
        var tpF = tp ?: return null
        var slF = sl ?: return null
        slPx?.let { slF = (1 - it / entry).coerceIn(slCap[0], slCap[1]) }
        tpPx?.let { tpF = (it / entry - 1).coerceIn(tpCap[0], tpCap[1]) }
        var tpp = entry * (1 + tpF)
        var slp = entry * (1 - slF)
        val t1 = Market.tick(tpp)
        tpp = ceil(tpp / t1 - 1e-9) * t1
        val t2 = Market.tick(slp)
        slp = floor(slp / t2 + 1e-9) * t2
        val mr = maxRatio
        if (mr != null) {
            if (entry - slp > mr * (tpp - entry) + 1e-9) slp = ceil(slp / t2 + 1e-9) * t2
            if (entry - slp > mr * (tpp - entry) + 1e-9) {
                slp = entry - mr * (tpp - entry)
                val t3 = Market.tick(slp)
                slp = ceil(slp / t3 - 1e-9) * t3
            }
        }
        if (!tpp.isFinite() || !slp.isFinite() || slp <= 0 || slp >= entry || tpp <= entry) return null
        return tpp to slp
    }
}

@Serializable
data class RadarStrategy(
    val id: Int,
    val jenis: String,
    val name: String,
    val desc: String = "",
    val cond: String = "",
    val icon: String = "",
    val color: String = "#3D8BFF",
)

@Serializable
data class BacktestStats(
    val trades: Int = 0,
    val winrate: Double = 0.0,
    @SerialName("kena_tp") val kenaTp: Double = 0.0,
    @SerialName("akumulasi_pl") val akumulasiPl: Double = 0.0,
    @SerialName("rata_pl") val rataPl: Double = 0.0,
    @SerialName("max_drawdown") val maxDrawdown: Double = 0.0,
    @SerialName("hold_rata") val holdRata: Double = 0.0,
    @SerialName("tp_rata") val tpRata: Double = 0.0,
    @SerialName("sl_rata") val slRata: Double = 0.0,
)

@Serializable
data class StrategySpec(
    val id: Int,
    val cond: String = "",
    val reg: String = "",
    val exit: String = "",
    val rank: String = "",
    @SerialName("reg_text") val regText: String = "",
    @SerialName("exit_text") val exitText: String = "",
    @SerialName("rank_text") val rankText: String = "",
    val weak: Boolean = false,
    val stats: BacktestStats = BacktestStats(),
)

@Serializable
data class SpecBoard(
    val strategies: List<StrategySpec> = emptyList(),
    @SerialName("top5_stats") val top5Stats: BacktestStats = BacktestStats(),
)

@Serializable
data class RadarPick(
    val ticker: String,
    val name: String = "",
    val rank: Int = 0,
    val close: Double = 0.0,
    val chg: Double = 0.0,
    @SerialName("value_ma20") val valueMa20: Double = 0.0,
    @SerialName("entry_high") val entryHigh: Double = 0.0,
    val tp: Double? = null,
    val sl: Double? = null,
    @SerialName("tp_pct") val tpPct: Double? = null,
    @SerialName("sl_pct") val slPct: Double? = null,
    val hold: Int = 7,
    val rule: ExitRule = ExitRule(),
    val valid: Boolean = true,
    val why: List<String> = emptyList(),
    /** Strategy of a combined top-5 pick. */
    val sid: Int? = null,
)

@Serializable
data class BoardStrategy(
    val id: Int,
    val active: Boolean = true,
    val note: String = "",
    val candidates: Int = 0,
    val held: List<String> = emptyList(),
    val picks: List<RadarPick> = emptyList(),
)

@Serializable
data class Board(
    val strategies: List<BoardStrategy> = emptyList(),
    val top5: List<RadarPick> = emptyList(),
)

@Serializable
data class RadarMarket(
    val breadth: Double = 0.0,
    @SerialName("breadth_ma5") val breadthMa5: Double = 0.0,
    @SerialName("breadth_chg10") val breadthChg10: Double? = null,
    @SerialName("ew_ret5") val ewRet5: Double? = null,
    val guard: Boolean = false,
    val weak30: Boolean = false,
    val weak20: Boolean = false,
    val label: String = "",
    val text: String = "",
)

@Serializable
data class RadarTier(val key: String, val label: String, val stocks: Int = 0)

@Serializable
data class RadarJenis(val key: String, val label: String, val desc: String = "")

/** One published radar (radar/latest.json). */
@Serializable
data class RadarDay(
    val version: Int = 1,
    val date: String,
    @SerialName("generated_at") val generatedAt: String = "",
    @SerialName("go_live") val goLive: String = "",
    val calibrated: String = "",
    val market: RadarMarket = RadarMarket(),
    val tiers: List<RadarTier> = emptyList(),
    val jenis: List<RadarJenis> = emptyList(),
    val strategies: Map<String, RadarStrategy> = emptyMap(),
    val specs: Map<String, Map<String, SpecBoard>> = emptyMap(),
    val boards: Map<String, Map<String, Board>> = emptyMap(),
    val bars: Map<String, List<JsonArray>> = emptyMap(),
) {
    fun strategy(id: Int): RadarStrategy? = strategies[id.toString()]
    fun spec(tier: String, jenis: String, id: Int): StrategySpec? = specs[tier]?.get(jenis)?.strategies?.firstOrNull { it.id == id }
    fun board(tier: String, jenis: String): Board? = boards[tier]?.get(jenis)
    fun tierLabel(tier: String): String = tiers.firstOrNull { it.key == tier }?.label ?: tier
    fun jenisLabel(jenis: String): String = JENIS_LABEL[jenis] ?: jenis

    companion object {
        val JENIS_LABEL = mapOf("profit" to "Profit tinggi", "winrate" to "Winrate tinggi")
        val JENIS_DESC = mapOf(
            "profit" to "Target lebar dari volatilitas atau resisten terdekat. Mengejar kenaikan besar, sesekali kena stop loss.",
            "winrate" to "Target kecil minimal 3% yang lebih sering tercapai. Untung tipis tapi konsisten.",
        )
    }

    /** Daily bars shipped with the radar for a picked stock. */
    fun barsOf(ticker: String): List<Bar> = bars[ticker].orEmpty().mapNotNull { a ->
        try {
            Bar(
                date = a[0].asStr() ?: return@mapNotNull null,
                open = a[1].asNum() ?: return@mapNotNull null,
                high = a[2].asNum() ?: return@mapNotNull null,
                low = a[3].asNum() ?: return@mapNotNull null,
                close = a[4].asNum() ?: return@mapNotNull null,
                volume = a.getOrNull(5).asNum() ?: 0.0,
                value = (a.getOrNull(5).asNum() ?: 0.0) * (a[4].asNum() ?: 0.0),
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** One published recommendation and what happened to it (radar/ledger_<tier>.json). */
@Serializable
data class LedgerRow(
    val date: String,
    val jenis: String,
    val sid: Int,
    /** true = pick of the combined top 5. */
    val g: Boolean = false,
    val ticker: String,
    val close: Double = 0.0,
    /** menunggu, batal, aktif, TP, SL, Waktu */
    val status: String = "",
    @SerialName("entry_date") val entryDate: String? = null,
    val entry: Double? = null,
    val tp: Double? = null,
    val sl: Double? = null,
    @SerialName("exit_date") val exitDate: String? = null,
    val exit: Double? = null,
    val pnl: Double? = null,
    val hold: Int? = null,
    val last: Double? = null,
) {
    val closed: Boolean get() = status == "TP" || status == "SL" || status == "Waktu"
}

@Serializable
data class Ledger(
    val date: String = "",
    @SerialName("go_live") val goLive: String = "",
    val rows: List<LedgerRow> = emptyList(),
)

@Serializable
data class StockHit(
    val tier: String,
    val jenis: String,
    val sid: Int,
    val rank: Int = 0,
    val of: Int = 0,
    /** top5, ditahan, pasar, peringkat */
    val status: String = "",
)

@Serializable
data class StockInfo(
    val name: String = "",
    val close: Double = 0.0,
    val chg: Double? = null,
    @SerialName("value_ma20") val valueMa20: Double? = null,
    val rsi14: Double? = null,
    val pos60: Double? = null,
    val gain60: Double? = null,
    @SerialName("vol_ratio") val volRatio: Double? = null,
    val eligible: List<String> = emptyList(),
    val hits: List<StockHit> = emptyList(),
)

@Serializable
data class RadarStocks(val date: String = "", val stocks: Map<String, StockInfo> = emptyMap())

/** Radar origin of a saved signal (portfolio entries taken from the radar). */
@Serializable
data class RadarTag(
    val jenis: String,
    val tier: String,
    val sid: Int,
    val name: String,
    val icon: String = "",
    val color: String = "#3D8BFF",
    val combined: Boolean = false,
    val why: List<String> = emptyList(),
    val exitText: String = "",
    val regText: String = "",
    val desc: String = "",
)


@Serializable
data class BacktestMonthInfo(val month: String, val label: String = "", val start: String = "", val end: String = "", val trades: Int = 0)

@Serializable
data class BacktestIndex(val period: String = "", val months: List<BacktestMonthInfo> = emptyList())

@Serializable
data class BacktestMonthFile(val month: String = "", val rows: List<JsonArray> = emptyList())

/** One simulated trade: signal at the close, buy next session, sell at TP, SL or the end of the hold. */
data class BtTrade(
    val jenis: String, val tier: String, val combined: Boolean, val sid: Int, val ticker: String,
    val signal: String, val buy: String, val sell: String, val close: Double, val entry: Double,
    val tp: Double, val sl: Double, val pnl: Double, val hold: Int, val exit: String,
) {
    companion object {
        fun parse(a: JsonArray): BtTrade? = try {
            BtTrade(
                a[0].asStr()!!, a[1].asStr()!!, (a[2].asNum() ?: 0.0) > 0, a[3].asNum()!!.toInt(), a[4].asStr()!!,
                a[5].asStr()!!, a[6].asStr()!!, a[7].asStr()!!, a[8].asNum()!!, a[9].asNum()!!,
                a[10].asNum()!!, a[11].asNum()!!, a[12].asNum()!!, a[13].asNum()!!.toInt(), a[14].asStr()!!,
            )
        } catch (e: Exception) {
            null
        }
    }
}
