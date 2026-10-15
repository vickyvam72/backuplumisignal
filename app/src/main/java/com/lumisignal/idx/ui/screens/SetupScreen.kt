package com.lumisignal.idx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisignal.idx.Graph
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.RadarPorto
import com.lumisignal.idx.engine.RadarRepo
import com.lumisignal.idx.service.Monitor
import com.lumisignal.idx.ui.components.GradientButton
import com.lumisignal.idx.ui.components.ProgressBar
import com.lumisignal.idx.ui.components.RadarScope
import com.lumisignal.idx.ui.theme.Lumi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class CheckState { WAIT, RUN, OK, FAIL }

private class SetupCheck(val title: String, val running: String) {
    var state by mutableStateOf(CheckState.WAIT)
    var detail by mutableStateOf("")
}

/**
 * First screening, done once after install + Stockbit login: a radar sweep while the app checks that
 * Stockbit is connected, the background audit is running and the stock data arrived complete. After
 * that the home screen shows the buy signals directly, refreshed every day at 17:00 WIB.
 */
@Composable
fun SetupScreen(onBack: () -> Unit, onDone: () -> Unit, autoStart: Boolean = true, previewAllOk: Boolean = false) {
    val ctx = LocalContext.current
    val day by RadarRepo.day.collectAsStateWithLifecycle()
    val checks = remember {
        mutableStateListOf(
            SetupCheck("Stockbit terhubung", "Menghubungkan akun Stockbit…"),
            SetupCheck("Audit latar belakang aktif", "Menyiapkan audit harga otomatis…"),
            SetupCheck("Data saham diterima lengkap", "Mengunduh closing semua saham IDX…"),
        )
    }
    var attempt by remember { mutableIntStateOf(if (autoStart && !previewAllOk) 1 else 0) }
    if (previewAllOk) remember {
        checks.forEachIndexed { i, c ->
            c.state = CheckState.OK
            c.detail = listOf("Akun Stockbit · hanya membaca data", "Harga dicek tiap 15 menit · notifikasi aktif", "950 saham IDX dipindai")[i]
        }
    }
    val allOk = checks.all { it.state == CheckState.OK }
    val failed = checks.any { it.state == CheckState.FAIL }

    LaunchedEffect(attempt) {
        if (attempt == 0) return@LaunchedEffect
        checks.forEach { it.state = CheckState.WAIT; it.detail = "" }
        suspend fun step(c: SetupCheck, block: suspend () -> String?) {
            c.state = CheckState.RUN
            val started = System.currentTimeMillis()
            val res = try { block() } catch (e: Exception) { null }
            val left = 1300L - (System.currentTimeMillis() - started)
            if (left > 0) delay(left)
            if (res != null) { c.state = CheckState.OK; c.detail = res } else c.state = CheckState.FAIL
        }
        step(checks[0]) {
            val access = Graph.session.access ?: return@step null
            val ok = Graph.api.validate(access) || (Graph.api.refreshNow() && Graph.api.validate(Graph.session.access ?: ""))
            if (!ok) null else Graph.session.displayName.ifBlank { "Akun Stockbit" } + " · hanya membaca data, tidak pernah membuat order"
        }
        if (checks[0].state != CheckState.OK) { checks[0].detail = "Gagal terhubung. Periksa internet atau login ulang."; return@LaunchedEffect }
        step(checks[1]) {
            Monitor.schedule(ctx)
            val on = withContext(Dispatchers.IO) { Monitor.isScheduled(ctx) }
            if (!on) null else "Harga dicek tiap 15 menit · notifikasi ${if (Perms.notifications(ctx)) "aktif" else "belum diizinkan"}"
        }
        if (checks[1].state != CheckState.OK) { checks[1].detail = "Audit belum berjalan. Coba lagi."; return@LaunchedEffect }
        step(checks[2]) {
            val d = RadarRepo.refresh()
            RadarRepo.refreshStocks()
            val n = d.tiers.firstOrNull { it.key == "semua" }?.stocks ?: d.tiers.maxOfOrNull { it.stocks } ?: 0
            if (n <= 0) null else "$n saham IDX dipindai · ${Market.prettyDate(d.date, false)}"
        }
        if (checks[2].state != CheckState.OK) checks[2].detail = RadarRepo.state.value.error ?: "Data belum lengkap. Coba lagi."
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Screening pertama", subtitle = "Cek koneksi sekali saja", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            RadarScope(250.dp, periodMs = if (allOk) 5200 else 1600, blips = if (allOk) 12 else 18, accent = if (allOk) Lumi.Green else Lumi.Sky)
            Spacer(Modifier.height(18.dp))
            Text(
                when {
                    allOk -> "Semua siap!"
                    failed -> "Ada yang belum tersambung"
                    attempt == 0 -> "Siap memindai"
                    else -> "Screening berjalan"
                },
                style = MaterialTheme.typography.titleLarge, color = if (allOk) Lumi.Green else if (failed) Lumi.Amber else Lumi.Text,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                day?.let { "Data terbaru: sinyal beli untuk ${Market.prettyDate(RadarPorto.nextSession(it.date))}" }
                    ?: "Dihitung dari harga closing semua saham IDX",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            ProgressBar(checks.count { it.state == CheckState.OK } / checks.size.toFloat(), color = if (allOk) Lumi.Green else Lumi.Blue)
            Spacer(Modifier.height(18.dp))
            checks.forEach { c ->
                CheckRow(c)
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(10.dp))
            when {
                allOk -> {
                    Text(
                        "Mulai sekarang kamu tidak perlu screening lagi. Sinyal beli muncul langsung di Beranda dan diperbarui otomatis setiap hari bursa pukul 17.00 WIB.",
                        style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    GradientButton("Lihat Sinyal Beli", {
                        RadarRepo.setupDone = true
                        RadarRepo.day.value?.let { d ->
                            RadarPorto.saveAll(d)
                            if (d.date >= RadarRepo.expectedDate()) RadarRepo.notifiedDate = d.date
                        }
                        onDone()
                    }, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow)
                }
                failed -> GradientButton("Coba lagi", { attempt++ }, Modifier.fillMaxWidth(), icon = Icons.Filled.Refresh)
                attempt == 0 -> GradientButton("Mulai", { attempt = 1 }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CheckRow(c: SetupCheck) {
    val color = when (c.state) {
        CheckState.OK -> Lumi.Green
        CheckState.FAIL -> Lumi.Red
        CheckState.RUN -> Lumi.Sky
        CheckState.WAIT -> Lumi.TextMuted
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            when (c.state) {
                CheckState.RUN -> CircularProgressIndicator(Modifier.size(18.dp), color = Lumi.Sky, strokeWidth = 2.dp)
                CheckState.OK -> Text("✓", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Lumi.Green)
                CheckState.FAIL -> Text("!", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Lumi.Red)
                CheckState.WAIT -> Box(Modifier.size(8.dp).clip(CircleShape).background(Lumi.TextMuted))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.titleSmall, color = if (c.state == CheckState.WAIT) Lumi.TextDim else Lumi.Text)
            val sub = when {
                c.state == CheckState.RUN -> c.running
                c.detail.isNotBlank() -> c.detail
                else -> null
            }
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = if (c.state == CheckState.FAIL) Lumi.Red else Lumi.TextDim)
        }
    }
}
