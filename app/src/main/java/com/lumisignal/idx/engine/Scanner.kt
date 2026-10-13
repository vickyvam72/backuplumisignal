package com.lumisignal.idx.engine

import com.lumisignal.idx.Graph
import com.lumisignal.idx.data.AuthExpiredException
import com.lumisignal.idx.data.BrokerSummary
import com.lumisignal.idx.data.SEED_SYMBOLS
import com.lumisignal.idx.data.SOURCE_PORTO
import com.lumisignal.idx.data.SOURCE_STRATEGY
import com.lumisignal.idx.data.Signal
import com.lumisignal.idx.data.StrategyEval
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.math.max

data class ScanState(
    val running: Boolean = false,
    val strategyId: Int = 0,
    val phase: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val message: String = "",
    val refDate: String = "",
    val candidates: Int = 0,
    val passed: Int = 0,
    val resultIds: List<String> = emptyList(),
    val error: String? = null,
    val finishedAt: Long = 0L,
    val startedAt: Long = 0L,
)

object ScanManager {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    fun update(transform: (ScanState) -> ScanState) {
        _state.value = transform(_state.value)
    }

    fun reset() {
        if (!_state.value.running) _state.value = ScanState()
    }
}

/** Result of analysing one ticker against every strategy (manual analysis). */
data class ManualResult(
    val symbol: String,
    val name: String,
    val refDate: String,
    val data: StockData,
    val evals: List<StrategyEval>,
    val lastPrice: Double?,
)

object Scanner {
    private const val STAGE2_LIMIT = 40
    private const val TOP_N = 5
    private const val UNIVERSE_TTL = 7L * 24 * 3600 * 1000

    private fun progress(phase: String, done: Int, total: Int, message: String) =
        ScanManager.update { it.copy(phase = phase, done = done, total = total, message = message) }

    /** Reference date = the last COMPLETED session, read from a liquid bellwether. */
    suspend fun referenceDate(): String {
        for (sym in listOf("BBCA", "BBRI", "TLKM")) {
            try {
                val bars = Market.completedOnly(Graph.api.historyPage(sym, 1).first)
                if (bars.isNotEmpty()) return bars.last().date
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: Exception) {
            }
        }
        throw IllegalStateException("Tidak bisa menentukan tanggal closing terakhir dari Stockbit")
    }

    private suspend fun universe(): List<String> {
        val (updatedAt, cached) = Graph.cache.universe()
        if (cached.size >= 300 && System.currentTimeMillis() - updatedAt < UNIVERSE_TTL) return cached
        progress("Menyiapkan daftar saham", 0, 0, "Memuat daftar emiten dari Stockbit…")
        val fresh = try {
            Graph.api.loadUniverse { msg -> progress("Menyiapkan daftar saham", 0, 0, msg) }
        } catch (e: AuthExpiredException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        return when {
            fresh.size >= 300 -> fresh.also { Graph.cache.saveUniverse(it) }
            cached.isNotEmpty() -> (cached + fresh).distinct().sorted()
            else -> (fresh + SEED_SYMBOLS).distinct().sorted()
        }
    }

    /** Runs a full screening for [strategy]. Returns the saved signal ids (best first). */
    suspend fun run(strategy: Strategy): List<String> {
        ScanManager.update {
            ScanState(running = true, strategyId = strategy.id, phase = "Memulai", startedAt = System.currentTimeMillis())
        }
        try {
            val refDate = referenceDate()
            Graph.cache.lastRefDate = refDate
            ScanManager.update { it.copy(refDate = refDate) }
            val symbols = universe()

            // ---- Stage 1: one page (12 sessions) for every ticker, cached per reference date.
            val stage1 = ConcurrentHashMap(Graph.cache.loadStage1(refDate))
            val missing = symbols.filter { it !in stage1.keys }
            val total1 = symbols.size
            val done1 = AtomicInteger(total1 - missing.size)
            progress("Tahap 1 · Data harga", done1.get(), total1, "Mengambil candle harian $total1 saham")
            val saveLock = Mutex()
            runPool(missing) { sym ->
                val bars = try {
                    Market.completedOnly(Graph.api.historyPage(sym, 1).first).filter { it.date <= refDate }
                } catch (e: AuthExpiredException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (bars != null) stage1[sym] = bars
                val d = done1.incrementAndGet()
                progress("Tahap 1 · Data harga", d, total1, "$sym · $d/$total1 saham")
                if (d % 100 == 0) saveLock.withLock { Graph.cache.saveStage1(refDate, HashMap(stage1)) }
            }
            Graph.cache.saveStage1(refDate, HashMap(stage1))

            // ---- Prefilter on the reference session.
            val ranked = stage1.entries
                .filter { (_, bars) -> bars.isNotEmpty() && bars.last().date == refDate }
                .mapNotNull { (sym, bars) -> StrategyEngine.prefilter(strategy, bars)?.let { sym to it } }
                .sortedByDescending { it.second }
                .take(STAGE2_LIMIT)
                .map { it.first }
            ScanManager.update { it.copy(candidates = ranked.size) }

            // ---- Stage 2: deeper history + broker summary for candidates only.
            val evaluated = ConcurrentHashMap<String, Pair<StockData, StrategyEval>>()
            val done2 = AtomicInteger(0)
            progress("Tahap 2 · Analisa bandar", 0, ranked.size, "${ranked.size} kandidat lolos saringan awal")
            runPool(ranked) { sym ->
                try {
                    val data = loadStockData(sym, refDate, strategy.needsRange, 26, 3)
                    if (data != null) {
                        val ev = StrategyEngine.evaluate(strategy, data)
                        if (ev.passed) evaluated[sym] = data to ev
                    }
                } catch (e: AuthExpiredException) {
                    throw e
                } catch (e: Exception) {
                }
                val d = done2.incrementAndGet()
                progress("Tahap 2 · Analisa bandar", d, ranked.size, "$sym · ${evaluated.size} lolos")
            }
            ScanManager.update { it.copy(passed = evaluated.size) }

            // ---- Final: top N, enrich with 60 sessions for chart & resistance levels.
            val top = evaluated.entries.sortedByDescending { it.value.second.score }.take(TOP_N)
            progress("Menyusun hasil", 0, top.size, "Menghitung entry, stop loss & target")
            val signals = mutableListOf<Signal>()
            top.forEachIndexed { i, (sym, pair) ->
                coroutineContext.ensureActive()
                var data = pair.first
                var ev = pair.second
                try {
                    val full = Market.completedOnly(Graph.api.history(sym, 60, 6)).filter { it.date <= refDate }
                    if (full.size > data.bars.size) {
                        val richer = data.copy(bars = full)
                        val ev2 = StrategyEngine.evaluate(strategy, richer)
                        if (ev2.passed) {
                            data = richer
                            ev = ev2
                        }
                    }
                } catch (e: AuthExpiredException) {
                    throw e
                } catch (e: Exception) {
                }
                val name = try {
                    Graph.api.quote(sym).name
                } catch (e: AuthExpiredException) {
                    throw e
                } catch (e: Exception) {
                    ""
                }
                signals += buildSignal(SOURCE_STRATEGY, strategy, data.copy(name = name), ev, refDate)
                progress("Menyusun hasil", i + 1, top.size, sym)
            }

            val existing = Graph.history.items.value.associateBy { it.id }
            val toSave = signals.filter { it.id !in existing }
            if (toSave.isNotEmpty()) Graph.history.upsert(toSave)
            val ids = signals.map { it.id }
            ScanManager.update {
                it.copy(
                    running = false, phase = "Selesai", resultIds = ids, finishedAt = System.currentTimeMillis(),
                    message = if (ids.isEmpty()) "Tidak ada saham yang memenuhi semua syarat strategi ini" else "${ids.size} saham terbaik tersimpan di Riwayat Strategi",
                )
            }
            return ids
        } catch (e: AuthExpiredException) {
            ScanManager.update { it.copy(running = false, error = e.message, phase = "Gagal", finishedAt = System.currentTimeMillis()) }
            throw e
        } catch (e: Exception) {
            ScanManager.update {
                it.copy(running = false, error = e.message ?: "Terjadi kesalahan", phase = "Gagal", finishedAt = System.currentTimeMillis())
            }
            throw e
        }
    }

    private suspend fun runPool(items: List<String>, workers: Int = 3, block: suspend (String) -> Unit) {
        if (items.isEmpty()) return
        val channel = Channel<String>(Channel.UNLIMITED)
        items.forEach { channel.trySend(it) }
        channel.close()
        coroutineScope {
            repeat(workers) {
                launch {
                    for (item in channel) block(item)
                }
            }
        }
    }

    /** Daily bars up to [refDate] + broker summary of that session (+ 20-session range if needed). */
    suspend fun loadStockData(symbol: String, refDate: String, needRange: Boolean, bars: Int, pages: Int): StockData? {
        val history = Market.completedOnly(Graph.api.history(symbol, bars, pages)).filter { it.date <= refDate }
        if (history.size < 6 || history.last().date != refDate) return null
        val day: BrokerSummary? = try {
            Graph.api.brokerSummary(symbol, refDate, refDate)
        } catch (e: AuthExpiredException) {
            throw e
        } catch (e: Exception) {
            null
        }
        var range: BrokerSummary? = null
        if (needRange) {
            val from = history[max(0, history.size - 20)].date
            range = try {
                Graph.api.brokerSummary(symbol, from, refDate)
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        return StockData(symbol = symbol, bars = history, day = day, range20 = range)
    }

    fun buildSignal(source: String, strategy: Strategy, data: StockData, ev: StrategyEval, refDate: String, linkedId: String? = null, customEntry: Double? = null, startDate: String? = null): Signal {
        val bars = data.bars
        val t = bars.last()
        val prev = bars.getOrNull(bars.size - 2)
        val id = if (source == SOURCE_PORTO) "P-${strategy.id}-${data.symbol}-$refDate-${System.currentTimeMillis()}" else "${strategy.id}-${data.symbol}-$refDate"
        return Signal(
            id = id,
            source = source,
            symbol = data.symbol,
            name = data.name,
            strategyId = strategy.id,
            refDate = refDate,
            createdAt = System.currentTimeMillis(),
            close = t.close,
            changePct = Market.changePct(t, prev),
            eval = ev,
            bars = bars.takeLast(60),
            broker = data.day?.let { it.copy(buyers = it.buyers.take(10), sellers = it.sellers.take(10)) },
            broker20 = data.range20?.let { it.copy(buyers = it.buyers.take(10), sellers = it.sellers.take(10)) },
            linkedId = linkedId,
            customEntry = customEntry,
            startDate = startDate,
            lastPrice = t.close,
        )
    }

    /** Copies a strategy signal into the portfolio history. */
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
        return tracked
    }

    private fun previousIso(iso: String): String = try {
        java.time.LocalDate.parse(iso).minusDays(1).toString()
    } catch (e: Exception) {
        iso
    }

    /** Analyses one ticker against all five strategies. */
    suspend fun analyzeManual(symbolRaw: String): ManualResult {
        val symbol = symbolRaw.trim().uppercase()
        val bars = Market.completedOnly(Graph.api.history(symbol, 60, 6))
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
        val data = StockData(symbol, quote?.name ?: "", bars, day, range)
        val evals = Strategy.entries.map { StrategyEngine.evaluate(it, data) }
        return ManualResult(symbol, data.name, refDate, data, evals, quote?.price)
    }
}
