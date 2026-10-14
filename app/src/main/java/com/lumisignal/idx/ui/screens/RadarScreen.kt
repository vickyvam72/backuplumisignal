package com.lumisignal.idx.ui.screens

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.lumisignal.idx.service.Monitor
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import com.lumisignal.idx.ui.components.ProgressBar
import com.lumisignal.idx.ui.components.RadarScope
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

/** Stages of a screening: scanning animation, liquidity tier, signal type, results. */
const val STAGE_SCAN = 0
const val STAGE_TIER = 1
const val STAGE_JENIS = 2
const val STAGE_RESULT = 3

private val TIER_HINT = mapOf(
    "semua" to "Semua saham aktif, termasuk yang sepi transaksi",
    "250jt" to "Transaksi ≥ Rp250 juta per hari",
    "1M" to "Transaksi ≥ Rp1 miliar per hari, cukup likuid",
    "5M" to "Transaksi ≥ Rp5 miliar per hari, likuid",
    "20M" to "Transaksi ≥ Rp20 miliar per hari, sangat likuid",
)

/**
 * Screening flow: a radar sweep while the latest results load, then the user picks a liquidity tier and
 * a signal type, then every strategy of that combination is shown at once.
 */
@Composable
fun RadarScreen(onBack: () -> Unit, onOpenPick: OpenPick, initialStage: Int = STAGE_SCAN, initialJenis: String = "") {
    val day by RadarRepo.day.collectAsStateWithLifecycle()
    val state by RadarRepo.state.collectAsStateWithLifecycle()
    val tier by RadarRepo.tier.collectAsStateWithLifecycle()
    var stage by rememberSaveable { mutableIntStateOf(initialStage) }
    var jenis by rememberSaveable { mutableStateOf(initialJenis.ifBlank { RadarRepo.jenis }) }
    var scanDone by rememberSaveable { mutableStateOf(initialStage != STAGE_SCAN) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (scanDone) return@LaunchedEffect
        val started = System.currentTimeMillis()
        runCatching { RadarRepo.refresh() }
        val left = 3200L - (System.currentTimeMillis() - started)
        if (left > 0) kotlinx.coroutines.delay(left)
        scanDone = true
        if (RadarRepo.day.value != null) stage = STAGE_TIER
    }
    val back: () -> Unit = {
        when (stage) {
            STAGE_RESULT -> stage = STAGE_JENIS
            STAGE_JENIS -> stage = STAGE_TIER
            else -> onBack()
        }
    }
    BackHandler(enabled = stage == STAGE_RESULT || stage == STAGE_JENIS) { back() }

    Column(Modifier.fillMaxSize()) {
        val d = day
        ScreenHeader(
            title = when (stage) {
                STAGE_TIER -> "Pilih tingkat likuiditas"
                STAGE_JENIS -> "Pilih jenis sinyal"
                STAGE_RESULT -> d?.jenisLabel(jenis) ?: "Hasil screening"
                else -> "Screening"
            },
            subtitle = d?.let { "Closing ${Market.prettyDate(it.date, false)} · beli ${Market.prettyDate(RadarPorto.nextSession(it.date))}" }
                ?: "Radar Besok",
            onBack = back,
        )
        when {
            stage == STAGE_SCAN && !scanDone -> ScanView()
            d == null -> Column(Modifier.padding(18.dp)) {
                GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Red) {
                    Text("Radar belum bisa dimuat", style = MaterialTheme.typography.titleMedium, color = Lumi.Red)
                    Spacer(Modifier.height(6.dp))
                    Text(state.error ?: "Coba lagi sebentar.", style = MaterialTheme.typography.bodyMedium, color = Lumi.TextDim)
                    Spacer(Modifier.height(14.dp))
                    GradientButton("Coba lagi", {
                        scope.launch { runCatching { RadarRepo.refresh() }; if (RadarRepo.day.value != null) stage = STAGE_TIER }
                    }, Modifier.fillMaxWidth())
                }
            }
            stage == STAGE_TIER || stage == STAGE_SCAN -> TierStep(d, tier) { RadarRepo.setTier(it); stage = STAGE_JENIS }
            stage == STAGE_JENIS -> JenisStep(d, tier, jenis) { jenis = it; RadarRepo.jenis = it; stage = STAGE_RESULT }
            else -> {
                val ctx = LocalContext.current
                // Every shown result goes into the strategy history and is audited with live prices.
                LaunchedEffect(d.date, tier, jenis) {
                    if (RadarPorto.saveScreening(d, tier, jenis) > 0) Monitor.refreshIfStale(ctx, 0)
                }
                ResultList(d, tier, jenis, state.error, onOpenPick, onChangeTier = { stage = STAGE_TIER }, onChangeJenis = { stage = STAGE_JENIS })
            }
        }
    }
}

private val SCAN_STEPS = listOf(
    "Memindai closing semua saham IDX…",
    "Membaca kondisi pasar…",
    "Mencocokkan pola tiap strategi…",
    "Menyusun batas beli, TP & SL…",
)

@Composable
private fun ScanView() {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(800)
            step = (step + 1) % SCAN_STEPS.size
        }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RadarScope(280.dp, periodMs = 1600, blips = 18)
        Spacer(Modifier.height(30.dp))
        Text("Screening berjalan", style = MaterialTheme.typography.titleLarge, color = Lumi.Text)
        Spacer(Modifier.height(6.dp))
        Text(SCAN_STEPS[step], style = MaterialTheme.typography.bodyMedium, color = Lumi.Sky)
        Spacer(Modifier.height(18.dp))
        ProgressBar(((step + 1) / SCAN_STEPS.size.toFloat()))
    }
}

private fun signalCount(d: RadarDay, tier: String, jenis: String? = null): Int =
    d.boards[tier].orEmpty().filterKeys { jenis == null || it == jenis }.values.sumOf { b ->
        b.strategies.flatMap { s -> s.picks.filter { it.valid }.map { it.ticker } }.toSet().size
    }

@Composable
private fun TierStep(d: RadarDay, selected: String, onPick: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { MarketCard(d, null) }
        item {
            Text(
                "Pilih sesuai seberapa ramai saham yang ingin kamu perdagangkan. Makin tinggi tingkatnya, makin mudah beli-jual dalam jumlah besar.",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim,
            )
        }
        RadarRepo.TIER_KEYS.forEach { key ->
            item(key = key) {
                val t = d.tiers.firstOrNull { it.key == key }
                val n = signalCount(d, key)
                val sel = key == selected
                GlassCard(Modifier.fillMaxWidth(), accent = if (sel) Lumi.Sky else null, padding = 14.dp, onClick = { onPick(key) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                                .background(Brush.linearGradient(listOf(Lumi.Sky.copy(alpha = 0.9f), Lumi.DeepBlue))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${RadarRepo.TIER_KEYS.indexOf(key) + 1}", style = NumberStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t?.label ?: key, style = MaterialTheme.typography.titleMedium, color = Lumi.Text)
                            Text(TIER_HINT[key] ?: "", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                            if (t != null) Text("${t.stocks} saham dipindai", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$n", style = NumberStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = if (n > 0) Lumi.Green else Lumi.TextMuted)
                            Text("sinyal", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JenisStep(d: RadarDay, tier: String, selected: String, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Pill(d.tierLabel(tier), Lumi.Sky)
        Spacer(Modifier.height(12.dp))
        listOf("profit", "winrate").forEach { j ->
            val color = if (j == "profit") Color(0xFFFF9F43) else Color(0xFF22D3EE)
            val n = signalCount(d, tier, j)
            GlassCard(Modifier.fillMaxWidth(), accent = color, onClick = { onPick(j) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadarBadge(if (j == "profit") "rocket" else "target", if (j == "profit") "#FF9F43" else "#22D3EE", j, 58.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.jenisLabel(j), style = MaterialTheme.typography.titleLarge, color = Lumi.Text)
                        Spacer(Modifier.height(2.dp))
                        Text(RadarDay.JENIS_DESC[j] ?: "", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("$n saham hari ini", if (n > 0) Lumi.Green else Lumi.TextMuted)
                    Pill(if (j == "profit") "TP lebar" else "TP min. 3%", color)
                    if (j == selected) Pill("Terakhir dipilih", Lumi.Sky)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ResultList(
    d: RadarDay, tier: String, jenis: String, error: String?, onOpenPick: OpenPick,
    onChangeTier: () -> Unit, onChangeJenis: () -> Unit,
) {
    val board = d.board(tier, jenis)
    val specs = d.specs[tier]?.get(jenis)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChangeChip(d.tierLabel(tier), Lumi.Sky, onChangeTier)
                ChangeChip(d.jenisLabel(jenis), if (jenis == "profit") Color(0xFFFF9F43) else Color(0xFF22D3EE), onChangeJenis)
            }
        }
        item { MarketCard(d, error) }
        if (board == null || specs == null) {
            item { EmptyState("Belum ada data", "Radar untuk pilihan ini belum tersedia.") }
        } else {
            item { Top5Card(d, tier, jenis, board, onOpenPick) }
            item { SectionHeaderLine("Per strategi", "Masing-masing maksimal 5 saham terbaik") }
            for (bs in board.strategies) {
                val spec = specs.strategies.firstOrNull { it.id == bs.id } ?: continue
                item(key = "s-${bs.id}") { StrategyCard(d, tier, jenis, spec, bs, onOpenPick) }
            }
        }
        item {
            Text(
                "Radar diperbarui otomatis setiap hari bursa ±17:50 WIB dari closing semua saham. Hasil nyata setiap sinyal tercatat di tab Riwayat.",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
            )
        }
    }
}

@Composable
private fun ChangeChip(text: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        Spacer(Modifier.width(4.dp))
        Text("ganti", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
    }
}

@Composable
private fun SectionHeaderLine(title: String, sub: String) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Lumi.Text)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
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
                Text("5 saham pilihan terbaik dari kelima strategi di bawah", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
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
                    Pill(if (bs.active) "${picks.size} saham" else "Tidak aktif", if (!bs.active) Lumi.Amber else if (picks.isNotEmpty()) Lumi.Green else Lumi.TextMuted)
                    if (spec.weak) Pill("Khusus pasar lemah", Lumi.Amber)
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
