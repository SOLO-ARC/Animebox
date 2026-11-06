package com.lagradost.cloudstream3.ui.animebox.extractors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 1:1 port of Anilili's AniPmProvider (L.w + F4.C0415m2.m()).
 *
 * Flow:
 * 1. GET https://ani.pm/api/anime/anipm-server/playback-bootstrap?anilistId={id}&ep={ep}&lang={sub|dub}
 *    -> data.playback.sessionToken
 * 2. GET https://ani.pm/api/anime/anipm-server/settlar/session?ep={ep}&lang={sub|dub}&token={sessionToken}
 *    -> data.embedUrl (e.g. https://embed.settlar.io/embed?t={embedToken})
 * 3. GET https://embed.settlar.io/api/embed/session?t={embedToken}
 *    -> streamUrl (.m3u8), subtitles[], intro {start,end}, outro {start,end}
 */
data class AniPmStreamResult(
    val hlsUrl: String,
    val subtitleUrl: String = "",
    val subtitles: List<Map<String, String>> = emptyList(),
    val provider: String = "Ani.pm",
    val referer: String = "",
    val introStart: Long = 0L,
    val introEnd: Long = 0L,
    val outroStart: Long = 0L,
    val outroEnd: Long = 0L
)

object AniPmExtractor {

    private const val BASE_URL = "https://ani.pm"
    private const val SETTLAR_BASE = "https://embed.settlar.io"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"

    private val TOKEN_REGEX = Regex("""[?&]t=([^&#]+)""")

    private val LANGUAGE_CODE_MAP = mapOf(
        "english" to "en",
        "spanish" to "es",
        "portuguese" to "pt",
        "french" to "fr",
        "german" to "de",
        "italian" to "it",
        "russian" to "ru",
        "arabic" to "ar",
        "japanese" to "ja",
        "korean" to "ko",
        "chinese" to "zh",
        "indonesian" to "id",
        "thai" to "th",
        "vietnamese" to "vi",
        "polish" to "pl",
        "turkish" to "tr",
        "dutch" to "nl",
        "hindi" to "hi"
    )

    private fun getJson(url: String, referer: String? = null, origin: String? = null): JSONObject? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .apply {
                if (!referer.isNullOrEmpty()) header("Referer", referer)
                if (!origin.isNullOrEmpty()) header("Origin", origin)
                header("Sec-Fetch-Mode", "cors")
                header("Sec-Fetch-Dest", "empty")
            }
            .get()
            .build()
        return AnililiHttpClient.client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || !body.trimStart().startsWith("{")) return null
            runCatching { JSONObject(body) }.getOrNull()
        }
    }

    suspend fun extract(
        anilistId: Int,
        episode: Int,
        lang: String = "sub"
    ): AniPmStreamResult? = withContext(Dispatchers.IO) {
        try {
            val langStr = if (lang.equals("dub", ignoreCase = true)) "dub" else "sub"

            // Step 1: Playback bootstrap -> settlarSelection + skip times (F4.C0415m2.m lines 1011-1065)
            val bootstrapUrl = "$BASE_URL/api/anime/playback-bootstrap/anilist/$anilistId?ep=$episode&lang=$langStr&core=0"
            val bootstrapJson = getJson(bootstrapUrl, referer = "$BASE_URL/", origin = BASE_URL) ?: return@withContext null

            val settlarSelection = bootstrapJson.optString("settlarSelection", "").trim()
                .ifEmpty { bootstrapJson.optJSONObject("data")?.optString("settlarSelection", "")?.trim() ?: "" }
                .ifEmpty {
                    bootstrapJson.optJSONObject("data")
                        ?.optJSONObject("playback")
                        ?.optString("sessionToken", "")
                        ?.trim()
                        .orEmpty()
                }
                .ifEmpty {
                    bootstrapJson.optJSONObject("playback")
                        ?.optString("sessionToken", "")
                        ?.trim()
                        .orEmpty()
                }
                .takeIf { it.isNotBlank() }
                ?: return@withContext null

            val effectiveLang = bootstrapJson.optString("effectiveLanguage", "")
                .ifEmpty { bootstrapJson.optJSONObject("data")?.optString("effectiveLanguage", "") ?: "" }
                .trim().lowercase()
            val isSubRequest = langStr == "sub"
            val isDubRequest = langStr == "dub"
            val isLanguageMatch = when {
                effectiveLang.isEmpty() -> true
                isSubRequest -> effectiveLang in listOf("sub", "ja", "jp", "jpn", "japanese", "raw", "en", "eng", "english")
                isDubRequest -> effectiveLang in listOf("dub", "en", "eng", "english")
                else -> true
            }
            if (!isLanguageMatch && settlarSelection.isBlank()) {
                return@withContext null
            }

            val skipObj = bootstrapJson.optJSONObject("skip")
                ?: bootstrapJson.optJSONObject("data")?.optJSONObject("skip")
            val opObj = skipObj?.optJSONObject("op") ?: bootstrapJson.optJSONObject("intro")
            val edObj = skipObj?.optJSONObject("ed") ?: bootstrapJson.optJSONObject("outro")
            var introStart = opObj?.optDouble("start", 0.0)?.toLong() ?: 0L
            var introEnd = opObj?.optDouble("end", 0.0)?.toLong() ?: 0L
            var outroStart = edObj?.optDouble("start", 0.0)?.toLong() ?: 0L
            var outroEnd = edObj?.optDouble("end", 0.0)?.toLong() ?: 0L

            // Step 2: Settlar session -> embedUrl
            val encodedSelection = URLEncoder.encode(settlarSelection, "UTF-8")
            val settlarSessionUrl = "$BASE_URL/api/anime/settlar/session?selection=$encodedSelection&provider=anipm&ep=$episode&channel=$langStr&telemetry=0"
            val settlarJson = getJson(settlarSessionUrl, referer = "$BASE_URL/", origin = BASE_URL) ?: return@withContext null
            val embedUrl = settlarJson.optString("embedUrl", "").trim()
                .ifEmpty { settlarJson.optJSONObject("data")?.optString("embedUrl", "")?.trim().orEmpty() }
                .ifEmpty { settlarJson.optJSONObject("data")?.optJSONObject("playback")?.optString("embedUrl", "")?.trim().orEmpty() }
                .ifEmpty { settlarJson.optString("embed_url", "").trim() }
                .takeIf { it.isNotBlank() }
                ?: return@withContext null

            val rawEmbedToken = TOKEN_REGEX.find(embedUrl)?.groupValues?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            val decodedEmbedToken = runCatching { URLDecoder.decode(rawEmbedToken, "UTF-8") }
                .getOrDefault(rawEmbedToken)
            val encodedEmbedToken = URLEncoder.encode(decodedEmbedToken, "UTF-8")

            // Step 3: Settlar embed session -> source (.m3u8) + subtitles
            val finalUrl = "$SETTLAR_BASE/api/embed/session?t=$encodedEmbedToken"
            val finalJson = getJson(finalUrl, referer = embedUrl, origin = SETTLAR_BASE) ?: return@withContext null

            val streamUrl = finalJson.optString("source", "").trim()
                .ifEmpty { finalJson.optString("streamUrl", "").trim() }
                .ifEmpty { finalJson.optString("url", "").trim() }
                .ifEmpty { finalJson.optJSONObject("data")?.optString("source", "")?.trim().orEmpty() }
                .ifEmpty { finalJson.optJSONObject("data")?.optString("streamUrl", "")?.trim().orEmpty() }
                .ifEmpty { finalJson.optJSONObject("data")?.optString("url", "")?.trim().orEmpty() }
                .ifEmpty { finalJson.optJSONArray("sources")?.optJSONObject(0)?.optString("url", "")?.trim().orEmpty() }
                .takeIf { it.startsWith("http", ignoreCase = true) }
                ?: return@withContext null

            val subtitleList = mutableListOf<Map<String, String>>()
            val seenSubUrls = HashSet<String>()
            var defaultSubUrl = ""

            val subsArr = finalJson.optJSONArray("subtitles") ?: finalJson.optJSONArray("tracks")
            if (subsArr != null) {
                for (i in 0 until subsArr.length()) {
                    val subObj = subsArr.optJSONObject(i) ?: continue
                    val kind = subObj.optString("kind", "").lowercase()
                    val rawLabel = subObj.optString("label", "").trim()
                    var sUrl = subObj.optString("url", "").trim()
                        .ifEmpty { subObj.optString("file", "").trim() }
                    if (sUrl.isEmpty()) continue
                    val lowerUrl = sUrl.lowercase()
                    if (kind.contains("thumb") || kind.contains("sprite") ||
                        rawLabel.contains("thumb", ignoreCase = true) || rawLabel.contains("sprite", ignoreCase = true) ||
                        lowerUrl.contains("sprite") || lowerUrl.contains("xywh=") || lowerUrl.contains("thumbnail") ||
                        lowerUrl.endsWith(".webp") || lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".png")
                    ) {
                        continue
                    }
                    if (!sUrl.startsWith("http://") && !sUrl.startsWith("https://")) {
                        sUrl = if (sUrl.startsWith("/")) "$SETTLAR_BASE$sUrl" else "$SETTLAR_BASE/$sUrl"
                    }
                    if (!seenSubUrls.add(sUrl)) continue

                    val label = rawLabel.ifEmpty { "Subtitles" }
                    val rawLang = subObj.optString("srclang", "").trim()
                        .ifEmpty { subObj.optString("lang", "").trim() }
                    val baseLabelWord = label.substringBefore(' ').substringBefore('(').lowercase()
                    val langCode = rawLang.ifEmpty { LANGUAGE_CODE_MAP[baseLabelWord] ?: "und" }
                    val isDefaultSub = subObj.optBoolean("default", false)

                    subtitleList.add(
                        mapOf(
                            "url" to sUrl,
                            "lang" to label,
                            "language" to langCode
                        )
                    )
                    if (defaultSubUrl.isEmpty() && (
                            isDefaultSub ||
                            label.contains("English", ignoreCase = true) ||
                            langCode.equals("en", ignoreCase = true) ||
                            langCode.equals("eng", ignoreCase = true)
                        )
                    ) {
                        defaultSubUrl = sUrl
                    }
                }
            }
            if (defaultSubUrl.isEmpty() && subtitleList.isNotEmpty()) {
                defaultSubUrl = subtitleList.first()["url"] ?: ""
            }

            // Fallback skip times from finalJson if not present in bootstrapJson
            if (introEnd <= introStart) {
                val fIntro = finalJson.optJSONObject("intro")
                introStart = fIntro?.optDouble("start", 0.0)?.toLong() ?: 0L
                introEnd = fIntro?.optDouble("end", 0.0)?.toLong() ?: 0L
            }
            if (outroEnd <= outroStart) {
                val fOutro = finalJson.optJSONObject("outro")
                outroStart = fOutro?.optDouble("start", 0.0)?.toLong() ?: 0L
                outroEnd = fOutro?.optDouble("end", 0.0)?.toLong() ?: 0L
            }

            AniPmStreamResult(
                hlsUrl = streamUrl,
                subtitleUrl = defaultSubUrl,
                subtitles = subtitleList,
                provider = if (langStr == "dub") "Ani.pm Dub" else "Ani.pm",
                referer = "https://embed.settlar.io/",
                introStart = if (introEnd > introStart) introStart else 0L,
                introEnd = if (introEnd > introStart) introEnd else 0L,
                outroStart = if (outroEnd > outroStart) outroStart else 0L,
                outroEnd = if (outroEnd > outroStart) outroEnd else 0L
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
