package com.lumisignal.idx.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisignal.idx.Graph
import com.lumisignal.idx.data.Bar
import com.lumisignal.idx.data.BrokerSummary
import com.lumisignal.idx.data.SOURCE_PORTO
import com.lumisignal.idx.data.SOURCE_STRATEGY
import com.lumisignal.idx.data.Signal
import com.lumisignal.idx.data.SignalStatus
import com.lumisignal.idx.data.StrategyEval
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.Scanner
import com.lumisignal.idx.engine.Strategy
import com.lumisignal.idx.ui.components.CandleChart
import com.lumisignal.idx.ui.components.BrokerPanel
import com.lumisignal.idx.ui.components.ChecksPanel
import com.lumisignal.idx.ui.components.EmptyState
import com.lumisignal.idx.ui.components.GhostButton
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.GradientButton
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.PlanPanel
import com.lumisignal.idx.ui.components.ScoreRing
import com.lumisignal.idx.ui.components.SectionHeader
import com.lumisignal.idx.ui.components.StatTile
import com.lumisignal.idx.ui.components.StatusBadge
import com.lumisignal.idx.ui.components.StrategyBadge
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Detail for a saved signal (strategy history or portfolio). */
@Composable
fun SignalDetailScreen(id: String, onBack: () -> Unit) {
    val items by Graph.history.items.collectAsStateWithLifecycle()
    val sig = items.firstOrNull { it.id == id }
    var portoDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    if (sig == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Sinyal", onBack = onBack)
            EmptyState("Sinyal tidak ditemukan", "Mungkin sudah dihapus dari riwayat.")
        }
        return
    }
    val porto = if (sig.source == SOURCE_STRATEGY) items.firstOrNull { it.source == SOURCE_PORTO && it.linkedId == sig.id } else null
    val chartBars = (sig.bars + sig.trackBars).associateBy { it.date }.values.sortedBy { it.date }

    if (sig.radar != null) {
        RadarSignalDetail(sig, chartBars, onBack, onDelete = { deleteDialog = true })
    } else AnalysisDetail(
        symbol = sig.symbol,
        name = sig.name,
        strategy = Strategy.byId(sig.strategyId),
        eval = sig.eval,
        bars = chartBars,
        signalDate = sig.refDate,
        day = sig.broker,
        range20 = sig.broker20,
        range5 = sig.broker5,
        close = sig.close,
        changePct = sig.changePct,
        headerTag = if (sig.source == SOURCE_PORTO) "PORTO" else "RIWAYAT STRATEGI",
        onBack = onBack,
        statusCard = { TrackingCard(sig) },
        actions = {
            if (sig.source == SOURCE_STRATEGY) {
                if (porto == null) {
                    GradientButton("Masukkan Porto", { portoDialog = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
                } else {
                    GhostButton("Sudah ada di Porto", {}, Modifier.fillMaxWidth(), icon = Icons.Filled.CheckCircle, color = Lumi.Green)
                }
            } else {
                GhostButton("Hapus dari Porto", { deleteDialog = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Delete, color = Lumi.Red)
            }
        },
    )

    if (portoDialog) {
        PortoDialog(sig, onDismiss = { portoDialog = false }) { price ->
            Scanner.addToPorto(sig, price)
            portoDialog = false
        }
    }
    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            containerColor = Lumi.Surface,
            title = { Text("Hapus ${sig.symbol} dari Porto?", color = Lumi.Text) },
            text = { Text("Posisi ini tidak akan dihitung lagi di winrate porto.", color = Lumi.TextDim) },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialog = false
                    Graph.history.remove(sig.id)
                    onBack()
                }) { Text("Hapus", color = Lumi.Red) }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Batal", color = Lumi.TextDim) } },
        )
    }
}

@Composable
private fun TrackingCard(sig: Signal) {
    val color = Lumi.statusColor(sig.status)
    GlassCard(Modifier.fillMaxWidth(), accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("STATUS PEMANTAUAN", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
                Spacer(Modifier.height(4.dp))
                StatusBadge(sig.status)
            }
            val pnl = sig.pnlPct
            if (pnl != null && sig.status != SignalStatus.CANCELLED) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (sig.isOpen) "P/L BERJALAN" else "HASIL", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
                    Text(Fmt.pct(pnl), style = NumberStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = if (pnl >= 0) Lumi.Green else Lumi.Red)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(sig.statusNote, style = MaterialTheme.typography.bodyMedium, color = Lumi.TextDim)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Masuk", sig.entryPrice?.let { Fmt.price(it) } ?: "–", Modifier.weight(1f), Lumi.Blue)
            StatTile("Terakhir", Fmt.price(sig.lastPrice), Modifier.weight(1f))
            StatTile("Keluar", sig.exitPrice?.let { Fmt.price(it) } ?: "–", Modifier.weight(1f), color)
        }
        Spacer(Modifier.height(8.dp))
        val updated = if (sig.lastUpdate > 0) SimpleDateFormat("d MMM HH:mm", Locale("id", "ID")).format(Date(sig.lastUpdate)) else "belum"
        Text(
            "Diperbarui: $updated · dipantau tiap ±15 menit saat bursa buka, dan dicek ulang dengan candle harian setelah tutup.",
            style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
        )
    }
}

/** Portfolio position taken from the Radar Besok screening. */
@Composable
private fun RadarSignalDetail(sig: Signal, chartBars: List<Bar>, onBack: () -> Unit, onDelete: () -> Unit) {
    val tag = sig.radar ?: return
    val accent = com.lumisignal.idx.ui.components.hexColor(tag.color)
    val plan = sig.eval.plan
    val sr = remember(chartBars.size, sig.refDate) { com.lumisignal.idx.engine.Levels.of(chartBars.filter { it.date <= sig.refDate }) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(sig.symbol, subtitle = sig.name.ifBlank { tag.name }, onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            GlassCard(Modifier.fillMaxWidth(), accent = accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.lumisignal.idx.ui.components.RadarBadge(tag.icon, tag.color, tag.jenis, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "PORTO · " + when (tag.jenis) {
                                "profit" -> "PROFIT TINGGI"
                                "winrate" -> "WINRATE TINGGI"
                                else -> "ANALISA MANUAL"
                            } + if (tag.combined) " · TOP 5 GABUNGAN" else "",
                            style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted,
                        )
                        Text(tag.name, style = MaterialTheme.typography.titleSmall, color = Lumi.Text)
                        Text("Sinyal closing ${Market.prettyDate(sig.refDate)}", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Fmt.price(sig.close), style = NumberStyle.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        Fmt.pct(sig.changePct), style = NumberStyle.copy(fontSize = 16.sp),
                        color = if (sig.changePct >= 0) Lumi.Green else Lumi.Red, modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TrackingCard(sig)
            Spacer(Modifier.height(12.dp))
            plan?.let {
                GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Blue) {
                    SectionHeader("Rencana trading", subtitle = if (sig.entryPrice != null) "TP/SL dihitung dari harga beli sebenarnya" else "TP/SL dihitung dari batas beli, menyesuaikan saat terbeli")
                    Spacer(Modifier.height(14.dp))
                    PlanPanel(it, entryPrice = sig.entryPrice)
                }
                Spacer(Modifier.height(12.dp))
            }
            if (chartBars.isNotEmpty()) {
                GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                    SectionHeader("Chart & level trading", subtitle = "Candle harian · MA5 (biru muda) · MA20 (kuning)")
                    Spacer(Modifier.height(10.dp))
                    CandleChart(bars = chartBars, plan = plan, signalDate = sig.refDate, height = 320.dp, levels = com.lumisignal.idx.ui.components.srChartLevels(sr))
                }
                Spacer(Modifier.height(12.dp))
                GlassCard(Modifier.fillMaxWidth(), accent = com.lumisignal.idx.ui.components.SupportColor) {
                    SectionHeader("Support & resistance", subtitle = "Dihitung dari chart sampai hari sinyal")
                    Spacer(Modifier.height(8.dp))
                    com.lumisignal.idx.ui.components.LevelsPanel(sr, sig.close)
                }
                Spacer(Modifier.height(12.dp))
            }
            GlassCard(Modifier.fillMaxWidth(), accent = accent) {
                SectionHeader(if (tag.jenis == "manual") "Catatan analisa" else "Kenapa masuk radar")
                Spacer(Modifier.height(8.dp))
                ChecksPanel(sig.eval.checks, "teknikal")
                if (tag.exitText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Keluar: ${tag.exitText}", style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted)
                }
                if (tag.desc.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Syarat: ${tag.desc}", style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted)
                }
            }
            Spacer(Modifier.height(12.dp))
            GhostButton("Hapus dari Porto", onDelete, Modifier.fillMaxWidth(), icon = Icons.Filled.Delete, color = Lumi.Red)
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Shared analysis layout: why the stock was picked (bandar + technical), chart and trade plan. */
@Composable
fun AnalysisDetail(
    symbol: String,
    name: String,
    strategy: Strategy,
    eval: StrategyEval,
    bars: List<Bar>,
    signalDate: String,
    day: BrokerSummary?,
    range20: BrokerSummary?,
    range5: BrokerSummary? = null,
    close: Double,
    changePct: Double,
    headerTag: String,
    onBack: () -> Unit,
    statusCard: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val accent = Lumi.strategyColor(strategy.id)
    val bandarChecks = eval.checks.filter { it.kind == "bandar" }
    val techChecks = eval.checks.filter { it.kind != "bandar" }
    val bandarPass = bandarChecks.count { it.pass }
    val techPass = techChecks.count { it.pass }
    val driver = when {
        bandarChecks.isNotEmpty() && bandarPass == bandarChecks.size && techPass == techChecks.size -> "Bandar + teknikal kompak"
        bandarChecks.isNotEmpty() && bandarPass * techChecks.size >= techPass * bandarChecks.size -> "Didorong jejak bandar"
        else -> "Didorong sinyal teknikal"
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(symbol, subtitle = name.ifBlank { strategy.title }, onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            // ---- hero
            GlassCard(Modifier.fillMaxWidth(), accent = accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StrategyBadge(strategy.id, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(headerTag, style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
                        Text(strategy.title, style = MaterialTheme.typography.titleSmall, color = Lumi.Text)
                        Text("Closing ${Market.prettyDate(signalDate)}", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                    }
                    ScoreRing(eval.score, accent, 56.dp)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Fmt.price(close), style = NumberStyle.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        Fmt.pct(changePct), style = NumberStyle.copy(fontSize = 17.sp),
                        color = if (changePct >= 0) Lumi.Green else Lumi.Red, modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (eval.passed) "Sinyal beli" else "Belum memenuhi", if (eval.passed) Lumi.Green else Lumi.Amber, filled = eval.passed)
                    Pill(driver, Lumi.Sky)
                }
            }
            Spacer(Modifier.height(12.dp))

            statusCard?.let {
                it()
                Spacer(Modifier.height(12.dp))
            }

            if (eval.lowLiquidity) {
                GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Amber, padding = 14.dp) {
                    Text("⚠ Likuiditas rendah", style = MaterialTheme.typography.titleSmall, color = Lumi.Amber)
                    Spacer(Modifier.height(4.dp))
                    Text(eval.liquidityNote, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---- chart
            GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                SectionHeader("Chart & level trading", subtitle = "Candle harian · MA5 (biru muda) · MA20 (kuning)")
                Spacer(Modifier.height(10.dp))
                CandleChart(bars = bars, plan = eval.plan, signalDate = signalDate)
            }
            Spacer(Modifier.height(12.dp))

            // ---- plan
            eval.plan?.let { plan ->
                GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Blue) {
                    SectionHeader("Rencana trading", subtitle = "Stop loss dari struktur harga, bukan rasio untung-rugi tetap")
                    Spacer(Modifier.height(14.dp))
                    PlanPanel(plan)
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---- bandar evidence
            if (bandarChecks.isNotEmpty() || day != null || range20 != null) {
                GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Purple) {
                    SectionHeader("Bukti bandar", subtitle = "$bandarPass dari ${bandarChecks.size} syarat bandarmologi terpenuhi")
                    Spacer(Modifier.height(8.dp))
                    ChecksPanel(eval.checks, "bandar")
                    if (day != null && day.buyers.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("BROKER SUMMARY HARI SINYAL", style = MaterialTheme.typography.labelSmall, color = Lumi.Sky)
                        Spacer(Modifier.height(6.dp))
                        BrokerPanel(day)
                    }
                    if (range5 != null && range5.buyers.isNotEmpty() && strategy.needsRange5) {
                        Spacer(Modifier.height(16.dp))
                        Text("ALIRAN BANDAR 5 HARI", style = MaterialTheme.typography.labelSmall, color = Lumi.Sky)
                        Spacer(Modifier.height(6.dp))
                        BrokerPanel(range5)
                    }
                    if (range20 != null && range20.buyers.isNotEmpty() && strategy.needsRange) {
                        Spacer(Modifier.height(16.dp))
                        Text("AKUMULASI 20 HARI", style = MaterialTheme.typography.labelSmall, color = Lumi.Sky)
                        Spacer(Modifier.height(6.dp))
                        BrokerPanel(range20)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---- technical evidence
            GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Sky) {
                SectionHeader("Bukti teknikal", subtitle = "$techPass dari ${techChecks.size} syarat teknikal terpenuhi")
                Spacer(Modifier.height(8.dp))
                ChecksPanel(eval.checks, "teknikal")
                Spacer(Modifier.height(10.dp))
                TechnicalStats(bars, signalDate)
            }
            Spacer(Modifier.height(12.dp))

            actions?.let {
                it()
                Spacer(Modifier.height(12.dp))
            }
            Text(
                strategy.description,
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun TechnicalStats(bars: List<Bar>, signalDate: String) {
    val upTo = bars.filter { it.date <= signalDate }
    if (upTo.size < 2) return
    val closes = upTo.map { it.close }
    val ma5 = Market.sma(closes, 5)
    val ma20 = Market.sma(closes, 20)
    val atr = Market.atr(upTo.takeLast(15))
    val last = upTo.last()
    val prior = upTo.dropLast(1).takeLast(20)
    val avgVol = if (prior.isNotEmpty()) prior.sumOf { it.volume } / prior.size else 0.0
    val vr = if (avgVol > 0) last.volume / avgVol else 0.0
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("MA5", ma5?.let { Fmt.price(it) } ?: "–", Modifier.weight(1f), Lumi.Sky)
        StatTile("MA20", ma20?.let { Fmt.price(it) } ?: "–", Modifier.weight(1f), Lumi.Amber)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Volume", "${Fmt.ratio(vr)} avg", Modifier.weight(1f))
        StatTile("ATR 14", Fmt.price(atr), Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Nilai transaksi", Fmt.idr(last.value), Modifier.weight(1f))
        StatTile("Frekuensi", Fmt.lots(last.freq) + "x", Modifier.weight(1f))
    }
}
