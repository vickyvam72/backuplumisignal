package com.lumisignal.idx.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisignal.idx.Graph
import com.lumisignal.idx.data.RadarDay
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.RadarPorto
import com.lumisignal.idx.engine.RadarRepo
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.GradientButton
import com.lumisignal.idx.ui.components.LumiLogo
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.RadarBadge
import com.lumisignal.idx.ui.components.SectionHeader
import com.lumisignal.idx.ui.components.Sparkle
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onOpenRadar: () -> Unit, onManual: (String) -> Unit) {
    val day by RadarRepo.day.collectAsStateWithLifecycle()
    val state by RadarRepo.state.collectAsStateWithLifecycle()
    val tier by RadarRepo.tier.collectAsStateWithLifecycle()
    val name = remember { Graph.session.displayName }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (day == null || RadarRepo.isStale()) runCatching { RadarRepo.refresh() }
    }

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

        RadarHero(day, state.loading, tier) {
            scope.launch { runCatching { RadarRepo.refresh() } }
            onOpenRadar()
        }
        Spacer(Modifier.height(14.dp))
        TierChips(day, tier) { RadarRepo.setTier(it) }
        Spacer(Modifier.height(16.dp))

        day?.let { d ->
            SectionHeader("Top 5 gabungan", subtitle = "Ringkasan radar ${d.tierLabel(tier)} · ketuk untuk detail semua strategi")
            Spacer(Modifier.height(10.dp))
            listOf("profit", "winrate").forEach { j ->
                TopStrip(d, tier, j, onOpenRadar)
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(8.dp))
            SectionHeader("Strategi di tingkat ini", subtitle = "Tiap tingkat likuiditas punya 5 strategi sendiri untuk tiap jenis sinyal")
            Spacer(Modifier.height(10.dp))
            StrategyGallery(d, tier, onOpenRadar)
            Spacer(Modifier.height(16.dp))
        }

        ManualSearch(onManual)
        Spacer(Modifier.height(16.dp))
        Text(
            "Lumi Signal adalah alat bantu screening, bukan rekomendasi investasi. Sinyal dihitung dari data closing; saham bisa bergerak di luar perkiraan. Selalu gunakan stop loss dan atur ukuran posisi.",
            style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RadarHero(day: RadarDay?, loading: Boolean, tier: String, onScreen: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "radar")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "angle")
    val stale = RadarRepo.isStale()
    val count = day?.boards?.get(tier)?.values?.sumOf { b -> b.strategies.sumOf { s -> s.picks.count { it.valid } } } ?: 0
    GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Blue) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(50))
                        .background(Brush.radialGradient(listOf(Lumi.Blue.copy(alpha = 0.35f), Lumi.Blue.copy(alpha = 0.02f)))),
                )
                Box(Modifier.size(46.dp).rotate(if (loading) angle else 0f), contentAlignment = Alignment.TopCenter) {
                    Sparkle(14.dp, Lumi.Sky)
                }
                Sparkle(18.dp, Lumi.Text)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("RADAR BESOK", style = MaterialTheme.typography.labelSmall, color = Lumi.Sky)
                Text(
                    if (day != null) "Closing ${Market.prettyDate(day.date, false)}" else "Screening dari harga closing",
                    style = MaterialTheme.typography.titleMedium, color = Lumi.Text,
                )
                Text(
                    when {
                        day == null -> "Sekali ketuk: semua strategi di semua tingkat likuiditas."
                        stale -> "Radar closing berikutnya terbit ±17:50 WIB setiap hari bursa."
                        else -> "$count sinyal di ${day.tierLabel(tier)} · beli ${Market.prettyDate(RadarPorto.nextSession(day.date), false)}"
                    },
                    style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim,
                )
            }
        }
        day?.market?.let { m ->
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(m.label, if (m.guard) Lumi.Red else if (m.weak30) Lumi.Amber else Lumi.Green)
                Pill("${Fmt.share(m.breadthMa5)} saham > MA20", Lumi.Sky)
            }
        }
        Spacer(Modifier.height(14.dp))
        GradientButton(
            if (loading) "Mengambil radar…" else "Screening sekarang",
            onScreen, Modifier.fillMaxWidth(), enabled = !loading, icon = Icons.Filled.PlayArrow,
        )
    }
}

@Composable
private fun TopStrip(d: RadarDay, tier: String, jenis: String, onOpen: () -> Unit) {
    val picks = d.board(tier, jenis)?.top5?.filter { it.valid }.orEmpty()
    val color = if (jenis == "profit") Color(0xFFFF9F43) else Color(0xFF22D3EE)
    GlassCard(Modifier.fillMaxWidth(), accent = color, padding = 14.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d.jenisLabel(jenis), style = MaterialTheme.typography.titleSmall, color = Lumi.Text, modifier = Modifier.weight(1f))
            d.specs[tier]?.get(jenis)?.top5Stats?.let {
                Text("backtest WR ${Fmt.num(it.winrate, 0)}%", style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (picks.isEmpty()) {
            Text("Tidak ada sinyal hari ini", style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted)
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                picks.forEach { p ->
                    val meta = p.sid?.let { d.strategy(it) }
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(Lumi.Bg.copy(alpha = 0.5f))
                            .border(1.dp, Lumi.Border, RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadarBadge(meta?.icon ?: "", meta?.color ?: "#3D8BFF", jenis, 24.dp)
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(p.ticker, style = NumberStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
                            Text("≤ ${Fmt.price(p.entryHigh)}", style = MaterialTheme.typography.labelSmall, color = Lumi.TextDim)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrategyGallery(d: RadarDay, tier: String, onOpen: () -> Unit) {
    listOf("profit", "winrate").forEach { jenis ->
        val board = d.board(tier, jenis) ?: return@forEach
        val specs = d.specs[tier]?.get(jenis)?.strategies.orEmpty()
        Text(d.jenisLabel(jenis).uppercase(), style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            board.strategies.forEach { bs ->
                val meta = d.strategy(bs.id)
                val spec = specs.firstOrNull { it.id == bs.id }
                val n = bs.picks.count { it.valid }
                Column(
                    Modifier.width(118.dp).clip(RoundedCornerShape(18.dp)).background(Lumi.Surface.copy(alpha = 0.8f))
                        .border(1.dp, Lumi.Border, RoundedCornerShape(18.dp)).clickable(onClick = onOpen).padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadarBadge(meta?.icon ?: "", meta?.color ?: "#3D8BFF", jenis, 38.dp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (!bs.active) "off" else "$n",
                            style = NumberStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            color = if (!bs.active) Lumi.TextMuted else if (n > 0) Lumi.Green else Lumi.TextDim,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(meta?.name ?: "", style = MaterialTheme.typography.labelMedium, color = Lumi.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(34.dp))
                    spec?.let { Text("WR ${Fmt.num(it.stats.winrate, 0)}% · ${Fmt.pct(it.stats.akumulasiPl, decimals = 0)}", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ManualSearch(onManual: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val valid = text.trim().length == 4
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Text("Cek satu saham", style = MaterialTheme.typography.titleSmall, color = Lumi.Text)
        Text("Lihat strategi radar mana yang dipenuhi, chart, dan jejak bandarnya", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
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
            GradientButton("Cek", { if (valid) onManual(text.trim()) }, enabled = valid)
        }
    }
}
