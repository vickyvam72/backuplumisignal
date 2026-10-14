package com.lumisignal.idx.ui.screens

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.lumisignal.idx.LoginActivity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.lumisignal.idx.Graph
import com.lumisignal.idx.data.DESKTOP_UA
import com.lumisignal.idx.data.TokenParser
import com.lumisignal.idx.ui.components.GlassCard
import com.lumisignal.idx.ui.components.GradientButton
import com.lumisignal.idx.ui.components.LumiBackground
import com.lumisignal.idx.ui.components.LumiLogo
import com.lumisignal.idx.ui.components.Sparkle
import com.lumisignal.idx.ui.theme.Lumi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginFlow(reason: String? = null) {
    val ctx = LocalContext.current
    WelcomeScreen(reason = reason, onConnect = {
        ctx.startActivity(Intent(ctx, LoginActivity::class.java))
    })
}

@Composable
private fun WelcomeScreen(reason: String?, onConnect: () -> Unit) {
    LumiBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            LumiLogo(104.dp)
            Spacer(Modifier.height(22.dp))
            Text("Lumi Signal IDX", style = MaterialTheme.typography.headlineMedium, color = Lumi.Text)
            Spacer(Modifier.height(8.dp))
            Text(
                "Radar sinyal beli saham Indonesia untuk sesi besok, dari closing hari ini.",
                style = MaterialTheme.typography.bodyLarge, color = Lumi.TextDim, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(30.dp))
            if (reason != null) {
                GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Amber) {
                    Text(reason, style = MaterialTheme.typography.bodyMedium, color = Lumi.Amber)
                }
                Spacer(Modifier.height(14.dp))
            }
            Feature(Icons.Filled.Search, "Radar Besok", "Sekali screening, semua strategi langsung tampil — tiap sinyal lengkap dengan batas beli, TP & SL.")
            Feature(Icons.Filled.Star, "Bukti yang transparan", "Lihat broker mana yang akumulasi dan chart teknikal di balik setiap sinyal.")
            Feature(Icons.Filled.Notifications, "Pemantauan otomatis", "Notifikasi saat harga menyentuh entry, take profit, atau stop loss.")
            Spacer(Modifier.height(26.dp))
            GradientButton("Hubungkan akun Stockbit", onConnect, Modifier.fillMaxWidth(), icon = Icons.Filled.Lock)
            Spacer(Modifier.height(12.dp))
            Text(
                "Login dilakukan langsung di halaman resmi stockbit.com. Lumi hanya membaca data (read-only) dan tidak pernah bisa membuat order.",
                style = MaterialTheme.typography.bodySmall, color = Lumi.TextMuted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Lumi.Blue.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Lumi.Sky, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Lumi.Text)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Lumi.TextDim)
        }
    }
}

