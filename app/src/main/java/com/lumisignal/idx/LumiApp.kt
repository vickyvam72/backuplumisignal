package com.lumisignal.idx

import android.app.Application
import com.lumisignal.idx.service.Monitor
import com.lumisignal.idx.service.Notifier

class LumiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifier.createChannels(this)
        Monitor.schedule(this)
    }
}
