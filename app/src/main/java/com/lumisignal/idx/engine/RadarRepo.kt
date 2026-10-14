package com.lumisignal.idx.engine

import android.content.Context
import com.lumisignal.idx.data.Ledger
import com.lumisignal.idx.data.LumiJson
import com.lumisignal.idx.data.RadarDay
import com.lumisignal.idx.data.RadarStocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Load state of the radar download. */
data class RadarState(
    val loading: Boolean = false,
    val error: String? = null,
    val fetchedAt: Long = 0L,
)

/**
 * Radar Besok results, screened every trading day after the close on the server with the calibrated
 * strategies (same engine as the backtest) and published as JSON on the repo's market-data branch.
 * One download gives every strategy of every liquidity tier at once; files are cached for offline use.
 */
object RadarRepo {
    private const val BASE = "https://raw.githubusercontent.com/vickyvam72/backuplumisignal/market-data/radar/"

    /**
     * The server publishes ±16:30 WIB; the app releases the new buy signals to the user at 17:00 WIB
     * (notification, home screen and strategy history all switch at the same moment).
     */
    val READY_AT: LocalTime = LocalTime.of(17, 0)

    val TIER_KEYS = listOf("semua", "250jt", "1M", "5M", "20M")

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    private lateinit var dir: File
    private lateinit var appContext: Context
    private val lock = Mutex()

    private val _day = MutableStateFlow<RadarDay?>(null)
    val day: StateFlow<RadarDay?> = _day.asStateFlow()

    private val _state = MutableStateFlow(RadarState())
    val state: StateFlow<RadarState> = _state.asStateFlow()

    private val _ledgers = MutableStateFlow<Map<String, Ledger>>(emptyMap())
    val ledgers: StateFlow<Map<String, Ledger>> = _ledgers.asStateFlow()

    private val _stocks = MutableStateFlow<RadarStocks?>(null)
    val stocks: StateFlow<RadarStocks?> = _stocks.asStateFlow()

    private val _tier = MutableStateFlow("1M")
    val tier: StateFlow<String> = _tier.asStateFlow()

    @Volatile
    private var ready = false

    @Synchronized
    fun init(ctx: Context) {
        if (ready) return
        appContext = ctx.applicationContext
        dir = File(appContext.filesDir, "radar").apply { mkdirs() }
        _tier.value = if (!com.lumisignal.idx.UiConfig.SHOW_TIER_PICKER) com.lumisignal.idx.UiConfig.FIXED_TIER
        else prefs().getString("tier", "1M") ?: "1M"
        _day.value = readCached("latest.json")?.let { parseDay(it) }
        _stocks.value = readCached("stocks.json")?.let { runCatching { LumiJson.decodeFromString(RadarStocks.serializer(), it) }.getOrNull() }
        _ledgers.value = TIER_KEYS.mapNotNull { t ->
            readCached("ledger_$t.json")?.let { txt -> runCatching { t to LumiJson.decodeFromString(Ledger.serializer(), txt) }.getOrNull() }
        }.toMap()
        ready = true
    }

    private fun prefs() = appContext.getSharedPreferences("lumi_radar", Context.MODE_PRIVATE)

    /** Last chosen signal type (screening and history open on it). */
    var jenis: String
        get() = prefs().getString("jenis", "profit") ?: "profit"
        set(v) = prefs().edit().putString("jenis", v).apply()

    fun setTier(t: String) {
        if (t !in TIER_KEYS || !com.lumisignal.idx.UiConfig.SHOW_TIER_PICKER) return
        _tier.value = t
        prefs().edit().putString("tier", t).apply()
    }

    /** The first screening (connection check) has been done; from then on the home shows the signals. */
    var setupDone: Boolean
        get() = prefs().getBoolean("setup_done", false)
        set(v) = prefs().edit().putBoolean("setup_done", v).apply()

    var notifiedDate: String
        get() = prefs().getString("notified", "") ?: ""
        set(v) = prefs().edit().putString("notified", v).apply()

    private fun readCached(name: String): String? = try {
        File(dir, name).takeIf { it.exists() }?.readText()
    } catch (e: Exception) {
        null
    }

    private fun writeCached(name: String, text: String) {
        try {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(File(dir, name))) {
                File(dir, name).writeText(text)
                tmp.delete()
            }
        } catch (e: Exception) {
        }
    }

    private fun parseDay(text: String): RadarDay? = try {
        LumiJson.decodeFromString(RadarDay.serializer(), text)
    } catch (e: Exception) {
        null
    }

    private suspend fun download(name: String): String = withContext(Dispatchers.IO) {
        // A changing query string skips the CDN copy so a fresh radar shows up right after it is published.
        val req = Request.Builder().url(BASE + name + "?t=" + System.currentTimeMillis() / 60_000)
            .header("Cache-Control", "no-cache").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException(if (r.code == 404) "Radar belum tersedia di server" else "Server radar membalas ${r.code}")
            r.body?.string() ?: throw IOException("Respons kosong dari server radar")
        }
    }

    /** Downloads the latest radar. Returns the radar (fresh or cached) or throws when nothing is available. */
    suspend fun refresh(): RadarDay = lock.withLock {
        _state.value = _state.value.copy(loading = true, error = null)
        try {
            val text = download("latest.json")
            val fetched = parseDay(text) ?: throw IOException("Format radar tidak dikenali, perbarui aplikasi")
            // A radar published before 17:00 is held back until then (a fresh install takes what there is).
            val held = _day.value
            val d = if (held != null && fetched.date > expectedDate()) held else fetched
            if (d === fetched) {
                writeCached("latest.json", text)
                _day.value = d
            }
            _state.value = RadarState(loading = false, fetchedAt = System.currentTimeMillis())
            d
        } catch (e: Exception) {
            val msg = when (e) {
                is java.net.UnknownHostException -> "Tidak ada koneksi internet"
                is java.net.SocketTimeoutException -> "Koneksi ke server radar terlalu lama"
                else -> e.message ?: "Gagal memuat radar"
            }
            _state.value = _state.value.copy(loading = false, error = msg)
            _day.value ?: throw IOException(msg)
        }
    }

    /** Live results of every published pick of one tier. */
    suspend fun refreshLedger(tier: String): Ledger? {
        return try {
            val text = download("ledger_$tier.json")
            val l = LumiJson.decodeFromString(Ledger.serializer(), text)
            writeCached("ledger_$tier.json", text)
            _ledgers.value = _ledgers.value + (tier to l)
            l
        } catch (e: Exception) {
            _ledgers.value[tier]
        }
    }

    /** Per-stock strategy check of the latest radar (manual analysis). */
    suspend fun refreshStocks(): RadarStocks? {
        val cached = _stocks.value
        if (cached != null && cached.date == _day.value?.date) return cached
        return try {
            val text = download("stocks.json")
            val s = LumiJson.decodeFromString(RadarStocks.serializer(), text)
            writeCached("stocks.json", text)
            _stocks.value = s
            s
        } catch (e: Exception) {
            cached
        }
    }

    private val _btIndex = MutableStateFlow<com.lumisignal.idx.data.BacktestIndex?>(null)
    val btIndex: StateFlow<com.lumisignal.idx.data.BacktestIndex?> = _btIndex.asStateFlow()
    private val btMonths = HashMap<String, List<com.lumisignal.idx.data.BtTrade>>()

    private var btIndexDay = ""

    /**
     * Months available in the backtest. Downloaded again once per day (and after a new radar), because
     * finished live trades (TP or SL hit) are added to it by the server every trading day.
     */
    suspend fun backtestIndex(): com.lumisignal.idx.data.BacktestIndex? {
        val stamp = Market.today() + "|" + (_day.value?.date ?: "")
        _btIndex.value?.takeIf { btIndexDay == stamp }?.let { return it }
        val fresh = try {
            download("backtest/index.json").also { writeCached("bt_index.json", it) }
        } catch (e: Exception) {
            null
        }
        val text = fresh ?: readCached("bt_index.json") ?: return _btIndex.value
        val idx = runCatching { LumiJson.decodeFromString(com.lumisignal.idx.data.BacktestIndex.serializer(), text) }.getOrNull() ?: return _btIndex.value
        if (fresh != null) btIndexDay = stamp
        _btIndex.value = idx
        return idx
    }

    private fun parseMonth(text: String?): List<com.lumisignal.idx.data.BtTrade>? = text?.let { t ->
        runCatching { LumiJson.decodeFromString(com.lumisignal.idx.data.BacktestMonthFile.serializer(), t) }.getOrNull()
            ?.rows?.mapNotNull { com.lumisignal.idx.data.BtTrade.parse(it) }
    }

    /** All trades of one month (signal month); downloaded again when the index lists more trades. */
    suspend fun backtestMonth(month: String): List<com.lumisignal.idx.data.BtTrade>? {
        val expected = _btIndex.value?.months?.firstOrNull { it.month == month }?.trades
        btMonths[month]?.takeIf { expected == null || it.size == expected }?.let { return it }
        val cached = parseMonth(readCached("bt_$month.json"))
        if (cached != null && (expected == null || cached.size == expected)) return cached.also { btMonths[month] = it }
        val fresh = try {
            download("backtest/$month.json").let { txt -> parseMonth(txt)?.also { writeCached("bt_$month.json", txt) } }
        } catch (e: Exception) {
            null
        }
        return (fresh ?: cached)?.also { btMonths[month] = it }
    }

    /** Forgets the downloaded screening results (they are fetched again on the next screening). */
    fun clearLocal() {
        listOf("latest.json", "stocks.json").forEach { File(dir, it).delete() }
        TIER_KEYS.forEach { File(dir, "ledger_$it.json").delete() }
        _day.value = null
        _stocks.value = null
        _ledgers.value = emptyMap()
        _state.value = RadarState()
        notifiedDate = ""
    }

    /** Closing date the newest radar should have now (weekends skipped; exchange holidays are not known). */
    fun expectedDate(now: java.time.ZonedDateTime = Market.now()): String {
        var d = now.toLocalDate()
        if (!Market.isWeekday(d) || now.toLocalTime() < READY_AT) {
            d = d.minusDays(1)
            while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.minusDays(1)
        }
        return d.toString()
    }

    /** True when a newer radar should already be on the server. */
    fun isStale(now: java.time.ZonedDateTime = Market.now()): Boolean {
        val d = _day.value ?: return true
        return d.date < expectedDate(now)
    }
}
