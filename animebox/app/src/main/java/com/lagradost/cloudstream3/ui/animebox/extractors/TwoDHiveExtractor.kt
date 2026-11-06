package com.lagradost.cloudstream3.ui.animebox.extractors

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class TwoDHiveResult(
    val hlsUrl: String,
    val referer: String = "https://2dhive.com/",
    val provider: String = "2Dhive",
    val subtitleUrl: String = "",
    val subtitles: List<Map<String, String>> = emptyList(),
    val introStart: Long = 0L,
    val introEnd: Long = 0L,
    val outroStart: Long = 0L,
    val outroEnd: Long = 0L
)

/**
 * 1:1 port of Anilili's 2Dhive provider:
 * - F4.N3 (2Dhive episode catalog, props hydration, and server resolution)
 * - F4.K3 (https://2dhive.com/episode?anime=<malId>&ep_num=<epNum> + prefetchedHls extraction)
 * - F4.J3 (https://megaplay.buzz/stream/mal/<malId>/<epNum>/<subOrDub> fallback with Referer 2dhive.com)
 * - babastream.top AES-GCM embed resolver
 */
object TwoDHiveExtractor {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val BASE_URL = "https://2dhive.com"

    private val cache = ConcurrentHashMap<String, TwoDHiveResult>()

    private fun decodeAstroProps(html: String): JSONObject? {
        val prefetchedIdx = html.indexOf("prefetchedHls")
        if (prefetchedIdx >= 0) {
            val propsPrefix = "props=\""
            val propsIdx = html.lastIndexOf(propsPrefix, prefetchedIdx)
            if (propsIdx >= 0) {
                val start = propsIdx + propsPrefix.length
                val end = html.indexOf('"', start)
                if (end > start) {
                    val rawProps = html.substring(start, end)
                    val unescaped = rawProps
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&#39;", "'")
                    return runCatching { JSONObject(unescaped) }.getOrNull()
                }
            }
        }
        return null
    }

    private fun extractM3u8OrIframe(html: String): Pair<String?, String?> {
        val m3u8M = Pattern.compile("""["'](https?://[^"'\s]+\.m3u8[^"'\s]*)["']""").matcher(html)
        val m3u8Url = if (m3u8M.find()) m3u8M.group(1) else null

        val iframeM = Pattern.compile("""<iframe\b[^>]*?\bsrc=["'](https?://[^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(html)
        val iframeUrl = if (iframeM.find()) iframeM.group(1) else null

        return Pair(m3u8Url, iframeUrl)
    }

    private fun aesGcmEncrypt(keyBytes: ByteArray, plaintext: String): String {
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(ciphertext, 0, out, iv.size, ciphertext.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun aesGcmDecrypt(keyBytes: ByteArray, b64Ciphertext: String): String {
        val raw = Base64.decode(b64Ciphertext, Base64.NO_WRAP)
        val iv = ByteArray(12)
        System.arraycopy(raw, 0, iv, 0, 12)
        val ct = ByteArray(raw.size - 12)
        System.arraycopy(raw, 12, ct, 0, ct.size)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val pt = cipher.doFinal(ct)
        return String(pt, Charsets.UTF_8)
    }

    private suspend fun resolveBabastream(embedUrl: String): TwoDHiveResult? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .build()

            val html = AnililiHttpClient.client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
            if (html.isEmpty()) return@withContext null

            val cfgM = Pattern.compile("""var\s+CFG\s*=\s*(\{.*?\});""").matcher(html)
            if (!cfgM.find()) return@withContext null
            val cfgJson = JSONObject(cfgM.group(1) ?: return@withContext null)

            val sid = cfgJson.optString("sid", "")
            val pkB64 = cfgJson.optString("pk", "")
            if (sid.isEmpty() || pkB64.isEmpty()) return@withContext null

            val pkBytes = Base64.decode(pkB64, Base64.NO_WRAP)
            val payload = """{"ts":${System.currentTimeMillis()}}"""
            val encPayload = aesGcmEncrypt(pkBytes, payload)

            val postJson = JSONObject().apply {
                put("s", sid)
                put("d", encPayload)
            }

            val apiReq = Request.Builder()
                .url("https://babastream.top/api/resolve")
                .header("User-Agent", USER_AGENT)
                .header("Referer", embedUrl)
                .header("Origin", "https://babastream.top")
                .post(postJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val apiRespStr = AnililiHttpClient.client.newCall(apiReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
            if (apiRespStr.isNotEmpty()) {
                val apiRespJson = JSONObject(apiRespStr)
                val respD = apiRespJson.optString("d", "")
                if (respD.isNotEmpty()) {
                    val decrypted = aesGcmDecrypt(pkBytes, respD)
                    val decJson = JSONObject(decrypted)
                    val streamUrl = decJson.optString("u", "")
                    if (streamUrl.isNotEmpty() && streamUrl.startsWith("http")) {
                        return@withContext TwoDHiveResult(
                            hlsUrl = streamUrl,
                            referer = "https://babastream.top/",
                            provider = "2Dhive"
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

    suspend fun extract(
        anilistId: Int,
        episodeNum: Int,
        mode: String = "sub",
        animeTitle: String? = null
    ): TwoDHiveResult? = withContext(Dispatchers.IO) {
        val malId = MalIdResolver.getMalId(anilistId)
        if (malId <= 0) return@withContext null

        val cacheKey = "${malId}_${episodeNum}_$mode"
        cache[cacheKey]?.let { return@withContext it }

        val targetMode = if (mode.equals("dub", ignoreCase = true)) "dub" else "sub"

        // 1. Check 2dhive page
        try {
            val pageUrl = "$BASE_URL/episode?anime=$malId&ep_num=$episodeNum"
            val pageReq = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .build()

            val html = AnililiHttpClient.client.newCall(pageReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }

            if (html.isNotEmpty()) {
                val props = decodeAstroProps(html)
                val prefetchedHls = props?.optString("prefetchedHls", "")
                if (!prefetchedHls.isNullOrBlank() && prefetchedHls.startsWith("http")) {
                    val res = TwoDHiveResult(
                        hlsUrl = prefetchedHls,
                        referer = "$BASE_URL/",
                        provider = "2Dhive"
                    )
                    cache[cacheKey] = res
                    return@withContext res
                }

                val (directM3u8, iframeUrl) = extractM3u8OrIframe(html)
                if (!directM3u8.isNullOrBlank()) {
                    val res = TwoDHiveResult(
                        hlsUrl = directM3u8,
                        referer = "$BASE_URL/",
                        provider = "2Dhive"
                    )
                    cache[cacheKey] = res
                    return@withContext res
                }

                if (!iframeUrl.isNullOrBlank()) {
                    if (iframeUrl.contains("babastream", ignoreCase = true)) {
                        resolveBabastream(iframeUrl)?.let {
                            cache[cacheKey] = it
                            return@withContext it
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Megaplay.buzz /stream/mal fallback (F4.J3 in Anilili)
        try {
            val megaUrl = "https://megaplay.buzz/stream/mal/$malId/$episodeNum/$targetMode"
            val megaReq = Request.Builder()
                .url(megaUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .build()

            val megaHtml = AnililiHttpClient.client.newCall(megaReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }

            if (megaHtml.isNotEmpty()) {
                val dataIdM = Pattern.compile("""data-id=["']?([^"'\s>]+)""", Pattern.CASE_INSENSITIVE).matcher(megaHtml)
                if (dataIdM.find()) {
                    val fileId = dataIdM.group(1).orEmpty()
                    if (fileId.isNotEmpty() && !fileId.equals("no", ignoreCase = true)) {
                        for (sParam in listOf("tcdn", "", "bcdn")) {
                            val sourcesUrl = if (sParam.isNotEmpty()) {
                                "https://megaplay.buzz/stream/getSources?id=$fileId&s=$sParam"
                            } else {
                                "https://megaplay.buzz/stream/getSources?id=$fileId"
                            }
                            val sourcesReq = Request.Builder()
                                .url(sourcesUrl)
                                .header("User-Agent", USER_AGENT)
                                .header("Referer", megaUrl)
                                .header("X-Requested-With", "XMLHttpRequest")
                                .build()
                            val sRespStr = try {
                                AnililiHttpClient.client.newCall(sourcesReq).execute().use { resp ->
                                    if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                                }
                            } catch (_: Exception) { "" }

                            if (sRespStr.isNotEmpty()) {
                                val sJson = JSONObject(sRespStr)
                                var hlsUrl = ""
                                if (sJson.has("sources")) {
                                    val sourcesArr = sJson.optJSONArray("sources")
                                    if (sourcesArr != null && sourcesArr.length() > 0) {
                                        hlsUrl = sourcesArr.getJSONObject(0).optString("file", "")
                                    }
                                }
                                if (hlsUrl.isEmpty() && sJson.has("file")) {
                                    hlsUrl = sJson.optString("file", "")
                                }
                                if (hlsUrl.isEmpty() && sJson.has("enc")) {
                                    hlsUrl = MegaPlayExtractor.decryptEncSources(sJson.optString("enc")) ?: ""
                                }
                                if (hlsUrl.isNotEmpty() && hlsUrl.startsWith("http")) {
                                    val subsList = mutableListOf<Map<String, String>>()
                                    val tracks = sJson.optJSONArray("tracks") ?: sJson.optJSONArray("subtitles")
                                    if (tracks != null) {
                                        for (ti in 0 until tracks.length()) {
                                            val tObj = tracks.optJSONObject(ti) ?: continue
                                            val f = tObj.optString("file", tObj.optString("url", ""))
                                            val l = tObj.optString("label", tObj.optString("lang", "English"))
                                            val kind = tObj.optString("kind", "")
                                            if (f.isNotEmpty() && (kind == "captions" || kind == "subtitles" || kind.isEmpty())) {
                                                subsList.add(mapOf("url" to f, "lang" to l))
                                            }
                                        }
                                    }
                                    val engSub = subsList.find { (it["lang"] ?: "").contains("eng", true) }?.get("url")
                                        ?: subsList.firstOrNull()?.get("url") ?: ""

                                    val intro = sJson.optJSONObject("intro")
                                    val outro = sJson.optJSONObject("outro")
                                    val iStart = intro?.optLong("start", 0L) ?: 0L
                                    val iEnd = intro?.optLong("end", 0L) ?: 0L
                                    val oStart = outro?.optLong("start", 0L) ?: 0L
                                    val oEnd = outro?.optLong("end", 0L) ?: 0L

                                    val res = TwoDHiveResult(
                                        hlsUrl = hlsUrl,
                                        referer = "https://megaplay.buzz/",
                                        provider = "2Dhive",
                                        subtitleUrl = engSub,
                                        subtitles = subsList,
                                        introStart = iStart * 1000L,
                                        introEnd = iEnd * 1000L,
                                        outroStart = oStart * 1000L,
                                        outroEnd = oEnd * 1000L
                                    )
                                    cache[cacheKey] = res
                                    return@withContext res
                                }
                            }
                        }
                    }
                }

                val (directM3u8, iframeUrl) = extractM3u8OrIframe(megaHtml)
                if (!directM3u8.isNullOrBlank()) {
                    val res = TwoDHiveResult(
                        hlsUrl = directM3u8,
                        referer = "https://megaplay.buzz/",
                        provider = "2Dhive"
                    )
                    cache[cacheKey] = res
                    return@withContext res
                }
                if (!iframeUrl.isNullOrBlank() && iframeUrl.contains("babastream", ignoreCase = true)) {
                    resolveBabastream(iframeUrl)?.let {
                        cache[cacheKey] = it
                        return@withContext it
                    }
                }
            }
        } catch (_: Exception) {}

        null
    }
}

