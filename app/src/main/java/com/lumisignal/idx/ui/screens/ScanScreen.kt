package com.lumisignal.idx.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisignal.idx.Graph
import com.lumisignal.idx.data.Signal
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.ScanManager
import com.lumisignal.idx.engine.Scanner
import com.lumisignal.idx.engine.Strategy
import com.lumisignal.idx.service.ScanService
import com.lumisignal.idx.ui.components.EmptyState
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.GradientButton
import com.lumisignal.idx.ui.components.ProgressBar
import com.lumisignal.idx.ui.components.Sparkle
import com.lumisignal.idx.ui.components.StatTile
import com.lumisignal.idx.ui.theme.Lumi

@Composable
fun ScanScreen(onBack: () -> Unit, onOpenSignal: (String) -> Unit) {
    val ctx = LocalContext.current
    val st by ScanManager.state.collectAsStateWithLifecycle()
    val items by Graph.history.items.collectAsStateWithLifecycle()
    var portoFor by remember { mutableStateOf<Signal?>(null) }
    val strategy = if (st.strategyId > 0) Strategy.byId(st.strategyId) else null
    val accent = Lumi.strategyColor(st.strategyId)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = strategy?.title ?: "Screening",
            subtitle = if (st.refDate.isNotBlank()) "Closing ${Market.prettyDate(st.refDate)}" else "Radar Besok",
            onBack = onBack,
        )
        when {
            st.running -> RunningView(st.phase, st.message, st.done, st.total, st.candidates, st.passed, accent)
            st.error != null -> Column(Modifier.padding(18.dp)) {
                GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Red) {
                    Text("Screening gagal", style = MaterialTheme.typography.titleMedium, color = Lumi.Red)
                    Spacer(Modifier.height(6.dp))
                    Text(st.error ?: "", style = MaterialTheme.typography.bodyMedium, color = Lumi.TextDim)
                    Spacer(Modifier.height(14.dp))
                    if (strategy != null) GradientButton("Coba lagi", { ScanService.start(ctx, strategy) }, Modifier.fillMaxWidth())
                }
            }
            st.finishedAt > 0 -> {
                val results = st.resultIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Kandidat", "${st.candidates}", Modifier.weight(1f))
                            StatTile("Lolos", "${st.passed}", Modifier.weight(1f), Lumi.Green)
                            StatTile("Top", "${results.size}", Modifier.weight(1f), accent)
                        }
                    }
                    if (results.isEmpty()) {
                        item {
                            EmptyState(
                                "Tidak ada sinyal hari ini",
                                "Tidak ada saham yang memenuhi semua syarat ${strategy?.title ?: "strategi ini"} pada closing terakhir. Coba strategi lain — pasar tidak selalu memberi setup yang bagus, dan itu normal.",
                            )
                        }
                    } else {
                        item {
                            Text(
                                "${results.size} saham terbaik sudah tersimpan di Riwayat Strategi dan dipantau otomatis. Ketuk \"Masukkan Porto\" untuk saham yang benar-benar kamu ambil.",
                                style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim,
                            )
                        }
                        itemsIndexed(results, key = { _, s -> s.id }) { i, sig ->
                            ResultCard(
                                rank = i + 1,
                                sig = sig,
                                inPorto = Graph.history.portoFor(sig.id) != null,
                                onOpen = { onOpenSignal(sig.id) },
                                onPorto = { portoFor = sig },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
            else -> EmptyState("Belum ada screening", "Pilih salah satu strategi di Beranda untuk memulai.")
        }
    }

    portoFor?.let { sig ->
        PortoDialog(sig, onDismiss = { portoFor = null }) { price ->
            Scanner.addToPorto(sig, price)
            portoFor = null
        }
    }
}

@Composable
private fun RunningView(phase: String, message: String, done: Int, total: Int, candidates: Int, passed: Int, accent: androidx.compose.ui.graphics.Color) {
    val t = rememberInfiniteTransition(label = "scan")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep")
    val pulse by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "pulse")
    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(10.dp))
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                for (i in 1..3) {
                    drawCircle(Lumi.Border.copy(alpha = 0.7f), radius = r * i / 3f, style = Stroke(1.5f))
                }
                drawArc(
                    Brush.sweepGradient(listOf(accent.copy(alpha = 0f), accent.copy(alpha = 0.55f)), center = center),
                    startAngle = sweep - 70f, sweepAngle = 70f, useCenter = true,
                )
                val rad = Math.toRadians(sweep.toDouble())
                drawLine(
                    accent, center,
                    Offset(center.x + (r * Math.cos(rad)).toFloat(), center.y + (r * Math.sin(rad)).toFloat()),
                    strokeWidth = 2.5f,
                )
                drawCircle(Lumi.Sky.copy(alpha = 0.25f * pulse), radius = r * 0.22f * pulse)
            }
            Sparkle(30.dp, Lumi.Text)
        }
        Spacer(Modifier.height(18.dp))
        Text(phase, style = MaterialTheme.typography.titleMedium, color = Lumi.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        ProgressBar(if (total > 0) done.toFloat() / total else 0f, color = accent)
        Spacer(Modifier.height(6.dp))
        Text(if (total > 0) "$done / $total" else "Menyiapkan…", style = MaterialTheme.typography.labelMedium, color = Lumi.TextMuted)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Kandidat", "$candidates", Modifier.weight(1f))
            StatTile("Lolos", "$passed", Modifier.weight(1f), Lumi.Green)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Kamu boleh menutup aplikasi — screening tetap berjalan dan kamu akan mendapat notifikasi saat selesai.",
            style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted, textAlign = TextAlign.Center,
        )
    }
}
