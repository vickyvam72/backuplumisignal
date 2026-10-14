package com.lumisignal.idx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisignal.idx.Graph
import com.lumisignal.idx.data.LedgerRow
import com.lumisignal.idx.data.RadarDay
import com.lumisignal.idx.data.SOURCE_PORTO
import com.lumisignal.idx.data.SOURCE_STRATEGY
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.RadarRepo
import com.lumisignal.idx.service.Monitor
import com.lumisignal.idx.ui.components.EmptyState
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.RadarBadge
import com.lumisignal.idx.ui.components.StatTile
import com.lumisignal.idx.ui.components.WinRateRing
import com.lumisignal.idx.ui.components.WinStatsCard
import com.lumisignal.idx.ui.components.winStats
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle

@Composable
fun HistoryScreen(onOpenSignal: (String) -> Unit) {
    val ctx = LocalContext.current
    val items by Graph.history.items.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { Monitor.refreshIfStale(ctx) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp)) {
            Text("Riwayat", style = MaterialTheme.typography.headlineSmall, color = Lumi.Text)
            Text("Hasil nyata setiap sinyal & winrate-nya", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
        }
        Spacer(Modifier.height(14.dp))
        Segmented(
            options = listOf("Riwayat Strategi", "Riwayat Portofolio"),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (tab == 0) StrategyHistory(onOpenSignal) else PortoHistory(items.filter { it.source == SOURCE_PORTO }, onOpenSignal)
    }
}

// ------------------------------------------------------------------------------------ strategy (screening results, audited on device)

@Composable
private fun StrategyHistory(onOpenSignal: (String) -> Unit) {
    val day by RadarRepo.day.collectAsStateWithLifecycle()
    val tier by RadarRepo.tier.collectAsStateWithLifecycle()
    val items by Graph.history.items.collectAsStateWithLifecycle()
    var jenisIdx by rememberSaveable { mutableIntStateOf(if (RadarRepo.jenis == "winrate") 1 else 0) }
    var sel by rememberSaveable { mutableIntStateOf(0) } // 0 = top 5 gabungan, else strategy id
    var showLegacy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (day == null) runCatching { RadarRepo.refresh() } }

    val jenis = if (jenisIdx == 0) "profit" else "winrate"
    val radarItems = items.filter { it.source == SOURCE_STRATEGY && it.radar != null }
    val all = radarItems.filter { it.radar?.tier == tier && it.radar?.jenis == jenis }
    val rows = (if (sel == 0) all.filter { it.radar?.combined == true } else all.filter { it.radar?.combined == false && it.strategyId == sel })
        .sortedWith(compareByDescending<com.lumisignal.idx.data.Signal> { it.refDate }.thenBy { it.symbol })
    val d = day
    val strategyIds = (d?.specs?.get(tier)?.get(jenis)?.strategies?.map { it.id }.orEmpty() +
        all.filter { it.radar?.combined == false }.map { it.strategyId }).distinct()
    val legacy = items.filter { it.source == SOURCE_STRATEGY && it.radar == null }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { TierChips(d, tier) { RadarRepo.setTier(it); sel = 0 } }
        item { Segmented(listOf("Profit tinggi", "Winrate tinggi"), jenisIdx, { jenisIdx = it; sel = 0; RadarRepo.jenis = if (it == 1) "winrate" else "profit" }) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("Top 5 gabungan", sel == 0, Lumi.Sky, null) { sel = 0 }
                strategyIds.forEach { id ->
                    val meta = d?.strategy(id)
                    val tag = all.firstOrNull { it.strategyId == id }?.radar
                    val hex = meta?.color ?: tag?.color ?: "#3D8BFF"
                    FilterChip(meta?.name ?: tag?.name ?: "Strategi $id", sel == id, com.lumisignal.idx.ui.components.hexColor(hex), Triple(meta?.icon ?: tag?.icon ?: "", hex, jenis)) { sel = id }
                }
            }
        }
        item {
            val title = if (sel == 0) "Top 5 gabungan" else d?.strategy(sel)?.name ?: all.firstOrNull { it.strategyId == sel }?.radar?.name ?: "Strategi"
            WinStatsCard(title, "Hasil screening ${d?.tierLabel(tier) ?: tier} · dipantau harga Stockbit", winStats(rows), if (jenis == "profit") Color(0xFFFF9F43) else Color(0xFF22D3EE))
        }
        if (sel == 0 && all.isNotEmpty()) item { Leaderboard(d, jenis, all, strategyIds) { sel = it } }
        if (rows.isEmpty()) {
            item {
                EmptyState(
                    "Belum ada hasil",
                    "Hasil screening tercatat di sini begitu kamu membuka daftar sahamnya. Setiap saham dipantau otomatis — saat aplikasi terbuka tiap menit, di latar belakang ±15 menit — sampai terbeli, kena TP, SL, atau habis masa hold.",
                )
            }
        } else {
            items(rows, key = { it.id }) { sig -> SignalRow(sig) { onOpenSignal(sig.id) } }
        }
        if (legacy.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth(), padding = 14.dp, onClick = { showLegacy = !showLegacy }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Strategi lama (${legacy.size} sinyal)", style = MaterialTheme.typography.titleSmall, color = Lumi.Text)
                            Text("Sinyal dari 5 strategi bandar sebelum Radar Besok", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                        }
                        Icon(if (showLegacy) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = Lumi.TextMuted)
                    }
                }
            }
            if (showLegacy) {
                item { WinStatsCard("Strategi lama", "Dipantau dengan harga Stockbit", winStats(legacy), Lumi.TextDim) }
                items(legacy, key = { it.id }) { sig -> SignalRow(sig) { onOpenSignal(sig.id) } }
            }
        }
    }
}

@Composable
private fun Leaderboard(d: RadarDay?, jenis: String, all: List<com.lumisignal.idx.data.Signal>, ids: List<Int>, onSelect: (Int) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Text("Rapor per strategi", style = MaterialTheme.typography.titleSmall, color = Lumi.Text)
        Spacer(Modifier.height(8.dp))
        ids.forEach { id ->
            val list = all.filter { it.radar?.combined == false && it.strategyId == id }
            val tag = list.firstOrNull()?.radar
            val meta = d?.strategy(id)
            val hex = meta?.color ?: tag?.color ?: "#3D8BFF"
            val st = winStats(list)
            Row(Modifier.fillMaxWidth().clickable { onSelect(id) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                RadarBadge(meta?.icon ?: tag?.icon ?: "", hex, jenis, 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(meta?.name ?: tag?.name ?: "", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(st.winRate?.let { "${it.toInt()}%" } ?: "–", style = NumberStyle, color = com.lumisignal.idx.ui.components.hexColor(hex))
                Spacer(Modifier.width(8.dp))
                Text(st.totalPnl?.let { Fmt.pct(it, decimals = 0) } ?: "${st.open} jalan", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, color: Color, badge: Triple<String, String, String>?, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) color.copy(alpha = 0.22f) else Lumi.Surface)
            .border(1.dp, if (selected) color else Lumi.Border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(start = if (badge != null) 5.dp else 14.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (badge != null) {
            RadarBadge(badge.first, badge.second, badge.third, 24.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) color else Lumi.TextDim, maxLines = 1)
    }
}

// ------------------------------------------------------------------------------------ portfolio (on device)

@Composable
private fun PortoHistory(portoItems: List<com.lumisignal.idx.data.Signal>, onOpenSignal: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { WinStatsCard("Portofolio kamu", "Saham yang kamu tandai \"Masukkan Porto\"", winStats(portoItems), Lumi.Green) }
        if (portoItems.isEmpty()) {
            item {
                EmptyState(
                    "Porto masih kosong",
                    "Buka sinyal di Radar Besok lalu tekan \"Masukkan Porto\" untuk saham yang benar-benar kamu beli. Lumi memantaunya dengan harga Stockbit sampai kena TP, SL, atau batas hari.",
                )
            }
        } else {
            items(portoItems, key = { it.id }) { sig -> SignalRow(sig) { onOpenSignal(sig.id) } }
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lumi.Surface)
            .border(1.dp, Lumi.Border, RoundedCornerShape(16.dp)).padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) Lumi.Blue.copy(alpha = 0.9f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (sel) Color.White else Lumi.TextDim, maxLines = 1)
            }
        }
    }
}
