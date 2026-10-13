package com.lumisignal.idx.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One daily session. Volume is in lots (Stockbit convention), value in IDR. */
@Serializable
data class Bar(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val average: Double = 0.0,
    val volume: Double = 0.0,
    val value: Double = 0.0,
    val freq: Double = 0.0,
)

@Serializable
data class BrokerFlow(
    val code: String,
    val type: String = "",
    val lots: Double = 0.0,
    val value: Double = 0.0,
    val avgPrice: Double = 0.0,
    val freq: Double = 0.0,
)

/** Net broker summary for one session or a date range (REGULER board). */
@Serializable
data class BrokerSummary(
    val from: String = "",
    val to: String = "",
    val buyers: List<BrokerFlow> = emptyList(),
    val sellers: List<BrokerFlow> = emptyList(),
    val accdist: String = "",
    val top1Acc: String = "",
    val top3Acc: String = "",
    val top5Acc: String = "",
    val totalBuyer: Int = 0,
    val totalSeller: Int = 0,
) {
    val totalBuyValue: Double get() = buyers.sumOf { it.value }

    fun buyShare(n: Int): Double {
        val total = totalBuyValue
        if (total <= 0.0) return 0.0
        return buyers.take(n).sumOf { it.value } / total
    }

    /** Best available accumulation/distribution label. */
    val mainLabel: String
        get() = listOf(accdist, top3Acc, top5Acc, top1Acc).firstOrNull { it.isNotBlank() } ?: "Netral"

    /** 0 = distribution/neutral, 0.7 = Acc, 1.0 = Big Acc. */
    fun accStrength(label: String = mainLabel): Double {
        val l = label.lowercase()
        return when {
            l.contains("dist") -> 0.0
            l.contains("big") && l.contains("acc") -> 1.0
            l.contains("acc") -> 0.7
            else -> 0.0
        }
    }

    val isAcc: Boolean get() = accStrength() > 0.0

    val sellerCount: Int get() = if (totalSeller > 0) totalSeller else sellers.size
    val buyerCount: Int get() = if (totalBuyer > 0) totalBuyer else buyers.size
}

@Serializable
data class Quote(
    val symbol: String,
    val name: String = "",
    val price: Double? = null,
    val change: Double? = null,
    val changePct: Double? = null,
)

@Serializable
data class Check(
    val label: String,
    val detail: String,
    val pass: Boolean,
    /** "bandar" or "teknikal" — which evidence family this criterion belongs to. */
    val kind: String = "teknikal",
    /** Required criteria decide the signal; optional ones only add to the score. */
    val required: Boolean = true,
)

@Serializable
data class TradePlan(
    val entryLow: Double,
    val entryHigh: Double,
    val cancelBelow: Double? = null,
    val cancelReason: String = "",
    val stopLoss: Double,
    val slReason: String,
    @SerialName("tp1") val tp: Double,
    @SerialName("tp1Reason") val tpReason: String,
    val maxHoldDays: Int = 10,
)

@Serializable
data class StrategyEval(
    val strategyId: Int,
    val passed: Boolean,
    val score: Int,
    val checks: List<Check>,
    val highlights: List<String> = emptyList(),
    val plan: TradePlan? = null,
    val lowLiquidity: Boolean = false,
    val liquidityNote: String = "",
)

@Serializable
enum class SignalStatus { WAITING, ACTIVE, TP, SL, CANCELLED, EXPIRED }

@Serializable
data class LivePoint(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val last: Double,
    val at: Long,
)

/**
 * A screened signal. The same record type is used for both histories: source = STRATEGY for
 * screening results (feeds the strategy win-rate) and PORTO for items the user added to the
 * portfolio (feeds the portfolio win-rate).
 */
@Serializable
data class Signal(
    val id: String,
    val source: String,
    val symbol: String,
    val name: String = "",
    val strategyId: Int,
    val refDate: String,
    val createdAt: Long,
    val close: Double,
    val changePct: Double,
    val eval: StrategyEval,
    val bars: List<Bar> = emptyList(),
    val broker: BrokerSummary? = null,
    val broker20: BrokerSummary? = null,
    val linkedId: String? = null,
    val customEntry: Double? = null,
    val startDate: String? = null,
    val trackBars: List<Bar> = emptyList(),
    val live: LivePoint? = null,
    /** Intraday minute path per date (only stored for days where the order of TP/SL mattered). */
    val dayPaths: Map<String, List<Double>> = emptyMap(),
    val status: SignalStatus = SignalStatus.WAITING,
    val statusNote: String = "",
    val entryPrice: Double? = null,
    val entryDate: String? = null,
    val exitPrice: Double? = null,
    val exitDate: String? = null,
    val lastPrice: Double? = null,
    val lastUpdate: Long = 0L,
) {
    val isOpen: Boolean
        get() = status == SignalStatus.WAITING || status == SignalStatus.ACTIVE

    /** Profit/loss % of a filled trade at exit (closed) or last price (open). */
    val pnlPct: Double?
        get() {
            val e = entryPrice ?: return null
            if (e <= 0) return null
            val px = exitPrice ?: lastPrice ?: return null
            return (px / e - 1.0) * 100.0
        }

    /** true = win, false = loss, null = not decided / not counted. */
    val outcome: Boolean?
        get() = when (status) {
            SignalStatus.TP -> true
            SignalStatus.SL -> false
            SignalStatus.EXPIRED -> (pnlPct ?: 0.0) > 0.0
            else -> null
        }

    val countsAsWin: Boolean get() = outcome == true
    val countsAsLoss: Boolean get() = outcome == false
}

const val SOURCE_STRATEGY = "STRATEGY"
const val SOURCE_PORTO = "PORTO"
