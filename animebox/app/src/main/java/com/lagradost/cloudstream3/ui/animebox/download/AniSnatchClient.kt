package com.lagradost.cloudstream3.ui.animebox.download
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.ui.animebox.api.AniZipClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

data class DownloadSource(
    val title: String,
    val quality: String,
    val size: String,
    val sourceUrl: String,
    val provider: String = "AniSnatch"
)

object AniSnatchClient {
    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private const val BASE_URL = "https://dl.anisnatch.top"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /**
     * Resolves AniList ID from Kitsu ID if needed, then queries AniSnatch for available download options.
     */
    suspend fun getDownloadSources(rawId: Int, episodeNum: Int): List<DownloadSource> = withContext(Dispatchers.IO) {
        val list = mutableListOf<DownloadSource>()
        try {
            // If rawId is a Kitsu ID, resolve the AniList ID
            val anilistId = AniZipClient.resolveAnilistIdFromKitsu(rawId) ?: rawId
            val targetUrl = "$BASE_URL/anime/$anilistId/$episodeNum"

            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "$BASE_URL/")
                .build()

            val html = client.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.string() ?: "" else ""
            }

            if (html.isBlank()) return@withContext emptyList()

            // Parse HTML / Markdown links like:
            // <a href="/pahe/dCznE" ...># SubsPlease · 1080p (179MB)</a>
            // or [# SubsPlease · 1080p (179MB)](https://dl.anisnatch.top/pahe/dCznE)
            val linkRegex = Pattern.compile(
                """<a[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)</a>|\[([^\]]+)\]\(([^\)]+)\)""",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = linkRegex.matcher(html)

            while (matcher.find()) {
                val rawUrl = (matcher.group(1) ?: matcher.group(4) ?: "").trim()
                var rawLabel = (matcher.group(2) ?: matcher.group(3) ?: "").trim()

                // Strip nested HTML tags inside label if any (e.g. <span> or <i>)
                rawLabel = rawLabel.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()

                if (rawUrl.isNotBlank() && rawLabel.isNotBlank()) {
                    val fullUrl = if (rawUrl.startsWith("http")) rawUrl else "$BASE_URL$rawUrl"

                    // Extract quality (1080p, 720p, 480p, 360p)
                    val quality = when {
                        rawLabel.contains("1080p", ignoreCase = true) -> "1080p"
                        rawLabel.contains("720p", ignoreCase = true) -> "720p"
                        rawLabel.contains("480p", ignoreCase = true) -> "480p"
                        rawLabel.contains("360p", ignoreCase = true) -> "360p"
                        rawLabel.contains("Filemoon", ignoreCase = true) -> "1080p HD"
                        else -> "720p"
                    }

                    // Extract size like "(179MB)" or "(1.2GB)"
                    val sizeMatch = Regex("\\(([0-9.]+\\s*[MG]B)\\)", RegexOption.IGNORE_CASE).find(rawLabel)
                    val size = sizeMatch?.groupValues?.getOrNull(1) ?: if (rawLabel.contains("Filemoon", ignoreCase = true)) "Fast Server" else "Direct Video"

                    val provider = when {
                        rawLabel.contains("SubsPlease", ignoreCase = true) -> "SubsPlease"
                        rawLabel.contains("Filemoon", ignoreCase = true) -> "Filemoon"
                        else -> "AniSnatch"
                    }

                    // Format clean title
                    val displayTitle = when {
                        rawLabel.contains("SubsPlease", ignoreCase = true) -> "SubsPlease • $quality ($size)"
                        rawLabel.contains("Filemoon", ignoreCase = true) -> "Filemoon Mirror • 1080p"
                        else -> rawLabel
                    }

                    list.add(
                        DownloadSource(
                            title = displayTitle,
                            quality = quality,
                            size = size,
                            sourceUrl = fullUrl,
                            provider = provider
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    /**
     * Follows redirect URLs and resolves direct MP4 link (such as https://vault-99.owocdn.top/mp4/...)
     * from AniSnatch / Kwik / Filemoon.
     */
    suspend fun resolveDownloadUrl(context: Context? = null, sourceUrl: String): String = withContext(Dispatchers.IO) {
        try {
            var currentUrl = sourceUrl

            // 1. Follow AniSnatch /pahe/... redirect
            if (currentUrl.contains("anisnatch.top")) {
                val req = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "$BASE_URL/")
                    .build()

                client.newCall(req).execute().use { res ->
                    val location = res.header("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = location
                    }
                }
            }

            // If it's already a direct mp4/m3u8, return immediately
            if (currentUrl.endsWith(".mp4") || currentUrl.contains("/mp4/") || currentUrl.contains(".m3u8")) {
                return@withContext currentUrl
            }

            // 2. If it's a kwik.cx link (e.g. https://kwik.cx/f/B2AEtFL9EPZY)
            if (currentUrl.contains("kwik.cx")) {
                // Try fast OkHttp POST extraction first
                val fastDirect = extractKwikDirectOkHttp(currentUrl)
                if (!fastDirect.isNullOrBlank()) {
                    return@withContext fastDirect
                }

                // If OkHttp is blocked by Cloudflare and Context is provided, use headless Android WebView
                if (context != null) {
                    val webViewDirect = extractKwikDirectWebView(context, currentUrl)
                    if (!webViewDirect.isNullOrBlank()) {
                        return@withContext webViewDirect
                    }
                }
            }

            return@withContext currentUrl
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext sourceUrl
        }
    }

    /**
     * Resolves kwik.cx direct link via OkHttp form POST
     */
    private fun extractKwikDirectOkHttp(kwikUrl: String): String? {
        try {
            val req = Request.Builder()
                .url(kwikUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://kwik.cx/")
                .build()

            var postAction = ""
            var token = ""

            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: ""
                val actionMatch = Regex("""action=["'](https://kwik\.cx/d/[^"']+)["']""").find(body)
                val tokenMatch = Regex("""name=["']_token["']\s+value=["']([^"']+)["']""").find(body)
                    ?: Regex("""value=["']([^"']+)["']\s+name=["']_token["']""").find(body)

                if (actionMatch != null && tokenMatch != null) {
                    postAction = actionMatch.groupValues[1]
                    token = tokenMatch.groupValues[1]
                }
            }

            if (postAction.isNotBlank() && token.isNotBlank()) {
                val formBody = FormBody.Builder()
                    .add("_token", token)
                    .build()

                val postReq = Request.Builder()
                    .url(postAction)
                    .post(formBody)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", kwikUrl)
                    .build()

                client.newCall(postReq).execute().use { postRes ->
                    val directLocation = postRes.header("Location")
                    if (!directLocation.isNullOrBlank()) {
                        return directLocation
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Headless background WebView extractor to solve Cloudflare / execute Kwik download submit
     */
    private suspend fun extractKwikDirectWebView(context: Context, kwikUrl: String): String? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(12000L) {
            suspendCancellableCoroutine { continuation ->
                var isCompleted = false
                var webView: WebView? = null

                fun finish(result: String?) {
                    if (!isCompleted) {
                        isCompleted = true
                        try {
                            webView?.stopLoading()
                            webView?.destroy()
                        } catch (_: Exception) {}
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                }

                try {
                    val wv = WebView(context.applicationContext)
                    webView = wv
                    wv.settings.javaScriptEnabled = true
                    wv.settings.domStorageEnabled = true
                    wv.settings.userAgentString = USER_AGENT

                    CookieManager.getInstance().setAcceptCookie(true)

                    wv.setDownloadListener { url, _, _, _, _ ->
                        if (url.startsWith("http")) {
                            finish(url)
                        }
                    }

                    wv.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val target = request?.url?.toString() ?: ""
                            if (target.contains("owocdn.top") || target.contains("/mp4/") || (target.endsWith(".mp4") && target.startsWith("http"))) {
                                finish(target)
                                return true
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Auto trigger download submit form
                            val js = """
                                (function() {
                                    var f = document.querySelector('form');
                                    if (f) { f.submit(); return; }
                                    var btn = document.querySelector('button[type="submit"], .btn-download, a.btn');
                                    if (btn) { btn.click(); }
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(js, null)

                            // Check periodically for redirect
                            Handler(Looper.getMainLooper()).postDelayed({
                                view?.evaluateJavascript(
                                    "(function() { return document.querySelector('a[href*=\"owocdn\"], a[href*=\".mp4\"]')?.href || ''; })();"
                                ) { directHref ->
                                    val cleaned = directHref?.replace("\"", "")?.trim()
                                    if (!cleaned.isNullOrBlank() && cleaned.startsWith("http")) {
                                        finish(cleaned)
                                    }
                                }
                            }, 2500L)
                        }
                    }

                    wv.loadUrl(kwikUrl)
                } catch (e: Exception) {
                    finish(null)
                }

                continuation.invokeOnCancellation {
                    try {
                        webView?.stopLoading()
                        webView?.destroy()
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
