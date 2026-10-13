package com.lumisignal.idx

import android.annotation.SuppressLint
import android.content.Context
import com.lumisignal.idx.data.CacheStore
import com.lumisignal.idx.data.HistoryStore
import com.lumisignal.idx.data.SessionStore
import com.lumisignal.idx.data.StockbitApi

/** Tiny service locator: one instance of each store/client for the whole process. */
@SuppressLint("StaticFieldLeak")
object Graph {
    lateinit var context: Context
        private set
    lateinit var session: SessionStore
        private set
    lateinit var api: StockbitApi
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var cache: CacheStore
        private set

    @Volatile
    private var ready = false

    @Synchronized
    fun init(appContext: Context) {
        if (ready) return
        context = appContext.applicationContext
        session = SessionStore(context)
        api = StockbitApi(session)
        history = HistoryStore(context)
        cache = CacheStore(context)
        ready = true
    }
}
