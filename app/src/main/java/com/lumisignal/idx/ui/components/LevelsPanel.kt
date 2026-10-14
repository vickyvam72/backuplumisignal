package com.lumisignal.idx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumisignal.idx.engine.Fmt
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.SrLevel
import com.lumisignal.idx.engine.SupportResistance
import com.lumisignal.idx.ui.theme.Lumi
import com.lumisignal.idx.ui.theme.NumberStyle

val ResistColor = Color(0xFFFFB86B)
val SupportColor = Color(0xFFB98BFF)

/** Support/resistance lines for [CandleChart]. */
fun srChartLevels(sr: SupportResistance): List<ChartLevel> =
    sr.resistances.mapIndexed { i, l -> ChartLevel(l.price, ResistColor, "R${i + 1}") } +
        sr.supports.mapIndexed { i, l -> ChartLevel(l.price, SupportColor, "S${i + 1}") }

/** Price ladder: R2, R1, last close, S1, S2 with the distance from the close. */
@Composable
fun LevelsPanel(sr: SupportResistance, close: Double, modifier: Modifier = Modifier) {
    Column(modifier) {
        sr.resistances.withIndex().reversed().forEach { (i, l) -> LevelRow("RESISTANCE ${i + 1}", l, close, ResistColor) }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp))
                .background(Lumi.Blue.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("HARGA TERAKHIR", style = MaterialTheme.typography.labelSmall, color = Lumi.Sky, modifier = Modifier.weight(1f))
            Text(Fmt.price(close), style = NumberStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
        }
        sr.supports.forEachIndexed { i, l -> LevelRow("SUPPORT ${i + 1}", l, close, SupportColor) }
        Spacer(Modifier.height(6.dp))
        Text(
            "Resistance = area harga yang sering menahan kenaikan (rawan memantul turun). Support = area yang sering menahan penurunan (berpeluang memantul naik). Kalau tembus, level berikutnya jadi patokan.",
            style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted,
        )
    }
}

@Composable
private fun LevelRow(label: String, l: SrLevel, close: Double, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color)
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(l.note, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.price(l.price), style = NumberStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = Lumi.Text)
            Text(Fmt.pct(Market.pct(l.price, close)), style = NumberStyle.copy(fontSize = 12.sp), color = Lumi.TextDim)
        }
    }
}
