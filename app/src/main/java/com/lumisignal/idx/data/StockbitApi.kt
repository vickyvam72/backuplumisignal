package com.lumisignal.idx.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : Exception(message)

/** Thrown when the session is gone and only a fresh login can fix it. */
class AuthExpiredException : Exception("Sesi Stockbit berakhir. Silakan login ulang.")

/**
 * Read-only client for Stockbit's exodus API. Every call carries the captured bearer token and
 * browser-shaped headers, is rate limited (3 in flight, spaced) and retried on 429/5xx.
 */
class StockbitApi(private val session: SessionStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    private val inFlight = Semaphore(3)
    private val spacing = Mutex()
    private var lastStart = 0L
    private val refreshLock = Mutex()

    /** Minimum gap between request starts, ms. Keeps traffic human-ish. */
    var minGapMs: Long = 180L

    private suspend fun pace() {
        spacing.withLock {
            val now = System.currentTimeMillis()
            val wait = lastStart + minGapMs - now
            if (wait > 0) delay(wait)
            lastStart = System.currentTimeMillis()
        }
    }

    suspend fun get(path: String, params: List<Pair<String, String>> = emptyList()): JsonElement =
        request("GET", path, params, null)

    suspend fun post(path: String, body: String): JsonElement = request("POST", path, emptyList(), body)

    private suspend fun request(
        method: String,
        path: String,
        params: List<Pair<String, String>>,
        body: String?,
    ): JsonElement {
        var attempt = 0
        var refreshed = false
        while (true) {
            attempt++
            val token = session.access ?: throw AuthExpiredException()
            val result = inFlight.withPermit {
                pace()
                execute(method, path, params, body, token)
            }
            when {
                result.code in 200..299 -> {
                    return try {
                        LumiJson.parseToJsonElement(result.body.ifBlank { "{}" })
                    } catch (e: Exception) {
                        throw ApiException(result.code, "Respons tidak valid dari Stockbit")
                    }
                }
                result.code == 401 -> {
                    if (!refreshed && tryRefresh(token)) {
                        refreshed = true
                        continue
                    }
                    session.markExpired()
                    throw AuthExpiredException()
                }
                (result.code == 429 || result.code >= 500 || result.code == -1) && attempt < 4 -> {
                    delay(800L * attempt * attempt)
                    continue
                }
                else -> throw ApiException(result.code, errorMessage(result))
            }
        }
    }

    private data class Raw(val code: Int, val body: String)

    private suspend fun execute(
        method: String,
        path: String,
        params: List<Pair<String, String>>,
        body: String?,
        token: String,
    ): Raw = withContext(Dispatchers.IO) {
        val urlBuilder = (BASE + path).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val reqBuilder = Request.Builder()
            .url(urlBuilder.build())
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("Origin", "https://stockbit.com")
            .header("Referer", "https://stockbit.com/")
            .header("User-Agent", session.userAgent)
        if (method == "POST") {
            reqBuilder.post((body ?: "").toRequestBody(JSON_TYPE))
        }
        try {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                Raw(resp.code, resp.body?.string() ?: "")
            }
        } catch (e: IOException) {
            Raw(-1, e.message ?: "network error")
        }
    }

    private fun errorMessage(raw: Raw): String {
        if (raw.code == -1) return "Koneksi gagal: ${raw.body}"
        val msg = try {
            LumiJson.parseToJsonElement(raw.body).path("message").asStr()
        } catch (e: Exception) {
            null
        }
        return "Stockbit ${raw.code}${if (msg.isNullOrBlank()) "" else ": $msg"}"
    }

    /** POST /login/refresh with the refresh token as bearer and an empty body. */
    private suspend fun tryRefresh(failedAccess: String): Boolean = refreshLock.withLock {
        // Another caller may already have refreshed while we waited.
        val current = session.access
        if (current != null && current != failedAccess) return@withLock true
        val refresh = session.refresh ?: return@withLock false
        val raw = withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$BASE/login/refresh")
                    .header("Authorization", "Bearer $refresh")
                    .header("Accept", "application/json")
                    .header("Origin", "https://stockbit.com")
                    .header("Referer", "https://stockbit.com/")
                    .header("User-Agent", session.userAgent)
                    .post("".toRequestBody(JSON_TYPE))
                    .build()
                client.newCall(req).execute().use { Raw(it.code, it.body?.string() ?: "") }
            } catch (e: IOException) {
                Raw(-1, "")
            }
        }
        if (raw.code !in 200..299) return@withLock false
        val tokens = TokenParser.fromJsonText(raw.body) ?: return@withLock false
        session.save(tokens.access, tokens.refresh)
        true
    }

    /** Checks a freshly captured token before it is stored (no refresh, no side effects). */
    suspend fun validate(token: String): Boolean {
        val raw = inFlight.withPermit {
            pace()
            execute("GET", "/emitten/BBCA/info", emptyList(), null, token)
        }
        return raw.code in 200..299
    }

    /** Forces a token renewal with the refresh token (daily renewal). True when new tokens were saved. */
    suspend fun refreshNow(): Boolean {
        val access = session.access ?: return false
        return tryRefresh(access) && session.access != access
    }

    /** Refreshes ahead of expiry (called by the background worker). */
    suspend fun refreshIfNeeded() {
        val access = session.access ?: return
        val exp = TokenParser.jwtExp(access)
        if (exp > 0 && exp - System.currentTimeMillis() / 1000 < 3 * 3600) {
            tryRefresh(access)
        }
    }

    // ------------------------------------------------------------------ endpoints

    /** One page (12 sessions, newest first) of daily OHLCV. Returns bars oldest-first + hasNext. */
    suspend fun historyPage(symbol: String, page: Int): Pair<List<Bar>, Boolean> {
        val root = get("/company-price-feed/historical/summary/$symbol", listOf("page" to page.toString()))
        val data = root.path("data")
        val rows = (data.path("result") as? JsonArray)
            ?: findObjectArrays(data).firstOrNull()
            ?: JsonArray(emptyList())
        val bars = rows.mapNotNull { parseBar(it) }
        val next = data.path("paginate", "next_page")
        val hasNext = next != null && next.asStr().let { !it.isNullOrBlank() && it != "0" && it != "false" }
        return bars.sortedBy { it.date } to hasNext
    }

    /** At least [count] most recent sessions (oldest first). */
    suspend fun history(symbol: String, count: Int, maxPages: Int = 6): List<Bar> {
        val all = mutableMapOf<String, Bar>()
        var page = 1
        while (page <= maxPages) {
            val (bars, hasNext) = historyPage(symbol, page)
            bars.forEach { all[it.date] = it }
            if (bars.isEmpty() || all.size >= count || !hasNext) break
            page++
        }
        return all.values.sortedBy { it.date }
    }

    /** Net broker summary on the REGULER board for a session or range (no `period` param). */
    suspend fun brokerSummary(symbol: String, from: String, to: String): BrokerSummary {
        val root = get(
            "/marketdetectors/$symbol",
            listOf(
                "transaction_type" to "TRANSACTION_TYPE_NET",
                "market_board" to "MARKET_BOARD_REGULER",
                "investor_type" to "INVESTOR_TYPE_ALL",
                "limit" to "50",
                "from" to from,
                "to" to to,
            ),
        )
        return parseBrokerSummary(root.dataOf(), from, to)
    }

    /** Minute-by-minute closes of the current session, oldest first (empty when unavailable). */
    suspend fun intradayPath(symbol: String): List<Double> {
        val root = get("/company-price-feed/prices/close", listOf("symbol" to symbol, "interval" to "1"))
        val data = root.path("data")
        val row = (data as? JsonArray)?.firstOrNull() ?: data
        val prices = row.path("prices") as? JsonArray ?: return emptyList()
        return prices.mapNotNull { it.asNum()?.takeIf { v -> v > 0 } }
    }

    suspend fun quote(symbol: String): Quote {
        val root = get("/emitten/$symbol/info")
        val d = root.dataOf()
        val price = d.path("price").asNum() ?: d.path("last").asNum() ?: d.path("close").asNum()
        val change = d.path("change").asNum()
        val pct = d.path("percentage").asNum() ?: d.path("change_percentage").asNum()
            ?: d.path("percent").asNum()
            ?: if (price != null && change != null && price - change != 0.0) change / (price - change) * 100 else null
        return Quote(
            symbol = symbol,
            name = d.path("name").asStr() ?: d.path("company_name").asStr() ?: "",
            price = price,
            change = change,
            changePct = pct,
        )
    }

    /** Ticker universe: every sector's company list, plus hotlists. Only 4-letter equities. */
    suspend fun loadUniverse(onProgress: (String) -> Unit = {}): List<String> {
        val symbols = linkedSetOf<String>()
        try {
            val sectors = get("/emitten/sectors").dataOf()
            val ids = (sectors as? JsonArray)?.mapNotNull { it.path("id").asStr() } ?: emptyList()
            ids.forEachIndexed { i, id ->
                onProgress("Memuat daftar saham sektor ${i + 1}/${ids.size}")
                try {
                    val res = get("/emitten/v3/sector/$id/company")
                    collectSymbols(res, symbols)
                } catch (e: AuthExpiredException) {
                    throw e
                } catch (e: Exception) {
                    // one sector failing is not fatal
                }
            }
        } catch (e: AuthExpiredException) {
            throw e
        } catch (e: Exception) {
            // fall through to other sources
        }
        onProgress("Memuat anggota indeks IHSG")
        for (index in listOf("IHSG", "COMPOSITE")) {
            try {
                collectSymbols(get("/emitten/indexes/$index", listOf("limit" to "500")), symbols)
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: Exception) {
            }
        }
        for (type in listOf("topgainer", "toploser", "mostactive")) {
            try {
                collectSymbols(get("/emitten/hotlist/$type", listOf("limit" to "50")), symbols)
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: Exception) {
            }
        }
        try {
            collectSymbols(get("/emitten/trending"), symbols)
        } catch (e: AuthExpiredException) {
            throw e
        } catch (e: Exception) {
        }
        return symbols.filter { EQUITY_RE.matches(it) }.sorted()
    }

    private fun collectSymbols(root: JsonElement, out: MutableSet<String>) {
        for (arr in findObjectArrays(root)) {
            for (row in arr) {
                val s = (row.path("symbol").asStr() ?: row.path("company_symbol").asStr())
                    ?.trim()?.uppercase()
                if (s != null && EQUITY_RE.matches(s)) out.add(s)
            }
        }
    }

    companion object {
        const val BASE = "https://exodus.stockbit.com"
        private val JSON_TYPE = "application/json".toMediaType()
        val EQUITY_RE = Regex("^[A-Z]{4}$")

        fun parseBar(el: JsonElement): Bar? {
            val o = el as? JsonObject ?: return null
            val date = normalizeDate(o["date"].asStr() ?: return null) ?: return null
            val close = o["close"].asNum() ?: return null
            if (close <= 0) return null
            val open = o["open"].asNum()?.takeIf { it > 0 } ?: close
            val high = o["high"].asNum()?.takeIf { it > 0 } ?: maxOf(open, close)
            val low = o["low"].asNum()?.takeIf { it > 0 } ?: minOf(open, close)
            return Bar(
                date = date,
                open = open,
                high = maxOf(high, open, close),
                low = minOf(low, open, close),
                close = close,
                average = o["average"].asNum() ?: close,
                volume = o["volume"].asNum() ?: 0.0,
                value = o["value"].asNum() ?: 0.0,
                freq = o["frequency"].asNum() ?: 0.0,
            )
        }

        private val MONTHS = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "mei" to 5, "jun" to 6,
            "jul" to 7, "aug" to 8, "agu" to 8, "agt" to 8, "sep" to 9, "oct" to 10, "okt" to 10,
            "nov" to 11, "dec" to 12, "des" to 12,
        )

        /** Normalizes the various date spellings to YYYY-MM-DD. */
        fun normalizeDate(raw: String): String? {
            val s = raw.trim()
            Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(s)?.let { return it.value }
            Regex("^(\\d{4})(\\d{2})(\\d{2})$").find(s)?.let {
                val (y, m, d) = it.destructured
                return "$y-$m-$d"
            }
            Regex("^(\\d{4})/(\\d{1,2})/(\\d{1,2})").find(s)?.let {
                val (y, m, d) = it.destructured
                return "%s-%02d-%02d".format(y, m.toInt(), d.toInt())
            }
            Regex("^(\\d{1,2})[ -]([A-Za-z]{3})[A-Za-z]*[ -](\\d{2,4})").find(s)?.let {
                val (d, mon, y) = it.destructured
                val m = MONTHS[mon.lowercase()] ?: return null
                val year = if (y.length == 2) "20$y" else y
                return "%s-%02d-%02d".format(year, m, d.toInt())
            }
            return null
        }

        fun parseBrokerSummary(data: JsonElement?, from: String, to: String): BrokerSummary {
            val bs = data.path("broker_summary")
            fun rows(key: String, buy: Boolean): List<BrokerFlow> {
                val arr = bs.path(key) as? JsonArray ?: return emptyList()
                return arr.mapNotNull { r ->
                    val code = r.path("netbs_broker_code").asStr() ?: return@mapNotNull null
                    val lots = (if (buy) r.path("blot") else r.path("slot")).asNum() ?: 0.0
                    val value = (if (buy) r.path("bval") else r.path("sval")).asNum() ?: 0.0
                    val avg = (if (buy) r.path("netbs_buy_avg_price") else r.path("netbs_sell_avg_price")).asNum() ?: 0.0
                    BrokerFlow(
                        code = code,
                        type = r.path("type").asStr() ?: "",
                        lots = kotlin.math.abs(lots),
                        value = kotlin.math.abs(value),
                        avgPrice = avg,
                        freq = r.path("freq").asNum() ?: 0.0,
                    )
                }.sortedByDescending { it.value }
            }
            val det = data.path("bandar_detector")
            fun acc(k: String): String = det.path(k, "accdist").asStr() ?: ""
            return BrokerSummary(
                from = data.path("from").asStr() ?: from,
                to = data.path("to").asStr() ?: to,
                buyers = rows("brokers_buy", true),
                sellers = rows("brokers_sell", false),
                accdist = det.path("broker_accdist").asStr() ?: "",
                top1Acc = acc("top1"),
                top3Acc = acc("top3"),
                top5Acc = acc("top5"),
                totalBuyer = det.path("total_buyer").asNum()?.toInt() ?: 0,
                totalSeller = det.path("total_seller").asNum()?.toInt() ?: 0,
            )
        }
    }
}
