package com.lumisignal.idx.engine

import com.lumisignal.idx.Graph
import com.lumisignal.idx.data.AuthExpiredException
import com.lumisignal.idx.data.Bar
import com.lumisignal.idx.data.BrokerSummary
import com.lumisignal.idx.data.SOURCE_PORTO
import com.lumisignal.idx.data.Signal
import kotlin.math.max

/** One stock read from Stockbit for the manual check: daily bars, broker flow and the live price. */
data class ManualResult(
    val symbol: String,
    val name: String,
    val refDate: String,
    val bars: List<Bar>,
    val day: BrokerSummary?,
    val range20: BrokerSummary?,
    val lastPrice: Double?,
)

object Scanner {

    /** Copies a (legacy) strategy signal into the portfolio history. */
    fun addToPorto(sig: Signal, customEntry: Double?): Signal {
        val today = Market.today()
        val start = if (customEntry != null) maxOf(sig.refDate, if (Market.sessionInProgress()) previousIso(today) else today) else null
        val porto = sig.copy(
            id = "P-${sig.id}-${System.currentTimeMillis()}",
            source = SOURCE_PORTO,
            linkedId = sig.id,
            createdAt = System.currentTimeMillis(),
            customEntry = customEntry,
            startDate = start,
            status = if (customEntry != null) com.lumisignal.idx.data.SignalStatus.ACTIVE else com.lumisignal.idx.data.SignalStatus.WAITING,
        )
        val tracked = Tracker.track(porto)
        Graph.history.upsert(listOf(tracked))
        com.lumisignal.idx.service.Monitor.runNow(Graph.context)
        return tracked
    }

    private fun previousIso(iso: String): String = try {
        java.time.LocalDate.parse(iso).minusDays(1).toString()
    } catch (e: Exception) {
        iso
    }

    /** Reads one ticker from Stockbit: 120 completed sessions, broker summary of the day and of 20 days. */
    /** Screenshot tests only: replaces the Stockbit download. */
    @Volatile
    var testSource: ((String) -> ManualResult)? = null

    suspend fun analyzeManual(symbolRaw: String): ManualResult {
        val symbol = symbolRaw.trim().uppercase()
        testSource?.let { return it(symbol) }
        val bars = Market.completedOnly(Graph.api.history(symbol, 120, 12))
        if (bars.size < 6) throw IllegalStateException("Data harga $symbol tidak cukup atau kode saham tidak ditemukan")
        val refDate = bars.last().date
        val day = try {
            Graph.api.brokerSummary(symbol, refDate, refDate)
        } catch (e: AuthExpiredException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val from = bars[max(0, bars.size - 20)].date
        val range = try {
            Graph.api.brokerSummary(symbol, from, refDate)
        } catch (e: AuthExpiredException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val quote = try {
            Graph.api.quote(symbol)
        } catch (e: AuthExpiredException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return ManualResult(symbol, quote?.name ?: "", refDate, bars, day, range, quote?.price)
    }
}
