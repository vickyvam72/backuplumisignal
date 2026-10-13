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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumisignal.idx.data.AuthExpiredException
import com.lumisignal.idx.data.SOURCE_STRATEGY
import com.lumisignal.idx.data.Signal
import com.lumisignal.idx.data.StrategyEval
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.ManualResult
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.Scanner
import com.lumisignal.idx.engine.Strategy
import com.lumisignal.idx.ui.components.CandleChart
import com.lumisignal.idx.ui.components.GhostButton
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.GradientButton
import com.lumisignal.idx.ui.components.LoadingBlock
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.ScoreRing
import com.lumisignal.idx.ui.components.SectionHeader
import com.lumisignal.idx.ui.components.StrategyBadge
import com.lumisignal.idx.ui.components.ChecksPanel
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle

/** Keeps the last manual analysis so its per-strategy detail screen can be opened. */
object ManualHolder {
    var result: ManualResult? = null
}

@Composable
fun ManualScreen(symbol: String, onBack: () -> Unit, onOpenStrategy: (Int) -> Unit) {
    var result by remember { mutableStateOf(ManualHolder.result?.takeIf { it.symbol == symbol }) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var portoFor by remember { mutableStateOf<Signal?>(null) }

    LaunchedEffect(symbol, attempt) {
        if (result != null && attempt == 0) return@LaunchedEffect
        error = null
        try {
            val r = Scanner.analyzeManual(symbol)
            ManualHolder.result = r
            result = r
        } catch (e: AuthExpiredException) {
            error = e.message
        } catch (e: Exception) {
            error = e.message ?: "Gagal mengambil data $symbol"
        }
    }

    Column(Modifier.fillMaxSize()) {
        val r = result
        ScreenHeader(
            "Analisa $symbol",
            subtitle = r?.let { "${it.name.ifBlank { symbol }} · closing ${Market.prettyDate(it.refDate, false)}" } ?: "Mengambil data dari Stockbit…",
            onBack = onBack,
        )
        when {
            error != null -> Column(Modifier.padding(18.dp)) {
                GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Red) {
                    Text("Analisa gagal", style = MaterialTheme.typography.titleMedium, color = Lumi.Red)
                    Spacer(Modifier.height(6.dp))
                    Text(error ?: "", style = MaterialTheme.typography.bodyMedium, color = Lumi.TextDim)
                    Spacer(Modifier.height(12.dp))
                    GradientButton("Coba lagi", { attempt++ }, Modifier.fillMaxWidth())
                }
            }
            r == null -> LoadingBlock("Mengambil 60 candle harian & broker summary $symbol…")
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
                val bars = r.data.bars
                val t = bars.last()
                val prev = bars.getOrNull(bars.size - 2)
                val chg = Market.changePct(t, prev)
                val passCount = r.evals.count { it.passed }
                GlassCard(Modifier.fillMaxWidth(), accent = if (passCount > 0) Lumi.Green else Lumi.Blue) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Fmt.price(t.close), style = NumberStyle.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
                        Spacer(Modifier.width(10.dp))
                        Text(Fmt.pct(chg), style = NumberStyle.copy(fontSize = 16.sp), color = if (chg >= 0) Lumi.Green else Lumi.Red, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (passCount > 0) "$passCount dari 5 strategi memberi sinyal beli" else "Belum ada strategi yang memberi sinyal beli",
                        style = MaterialTheme.typography.titleSmall, color = if (passCount > 0) Lumi.Green else Lumi.TextDim,
                    )
                    r.lastPrice?.let {
                        if (it != t.close) {
                            Text("Harga saat ini ${Fmt.price(it)} (sesi berjalan)", style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    CandleChart(bars, plan = r.evals.firstOrNull { it.passed }?.plan, signalDate = r.refDate, height = 220.dp)
                }
                Spacer(Modifier.height(16.dp))
                SectionHeader("Hasil per strategi", subtitle = "Ketuk untuk melihat syarat yang lolos dan gagal")
                Spacer(Modifier.height(10.dp))
                r.evals.sortedWith(compareByDescending<StrategyEval> { it.passed }.thenByDescending { it.score }).forEach { ev ->
                    StrategyResultCard(
                        ev,
                        onDetail = { onOpenStrategy(ev.strategyId) },
                        onPorto = {
                            portoFor = Scanner.buildSignal(SOURCE_STRATEGY, Strategy.byId(ev.strategyId), r.data, ev, r.refDate)
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    portoFor?.let { sig ->
        PortoDialog(sig, onDismiss = { portoFor = null }) { price ->
            Scanner.addToPorto(sig.copy(id = "M-${sig.id}"), price)
            portoFor = null
        }
    }
}

@Composable
private fun StrategyResultCard(ev: StrategyEval, onDetail: () -> Unit, onPorto: () -> Unit) {
    val s = Strategy.byId(ev.strategyId)
    val accent = Lumi.strategyColor(s.id)
    var open by remember { mutableStateOf(ev.passed) }
    GlassCard(Modifier.fillMaxWidth(), accent = if (ev.passed) accent else null, onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StrategyBadge(s.id, 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleSmall, color = Lumi.Text)
                Spacer(Modifier.height(4.dp))
                val passed = ev.checks.count { it.pass }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (ev.passed) "SINYAL BELI" else "Tidak memenuhi", if (ev.passed) Lumi.Green else Lumi.TextDim, filled = ev.passed)
                    Pill("$passed/${ev.checks.size} syarat", accent)
                }
            }
            ScoreRing(ev.score, accent, 46.dp)
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = Lumi.TextMuted)
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            Text("TEKNIKAL", style = MaterialTheme.typography.labelSmall, color = Lumi.Sky)
            ChecksPanel(ev.checks, "teknikal")
            if (ev.checks.any { it.kind == "bandar" }) {
                Spacer(Modifier.height(6.dp))
                Text("BANDAR", style = MaterialTheme.typography.labelSmall, color = Lumi.Purple)
                ChecksPanel(ev.checks, "bandar")
            }
            ev.plan?.let { plan ->
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniLevel("BELI", Fmt.price(plan.entryHigh), Lumi.Blue, Modifier.weight(1f))
                    MiniLevel("SL", Fmt.price(plan.stopLoss), Lumi.Red, Modifier.weight(1f))
                    MiniLevel("TP1", Fmt.price(plan.tp1), Lumi.Green, Modifier.weight(1f))
                    MiniLevel("TP2", Fmt.price(plan.tp2), Lumi.Green, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Analisa lengkap", onDetail, Modifier.weight(1f))
                if (ev.passed) GhostButton("Masukkan Porto", onPorto, Modifier.weight(1f), icon = Icons.Filled.Add, color = Lumi.Green)
            }
        }
    }
}

/** Full analysis layout for one strategy from the last manual analysis. */
@Composable
fun ManualDetailScreen(strategyId: Int, onBack: () -> Unit) {
    val r = ManualHolder.result
    val ev = r?.evals?.firstOrNull { it.strategyId == strategyId }
    if (r == null || ev == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Analisa", onBack = onBack)
            com.lumisignal.idx.ui.components.EmptyState("Data analisa tidak tersedia", "Ulangi analisa manual dari Beranda.")
        }
        return
    }
    val bars = r.data.bars
    val t = bars.last()
    AnalysisDetail(
        symbol = r.symbol,
        name = r.name,
        strategy = Strategy.byId(strategyId),
        eval = ev,
        bars = bars,
        signalDate = r.refDate,
        day = r.data.day,
        range20 = r.data.range20,
        close = t.close,
        changePct = Market.changePct(t, bars.getOrNull(bars.size - 2)),
        headerTag = "ANALISA MANUAL",
        onBack = onBack,
    )
}
