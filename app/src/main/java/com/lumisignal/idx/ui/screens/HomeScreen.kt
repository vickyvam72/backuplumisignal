package com.lumisignal.idx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisignal.idx.Graph
import com.lumisignal.idx.UiConfig
import com.lumisignal.idx.data.RadarDay
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.RadarPorto
import com.lumisignal.idx.engine.RadarRepo
import com.lumisignal.idx.ui.components.GradientButton
import com.lumisignal.idx.ui.components.LumiLogo
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.RadarBadge
import com.lumisignal.idx.ui.components.RadarScope
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle
import kotlinx.coroutines.launch

private val PROFIT_COLOR = Color(0xFFFF9F43)
private val WINRATE_COLOR = Color(0xFF22D3EE)

/**
 * Home. Before the first screening: the radar and one button (the screening checks the connections).
 * After it: the buy signals for the next session, profit tinggi and winrate tinggi on one page,
 * refreshed every trading day at 17:00 WIB.
 */
@Composable
fun HomeScreen(onScreening: () -> Unit, onOpenPick: OpenPick = { _, _, _, _, _ -> }) {
    val day by RadarRepo.day.collectAsStateWithLifecycle()
    val items by Graph.history.items.collectAsStateWithLifecycle()
    // Users who already screened with an older version go straight to the signals.
    val setup = remember(items) {
        RadarRepo.setupDone || items.any { it.source == com.lumisignal.idx.data.SOURCE_STRATEGY }.also { if (it) RadarRepo.setupDone = true }
    }
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!setup) return@LifecycleEventEffect
        scope.launch {
            if (RadarRepo.isStale()) runCatching { RadarRepo.refresh() }
            RadarRepo.day.value?.takeIf { it.date >= RadarRepo.expectedDate() }?.let { RadarPorto.saveAll(it) }
            tick++
        }
    }
    Column(Modifier.fillMaxSize()) {
        HomeHeader()
        val d = day
        if (!setup || d == null) Intro(d, onScreening) else Signals(d, tick, onOpenPick)
    }
}

@Composable
private fun HomeHeader() {
    val name = remember { Graph.session.displayName }
    Row(Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        LumiLogo(42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Lumi Signal IDX", style = MaterialTheme.typography.titleLarge, color = Lumi.Text)
            Text(
                if (name.isNotBlank()) "Halo, ${name.split(" ").first()} 👋" else "Radar sinyal beli saham IDX",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim,
            )
        }
        Pill("Stockbit terhubung", Lumi.Green)
    }
}

@Composable
private fun Intro(day: RadarDay?, onScreening: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Column(
            Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RadarScope(260.dp, periodMs = 5200, blips = 9)
            Spacer(Modifier.height(28.dp))
            Text("RADAR BESOK", style = MaterialTheme.typography.labelLarge, color = Lumi.Sky)
            Spacer(Modifier.height(6.dp))
            Text(
                "Temukan saham untuk dibeli\ndi sesi berikutnya",
                style = MaterialTheme.typography.headlineSmall, color = Lumi.Text, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                day?.let { "Data terbaru: sinyal beli untuk ${Market.prettyDate(RadarPorto.nextSession(it.date))}" }
                    ?: "Dihitung dari harga closing semua saham IDX",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))
            GradientButton("Mulai Screening", onScreening, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow)
            Spacer(Modifier.height(10.dp))
            Text(
                "Cukup sekali di awal. Setelah itu sinyal beli tampil otomatis di sini setiap hari pukul 17.00 WIB.",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted, textAlign = TextAlign.Center,
            )
        }
        Disclaimer()
    }
}

@Composable
private fun Signals(d: RadarDay, tick: Int, onOpenPick: OpenPick) {
    val tier = UiConfig.FIXED_TIER
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BuyDateHero(d) }
        item { RefreshNote(d, tick) }
        listOf("profit", "winrate").forEach { j ->
            item(key = "j-$j") { JenisSection(d, tier, j, onOpenPick) }
        }
        item { MarketCard(d, null) }
        item { Disclaimer() }
    }
}

/** The session the signals are for, big, with a small radar for the look. */
@Composable
private fun BuyDateHero(d: RadarDay) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Lumi.DeepBlue.copy(alpha = 0.55f), Lumi.Green.copy(alpha = 0.22f))))
            .border(1.dp, Lumi.Sky.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadarScope(78.dp, periodMs = 3200, blips = 6)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("SINYAL BELI UNTUK", style = MaterialTheme.typography.labelMedium, color = Lumi.Green)
            Text(
                Market.prettyDate(RadarPorto.nextSession(d.date)),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White,
            )
        }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ShareIcon({ com.lumisignal.idx.share.ShareCards.shareDay(ctx, d, UiConfig.FIXED_TIER) }, 42.dp)
            Spacer(Modifier.height(3.dp))
            Text("Bagikan", style = MaterialTheme.typography.labelSmall, color = Lumi.Sky)
        }
    }
}

/** Tells the user when the signals change: every trading day at 17:00 WIB. */
@Composable
private fun RefreshNote(d: RadarDay, @Suppress("UNUSED_PARAMETER") tick: Int) {
    val now = Market.now()
    val waiting = Market.isWeekday(now.toLocalDate()) && now.toLocalTime() >= RadarRepo.READY_AT && d.date < RadarRepo.expectedDate(now)
    val text = when {
        waiting -> "Sinyal hari ini sedang disiapkan server dan akan muncul sebentar lagi (atau hari ini libur bursa)."
        else -> "Saham pantauan diperbarui otomatis setiap hari bursa pukul 17.00 WIB dan langsung masuk Riwayat Strategi. Harga entry, TP & SL baru muncul di Riwayat begitu saham terbeli, lengkap dengan notifikasi."
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lumi.Amber.copy(alpha = 0.1f))
            .border(1.dp, Lumi.Amber.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Notifications, null, tint = Lumi.Amber, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("REFRESH TIAP 17.00 WIB", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Lumi.Amber)
            Text(text, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
        }
    }
}

@Composable
private fun JenisSection(d: RadarDay, tier: String, jenis: String, onOpenPick: OpenPick) {
    val color = if (jenis == "profit") PROFIT_COLOR else WINRATE_COLOR
    val picks = d.board(tier, jenis)?.top5?.filter { it.valid }.orEmpty()
    Column {
        // Loud title so the two signal types are told apart at a glance.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.55f))))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                RadarBadge(if (jenis == "profit") "rocket" else "target", if (jenis == "profit") "#FF9F43" else "#22D3EE", jenis, 40.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d.jenisLabel(jenis).uppercase(),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                    color = Color(0xFF0B1020),
                )
                Text(
                    if (jenis == "profit") "Target kenaikan besar" else "Target kecil, lebih sering tercapai",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF0B1020).copy(alpha = 0.75f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${picks.size}", style = NumberStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = Color(0xFF0B1020))
                Text("saham", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0B1020).copy(alpha = 0.75f))
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                .background(Brush.verticalGradient(listOf(Lumi.SurfaceHi.copy(alpha = 0.72f), Lumi.Surface.copy(alpha = 0.88f))))
                .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                .padding(12.dp),
        ) {
            Text(RadarDay.JENIS_DESC[jenis] ?: "", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
            Spacer(Modifier.height(10.dp))
            if (picks.isEmpty()) {
                Text(
                    "Tidak ada saham yang lolos untuk sesi ini. Pasar tidak selalu memberi setup — itu normal.",
                    style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
                )
            } else {
                picks.forEachIndexed { n, p ->
                    val sid = p.sid ?: 0
                    PickRow(d, tier, jenis, p, sid, combined = true, rankLabel = "${n + 1}") { onOpenPick(tier, jenis, sid, p.ticker, true) }
                    if (n < picks.lastIndex) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun Disclaimer() {
    Text(
        "Alat bantu screening, bukan rekomendasi investasi. Selalu gunakan stop loss dan atur ukuran posisi.",
        style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
}
