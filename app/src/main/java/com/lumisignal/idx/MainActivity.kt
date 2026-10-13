package com.lumisignal.idx

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lumisignal.idx.service.Notifier
import com.lumisignal.idx.ui.AppRoot
import com.lumisignal.idx.ui.PendingNav
import com.lumisignal.idx.ui.theme.LumiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(0xFF070B1A.toInt()),
        )
        handleIntent(intent)
        setContent {
            LumiTheme { AppRoot() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val signal = intent?.getStringExtra(Notifier.EXTRA_OPEN_SIGNAL)
        val scan = intent?.getStringExtra(Notifier.EXTRA_OPEN_SCAN)
        when {
            signal != null -> PendingNav.value = "detail/$signal"
            scan == "1" -> PendingNav.value = "scan"
        }
    }
}

object BuildConfigInfo {
    fun versionName(ctx: Context): String = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        }
        info.versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }
}
