package com.lumisignal.idx.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Strategy + portfolio history, persisted as one JSON file. */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "history.json")
    private val serializer = ListSerializer(Signal.serializer())
    private val lock = Any()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Signal>> = _items.asStateFlow()

    private fun load(): List<Signal> = try {
        if (file.exists()) LumiJson.decodeFromString(serializer, file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun persist(list: List<Signal>) {
        val tmp = File(file.parentFile, "history.json.tmp")
        tmp.writeText(LumiJson.encodeToString(serializer, list))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun get(id: String): Signal? = _items.value.firstOrNull { it.id == id }

    fun upsert(signals: List<Signal>) = synchronized(lock) {
        val map = LinkedHashMap<String, Signal>()
        _items.value.forEach { map[it.id] = it }
        signals.forEach { map[it.id] = it }
        val list = map.values.sortedByDescending { it.createdAt }
        persist(list)
        _items.value = list
    }

    fun update(id: String, transform: (Signal) -> Signal) = synchronized(lock) {
        val list = _items.value.map { if (it.id == id) transform(it) else it }
        persist(list)
        _items.value = list
    }

    fun remove(id: String) = synchronized(lock) {
        val list = _items.value.filterNot { it.id == id }
        persist(list)
        _items.value = list
    }

    fun portoFor(signalId: String): Signal? =
        _items.value.firstOrNull { it.source == SOURCE_PORTO && it.linkedId == signalId }
}

@Serializable
private data class UniverseFile(val updatedAt: Long, val symbols: List<String>)

/** Cached ticker universe and the per-reference-date stage-1 price cache. */
class CacheStore(private val context: Context) {
    private val universeFile = File(context.filesDir, "universe.json")
    private val stageDir = File(context.cacheDir, "stage1").apply { mkdirs() }
    private val barsMap = MapSerializer(String.serializer(), ListSerializer(Bar.serializer()))

    fun universe(): Pair<Long, List<String>> = try {
        if (!universeFile.exists()) 0L to emptyList()
        else LumiJson.decodeFromString(UniverseFile.serializer(), universeFile.readText())
            .let { it.updatedAt to it.symbols }
    } catch (e: Exception) {
        0L to emptyList()
    }

    fun saveUniverse(symbols: List<String>) {
        universeFile.writeText(
            LumiJson.encodeToString(UniverseFile.serializer(), UniverseFile(System.currentTimeMillis(), symbols)),
        )
    }

    fun loadStage1(refDate: String): MutableMap<String, List<Bar>> = try {
        val f = File(stageDir, "$refDate.json")
        if (f.exists()) LumiJson.decodeFromString(barsMap, f.readText()).toMutableMap() else mutableMapOf()
    } catch (e: Exception) {
        mutableMapOf()
    }

    fun saveStage1(refDate: String, data: Map<String, List<Bar>>) {
        try {
            stageDir.listFiles()?.filter { it.name != "$refDate.json" }?.forEach { it.delete() }
            File(stageDir, "$refDate.json").writeText(LumiJson.encodeToString(barsMap, data))
        } catch (e: Exception) {
            // cache is best effort
        }
    }

    private val prefs = context.getSharedPreferences("lumi_prefs", Context.MODE_PRIVATE)

    var lastRefDate: String
        get() = prefs.getString("last_ref", "") ?: ""
        set(v) = prefs.edit().putString("last_ref", v).apply()

    var lastMonitorRun: Long
        get() = prefs.getLong("last_monitor", 0L)
        set(v) = prefs.edit().putLong("last_monitor", v).apply()

    var lastDailyKey: String
        get() = prefs.getString("last_daily", "") ?: ""
        set(v) = prefs.edit().putString("last_daily", v).apply()

    var loginNotifiedDay: String
        get() = prefs.getString("login_notified", "") ?: ""
        set(v) = prefs.edit().putString("login_notified", v).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(v) = prefs.edit().putBoolean("onboarding_done", v).apply()
}

/** Last-resort ticker list used only when Stockbit's sector lists cannot be loaded. */
val SEED_SYMBOLS = listOf(
    "AADI", "AALI", "ACES", "ADHI", "ADMR", "ADRO", "AGRO", "AKRA", "AMMN", "AMRT", "ANTM", "APLN",
    "ARTO", "ASII", "ASRI", "AUTO", "AVIA", "BBCA", "BBNI", "BBRI", "BBTN", "BBYB", "BDMN", "BEST",
    "BFIN", "BJBR", "BJTM", "BMRI", "BNGA", "BREN", "BRIS", "BRMS", "BRPT", "BSDE", "BTPS", "BUKA",
    "BUMI", "BWPT", "CPIN", "CTRA", "CUAN", "DEWA", "DILD", "DMAS", "DOID", "DSNG", "ELSA", "EMTK",
    "ENRG", "ERAA", "ESSA", "EXCL", "GGRM", "GOTO", "HEAL", "HMSP", "HRUM", "ICBP", "INCO", "INDF",
    "INDY", "INKP", "INTP", "ISAT", "ITMG", "JPFA", "JSMR", "KIJA", "KLBF", "LPKR", "LPPF", "LSIP",
    "MAIN", "MAPA", "MAPI", "MBMA", "MDKA", "MEDC", "MIKA", "MNCN", "MTEL", "MYOR", "NCKL", "NISP",
    "PANI", "PGAS", "PGEO", "PNBN", "PTBA", "PTPP", "PTRO", "PWON", "RAJA", "RALS", "SCMA", "SIDO",
    "SILO", "SIMP", "SMGR", "SMRA", "SRTG", "SSIA", "SSMS", "TAPG", "TBIG", "TINS", "TKIM", "TLKM",
    "TOWR", "TPIA", "UNTR", "UNVR", "WIKA", "WSKT", "WTON",
)
