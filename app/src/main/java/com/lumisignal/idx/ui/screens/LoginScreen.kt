package com.lumisignal.idx.ui.screens

import android.annotation.SuppressLint
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
    var showWeb by remember { mutableStateOf(false) }
    if (showWeb) {
        StockbitWebLogin(onBack = { showWeb = false })
    } else {
        WelcomeScreen(reason = reason, onConnect = { showWeb = true })
    }
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
                "Radar sinyal beli saham Indonesia dari jejak bandar & candle penutupan.",
                style = MaterialTheme.typography.bodyLarge, color = Lumi.TextDim, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(30.dp))
            if (reason != null) {
                GlassCard(Modifier.fillMaxWidth(), accent = Lumi.Amber) {
                    Text(reason, style = MaterialTheme.typography.bodyMedium, color = Lumi.Amber)
                }
                Spacer(Modifier.height(14.dp))
            }
            Feature(Icons.Filled.Search, "5 strategi screening", "Dari closing kuat sampai gorengan bangun — tiap sinyal lengkap dengan entry, SL & TP.")
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

/** Hooks fetch/XHR inside stockbit.com to observe the bearer header and the login response. */
private const val HOOK_JS = """
(function(){
  if (window.__lumiHooked) return; window.__lumiHooked = true;
  function bearer(v){ try { if (v && String(v).indexOf('Bearer ') === 0) LumiBridge.onBearer(String(v).substring(7)); } catch(e){} }
  function sniff(t){ try { if (t && t.indexOf('token') >= 0) LumiBridge.onJson(String(t)); } catch(e){} }
  try {
    var of = window.fetch;
    if (of) {
      window.fetch = function(input, init){
        try {
          var h = init && init.headers;
          if (h) { bearer(typeof h.get === 'function' ? (h.get('Authorization') || h.get('authorization')) : (h['Authorization'] || h['authorization'])); }
          if (input && typeof input === 'object' && input.headers && typeof input.headers.get === 'function') bearer(input.headers.get('Authorization'));
        } catch(e){}
        var p = of.apply(this, arguments);
        try {
          var url = (typeof input === 'string') ? input : ((input && input.url) || '');
          if (/login|refresh|auth/i.test(url)) { p.then(function(r){ try { r.clone().text().then(sniff); } catch(e){} }); }
        } catch(e){}
        return p;
      };
    }
  } catch(e){}
  try {
    var oo = XMLHttpRequest.prototype.open, os = XMLHttpRequest.prototype.setRequestHeader, ose = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function(m, u){ this.__lumiUrl = u; return oo.apply(this, arguments); };
    XMLHttpRequest.prototype.setRequestHeader = function(k, v){ try { if (/authorization/i.test(k)) bearer(v); } catch(e){} return os.apply(this, arguments); };
    XMLHttpRequest.prototype.send = function(){
      var x = this;
      try { if (/login|refresh|auth/i.test(x.__lumiUrl || '')) x.addEventListener('load', function(){ try { sniff(x.responseText); } catch(e){} }); } catch(e){}
      return ose.apply(this, arguments);
    };
  } catch(e){}
})();
"""

private class LumiBridge(private val onToken: (String?, String?) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onBearer(token: String?) {
        if (TokenParser.isJwt(token)) main.post { onToken(token, null) }
    }

    @JavascriptInterface
    fun onJson(text: String?) {
        val t = TokenParser.fromJsonText(text) ?: return
        main.post { onToken(t.access, t.refresh) }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun StockbitWebLogin(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Silakan login dengan username & password Stockbit kamu.") }
    var verifying by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val tried = remember { mutableSetOf<String>() }
    var pendingRefresh by remember { mutableStateOf<String?>(null) }
    var webRef by remember { mutableStateOf<WebView?>(null) }

    fun consider(access: String?, refresh: String?) {
        if (!refresh.isNullOrBlank()) pendingRefresh = refresh
        if (access.isNullOrBlank() || access in tried || verifying) return
        tried += access
        verifying = true
        status = "Token ditemukan, memverifikasi ke Stockbit…"
        scope.launch {
            val ok = try {
                Graph.api.validate(access)
            } catch (e: Exception) {
                false
            }
            if (ok) {
                status = "Berhasil terhubung!"
                Graph.session.save(access, pendingRefresh, DESKTOP_UA)
            } else {
                status = "Belum terhubung — selesaikan login di halaman Stockbit."
                verifying = false
            }
        }
    }

    // Poll Stockbit's own credential cookie: the most reliable source of the session token.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            val cookie = try {
                CookieManager.getInstance().getCookie("https://stockbit.com")
            } catch (e: Exception) {
                null
            }
            val t = TokenParser.fromCookieHeader(cookie)
            if (t != null) consider(t.access, t.refresh)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webRef?.let {
                it.stopLoading()
                it.destroy()
            }
        }
    }

    LumiBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Lumi.SurfaceHi).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Lumi.Text) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Login Stockbit", style = MaterialTheme.typography.titleMedium, color = Lumi.Text)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = if (verifying) Lumi.Sky else Lumi.TextDim)
                }
                if (verifying || loading) {
                    CircularProgressIndicator(color = Lumi.Sky, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Sparkle(16.dp, Lumi.Sky)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
                    .background(Lumi.Amber.copy(alpha = 0.1f)).padding(10.dp),
                horizontalArrangement = Arrangement.Start,
            ) {
                Text(
                    "Tips: gunakan username/email + password. Tombol login Google/Facebook di web Stockbit tidak berfungsi. Halaman bisa di-zoom dengan dua jari.",
                    style = MaterialTheme.typography.bodySmall, color = Lumi.Amber,
                )
            }
            Spacer(Modifier.height(8.dp))
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webRef = this
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.userAgentString = DESKTOP_UA
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        addJavascriptInterface(LumiBridge { a, r -> consider(a, r) }, "LumiBridge")
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                            try {
                                WebViewCompat.addDocumentStartJavaScript(this, HOOK_JS, setOf("https://stockbit.com", "https://*.stockbit.com"))
                            } catch (e: Exception) {
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                view?.evaluateJavascript(HOOK_JS, null)
                                CookieManager.getInstance().flush()
                            }

                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val auth = request?.requestHeaders?.entries
                                    ?.firstOrNull { it.key.equals("Authorization", true) }?.value
                                if (auth != null && auth.startsWith("Bearer ")) {
                                    val token = auth.removePrefix("Bearer ").trim()
                                    if (TokenParser.isJwt(token)) {
                                        Handler(Looper.getMainLooper()).post { consider(token, null) }
                                    }
                                }
                                return null
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val host = request?.url?.host ?: return false
                                // Keep the login inside the WebView; never hand off to the Stockbit app.
                                return !(host.endsWith("stockbit.com") || host.endsWith("google.com") || host.endsWith("recaptcha.net") || host.endsWith("gstatic.com"))
                            }
                        }
                        loadUrl("https://stockbit.com/login")
                    }
                },
            )
        }
    }
}
