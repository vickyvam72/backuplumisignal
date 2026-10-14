package com.lumisignal.idx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisignal.idx.Graph
import com.lumisignal.idx.data.AuthExpiredException
import com.lumisignal.idx.data.RadarDay
import com.lumisignal.idx.data.RadarPick
import com.lumisignal.idx.data.StockHit
import com.lumisignal.idx.data.StockInfo
import com.lumisignal.idx.data.TradePlan
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.Levels
import com.lumisignal.idx.engine.ManualResult
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.RadarPorto
import com.lumisignal.idx.engine.RadarRepo
import com.lumisignal.idx.engine.Scanner
import com.lumisignal.idx.ui.components.BrokerPanel
import com.lumisignal.idx.ui.components.CandleChart
import com.lumisignal.idx.ui.components.EmptyState
import com.lumisignal.idx.ui.components.GhostButton
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.GradientButton
import com.lumisignal.idx.ui.components.LevelsPanel
import com.lumisignal.idx.ui.components.LoadingBlock
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.PlanPanel
import com.lumisignal.idx.ui.components.RadarBadge
import com.lumisignal.idx.ui.components.SectionHeader
import com.lumisignal.idx.ui.components.StatTile
import com.lumisignal.idx.ui.components.SupportColor
import com.lumisignal.idx.ui.components.srChartLevels
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** A radar signal of this stock, if any (preferred: the user's own liquidity tier). */
private data class RadarMatch(val tier: String, val jenis: String, val sid: Int, val pick: RadarPick)

private fun radarMatch(day: RadarDay?, info: StockInfo?, symbol: String, tier: String): RadarMatch? {
    if (day == null || info == null) return null
    val hits = info.hits.filter { it.status == "top5" }.sortedWith(compareBy({ it.tier != tier }, { RadarRepo.TIER_KEYS.indexOf(it.tier) }))
    for (h in hits) {
        val p = day.board(h.tier, h.jenis)?.strategies?.firstOrNull { it.id == h.sid }?.picks?.firstOrNull { it.ticker == symbol && it.valid }
        if (p != null) return RadarMatch(h.tier, h.jenis, h.sid, p)
    }
    return null
}

/** "Cek Saham" tab: analysis of any stock with entry, TP, SL, 2 supports and 2 resistances. */
@Composable
fun CekSahamScreen(onOpenPick: OpenPick, onOpenSignal: (String) -> Unit, initialSymbol: String = "") {
    var text by rememberSaveable { mutableStateOf(initialSymbol) }
    var symbol by rememberSaveable { mutableStateOf(initialSymbol) }
    val focus = LocalFocusManager.current
    val valid = text.trim().length == 4

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp)) {
            Text("Cek Saham", style = MaterialTheme.typography.headlineSmall, color = Lumi.Text)
            Text("Entry, TP, SL, support & resistance satu saham", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Lumi.Bg.copy(alpha = 0.6f))
                        .border(1.dp, Lumi.Border, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = Lumi.TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (text.isEmpty()) Text("Kode saham, mis. BBCA", style = MaterialTheme.typography.bodyLarge, color = Lumi.TextMuted)
                        BasicTextField(
                            value = text,
                            onValueChange = { v -> text = v.filter { it.isLetter() }.take(4).uppercase() },
                            singleLine = true,
                            textStyle = NumberStyle.copy(fontSize = 17.sp, color = Lumi.Text),
                            cursorBrush = SolidColor(Lumi.Sky),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { if (valid) { symbol = text.trim(); focus.clearFocus() } }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                GradientButton("Cek", { if (valid) { symbol = text.trim(); focus.clearFocus() } }, enabled = valid)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (symbol.isBlank()) {
            EmptyState(
                "Analisa saham pilihanmu",
                "Ketik kode saham untuk melihat batas beli, take profit, stop loss, dua support dan dua resistance-nya di chart, lalu tambahkan ke Porto agar dipantau otomatis.",
            )
        } else {
            StockAnalysis(symbol, onOpenPick, onOpenSignal)
        }
    }
}

@Composable
private fun StockAnalysis(symbol: String, onOpenPick: OpenPick, onOpenSignal: (String) -> Unit) {
    var result by remember(symbol) { mutableStateOf<ManualResult?>(null) }
    var info by remember(symbol) { mutableStateOf<StockInfo?>(null) }
    var radarDate by remember(symbol) { mutableStateOf("") }
    var loaded by remember(symbol) { mutableStateOf(false) }
    var error by remember(symbol) { mutableStateOf<String?>(null) }
    var attempt by remember(symbol) { mutableIntStateOf(0) }
    var dialog by remember(symbol) { mutableStateOf(false) }
    val day by RadarRepo.day.collectAsStateWithLifecycle()
    val tier by RadarRepo.tier.collectAsStateWithLifecycle()
    val items by Graph.history.items.collectAsStateWithLifecycle()

    LaunchedEffect(symbol, attempt) {
        error = null
        loaded = false
        coroutineScope {
            val radar = async {
                runCatching { if (RadarRepo.day.value == null || RadarRepo.isStale()) RadarRepo.refresh() }
                RadarRepo.refreshStocks()
            }
            val sb = async {
                try {
                    Scanner.analyzeManual(symbol)
                } catch (e: AuthExpiredException) {
                    error = e.message
                    null
                } catch (e: Exception) {
                    error = e.message ?: "Gagal mengambil data $symbol dari Stockbit"
                    null
                }
            }
            val st = radar.await()
            info = st?.stocks?.get(symbol)
            radarDate = st?.date ?: ""
            result = sb.await()
        }
        loaded = true
    }

    if (!loaded) {
        LoadingBlock("Menganalisa $symbol…")
        return
    }
    val r = result
    val match = radarMatch(day, info, symbol, tier)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        if (r == null) {
            GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Amber) {
                Text("Data $symbol tidak bisa diambil", style = MaterialTheme.typography.titleSmall, color = Lumi.Amber)
                Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                Spacer(Modifier.height(10.dp))
                GradientButton("Coba lagi", { attempt++ }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            RadarCheck(day, symbol, info, radarDate, tier, onOpenPick)
            Spacer(Modifier.height(24.dp))
        } else {
        val bars = r.bars
        val t = bars.last()
        val chg = Market.changePct(t, bars.getOrNull(bars.size - 2))
        val sr = remember(r) { Levels.of(bars) }
        val plan: TradePlan = remember(r, match, day) {
            if (match != null && day != null) RadarPorto.plan(day, match.tier, match.jenis, match.sid, match.pick) else Levels.manualPlan(bars, sr)
        }
        val existing = remember(items, match, r) {
            if (match != null && day != null) RadarPorto.find(day.date, match.tier, match.jenis, match.sid, symbol, false)
            else RadarPorto.findManual(r.refDate, symbol)
        }

        // ---- price
        GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Blue) {
            Text(r.name.ifBlank { info?.name ?: symbol }, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(symbol, style = NumberStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = Lumi.Sky)
                Spacer(Modifier.width(12.dp))
                Text(Fmt.price(t.close), style = NumberStyle.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
                Spacer(Modifier.width(10.dp))
                Text(Fmt.pct(chg), style = NumberStyle.copy(fontSize = 16.sp), color = if (chg >= 0) Lumi.Green else Lumi.Red, modifier = Modifier.padding(bottom = 5.dp))
            }
            Text("Closing ${Market.prettyDate(r.refDate)}", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
            r.lastPrice?.let {
                if (it != t.close) Text("Harga saat ini ${Fmt.price(it)} (sesi berjalan)", style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted)
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---- radar signal banner
        if (match != null && day != null) {
            val meta = day.strategy(match.sid)
            GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Green, padding = 14.dp, onClick = { onOpenPick(match.tier, match.jenis, match.sid, symbol, false) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadarBadge(meta?.icon ?: "", meta?.color ?: "#3D8BFF", match.jenis, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MASUK RADAR BESOK", style = MaterialTheme.typography.labelSmall, color = Lumi.Green)
                        Text(meta?.name ?: "", style = MaterialTheme.typography.titleSmall, color = Lumi.Text)
                        Text("${day.jenisLabel(match.jenis)} · ${day.tierLabel(match.tier)} · rencana di bawah mengikuti sinyal ini", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                    }
                    Pill("Lihat", Lumi.Green)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---- plan
        GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Blue) {
            SectionHeader(
                "Rencana trading",
                subtitle = if (match != null) "Dari sinyal Radar Besok" else "Dari support & resistance terdekat · hold maks 7 sesi",
            )
            Spacer(Modifier.height(14.dp))
            PlanPanel(plan)
        }
        Spacer(Modifier.height(12.dp))

        // ---- chart
        GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
            SectionHeader("Chart & level", subtitle = "Beli, TP, SL, support (ungu) & resistance (oranye)")
            Spacer(Modifier.height(10.dp))
            CandleChart(bars, plan = plan, signalDate = r.refDate, height = 330.dp, maxBars = 60, levels = srChartLevels(sr))
        }
        Spacer(Modifier.height(12.dp))

        // ---- support & resistance
        GlassCard(Modifier.fillMaxWidth(), accent = SupportColor) {
            SectionHeader("Support & resistance", subtitle = "Area harga tempat saham biasanya memantul")
            Spacer(Modifier.height(8.dp))
            LevelsPanel(sr, t.close)
        }
        Spacer(Modifier.height(12.dp))

        // ---- porto
        if (existing == null) {
            GradientButton("Tambahkan ke Porto", { dialog = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            Spacer(Modifier.height(6.dp))
            Text(
                "Saham di Porto dipantau otomatis: kapan terbeli, kena TP, kena SL, atau habis masa hold — lengkap dengan notifikasi.",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
            )
        } else {
            GhostButton("Sudah di Porto · lihat posisi", { onOpenSignal(existing.id) }, Modifier.fillMaxWidth(), icon = Icons.Filled.CheckCircle, color = Lumi.Green)
        }
        Spacer(Modifier.height(12.dp))

        RadarCheck(day, symbol, info, radarDate, tier, onOpenPick)
        Spacer(Modifier.height(12.dp))

        r.day?.takeIf { it.buyers.isNotEmpty() }?.let {
            GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Purple) {
                SectionHeader("Jejak bandar", subtitle = "Broker summary closing ${Market.shortDate(r.refDate)}")
                Spacer(Modifier.height(10.dp))
                BrokerPanel(it)
                r.range20?.takeIf { x -> x.buyers.isNotEmpty() }?.let { rg ->
                    Spacer(Modifier.height(16.dp))
                    Text("AKUMULASI 20 HARI", style = MaterialTheme.typography.labelSmall, color = Lumi.Sky)
                    Spacer(Modifier.height(6.dp))
                    BrokerPanel(rg)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(20.dp))

        if (dialog) {
            RadarPortoDialog(symbol, plan.entryHigh, onDismiss = { dialog = false }) { price ->
                dialog = false
                val s = if (match != null && day != null) RadarPorto.add(day, match.tier, match.jenis, match.sid, match.pick, false, price)
                else {
                    val why = buildList {
                        sr.supports.firstOrNull()?.let { add("Support 1 di ${Fmt.price(it.price)}, support 2 di ${Fmt.price(sr.supports.getOrNull(1)?.price)}") }
                        sr.resistances.firstOrNull()?.let { add("Resistance 1 di ${Fmt.price(it.price)}, resistance 2 di ${Fmt.price(sr.resistances.getOrNull(1)?.price)}") }
                    }
                    RadarPorto.addManual(symbol, r.name.ifBlank { info?.name ?: "" }, r.refDate, bars, plan, why, price)
                }
                onOpenSignal(s.id)
            }
        }
        }
    }
}

@Composable
private fun RadarCheck(day: RadarDay?, symbol: String, info: StockInfo?, radarDate: String, tier: String, onOpenPick: OpenPick) {
    GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Sky) {
        SectionHeader(
            "Cek strategi Radar Besok",
            subtitle = if (radarDate.isNotBlank()) "Closing ${Market.prettyDate(radarDate, false)}" else "Data radar belum tersedia",
        )
        Spacer(Modifier.height(10.dp))
        if (info == null) {
            Text(
                if (radarDate.isBlank()) "Radar belum bisa dimuat. Periksa koneksi internet lalu coba lagi."
                else "$symbol tidak ada di data radar closing ini (belum cukup riwayat, tidak aktif ditransaksikan, atau sedang disuspensi).",
                style = MaterialTheme.typography.bodyMedium, color = Lumi.TextDim,
            )
        } else RadarCheckBody(day, symbol, info, tier, onOpenPick)
    }
}

@Composable
private fun RadarCheckBody(day: RadarDay?, symbol: String, info: StockInfo, tier: String, onOpenPick: OpenPick) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("RSI14", info.rsi14?.let { Fmt.num(it) } ?: "–", Modifier.weight(1f))
            StatTile("Posisi 60h", info.pos60?.let { if (it < 0) "Dasar baru" else Fmt.share(it) } ?: "–", Modifier.weight(1f))
            StatTile("60 hari", info.gain60?.let { Fmt.pct(it * 100, decimals = 0) } ?: "–", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Volume", info.volRatio?.let { Fmt.ratio(it) + " avg" } ?: "–", Modifier.weight(1f))
            StatTile("Nilai tx 20h", Fmt.idr(info.valueMa20), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (info.eligible.isEmpty()) "Belum masuk tingkat likuiditas mana pun (harga ≤ 60, jarang ditransaksikan, ARA/ARB, atau data kurang)."
            else "Masuk tingkat: " + info.eligible.joinToString(", ") { day?.tierLabel(it) ?: it },
            style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
        )
        Spacer(Modifier.height(12.dp))
        if (info.hits.isEmpty()) {
            Text("Belum memenuhi syarat strategi radar mana pun di closing ini.", style = MaterialTheme.typography.titleSmall, color = Lumi.TextDim)
        } else {
            val ordered = info.hits.sortedWith(compareBy<StockHit>({ it.tier != tier }, { RadarRepo.TIER_KEYS.indexOf(it.tier) }, { it.jenis }, { it.status != "top5" }))
            ordered.forEach { h ->
                HitRow(day, h) { onOpenPick(h.tier, h.jenis, h.sid, symbol, false) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HitRow(day: RadarDay?, h: StockHit, onOpen: () -> Unit) {
    val meta = day?.strategy(h.sid)
    val (label, color) = when (h.status) {
        "top5" -> "Masuk top 5 (peringkat ${h.rank})" to Lumi.Green
        "ditahan" -> "Lolos syarat, tapi masih dipegang dari sinyal sebelumnya" to Lumi.Sky
        "pasar" -> "Lolos syarat, tapi strategi sedang tidak aktif (kondisi pasar)" to Lumi.Amber
        else -> "Lolos syarat, peringkat ${h.rank} dari ${h.of} (di luar top 5)" to Lumi.TextDim
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Lumi.Bg.copy(alpha = 0.45f))
            .border(1.dp, if (h.status == "top5") Lumi.Green.copy(alpha = 0.5f) else Lumi.Border, RoundedCornerShape(14.dp))
            .then(if (h.status == "top5") Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadarBadge(meta?.icon ?: "", meta?.color ?: "#3D8BFF", h.jenis, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(meta?.name ?: "Strategi ${h.sid}", style = MaterialTheme.typography.titleSmall, color = Lumi.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${day?.jenisLabel(h.jenis) ?: h.jenis} · ${day?.tierLabel(h.tier) ?: h.tier}", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
            Text(label, style = MaterialTheme.typography.bodySmall, color = color)
        }
        if (h.status == "top5") Pill("Lihat", Lumi.Green)
    }
}
