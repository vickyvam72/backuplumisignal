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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisignal.idx.Graph
import com.lumisignal.idx.data.SOURCE_PORTO
import com.lumisignal.idx.data.SOURCE_STRATEGY
import com.lumisignal.idx.engine.Strategy
import com.lumisignal.idx.service.Monitor
import com.lumisignal.idx.ui.components.EmptyState
import com.lumisignal.idx.ui.components.WinStatsCard
import com.lumisignal.idx.ui.components.winStats
import com.lumisignal.idx.ui.theme.Lumi

@Composable
fun HistoryScreen(onOpenSignal: (String) -> Unit) {
    val ctx = LocalContext.current
    val items by Graph.history.items.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { Monitor.refreshIfStale(ctx) }

    val strategyItems = items.filter { it.source == SOURCE_STRATEGY }
    val portoItems = items.filter { it.source == SOURCE_PORTO }
    val shown = if (tab == 0) {
        if (filter == 0) strategyItems else strategyItems.filter { it.strategyId == filter }
    } else portoItems

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Riwayat", style = MaterialTheme.typography.headlineSmall, color = Lumi.Text)
                Text("Audit setiap sinyal & ukur winrate", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
            }
        }
        Spacer(Modifier.height(14.dp))
        Segmented(
            options = listOf("Riwayat Strategi", "Riwayat Portofolio"),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (tab == 0) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Semua", filter == 0, Lumi.Sky) { filter = 0 }
                        Strategy.entries.forEach { s ->
                            Chip("S${s.id} · ${s.tagline}", filter == s.id, Lumi.strategyColor(s.id)) { filter = s.id }
                        }
                    }
                }
                item {
                    val title = if (filter == 0) "Semua strategi" else Strategy.byId(filter).title
                    WinStatsCard(
                        title, "Top 5 hasil screening yang dipantau otomatis", winStats(shown),
                        if (filter == 0) Lumi.Sky else Lumi.strategyColor(filter),
                    )
                }
                if (filter == 0 && strategyItems.isNotEmpty()) {
                    item { StrategyLeaderboard(strategyItems) }
                }
            } else {
                item {
                    WinStatsCard("Portofolio kamu", "Saham yang kamu tandai \"Masukkan Porto\"", winStats(portoItems), Lumi.Green)
                }
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        if (tab == 0) "Belum ada riwayat strategi" else "Porto masih kosong",
                        if (tab == 0) "Jalankan screening dari Beranda. Lima saham terbaik otomatis masuk ke sini dan dipantau sampai kena TP atau SL."
                        else "Tekan \"Masukkan Porto\" pada hasil screening untuk saham yang benar-benar kamu beli.",
                    )
                }
            } else {
                items(shown, key = { it.id }) { sig -> SignalRow(sig) { onOpenSignal(sig.id) } }
            }
        }
    }
}

@Composable
private fun StrategyLeaderboard(items: List<com.lumisignal.idx.data.Signal>) {
    com.lumisignal.idx.ui.components.GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Text("Rapor per strategi", style = MaterialTheme.typography.titleSmall, color = Lumi.Text)
        Spacer(Modifier.height(8.dp))
        Strategy.entries.forEach { s ->
            val st = winStats(items.filter { it.strategyId == s.id })
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                com.lumisignal.idx.ui.components.StrategyBadge(s.id, 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(s.tagline, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim, modifier = Modifier.weight(1f))
                Text(
                    st.winRate?.let { "${it.toInt()}%" } ?: "–",
                    style = com.lumisignal.idx.ui.theme.NumberStyle, color = Lumi.strategyColor(s.id),
                )
                Spacer(Modifier.width(8.dp))
                Text("${st.wins}M/${st.losses}K", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
            }
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
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (sel) Color.White else Lumi.TextDim)
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) color.copy(alpha = 0.22f) else Lumi.Surface)
            .border(1.dp, if (selected) color else Lumi.Border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) color else Lumi.TextDim)
    }
}
