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
    var notifAsked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notif = granted || Perms.notifications(ctx)
        notifAsked = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notif = Perms.notifications(ctx)
        bg = Perms.background(ctx)
    }

    LumiBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(36.dp))
            LumiLogo(56.dp)
            Spacer(Modifier.height(18.dp))
            Text("Satu langkah lagi", style = MaterialTheme.typography.headlineSmall, color = Lumi.Text)
            Spacer(Modifier.height(6.dp))
            Text(
                "Lumi memantau harga sinyal kamu di latar belakang dan memberi tahu saat harga menyentuh entry, take profit, atau stop loss. Untuk itu dua izin ini wajib diaktifkan.",
                style = MaterialTheme.typography.bodyMedium, color = Lumi.TextDim,
            )
            Spacer(Modifier.height(22.dp))

            PermCard(
                icon = Icons.Filled.Notifications,
                title = "Izinkan notifikasi",
                body = "Supaya kamu langsung tahu saat sinyal masuk entry, kena TP, atau kena SL.",
                granted = notif,
            ) {
                if (Build.VERSION.SDK_INT >= 33 && !notifAsked) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Perms.openAppSettings(ctx)
                }
            }
            Spacer(Modifier.height(12.dp))
            PermCard(
                icon = Icons.Filled.Refresh,
                title = "Izinkan berjalan di latar belakang",
                body = "Mengecualikan Lumi dari penghemat baterai agar pemantauan harga tetap jalan walau aplikasi ditutup.",
                granted = bg,
            ) { Perms.requestBackground(ctx) }

            Spacer(Modifier.height(16.dp))
            GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Amber, padding = 14.dp) {
                Text("Pengguna Xiaomi, Oppo, Vivo, Realme & Infinix", style = MaterialTheme.typography.titleSmall, color = Lumi.Amber)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Aktifkan juga \"Autostart\" / \"Mulai otomatis\" untuk Lumi Signal di pengaturan aplikasi HP kamu, karena sistem HP tersebut sering menutup aplikasi di latar belakang.",
                    style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim,
                )
                Spacer(Modifier.height(10.dp))
                GhostButton("Buka pengaturan aplikasi", { Perms.openAppSettings(ctx) }, color = Lumi.Amber)
            }
            Spacer(Modifier.height(24.dp))
            GradientButton(
                if (notif && bg) "Lanjut ke Dashboard" else "Aktifkan kedua izin dulu",
                onClick = onDone,
                enabled = notif && bg,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun PermCard(icon: ImageVector, title: String, body: String, granted: Boolean, onRequest: () -> Unit) {
    val accent: Color = if (granted) Lumi.Green else Lumi.Blue
    GlassCard(Modifier.fillMaxWidth(), accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (granted) Icons.Filled.CheckCircle else icon, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Lumi.Text)
                Text(body, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
            }
        }
        if (!granted) {
            Spacer(Modifier.height(12.dp))
            GradientButton("Izinkan", onRequest, Modifier.fillMaxWidth())
        } else {
            Spacer(Modifier.height(8.dp))
            Text("✓ Sudah diizinkan", style = MaterialTheme.typography.labelMedium, color = Lumi.Green)
        }
    }
}
