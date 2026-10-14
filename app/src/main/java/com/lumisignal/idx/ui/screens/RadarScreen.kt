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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisignal.idx.data.Board
import com.lumisignal.idx.data.BoardStrategy
import com.lumisignal.idx.data.RadarDay
import com.lumisignal.idx.data.RadarPick
import com.lumisignal.idx.data.StrategySpec
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.RadarPorto
import com.lumisignal.idx.engine.RadarRepo
import com.lumisignal.idx.ui.components.EmptyState
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.GradientButton
import com.lumisignal.idx.ui.components.LoadingBlock
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.RadarBadge
import com.lumisignal.idx.ui.components.Sparkle
import com.lumisignal.idx.ui.components.hexColor
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle
import kotlinx.coroutines.launch

/** Opens one pick: tier, jenis, strategy id, ticker, combined flag. */
typealias OpenPick = (String, String, Int, String, Boolean) -> Unit

/** All Radar Besok results of one liquidity tier at once: both signal types, combined top 5 and every strategy. */
@Composable
fun RadarScreen(onBack: () -> Unit, onOpenPick: OpenPick) {
    val day by RadarRepo.day.collectAsStateWithLifecycle()
    val state by RadarRepo.state.collectAsStateWithLifecycle()
    val tier by RadarRepo.tier.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (day == null || RadarRepo.isStale()) runCatching { RadarRepo.refresh() }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Radar Besok",
            subtitle = day?.let { "Closing ${Market.prettyDate(it.date, false)} · beli ${Market.prettyDate(RadarPorto.nextSession(it.date))}" }
                ?: "Hasil screening semua strategi",
            onBack = onBack,
            trailing = {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Lumi.SurfaceHi)
                        .clickable(enabled = !state.loading) { scope.launch { runCatching { RadarRepo.refresh() } } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.loading) CircularProgressIndicator(color = Lumi.Sky, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    else Icon(Icons.Filled.Refresh, "Muat ulang", tint = Lumi.Text)
                }
            },
        )
        val d = day
        when {
            d == null && state.loading -> LoadingBlock("Mengambil hasil Radar Besok…")
            d == null -> Column(Modifier.padding(18.dp)) {
                GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Red) {
                    Text("Radar belum bisa dimuat", style = MaterialTheme.typography.titleMedium, color = Lumi.Red)
                    Spacer(Modifier.height(6.dp))
                    Text(state.error ?: "Coba lagi sebentar.", style = MaterialTheme.typography.bodyMedium, color = Lumi.TextDim)
                    Spacer(Modifier.height(14.dp))
                    GradientButton("Coba lagi", { scope.launch { runCatching { RadarRepo.refresh() } } }, Modifier.fillMaxWidth())
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { MarketCard(d, state.error) }
                item {
                    TierChips(d, tier) { RadarRepo.setTier(it) }
                    Spacer(Modifier.height(10.dp))
                    Segmented(listOf("Semua", "Profit besar", "Winrate tinggi"), filter, { filter = it })
                }
                val jenisList = when (filter) {
                    1 -> listOf("profit")
                    2 -> listOf("winrate")
                    else -> listOf("profit", "winrate")
                }
                for (jenis in jenisList) {
                    val board = d.board(tier, jenis)
                    val specs = d.specs[tier]?.get(jenis)
                    item(key = "h-$jenis") { JenisHeader(d, tier, jenis, board) }
                    if (board == null || specs == null) {
                        item(key = "e-$jenis") { EmptyState("Belum ada data", "Radar untuk tingkat ini belum tersedia.") }
                        continue
                    }
                    item(key = "t-$jenis") { Top5Card(d, tier, jenis, board, onOpenPick) }
                    for (bs in board.strategies) {
                        val spec = specs.strategies.firstOrNull { it.id == bs.id } ?: continue
                        item(key = "s-$jenis-${bs.id}") { StrategyCard(d, tier, jenis, spec, bs, onOpenPick) }
                    }
                }
                item {
                    Text(
                        "Radar dihitung otomatis setiap hari bursa ±17:50 WIB dari closing semua saham, dengan mesin yang sama persis dengan backtest " +
                            "(${d.calibrated}). Statistik pada kartu adalah hasil backtest; hasil nyata sejak ${Market.prettyDate(d.goLive, false)} ada di tab Riwayat.",
                        style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun MarketCard(d: RadarDay, error: String?) {
    val m = d.market
    val color = when {
        m.guard -> Lumi.Red
        m.weak30 -> Lumi.Amber
        else -> Lumi.Green
    }
    GlassCard(Modifier.fillMaxWidth(), accent = color, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text("KONDISI PASAR", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted, modifier = Modifier.weight(1f))
            Pill(m.label.ifBlank { "Pasar" }, color)
        }
        Spacer(Modifier.height(6.dp))
        Text(m.text, style = MaterialTheme.typography.bodyMedium, color = Lumi.TextDim)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("${Fmt.share(m.breadthMa5)} saham > MA20", Lumi.Sky)
            m.ewRet5?.let { Pill("Pasar 5 hari ${Fmt.pct(it * 100)}", if (it >= 0) Lumi.Green else Lumi.Red) }
        }
        if (RadarRepo.isStale()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Radar closing ${Market.prettyDate(RadarRepo.expectedDate(), false)} belum terbit (biasanya ±17:50 WIB, atau hari itu libur bursa). " +
                    "Yang tampil: closing ${Market.prettyDate(d.date, false)}.",
                style = MaterialTheme.typography.bodySmall, color = Lumi.Amber,
            )
        }
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text("Gagal memperbarui: $error", style = MaterialTheme.typography.bodySmall, color = Lumi.Red)
        }
    }
}

@Composable
fun TierChips(d: RadarDay?, selected: String, onSelect: (String) -> Unit) {
    Column {
        Text("TINGKAT LIKUIDITAS (rata-rata nilai transaksi 20 hari)", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadarRepo.TIER_KEYS.forEach { key ->
                val t = d?.tiers?.firstOrNull { it.key == key }
                val label = t?.label ?: key
                val sel = key == selected
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (sel) Lumi.Blue.copy(alpha = 0.22f) else Lumi.Surface)
                        .border(1.dp, if (sel) Lumi.Sky else Lumi.Border, RoundedCornerShape(14.dp))
                        .clickable { onSelect(key) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) Lumi.Sky else Lumi.TextDim)
                    if (t != null && t.stocks > 0) Text("${t.stocks} saham", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun JenisHeader(d: RadarDay, tier: String, jenis: String, board: Board?) {
    val j = d.jenis.firstOrNull { it.key == jenis }
    val stats = d.specs[tier]?.get(jenis)?.top5Stats
    val count = board?.strategies?.sumOf { s -> s.picks.count { it.valid } } ?: 0
    val color = if (jenis == "profit") Color(0xFFFF9F43) else Color(0xFF22D3EE)
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sparkle(12.dp, color)
            Spacer(Modifier.width(8.dp))
            Text(j?.label ?: jenis, style = MaterialTheme.typography.headlineSmall, color = Lumi.Text, modifier = Modifier.weight(1f))
            Pill("$count sinyal", color)
        }
        if (j != null) {
            Spacer(Modifier.height(2.dp))
            Text(j.desc, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
        }
        if (stats != null && stats.trades > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Backtest top 5 gabungan: winrate ${Fmt.num(stats.winrate)}% · akumulasi P/L ${Fmt.pct(stats.akumulasiPl)} · ${stats.trades} transaksi",
                style = MaterialTheme.typography.labelMedium, color = color,
            )
        }
    }
}

@Composable
private fun Top5Card(d: RadarDay, tier: String, jenis: String, board: Board, onOpenPick: OpenPick) {
    val picks = board.top5.filter { it.valid }
    GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Sky) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Lumi.Sky, Lumi.DeepBlue))),
                contentAlignment = Alignment.Center,
            ) { Text("5", style = NumberStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Top 5 gabungan", style = MaterialTheme.typography.titleMedium, color = Lumi.Text)
                Text("5 saham terbaik dari kelima strategi, strategi dengan rata-rata P/L terbaik didahulukan", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (picks.isEmpty()) {
            Text("Tidak ada saham yang lolos hari ini. Pasar tidak selalu memberi setup — itu normal.", style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted)
        } else {
            picks.forEachIndexed { n, p ->
                val sid = p.sid ?: 0
                PickRow(d, tier, jenis, p, sid, combined = true, rankLabel = "${n + 1}") { onOpenPick(tier, jenis, sid, p.ticker, true) }
                if (n < picks.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StrategyCard(d: RadarDay, tier: String, jenis: String, spec: StrategySpec, bs: BoardStrategy, onOpenPick: OpenPick) {
    val meta = d.strategy(spec.id)
    val accent = hexColor(meta?.color ?: "#3D8BFF")
    var open by remember { mutableStateOf(false) }
    val picks = bs.picks.filter { it.valid }
    GlassCard(Modifier.fillMaxWidth(), accent = if (bs.active) accent else null) {
        Row(Modifier.clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            RadarBadge(meta?.icon ?: "", meta?.color ?: "#3D8BFF", jenis, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(meta?.name ?: "Strategi ${spec.id}", style = MaterialTheme.typography.titleSmall, color = Lumi.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("WR ${Fmt.num(spec.stats.winrate, 0)}%", Lumi.Green)
                    Pill("P/L ${Fmt.pct(spec.stats.akumulasiPl, decimals = 0)}", accent)
                    if (spec.weak) Pill("Pasar lemah", Lumi.Amber)
                }
            }
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = Lumi.TextMuted)
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            RuleLine("Syarat", meta?.desc ?: spec.cond)
            RuleLine("Pasar", spec.regText)
            RuleLine("Urutan", spec.rankText)
            RuleLine("Keluar", spec.exitText)
            RuleLine(
                "Backtest",
                "${spec.stats.trades} transaksi · winrate ${Fmt.num(spec.stats.winrate)}% · kena TP ${Fmt.num(spec.stats.kenaTp)}% · rata-rata ${Fmt.pct(spec.stats.rataPl, decimals = 2)} · " +
                    "max drawdown ${Fmt.pct(spec.stats.maxDrawdown)} · hold ${Fmt.num(spec.stats.holdRata)} hari",
            )
        }
        Spacer(Modifier.height(10.dp))
        when {
            !bs.active -> NoteBox("Tidak aktif hari ini", bs.note, Lumi.Amber)
            picks.isEmpty() -> NoteBox(
                "Tidak ada sinyal",
                if (bs.candidates > 0) "${bs.candidates} saham memenuhi syarat, tapi semuanya masih dipegang dari sinyal sebelumnya." else "Tidak ada saham yang memenuhi syarat strategi ini hari ini.",
                Lumi.TextMuted,
            )
            else -> picks.forEachIndexed { n, p ->
                PickRow(d, tier, jenis, p, spec.id, combined = false, rankLabel = "${p.rank}") { onOpenPick(tier, jenis, spec.id, p.ticker, false) }
                if (n < picks.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
        if (bs.held.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Masih dipegang (tidak direkomendasikan ulang): ${bs.held.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted,
            )
        }
    }
}

@Composable
private fun RuleLine(label: String, text: String) {
    if (text.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Lumi.Sky, modifier = Modifier.width(72.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NoteBox(title: String, body: String, color: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = color)
        if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
    }
}

/** Compact pick: ticker, change, buy limit and TP/SL with the % from the buy limit. */
@Composable
fun PickRow(d: RadarDay, tier: String, jenis: String, p: RadarPick, sid: Int, combined: Boolean, rankLabel: String, onClick: () -> Unit) {
    val items by com.lumisignal.idx.Graph.history.items.collectAsStateWithLifecycle()
    val inPorto = remember(items, d.date, tier, jenis, sid, p.ticker) { RadarPorto.find(d.date, tier, jenis, sid, p.ticker, combined) != null }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lumi.Bg.copy(alpha = 0.45f))
            .border(1.dp, Lumi.Border.copy(alpha = 0.7f), RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (combined) {
                val meta = d.strategy(sid)
                RadarBadge(meta?.icon ?: "", meta?.color ?: "#3D8BFF", jenis, 30.dp)
            } else {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Lumi.SurfaceHi), contentAlignment = Alignment.Center) {
                    Text(rankLabel, style = NumberStyle.copy(fontSize = 13.sp), color = Lumi.Sky)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.ticker, style = NumberStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
                    Spacer(Modifier.width(6.dp))
                    Text(Fmt.pct(p.chg), style = NumberStyle.copy(fontSize = 12.sp), color = if (p.chg >= 0) Lumi.Green else Lumi.Red)
                    if (inPorto) {
                        Spacer(Modifier.width(6.dp))
                        Pill("Porto", Lumi.Green)
                    }
                }
                val sub = if (combined) d.strategy(sid)?.name ?: p.name else p.name
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("BELI ≤", style = MaterialTheme.typography.labelSmall, color = Lumi.Blue)
                Text(Fmt.price(p.entryHigh), style = NumberStyle.copy(fontSize = 16.sp), color = Lumi.Text)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LevelChip("TP", p.tp, p.tpPct, Lumi.Green, Modifier.weight(1f))
            LevelChip("SL", p.sl, p.slPct?.let { -it }, Lumi.Red, Modifier.weight(1f))
            Column(
                Modifier.weight(0.7f).clip(RoundedCornerShape(10.dp)).background(Lumi.SurfaceHi.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text("HOLD", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
                Text("${p.hold} hari", style = NumberStyle.copy(fontSize = 13.sp), color = Lumi.Text)
            }
        }
    }
}

@Composable
private fun LevelChip(label: String, price: Double?, pct: Double?, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(label + (pct?.let { " " + Fmt.pct(it, decimals = 2) } ?: ""), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        Text(Fmt.price(price), style = NumberStyle.copy(fontSize = 13.sp), color = Lumi.Text)
    }
}
