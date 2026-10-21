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
import androidx.compose.foundation.layout.heightIn
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
/** Round share button (opens the Android share sheet with a picture report). */
@Composable
fun ShareIcon(onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 34.dp) {
    Box(
        Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape).background(Lumi.Sky.copy(alpha = 0.12f))
            .border(1.dp, Lumi.Sky.copy(alpha = 0.35f), androidx.compose.foundation.shape.CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Share glyph: three nodes joined by two lines.
        androidx.compose.foundation.Canvas(Modifier.size(size * 0.46f)) {
            val w = this.size.width
            val r = w * 0.14f
            val a = androidx.compose.ui.geometry.Offset(w * 0.78f, w * 0.18f)
            val b = androidx.compose.ui.geometry.Offset(w * 0.22f, w * 0.5f)
            val c = androidx.compose.ui.geometry.Offset(w * 0.78f, w * 0.82f)
            drawLine(Lumi.Sky, b, a, strokeWidth = w * 0.09f)
            drawLine(Lumi.Sky, b, c, strokeWidth = w * 0.09f)
            listOf(a, b, c).forEach { drawCircle(Lumi.Sky, r, it) }
        }
    }
}

@Composable
fun SignalRow(sig: Signal, onOpen: () -> Unit) {
    val shareCtx = androidx.compose.ui.platform.LocalContext.current
    val plan = sig.eval.plan
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp, onClick = onOpen) {
        // Header: badge, ticker + strategy, status. Buttons get their own row so nothing is squeezed.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tag = sig.radar
            if (tag != null) RadarBadge(tag.icon, tag.color, tag.jenis, 36.dp) else StrategyBadge(sig.strategyId, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sig.symbol, style = NumberStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = Lumi.Text, maxLines = 1, softWrap = false)
                Text(
                    sig.radar?.name ?: Strategy.byId(sig.strategyId).title, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            // Strategy history: add to the portfolio from the top of the card.
            if (sig.source == com.lumisignal.idx.data.SOURCE_STRATEGY) {
                val items by com.lumisignal.idx.Graph.history.items.collectAsStateWithLifecycle()
                val inPorto = androidx.compose.runtime.remember(items, sig.id) {
                    items.any { it.source == com.lumisignal.idx.data.SOURCE_PORTO && it.linkedId == sig.id }
                }
                var dialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                if (dialog) PortoDialog(sig, onDismiss = { dialog = false }) { price ->
                    dialog = false
                    com.lumisignal.idx.engine.Scanner.addToPorto(sig, price)
                }
                val c = if (inPorto) Lumi.Green else Lumi.Blue
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = if (inPorto) 0.12f else 0.22f))
                        .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable { if (!inPorto) dialog = true else onOpen() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (inPorto) "✓ Sudah di Porto" else "+ Tambahkan ke Porto",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = if (inPorto) Lumi.Green else Lumi.Text, maxLines = 1,
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            ShareIcon({ com.lumisignal.idx.share.ShareCards.shareSignal(shareCtx, sig) }, 36.dp)
        }
        Spacer(Modifier.height(8.dp))
        TradeTimeline(
            signal = sig.refDate, buy = sig.entryDate, sell = sig.exitDate, hold = sig.holdDays,
            pending = when {
                sig.status == SignalStatus.CANCELLED -> "batal"
                sig.entryDate == null -> "menunggu"
                sig.isOpen -> "masih dipegang"
                else -> null
            },
        )
        val entry = sig.entryPrice ?: sig.customEntry
        if (plan != null && entry != null) {
            // Real entry and the TP/SL recalculated from it.
            val (tp, sl) = com.lumisignal.idx.share.ShareCards.levels(sig)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniLevel("ENTRY", Fmt.price(entry), Lumi.Blue, Modifier.weight(1f))
                MiniLevel("SL", Fmt.price(sl), Lumi.Red, Modifier.weight(1f))
                MiniLevel("TP", Fmt.price(tp), Lumi.Green, Modifier.weight(1f))
            }
        } else if (plan != null && sig.status == SignalStatus.WAITING) {
            Spacer(Modifier.height(8.dp))
            Text("👁 Dipantau · entry, TP & SL muncul di sini setelah terbeli", style = MaterialTheme.typography.labelMedium, color = Lumi.Sky)
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
            TimelineCell("SINYAL UTK", Market.shortDate(com.lumisignal.idx.engine.RadarPorto.nextSession(signal)), Lumi.Sky, Modifier.weight(1f))
            Text("›", color = Lumi.TextMuted, style = MaterialTheme.typography.titleMedium)
            TimelineCell("BELI", buy?.let { Market.shortDate(it) } ?: (if (pending == "batal") "tidak terbeli" else pending ?: "–"), Lumi.Blue, Modifier.weight(1f))
            Text("›", color = Lumi.TextMuted, style = MaterialTheme.typography.titleMedium)
            TimelineCell("JUAL", sell?.takeIf { buy != null }?.let { Market.shortDate(it) } ?: "–", Lumi.Green, Modifier.weight(1f))
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

/** "Bagikan laporan" button: share glyph + label. */
@Composable
fun ShareReportButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(Lumi.Sky.copy(alpha = 0.10f))
            .border(1.dp, Lumi.Sky.copy(alpha = 0.4f), RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShareIcon(onClick, 28.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Lumi.Sky)
    }
}

/** Dropdown field: label above, current value, list of options. */
@Composable
private fun PickField(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Lumi.TextMuted)
        Spacer(Modifier.height(4.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lumi.Bg.copy(alpha = 0.6f))
                    .border(1.dp, Lumi.Sky.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(options.getOrNull(selected) ?: "–", style = MaterialTheme.typography.bodyMedium, color = Lumi.Text, modifier = Modifier.weight(1f), maxLines = 1)
                Text("▾", color = Lumi.Sky)
            }
            androidx.compose.material3.DropdownMenu(open, { open = false }, Modifier.background(Lumi.Surface).heightIn(max = 360.dp)) {
                options.forEachIndexed { i, o ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(o, color = if (i == selected) Lumi.Sky else Lumi.Text) },
                        onClick = { onSelect(i); open = false },
                    )
                }
            }
        }
    }
}

/**
 * Picks a range of signal dates (shown as the session to buy) and shares the history in it.
 * [dates] are closing dates, oldest first; [count] gives the number of trades in a range.
 */
@Composable
fun RangeShareDialog(title: String, dates: List<String>, count: (String, String) -> Int, onDismiss: () -> Unit, onShare: (String, String) -> Unit) {
    var from by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var to by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(dates.lastIndex.coerceAtLeast(0)) }
    val labels = dates.map { Market.prettyDate(com.lumisignal.idx.engine.RadarPorto.nextSession(it), false) }
    val n = if (dates.isEmpty()) 0 else count(dates[minOf(from, to)], dates[maxOf(from, to)])
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lumi.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = Lumi.Text) },
        text = {
            Column {
                Text("Pilih tanggal sinyal (tanggal beli yang dituju). Laporan dibagikan sebagai gambar.", style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                Spacer(Modifier.height(12.dp))
                PickField("DARI TANGGAL", labels, from, { from = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                PickField("SAMPAI TANGGAL", labels, to, { to = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("$n transaksi akan dibagikan", style = MaterialTheme.typography.labelLarge, color = if (n > 0) Lumi.Green else Lumi.TextMuted)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                if (dates.isNotEmpty()) onShare(dates[minOf(from, to)], dates[maxOf(from, to)])
            }, enabled = n > 0) { Text("Bagikan", color = if (n > 0) Lumi.Sky else Lumi.TextMuted) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Batal", color = Lumi.TextDim) } },
    )
}

/** Picks one month of the backtest to share. [months] = (key, label), newest first. */
@Composable
fun MonthShareDialog(months: List<Pair<String, String>>, initial: String, busy: Boolean, onDismiss: () -> Unit, onShare: (String) -> Unit) {
    var sel by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(months.indexOfFirst { it.first == initial }.coerceAtLeast(0)) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lumi.Surface,
        title = { Text("Bagikan backtest", style = MaterialTheme.typography.titleLarge, color = Lumi.Text) },
        text = {
            Column {
                Text("Pilih bulan yang mau dibagikan. Laporan berisi jumlah transaksi, TP, SL, akumulasi P/L dan daftar sahamnya, sebagai gambar.",
                    style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
                Spacer(Modifier.height(12.dp))
                PickField("BULAN", months.map { it.second }, sel, { sel = it }, Modifier.fillMaxWidth())
                if (busy) {
                    Spacer(Modifier.height(10.dp))
                    Text("Menyiapkan gambar…", style = MaterialTheme.typography.labelLarge, color = Lumi.Sky)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { months.getOrNull(sel)?.let { onShare(it.first) } }, enabled = !busy && months.isNotEmpty()) {
                Text("Bagikan", color = Lumi.Sky)
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Batal", color = Lumi.TextDim) } },
    )
}
