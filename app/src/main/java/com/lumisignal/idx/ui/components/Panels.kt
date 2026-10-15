package com.lumisignal.idx.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumisignal.idx.data.BrokerFlow
import com.lumisignal.idx.data.BrokerSummary
import com.lumisignal.idx.data.Check
import com.lumisignal.idx.data.Signal
import com.lumisignal.idx.data.TradePlan
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle

// ------------------------------------------------------------------- trade plan

@Composable
fun PlanPanel(plan: TradePlan, modifier: Modifier = Modifier, entryPrice: Double? = null) {
    val ref = entryPrice ?: plan.entryHigh
    fun dist(p: Double) = Fmt.pct(Market.pct(p, ref))
    Column(modifier) {
        PlanRow("TAKE PROFIT", Fmt.price(plan.tp), dist(plan.tp), Lumi.Green, plan.tpReason)
        PlanRow(
            if (entryPrice != null) "MASUK" else "AREA BELI",
            if (entryPrice != null) Fmt.price(entryPrice)
            else if (plan.entryLow == plan.entryHigh) Fmt.price(plan.entryHigh) else "${Fmt.price(plan.entryLow)} – ${Fmt.price(plan.entryHigh)}",
            if (entryPrice != null) "harga masuk" else "maks. ${Fmt.price(plan.entryHigh)}",
            Lumi.Blue,
            plan.cancelReason.ifBlank { "Masuk saat harga berada di area ini" },
        )
        PlanRow("STOP LOSS", Fmt.price(plan.stopLoss), dist(plan.stopLoss), Lumi.Red, plan.slReason, last = true)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pegang maksimal ${plan.maxHoldDays} sesi. Jual di TP, atau keluar disiplin di stop loss.",
            style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
        )
    }
}

@Composable
private fun PlanRow(label: String, price: String, dist: String, color: Color, reason: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Column(Modifier.width(18.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(color))
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(Lumi.Border))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.weight(1f))
                Text(dist, style = NumberStyle.copy(fontSize = 12.sp), color = Lumi.TextDim)
            }
            Text(price, style = NumberStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
            Text(reason, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
        }
    }
}

// ------------------------------------------------------------------- checklist

@Composable
fun ChecksPanel(checks: List<Check>, kind: String, modifier: Modifier = Modifier) {
    val list = checks.filter { it.kind == kind }
    Column(modifier) {
        list.forEach { c ->
            val tint = when {
                c.pass -> Lumi.Green
                c.required -> Lumi.Red
                else -> Lumi.TextMuted
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (c.pass) Icons.Filled.Check else Icons.Filled.Close, null,
                        tint = tint, modifier = Modifier.size(13.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.label, style = MaterialTheme.typography.titleSmall, color = Lumi.Text, modifier = Modifier.weight(1f, fill = false))
                        if (!c.required) {
                            Spacer(Modifier.width(6.dp))
                            Text("BONUS", style = MaterialTheme.typography.labelSmall, color = Lumi.Amber)
                        }
                    }
                    if (c.detail.isNotBlank()) Text(c.detail, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                }
            }
        }
    }
}

// ------------------------------------------------------------------- broker summary

@Composable
fun BrokerPanel(summary: BrokerSummary, modifier: Modifier = Modifier) {
    val maxVal = maxOf(
        summary.buyers.take(5).maxOfOrNull { it.value } ?: 0.0,
        summary.sellers.take(5).maxOfOrNull { it.value } ?: 0.0,
        1.0,
    )
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val acc = summary.accStrength()
            Pill(summary.mainLabel, if (acc > 0) Lumi.Green else if (summary.mainLabel.contains("dist", true)) Lumi.Red else Lumi.TextDim)
            Pill("${summary.buyerCount} pembeli", Lumi.Sky)
            Pill("${summary.sellerCount} penjual", Lumi.Purple)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Top 3 pembeli menguasai ${Fmt.share(summary.buyShare(3))} nilai beli · periode ${Market.shortDate(summary.from)}" +
                if (summary.to != summary.from) " – ${Market.shortDate(summary.to)}" else "",
            style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("NET BELI", style = MaterialTheme.typography.labelSmall, color = Lumi.Green)
                Spacer(Modifier.height(4.dp))
                summary.buyers.take(5).forEach { BrokerRow(it, maxVal, Lumi.Green) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("NET JUAL", style = MaterialTheme.typography.labelSmall, color = Lumi.Red)
                Spacer(Modifier.height(4.dp))
                summary.sellers.take(5).forEach { BrokerRow(it, maxVal, Lumi.Red) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Legend("Lokal", Lumi.brokerTypeColor("Lokal"))
            Legend("Asing", Lumi.brokerTypeColor("Asing"))
            Legend("Pemerintah", Lumi.brokerTypeColor("Pemerintah"))
        }
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
    }
}

@Composable
private fun BrokerRow(b: BrokerFlow, maxVal: Double, barColor: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Lumi.brokerTypeColor(b.type).copy(alpha = 0.2f))
                    .border(1.dp, Lumi.brokerTypeColor(b.type).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(b.code, style = NumberStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Lumi.brokerTypeColor(b.type))
            }
            Spacer(Modifier.width(6.dp))
            Text(Fmt.idr(b.value), style = NumberStyle.copy(fontSize = 12.sp), color = Lumi.Text, modifier = Modifier.weight(1f), maxLines = 1)
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Lumi.Border.copy(alpha = 0.5f))) {
            Box(
                Modifier.fillMaxWidth((b.value / maxVal).toFloat().coerceIn(0.02f, 1f)).height(4.dp)
                    .clip(RoundedCornerShape(50)).background(barColor.copy(alpha = 0.8f)),
            )
        }
        Text(
            "${Fmt.lots(b.lots)} lot · avg ${Fmt.price(b.avgPrice)}",
            style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------------------- win rate

data class WinStats(
    val total: Int,
    val wins: Int,
    val losses: Int,
    val open: Int,
    val cancelled: Int,
    val winRate: Double?,
    /** Sum of realised P/L % of every closed trade. */
    val totalPnl: Double?,
    /** Positions currently held (bought, not sold yet). */
    val holding: Int = 0,
    val tp: Int = 0,
    val sl: Int = 0,
)

fun winStats(list: List<Signal>): WinStats {
    val wins = list.count { it.countsAsWin }
    val losses = list.count { it.countsAsLoss }
    val open = list.count { it.isOpen && !it.countsAsWin }
    val cancelled = list.count { it.status == com.lumisignal.idx.data.SignalStatus.CANCELLED }
    val decided = wins + losses
    val closedPnl = list.filter { !it.isOpen && it.exitPrice != null && it.entryPrice != null }.mapNotNull { it.pnlPct }
    return WinStats(
        total = list.size,
        wins = wins,
        losses = losses,
        open = open,
        cancelled = cancelled,
        winRate = if (decided > 0) wins * 100.0 / decided else null,
        totalPnl = if (closedPnl.isNotEmpty()) closedPnl.sum() else null,
        holding = list.count { it.status == com.lumisignal.idx.data.SignalStatus.ACTIVE },
        tp = list.count { it.status == com.lumisignal.idx.data.SignalStatus.TP },
        sl = list.count { it.status == com.lumisignal.idx.data.SignalStatus.SL },
    )
}

@Composable
fun WinRateRing(rate: Double?, color: Color, modifier: Modifier = Modifier, sizeDp: Int = 86) {
    Box(modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(sizeDp.dp)) {
            val stroke = size.minDimension * 0.11f
            val tl = Offset(stroke / 2, stroke / 2)
            val sz = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawArc(Lumi.Red.copy(alpha = 0.35f), -90f, 360f, false, topLeft = tl, size = sz, style = Stroke(stroke))
            if (rate != null) {
                drawArc(color, -90f, (360f * rate / 100f).toFloat(), false, topLeft = tl, size = sz, style = Stroke(stroke, cap = StrokeCap.Round))
            } else {
                drawArc(Lumi.Border, -90f, 360f, false, topLeft = tl, size = sz, style = Stroke(stroke))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (rate == null) "–" else "${rate.toInt()}%",
                style = NumberStyle.copy(fontSize = (sizeDp * 0.22f).sp, fontWeight = FontWeight.Bold), color = Lumi.Text,
            )
            Text("winrate", style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
        }
    }
}

@Composable
fun WinStatsCard(title: String, subtitle: String, stats: WinStats, accent: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth(), accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WinRateRing(stats.winRate, accent)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Lumi.Text)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("${stats.wins} menang", Lumi.Green)
                    Pill("${stats.losses} kalah", Lumi.Red)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Total", "${stats.total}", Modifier.weight(1f))
            StatTile("Hold", "${stats.holding}", Modifier.weight(1f), Lumi.Sky)
            StatTile("TP", "${stats.tp}", Modifier.weight(1f), Lumi.Green)
            StatTile("SL", "${stats.sl}", Modifier.weight(1f), Lumi.Red)
        }
        Spacer(Modifier.height(8.dp))
        StatTile(
            "Akumulasi P/L", stats.totalPnl?.let { Fmt.pct(it) } ?: "–", Modifier.fillMaxWidth(),
            if ((stats.totalPnl ?: 0.0) >= 0) Lumi.Green else Lumi.Red,
        )
    }
}
