package com.lumisignal.idx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisignal.idx.data.AuthExpiredException
import com.lumisignal.idx.data.RadarDay
import com.lumisignal.idx.data.StockHit
import com.lumisignal.idx.data.StockInfo
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.ManualResult
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.RadarRepo
import com.lumisignal.idx.engine.Scanner
import com.lumisignal.idx.ui.components.BrokerPanel
import com.lumisignal.idx.ui.components.CandleChart
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.GradientButton
import com.lumisignal.idx.ui.components.LoadingBlock
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.RadarBadge
import com.lumisignal.idx.ui.components.SectionHeader
import com.lumisignal.idx.ui.components.StatTile
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** One stock against every Radar Besok strategy (from the latest radar) plus its chart and broker flow. */
@Composable
fun ManualScreen(symbol: String, onBack: () -> Unit, onOpenPick: OpenPick) {
    var result by remember { mutableStateOf<ManualResult?>(null) }
    var info by remember { mutableStateOf<StockInfo?>(null) }
    var radarDate by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val day by RadarRepo.day.collectAsStateWithLifecycle()
    val tier by RadarRepo.tier.collectAsStateWithLifecycle()

    LaunchedEffect(symbol, attempt) {
        error = null
        loaded = false
        coroutineScope {
            val radar = async { runCatching { if (RadarRepo.day.value == null || RadarRepo.isStale()) RadarRepo.refresh() }; RadarRepo.refreshStocks() }
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

    Column(Modifier.fillMaxSize()) {
        val r = result
        ScreenHeader(
            "Cek $symbol",
            subtitle = (r?.name ?: info?.name)?.takeIf { it.isNotBlank() } ?: "Radar Besok + data Stockbit",
            onBack = onBack,
        )
        if (!loaded) {
            LoadingBlock("Mengambil radar & candle harian $symbol…")
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            if (r != null) {
                val bars = r.bars
                val t = bars.last()
                val chg = Market.changePct(t, bars.getOrNull(bars.size - 2))
                GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Blue) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Fmt.price(t.close), style = NumberStyle.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
                        Spacer(Modifier.width(10.dp))
                        Text(Fmt.pct(chg), style = NumberStyle.copy(fontSize = 16.sp), color = if (chg >= 0) Lumi.Green else Lumi.Red, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    Text("Closing ${Market.prettyDate(r.refDate)}", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                    r.lastPrice?.let {
                        if (it != t.close) Text("Harga saat ini ${Fmt.price(it)} (sesi berjalan)", style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted)
                    }
                    Spacer(Modifier.height(10.dp))
                    CandleChart(bars, plan = null, signalDate = r.refDate, height = 220.dp)
                }
                Spacer(Modifier.height(12.dp))
            } else if (error != null) {
                GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Amber) {
                    Text("Data Stockbit tidak bisa diambil", style = MaterialTheme.typography.titleSmall, color = Lumi.Amber)
                    Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                    Spacer(Modifier.height(10.dp))
                    GradientButton("Coba lagi", { attempt++ }, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(12.dp))
            }

            RadarCheck(day, symbol, info, radarDate, tier, onOpenPick)
            Spacer(Modifier.height(12.dp))

            r?.day?.takeIf { it.buyers.isNotEmpty() }?.let {
                GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Purple) {
                    SectionHeader("Jejak bandar hari itu", subtitle = "Broker summary closing ${Market.shortDate(r.refDate)} · info tambahan, bukan syarat radar")
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
        }
    }
}

@Composable
private fun RadarCheck(day: RadarDay?, symbol: String, info: StockInfo?, radarDate: String, tier: String, onOpenPick: OpenPick) {
    GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Sky) {
        SectionHeader(
            "Radar Besok",
            subtitle = if (radarDate.isNotBlank()) "Cek terhadap semua strategi · closing ${Market.prettyDate(radarDate, false)}" else "Data radar belum tersedia",
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
            Text("Tidak memenuhi syarat strategi radar mana pun di closing ini.", style = MaterialTheme.typography.titleSmall, color = Lumi.TextDim)
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
