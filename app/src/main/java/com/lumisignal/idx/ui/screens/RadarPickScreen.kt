package com.lumisignal.idx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisignal.idx.Graph
import com.lumisignal.idx.data.Bar
import com.lumisignal.idx.data.RadarPick
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.Levels
import com.lumisignal.idx.ui.components.LevelsPanel
import com.lumisignal.idx.ui.components.SupportColor
import com.lumisignal.idx.ui.components.srChartLevels
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.RadarPorto
import com.lumisignal.idx.engine.RadarRepo
import com.lumisignal.idx.ui.components.CandleChart
import com.lumisignal.idx.ui.components.EmptyState
import com.lumisignal.idx.ui.components.GhostButton
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.GradientButton
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.PlanPanel
import com.lumisignal.idx.ui.components.RadarBadge
import com.lumisignal.idx.ui.components.SectionHeader
import com.lumisignal.idx.ui.components.StatTile
import com.lumisignal.idx.ui.components.hexColor
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle

/** Detail of one radar pick: plan, chart, support/resistance, why it was picked and the strategy's rules. */
@Composable
fun RadarPickScreen(tier: String, jenis: String, sid: Int, ticker: String, combined: Boolean, onBack: () -> Unit, onOpenSignal: (String) -> Unit) {
    val day by RadarRepo.day.collectAsStateWithLifecycle()
    val items by Graph.history.items.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf(false) }
    val d = day
    val board = d?.board(tier, jenis)
    val pick: RadarPick? = if (combined) board?.top5?.firstOrNull { it.ticker == ticker && it.sid == sid }
    else board?.strategies?.firstOrNull { it.id == sid }?.picks?.firstOrNull { it.ticker == ticker }
    if (d == null || pick == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(ticker, onBack = onBack)
            EmptyState("Sinyal tidak ditemukan", "Radar sudah diperbarui. Buka lagi dari halaman Radar Besok.")
        }
        return
    }
    val meta = d.strategy(sid)
    val spec = d.spec(tier, jenis, sid)
    val accent = hexColor(meta?.color ?: "#3D8BFF")
    val plan = remember(d.date, tier, jenis, sid, ticker) { RadarPorto.plan(d, tier, jenis, sid, pick) }
    val bars: List<Bar> = remember(d.date, ticker) { d.barsOf(ticker) }
    val sr = remember(d.date, ticker) { Levels.of(bars) }
    val porto = remember(items, d.date) { RadarPorto.find(d.date, tier, jenis, sid, ticker, combined) }

    Column(Modifier.fillMaxSize()) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        ScreenHeader(ticker, subtitle = pick.name.ifBlank { meta?.name ?: "" }, onBack = onBack,
            trailing = { ShareIcon({ com.lumisignal.idx.share.ShareCards.sharePick(ctx, d, jenis, pick, sid) }, 40.dp) })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            GlassCard(Modifier.fillMaxWidth(), accent = accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadarBadge(meta?.icon ?: "", meta?.color ?: "#3D8BFF", jenis, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            d.jenisLabel(jenis).uppercase() + (if (com.lumisignal.idx.UiConfig.SHOW_TIER_PICKER) " · ${d.tierLabel(tier)}" else "") +
                                if (combined) " · ${com.lumisignal.idx.UiConfig.TOP5_TITLE.uppercase()}" else "",
                            style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted,
                        )
                        Text(meta?.name ?: "Strategi $sid", style = MaterialTheme.typography.titleMedium, color = Lumi.Text)
                        Text("Sinyal beli untuk ${Market.prettyDate(RadarPorto.nextSession(d.date))}", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                    }
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)).border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("#${pick.rank}", style = NumberStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = accent) }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Fmt.price(pick.close), style = NumberStyle.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        Fmt.pct(pick.chg), style = NumberStyle.copy(fontSize = 17.sp),
                        color = if (pick.chg >= 0) Lumi.Green else Lumi.Red, modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Beli ${Market.prettyDate(RadarPorto.nextSession(d.date), false)}", Lumi.Sky)
                    Pill("Nilai tx ${Fmt.idr(pick.valueMa20)}/hari", Lumi.Purple)
                }
            }
            Spacer(Modifier.height(12.dp))

            GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Blue) {
                SectionHeader("Rencana trading", subtitle = "Harga TP/SL di bawah dihitung dari batas beli ${Fmt.price(pick.entryHigh)}")
                Spacer(Modifier.height(14.dp))
                PlanPanel(plan)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Kalau terbeli lebih murah (dibuka di bawah batas), TP dan SL ikut dihitung ulang dari harga beli itu dengan aturan yang sama — " +
                        "Lumi melakukannya otomatis untuk posisi di Porto.",
                    style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
                )
            }
            Spacer(Modifier.height(12.dp))

            if (bars.isNotEmpty()) {
                GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                    SectionHeader("Chart & level trading", subtitle = "Beli, TP, SL, support (ungu) & resistance (oranye)")
                    Spacer(Modifier.height(10.dp))
                    CandleChart(bars = bars, plan = plan, signalDate = d.date, height = 320.dp, levels = srChartLevels(sr))
                }
                Spacer(Modifier.height(12.dp))
                GlassCard(Modifier.fillMaxWidth(), accent = SupportColor) {
                    SectionHeader("Support & resistance", subtitle = "Area harga tempat saham biasanya memantul")
                    Spacer(Modifier.height(8.dp))
                    LevelsPanel(sr, pick.close)
                }
                Spacer(Modifier.height(12.dp))
            }

            GlassCard(Modifier.fillMaxWidth(), accent = accent) {
                SectionHeader("Kenapa masuk radar", subtitle = "Semua syarat strategi terpenuhi · beli ${Market.shortDate(RadarPorto.nextSession(d.date))}")
                Spacer(Modifier.height(8.dp))
                pick.why.forEach { WhyRow(it) }
                spec?.let {
                    WhyRow(it.rankText + " di antara saham yang lolos (peringkat ${pick.rank})")
                    WhyRow(it.regText)
                }
            }
            Spacer(Modifier.height(12.dp))

            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Aturan strategi", subtitle = meta?.name ?: "")
                Spacer(Modifier.height(8.dp))
                Text("Syarat: ${meta?.desc ?: ""}", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                spec?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("Keluar: ${it.exitText}", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (porto == null) {
                GradientButton("Masukkan Porto", { dialog = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            } else {
                GhostButton("Sudah di Porto · lihat posisi", { onOpenSignal(porto.id) }, Modifier.fillMaxWidth(), icon = Icons.Filled.CheckCircle, color = Lumi.Green)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Bukan rekomendasi investasi. Sinyal dihitung dari data closing; selalu pakai stop loss dan atur ukuran posisi.",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    if (dialog) {
        RadarPortoDialog(ticker, pick.entryHigh, onDismiss = { dialog = false }) { price ->
            val s = RadarPorto.add(d, tier, jenis, sid, pick, combined, price)
            dialog = false
            onOpenSignal(s.id)
        }
    }
}

@Composable
private fun WhyRow(text: String) {
    if (text.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Lumi.Green.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Lumi.Green, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Lumi.Text, modifier = Modifier.weight(1f))
    }
}

/** Optional real buy price before adding a radar pick to the portfolio. */
@Composable
fun RadarPortoDialog(ticker: String, top: Double, onDismiss: () -> Unit, onConfirm: (Double?) -> Unit) {
    var text by remember { mutableStateOf("") }
    val price = text.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lumi.Surface,
        title = { Text("Masukkan $ticker ke Porto", style = MaterialTheme.typography.titleLarge, color = Lumi.Text) },
        text = {
            Column {
                Text(
                    "Isi harga beli kalau kamu sudah membeli — TP dan SL langsung dihitung dari harga itu. " +
                        "Kosongkan untuk mengikuti aturan radar (beli ≤ ${Fmt.price(top)} di sesi berikutnya).",
                    style = MaterialTheme.typography.bodyMedium, color = Lumi.TextDim,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lumi.Bg)
                        .border(1.dp, Lumi.Border, RoundedCornerShape(12.dp)).padding(12.dp),
                ) {
                    if (text.isEmpty()) Text("Harga beli (opsional)", style = MaterialTheme.typography.bodyLarge, color = Lumi.TextMuted)
                    BasicTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() }.take(7) },
                        singleLine = true,
                        textStyle = NumberStyle.copy(fontSize = 18.sp, color = Lumi.Text),
                        cursorBrush = SolidColor(Lumi.Sky),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (price != null && price > 0 && Market.floorTick(price) != price) {
                    Spacer(Modifier.height(6.dp))
                    Text("Harga tidak sesuai fraksi IDX (kelipatan ${Fmt.price(Market.tick(price))}).", style = MaterialTheme.typography.bodySmall, color = Lumi.Amber)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(price?.takeIf { it > 0 }) },
                enabled = price == null || (price > 0 && Market.floorTick(price) == price),
            ) { Text("Masukkan Porto", color = Lumi.Green) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = Lumi.TextDim) } },
    )
}
