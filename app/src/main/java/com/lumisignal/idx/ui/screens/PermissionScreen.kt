package com.lumisignal.idx.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lumisignal.idx.ui.components.GhostButton
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.GradientButton
import com.lumisignal.idx.ui.components.LumiBackground
import com.lumisignal.idx.ui.components.LumiLogo
import com.lumisignal.idx.ui.theme.Lumi

object Perms {
    fun notifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun background(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun all(ctx: Context) = notifications(ctx) && background(ctx)

    @SuppressLint("BatteryLife")
    fun requestBackground(ctx: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
            }
        }
    }

    /** Phone makers' own "autostart / mulai otomatis" pages (not the battery saver). */
    private val AUTOSTART = listOf(
        // Xiaomi / Redmi / POCO (MIUI, HyperOS)
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        // OPPO / realme / OnePlus (ColorOS, realme UI, OxygenOS)
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oplus.safecenter" to "com.oplus.safecenter.startupapp.view.StartupAppListActivity",
        "com.oplus.safecenter" to "com.oplus.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.privacypermissionsentry.PermissionTopActivity",
        // vivo / iQOO
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        // Huawei / Honor
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        // Infinix / Tecno / itel
        "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
        // ASUS
        "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
        "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity",
        // Letv
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
    )

    /** Opens the autostart list. Falls back to the app's info page when the phone has no such page. */
    fun openAutostart(ctx: Context): Boolean {
        for ((pkg, cls) in AUTOSTART) {
            try {
                val i = Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                return true
            } catch (e: Exception) {
            }
        }
        openAppSettings(ctx)
        return false
    }

    fun openAppSettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        } catch (e: Exception) {
        }
    }
}

@Composable
fun PermissionScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var notif by remember { mutableStateOf(Perms.notifications(ctx)) }
    var bg by remember { mutableStateOf(Perms.background(ctx)) }
    var autoOpened by remember { mutableStateOf(false) }
    var notifAsked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notif = granted || Perms.notifications(ctx)
        notifAsked = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notif = Perms.notifications(ctx)
        bg = Perms.background(ctx)
    }

    // One screen, no scrolling: compact rows and the button pinned to the bottom.
    LumiBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LumiLogo(44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Satu langkah lagi", style = MaterialTheme.typography.titleLarge, color = Lumi.Text)
                    Text("Supaya sinyal & audit harga jalan otomatis", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Lumi memantau harga di latar belakang dan memberi notifikasi saat sinyal beli terbit (17.00 WIB) serta saat harga menyentuh entry, TP, atau SL.",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim,
            )
            Spacer(Modifier.height(14.dp))
            PermRow(Icons.Filled.Notifications, "Notifikasi", "Kabar sinyal beli, entry, TP & SL", notif, "Izinkan") {
                if (Build.VERSION.SDK_INT >= 33 && !notifAsked) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else Perms.openAppSettings(ctx)
            }
            Spacer(Modifier.height(10.dp))
            PermRow(Icons.Filled.Refresh, "Penghemat baterai", "Kecualikan Lumi agar audit tetap jalan", bg, "Izinkan") { Perms.requestBackground(ctx) }
            Spacer(Modifier.height(10.dp))
            PermRow(
                Icons.Filled.PlayArrow, "Autostart / Mulai otomatis", "Xiaomi, Oppo, Vivo, Realme, Infinix: nyalakan Lumi Signal IDX",
                autoOpened, "Buka", doneText = "Dibuka",
            ) { autoOpened = true; Perms.openAutostart(ctx) }
            Spacer(Modifier.weight(1f))
            Text(
                "Autostart wajib di HP yang punya menu itu; di HP lain lewati saja.",
                style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted,
            )
            Spacer(Modifier.height(10.dp))
            GradientButton(
                if (notif && bg) "Lanjut" else "Aktifkan notifikasi & baterai dulu",
                onClick = onDone,
                enabled = notif && bg,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PermRow(icon: ImageVector, title: String, body: String, granted: Boolean, action: String, doneText: String = "Aktif", onRequest: () -> Unit) {
    val accent: Color = if (granted) Lumi.Green else Lumi.Blue
    GlassCard(Modifier.fillMaxWidth(), accent = accent, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (granted) Icons.Filled.CheckCircle else icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Lumi.Text, maxLines = 1)
                Text(body, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Text("✓ $doneText", style = MaterialTheme.typography.labelMedium, color = Lumi.Green)
            } else {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Lumi.ButtonGradient).clickable(onClick = onRequest)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(action, style = MaterialTheme.typography.labelLarge, color = Color.White) }
            }
        }
    }
}
