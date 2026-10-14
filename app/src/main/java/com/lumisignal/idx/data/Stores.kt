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

/** Small app preferences (monitor bookkeeping, onboarding). */
class CacheStore(private val context: Context) {
    init {
        // Price caches of the old on-phone screening are no longer used.
        try {
            java.io.File(context.cacheDir, "stage1").deleteRecursively()
            java.io.File(context.filesDir, "universe.json").delete()
        } catch (e: Exception) {
        }
    }

    private val prefs = context.getSharedPreferences("lumi_prefs", Context.MODE_PRIVATE)

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
