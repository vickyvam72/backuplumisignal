package com.lumisignal.idx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisignal.idx.Graph
import com.lumisignal.idx.engine.Market
import com.lumisignal.idx.engine.RadarPorto
import com.lumisignal.idx.engine.RadarRepo
import com.lumisignal.idx.ui.components.GradientButton
import com.lumisignal.idx.ui.components.LumiLogo
import com.lumisignal.idx.ui.components.Pill
import com.lumisignal.idx.ui.components.RadarScope
import com.lumisignal.idx.ui.theme.Lumi

/** Clean start screen: the radar and one button. */
@Composable
fun HomeScreen(onScreening: () -> Unit) {
    val day by RadarRepo.day.collectAsStateWithLifecycle()
    val name = remember { Graph.session.displayName }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
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
        Column(
            Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RadarScope(260.dp, periodMs = 5200, blips = 9)
            Spacer(Modifier.height(28.dp))
            Text("RADAR BESOK", style = MaterialTheme.typography.labelLarge, color = Lumi.Sky)
            Spacer(Modifier.height(6.dp))
            Text(
                "Temukan saham untuk dibeli\ndi sesi berikutnya",
                style = MaterialTheme.typography.headlineSmall, color = Lumi.Text, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                day?.let { "Data terbaru: closing ${Market.prettyDate(it.date, false)} · beli ${Market.prettyDate(RadarPorto.nextSession(it.date), false)}" }
                    ?: "Dihitung dari harga closing semua saham IDX",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))
            GradientButton("Mulai Screening", onScreening, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow)
        }
        Text(
            "Alat bantu screening, bukan rekomendasi investasi. Selalu gunakan stop loss dan atur ukuran posisi.",
            style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        )
    }
}
