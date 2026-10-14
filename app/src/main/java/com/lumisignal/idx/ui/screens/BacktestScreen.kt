package com.lumisignal.idx.ui.screens

import com.lumisignal.idx.UiConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisignal.idx.data.BtTrade
import com.lumisignal.idx.data.RadarDay
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.RadarRepo
import com.lumisignal.idx.ui.components.EmptyState
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.LoadingBlock
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.RadarBadge
import com.lumisignal.idx.ui.components.StatTile
import com.lumisignal.idx.ui.components.WinRateRing
import com.lumisignal.idx.ui.components.hexColor
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle

/**
 * Backtest tab: every strategy replayed on historical closings, month by month. Same rules as the live
 * radar: signal at the close, buy next session (≤ close +1%), sell at TP, SL or the end of the hold.
 */
@Composable
fun BacktestScreen() {
    val day by RadarRepo.day.collectAsStateWithLifecycle()
    val index by RadarRepo.btIndex.collectAsStateWithLifecycle()
    val tier by RadarRepo.tier.collectAsStateWithLifecycle()
    var month by rememberSaveable { mutableStateOf("") }
    var jenisIdx by rememberSaveable { mutableIntStateOf(if (RadarRepo.jenis == "winrate") 1 else 0) }
    var sel by rememberSaveable { mutableIntStateOf(0) }
    var trades by remember { mutableStateOf<List<BtTrade>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (RadarRepo.day.value == null) runCatching { RadarRepo.refresh() }
        val idx = RadarRepo.backtestIndex()
        if (idx == null) {
            failed = true
            loading = false
        } else if (month.isBlank()) {
            month = idx.months.firstOrNull()?.month ?: ""
        }
    }
    LaunchedEffect(month) {
        if (month.isBlank()) return@LaunchedEffect
        loading = true
        trades = RadarRepo.backtestMonth(month)
        failed = trades == null
        loading = false
    }

    val jenis = if (jenisIdx == 0) "profit" else "winrate"
    val all = trades.orEmpty().filter { it.tier == tier && it.jenis == jenis }
    val rows = (if (sel == 0) all.filter { it.combined } else all.filter { !it.combined && it.sid == sel })
        .sortedWith(compareByDescending<BtTrade> { it.signal }.thenBy { it.ticker })
    val ids = all.filter { !it.combined }.map { it.sid }.distinct().sorted()
    val d = day

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp)) {
            Text("Backtest", style = MaterialTheme.typography.headlineSmall, color = Lumi.Text)
            Text(
                "Uji strategi pada data historis${index?.period?.let { " · $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim,
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("PILIH BULAN (tanggal sinyal)", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    index?.months.orEmpty().forEach { m ->
                        val on = m.month == month
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp))
                                .background(if (on) Lumi.Blue.copy(alpha = 0.22f) else Lumi.Surface)
                                .border(1.dp, if (on) Lumi.Sky else Lumi.Border, RoundedCornerShape(14.dp))
                                .clickable { month = m.month; sel = 0 }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(m.label, style = MaterialTheme.typography.labelMedium, color = if (on) Lumi.Sky else Lumi.TextDim)
                        }
                    }
                }
            }
            if (UiConfig.SHOW_TIER_PICKER) item { TierChips(d, tier) { RadarRepo.setTier(it); sel = 0 } }
            item { Segmented(listOf("Profit tinggi", "Winrate tinggi"), jenisIdx, { jenisIdx = it; sel = 0 }) }
            if (UiConfig.SHOW_STRATEGIES) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BtChip(UiConfig.TOP5_TITLE, sel == 0, Lumi.Sky, null, jenis) { sel = 0 }
                    ids.forEach { id ->
                        val meta = d?.strategy(id)
                        BtChip(meta?.name ?: "Strategi $id", sel == id, hexColor(meta?.color ?: "#3D8BFF"), meta, jenis) { sel = id }
                    }
                }
            }
            when {
                loading -> item { LoadingBlock("Memuat backtest…") }
                failed -> item { EmptyState("Backtest belum bisa dimuat", "Periksa koneksi internet lalu buka lagi tab ini.") }
                else -> {
                    item { BtStatsCard(if (sel == 0) UiConfig.TOP5_TITLE else d?.strategy(sel)?.name ?: "Strategi", rows, d, tier, jenis, index?.months?.firstOrNull { it.month == month }?.label ?: month) }
                    if (rows.isEmpty()) {
                        item { EmptyState("Tidak ada transaksi", "Strategi ini tidak memberi sinyal yang terbeli di bulan dan tingkat likuiditas ini.") }
                    } else {
                        items(rows, key = { "${it.signal}-${it.combined}-${it.sid}-${it.ticker}" }) { t -> BtTradeCard(d, t) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BtChip(text: String, selected: Boolean, color: Color, meta: com.lumisignal.idx.data.RadarStrategy?, jenis: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) color.copy(alpha = 0.22f) else Lumi.Surface)
            .border(1.dp, if (selected) color else Lumi.Border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(start = if (meta != null) 5.dp else 14.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (meta != null) {
            RadarBadge(meta.icon, meta.color, jenis, 24.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) color else Lumi.TextDim, maxLines = 1)
    }
}

@Composable
private fun BtStatsCard(title: String, rows: List<BtTrade>, d: RadarDay?, tier: String, jenis: String, monthLabel: String) {
    val accent = if (jenis == "profit") Color(0xFFFF9F43) else Color(0xFF22D3EE)
    val wins = rows.count { it.pnl > 0 }
    val total = rows.sumOf { it.pnl }
    GlassCard(Modifier.fillMaxWidth(), accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WinRateRing(if (rows.isNotEmpty()) wins * 100.0 / rows.size else null, accent)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Lumi.Text)
                Text(if (UiConfig.SHOW_TIER_PICKER) "$monthLabel · ${d?.tierLabel(tier) ?: tier}" else monthLabel, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("$wins menang", Lumi.Green)
                    Pill("${rows.size - wins} kalah", Lumi.Red)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Transaksi", "${rows.size}", Modifier.weight(1f))
            StatTile("TP", "${rows.count { it.exit == "TP" }}", Modifier.weight(1f), Lumi.Green)
            StatTile("SL", "${rows.count { it.exit == "SL" }}", Modifier.weight(1f), Lumi.Red)
        }
        Spacer(Modifier.height(8.dp))
        StatTile("Akumulasi P/L", if (rows.isEmpty()) "–" else Fmt.pct(total), Modifier.fillMaxWidth(), if (total >= 0) Lumi.Green else Lumi.Red)
        Spacer(Modifier.height(8.dp))
        Text(
            "Sinyal dari closing, dibeli sesi berikutnya (≤ closing +1%), dijual saat kena TP, SL, atau di closing hari terakhir masa hold. P/L murni harga beli ke harga jual, tanpa fee.",
            style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
        )
    }
}

@Composable
private fun BtTradeCard(d: RadarDay?, t: BtTrade) {
    val meta = d?.strategy(t.sid)
    val (label, color) = when (t.exit) {
        "TP" -> "TP tercapai" to Lumi.Green
        "SL" -> "Stop loss" to Lumi.Red
        else -> "Jual akhir hold" to if (t.pnl > 0) Lumi.Green else Lumi.Amber
    }
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadarBadge(meta?.icon ?: "", meta?.color ?: "#3D8BFF", t.jenis, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.ticker, style = NumberStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
                Text(meta?.name ?: "Strategi ${t.sid}", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Pill(label, color)
                Spacer(Modifier.height(4.dp))
                Text(Fmt.pct(t.pnl, decimals = 2), style = NumberStyle.copy(fontSize = 14.sp), color = if (t.pnl > 0) Lumi.Green else Lumi.Red)
            }
        }
        Spacer(Modifier.height(10.dp))
        TradeTimeline(signal = t.signal, buy = t.buy, sell = t.sell, hold = t.hold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniLevel("BELI", Fmt.price(t.entry), Lumi.Blue, Modifier.weight(1f))
            MiniLevel("TP", Fmt.price(t.tp), Lumi.Green, Modifier.weight(1f))
            MiniLevel("SL", Fmt.price(t.sl), Lumi.Red, Modifier.weight(1f))
        }
    }
}
