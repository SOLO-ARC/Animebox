package com.lagradost.cloudstream3.ui.animebox.api
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class YouTubeVideoInfo(
    val videoId: String,
    val title: String,
    val authorName: String,
    val authorAvatarUrl: String,
    val thumbnailUrl: String,
    val unavailable: Boolean = false
)

object YouTubeMetadataManager {

    private val cache = ConcurrentHashMap<String, YouTubeVideoInfo>()

    private val httpClient: OkHttpClient by lazy {
        AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val AVATAR_URL_PATTERN = Pattern.compile("""https://yt3\.ggpht\.com/[a-zA-Z0-9_-]+=s\d+-[^"'\s]+""")
    private val OWNER_RENDERER_PATTERN = Pattern.compile(""""owner"\s*:\s*\{"videoOwnerRenderer"\s*:\s*\{[^}]*"thumbnail"\s*:\s*\{"thumbnails"\s*:\s*\[\{"url"\s*:\s*"([^"]+)"""")
    private val CHANNEL_THUMB_PATTERN = Pattern.compile(""""channelThumbnailSupportedRenderers"\s*:\s*\{"channelThumbnailWithLinkRenderer"\s*:\s*\{"thumbnail"\s*:\s*\{"thumbnails"\s*:\s*\[\{"url"\s*:\s*"([^"]+)"""")

    fun getCached(videoId: String): YouTubeVideoInfo? = cache[videoId]

    suspend fun fetchVideoInfo(videoId: String, defaultTitle: String? = null): YouTubeVideoInfo = withContext(Dispatchers.IO) {
        val cleanId = videoId.trim()
        if (cleanId.isBlank()) {
            return@withContext YouTubeVideoInfo(
                videoId = cleanId,
                title = defaultTitle ?: "YouTube Video",
                authorName = "YouTube",
                authorAvatarUrl = "",
                thumbnailUrl = "",
                unavailable = false
            )
        }

        cache[cleanId]?.let { return@withContext it }

        var resolvedTitle = defaultTitle?.takeIf { it.isNotBlank() && !it.equals("Watch video on YouTube", ignoreCase = true) && !it.equals("YouTube Video Attachment", ignoreCase = true) } ?: ""
        var resolvedAuthor = "YouTube"
        var resolvedAvatar = ""
        var resolvedThumb = "https://i.ytimg.com/vi/$cleanId/hqdefault.jpg"
        var unavailable = false

        // 1. Fetch Fast oEmbed metadata (Title, Author Name, Thumbnail)
        try {
            val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$cleanId&format=json"
            val oembedReq = Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            httpClient.newCall(oembedReq).execute().use { resp ->
                if (resp.code == 404) unavailable = true
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val oembedTitle = json.optString("title", "").trim()
                        if (oembedTitle.isNotBlank()) {
                            resolvedTitle = oembedTitle
                        }
                        val oembedAuthor = json.optString("author_name", "").trim()
                        if (oembedAuthor.isNotBlank()) {
                            resolvedAuthor = oembedAuthor
                        }
                        val oembedThumb = json.optString("thumbnail_url", "").trim()
                        if (oembedThumb.isNotBlank()) {
                            resolvedThumb = oembedThumb
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // 2. Fetch watch page to extract the actual creator channel avatar logo icon
        try {
            val watchUrl = "https://www.youtube.com/watch?v=$cleanId"
            val watchReq = Request.Builder()
                .url(watchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            httpClient.newCall(watchReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    if (Regex("\"playabilityStatus\"\\s*:\\s*\\{\\s*\"status\"\\s*:\\s*\"ERROR\"").containsMatchIn(html) &&
                        Regex("""(?i)(video (?:is )?unavailable|video has been removed|removed by the uploader)""").containsMatchIn(html)
                    ) {
                        unavailable = true
                    }

                    // Extract Title if oEmbed was blocked or missing
                    if (resolvedTitle.isBlank() || resolvedTitle == "YouTube Video") {
                        val titleMatch = Regex("""<title>(.*?) - YouTube</title>""").find(html)
                        if (titleMatch != null) {
                            resolvedTitle = titleMatch.groupValues[1].trim()
                        }
                    }

                    // Extract creator avatar from videoOwnerRenderer or channel thumbnail
                    var avatarCandidate = ""
                    val ownerMatcher = OWNER_RENDERER_PATTERN.matcher(html)
                    if (ownerMatcher.find()) {
                        avatarCandidate = ownerMatcher.group(1).orEmpty()
                    }

                    if (avatarCandidate.isBlank()) {
                        val chMatcher = CHANNEL_THUMB_PATTERN.matcher(html)
                        if (chMatcher.find()) {
                            avatarCandidate = chMatcher.group(1).orEmpty()
                        }
                    }

                    if (avatarCandidate.isBlank()) {
                        val avatarMatcher = AVATAR_URL_PATTERN.matcher(html)
                        if (avatarMatcher.find()) {
                            avatarCandidate = avatarMatcher.group()
                        }
                    }

                    if (avatarCandidate.isNotBlank()) {
                        // Upgrade to sharp high-res square avatar
                        resolvedAvatar = avatarCandidate.replace(Regex("""=s\d+-[^"'\s]+"""), "=s176-c-k-c0x00ffffff-no-rj")
                    }
                }
            }
        } catch (_: Throwable) {}

        if (resolvedTitle.isBlank()) {
            resolvedTitle = defaultTitle ?: "YouTube Video"
        }

        val result = YouTubeVideoInfo(
            videoId = cleanId,
            title = resolvedTitle,
            authorName = resolvedAuthor,
            authorAvatarUrl = resolvedAvatar,
            thumbnailUrl = resolvedThumb,
            unavailable = unavailable
        )
        cache[cleanId] = result
        return@withContext result
    }
}
