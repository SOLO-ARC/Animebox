package com.lagradost.cloudstream3.ui.animebox.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WatchOrderMapItem(
    val id: Int,
    val title: String,
    val badge: String = "",
    val note: String = "",
    val coverUrl: String = "",
    val bannerUrl: String = "",
    val format: String = "",
    val year: Int? = null,
    val episodes: Int? = null,
    val iidbUrl: String = ""
)

data class AiQuizOption(
    val label: String, // "A", "B", "C", "D"
    val text: String,
    val imageUrl: String = "",
    val note: String = ""
)

data class AiQuizData(
    val question: String,
    val gameType: String = "", // "character", "anime", "pokemon", "trivia", "quote", etc.
    val options: List<AiQuizOption> = emptyList()
)

data class IidbGameMetadata(
    val id: Int,
    val title: String,
    val bannerUrl: String,
    val coverUrl: String,
    val detailUrl: String
)

data class AiPlayEpisodeAction(
    val animeId: Int,
    val animeTitle: String,
    val episodeNum: Int,
    val isOutOfRange: Boolean = false,
    val maxEpisodes: Int? = null,
    val clarificationNeeded: Boolean = false,
    val clarificationQuestion: String = ""
)

data class AiSearchImage(
    val title: String,
    val imageUrl: String,
    val sourceUrl: String = "",
    val thumbnailUrl: String = ""
)

data class AiVideoResult(
    val videoId: String,
    val title: String,
    val channel: String = "",
    val duration: String = "",
    val thumbnailUrl: String = "",
    val videoUrl: String = "",
    val channelAvatarUrl: String = ""
)

data class AiChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant" or "system"
    val text: String,
    val watchOrderMap: List<WatchOrderMapItem>? = null,
    val quizData: AiQuizData? = null,
    val playAction: AiPlayEpisodeAction? = null,
    val sectionTitle: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val base64Image: String? = null,
    val searchImages: List<AiSearchImage>? = null,
    val searchVideos: List<AiVideoResult>? = null
)

data class ParsedAiPayload(
    val cleanText: String,
    val mapItems: List<WatchOrderMapItem>,
    val quizData: AiQuizData?,
    val playAction: AiPlayEpisodeAction?,
    val sectionTitle: String?
)

/**
 * Fast & reliable image search fetcher using Lovable Image API.
 * Queries https://ai-overview-api.lovable.app/api/public/images?q={query}&limit=20
 */
object GoogleImageSearchFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private fun cleanHtmlEntities(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#8217;", "'")
            .replace("&#8216;", "'")
            .replace("&#8211;", "-")
            .replace("&#8212;", "-")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun queryLovableApi(query: String, limit: Int = 20, safe: String = "moderate"): List<AiSearchImage> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        val lovableUrl = "https://web-image-sleuth.lovable.app/api/public/image-search?q=$encoded&page=1&safe=$safe"
        val req = Request.Builder()
            .url(lovableUrl)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "application/json")
            .build()

        val results = mutableListOf<AiSearchImage>()
        val seen = mutableSetOf<String>()

        try {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val rootJson = JSONObject(body)
                    val imagesArr = rootJson.optJSONArray("results") ?: rootJson.optJSONArray("images")
                    if (imagesArr != null) {
                        for (i in 0 until imagesArr.length()) {
                            val item = imagesArr.optJSONObject(i) ?: continue
                            val imgUrl = item.optString("imageUrl", "").trim()
                            if (imgUrl.isBlank() || !imgUrl.startsWith("http") || imgUrl in seen) continue

                            val rawTitle = item.optString("title", query.trim())
                            val cleanTitle = cleanHtmlEntities(rawTitle).ifBlank { query.trim() }
                            val thumbUrl = item.optString("thumbnailUrl", imgUrl).trim().ifBlank { imgUrl }
                            val sourceUrl = item.optString("sourceUrl", item.optString("sourcePage", "")).trim()

                            seen.add(imgUrl)
                            results.add(
                                AiSearchImage(
                                    title = cleanTitle,
                                    imageUrl = imgUrl,
                                    sourceUrl = sourceUrl.ifBlank { thumbUrl },
                                    thumbnailUrl = thumbUrl
                                )
                            )
                            if (results.size >= limit) break
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return results
    }

    suspend fun searchImages(animeTitle: String, userQuery: String): List<AiSearchImage> = withContext(Dispatchers.IO) {
        try {
            val lower = userQuery.lowercase().trim()

            // Guardrail 1: App guide / settings / video player theme questions MUST NOT trigger image searches
            val isAppGuideQuery = run {
                val mentionsMySpace = lower.contains("my space") || lower.contains("myspace")
                val isHowToSettings = (lower.contains("how to") || lower.contains("how do i") || lower.contains("how can i") || lower.contains("where to") || lower.contains("where is") || lower.contains("where can i") || lower.contains("how do we")) &&
                    (lower.contains("setting") || lower.contains("settings") || lower.contains("change") || lower.contains("enable") || lower.contains("disable") || lower.contains("turn on") || lower.contains("turn off") || lower.contains("set") || lower.contains("configure")) &&
                    (lower.contains("theme") || lower.contains("color") || lower.contains("colour") || lower.contains("seekbar") || lower.contains("player") || lower.contains("dns") || lower.contains("subtitle") || lower.contains("audio") || lower.contains("cache") || lower.contains("download") || lower.contains("backup") || lower.contains("restore") || lower.contains("profile") || lower.contains("avatar") || lower.contains("sync") || lower.contains("server"))
                val isAppTroubleshoot = (lower.contains("firefly") || lower.contains("animebox") || lower.contains("in app") || lower.contains("the app")) &&
                    (lower.contains("bug") || lower.contains("crash") || lower.contains("ticket") || lower.contains("support") || lower.contains("update") || lower.contains("version") || lower.contains("setting") || lower.contains("settings"))
                val isDirectSetting = lower.contains("seekbar color") || lower.contains("timeline color") || lower.contains("dns routing") || lower.contains("clear cache") || lower.contains("subtitles appearance") || lower.contains("backup & data") || lower.contains("backup data") || lower.contains("low performance mode")

                mentionsMySpace || isHowToSettings || isAppTroubleshoot || isDirectSetting
            }

            if (isAppGuideQuery && !lower.contains("image") && !lower.contains("photo") && !lower.contains("picture")) return@withContext emptyList()

            val isImageIntent = lower.contains("image") ||
                lower.contains("images") ||
                lower.contains("iamge") ||
                lower.contains("iamges") ||
                lower.contains("picture") ||
                lower.contains("pictures") ||
                lower.contains("picitre") ||
                lower.contains("picitres") ||
                lower.contains("photo") ||
                lower.contains("photos") ||
                lower.contains("pic") ||
                lower.contains("pics") ||
                lower.contains("gallery") ||
                lower.contains("look like") ||
                lower.contains("show") ||
                lower.contains("wallpaper") ||
                lower.contains("wallpapers") ||
                lower.contains("art") ||
                lower.contains("visual") ||
                lower.contains("appearance") ||
                lower.contains("dress") ||
                lower.contains("outfit") ||
                lower.contains("costume") ||
                lower.contains("bunny") ||
                lower.contains("cosplay") ||
                lower.contains("scene") ||
                lower.contains("tree view") ||
                lower.contains("form") ||
                lower.contains("clothes") ||
                lower.contains("suit") ||
                lower.contains("swimsuit") ||
                lower.contains("swimsuits") ||
                lower.contains("bikini") ||
                lower.contains("beach") ||
                lower.contains("figure") ||
                lower.contains("model") ||
                lower.contains("pokemon") ||
                lower.contains("pokémon") ||
                lower.contains("mizuhara") ||
                lower.contains("chizuru") ||
                lower.contains("naked") ||
                lower.contains("nude") ||
                lower.contains("hot") ||
                lower.contains("ecchi") ||
                lower.contains("hentai") ||
                lower.contains("nsfw") ||
                lower.contains("sexy") ||
                lower.contains("lewd") ||
                lower.contains("cute") ||
                (lower.contains("character") && (lower.contains("who") || lower.contains("how") || lower.contains("what") || lower.contains("design"))) ||
                (lower.contains("who is") && userQuery.split(" ").size <= 5)

            if (!isImageIntent) return@withContext emptyList()

            // Safe search parameter: "moderate" by default; "off" if adult/nsfw keywords detected
            val isAdultQuery = lower.contains("naked") ||
                lower.contains("nude") ||
                lower.contains("nudity") ||
                lower.contains("hot") ||
                lower.contains("adult") ||
                lower.contains("ecchi") ||
                lower.contains("hentai") ||
                lower.contains("bikini") ||
                lower.contains("swimsuit") ||
                lower.contains("swimsuits") ||
                lower.contains("lewd") ||
                lower.contains("r18") ||
                lower.contains("r-18") ||
                lower.contains("nsfw") ||
                lower.contains("sexy") ||
                lower.contains("lingerie") ||
                lower.contains("undressed") ||
                lower.contains("ero") ||
                lower.contains("erotic") ||
                lower.contains("cleavage") ||
                lower.contains("oppai") ||
                lower.contains("boobs") ||
                lower.contains("ass") ||
                lower.contains("thighs") ||
                lower.contains("uncensored") ||
                lower.contains("strip") ||
                lower.contains("bare") ||
                lower.contains("seductive") ||
                lower.contains("bunny girl")

            val safeParam = if (isAdultQuery) "off" else "moderate"

            // 2. Query sanitization: extract clean search keywords directly from user query
            val cleanQuery = userQuery
                .replace(Regex("""(?i)\b(show|tell|me|please|find|can|you|what|does|look|like|pictures?|picitres?|pictuers?|images?|iamges?|photos?|pic|pics|picts?|search|give|of|a|an|the|wallpaper|wallpapers|gallery)\b"""), " ")
                .replace(Regex("""[?!#*"]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

            val cleanAnime = animeTitle
                .replace(Regex("""(?i)\b(null|undefined)\b"""), "")
                .replace(Regex("""\s*\((?:TV|Dub|Sub|Season\s*\d+|Movie)\)"""), "")
                .trim()

            // Determine target query: ALWAYS figure out and attach the anime name along with the search term
            // to ensure search results are specifically from that anime and avoid wrong images
            val targetSearchQuery = when {
                cleanQuery.isNotBlank() && cleanAnime.isNotBlank() -> {
                    if (cleanQuery.contains(cleanAnime, ignoreCase = true)) {
                        cleanQuery
                    } else {
                        "$cleanAnime $cleanQuery".trim()
                    }
                }
                cleanQuery.isNotBlank() -> cleanQuery
                cleanAnime.isNotBlank() -> cleanAnime
                else -> "anime"
            }

            // 3. 100% Lovable API: fetch search images directly from web-image-sleuth Lovable API with limit 20
            var results = queryLovableApi(targetSearchQuery, limit = 20, safe = safeParam)

            // Optional fallback: If combined query returned 0 results, try cleanAnime alone or cleanQuery alone
            if (results.isEmpty() && cleanQuery.isNotBlank() && targetSearchQuery != cleanQuery) {
                results = queryLovableApi(cleanQuery, limit = 20, safe = safeParam)
            }
            if (results.isEmpty() && cleanAnime.isNotBlank() && targetSearchQuery != cleanAnime) {
                results = queryLovableApi(cleanAnime, limit = 20, safe = safeParam)
            }

            return@withContext results
        } catch (_: Exception) {
            return@withContext emptyList()
        }
    }

    private val gameArtCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

    suspend fun fetchGameCoverAndBanner(gameTitle: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val clean = gameTitle
            .replace(Regex("""\s*\(\d{4}(?:/\d{4})?\)"""), "")
            .substringBefore(" or ")
            .substringBefore(" / ")
            .substringBefore(" and ")
            .substringBefore(" & ")
            .trim()
        if (clean.isBlank()) return@withContext Pair("", "")
        val cacheKey = clean.lowercase()
        gameArtCache[cacheKey]?.let { return@withContext it }

        val collectedUrls = mutableListOf<String>()

        // 100% Lovable API: Try with "$clean anime game" first for precision, then clean directly
        val queryCandidates = listOf("$clean anime game", clean)
        for (q in queryCandidates) {
            val lovableImages = queryLovableApi(q, limit = 10)
            for (img in lovableImages) {
                if (img.imageUrl.isNotBlank() && img.imageUrl !in collectedUrls) {
                    collectedUrls.add(img.imageUrl)
                    if (collectedUrls.size >= 2) break
                }
            }
            if (collectedUrls.isNotEmpty()) break
        }

        val cover = collectedUrls.getOrNull(0) ?: ""
        val banner = collectedUrls.getOrNull(1) ?: cover
        val result = Pair(cover, banner)
        if (cover.isNotBlank()) gameArtCache[cacheKey] = result
        return@withContext result
    }
}

/**
 * Real-time YouTube video search fetcher for anime trailers, fight scenes, clips, and moments.
 * Deep links directly to the official YouTube app or browser without embedding.
 */
object YouTubeVideoSearchFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * YOUTUBE RELEVANCE RULE (single source of truth - keep all gating here):
     *  1. A YouTube video is attached ONLY when the user explicitly asks for video content
     *     (the words video / youtube / trailer / teaser / pv / amv / clip, or a fight/battle scene).
     *  2. A request to play an episode ("play episode 5", "start ep 2 of season 3", ...) is a
     *     playback request: it gets the episode play button and NEVER a YouTube video,
     *     unless the same message also explicitly asks for a video/trailer/clip.
     *  3. A YouTube video and an episode play button are never bundled in one response
     *     (the response builder and the renderer both enforce this).
     *  4. Anything else (general chat, watch order, recommendations, app help) gets no video.
     */
    fun isPlayEpisodeRequest(userQuery: String): Boolean {
        val lower = userQuery.lowercase()
        return Regex("""\b(play|start|begin|put on|resume|card|episode card)\b.{0,60}\b(episode|ep|eps|e\d+)\b""").containsMatchIn(lower) ||
            Regex("""\b(episode|ep)\s*\d+\b.{0,40}\b(play|start|card)\b""").containsMatchIn(lower) ||
            Regex("""\b(episode\s*card|create\s*(?:an?\s*)?episode\s*card)\b""").containsMatchIn(lower) ||
            Regex("""^\s*(play|start|begin|put on)\b""").containsMatchIn(lower)
    }

    suspend fun searchVideos(animeTitle: String, userQuery: String): List<AiVideoResult> = withContext(Dispatchers.IO) {
        try {
            val lower = userQuery.lowercase()

            // Relevance rule #2: playback requests never get a YouTube video unless one is explicitly requested.
            val explicitlyAsksForVideo = lower.contains("video") || lower.contains("youtube") ||
                lower.contains("trailer") || lower.contains("teaser") || lower.contains("clip") ||
                Regex("\\b(pv|amv|yt)\\b").containsMatchIn(lower)
            if (isPlayEpisodeRequest(userQuery) && !explicitlyAsksForVideo) return@withContext emptyList()

            // Guardrail 1: ONLY suppress YouTube video suggestions if user is asking about app settings, UI customization, or My Space
            val isAppGuideQuery = run {
                val mentionsMySpace = lower.contains("my space") || lower.contains("myspace")
                val isHowToSettings = (lower.contains("how to") || lower.contains("how do i") || lower.contains("how can i") || lower.contains("where to") || lower.contains("where is") || lower.contains("where can i") || lower.contains("how do we")) &&
                    (lower.contains("setting") || lower.contains("settings") || lower.contains("change") || lower.contains("enable") || lower.contains("disable") || lower.contains("turn on") || lower.contains("turn off") || lower.contains("set") || lower.contains("configure")) &&
                    (lower.contains("theme") || lower.contains("color") || lower.contains("colour") || lower.contains("seekbar") || lower.contains("player") || lower.contains("dns") || lower.contains("subtitle") || lower.contains("audio") || lower.contains("cache") || lower.contains("download") || lower.contains("backup") || lower.contains("restore") || lower.contains("profile") || lower.contains("avatar") || lower.contains("sync") || lower.contains("server"))
                val isAppTroubleshoot = (lower.contains("firefly") || lower.contains("animebox") || lower.contains("in app") || lower.contains("the app")) &&
                    (lower.contains("bug") || lower.contains("crash") || lower.contains("ticket") || lower.contains("support") || lower.contains("update") || lower.contains("version") || lower.contains("setting") || lower.contains("settings"))
                val isDirectSetting = lower.contains("seekbar color") || lower.contains("timeline color") || lower.contains("dns routing") || lower.contains("clear cache") || lower.contains("subtitles appearance") || lower.contains("backup & data") || lower.contains("backup data") || lower.contains("low performance mode")

                mentionsMySpace || isHowToSettings || isAppTroubleshoot || isDirectSetting
            }

            if (isAppGuideQuery && !lower.contains("youtube") && !lower.contains("video")) return@withContext emptyList()

            // Only attach video cards when the user asks for video content or for an explicit video guide.
            val isWatchOrder = lower.contains("watch order") ||
                lower.contains("chronological") ||
                lower.contains("timeline") && (lower.contains("watch") || lower.contains("anime")) ||
                lower.contains("where to start") && lower.contains("watch") ||
                lower.contains("how to watch")

            val isExplicitVideoMention = lower.contains("video") ||
                lower.contains("videos") ||
                lower.contains("youtube") ||
                lower.contains("yt ")

            val isAnimeVideoQuery = lower.contains("trailer") ||
                Regex("\\b(pv|teaser|amv)\\b").containsMatchIn(lower) ||
                ((lower.contains("fight") || lower.contains("battle")) &&
                    (lower.contains("scene") || lower.contains("clip")))

            // If user explicitly asks for images only (e.g. "show image", "wallpaper"), avoid video cards unless videos also mentioned
            val isPureImageQuery = (lower.contains("image") || lower.contains("picture") || lower.contains("photo") || lower.contains("wallpaper")) &&
                !isExplicitVideoMention && !isAnimeVideoQuery && !isWatchOrder

            if (isPureImageQuery) return@withContext emptyList()

            // Relevance rule #1/#4: watch-order / generic chat never auto-attaches a video; only explicit video asks do.
            val shouldSuggestVideos = isExplicitVideoMention || isAnimeVideoQuery
            if (!shouldSuggestVideos) return@withContext emptyList()

            val cleanQuery = userQuery
                .replace(Regex("""(?i)\b(show|tell|me|please|find|can|you|the|video|videos|clip|moment|watch|youtube)\b"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

            val effectiveKeywords = when {
                isWatchOrder -> "watch order guide"
                lower.contains("trailer") || lower.contains("pv") || lower.contains("teaser") -> "official trailer"
                lower.contains("fight") || lower.contains("battle") || lower.contains("vs") -> "fight scene"
                lower.contains("opening") || lower.contains("op") -> "opening"
                lower.contains("ending") || lower.contains("ed") -> "ending"
                lower.contains("ost") || lower.contains("soundtrack") -> "soundtrack ost"
                lower.contains("scene") || lower.contains("moment") || lower.contains("clip") -> "best scenes"
                lower.contains("review") || lower.contains("recap") || lower.contains("summary") -> "recap explained"
                cleanQuery.isNotBlank() -> cleanQuery
                else -> "official trailer"
            }

            val fullQuery = when {
                animeTitle.isNotBlank() && !effectiveKeywords.contains(animeTitle, ignoreCase = true) -> "$animeTitle $effectiveKeywords".trim()
                else -> effectiveKeywords
            }
            val encodedQuery = URLEncoder.encode(fullQuery, "UTF-8")
            val ytUrl = "https://www.youtube.com/results?search_query=$encodedQuery"

            val req = Request.Builder()
                .url(ytUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val videos = mutableListOf<AiVideoResult>()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val dataMatch = Regex("""var ytInitialData\s*=\s*(\{.+?\});</script>""").find(html)
                        ?: Regex("""window\["ytInitialData"\]\s*=\s*(\{.+?\});</script>""").find(html)

                    if (dataMatch != null) {
                        try {
                            val rootJson = JSONObject(dataMatch.groupValues[1])
                            val contents = rootJson.optJSONObject("contents")
                                ?.optJSONObject("twoColumnSearchResultsRenderer")
                                ?.optJSONObject("primaryContents")
                                ?.optJSONObject("sectionListRenderer")
                                ?.optJSONArray("contents")

                            if (contents != null) {
                                for (i in 0 until contents.length()) {
                                    val section = contents.optJSONObject(i)
                                    val items = section?.optJSONObject("itemSectionRenderer")?.optJSONArray("contents") ?: continue
                                    for (j in 0 until items.length()) {
                                        val v = items.optJSONObject(j)?.optJSONObject("videoRenderer") ?: continue
                                        val vid = v.optString("videoId", "")
                                        if (vid.isBlank() || videos.any { it.videoId == vid }) continue

                                        val title = v.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: cleanQuery
                                        val channel = v.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                                        val duration = v.optJSONObject("lengthText")?.optString("simpleText", "") ?: ""
                                        val thumbs = v.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                        val bestThumb = if (thumbs != null && thumbs.length() > 0) {
                                            thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                                        } else ""
                                        val channelThumbArr = v.optJSONObject("channelThumbnailSupportedRenderers")
                                            ?.optJSONObject("channelThumbnailWithLinkRenderer")
                                            ?.optJSONObject("thumbnail")
                                            ?.optJSONArray("thumbnails")
                                        val channelAvatar = if (channelThumbArr != null && channelThumbArr.length() > 0) {
                                            channelThumbArr.optJSONObject(channelThumbArr.length() - 1)?.optString("url", "") ?: ""
                                        } else ""

                                        videos.add(
                                            AiVideoResult(
                                                videoId = vid,
                                                title = title,
                                                channel = channel,
                                                duration = duration,
                                                thumbnailUrl = bestThumb,
                                                videoUrl = "https://www.youtube.com/watch?v=$vid",
                                                channelAvatarUrl = channelAvatar
                                            )
                                        )
                                        if (videos.size >= 3) break
                                    }
                                    if (videos.size >= 3) break
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // Fallback regex if ytInitialData schema changes
                    if (videos.isEmpty()) {
                        val vidMatches = Regex("""/watch\?v=([a-zA-Z0-9_-]{11})""").findAll(html)
                        for (m in vidMatches) {
                            val vid = m.groupValues[1]
                            if (!videos.any { it.videoId == vid }) {
                                videos.add(
                                    AiVideoResult(
                                        videoId = vid,
                                        title = "$animeTitle - $cleanQuery",
                                        channel = "YouTube",
                                        duration = "",
                                        thumbnailUrl = "https://i.ytimg.com/vi/$vid/hqdefault.jpg",
                                        videoUrl = "https://www.youtube.com/watch?v=$vid"
                                    )
                                )
                            }
                            if (videos.size >= 3) break
                        }
                    }
                }
            }
            return@withContext videos
        } catch (_: Exception) {
            return@withContext emptyList()
        }
    }
}

/**
 * Character image fetcher using MyAnimeList / Jikan v4 API.
 * Jikan provides complete character lists and character images directly from MyAnimeList.
 */
object MalCharacterFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun getCharacterImage(characterName: String): String = withContext(Dispatchers.IO) {
        val trimmed = characterName.trim()
        if (trimmed.isBlank()) return@withContext ""
        val cacheKey = trimmed.lowercase()
        cache[cacheKey]?.let { return@withContext it }

        // Clean name (remove extra bracketed notes or series tags e.g. "Saber (Artoria Pendragon)" -> "Artoria Pendragon")
        val cleanName = if (trimmed.contains("(") && trimmed.contains(")")) {
            val inside = trimmed.substringAfter("(").substringBefore(")").trim()
            val before = trimmed.substringBefore("(").trim()
            if (inside.length >= 3 && !inside.contains("fate", ignoreCase = true)) inside else before
        } else trimmed

        try {
            val encoded = URLEncoder.encode(cleanName, "UTF-8")
            val url = "https://api.jikan.moe/v4/characters?q=$encoded&limit=3"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val root = JSONObject(body)
                    val dataArr = root.optJSONArray("data")
                    if (dataArr != null && dataArr.length() > 0) {
                        for (i in 0 until dataArr.length()) {
                            val charObj = dataArr.optJSONObject(i) ?: continue
                            val images = charObj.optJSONObject("images")
                            val jpg = images?.optJSONObject("jpg")
                            val webp = images?.optJSONObject("webp")
                            val imgUrl = jpg?.optString("image_url", "")?.takeIf { it.isNotBlank() && !it.contains("apple-touch-icon") }
                                ?: webp?.optString("image_url", "")?.takeIf { it.isNotBlank() }
                                ?: ""
                            if (imgUrl.isNotBlank()) {
                                cache[cacheKey] = imgUrl
                                return@withContext imgUrl
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback to AniList if Jikan was rate limited or empty
        try {
            val aniData = AniListClient.searchCharacter(trimmed)
            if (aniData != null && aniData.imageUrl.isNotBlank()) {
                cache[cacheKey] = aniData.imageUrl
                return@withContext aniData.imageUrl
            }
        } catch (_: Exception) {}

        ""
    }
}

/**
 * Real-time Google and Web AI Search Overview fetcher.
 * Uses DuckDuckGo HTML snippets and Wikipedia Character/Episode API for 100% reliable context without CAPTCHAs.
 */
object GoogleSearchOverviewFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun searchGoogleOverview(animeTitle: String, userQuery: String): String? = withContext(Dispatchers.IO) {
        try {
            val lower = userQuery.lowercase()
            val isSearchQuery = lower.contains("episode") ||
                    lower.contains("ep ") ||
                    lower.contains("eps ") ||
                    lower.contains("which ep") ||
                    lower.contains("what ep") ||
                    lower.contains("find ep") ||
                    lower.contains("where does") ||
                    lower.contains("when does") ||
                    lower.contains("fight") ||
                    lower.contains("scene") ||
                    lower.contains("arc") ||
                    lower.contains("transform") ||
                    lower.contains("form") ||
                    lower.contains("die") ||
                    lower.contains("death") ||
                    lower.contains("who is") ||
                    lower.contains("what is") ||
                    lower.contains("dress") ||
                    lower.contains("outfit") ||
                    lower.contains("costume") ||
                    lower.contains("bunny") ||
                    lower.contains("cosplay") ||
                    lower.contains("look like") ||
                    lower.contains("show me") ||
                    lower.contains("image") ||
                    lower.contains("picture") ||
                    lower.contains("photo") ||
                    lower.contains("pokemon") ||
                    lower.contains("how to") ||
                    lower.contains("why does") ||
                    userQuery.split(" ").size >= 2

            if (!isSearchQuery) return@withContext null

            val cleanUserQuery = userQuery
                .replace(Regex("""(?i)\b(show|tell|me|please|find|can|you|what|is|does)\b"""), " ")
                .trim()

            val fullQuery = if (cleanUserQuery.contains(animeTitle, ignoreCase = true)) cleanUserQuery else "$animeTitle $cleanUserQuery".trim()
            val encodedQuery = URLEncoder.encode(fullQuery, "UTF-8")

            val snippets = mutableListOf<String>()

            // 1. DuckDuckGo HTML Search Snippets (100% reliable, zero CAPTCHAs)
            try {
                val ddgUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"
                val ddgReq = Request.Builder()
                    .url(ddgUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .build()

                client.newCall(ddgReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string() ?: ""
                        val matches = Regex("""<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).findAll(html)
                        for (m in matches) {
                            val text = m.groupValues[1]
                                .replace(Regex("""<[^>]*>"""), " ")
                                .replace("&quot;", "\"")
                                .replace("&#39;", "'")
                                .replace("&amp;", "&")
                                .replace(Regex("""\s+"""), " ")
                                .trim()
                            if (text.length >= 25 && !snippets.contains(text)) {
                                snippets.add(text)
                            }
                            if (snippets.size >= 4) break
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Wikipedia Search API Fallback
            if (snippets.size < 2) {
                try {
                    val wikiUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&utf8=&format=json"
                    val wikiReq = Request.Builder()
                        .url(wikiUrl)
                        .header("User-Agent", "AnimeBox/1.0 (https://animebox.app)")
                        .build()

                    client.newCall(wikiReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val jsonStr = resp.body?.string() ?: ""
                            val json = JSONObject(jsonStr)
                            val searchArr = json.optJSONObject("query")?.optJSONArray("search")
                            if (searchArr != null) {
                                for (i in 0 until searchArr.length().coerceAtMost(3)) {
                                    val item = searchArr.getJSONObject(i)
                                    val title = item.optString("title", "")
                                    val rawSnippet = item.optString("snippet", "")
                                    val cleanSnippet = rawSnippet
                                        .replace(Regex("""<[^>]*>"""), " ")
                                        .replace("&quot;", "\"")
                                        .replace("&#39;", "'")
                                        .replace("&amp;", "&")
                                        .replace(Regex("""\s+"""), " ")
                                        .trim()
                                    if (cleanSnippet.length >= 25) {
                                        snippets.add("[$title]: $cleanSnippet")
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (snippets.isNotEmpty()) {
                return@withContext snippets.joinToString("\n• ") { it }
            }

            return@withContext null
        } catch (_: Exception) {
            return@withContext null
        }
    }
}

object WikipediaEpisodeSearcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun searchEpisode(animeTitle: String, userQuery: String): String? = withContext(Dispatchers.IO) {
        try {
            val lower = userQuery.lowercase()
            val isEpQuestion = lower.contains("episode") ||
                    lower.contains("ep ") ||
                    lower.contains("eps ") ||
                    lower.contains("which ep") ||
                    lower.contains("what ep") ||
                    lower.contains("find ep") ||
                    lower.contains("where does") ||
                    lower.contains("when does") ||
                    lower.contains("fight") ||
                    lower.contains("scene") ||
                    lower.contains("arc") ||
                    lower.contains("transform") ||
                    lower.contains("gear 5") ||
                    lower.contains("bankai") ||
                    lower.contains("die") ||
                    lower.contains("death")

            if (!isEpQuestion) return@withContext null

            val stripped = userQuery
                .replace(Regex("""(?i)\b(in|which|what|where|does|did|find|the|an|a|show|tell|me|episode|episodes|ep|anime|season)\b"""), " ")
                .replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
                .trim()
                .replace(Regex("""\s+"""), " ")

            val queryParam = "$animeTitle list of episodes $stripped".trim()
            val encoded = URLEncoder.encode(queryParam, "UTF-8")
            val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&utf8=1&srlimit=3"

            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "AnimeBoxApp/1.0 (contact@animebox.app)")
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val searchArr = json.optJSONObject("query")?.optJSONArray("search") ?: return@withContext null

            val snippets = mutableListOf<String>()
            for (i in 0 until minOf(searchArr.length(), 3)) {
                val item = searchArr.getJSONObject(i)
                val title = item.optString("title", "")
                val rawSnippet = item.optString("snippet", "")
                val cleanSnippet = rawSnippet
                    .replace(Regex("<[^>]*>"), "")
                    .replace("&quot;", "\"")
                    .replace("&#039;", "'")
                    .replace("&amp;", "&")
                    .trim()
                if (cleanSnippet.isNotBlank()) {
                    snippets.add("• [$title]: $cleanSnippet")
                }
            }

            if (snippets.isNotEmpty()) {
                return@withContext snippets.joinToString("\n")
            }
        } catch (_: Exception) {}
        return@withContext null
    }
}



object OpenRouterChatClient {

    private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun sendMessage(
        context: android.content.Context,
        apiKey: String,
        model: String,
        messages: List<AiChatMessage>,
        currentAnimeTitle: String,
        franchiseItems: List<WatchOrderItem>,
        base64Image: String? = null
    ): Result<AiChatMessage> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("OpenRouter API key is missing. Please set your API key."))
        }

        val isFreeMode = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isOpenRouterFreeMode(context) ||
                (model.isNotBlank() && model.contains(":free", ignoreCase = true) && !model.contains("gemini-3.8"))
        val hasImage = !base64Image.isNullOrBlank()

        val candidateModels = mutableListOf<String>()

        if (isFreeMode) {
            if (hasImage) {
                candidateModels.add("google/gemini-2.0-flash-exp:free")
                candidateModels.add("meta-llama/llama-3.2-11b-vision-instruct:free")
                candidateModels.add("qwen/qwen-2-vl-72b-instruct:free")
                candidateModels.add("openrouter/free")
            }
            val requested = model.trim().ifBlank { "inclusionai/ling-3.0-flash-vl:free" }
            if (requested.isNotBlank() && requested.contains(":free") && !candidateModels.contains(requested)) {
                candidateModels.add(requested)
            }
            val validFreeModels = listOf(
                "inclusionai/ling-3.0-flash-vl:free",
                "nex-agi/nex-n2.5-mini:free",
                "nex-agi/nex-n2.5-pro:free",
                "qwen/qwen3.8-27b:free",
                "inclusionai/ling-3.0-flash-sante:free",
                "inclusionai/ling-3.0-flash-fin:free",
                "dots-studio/dots-3-note-preview:free",
                "openrouter/free"
            )
            for (fm in validFreeModels) {
                if (!candidateModels.contains(fm)) {
                    candidateModels.add(fm)
                }
            }
        } else {
            val primaryModel = when {
                hasImage -> "google/gemini-2.0-flash-001"
                model.isBlank() || model.equals("google/gemini-2.0-flash-001", ignoreCase = true) -> "google/gemini-3.8-flash"
                else -> model.trim()
            }
            candidateModels.add(primaryModel)
            if (!candidateModels.contains("google/gemini-2.0-flash-001")) candidateModels.add("google/gemini-2.0-flash-001")
            if (!candidateModels.contains("anthropic/claude-3.5-sonnet")) candidateModels.add("anthropic/claude-3.5-sonnet")
            if (!candidateModels.contains("google/gemini-3.8-flash")) candidateModels.add("google/gemini-3.8-flash")
            if (!candidateModels.contains("google/gemini-3.7-flash")) candidateModels.add("google/gemini-3.7-flash")
            if (!candidateModels.contains("google/gemini-3.5-flash")) candidateModels.add("google/gemini-3.5-flash")
            if (!candidateModels.contains("google/gemini-3.5-flash-lite")) candidateModels.add("google/gemini-3.5-flash-lite")
            if (!candidateModels.contains("openrouter/auto")) candidateModels.add("openrouter/auto")
        }

        var lastError: Exception? = null

        // Concurrently query Google/Web search overview, image search, and YouTube video search without UI lag
        val latestUserMsg = messages.lastOrNull { it.role == "user" }?.text ?: ""
        val (googleOverviewContext, searchImages, searchVideos) = if (latestUserMsg.isNotBlank()) {
            val overviewDeferred = async(Dispatchers.IO) {
                GoogleSearchOverviewFetcher.searchGoogleOverview(currentAnimeTitle, latestUserMsg)
            }
            val imagesDeferred = async(Dispatchers.IO) {
                GoogleImageSearchFetcher.searchImages(currentAnimeTitle, latestUserMsg)
            }
            val videosDeferred = async(Dispatchers.IO) {
                YouTubeVideoSearchFetcher.searchVideos(currentAnimeTitle, latestUserMsg)
            }
            Triple(overviewDeferred.await(), imagesDeferred.await(), videosDeferred.await())
        } else {
            Triple(null, emptyList(), emptyList())
        }

        for (candidateModel in candidateModels) {
            try {
                val systemPrompt = buildSystemPrompt(currentAnimeTitle, franchiseItems, googleOverviewContext)

                val jsonMessages = JSONArray()
                // 1. System Prompt
                jsonMessages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })

                // 2. Chat history (compact: last 5 messages to avoid token bloat & rate limits)
                val recentMessages = messages.takeLast(5)
                for ((mIdx, msg) in recentMessages.withIndex()) {
                    if (msg.role == "user" || msg.role == "assistant") {
                        val isLatestUserWithImage = (mIdx == recentMessages.size - 1 && msg.role == "user" && !base64Image.isNullOrBlank()) ||
                                (!msg.base64Image.isNullOrBlank() && msg.role == "user")
                        val effectiveImage = if (mIdx == recentMessages.size - 1 && !base64Image.isNullOrBlank()) base64Image else msg.base64Image

                        if (isLatestUserWithImage && !effectiveImage.isNullOrBlank()) {
                            val contentArr = JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", msg.text.ifBlank { "Please identify this anime character or anime from the image." })
                                })
                                put(JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().apply {
                                        put("url", "data:image/jpeg;base64,$effectiveImage")
                                    })
                                })
                            }
                            jsonMessages.put(JSONObject().apply {
                                put("role", "user")
                                put("content", contentArr)
                            })
                        } else {
                            jsonMessages.put(JSONObject().apply {
                                put("role", msg.role)
                                put("content", msg.text)
                            })
                        }
                    }
                }

                val requestBodyJson = JSONObject().apply {
                    put("model", candidateModel)
                    put("messages", jsonMessages)
                    put("temperature", 0.3)
                    put("max_tokens", 2500)
                }

                val request = Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("Authorization", "Bearer $cleanKey")
                    .addHeader("HTTP-Referer", "https://animebox.app")
                    .addHeader("X-Title", "AnimeBox")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val respBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val isEndpointNotFound = response.code == 404 || respBody.contains("endpoint", ignoreCase = true)
                    if (isEndpointNotFound && candidateModel != candidateModels.last()) {
                        continue
                    }

                    val errorMsg = when (response.code) {
                        401 -> "Invalid OpenRouter API key (HTTP 401). Please verify your API key."
                        402 -> "OpenRouter account balance is empty (HTTP 402). Check credits or use a free model."
                        429 -> "OpenRouter rate limit reached (HTTP 429). Please update your API key or wait a moment."
                        else -> try {
                            val errObj = JSONObject(respBody).optJSONObject("error")
                            errObj?.optString("message", "Error ${response.code}: ${response.message}")
                                ?: "OpenRouter error (HTTP ${response.code})"
                        } catch (_: Exception) {
                            "OpenRouter server error (HTTP ${response.code})"
                        }
                    }
                    lastError = Exception(errorMsg)
                    continue
                }

                val respJson = JSONObject(respBody)
                val choices = respJson.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    lastError = Exception("No response choices received from Atlas AI.")
                    continue
                }

                val firstChoice = choices.getJSONObject(0)
                val messageObj = firstChoice.optJSONObject("message")
                val rawContent = messageObj?.optString("content", "") ?: ""

                if (rawContent.isBlank()) {
                    lastError = Exception("Received empty response from Atlas AI.")
                    continue
                }

                // Parse structured watch order recommendations, interactive quizzes, and iiDB games
                val parsed = parseWatchOrderMap(rawContent, franchiseItems)

                return@withContext Result.success(
                    AiChatMessage(
                        role = "assistant",
                        text = parsed.cleanText,
                        watchOrderMap = if (searchImages.isNotEmpty()) null else parsed.mapItems.takeIf { it.isNotEmpty() },
                        quizData = parsed.quizData,
                        playAction = parsed.playAction,
                        sectionTitle = parsed.sectionTitle,
                        searchImages = searchImages.takeIf { it.isNotEmpty() },
                        // Relevance rule #3: never bundle a YouTube video with an episode play button.
                        searchVideos = if (parsed.playAction != null) null else searchVideos.takeIf { it.isNotEmpty() }
                    )
                )
            } catch (e: Exception) {
                lastError = e
            }
        }

        Result.failure(lastError ?: Exception("Failed to connect to Atlas AI via OpenRouter."))
    }

    private fun buildSystemPrompt(
        currentAnimeTitle: String,
        franchiseItems: List<WatchOrderItem>,
        wikiEpisodeContext: String? = null
    ): String {
        val sb = StringBuilder()
        sb.appendLine("You are Atlas, an elite anime intelligence, interactive gaming architect, and storyline master.")
        sb.appendLine("The user is currently viewing: \"$currentAnimeTitle\".")
        sb.appendLine()
        sb.appendLine("Verified Database Entries for this franchise (Release Order):")
        for ((idx, item) in franchiseItems.withIndex()) {
            val yearStr = item.year?.let { " ($it)" } ?: ""
            val epStr = item.episodes?.let { " - $it eps" } ?: ""
            val relStr = if (item.relationType.isNotBlank()) " [${item.relationType}]" else ""
            val curTag = if (item.isCurrent) " *CURRENT*" else ""
            sb.appendLine("${idx + 1}. [ID: ${item.id}] \"${item.title}\" (${item.format}$epStr$yearStr$relStr)$curTag")
        }
        sb.appendLine()
        sb.appendLine("RULES & CAPABILITIES:")
        sb.appendLine("1. Identity: You are Atlas. Direct, witty, encyclopedic, and authoritative. Avoid emojis.")
        sb.appendLine("2. Formatting: Use clean numbered steps, bold highlights, and markdown tables. Never use raw '####' hashes or stray '* *' asterisks. NEVER output stray quotation marks (like \"\" or ''), ditto marks, or orphan dots in empty table cells or text.")
        sb.appendLine("3. MARKDOWN TABLES: When comparing anime, ranking genres, showing Pokémon stats, or presenting metadata, ALWAYS format clean markdown tables using | Header 1 | Header 2 | with |---|---| separators. Ensure every table row starts and ends with a '|' and do NOT insert blank lines between table rows.")
        sb.appendLine()
        sb.appendLine("4. INTERACTIVE ANIME & POKEMON GAMES (ONLY when explicitly requested by user or playing games):")
        sb.appendLine("   - CRITICAL RESTRAINT: DO NOT output any ```quiz block when the user is asking normal questions, asking for watch order, comparing anime, asking for explanations, or browsing guides! ONLY generate a ```quiz block if:")
        sb.appendLine("     (a) The user explicitly asks to play a quiz, game, trivia, or riddle (e.g. 'let\\'s play a game', 'quiz me', 'guess the character').")
        sb.appendLine("     (b) Multi-season clarification is needed to select which season\\'s episode to play.")
        sb.appendLine("     NEVER generate useless ABCD quiz choices randomly on standard inquiries!")
        sb.appendLine("   - When a game/quiz is actually requested, provide ONLY ONE 4-choice interactive MCQ block inside a ```quiz code fence:")
        sb.appendLine("   - CRITICAL REQUIREMENTS FOR QUIZ / TRIVIA:")
        sb.appendLine("     1. ALWAYS state the question prominently in your plain text reply AND in the \"question\" field of the JSON.")
        sb.appendLine("     2. You MUST ALWAYS provide EXACTLY 4 choices labeled \"A\", \"B\", \"C\", and \"D\". Never provide fewer than 4 choices.")
        sb.appendLine("     3. Every option must have \"label\" (\"A\", \"B\", \"C\", or \"D\") and \"text\" (the answer option text).")
        sb.appendLine("   ```quiz")
        sb.appendLine("   {")
        sb.appendLine("     \"question\": \"Which Servant in Fate/stay night wields Excalibur?\",")
        sb.appendLine("     \"gameType\": \"character\",")
        sb.appendLine("     \"options\": [")
        sb.appendLine("       {\"label\": \"A\", \"text\": \"Saber (Artoria Pendragon)\"},")
        sb.appendLine("       {\"label\": \"B\", \"text\": \"Archer (EMIYA)\"},")
        sb.appendLine("       {\"label\": \"C\", \"text\": \"Lancer (Cu Chulainn)\"},")
        sb.appendLine("       {\"label\": \"D\", \"text\": \"Rider (Medusa)\"}")
        sb.appendLine("     ]")
        sb.appendLine("   }")
        sb.appendLine("   ```")
        sb.appendLine("   Use ONLY characters with real, established names so the app can fetch official MyAnimeList character artwork!")
        sb.appendLine()
        sb.appendLine("5. ANIME GAMES & ADAPTATIONS:")
        sb.appendLine("   When the user talks about anime video games (e.g. Pokémon, Fate/Extra, Dragon Ball Xenoverse/Sparking, Naruto Storm, Bleach, Persona, Genshin), provide game lore, platforms, gameplay styles, and include them in a ```watch_order block with badge: 'Anime Game'. The app automatically retrieves official high-resolution game box art and visual assets from image search!")
        sb.appendLine()
        sb.appendLine("6. MANDATORY INTERACTIVE VISUAL ROADMAP & SEASON RANKINGS:")
        sb.appendLine("   Whenever explaining watch order, routes, rankings, skip advice, or recommending other anime, ALWAYS append a JSON array inside ```watch_order:")
        sb.appendLine("   ```watch_order")
        sb.appendLine("   [")
        sb.appendLine("     {\"id\": <id_or_0>, \"title\": \"<exact_title>\", \"badge\": \"Rank #1 / Core Canon / Recommended / Anime Game\", \"note\": \"Brief 3-6 word reason\"}")
        sb.appendLine("   ]")
        sb.appendLine("   ```")
        sb.appendLine("   - For franchise entries, seasons, or spinoffs: Look up the exact [ID: ...] from the 'Verified Database Entries for this franchise' list above and use each season's exact respective ID and exact respective Title (e.g. 'Tokyo Ghoul' for Season 1, 'Tokyo Ghoul √A' for Season 2, 'Tokyo Ghoul:re' for Season 3, 'Tokyo Ghoul:re 2nd Season' for Season 4) so each season displays its own authentic cover art and banner. NEVER reuse the base franchise title or ID for all seasons!")
        sb.appendLine("   - For external or other recommended anime not in the franchise list, set id: 0 and provide the full title including season (e.g. 'Attack on Titan Season 3').")
        sb.appendLine()
        sb.appendLine("7. GUARDRAILS & ANIME-FIRST FOCUS:")
        sb.appendLine("   - Reject non-anime questions (e.g. general travel, cooking, tech, politics) politely, UNLESS the user asks to connect it to anime (e.g. 'list top travel destinations and give an anime representing each location'). When connected, answer with anime pairings.")
        sb.appendLine("   - When answering manga questions, explain in text only. NEVER output AniList manga cards (FireFly is an anime streaming app).")
        sb.appendLine("8. IMAGE IDENTIFICATION:")
        sb.appendLine("   When an image is provided, identify the anime character, their anime series, studio, traits, and provide watch cards.")
        sb.appendLine()
        sb.appendLine("9. EPISODE IDENTIFIER, SCENE & CHARACTER OUTFIT LOOKUP (GOOGLE AI SEARCH OVERVIEW):")
        sb.appendLine("   When user asks to find an episode, character, outfit, cosplay, dress (e.g. 'marin kitagawa bunny girl dress'), transformation, fight, or scene:")
        if (!wikiEpisodeContext.isNullOrBlank()) {
            sb.appendLine("   [Verified Real-Time Search & Web Context]:")
            sb.appendLine(wikiEpisodeContext)
            sb.appendLine("   Ground your answer strictly using this verified factual search data! Explain the character, costume/dress details, story event, and exact episode number accurately.")
        }
        sb.appendLine("   Present the answer formatted like a structured Google AI Search Result Overview:")
        sb.appendLine("   - **Subject / Character**: Character name and outfit or scene name")
        sb.appendLine("   - **Season & Episode**: Exact AniList Season Title (from the Verified Database Entries list above) and Exact Episode Number (e.g. 'Season 1 (`[Exact AniList Title]`) — Episode 5')")
        sb.appendLine("   - **Context & Story**: Clear, encyclopedic summary of why they wore it, what happens in the scene, and key character dynamics")
        sb.appendLine("   - When answering image, outfit, swimsuit, dress, or costume queries, DO NOT append a ```watch_order block! The app automatically displays high-resolution anime search images in a dedicated carousel.")
        sb.appendLine("   - Whenever a specific episode is identified or requested (and no multi-season clarification question is needed), ALWAYS append a ```play_episode block with the exact `animeId`, `animeTitle`, and `episodeNum` from the Verified Database Entries list above!")
        sb.appendLine()
        sb.appendLine("10. ANIME GAMES & BLU-RAY / DVD PRICE COMPARISON:")
        sb.appendLine("   When the user asks where to buy anime games, movies, DVDs, Blu-rays, or merchandise:")
        sb.appendLine("   - Always provide a structured comparison table comparing official stores (Steam, Epic Games, GOG, PlayStation Store, Nintendo eShop, Amazon, Crunchyroll Store, CDJapan, AmiAmi).")
        sb.appendLine("   - Format with columns: | Store / Retailer | Edition / Format | Approx Price | Regional Notes / Advantages | Direct Link |")
        sb.appendLine("   - Provide real markdown links (e.g. [Steam](https://store.steampowered.com/search/?term=...), [Epic Games](https://store.epicgames.com/browse?q=...), [GOG](https://www.gog.com/games?query=...), [PlayStation Store](https://store.playstation.com/en-us/search/...), [Nintendo eShop](https://www.nintendo.com/search/#q=...), [Amazon](https://www.amazon.com/s?k=...), [Crunchyroll Store](https://store.crunchyroll.com/search?q=...), [CDJapan](https://www.cdjapan.co.jp/searchuni?q=...), [AmiAmi](https://www.amiami.com/eng/search/list/?s_keywords=...)).")
        sb.appendLine("   - State clearly which platform is cheaper, upcoming seasonal sales, and regional differences.")
        sb.appendLine("   - When mentioning or recommending anime video games (e.g. Pokémon, Fate, Xenoblade, Naruto Storm, Dragon Ball, Persona), append a ```watch_order card with `{\"title\": \"<exact game title>\", \"badge\": \"Anime Game\", \"is_game\": true}`!")
        sb.appendLine()
        sb.appendLine("11. EPISODE PLAYBACK, EPISODE CARDS, MULTI-SEASON CLARIFICATION, AND OUT-OF-RANGE EPISODES:")
        sb.appendLine("   When user asks to play an episode, asks about a finale/premiere/specific episode, creates an episode card, or shows an episode card (e.g., 'play episode 5', 'finale of season 1', 'last episode of season 2', 'create episode card for episode 1', 'play ep 2 of 3rd season', 'play ep 12 of season 2', 'play ep 3'):")
        sb.appendLine("   - A. MULTI-SEASON CLARIFICATION: If the anime has multiple seasons, movies, or franchise entries (e.g. Attack on Titan, Fate, Bleach, Sword Art Online) and the user did not specify which season:")
        sb.appendLine("     DO NOT guess or assume the wrong season. Ask the user directly to clarify:")
        sb.appendLine("     'Which season would you like to play Episode [No] from?' (or if they asked for the finale, 'Which season finale would you like to play?')")
        sb.appendLine("     Append an interactive 4-choice ```quiz block with the season names (e.g. 'Season 1', 'Season 2', 'Season 3', 'Final Season') so the user can tap and specify instantly!")
        sb.appendLine("     Word the question plainly, with no emoji and no quiz/game phrasing, and keep each option short (just the season or entry name).")
        sb.appendLine("   - YOUTUBE RELEVANCE RULE: When the user asks to play an episode, respond with the episode play button only. Never mention, link, or attach a YouTube video, trailer, or clip in that response, and never combine a YouTube video with an episode play button. Only provide a YouTube video, trailer, or clip when the user explicitly asks for one or it is directly relevant to what they asked.")
        sb.appendLine("   - B. OUT-OF-RANGE / INVALID EPISODE: If the user requests an episode number that is incorrect, does not exist, or exceeds the total episodes for that anime:")
        sb.appendLine("     DO NOT generate an episode play button! Tell the user clearly:")
        sb.appendLine("     'This series may not have Episode [No]. [Show Name] only has [X] episodes. Note: I could make a mistake with episode counts or information, so here is the direct anime detail page where you can check the episode list for yourself.'")
        sb.appendLine("     Append a ```watch_order card pointing directly to that show's detail page (using its ID if known)!")
        sb.appendLine("   - C. VALID EPISODE (INCLUDING FINALE / LAST EPISODE / PREMIERE): If the episode exists and the season is specified or clear:")
        sb.appendLine("     1) ALWAYS explicitly write both the exact AniList Season Title (from 'Verified Database Entries for this franchise' above) and the exact Episode Number (e.g. 'Episode 12') in your text reply.")
        sb.appendLine("     2) FINALE / LAST EPISODE RULE: If the user asks for the 'finale', 'season finale', 'final episode', 'last episode', or 'ending episode' of a season, NEVER output Episode 1! Look up that season's total episode count (`X eps`) in the 'Verified Database Entries for this franchise' list above, state `Episode X` in your text, and set `\"episodeNum\": X` in the ```play_episode block!")
        sb.appendLine("     3) SEASON VS EPISODE RULE: NEVER confuse a season number (like 'Season 1' or 'Season 2') with an episode number (`Episode 1` or `Episode 2`)! If the user selects 'Season 2' to clarify a previous request for Episode 7 or the finale, use Season 2's AniList entry and the requested Episode 7 (or Season 2's final episode count), NOT Episode 2!")
        sb.appendLine("     4) EXACT ANILIST SEASON MATCHING: Match the requested season (Season 1, Season 2, Season 3, Part 2, Movie, etc.) against the 'Verified Database Entries for this franchise' listed above, and use that specific season's exact `ID` for `animeId` and exact title for `animeTitle`! Note that Season 1 is the first main TV entry in the franchise list (even if its AniList title does not literally contain the words 'Season 1').")
        sb.appendLine("     ```play_episode")
        sb.appendLine("     {")
        sb.appendLine("       \"animeId\": <exact_verified_id_from_franchise_list_above_matching_requested_season>,")
        sb.appendLine("       \"animeTitle\": \"<exact_title_from_franchise_list>\",")
        sb.appendLine("       \"episodeNum\": <exact_episode_number_never_default_to_1_for_finale>,")
        sb.appendLine("       \"isOutOfRange\": false,")
        sb.appendLine("       \"maxEpisodes\": <total_episodes_or_null>")
        sb.appendLine("     }")
        sb.appendLine("     ```")
        sb.appendLine("12. POKÉMON SPRITES & EVOLUTION CHAINS:")
        sb.appendLine("   When the user asks about Pokémon details, rankings, evolution lines, stats, or type matchups:")
        sb.appendLine("   - For Evolution Lines: Format clean evolution steps on a single inline markdown line with sprite image tags and arrows `➔` (e.g. `![Pichu](https://play.pokemonshowdown.com/sprites/gen5/pichu.png) Pichu ➔ ![Pikachu](https://play.pokemonshowdown.com/sprites/gen5/pikachu.png) Pikachu ➔ ![Raichu](https://play.pokemonshowdown.com/sprites/gen5/raichu.png) Raichu`).")
        sb.appendLine("   - For Stats & Tables: Include sprite image tags inside markdown comparison table cells alongside Stage, Type, Base Stats, Abilities, and Tier rankings! NEVER place stray quotation marks (such as \"\" or ''), ditto marks, or orphan dots next to sprite images or in empty table cells.")
        sb.appendLine()
        sb.appendLine("13. FIREFLY APP NAVIGATOR & \"MY SPACE\" USER GUIDE (Authoritative In-App Assistance):")
        sb.appendLine("   You are the official in-app AI assistant for FireFly! When a user asks how to do anything in the app, where to find a feature, how to change settings, or how to troubleshoot issues, guide them directly using the options in **My Space** (Tab 4 on the bottom navigation bar):")
        sb.appendLine("   - **How to Open My Space**: Tap the user profile avatar icon (Tab 4) on the bottom navigation bar from any screen.")
        sb.appendLine("   - **Change Profile Avatar / Custom Picture**: Go to **My Space** > at the top tap **Profile Picture** or **Upload Custom** / **Gallery** to select an image from your device, or tap the pencil edit badge on the avatar.")
        sb.appendLine("   - **Animated Avatar Decorations (Discord-Style)**: Go to **My Space** > under **Animated Avatar Decoration**, tap any animated frame to preview and equip it around your profile picture.")
        sb.appendLine("   - **Switch or Create Profiles**: Go to **My Space** > tap **Switch Profile** at the top right to switch, create, edit names, or delete viewer profiles.")
        sb.appendLine("   - **AniList Sync**: Go to **My Space** > tap **AniList Sync** > log into your AniList account for two-way library, watchlist, and episode progress sync.")
        sb.appendLine("   - **Fix Buffering / Video Won't Load / ISP Throttling**: Go to **My Space** > tap **Network & DNS Routing** > change from System Default to **Cloudflare DoH (1.1.1.1)**, **Google Public DNS (8.8.8.8)**, or **AdGuard DNS**.")
        sb.appendLine("   - **Preferred Audio & Subtitle Language**: Go to **My Space** > tap **Audio & Subtitle Languages** to set default audio (Japanese, English Dub, Hindi, etc.) and preferred subtitle language.")
        sb.appendLine("   - **Server Priority**: Go to **My Space** > tap **Server Priority** to configure preferred primary servers for Sub, Dub, Hard Sub, and Hindi.")
        sb.appendLine("   - **Subtitle Appearance & Styling**: Go to **My Space** > tap **Subtitle Appearance** to adjust font size, colors, background opacity, outline thickness, or apply style presets.")
        sb.appendLine("   - **Cloud AI Transcribe (Speech-to-Text Subtitles)**: Go to **My Space** > tap **Cloud AI Transcribe** to configure your Groq Whisper API key for real-time AI subtitles.")
        sb.appendLine("   - **Atlas Watch Order Guide Key**: Go to **My Space** > tap **Atlas Watch Order Guide** to configure your OpenRouter API key for Atlas AI chat.")
        sb.appendLine("   - **Downloads & Storage / Clear Cache**: Go to **My Space** > tap **Downloads & Cache Storage** to view internal storage, toggle Wi-Fi only downloads, or clear cached images and streams.")
        sb.appendLine("   - **Player Gestures & Seek Time**: Go to **My Space** > tap **Player & Gesture Controls** to set double-tap seek duration (5s to 30s) and swipe gestures for brightness & volume.")
        sb.appendLine("   - **Player Timeline Seekbar Color**: Go to **My Space** > tap **Player Timeline Color** to pick your favorite seekbar color (Crimson Red, Ocean Cyan, Emerald Green, Golden Amber, Pure White, Lavender Purple).")
        sb.appendLine("   - **Episode Screenshot Camera Button**: Go to **My Space** > toggle **Episode Screenshots** to show/hide the snapshot camera button in player controls.")
        sb.appendLine("   - **Auto-Skip Opening/Ending (Intro/Outro)**: Go to **My Space** > toggle **Auto-Skip Intro / Outro** to ON.")
        sb.appendLine("   - **Autoplay Next Episode**: Go to **My Space** > toggle **Autoplay Next Episode**.")
        sb.appendLine("   - **Blur Episode Spoilers**: Go to **My Space** > toggle **Blur Episode Spoilers** to hide spoiler thumbnails in episode lists.")
        sb.appendLine("   - **Low Performance Mode**: Go to **My Space** > toggle **Low Performance Mode** for budget phones (disables blur shaders & heavy animations).")
        sb.appendLine("   - **Clustered Watch History**: Go to **My Space** > toggle **Clustered Watch History** (all watched episodes vs clean mode with latest unfinished episode).")
        sb.appendLine("   - **Backup & Restore Data**: Go to **My Space** > tap **Backup & Data Management** > tap **Export Data to JSON** to back up bookmarks, history, and settings, or **Import Data** to restore.")
        sb.appendLine("   - **Anime Release Schedule / Airing Calendar**: Go to **My Space** > tap **Anime Release Schedule** (or tap the **Schedule** tab on the bottom bar).")
        sb.appendLine("   - **Anime News Feed**: Go to **My Space** > tap **Anime News Feed** to read stories from Anime Corner.")
        sb.appendLine("   - **Anime Games Directory**: Go to **My Space** > tap **Anime Games** to browse popular anime mobile and gacha games with Play Store links.")
        sb.appendLine("   - **Digital Wellbeing & Screen Time**: Go to **My Space** > tap **App Activity Details** to see daily watch time and anime breakdown.")
        sb.appendLine("   - **Support Tickets & Feedback**: Go to **My Space** > tap **Support & Feedback Tickets** to submit bug reports, suggestions, or chat with support.")
        sb.appendLine("   - **Check for App Updates**: Go to **My Space** > tap **FireFly Updater** > tap **CHECK** to install the latest GitHub releases.")
        sb.appendLine("   - **Theme Accent Color**: Go to **My Space** > tap **Theme Accent Color** to change the app's color palette.")
        sb.appendLine("   Whenever a user asks a 'how-to', 'where is', or troubleshooting question, structure your reply with numbered steps (e.g. 'Step 1: Go to **My Space**...', 'Step 2: Tap **...**'). Be clear, helpful, and concise!")
        sb.appendLine()
        sb.appendLine("MAIN FOCUS: While you can answer games, trivia, quizzes, and Pokémon lore/sprites, your primary mastery is WATCH ORDER GUIDES, canon watch sequences, release vs chronological timelines, and franchise storylines.")
        return sb.toString()
    }

    fun extractSeasonNumber(text: String): Int? {
        if (text.isBlank()) return null
        val lower = text.lowercase()
        val match = Regex("""\b(?:season\s*(\d+)|(\d+)(?:st|nd|rd|th)?\s*season|part\s*(\d+)|cour\s*(\d+)|s(\d+)\b|s(\d+)e\d+)""", RegexOption.IGNORE_CASE).find(lower)
        if (match != null) {
            for (idx in 1..6) {
                val g = match.groupValues.getOrNull(idx).orEmpty()
                if (g.isNotEmpty()) return g.toIntOrNull()
            }
        }
        val wordMap = mapOf(
            "one" to 1, "first" to 1, "1st" to 1,
            "two" to 2, "second" to 2, "2nd" to 2,
            "three" to 3, "third" to 3, "3rd" to 3,
            "four" to 4, "fourth" to 4, "4th" to 4,
            "five" to 5, "fifth" to 5, "5th" to 5,
            "six" to 6, "sixth" to 6, "6th" to 6,
            "seven" to 7, "seventh" to 7, "7th" to 7,
            "eight" to 8, "eighth" to 8, "8th" to 8,
            "nine" to 9, "ninth" to 9, "9th" to 9,
            "ten" to 10, "tenth" to 10, "10th" to 10
        )
        val wordMatch = Regex("""\b(?:season\s*(one|two|three|four|five|six|seven|eight|nine|ten|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth)|(one|two|three|four|five|six|seven|eight|nine|ten|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|1st|2nd|3rd|4th|5th|6th|7th|8th|9th|10th)\s*season)\b""", RegexOption.IGNORE_CASE).find(lower)
        if (wordMatch != null) {
            val w = wordMatch.groupValues[1].ifEmpty { wordMatch.groupValues[2] }.lowercase()
            wordMap[w]?.let { return it }
        }
        val romanMatch = Regex("""\bseason\s*(ii|iii|iv|v|vi)\b""", RegexOption.IGNORE_CASE).find(lower)
        if (romanMatch != null) {
            when (romanMatch.groupValues[1].lowercase()) {
                "ii" -> return 2
                "iii" -> return 3
                "iv" -> return 4
                "v" -> return 5
                "vi" -> return 6
            }
        }
        if (Regex("""\bfinal\s*season\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) return 99
        return null
    }

    fun extractEpisodeNumber(
        userText: String,
        aiText: String,
        seasonTotalEpisodes: Int? = null
    ): Int? {
        fun stripSeasonRefs(input: String): String {
            return input
                .replace(Regex("""\b(?:season\s*(?:\d+|one|two|three|four|five|six|seven|eight|nine|ten|first|second|third|fourth|fifth|ii|iii|iv|v)|(?:\d+(?:st|nd|rd|th)?|first|second|third|fourth|fifth|one|two|three|four|five)\s*season|part\s*\d+|cour\s*\d+|final\s*season)\b""", RegexOption.IGNORE_CASE), " ")
        }

        val cleanUser = stripSeasonRefs(userText)
        val cleanAi = stripSeasonRefs(aiText)

        // 1. Check explicit SxEy or Episode/Ep number in user prompt first, then AI text
        val epPatterns = listOf(
            Regex("""\bs\d+\s*e(\d+)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:episode|ep\.?|eps\.?)\s*#?(\d+)\b""", RegexOption.IGNORE_CASE)
        )
        for (pat in epPatterns) {
            val mUser = pat.find(userText)?.groupValues?.get(1)?.toIntOrNull()
            if (mUser != null && mUser > 0) return mUser
        }
        for (pat in epPatterns) {
            val mAi = pat.find(cleanAi)?.groupValues?.get(1)?.toIntOrNull()
            if (mAi != null && mAi > 0) return mAi
        }

        // 2. Check word episode numbers (e.g., "episode one", "episode two")
        val epWordMap = mapOf(
            "one" to 1, "first" to 1, "1st" to 1,
            "two" to 2, "second" to 2, "2nd" to 2,
            "three" to 3, "third" to 3, "3rd" to 3,
            "four" to 4, "fourth" to 4, "4th" to 4,
            "five" to 5, "fifth" to 5, "5th" to 5,
            "six" to 6, "sixth" to 6, "6th" to 6,
            "seven" to 7, "seventh" to 7, "7th" to 7,
            "eight" to 8, "eighth" to 8, "8th" to 8,
            "nine" to 9, "ninth" to 9, "9th" to 9,
            "ten" to 10, "tenth" to 10, "10th" to 10,
            "eleven" to 11, "eleventh" to 11, "11th" to 11,
            "twelve" to 12, "twelfth" to 12, "12th" to 12
        )
        val wordEpRegex = Regex("""\b(?:episode|ep\.?)\s*(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth)\b""", RegexOption.IGNORE_CASE)
        wordEpRegex.find(cleanUser)?.groupValues?.get(1)?.lowercase()?.let { epWordMap[it] }?.let { return it }
        wordEpRegex.find(cleanAi)?.groupValues?.get(1)?.lowercase()?.let { epWordMap[it] }?.let { return it }

        // 3. Check finale / final episode / last episode keywords
        val combinedLower = "$cleanUser $cleanAi".lowercase()
        val isFinale = Regex("""\b(?:finale|final\s+episode|last\s+episode|ending\s+episode|season\s+finale|series\s+finale|concluding\s+episode)\b""", RegexOption.IGNORE_CASE).containsMatchIn(combinedLower)
        if (isFinale) {
            if (seasonTotalEpisodes != null && seasonTotalEpisodes > 0) {
                return seasonTotalEpisodes
            }
            // Also check if AI mentioned "(X eps)" or "X episodes"
            val totalMatch = Regex("""\b(\d+)\s*(?:episodes|eps)\b""", RegexOption.IGNORE_CASE).find(cleanAi)?.groupValues?.get(1)?.toIntOrNull()
            if (totalMatch != null && totalMatch > 0) return totalMatch
        }

        // 4. Check premiere / first episode / pilot keywords
        val isPremiere = Regex("""\b(?:premiere|first\s+episode|1st\s+episode|opening\s+episode|pilot|beginning)\b""", RegexOption.IGNORE_CASE).containsMatchIn(combinedLower)
        if (isPremiere) return 1

        return null
    }

    fun matchFranchiseSeason(
        targetTitle: String,
        contextText: String,
        franchiseItems: List<WatchOrderItem>
    ): WatchOrderItem? {
        if (franchiseItems.isEmpty()) return null

        val tvSeasons = franchiseItems.filter {
            it.format.equals("TV", ignoreCase = true) ||
            it.format.equals("TV_SHORT", ignoreCase = true) ||
            it.format.equals("ONA", ignoreCase = true) ||
            ((it.episodes ?: 0) > 1 && !it.format.equals("MOVIE", ignoreCase = true) && !it.format.equals("OVA", ignoreCase = true) && !it.format.equals("SPECIAL", ignoreCase = true))
        }.ifEmpty { franchiseItems }

        val sNum = extractSeasonNumber(targetTitle) ?: extractSeasonNumber(contextText)

        // 1. Direct exact title match first
        if (targetTitle.isNotBlank()) {
            val exact = franchiseItems.find { it.title.equals(targetTitle.trim(), ignoreCase = true) }
            if (exact != null) {
                val exactSNum = extractSeasonNumber(exact.title)
                if (sNum == null || exactSNum == sNum || (sNum == 1 && exactSNum == null && exact.id == tvSeasons.firstOrNull()?.id)) {
                    return exact
                }
            }
        }

        // 2. Match by explicit Season Number (handles Season 1 where AniList title has no "Season 1" suffix, as well as Season 2+)
        if (sNum != null) {
            if (sNum == 99) {
                val finalMatch = franchiseItems.find { extractSeasonNumber(it.title) == 99 || it.title.contains("Final", ignoreCase = true) }
                    ?: tvSeasons.lastOrNull()
                if (finalMatch != null) return finalMatch
            }
            val explicitSeasonMatch = franchiseItems.find { item ->
                extractSeasonNumber(item.title) == sNum
            }
            if (explicitSeasonMatch != null) return explicitSeasonMatch

            if (sNum == 1) {
                val firstTvSeason = tvSeasons.firstOrNull { extractSeasonNumber(it.title) == null }
                    ?: tvSeasons.firstOrNull()
                if (firstTvSeason != null) return firstTvSeason
            } else if (sNum >= 2 && sNum <= tvSeasons.size) {
                return tvSeasons[sNum - 1]
            }
        }

        // 3. Substring / longest title match when targetTitle is provided
        if (targetTitle.isNotBlank()) {
            val cleanTarget = targetTitle.trim()
            val containsMatch = franchiseItems
                .filter { it.title.contains(cleanTarget, ignoreCase = true) || cleanTarget.contains(it.title, ignoreCase = true) }
                .maxByOrNull { it.title.length }
            if (containsMatch != null) return containsMatch
        }

        // 4. Check if contextText mentions any exact franchise item title (longest match first so "Attack on Titan Season 2" beats "Attack on Titan")
        val mentionedInContext = franchiseItems
            .filter { it.title.length >= 4 && contextText.contains(it.title, ignoreCase = true) }
            .maxByOrNull { it.title.length }
        if (mentionedInContext != null) return mentionedInContext

        // 5. Movie or film match
        val lowerContext = (targetTitle + " " + contextText).lowercase()
        if (lowerContext.contains("movie") || lowerContext.contains("film")) {
            val movieItem = franchiseItems.find { it.format.equals("MOVIE", ignoreCase = true) || it.title.contains("Movie", ignoreCase = true) }
            if (movieItem != null) return movieItem
        }

        return null
    }

    private suspend fun parseWatchOrderMap(
        rawContent: String,
        franchiseItems: List<WatchOrderItem>
    ): ParsedAiPayload {
        val franchiseMap = franchiseItems.associateBy { it.id }
        val items = mutableListOf<WatchOrderMapItem>()
        var cleanText = rawContent
        var quizData: AiQuizData? = null
        var playAction: AiPlayEpisodeAction? = null

        // 1. Parse interactive quiz block if present (strip ALL quiz blocks, including ```json and unfenced quiz JSON so raw JSON never leaks!)
        val quizRegex = Regex("""```(?:quiz|game|json)?\s*(\{\s*[\s\S]*?"(?:options|gameType)"[\s\S]*?\})\s*```""", RegexOption.IGNORE_CASE)
        val allQuizMatches = quizRegex.findAll(rawContent).toList()
        for (quizMatch in allQuizMatches) {
            cleanText = cleanText.replace(quizMatch.value, "").trim()
            if (quizData == null) {
                try {
                    val quizJson = JSONObject(quizMatch.groupValues[1])
                    var qText = quizJson.optString("question", "").trim()
                    val gType = quizJson.optString("gameType", "trivia").lowercase()
                    val optsArr = quizJson.optJSONArray("options")
                    val opts = mutableListOf<AiQuizOption>()
                    if (optsArr != null) {
                        for (i in 0 until optsArr.length()) {
                            val optObj = optsArr.optJSONObject(i) ?: continue
                            val label = optObj.optString("label", ('A'.code + i).toChar().toString())
                            val text = optObj.optString("text", "")
                            var img = optObj.optString("image", "")
                            val note = optObj.optString("note", "")

                            // If gameType is character and image missing, fetch verified character artwork directly from MAL / Jikan
                            if (img.isBlank() && (gType.contains("character") || gType.contains("who"))) {
                                try {
                                    val malImg = MalCharacterFetcher.getCharacterImage(text)
                                    if (malImg.isNotBlank()) {
                                        img = malImg
                                    }
                                } catch (_: Exception) {}
                            } else if (img.isBlank() && gType.contains("pokemon")) {
                                val cleanPokemon = text.replace(Regex("""[^a-zA-Z0-9\-]"""), "").lowercase()
                                if (cleanPokemon.isNotBlank()) {
                                    img = "https://play.pokemonshowdown.com/sprites/gen5/$cleanPokemon.png"
                                }
                            } else if (img.isBlank() && (gType.contains("anime") || gType.contains("show"))) {
                                try {
                                    val animeRes = AniListClient.searchAnime(text)
                                    if (!animeRes.isNullOrBlank()) {
                                        val media = JSONObject(animeRes).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")?.optJSONObject(0)
                                        img = media?.optJSONObject("coverImage")?.optString("large", "") ?: ""
                                    }
                                } catch (_: Exception) {}
                            }

                            opts.add(AiQuizOption(label = label, text = text, imageUrl = img, note = note))
                        }
                    }
                    if (opts.isNotEmpty()) {
                        // User Rule: If even one of the 4 options misses a character image, clear images for ALL 4 options so none of them show missing image placeholders.
                        val finalOpts = if (gType.contains("character") || gType.contains("who")) {
                            if (opts.all { it.imageUrl.isNotBlank() }) {
                                opts
                            } else {
                                opts.map { it.copy(imageUrl = "") }
                            }
                        } else {
                            opts
                        }
                        if (qText.isBlank()) {
                            val lastQ = Regex("""([^\n\.\!]+?\?)""").findAll(cleanText).lastOrNull()?.groupValues?.get(1)?.trim()
                            if (!lastQ.isNullOrBlank()) qText = lastQ
                        }
                        quizData = AiQuizData(question = qText, gameType = gType, options = finalOpts)
                    }
                } catch (_: Exception) {}
            }
        }
        // Fallback: If quiz block was unfenced JSON with options array
        if (quizData == null || quizData.options.isEmpty()) {
            val unfencedQuizRegex = Regex("""(?s)(\{\s*"question"[\s\S]*?"options"\s*:\s*\[[\s\S]*?\]\s*\})""")
            val unfencedMatch = unfencedQuizRegex.find(cleanText)
            if (unfencedMatch != null) {
                try {
                    val quizJson = JSONObject(unfencedMatch.groupValues[1])
                    var qText = quizJson.optString("question", "").trim()
                    val gType = quizJson.optString("gameType", "trivia").lowercase()
                    val optsArr = quizJson.optJSONArray("options")
                    val opts = mutableListOf<AiQuizOption>()
                    if (optsArr != null) {
                        for (i in 0 until optsArr.length()) {
                            val optObj = optsArr.optJSONObject(i) ?: continue
                            val label = optObj.optString("label", ('A'.code + i).toChar().toString())
                            val text = optObj.optString("text", "")
                            var img = optObj.optString("image", "")
                            val note = optObj.optString("note", "")
                            if (img.isBlank() && (gType.contains("character") || gType.contains("who"))) {
                                try {
                                    val malImg = MalCharacterFetcher.getCharacterImage(text)
                                    if (malImg.isNotBlank()) img = malImg
                                } catch (_: Exception) {}
                            }
                            opts.add(AiQuizOption(label = label, text = text, imageUrl = img, note = note))
                        }
                    }
                    if (opts.isNotEmpty()) {
                        val finalOpts = if (gType.contains("character") || gType.contains("who")) {
                            if (opts.all { it.imageUrl.isNotBlank() }) opts else opts.map { it.copy(imageUrl = "") }
                        } else opts
                        if (qText.isBlank()) {
                            val lastQ = Regex("""([^\n\.\!]+?\?)""").findAll(cleanText).lastOrNull()?.groupValues?.get(1)?.trim()
                            if (!lastQ.isNullOrBlank()) qText = lastQ
                        }
                        quizData = AiQuizData(question = qText, gameType = gType, options = finalOpts)
                        cleanText = cleanText.replace(unfencedMatch.value, "").trim()
                    }
                } catch (_: Exception) {}
            }
        }
        // Convert common plain Markdown A/B/C/D quiz replies into the same clickable quiz card.
        // Models sometimes ignore the JSON instruction, leaving choices as static text.
        run {
            val choiceStart = Regex("""(?im)^\s*(?:[-*•]\s*)?(?:\*\*)?([A-D])(?:\*\*)?\s*[:.)-]\s*(.+?)\s*\*{0,2}\s*$""")
            val lines = cleanText.lines()
            val starts = lines.mapIndexedNotNull { index, line ->
                choiceStart.find(line)?.let { index to it }
            }
            if (starts.size >= 4) {
                val four = starts.take(4)
                val firstChoiceLine = four.first().first
                val precedingLines = lines.take(firstChoiceLine).asReversed()
                    .map { it.trim().trim('*', '_', '`').trim() }
                    .filter { it.isNotBlank() && !it.equals("Next Question", ignoreCase = true) && !it.startsWith("#") }
                val question = (precedingLines.firstOrNull { it.contains('?') } ?: precedingLines.firstOrNull()).orEmpty()
                if (question.isNotBlank()) {
                    val choices = four.mapIndexed { index, (lineIndex, match) ->
                        val end = four.getOrNull(index + 1)?.first ?: lines.size
                        val wrapped = lines.subList(lineIndex + 1, end)
                            .takeWhile { !choiceStart.containsMatchIn(it) }
                            .map { it.trim().removePrefix("•").trim() }
                            .filter { it.isNotBlank() }
                        AiQuizOption(
                            label = match.groupValues[1].uppercase(),
                            text = (listOf(match.groupValues[2].trim()) + wrapped).joinToString(" ")
                        )
                    }
                    if (quizData == null || quizData.options.isEmpty()) {
                        quizData = AiQuizData(question = question, gameType = "trivia", options = choices)
                    }
                    val questionStartOffset = cleanText.indexOf(question).let { if (it >= 0) it else 0 }
                    val headingStart = cleanText.lastIndexOf("Next Question", questionStartOffset, ignoreCase = true)
                        .takeIf { it >= 0 } ?: questionStartOffset
                    cleanText = cleanText.substring(0, headingStart).trim()
                }
            }
        }
        // Also clean any unclosed or trailing quiz/json fences and dots
        cleanText = cleanText.replace(Regex("""```(?:quiz|game|json)?\s*(\{\s*"question"[\s\S]*?)?$""", RegexOption.IGNORE_CASE), "").trim()

        // 1.8 Parse Play Episode Action block if present
        val playEpRegex = Regex("""```(?:play_episode|play|episode_card|episode|card)\s*(\{[\s\S]*?\})\s*```""", RegexOption.IGNORE_CASE)
        var playEpMatch = playEpRegex.find(cleanText)
        if (playEpMatch == null) {
            val jsonEpRegex = Regex("""```(?:json)?\s*(\{\s*"(?:action|type|animeId|anime_id|episodeNum|episode|ep)"[\s\S]*?\})\s*```""", RegexOption.IGNORE_CASE)
            playEpMatch = jsonEpRegex.find(cleanText)
        }
        if (playEpMatch != null) {
            try {
                val pJson = JSONObject(playEpMatch.groupValues[1])
                val hasEpKey = pJson.has("episodeNum") || pJson.has("episode") || pJson.has("ep") ||
                    pJson.optString("action").contains("episode", ignoreCase = true) ||
                    pJson.optString("type").contains("episode", ignoreCase = true)
                if (hasEpKey || playEpRegex.containsMatchIn(playEpMatch.value)) {
                    var aId = pJson.optInt("animeId", pJson.optInt("anime_id", 0))
                    var aTitle = pJson.optString("animeTitle", pJson.optString("anime_title", ""))
                    var epNum = if (pJson.has("episodeNum")) pJson.optInt("episodeNum", 1)
                        else if (pJson.has("episode")) pJson.optInt("episode", 1)
                        else if (pJson.has("ep")) pJson.optInt("ep", 1)
                        else 1
                    val isOutOfRange = pJson.optBoolean("isOutOfRange", false)
                    val maxEps = if (pJson.has("maxEpisodes")) pJson.optInt("maxEpisodes") else null
                    val clarNeeded = pJson.optBoolean("clarificationNeeded", false)
                    val clarQ = pJson.optString("clarificationQuestion", "")

                    // Resolve franchise item if multi-season
                    var resolvedSeasonItem: WatchOrderItem? = franchiseMap[aId]
                    if (franchiseItems.isNotEmpty()) {
                        val matchedFranchise = matchFranchiseSeason(aTitle, cleanText, franchiseItems)
                        if (matchedFranchise != null) {
                            val currentItem = franchiseMap[aId]
                            val requestedSeason = extractSeasonNumber(aTitle) ?: extractSeasonNumber(cleanText)
                            val currentItemSeason = currentItem?.let {
                                extractSeasonNumber(it.title) ?: if (it.id == franchiseItems.firstOrNull()?.id) 1 else null
                            }
                            if (aId == 0 || currentItem == null || (requestedSeason != null && requestedSeason != currentItemSeason) || (!aTitle.equals(currentItem.title, ignoreCase = true) && aTitle.equals(matchedFranchise.title, ignoreCase = true))) {
                                aId = matchedFranchise.id
                                aTitle = matchedFranchise.title
                                resolvedSeasonItem = matchedFranchise
                            }
                        }
                    }

                    // Safeguard against model outputting episodeNum = 1 or season number when prose specifies finale or another episode number
                    val seasonTotalEps = resolvedSeasonItem?.episodes ?: maxEps
                    val proseEpNum = extractEpisodeNumber("", cleanText, seasonTotalEps)
                    if (proseEpNum != null && proseEpNum > 0) {
                        val isFinaleMentioned = Regex("""\b(?:finale|final\s+episode|last\s+episode|ending\s+episode|season\s+finale|series\s+finale)\b""", RegexOption.IGNORE_CASE).containsMatchIn(cleanText)
                        val reqSeason = extractSeasonNumber(aTitle) ?: extractSeasonNumber(cleanText)
                        if ((isFinaleMentioned && epNum == 1 && proseEpNum > 1) || (reqSeason != null && epNum == reqSeason && proseEpNum != epNum)) {
                            epNum = proseEpNum
                        }
                    }

                    playAction = AiPlayEpisodeAction(
                        animeId = aId,
                        animeTitle = aTitle,
                        episodeNum = epNum,
                        isOutOfRange = isOutOfRange,
                        maxEpisodes = maxEps ?: resolvedSeasonItem?.episodes,
                        clarificationNeeded = clarNeeded,
                        clarificationQuestion = clarQ
                    )
                    cleanText = cleanText.replace(playEpMatch.value, "").trim()
                }
            } catch (_: Exception) {}
        }

        // 2. Try matching code fences (watch_order, json, roadmap, games, iidb, or raw fence)
        val fenceRegex = Regex("""```(?:watch_order|json|roadmap|games?|iidb|anime_games?)?\s*(\[\s*\{[\s\S]*?\}\s*\])\s*```""", RegexOption.IGNORE_CASE)
        val fenceMatch = fenceRegex.find(cleanText)

        var jsonStr: String? = null

        if (fenceMatch != null) {
            jsonStr = fenceMatch.groupValues[1]
            cleanText = cleanText.replace(fenceMatch.value, "").trim()
        } else {
            // Try matching raw JSON array at the end of the text
            val rawArrayRegex = Regex("""(\[\s*\{\s*"id"[\s\S]*?\}\s*\])\s*$""")
            val arrayMatch = rawArrayRegex.find(cleanText)
            if (arrayMatch != null) {
                jsonStr = arrayMatch.groupValues[1]
                cleanText = cleanText.replace(arrayMatch.value, "").trim()
            }
        }

        if (!jsonStr.isNullOrBlank()) {
            try {
                val jsonArr = JSONArray(jsonStr)
                val matchedFranchiseIds = mutableSetOf<Int>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.optJSONObject(i) ?: continue
                    val id = obj.optInt("id", 0)
                    val title = obj.optString("title", "")
                    val badge = obj.optString("badge", "Step ${i + 1}")
                    val note = obj.optString("note", "")
                    val isGame = obj.optBoolean("is_game", false) ||
                        obj.optBoolean("isGame", false) ||
                        obj.optBoolean("game", false) ||
                        badge.contains("Game", ignoreCase = true) ||
                        badge.contains("iiDB", ignoreCase = true) ||
                        note.contains("Game", ignoreCase = true) ||
                        title.contains("Pokemon", ignoreCase = true) ||
                        title.contains("Pokémon", ignoreCase = true)

                    val candHasSeasonIndicator = title.contains(Regex("""(?i)\b(season\s*\d+|part\s*\d+|root\s*a|:re|√a|\b\d+(?:st|nd|rd|th)\s*season|movie|ova|final\s*season)\b"""))
                    val baseItem = if (id > 0) franchiseMap[id] else null
                    val matchedItem = if (baseItem != null && (id !in matchedFranchiseIds) && (!candHasSeasonIndicator || baseItem.title.contains(Regex("""(?i)\b(season\s*\d+|part\s*\d+|root\s*a|:re|√a|\b\d+(?:st|nd|rd|th)\s*season|movie|ova|final\s*season)\b""")))) {
                        baseItem
                    } else {
                        findBestFranchiseMatch(title, franchiseItems, matchedFranchiseIds) ?: baseItem
                    }
                    if (matchedItem != null) {
                        matchedFranchiseIds.add(matchedItem.id)
                    }

                    var finalId = matchedItem?.id ?: id
                    val candHasSeason = title.contains(Regex("""(?i)\b(season\s*\d+|part\s*\d+|root\s*a|:re|√a|\b\d+(?:st|nd|rd|th)\s*season)\b"""))
                    val matchedHasSeason = matchedItem?.title?.contains(Regex("""(?i)\b(season\s*\d+|part\s*\d+|root\s*a|:re|√a|\b\d+(?:st|nd|rd|th)\s*season)\b""")) == true
                    var finalTitle = if (candHasSeason && !matchedHasSeason && matchedItem != null && matchedItem.id == franchiseItems.firstOrNull()?.id) {
                        title
                    } else {
                        matchedItem?.title ?: title
                    }
                    var finalCover = matchedItem?.coverUrl ?: ""
                    var finalBanner = matchedItem?.bannerUrl ?: ""
                    var finalFormat = matchedItem?.format ?: ""
                    var finalYear = matchedItem?.year
                    var finalEpisodes = matchedItem?.episodes

                    // If external anime recommendation or missing poster, fetch live from AniList!
                    if ((finalCover.isEmpty() || finalId == 0) && !isGame) {
                        try {
                            val searchQuery = cleanAnimeTitleForSearch(finalTitle).ifBlank { finalTitle }
                            val searchRes = AniListClient.searchAnime(searchQuery)
                            if (!searchRes.isNullOrBlank()) {
                                val mediaArr = JSONObject(searchRes).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")
                                if (mediaArr != null && mediaArr.length() > 0) {
                                    val firstMedia = mediaArr.getJSONObject(0)
                                    if (finalId == 0) finalId = firstMedia.optInt("id", 0)
                                    val titleObj = firstMedia.optJSONObject("title")
                                    val englishTitle = titleObj?.optString("english")
                                    val romajiTitle = titleObj?.optString("romaji")
                                    val userPref = titleObj?.optString("userPreferred")
                                    val chosen = englishTitle?.takeIf { it.isNotBlank() }
                                        ?: userPref?.takeIf { it.isNotBlank() }
                                        ?: romajiTitle
                                    if (!chosen.isNullOrBlank()) {
                                        val candHasSeason = finalTitle.contains(Regex("""(?i)\b(season\s*\d+|part\s*\d+|root\s*a|:re|√a|\b\d+(?:st|nd|rd|th)\s*season)\b"""))
                                        val chosenHasSeason = chosen.contains(Regex("""(?i)\b(season\s*\d+|part\s*\d+|root\s*a|:re|√a|\b\d+(?:st|nd|rd|th)\s*season)\b"""))
                                        if (candHasSeason && !chosenHasSeason) {
                                            // Preserve specific season title
                                        } else {
                                            finalTitle = chosen
                                        }
                                    }

                                    val coverObj = firstMedia.optJSONObject("coverImage")
                                    val cover = coverObj?.optString("large")
                                        ?: coverObj?.optString("extraLarge")
                                        ?: ""
                                    if (cover.isNotBlank()) finalCover = cover

                                    val banner = firstMedia.optString("bannerImage", "")
                                    finalBanner = if (banner.isNotBlank()) banner else finalCover

                                    val fmt = firstMedia.optString("format", "")
                                    if (fmt.isNotBlank()) finalFormat = fmt

                                    val ep = firstMedia.optInt("episodes", 0)
                                    if (ep > 0) finalEpisodes = ep

                                    val startObj = firstMedia.optJSONObject("startDate")
                                    val yr = startObj?.optInt("year", 0) ?: 0
                                    if (yr > 0) finalYear = yr
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // Anime games: fetch high-resolution official game cover and banner from image search
                    var gameLink = ""
                    val isGameDetected = isGame ||
                        obj.optBoolean("is_game", false) ||
                        obj.optBoolean("isGame", false) ||
                        obj.optBoolean("game", false) ||
                        badge.contains("iiDB", ignoreCase = true) ||
                        badge.contains("Game", ignoreCase = true) ||
                        note.contains("Game", ignoreCase = true) ||
                        finalTitle.contains("Pokemon", ignoreCase = true) ||
                        finalTitle.contains("Pokémon", ignoreCase = true)

                    if (isGameDetected) {
                        val rawQuery = cleanAnimeTitleForSearch(finalTitle).ifBlank { finalTitle }
                        val primaryQuery = rawQuery
                            .substringBefore(" or ")
                            .substringBefore(" / ")
                            .substringBefore(" and ")
                            .substringBefore(" & ")
                            .replace(Regex("""\s*\(\d{4}(?:/\d{4})?\)"""), "")
                            .trim()

                        val (gameCover, gameBanner) = GoogleImageSearchFetcher.fetchGameCoverAndBanner(primaryQuery)
                        if (gameCover.isNotBlank()) {
                            finalCover = gameCover
                            finalBanner = gameBanner.ifBlank { gameCover }
                        }
                        gameLink = "https://www.google.com/search?q=" + URLEncoder.encode("$primaryQuery anime game", "UTF-8")
                        if (finalCover.isBlank() && (primaryQuery.contains("Pokemon", ignoreCase = true) || primaryQuery.contains("Pokémon", ignoreCase = true))) {
                            val cleanPokemon = primaryQuery.replace(Regex("""[^a-zA-Z0-9\-]"""), "").lowercase()
                            finalCover = "https://play.pokemonshowdown.com/sprites/gen5/$cleanPokemon.png"
                        }
                    }

                    val effectiveBanner = finalBanner.ifBlank { finalCover }
                    if (finalId > 0 || finalTitle.isNotBlank() || gameLink.isNotBlank()) {
                        items.add(
                            WatchOrderMapItem(
                                id = finalId,
                                title = finalTitle,
                                badge = badge.replace("iiDB", "Game"),
                                note = note.replace("iiDB", "Game"),
                                coverUrl = finalCover,
                                bannerUrl = effectiveBanner,
                                format = finalFormat,
                                year = finalYear,
                                episodes = finalEpisodes,
                                iidbUrl = gameLink
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback Auto-Detection: If franchise entries are mentioned in text
        if (items.isEmpty() && franchiseItems.isNotEmpty()) {
            val mentioned = mutableListOf<WatchOrderItem>()
            val matchedIds = mutableSetOf<Int>()
            for (item in franchiseItems) {
                val isMentioned = cleanText.contains(item.title, ignoreCase = true) ||
                    (item.title.contains("√A") && (cleanText.contains("Root A", ignoreCase = true) || cleanText.contains("Season 2", ignoreCase = true))) ||
                    (item.title.contains(":re 2", ignoreCase = true) && cleanText.contains(":re Season 2", ignoreCase = true)) ||
                    (item.title.contains(":re", ignoreCase = true) && cleanText.contains(":re", ignoreCase = true))
                if (isMentioned && item.id !in matchedIds) {
                    mentioned.add(item)
                    matchedIds.add(item.id)
                }
            }
            if (mentioned.size >= 2) {
                val isRanking = cleanText.contains(Regex("""(?i)\b(rank|ranking|best to worst|worst to best|tier)\b"""))
                val sortedMentioned = mentioned.sortedBy { item ->
                    val idx1 = cleanText.indexOf(item.title, ignoreCase = true)
                    val idx2 = if (item.title.contains("√A")) {
                        cleanText.indexOf("Root A", ignoreCase = true).takeIf { it >= 0 }
                            ?: cleanText.indexOf("Season 2", ignoreCase = true)
                    } else -1
                    listOf(idx1, idx2).filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE
                }
                for ((idx, item) in sortedMentioned.withIndex()) {
                    val badge = if (isRanking) "Rank #${idx + 1}" else if (idx == 0) "Start" else "Step ${idx + 1}"
                    items.add(
                        WatchOrderMapItem(
                            id = item.id,
                            title = item.title,
                            badge = badge,
                            note = item.format,
                            coverUrl = item.coverUrl,
                            bannerUrl = item.bannerUrl.ifBlank { item.coverUrl },
                            format = item.format,
                            year = item.year,
                            episodes = item.episodes
                        )
                    )
                }
            }
        }

        // 4. Fallback for External Anime Recommendations mentioned as **Title**
        if (items.isEmpty()) {
            val titleRegex = Regex("""(?:^|\n)\s*(?:[\*\-•\+]|\d+[\.\)])\s*\*\*([^*]+)\*\*""")
            val matches = titleRegex.findAll(cleanText).take(6)
            val fallbackMatchedFranchiseIds = mutableSetOf<Int>()
            for ((idx, m) in matches.withIndex()) {
                val rawCand = m.groupValues[1].trim()
                // Check franchise items first so franchise seasons don't collapse
                val franchiseMatch = if (franchiseItems.isNotEmpty()) {
                    findBestFranchiseMatch(rawCand, franchiseItems, fallbackMatchedFranchiseIds)
                } else null

                if (franchiseMatch != null) {
                    fallbackMatchedFranchiseIds.add(franchiseMatch.id)
                    items.add(
                        WatchOrderMapItem(
                            id = franchiseMatch.id,
                            title = franchiseMatch.title,
                            badge = "Rank #${idx + 1}",
                            note = franchiseMatch.format,
                            coverUrl = franchiseMatch.coverUrl,
                            bannerUrl = franchiseMatch.bannerUrl.ifBlank { franchiseMatch.coverUrl },
                            format = franchiseMatch.format,
                            year = franchiseMatch.year,
                            episodes = franchiseMatch.episodes
                        )
                    )
                    continue
                }

                val candidateTitle = cleanAnimeTitleForSearch(rawCand).ifBlank { rawCand }
                if (candidateTitle.length >= 3) {
                    try {
                        val searchRes = AniListClient.searchAnime(candidateTitle)
                        if (!searchRes.isNullOrBlank()) {
                            val mediaArr = JSONObject(searchRes).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")
                            if (mediaArr != null && mediaArr.length() > 0) {
                                val firstMedia = mediaArr.getJSONObject(0)
                                val aId = firstMedia.optInt("id", 0)
                                val titleObj = firstMedia.optJSONObject("title")
                                val englishTitle = titleObj?.optString("english")
                                val romajiTitle = titleObj?.optString("romaji")
                                val userPref = titleObj?.optString("userPreferred")
                                val chosen = englishTitle?.takeIf { it.isNotBlank() }
                                    ?: userPref?.takeIf { it.isNotBlank() }
                                    ?: romajiTitle
                                    ?: candidateTitle

                                val coverObj = firstMedia.optJSONObject("coverImage")
                                val aCover = coverObj?.optString("large") ?: coverObj?.optString("extraLarge") ?: ""
                                val aBanner = firstMedia.optString("bannerImage", "")
                                val fmt = firstMedia.optString("format", "")
                                val ep = firstMedia.optInt("episodes", 0).takeIf { it > 0 }
                                val yr = firstMedia.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }

                                items.add(
                                    WatchOrderMapItem(
                                        id = aId,
                                        title = chosen,
                                        badge = "Rank #${idx + 1}",
                                        note = fmt,
                                        coverUrl = aCover,
                                        bannerUrl = aBanner.ifBlank { aCover },
                                        format = fmt,
                                        year = yr,
                                        episodes = ep
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // 5. Fallback detection for Anime Video Games mentioned in text (especially by Free AI models)
        if (items.none { it.iidbUrl.isNotBlank() || it.badge.contains("Game", ignoreCase = true) }) {
            val gameBulletRegex = Regex("""(?:^|\n)\s*(?:[\*\-•\+]|\d+[\.\)])\s*(?:[A-Za-z\s]+->\s*)?(?:(?:Consider|Try|Play|Grab)\s+)?\**([A-Z][a-zA-Z0-9\s:'\-–/&]+?(?:Pokémon|Pokemon|Fate|Persona|Naruto|Dragon Ball|Sword|Shield|Arceus|Scarlet|Violet|Storm|Sparking|Xenoverse|Genshin|Honkai|Bleach)[a-zA-Z0-9\s:'\-–/&]*?)\**(?:\s*\(|\s*->|\s*—|\s*:|\s*DLC|$)""", RegexOption.MULTILINE)
            val gameMatches = gameBulletRegex.findAll(cleanText).take(5)
            for (gm in gameMatches) {
                val cand = gm.groupValues[1].trim()
                val cleanCand = cand
                    .substringBefore(" or ")
                    .substringBefore(" / ")
                    .substringBefore(" and ")
                    .replace(Regex("""\s*\(\d{4}.*"""), "")
                    .trim(' ', '*', '-', '•')
                if (cleanCand.length >= 4 && items.none { it.title.equals(cleanCand, ignoreCase = true) }) {
                    try {
                        val (coverUrl, bannerUrl) = GoogleImageSearchFetcher.fetchGameCoverAndBanner(cleanCand)
                        if (coverUrl.isNotBlank()) {
                            val googleSearchUrl = "https://www.google.com/search?q=" + java.net.URLEncoder.encode("$cleanCand game", "UTF-8")
                            items.add(
                                WatchOrderMapItem(
                                    id = 0,
                                    title = cleanCand,
                                    badge = "Anime Game",
                                    note = "Video Game",
                                    coverUrl = coverUrl,
                                    bannerUrl = bannerUrl.ifBlank { coverUrl },
                                    iidbUrl = googleSearchUrl
                                )
                            )
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // Clean any stray code blocks or markdown artifacts left at the end
        cleanText = cleanText.replace(Regex("""```(?:watch_order|json|quiz|game)?\s*$"""), "").trim()
        cleanText = cleanText.lines().filterNot { line ->
            val t = line.trim()
            t == "..." || t == "```" || t == "```quiz" || t == "```game" ||
                (t.isNotEmpty() && t.all { ch -> ch == '"' || ch == '\'' || ch == '”' || ch == '“' || ch == '’' || ch == '‘' || ch == ' ' || ch == '.' || ch == '•' })
        }.joinToString("\n").trim()

        // Determine dynamic section title to suit the event
        val sectionTitle = when {
            items.any { it.iidbUrl.isNotBlank() || it.badge.contains("Game", ignoreCase = true) } ->
                "Featured Anime Games • Tap to Explore"
            items.any { it.badge.contains("Rank", ignoreCase = true) || it.badge.contains("Top", ignoreCase = true) } ->
                "Top Ranked Anime • Tap to Explore"
            items.any { it.badge.contains("Similar", ignoreCase = true) || it.badge.contains("Recommend", ignoreCase = true) } ->
                "Recommended Anime & Visual Roadmap"
            items.any { it.badge.contains("Choice", ignoreCase = true) || it.badge.contains("Option", ignoreCase = true) } ->
                "Interactive Choices • Tap to Select"
            items.any { it.badge.contains("Canon", ignoreCase = true) || it.badge.contains("Start", ignoreCase = true) || it.badge.contains("Step", ignoreCase = true) } ->
                "Chronological Watch Flow (Tap card to view)"
            quizData != null ->
                "Interactive Challenge • Choose Your Answer"
            items.isNotEmpty() ->
                "Recommended Titles (Tap card to view)"
            else -> null
        }

        return ParsedAiPayload(cleanText, items, quizData, playAction, sectionTitle)
    }

    private fun cleanAnimeTitleForSearch(raw: String): String {
        return raw
            .replace(Regex("""^[\d\.\-\*\•\+\s"'\#]+"""), "")
            .replace(Regex("""["']+$"""), "")
            .replace(Regex("""\s*\(\d{4}\)"""), "")
            .replace(Regex("""\s*\(TV\)"""), "")
            .replace(Regex("""\s*\(Movie\)"""), "")
            .replace(Regex("""\s*\(OVA\)"""), "")
            .trim()
    }

    private fun findBestFranchiseMatch(
        candidateTitle: String,
        franchiseItems: List<WatchOrderItem>,
        alreadyMatchedIds: Set<Int>
    ): WatchOrderItem? {
        val cleanCand = candidateTitle.trim()
        if (cleanCand.isBlank() || franchiseItems.isEmpty()) return null

        // 1. Exact case-insensitive match
        val exact = franchiseItems.find { it.title.equals(cleanCand, ignoreCase = true) }
        if (exact != null && (exact.id !in alreadyMatchedIds || franchiseItems.size == 1)) return exact

        // 2. Normalized match (stripping year, TV/Movie/OVA tags, quotes)
        fun normalize(s: String) = s
            .replace(Regex("""\s*\(\d{4}\)"""), "")
            .replace(Regex("""\s*\(TV\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(Movie\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(OVA\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""["']"""), "")
            .trim()

        val normalizedCand = normalize(cleanCand)
        val exactNorm = franchiseItems.find {
            normalize(it.title).equals(normalizedCand, ignoreCase = true)
        }
        if (exactNorm != null && (exactNorm.id !in alreadyMatchedIds || franchiseItems.size == 1)) return exactNorm

        // 3. Season and Sub-series specific matching (handles Tokyo Ghoul √A/Root A, :re, Part 1/2, Seasons 1-4)
        val candLower = cleanCand.lowercase()
        val isReSeason2 = candLower.contains(":re 2") ||
            candLower.contains(":re season 2") ||
            candLower.contains("re 2nd") ||
            (candLower.contains(":re") && candLower.contains("2"))
        val isReSeason1 = (candLower.contains(":re") || candLower.contains(" re")) && !isReSeason2

        val isPart2 = candLower.contains("part 2") || candLower.contains("part ii") || candLower.contains("cour 2")
        val isPart1 = (candLower.contains("part 1") || candLower.contains("part i") || candLower.contains("cour 1")) && !isPart2

        val isSeason2 = candLower.contains("season 2") ||
            candLower.contains("2nd season") ||
            candLower.contains("root a") ||
            cleanCand.contains("√A") ||
            candLower.contains("s2")
        val isSeason3 = candLower.contains("season 3") ||
            candLower.contains("3rd season") ||
            candLower.contains("s3")
        val isSeason4 = candLower.contains("season 4") ||
            candLower.contains("4th season") ||
            candLower.contains("final season") ||
            candLower.contains("s4")

        if (isReSeason2) {
            val match = franchiseItems.find { item ->
                val itLower = item.title.lowercase()
                item.id !in alreadyMatchedIds && (
                    itLower.contains(":re 2") ||
                    itLower.contains(":re 2nd") ||
                    (itLower.contains(":re") && itLower.contains("2"))
                )
            }
            if (match != null) return match
        }
        if (isReSeason1) {
            val match = franchiseItems.find { item ->
                val itLower = item.title.lowercase()
                item.id !in alreadyMatchedIds && (
                    itLower.contains(":re") || itLower.contains(" re")
                ) && !itLower.contains("2")
            }
            if (match != null) return match
        }
        if (isSeason2) {
            val match = franchiseItems.find { item ->
                val itLower = item.title.lowercase()
                item.id !in alreadyMatchedIds && (
                    itLower.contains("2") ||
                    itLower.contains("root a") ||
                    item.title.contains("√A") ||
                    itLower.contains("second")
                )
            }
            if (match != null) return match
        }
        if (isSeason3) {
            val match = franchiseItems.find { item ->
                val itLower = item.title.lowercase()
                item.id !in alreadyMatchedIds && (
                    itLower.contains("3") ||
                    itLower.contains("third") ||
                    itLower.contains(":re")
                )
            }
            if (match != null) return match
        }
        if (isSeason4) {
            val match = franchiseItems.find { item ->
                val itLower = item.title.lowercase()
                item.id !in alreadyMatchedIds && (
                    itLower.contains("4") ||
                    itLower.contains("final") ||
                    itLower.contains(":re 2") ||
                    itLower.contains(":re 2nd")
                )
            }
            if (match != null) return match
        }
        if (isPart2) {
            val match = franchiseItems.find { item ->
                val itLower = item.title.lowercase()
                item.id !in alreadyMatchedIds && (itLower.contains("part 2") || itLower.contains("part ii"))
            }
            if (match != null) return match
        }
        if (isPart1) {
            val match = franchiseItems.find { item ->
                val itLower = item.title.lowercase()
                item.id !in alreadyMatchedIds && (itLower.contains("part 1") || itLower.contains("part i"))
            }
            if (match != null) return match
        }

        // 4. Substring fallback prioritized by length & unmatched IDs
        val candidates = franchiseItems.filter { item ->
            val itTitle = item.title.lowercase()
            candLower.contains(itTitle) || itTitle.contains(candLower)
        }.sortedByDescending { it.title.length }

        return candidates.firstOrNull { it.id !in alreadyMatchedIds } ?: candidates.firstOrNull()
    }
}
