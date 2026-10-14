package com.lumisignal.idx.ui.screens

import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.filled.Delete
import com.lumisignal.idx.data.SOURCE_PORTO
import com.lumisignal.idx.data.SOURCE_STRATEGY
import com.lumisignal.idx.engine.RadarRepo
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lumisignal.idx.BuildConfigInfo
import com.lumisignal.idx.Graph
import com.lumisignal.idx.ui.components.GhostButton
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.LumiLogo
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.SectionHeader
import com.lumisignal.idx.ui.theme.Lumi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var logoutDialog by remember { mutableStateOf(false) }
    var resetDialog by remember { mutableIntStateOf(0) }
    var notif by remember { mutableStateOf(Perms.notifications(ctx)) }
    var bg by remember { mutableStateOf(Perms.background(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notif = Perms.notifications(ctx)
        bg = Perms.background(ctx)
    }
    val fmt = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale("id", "ID")) }
    val name = Graph.session.displayName
    val exp = Graph.session.accessExpiry
    val hasRefresh = Graph.session.refresh != null
    val lastRun = Graph.cache.lastMonitorRun

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(14.dp))
        Text("Akun & Pengaturan", style = MaterialTheme.typography.headlineSmall, color = Lumi.Text)
        Spacer(Modifier.height(14.dp))

        GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Green) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LumiLogo(44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name.ifBlank { "Akun Stockbit" }, style = MaterialTheme.typography.titleMedium, color = Lumi.Text)
                    Text(
                        if (exp > 0) "Token berlaku s/d ${fmt.format(Date(exp * 1000))}" else "Token aktif",
                        style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim,
                    )
                }
                Pill("Terhubung", Lumi.Green)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (hasRefresh) "Token diperpanjang otomatis sebelum kedaluwarsa, selama aplikasi berjalan setidaknya seminggu sekali."
                else "Token Stockbit berlaku ±24 jam. Jika habis, kamu akan diminta login ulang.",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
            )
            Spacer(Modifier.height(12.dp))
            GhostButton("Keluar dari Stockbit", { logoutDialog = true }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.ExitToApp, color = Lumi.Red)
        }
        Spacer(Modifier.height(16.dp))

        SectionHeader("Pemantauan latar belakang")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(if (notif) "Notifikasi aktif" else "Notifikasi mati", if (notif) Lumi.Green else Lumi.Red)
                Pill(if (bg) "Latar belakang aktif" else "Dibatasi baterai", if (bg) Lumi.Green else Lumi.Red)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Terakhir memeriksa harga: ${if (lastRun > 0) fmt.format(Date(lastRun)) else "belum pernah"}",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Saham di Riwayat Strategi dan Porto diaudit dengan high & low harian asli dari Stockbit: tiap menit saat aplikasi terbuka di jam bursa, ±15 menit sekali di latar belakang. Notifikasi muncul saat Radar Besok terbit dan saat harga menyentuh entry, TP, atau SL.",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
            )
            if (!notif || !bg) {
                Spacer(Modifier.height(8.dp))
                GhostButton("Perbaiki izin", {
                    if (!bg) Perms.requestBackground(ctx) else Perms.openAppSettings(ctx)
                }, Modifier.fillMaxWidth(), color = Lumi.Amber)
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionHeader("Reset data")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Hapus hasil screening & Riwayat Strategi", style = MaterialTheme.typography.titleSmall, color = Lumi.Text)
            Text("Semua saham hasil screening dan pemantauannya dihapus. Porto tidak ikut terhapus.", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
            Spacer(Modifier.height(8.dp))
            GhostButton("Reset riwayat strategi", { resetDialog = 1 }, Modifier.fillMaxWidth(), icon = Icons.Filled.Delete, color = Lumi.Amber)
            Spacer(Modifier.height(14.dp))
            Text("Hapus Riwayat Portofolio", style = MaterialTheme.typography.titleSmall, color = Lumi.Text)
            Text("Semua saham yang kamu masukkan Porto beserta hasilnya dihapus. Riwayat strategi tidak ikut terhapus.", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
            Spacer(Modifier.height(8.dp))
            GhostButton("Reset riwayat portofolio", { resetDialog = 2 }, Modifier.fillMaxWidth(), icon = Icons.Filled.Delete, color = Lumi.Red)
        }
        Spacer(Modifier.height(16.dp))

        SectionHeader("Cara kerja Lumi")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Bullet("Radar Besok dihitung otomatis setiap hari bursa ±16:30 WIB dari closing semua saham IDX. Sekali screening, pilih jenis sinyal dan Top 5 Saham IDX langsung tampil.")
            Bullet("Dua jenis sinyal: Profit tinggi (target lebar dari volatilitas atau resisten) dan Winrate tinggi (TP minimal 3% yang sering tersentuh). Saham yang diproses: nilai transaksi rata-rata minimal Rp250 juta per hari.")
            Bullet("Beli di sesi berikutnya: di harga pembukaan bila ≤ closing +1%, kalau dibuka lebih tinggi antre di closing +1%. Tidak terisi di sesi itu = batal. Saham yang masih dipegang tidak direkomendasikan lagi.")
            Bullet("TP dan SL mengikuti fraksi harga IDX (TP dibulatkan ke atas, SL ke bawah) dan dihitung dari harga beli sebenarnya. Kalau belum kena TP/SL, posisi dijual di closing hari terakhir masa hold.")
            Bullet("Setiap hasil screening yang kamu buka masuk Riwayat Strategi, dan Porto berisi saham pilihanmu. Keduanya diaudit dengan harga Stockbit: tiap menit saat aplikasi terbuka di jam bursa, dan ±15 menit sekali di latar belakang. Winrate = transaksi untung dibagi transaksi selesai; akumulasi P/L = jumlah P/L real.")
        }
        Spacer(Modifier.height(16.dp))

        GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Amber) {
            Text("Disclaimer", style = MaterialTheme.typography.titleSmall, color = Lumi.Amber)
            Spacer(Modifier.height(4.dp))
            Text(
                "Lumi Signal IDX bukan produk resmi Stockbit dan bukan rekomendasi investasi. Radar memakai data harga publik; pemantauan Porto dan analisa bandar memakai akun Stockbit milikmu sendiri (read-only, tidak pernah membuat order). Akses otomatis bisa bertentangan dengan ketentuan Stockbit; gunakan secara wajar. Keputusan jual-beli sepenuhnya tanggung jawab pengguna.",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Lumi Signal IDX v${BuildConfigInfo.versionName(ctx)}", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
        Spacer(Modifier.height(28.dp))
    }

    if (resetDialog != 0) {
        val strategy = resetDialog == 1
        AlertDialog(
            onDismissRequest = { resetDialog = 0 },
            containerColor = Lumi.Surface,
            title = { Text(if (strategy) "Reset riwayat strategi?" else "Reset riwayat portofolio?", color = Lumi.Text) },
            text = {
                Text(
                    if (strategy) "Semua hasil screening dan Riwayat Strategi akan dihapus permanen. Screening berikutnya akan mencatat dari awal lagi."
                    else "Semua posisi Porto dan hasilnya akan dihapus permanen, termasuk winrate porto.",
                    color = Lumi.TextDim,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (strategy) {
                        Graph.history.removeWhere { it.source == SOURCE_STRATEGY }
                        RadarRepo.clearLocal()
                    } else {
                        Graph.history.removeWhere { it.source == SOURCE_PORTO }
                    }
                    resetDialog = 0
                    android.widget.Toast.makeText(ctx, if (strategy) "Riwayat strategi dihapus" else "Riwayat portofolio dihapus", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Hapus", color = Lumi.Red) }
            },
            dismissButton = { TextButton(onClick = { resetDialog = 0 }) { Text("Batal", color = Lumi.TextDim) } },
        )
    }

    if (logoutDialog) {
        AlertDialog(
            onDismissRequest = { logoutDialog = false },
            containerColor = Lumi.Surface,
            title = { Text("Keluar dari Stockbit?", color = Lumi.Text) },
            text = { Text("Pemantauan sinyal berhenti sampai kamu login lagi. Riwayat strategi dan porto tetap tersimpan.", color = Lumi.TextDim) },
            confirmButton = {
                TextButton(onClick = {
                    logoutDialog = false
                    CookieManager.getInstance().removeAllCookies(null)
                    WebStorage.getInstance().deleteAllData()
                    Graph.session.clear()
                }) { Text("Keluar", color = Lumi.Red) }
            },
            dismissButton = { TextButton(onClick = { logoutDialog = false }) { Text("Batal", color = Lumi.TextDim) } },
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium, color = Lumi.Sky)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
    }
}
