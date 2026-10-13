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
import androidx.compose.material.icons.filled.Add
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
import com.lumisignal.idx.ui.components.GhostButton
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.ScoreRing
import com.lumisignal.idx.ui.components.StatusBadge
import com.lumisignal.idx.ui.components.StrategyBadge
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

/** A top-N screening result. */
@Composable
fun ResultCard(rank: Int, sig: Signal, inPorto: Boolean, onOpen: () -> Unit, onPorto: () -> Unit) {
    val accent = Lumi.strategyColor(sig.strategyId)
    val plan = sig.eval.plan
    GlassCard(Modifier.fillMaxWidth(), accent = accent, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#$rank", style = NumberStyle.copy(fontSize = 15.sp), color = accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sig.symbol, style = NumberStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Fmt.pct(sig.changePct), style = NumberStyle.copy(fontSize = 14.sp),
                        color = if (sig.changePct >= 0) Lumi.Green else Lumi.Red,
                    )
                }
                if (sig.name.isNotBlank()) {
                    Text(sig.name, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            ScoreRing(sig.eval.score, accent, 52.dp)
        }
        Spacer(Modifier.height(10.dp))
        FlowPills(sig.eval.highlights.take(4), accent)
        if (plan != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniLevel("BELI", if (plan.entryLow == plan.entryHigh) Fmt.price(plan.entryHigh) else "${Fmt.price(plan.entryLow)}–${Fmt.price(plan.entryHigh)}", Lumi.Blue, Modifier.weight(1.3f))
                MiniLevel("SL", Fmt.price(plan.stopLoss), Lumi.Red, Modifier.weight(1f))
                MiniLevel("TP1", Fmt.price(plan.tp1), Lumi.Green, Modifier.weight(1f))
            }
        }
        if (sig.eval.lowLiquidity) {
            Spacer(Modifier.height(8.dp))
            Pill("⚠ Likuiditas rendah", Lumi.Amber)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Lihat analisa", onOpen, Modifier.weight(1f))
            if (inPorto) {
                GhostButton("Di Porto", {}, Modifier.weight(1f), icon = Icons.Filled.CheckCircle, color = Lumi.Green)
            } else {
                GhostButton("Masukkan Porto", onPorto, Modifier.weight(1f), icon = Icons.Filled.Add, color = Lumi.Green)
            }
        }
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
            StrategyBadge(sig.strategyId, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sig.symbol, style = NumberStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
                    Spacer(Modifier.width(8.dp))
                    Text(Market.shortDate(sig.refDate), style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted)
                }
                Text(
                    Strategy.byId(sig.strategyId).title, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim,
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
        if (plan != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniLevel("ENTRY", Fmt.price(sig.entryPrice ?: plan.entryHigh), Lumi.Blue, Modifier.weight(1f))
                MiniLevel("SL", Fmt.price(plan.stopLoss), Lumi.Red, Modifier.weight(1f))
                MiniLevel("TP1", Fmt.price(plan.tp1), Lumi.Green, Modifier.weight(1f))
                MiniLevel("TP2", Fmt.price(plan.tp2), Lumi.Green, Modifier.weight(1f))
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
