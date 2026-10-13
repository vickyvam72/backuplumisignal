package com.lumisignal.idx.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisignal.idx.Graph
import com.lumisignal.idx.data.SOURCE_STRATEGY
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.ScanManager
import com.lumisignal.idx.engine.Strategy
import com.lumisignal.idx.service.ScanService
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.GradientButton
import com.lumisignal.idx.ui.components.LumiLogo
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.ProgressBar
import com.lumisignal.idx.ui.components.SectionHeader
import com.lumisignal.idx.ui.components.Sparkle
import com.lumisignal.idx.ui.components.StrategyBadge
import com.lumisignal.idx.ui.components.winStats
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle

@Composable
fun HomeScreen(onOpenScan: () -> Unit, onManual: (String) -> Unit) {
    val ctx = LocalContext.current
    val scan by ScanManager.state.collectAsStateWithLifecycle()
    val items by Graph.history.items.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Strategy?>(null) }
    val name = remember { Graph.session.displayName }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        Spacer(Modifier.height(18.dp))

        RadarHero(scan.running, scan.phase, scan.done, scan.total, onOpenScan)
        Spacer(Modifier.height(16.dp))

        ManualSearch(onManual)
        Spacer(Modifier.height(22.dp))

        SectionHeader("Pilih strategi", subtitle = "Hasilnya 5 saham terbaik, otomatis masuk Riwayat Strategi")
        Spacer(Modifier.height(10.dp))
        Strategy.entries.forEach { s ->
            val st = winStats(items.filter { it.source == SOURCE_STRATEGY && it.strategyId == s.id })
            StrategyCard(s, st.total, st.winRate, onClick = { if (!scan.running) confirm = s else onOpenScan() })
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Lumi Signal adalah alat bantu screening, bukan rekomendasi investasi. Sinyal dihitung dari data closing; saham gorengan bisa bergerak di luar perkiraan. Selalu gunakan stop loss dan atur ukuran posisi.",
            style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
        )
        Spacer(Modifier.height(24.dp))
    }

    confirm?.let { s ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Lumi.Surface,
            title = { Text(s.title, style = MaterialTheme.typography.titleLarge, color = Lumi.Text) },
            text = {
                Column {
                    Text(s.description, style = MaterialTheme.typography.bodyMedium, color = Lumi.TextDim)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Screening pertama di hari bursa yang sama memeriksa ±900 saham (±5–8 menit). Screening berikutnya memakai cache sehingga jauh lebih cepat. Proses tetap berjalan walau aplikasi ditutup.",
                        style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    ScanService.start(ctx, s)
                    onOpenScan()
                }) { Text("Mulai screening", color = Lumi.Sky) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Batal", color = Lumi.TextDim) }
            },
        )
    }
}

@Composable
private fun RadarHero(running: Boolean, phase: String, done: Int, total: Int, onOpenScan: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "radar")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "angle")
    val ref = Graph.cache.lastRefDate
    val now = Market.now()
    val note = if (Market.sessionInProgress(now)) {
        "Bursa masih berjalan — analisa memakai closing sesi sebelumnya."
    } else {
        "Data closing hari ini final setelah 16:30 WIB."
    }
    GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Blue, onClick = if (running) onOpenScan else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(50))
                        .background(Brush.radialGradient(listOf(Lumi.Blue.copy(alpha = 0.35f), Lumi.Blue.copy(alpha = 0.02f)))),
                )
                Box(Modifier.size(46.dp).rotate(if (running) angle else 0f), contentAlignment = Alignment.TopCenter) {
                    Sparkle(14.dp, Lumi.Sky)
                }
                Sparkle(18.dp, Lumi.Text)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("RADAR BESOK", style = MaterialTheme.typography.labelSmall, color = Lumi.Sky)
                Text(
                    if (ref.isNotBlank()) "Closing ${Market.prettyDate(ref)}" else "Analisa dari harga closing",
                    style = MaterialTheme.typography.titleMedium, color = Lumi.Text,
                )
                Text(note, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
            }
        }
        if (running) {
            Spacer(Modifier.height(14.dp))
            Text(phase, style = MaterialTheme.typography.labelMedium, color = Lumi.Sky)
            Spacer(Modifier.height(6.dp))
            ProgressBar(if (total > 0) done.toFloat() / total else 0f)
            Spacer(Modifier.height(6.dp))
            Text("Ketuk untuk melihat progres screening", style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted)
        }
    }
}

@Composable
private fun ManualSearch(onManual: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val valid = text.trim().length == 4
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Text("Analisa manual", style = MaterialTheme.typography.titleSmall, color = Lumi.Text)
        Text("Cek satu saham terhadap kelima strategi sekaligus", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Lumi.Bg.copy(alpha = 0.6f))
                    .border(1.dp, Lumi.Border, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = Lumi.TextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text("Kode saham, mis. BBCA", style = MaterialTheme.typography.bodyLarge, color = Lumi.TextMuted)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isLetter() }.take(4).uppercase() },
                        singleLine = true,
                        textStyle = NumberStyle.copy(fontSize = 17.sp, color = Lumi.Text),
                        cursorBrush = SolidColor(Lumi.Sky),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { if (valid) onManual(text.trim()) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            GradientButton("Analisa", { if (valid) onManual(text.trim()) }, enabled = valid)
        }
    }
}

@Composable
private fun StrategyCard(s: Strategy, total: Int, winRate: Double?, onClick: () -> Unit) {
    val accent = Lumi.strategyColor(s.id)
    GlassCard(Modifier.fillMaxWidth(), accent = accent, padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StrategyBadge(s.id, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleSmall, color = Lumi.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.tagline, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Risiko ${s.risk}", accent)
                    if (winRate != null) Pill("Winrate ${winRate.toInt()}%", Lumi.Green)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, "Mulai", tint = accent) }
        }
    }
}
