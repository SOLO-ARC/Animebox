package com.lagradost.cloudstream3.ui.animebox.extractors

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 1:1 port of Anilili's PaheBridge (F4.C0437na, F4.O6, F4.E1, F4.U9).
 *
 * Uses a main-thread singleton hidden WebView with third-party cookies enabled
 * to pass DDoS-Guard / Cloudflare challenges on https://animepahe.pw and https://kwik.cx / kwik.si,
 * execute in-page XHR fetch() calls on animepahe.pw, and extract Kwik embed HTML.
 */
object PaheWebViewBridge {

    private const val BASE_URL = "https://animepahe.pw"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private val mutex = Mutex()
    private val reqCounter = AtomicLong(0L)

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var webView: WebView? = null

    data class BridgeResponse(
        val status: Int,
        val body: String
    )

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun getOrCreateWebView(context: Context): WebView = withContext(Dispatchers.Main) {
        webView?.let { return@withContext it }
        val appCtx = context.applicationContext
        val wv = WebView(appCtx)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = true
            userAgentString = USER_AGENT
        }
        runCatching {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(wv, true)
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }
        webView = wv
        wv
    }

    private suspend fun evalJs(wv: WebView, script: String): String = withContext(Dispatchers.Main) {
        suspendCoroutine { cont ->
            try {
                wv.evaluateJavascript(script) { raw ->
                    cont.resume(unescapeEvaluateJsResult(raw))
                }
            } catch (_: Throwable) {
                cont.resume("")
            }
        }
    }

    /**
     * 1:1 port of F4.C0437na.h(String) which unescapes WebView.evaluateJavascript JSON string output.
     */
    private fun unescapeEvaluateJsResult(raw: String?): String {
        if (raw == null || raw == "null") return ""
        val s = if (raw.length >= 2 && raw.first() == '"' && raw.last() == '"') {
            raw.substring(1, raw.length - 1)
        } else {
            raw
        }
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    'u' -> {
                        if (i + 5 < s.length) {
                            val hex = s.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                                continue
                            }
                        }
                        sb.append(c)
                        i++
                    }
                    else -> {
                        sb.append(next)
                        i += 2
                    }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private const val CLEARANCE_CHECK_JS =
        "(location.origin === 'https://animepahe.pw' && document.readyState === 'complete' && !/Just a moment|DDoS-Guard/i.test(document.title)) ? location.origin : ''"

    private suspend fun ensureClearance(context: Context, forceReload: Boolean): Boolean {
        val wv = getOrCreateWebView(context)
        if (!forceReload) {
            val currentOrigin = evalJs(wv, CLEARANCE_CHECK_JS)
            if (currentOrigin == BASE_URL) return true
        }
        withContext(Dispatchers.Main) {
            wv.loadUrl("$BASE_URL/")
        }
        val deadline = SystemClock.elapsedRealtime() + 25_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(400L)
            val origin = evalJs(wv, CLEARANCE_CHECK_JS)
            if (origin == BASE_URL) {
                runCatching { CookieManager.getInstance().flush() }
                return true
            }
        }
        return false
    }

    private suspend fun executeFetchOnPage(wv: WebView, path: String): BridgeResponse? {
        val key = "r${reqCounter.incrementAndGet()}"
        val escapedPath = path.replace("\\", "\\\\").replace("'", "\\'")
        val startJs = """
            (function(){
              var k = '$key';
              window.__pahe = window.__pahe || {};
              delete window.__pahe[k];
              fetch('$escapedPath', { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
                .then(function(r) { return r.text().then(function(t) { window.__pahe[k] = JSON.stringify({ ok: r.ok, status: r.status, body: t }); }); })
                .catch(function(e) { window.__pahe[k] = JSON.stringify({ ok: false, status: 0, error: String(e) }); });
            })();
        """.trimIndent()
        val pollJs = "(window.__pahe && window.__pahe['$key']) || ''"
        val cleanupJs = "if (window.__pahe) delete window.__pahe['$key'];"

        evalJs(wv, startJs)
        try {
            val deadline = SystemClock.elapsedRealtime() + 15_000L
            while (SystemClock.elapsedRealtime() < deadline) {
                delay(150L)
                val resStr = evalJs(wv, pollJs)
                if (resStr.isNotEmpty()) {
                    val json = runCatching { JSONObject(resStr) }.getOrNull() ?: return null
                    val status = json.optInt("status", 0)
                    if (status == 0) return null
                    val body = json.optString("body", "")
                    return BridgeResponse(status = status, body = body)
                }
            }
            return null
        } finally {
            runCatching { evalJs(wv, cleanupJs) }
        }
    }

    /**
     * 1:1 port of F4.C0437na.g(path) — executes an in-page fetch on https://animepahe.pw.
     */
    suspend fun get(context: Context, path: String): BridgeResponse? = mutex.withLock {
        val wv = getOrCreateWebView(context)
        if (!ensureClearance(context, forceReload = false)) return@withLock null

        val firstTry = executeFetchOnPage(wv, path)
        val isApi = path.startsWith("/api")
        val looksValid = firstTry != null &&
            firstTry.status in 200..299 &&
            (!isApi || firstTry.body.trimStart().startsWith("{"))

        if (looksValid) return@withLock firstTry

        // Retry with forced clearance reload if challenged (403 / 503 / HTML challenge)
        if (!ensureClearance(context, forceReload = true)) return@withLock firstTry
        executeFetchOnPage(wv, path)
    }

    /**
     * 1:1 port of F4.C0437na.f(url) — loads the Kwik embed URL with Referer: https://animepahe.pw/
     * inside the WebView and returns its outerHTML once the packed eval script is present.
     */
    suspend fun fetchKwikHtml(
        context: Context,
        kwikUrl: String,
        referer: String = "$BASE_URL/"
    ): String? = mutex.withLock {
        val host = runCatching { URI(kwikUrl).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@withLock null
        val wv = getOrCreateWebView(context)
        withContext(Dispatchers.Main) {
            wv.loadUrl(kwikUrl, mapOf("Referer" to referer))
        }
        val checkJs = "(document.readyState === 'complete' && location.host === '$host') ? document.documentElement.outerHTML : ''"
        val deadline = SystemClock.elapsedRealtime() + 20_000L
        var lastHtml: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(250L)
            val html = evalJs(wv, checkJs)
            if (html.isNotEmpty()) {
                lastHtml = html
                if (AnililiHttpClient.isPackedJs(html) || html.contains(".m3u8")) {
                    return@withLock html
                }
            }
        }
        lastHtml
    }
}
