package com.lumisignal.idx

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.lumisignal.idx.data.DESKTOP_UA
import com.lumisignal.idx.data.TokenParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Stockbit login in a plain View-based activity. Keeping the WebView out of Compose avoids the
 * keyboard-connection resets that made typed text appear reversed.
 */
class LoginActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var statusText: TextView
    private lateinit var spinner: ProgressBar
    private val main = Handler(Looper.getMainLooper())
    private val tried = mutableSetOf<String>()
    private var pendingRefresh: String? = null
    private var verifying = false
    private var pollJob: Job? = null

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }

        // ---- top bar
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val back = TextView(this).apply {
            text = "←"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(SURFACE)
            }
            setOnClickListener { finish() }
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        titles.addView(TextView(this).apply {
            text = "Login Stockbit"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        })
        statusText = TextView(this).apply {
            text = "Silakan login dengan username/email & password Stockbit."
            setTextColor(DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        titles.addView(statusText)
        bar.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        spinner = ProgressBar(this).apply { isIndeterminate = true }
        bar.addView(spinner, LinearLayout.LayoutParams(dp(22), dp(22)))
        root.addView(bar)

        // ---- tip
        val tip = TextView(this).apply {
            text = "Tips: pakai username/email + password. Login Google/Facebook di web Stockbit tidak berfungsi. Halaman bisa di-zoom dengan dua jari."
            setTextColor(AMBER)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0x1AFFC14D)
            }
        }
        root.addView(tip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(16), 0, dp(16), dp(8))
        })

        // ---- web view
        web = WebView(this)
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        // Android 15 draws edge-to-edge: pad for the status bar, navigation bar and keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        setupWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        // Poll Stockbit's own credential cookie — the most reliable source of the session token.
        pollJob = lifecycleScope.launch {
            while (true) {
                delay(1500)
                val cookie = try {
                    CookieManager.getInstance().getCookie("https://stockbit.com")
                } catch (e: Exception) {
                    null
                }
                TokenParser.fromCookieHeader(cookie)?.let { consider(it.access, it.refresh) }
            }
        }

        if (savedInstanceState == null) web.loadUrl("https://stockbit.com/login") else web.restoreState(savedInstanceState)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = DESKTOP_UA
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = true
        }
        web.addJavascriptInterface(Bridge(), "LumiBridge")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(web, HOOK_JS, setOf("https://stockbit.com", "https://*.stockbit.com"))
            } catch (e: Exception) {
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                spinner.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!verifying) spinner.visibility = View.INVISIBLE
                view?.evaluateJavascript(HOOK_JS, null)
                CookieManager.getInstance().flush()
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val auth = request?.requestHeaders?.entries?.firstOrNull { it.key.equals("Authorization", true) }?.value
                if (auth != null && auth.startsWith("Bearer ")) {
                    val token = auth.removePrefix("Bearer ").trim()
                    if (TokenParser.isJwt(token)) main.post { consider(token, null) }
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val host = request?.url?.host ?: return false
                // Keep the login inside this page; never hand off to the Stockbit app.
                return !(host.endsWith("stockbit.com") || host.endsWith("google.com") ||
                    host.endsWith("recaptcha.net") || host.endsWith("gstatic.com"))
            }
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onBearer(token: String?) {
            if (TokenParser.isJwt(token)) main.post { consider(token, null) }
        }

        @JavascriptInterface
        fun onJson(text: String?) {
            val t = TokenParser.fromJsonText(text) ?: return
            main.post { consider(t.access, t.refresh) }
        }
    }

    private fun consider(access: String?, refresh: String?) {
        if (!refresh.isNullOrBlank()) pendingRefresh = refresh
        if (access.isNullOrBlank() || access in tried || verifying || isFinishing) return
        tried += access
        verifying = true
        spinner.visibility = View.VISIBLE
        statusText.text = "Token ditemukan, memverifikasi ke Stockbit…"
        statusText.setTextColor(SKY)
        lifecycleScope.launch {
            val ok = try {
                Graph.api.validate(access)
            } catch (e: Exception) {
                false
            }
            if (ok) {
                statusText.text = "Berhasil terhubung!"
                Graph.session.save(access, pendingRefresh, DESKTOP_UA)
                pollJob?.cancel()
                finish()
            } else {
                statusText.text = "Belum terhubung — selesaikan login di halaman Stockbit."
                statusText.setTextColor(DIM)
                spinner.visibility = View.INVISIBLE
                verifying = false
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        pollJob?.cancel()
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private val BG = 0xFF070B1A.toInt()
        private val SURFACE = 0xFF16214A.toInt()
        private val DIM = 0xFF9EABD0.toInt()
        private val SKY = 0xFF9EDCFF.toInt()
        private val AMBER = 0xFFFFC14D.toInt()

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
    }
}
