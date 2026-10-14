package com.lumisignal.idx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumisignal.idx.data.Signal
import com.lumisignal.idx.data.SignalStatus
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.Strategy
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.StatusBadge
import com.lumisignal.idx.ui.components.StrategyBadge
import com.lumisignal.idx.ui.components.RadarBadge
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Lumi.SurfaceHi).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Lumi.Text) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Lumi.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
fun FlowPills(items: List<String>, color: Color) {
    // Simple two-row wrap: Compose's FlowRow is still experimental, so split by count.
    val rows = items.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { Pill(it, color) }
            }
        }
    }
}

@Composable
fun MiniLevel(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(value, style = NumberStyle.copy(fontSize = 14.sp), color = Lumi.Text, maxLines = 1)
    }
}

/** Compact row for the history lists. */
@Composable
fun SignalRow(sig: Signal, onOpen: () -> Unit) {
    val plan = sig.eval.plan
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tag = sig.radar
            if (tag != null) RadarBadge(tag.icon, tag.color, tag.jenis, 36.dp) else StrategyBadge(sig.strategyId, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sig.symbol, style = NumberStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    sig.radar?.name ?: Strategy.byId(sig.strategyId).title, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusBadge(sig.status)
                val pnl = sig.pnlPct
                if (pnl != null && sig.status != SignalStatus.CANCELLED) {
                    Spacer(Modifier.height(4.dp))
                    Text(Fmt.pct(pnl), style = NumberStyle.copy(fontSize = 14.sp), color = if (pnl >= 0) Lumi.Green else Lumi.Red)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        TradeTimeline(
            signal = sig.refDate, buy = sig.entryDate, sell = sig.exitDate, hold = sig.holdDays,
            pending = when {
                sig.status == SignalStatus.CANCELLED -> "batal"
                sig.entryDate == null -> "menunggu"
                sig.isOpen -> "masih dipegang"
                else -> null
            },
        )
        if (plan != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniLevel("ENTRY", Fmt.price(sig.entryPrice ?: plan.entryHigh), Lumi.Blue, Modifier.weight(1f))
                MiniLevel("SL", Fmt.price(plan.stopLoss), Lumi.Red, Modifier.weight(1f))
                MiniLevel("TP", Fmt.price(plan.tp), Lumi.Green, Modifier.weight(1f))
            }
        }
        if (sig.statusNote.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(sig.statusNote, style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Asks for an optional actual buy price, then adds the signal to the portfolio history. */
@Composable
fun PortoDialog(sig: Signal, onDismiss: () -> Unit, onConfirm: (Double?) -> Unit) {
    var text by remember { mutableStateOf("") }
    val price = text.replace(".", "").replace(",", "").toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lumi.Surface,
        title = { Text("Masukkan ${sig.symbol} ke Porto", style = MaterialTheme.typography.titleLarge, color = Lumi.Text) },
        text = {
            Column {
                Text(
                    "Posisi porto dipantau terpisah sehingga winrate porto kamu terlihat jelas. Isi harga beli jika kamu sudah membeli; kosongkan untuk mengikuti area beli sinyal (${Fmt.price(sig.eval.plan?.entryLow)}–${Fmt.price(sig.eval.plan?.entryHigh)}).",
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(price?.takeIf { it > 0 }) }) { Text("Masukkan Porto", color = Lumi.Green) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = Lumi.TextDim) } },
    )
}


/**
 * Signal → buy → sell dates of one trade with the number of trading sessions held (entry day included).
 * [pending] replaces a missing buy/sell date ("menunggu", "masih dipegang", "batal").
 */
@Composable
fun TradeTimeline(signal: String, buy: String?, sell: String?, hold: Int?, pending: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Lumi.Bg.copy(alpha = 0.5f))
            .border(1.dp, Lumi.Border.copy(alpha = 0.7f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimelineCell("SINYAL", Market.shortDate(signal), Lumi.Sky, Modifier.weight(1f))
            Text("›", color = Lumi.TextMuted, style = MaterialTheme.typography.titleMedium)
            TimelineCell("BELI", buy?.let { Market.shortDate(it) } ?: (if (pending == "batal") "tidak terbeli" else pending ?: "–"), Lumi.Blue, Modifier.weight(1f))
            Text("›", color = Lumi.TextMuted, style = MaterialTheme.typography.titleMedium)
            TimelineCell("JUAL", sell?.let { Market.shortDate(it) } ?: (if (buy != null) pending ?: "–" else "–"), Lumi.Green, Modifier.weight(1f))
            Column(Modifier.width(62.dp), horizontalAlignment = Alignment.End) {
                Text("HOLD", style = MaterialTheme.typography.labelSmall, color = Lumi.Amber)
                Text(hold?.let { "$it hari" } ?: "–", style = NumberStyle.copy(fontSize = 13.sp), color = Lumi.Text)
            }
        }
    }
}

@Composable
private fun TimelineCell(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(value, style = MaterialTheme.typography.labelMedium, color = Lumi.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
