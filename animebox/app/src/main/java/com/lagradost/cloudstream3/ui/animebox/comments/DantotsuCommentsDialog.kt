package com.lagradost.cloudstream3.ui.animebox.comments

import com.lagradost.cloudstream3.ui.animebox.components.ThemeDotCircularProgressIndicator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.lagradost.cloudstream3.ui.animebox.SolarPlayVector
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.lagradost.cloudstream3.R


import com.lagradost.cloudstream3.ui.animebox.api.AiVideoResult
import com.lagradost.cloudstream3.ui.animebox.api.AniListClient
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager
import com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxSyncDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class DantotsuAnimeReviewItem(
    val id: Int,
    val user: String,
    val avatarUrl: String,
    val score: Int,
    val summary: String,
    val body: String,
    val isSpoiler: Boolean
)

private fun cleanAniListReviewMarkdown(raw: String): String {
    if (raw.isBlank()) return ""
    var text = raw.replace("\r\n", "\n").replace("\r", "\n")

    // Strip AniList spoiler syntax wrappers ~!... !~
    text = text.replace(Regex("~!(.*?)!~", RegexOption.DOT_MATCHES_ALL), "$1")

    // Convert AniList custom image tags img220(url), img420(url), img(url), img100%(url), img(width, url) to markdown images
    text = text.replace(
        Regex("""(?i)[~]*\s*img\d*(?:%|\b)?\(\s*(?:[^,)]+,\s*)*(https?://[^\s)]+?)\s*\)\s*[~]*"""),
        "\n\n![]($1)\n\n"
    )

    // Handle webm(url) or video(url)
    text = text.replace(
        Regex("""(?i)[~]*\s*(?:webm|video)\(\s*(https?://[^\s)]+?)\s*\)\s*[~]*"""),
        "\n\n[$1]($1)\n\n"
    )

    // Strip standalone AniList center tags ~~~ (e.g. ~~~ or ~~~~~)
    text = text.replace(Regex("""(?m)^\s*~{2,}\s*$"""), "")
    text = text.replace(Regex("""~{2,}"""), " ")

    // Clean horizontal rule and decorative divider lines
    text = text.replace(Regex("""(?m)^\s*#{0,6}\s*[-=_*~—]{2,}\s*$"""), "")
    text = text.replace(Regex("""(?m)^\s*[-=_*~—]{2,}\s*$"""), "")
    text = text.replace(Regex("""(?m)^\s*_{2,}\s*$"""), "")

    // Clean noise dividers at the start
    text = text.replace(Regex("""^(?:\s*(?:#{1,6}\s*)?[-=_*~—]{2,}\s*[\n\r]*)+"""), "")
    text = text.replace(Regex("""^(?:\s*#{1,6}\s*(?:Review|Overview|Introduction|Summary|Thoughts)\s*[\n\r]*)+"""), "")
    text = text.replace(Regex("""^(?:\s*(?:#{1,6}\s*)?[-=_*~—]{2,}\s*[\n\r]*)+"""), "")

    // Clean inline trailing dash/underscore/tilde artifacts
    text = text.replace(Regex("""(?<=\w)["'”)]?\s*[-=_*~—]{2,}"""), "")

    // Strip trailing divider noise
    text = text.replace(Regex("""(?:\s*(?:#{1,6}\s*)?[-=_*~—]{2,}\s*[\n\r]*)+$"""), "")

    // Collapse excessive blank lines
    text = text.replace(Regex("""\n[ \t]*\n[ \t]*\n+"""), "\n\n")

    return text.trim()
}

private fun isSafeReviewUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

private fun reviewBodyToMarkdown(raw: String): String {
    val cleanedRaw = cleanAniListReviewMarkdown(raw)
    val htmlPattern = Regex("(?is)</?(?:p|div|br|hr|a|img|span|strong|b|em|i|ul|ol|li|blockquote|h[1-6]|code|pre)")
    val normalized = if (htmlPattern.containsMatchIn(cleanedRaw)) {
        val document = org.jsoup.Jsoup.parseBodyFragment(cleanedRaw)
        fun org.jsoup.nodes.Node.asMarkdown(): String = when (this) {
            is org.jsoup.nodes.TextNode -> wholeText
            is org.jsoup.nodes.Element -> {
                val childText = childNodes().joinToString("") { it.asMarkdown() }
                when (normalName()) {
                    "img" -> (attr("src").ifBlank { attr("data-src") }).takeIf(::isSafeReviewUrl)
                        ?.let { "\n![${attr("alt").ifBlank { "Review image" }}]($it)\n" }.orEmpty()
                    "a" -> {
                        val href = absUrl("href").ifBlank { attr("href") }
                        if (childText.contains("![") || !isSafeReviewUrl(href) || childText.isBlank()) childText
                        else "[$childText]($href)"
                    }
                    "br" -> "\n"
                    "hr" -> "\n"
                    "p", "div", "blockquote", "pre" -> "\n\n$childText\n\n"
                    "li" -> "\n• $childText"
                    "strong", "b" -> "**$childText**"
                    "em", "i" -> "*$childText*"
                    "code" -> "`$childText`"
                    else -> childText
                }
            }
            else -> childNodes().joinToString("") { it.asMarkdown() }
        }
        document.body().childNodes().joinToString("") { it.asMarkdown() }
    } else cleanedRaw

    return cleanAniListReviewMarkdown(normalized)
}

private fun reviewTextForDisplay(raw: String): String {
    return cleanAniListReviewMarkdown(raw)
        .replace(Regex("(?m)^\\s*#{1,6}\\s*[-=_*~—]{2,}\\s*$"), "")
        .replace(Regex("(?m)^\\s*(?:-{2,}|_{2,}|\\*{2,}|={2,}|~{2,}|—{2,})\\s*$"), "")
        .replace(Regex("(?m)^\\s*#{1,6}\\s+(.+?)\\s*#*\\s*$")) { "**${it.groupValues[1].trim()}**" }
        .replace(Regex("~{2,}"), " ")
        .replace(Regex("""\n[ \t]*\n[ \t]*\n+"""), "\n\n")
        .trim()
}

private sealed interface ReviewBodyPart {
    data class Text(val value: String) : ReviewBodyPart
    data class Image(val url: String, val label: String) : ReviewBodyPart
    data class YouTube(val url: String, val videoId: String, val label: String) : ReviewBodyPart
    data class Link(val url: String, val label: String, val host: String) : ReviewBodyPart
}

private fun parseReviewBodyParts(body: String): List<ReviewBodyPart> {
    val links = Regex("!?\\[([^]]*)]\\((https?://[^)]+)\\)|https?://[^\\s<>()]+", RegexOption.IGNORE_CASE)
    return buildList {
        var cursor = 0
        links.findAll(body).forEach { match ->
            if (match.range.first > cursor) {
                val textChunk = body.substring(cursor, match.range.first)
                if (textChunk.trim().isNotBlank()) add(ReviewBodyPart.Text(textChunk))
            }
            val markdown = match.value.startsWith("[") || match.value.startsWith("![")
            val image = match.value.startsWith("![")
            val url = (if (markdown) match.groupValues[2] else match.value).trimEnd('.', ',', ';', '!', '?', ')')
            val label = if (markdown) match.groupValues[1].ifBlank { "Review link" } else url
            val videoId = reviewYoutubeId(url)
            when {
                image || isReviewImageUrl(url) -> add(ReviewBodyPart.Image(url, label))
                videoId != null -> add(ReviewBodyPart.YouTube(url, videoId, if (markdown) label else "Watch video on YouTube"))
                else -> {
                    val host = runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
                    add(ReviewBodyPart.Link(url, label, host))
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < body.length) {
            val textChunk = body.substring(cursor)
            if (textChunk.trim().isNotBlank()) add(ReviewBodyPart.Text(textChunk))
        }
    }
}

private fun reviewYoutubeId(url: String): String? = runCatching {
    val uri = Uri.parse(url)
    val host = uri.host.orEmpty().lowercase()
    val candidate = when {
        host == "youtu.be" || host.endsWith(".youtu.be") -> uri.pathSegments.firstOrNull()
        host.contains("youtube") -> uri.getQueryParameter("v") ?: run {
            val segments = uri.pathSegments
            val markerIndex = segments.indexOfFirst { it.equals("embed", true) || it.equals("shorts", true) || it.equals("live", true) }
            segments.getOrNull(markerIndex + 1)
        }
        else -> null
    }
    candidate?.takeIf { Regex("^[A-Za-z0-9_-]{6,}$").matches(it) }
}.getOrNull()

private fun isReviewImageUrl(url: String): Boolean =
    Regex("\\.(?:png|jpe?g?|jpf|webp|gif|bmp|avif|svg)(?:[?#].*)?$", RegexOption.IGNORE_CASE).containsMatchIn(url) ||
        url.contains("imgur.com", ignoreCase = true) || url.contains("anilist.co/img", ignoreCase = true) ||
        url.contains("i.pinimg.com", ignoreCase = true) || url.contains("pbs.twimg.com/media", ignoreCase = true) ||
        url.contains("images.unsplash.com", ignoreCase = true) || url.contains("image.tmdb.org", ignoreCase = true) ||
        url.contains("cdn.myanimelist.net", ignoreCase = true) || url.contains("media.discordapp.net/attachments", ignoreCase = true) ||
        url.contains("imgsrv.crunchyroll.com", ignoreCase = true) || url.contains("cdn-cgi/image", ignoreCase = true) ||
        url.contains("static.animecorner.me", ignoreCase = true) || url.contains("animecorner.me", ignoreCase = true) ||
        url.contains("preview.redd.it", ignoreCase = true) || url.contains("i.redd.it", ignoreCase = true) ||
        url.contains("media.tenor.com", ignoreCase = true) || url.contains("giphy.com", ignoreCase = true)

private fun buildAnnotatedMarkdownString(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        append(text)
    }
}

@Composable
private fun YouTubeReviewEmbed(part: ReviewBodyPart.YouTube, onOpenUrl: (String) -> Unit) {
    val initialTitle = if (part.label.isNotBlank() && part.label != part.url) part.label else "Watch video on YouTube"
    val cachedInfo = remember(part.videoId) {
        com.lagradost.cloudstream3.ui.animebox.api.YouTubeMetadataManager.getCached(part.videoId)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1F2026))
            .clickable { onOpenUrl(part.url) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 80.dp, height = 48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF141418))
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = cachedInfo?.thumbnailUrl ?: "https://i.ytimg.com/vi/${part.videoId}/hqdefault.jpg"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = SolarPlayVector,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cachedInfo?.title ?: initialTitle,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = cachedInfo?.authorName ?: "YouTube",
                color = Color(0xFFA0A0AB),
                fontSize = 10.5.sp
            )
        }
    }
}

@Composable
private fun ReviewLinkPreview(part: ReviewBodyPart.Link, onOpenUrl: (String) -> Unit) {
    val faviconUrl = remember(part.host) { "https://www.google.com/s2/favicons?domain=${Uri.encode(part.host)}&sz=64" }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E24))
            .clickable { onOpenUrl(part.url) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val favContext = LocalContext.current

        val favReq = remember(faviconUrl) { ImageRequest.Builder(favContext).data(faviconUrl).crossfade(true).memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).build() }

        Image(

            painter = rememberAsyncImagePainter(model = favReq),

            contentDescription = "${part.host} icon",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = part.label,
                color = Color(0xFFE5E5EA),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = part.host,
                color = Color(0xFFA5A5AD),
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReviewRichBody(body: String, expanded: Boolean, onOpenUrl: (String) -> Unit) {
    val cleanBody = remember(body) { reviewBodyToMarkdown(body) }
    val parts = remember(cleanBody) { parseReviewBodyParts(cleanBody) }
    var previewTextRendered = false
    var previewEmbedRendered = false
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { part ->
            when (part) {
                is ReviewBodyPart.Text -> {
                    val trimmed = part.value.trim()
                    if (trimmed.isNotBlank() && (expanded || !previewTextRendered)) {
                        previewTextRendered = true
                        val formattedText = remember(trimmed) {
                            buildAnnotatedMarkdownString(reviewTextForDisplay(trimmed))
                        }
                        if (formattedText.isNotBlank()) {
                            Text(
                                text = formattedText,
                                color = Color(0xFFD6D6DF),
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                maxLines = if (expanded) Int.MAX_VALUE else 5,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                is ReviewBodyPart.Image -> if (expanded || !previewEmbedRendered) {

                    previewEmbedRendered = true

                    val rContext = LocalContext.current

                    val rImgReq = remember(part.url) { ImageRequest.Builder(rContext).data(part.url).crossfade(true).memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).build() }

                    Image(

                        painter = rememberAsyncImagePainter(model = rImgReq),
                        contentDescription = part.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenUrl(part.url) }
                    )
                }
                is ReviewBodyPart.YouTube -> if (expanded || !previewEmbedRendered) {
                    previewEmbedRendered = true
                    YouTubeReviewEmbed(part, onOpenUrl)
                }
                is ReviewBodyPart.Link -> if (expanded || !previewEmbedRendered) {
                    previewEmbedRendered = true
                    ReviewLinkPreview(part, onOpenUrl)
                }
            }
        }
    }
}

private fun openReviewUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun SolarChatRoundDotsBrokenIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    strokeWidth: Float = 1.25f
) {
    val paths = remember {
        listOf(
            PathParser().parsePathString("M17 3.33782C15.5291 2.48697 13.8214 2 12 2C6.47715 2 2 6.47715 2 12C2 13.5997 2.37562 15.1116 3.04346 16.4525C3.22094 16.8088 3.28001 17.2161 3.17712 17.6006L2.58151 19.8267C2.32295 20.793 3.20701 21.677 4.17335 21.4185L6.39939 20.8229C6.78393 20.72 7.19121 20.7791 7.54753 20.9565C8.88837 21.6244 10.4003 22 12 22C17.5228 22 22 17.5228 22 12C22 10.1786 21.513 8.47087 20.6622 7").toPath(),
            PathParser().parsePathString("M8 12H8.009M11.991 12H12M15.991 12H16").toPath()
        )
    }
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            paths.forEach { path ->
                drawPath(path, tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
fun DantotsuPlayerCommentButton(onClick: () -> Unit) {
    IconButton(
        modifier = Modifier.size(44.dp),
        onClick = onClick
    ) {
        SolarChatRoundDotsBrokenIcon(
            modifier = Modifier.size(28.dp),
            tint = Color.White,
            strokeWidth = 1.8f
        )
    }
}

@Composable
fun DantotsuPlayerCommentModal(
    visible: Boolean,
    animeId: Int,
    animeTitle: String,
    currentEpisodeNum: Int?,
    onDismiss: () -> Unit
) {
    if (visible) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121319))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            contentAlignment = Alignment.Center
        ) {
            DantotsuCommentsPanel(
                animeId = animeId,
                animeTitle = animeTitle,
                currentEpisodeNum = currentEpisodeNum,
                onDismiss = onDismiss,
                modifier = Modifier
                    .width(maxWidth.coerceAtMost(560.dp))
                    .fillMaxHeight()
            )
        }
    }
}

/**
 * Fullscreen vertical discussion modal for Video Player and Anime Details.
 * The video player underneath stays strictly horizontal while this modal opens as a sleek vertical mobile panel.
 */
@Composable
fun DantotsuCommentsDialog(
    animeId: Int,
    animeTitle: String,
    currentEpisodeNum: Int? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT
                )
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.statusBarColor = android.graphics.Color.rgb(18, 19, 25)
                window.navigationBarColor = android.graphics.Color.rgb(18, 19, 25)
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.rgb(18, 19, 25)))
                window.decorView.setBackgroundColor(android.graphics.Color.rgb(18, 19, 25))
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                    window.isStatusBarContrastEnforced = false
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121319)),
            contentAlignment = Alignment.Center
        ) {
            // Centered vertical mobile container (never stretches weirdly across horizontal video)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                color = Color(0xFF121319),
                shape = RoundedCornerShape(0.dp)
            ) {
                DantotsuCommentsPanel(
                    animeId = animeId,
                    animeTitle = animeTitle,
                    currentEpisodeNum = currentEpisodeNum,
                    onDismiss = onDismiss,
                    modifier = Modifier.fillMaxSize().statusBarsPadding()
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Vertical))
                )
            }
        }
    }
}

@Composable
fun DantotsuCommentsPanel(
    animeId: Int,
    animeTitle: String,
    currentEpisodeNum: Int? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeColor = MaterialTheme.colorScheme.primary

    var showAniListLoginWeb by remember { mutableStateOf(false) }
    var hasAniListAuth by remember { mutableStateOf(DantotsuCommentsManager.hasAniListLogin(context)) }

    val hasEp = currentEpisodeNum != null && currentEpisodeNum > 0
    val tabLabels = remember(hasEp, currentEpisodeNum) {
        if (hasEp) listOf("EP $currentEpisodeNum", "All Anime", "Reviews")
        else listOf("Comments", "Reviews")
    }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedSort by remember { mutableStateOf("newest") } // "newest", "highest_rated", "oldest"

    // Comments State
    var comments by remember { mutableStateOf<List<DantotsuComment>>(emptyList()) }
    var totalPages by remember { mutableStateOf(1) }
    var currentPage by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // AniList Reviews State
    var reviews by remember { mutableStateOf<List<DantotsuAnimeReviewItem>>(emptyList()) }
    var reviewsLoading by remember { mutableStateOf(false) }
    var reviewsError by remember { mutableStateOf<String?>(null) }
    var expandedReviewIds by remember { mutableStateOf(emptySet<Int>()) }

    // Posting State
    var replyingTo by remember { mutableStateOf<DantotsuComment?>(null) }
    var draft by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isSending by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedImages = uris.take(4)
    }

    val activeTag: Int? = if (hasEp) {
        if (selectedTab == 0) currentEpisodeNum else null
    } else null

    val isReviewsTab = if (hasEp) selectedTab == 2 else selectedTab == 1

    val loadComments: suspend (page: Int, reset: Boolean) -> Unit = { page, reset ->
        if (reset) loading = true else loadingMore = true
        error = null
        hasAniListAuth = DantotsuCommentsManager.hasAniListLogin(context)
        val tagToFetch = activeTag
        val result = DantotsuCommentsManager.fetchComments(
            context = context,
            mediaId = animeId,
            page = page,
            tag = tagToFetch,
            sort = selectedSort
        )
        if (reset) {
            comments = result.comments
        } else {
            val existingIds = comments.map { it.commentId }.toSet()
            val newItems = result.comments.filter { it.commentId !in existingIds }
            comments = comments + newItems
        }
        totalPages = result.totalPages
        currentPage = page
        error = DantotsuCommentsManager.lastError
        loading = false
        loadingMore = false
    }

    val loadReviews: suspend () -> Unit = {
        reviewsLoading = true
        reviewsError = null
        val raw = withContext(Dispatchers.IO) { AniListClient.getAnimeReviews(animeId) }
        reviewsError = if (raw == null) AniListClient.lastErrorMessage else null
        reviews = runCatching {
            val nodes = JSONObject(raw ?: "{}").optJSONObject("data")
                ?.optJSONObject("Media")?.optJSONObject("reviews")?.optJSONArray("nodes")
            (0 until (nodes?.length() ?: 0)).mapNotNull { index ->
                val node = nodes?.optJSONObject(index) ?: return@mapNotNull null
                val rawBody = node.optString("body", "")
                DantotsuAnimeReviewItem(
                    id = node.optInt("id", index),
                    user = node.optJSONObject("user")?.optString("name", "AniList user") ?: "AniList user",
                    avatarUrl = node.optJSONObject("user")?.optJSONObject("avatar")?.optString("large", "").orEmpty(),
                    score = node.optInt("score", 0).coerceIn(0, 100),
                    summary = node.optString("summary", "").trim(),
                    body = rawBody,
                    isSpoiler = Regex("~!.*?!~", RegexOption.DOT_MATCHES_ALL).containsMatchIn(rawBody)
                )
            }
        }.getOrDefault(emptyList())
        reviewsLoading = false
    }

    LaunchedEffect(animeId, selectedTab, selectedSort) {
        hasAniListAuth = DantotsuCommentsManager.hasAniListLogin(context)
        if (isReviewsTab) {
            if (reviews.isEmpty()) loadReviews()
        } else {
            loadComments(1, true)
        }
    }

    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .background(Color(0xFF121319))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = animeTitle,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onDismiss != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E202B))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Navigation Tabs (EP X, All Anime, Reviews)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1B1C24))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabLabels.forEachIndexed { index, label ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) Color(0xFF2C2F3D) else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color(0xFF8E92A4),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Subheader: Sort Bar & Info
        if (!isReviewsTab && hasAniListAuth) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val counterText = when {
                    comments.isEmpty() -> ""
                    totalPages <= 1 -> "${comments.size} comments"
                    else -> "${comments.size} comments · Page $currentPage of $totalPages"
                }
                Text(
                    text = counterText,
                    color = Color(0xFF7E8299),
                    fontSize = 10.5.sp,
                    modifier = Modifier.weight(1f)
                )

                // Sort pills
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1B1C24))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("newest" to "New", "highest_rated" to "Top", "oldest" to "Old").forEach { (sortKey, sortLabel) ->
                        val isSortSelected = selectedSort == sortKey
                        Text(
                            text = sortLabel,
                            color = if (isSortSelected) Color.White else Color(0xFF7E8299),
                            fontSize = 10.sp,
                            fontWeight = if (isSortSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSortSelected) Color(0xFF2E3244) else Color.Transparent)
                                .clickable { selectedSort = sortKey }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Refresh",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF1B1C24))
                        .clickable { scope.launch { loadComments(1, true) } }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isReviewsTab) {
                // AniList Reviews
                when {
                    reviewsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ThemeDotCircularProgressIndicator(color = themeColor, size = 36.dp)
                    }
                    reviews.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(reviewsError ?: "No AniList reviews available.", color = Color(0xFF7E8299), fontSize = 13.sp)
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(reviews, key = { it.id }) { review ->
                            val expanded = review.id in expandedReviewIds
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF181922))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF262838)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (review.avatarUrl.isNotBlank()) {

                                            val avContext = LocalContext.current

                                            val avReq = remember(review.avatarUrl) { ImageRequest.Builder(avContext).data(review.avatarUrl).crossfade(true).memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).build() }

                                            Image(

                                                painter = rememberAsyncImagePainter(model = avReq),
                                                contentDescription = review.user,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(review.user.take(1).uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(review.user, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Score: ${review.score}/100", color = themeColor, fontSize = 10.5.sp)
                                    }
                                }
                                if (review.summary.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(review.summary, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                                if (review.body.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    ReviewRichBody(
                                        body = review.body,
                                        expanded = expanded,
                                        onOpenUrl = { openReviewUrl(context, it) }
                                    )
                                    if (review.body.length > 200) {
                                        Text(
                                            text = if (expanded) "Show less" else "Show more",
                                            color = Color(0xFF8E92A4),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .padding(top = 6.dp)
                                                .clickable {
                                                    expandedReviewIds = if (expanded) expandedReviewIds - review.id else expandedReviewIds + review.id
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Dantotsu Comments
                when {
                    loading && comments.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ThemeDotCircularProgressIndicator(color = themeColor, size = 36.dp)
                    }
                    !hasAniListAuth && comments.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_anilist_official),
                                contentDescription = "AniList logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(52.dp)
                            )
                            Text(
                                text = "Connect an AniList account to view and use comments.",
                                color = Color(0xFFD4D6E0),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(themeColor)
                                    .clickable { showAniListLoginWeb = true }
                                    .padding(horizontal = 22.dp, vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Connect AniList",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    error != null && comments.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (hasAniListAuth) {
                                SolarChatRoundDotsBrokenIcon(modifier = Modifier.size(36.dp), tint = Color(0xFFEF4444), strokeWidth = 1.25f)
                            }
                            Text(
                                text = if (hasAniListAuth) error ?: "Failed to load comments"
                                else "Connect an AniList account to view and use comments.",
                                color = if (hasAniListAuth) Color(0xFFEF4444) else Color(0xFFD4D6E0),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { scope.launch { loadComments(1, true) } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2230)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Retry", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                    comments.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            SolarChatRoundDotsBrokenIcon(modifier = Modifier.size(36.dp), tint = Color(0xFF4A4E63), strokeWidth = 1.25f)
                            Text(
                                text = if (activeTag != null) "No comments yet for Episode $activeTag." else "No comments yet for this anime.",
                                color = Color(0xFF7E8299),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Be the first to share your thoughts!",
                                color = Color(0xFF565A70),
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center
                            )
                            if (activeTag != null && tabLabels.size > 1) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E212E))
                                        .clickable { selectedTab = 1 }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = "View All Anime Comments",
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(comments, key = { it.commentId }) { comment ->
                            DantotsuCommentCard(
                                comment = comment,
                                onReply = { replyingTo = comment },
                                onVote = { cId, vType ->
                                    scope.launch {
                                        val success = DantotsuCommentsManager.vote(context, cId, vType)
                                        if (success) {
                                            comments = comments.map { c ->
                                                if (c.commentId == cId) {
                                                    val oldVote = c.userVoteType
                                                    val newVote = if (oldVote == vType) 0 else vType
                                                    val delta = newVote - oldVote
                                                    c.copy(
                                                        userVoteType = newVote,
                                                        upvotes = (c.upvotes + (if (newVote == 1) 1 else if (oldVote == 1) -1 else 0)).coerceAtLeast(0),
                                                        totalVotes = c.totalVotes + delta
                                                    )
                                                } else c
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        // Load More systematic handling for large numbers of comments
                        if (currentPage < totalPages) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (loadingMore) {
                                        ThemeDotCircularProgressIndicator(color = themeColor, size = 24.dp)
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF1E202B))
                                                .clickable {
                                                    scope.launch {
                                                        loadComments(currentPage + 1, false)
                                                    }
                                                }
                                                .padding(horizontal = 16.dp, vertical = 9.dp)
                                        ) {
                                            Text(
                                                text = "Load more comments (${currentPage + 1}/$totalPages)",
                                                color = Color.White,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Bar / Input Area
        if (!isReviewsTab && hasAniListAuth) {
                // Comment posting input box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1A1B22))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Replying to chip
                    replyingTo?.let { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF262838))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Replying to @${target.username}: \"${target.cleanContent.take(28)}\"",
                                color = themeColor,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "✕",
                                color = Color(0xFF8E92A4),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { replyingTo = null }
                                    .padding(4.dp)
                            )
                        }
                    }

                    // Text input
                    androidx.compose.foundation.text.BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp, max = 110.dp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                        textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp, lineHeight = 18.sp),
                        decorationBox = { inner ->
                            Box {
                                if (draft.isBlank()) {
                                    Text(
                                        text = if (replyingTo != null) "Write a reply…" else if (activeTag != null) "Discuss Episode $activeTag…" else "Share your thoughts on $animeTitle…",
                                        color = Color(0xFF63677A),
                                        fontSize = 12.5.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )

                    // Image thumbnails preview
                    if (selectedImages.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            selectedImages.forEachIndexed { idx, uri ->
                                Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(7.dp))) {
                                    Image(
                                        painter = rememberAsyncImagePainter(uri),
                                        contentDescription = "Image preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.7f))
                                            .clickable { selectedImages = selectedImages.filterIndexed { i, _ -> i != idx } },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✕", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Bottom actions: Attach Image, Add Spoiler, Send
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Image attachment button
                        Text(
                            text = "+ Image",
                            color = Color(0xFFA5A9BD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF242634))
                                .clickable { imagePicker.launch("image/*") }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Spoiler tag helper
                        Text(
                            text = "Spoiler ||",
                            color = Color(0xFFA5A9BD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF242634))
                                .clickable {
                                    draft = if (draft.isBlank()) "||spoiler||" else "$draft ||spoiler||"
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Send Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSending || (draft.isBlank() && selectedImages.isEmpty())) Color(0xFF252735)
                                    else themeColor
                                )
                                .clickable(enabled = !isSending && (draft.isNotBlank() || selectedImages.isNotEmpty())) {
                                    scope.launch {
                                        isSending = true
                                        try {
                                            val uploadedUrls = selectedImages.mapNotNull {
                                                DantotsuCommentsManager.uploadImage(context, it)
                                            }
                                            val success = DantotsuCommentsManager.postComment(
                                                context = context,
                                                mediaId = animeId,
                                                content = draft,
                                                tag = activeTag,
                                                parentCommentId = replyingTo?.commentId,
                                                imageUrls = uploadedUrls
                                            )
                                            if (success) {
                                                draft = ""
                                                selectedImages = emptyList()
                                                replyingTo = null
                                                Toast.makeText(context, "Comment posted!", Toast.LENGTH_SHORT).show()
                                                loadComments(1, true)
                                            } else {
                                                val errMsg = DantotsuCommentsManager.lastError ?: "Failed to post comment."
                                                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                                            }
                                        } finally {
                                            isSending = false
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isSending) "Posting…" else if (replyingTo != null) "Reply" else "Post",
                                color = if (isSending || (draft.isBlank() && selectedImages.isEmpty())) Color(0xFF6B6F84) else Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
        }
    }

    // Modal AniList Login if triggered
    if (showAniListLoginWeb) {
        AnimeBoxSyncDialog(
            profileId = ProfileManager.getActiveProfile(context),
            initialProvider = com.lagradost.cloudstream3.ui.animebox.sync.SyncProvider.ANILIST,
            onDismiss = {
                showAniListLoginWeb = false
                hasAniListAuth = DantotsuCommentsManager.hasAniListLogin(context)
                if (hasAniListAuth) {
                    Toast.makeText(context, "AniList connected! Loading comments...", Toast.LENGTH_SHORT).show()
                    scope.launch { loadComments(1, true) }
                }
            }
        )
    }
}

/**
 * Individual comment card with like pill, episode tag, thread replies, collection card, and images.
 */
@Composable
fun DantotsuCommentCard(
    comment: DantotsuComment,
    onReply: () -> Unit,
    onVote: (commentId: Int, voteType: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isThreadExpanded by remember { mutableStateOf(false) }
    var replies by remember { mutableStateOf<List<DantotsuComment>>(emptyList()) }
    var loadingReplies by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (comment.isAdmin) Color(0xFF1D202C) else Color(0xFF171821))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Author row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF262838)),
                contentAlignment = Alignment.Center
            ) {
                if (!comment.profilePictureUrl.isNullOrBlank()) {

                    val pContext = LocalContext.current

                    val pReq = remember(comment.profilePictureUrl) { ImageRequest.Builder(pContext).data(comment.profilePictureUrl).crossfade(true).memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).build() }

                    Image(

                        painter = rememberAsyncImagePainter(model = pReq),
                        contentDescription = comment.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = comment.username.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Username and info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = comment.username,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (comment.isAdmin) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("ADMIN", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (comment.isMod) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF10B981))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("MOD", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (comment.tag != null && comment.tag > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF292A30))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("EP ${comment.tag}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    text = comment.formattedDateTime(),
                    color = Color(0xFF7E8299),
                    fontSize = 9.5.sp
                )
            }

            // Reply button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF222432))
                    .clickable(onClick = onReply)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Reply",
                    color = Color(0xFFA5A9BD),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Comment content with spoiler protection
        if (comment.deleted) {
            Text(
                text = "[comment deleted by author or moderation]",
                color = Color(0xFF6B6E80),
                fontSize = 11.5.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        } else if (comment.cleanContent.isNotBlank()) {
            val isSpoiler = comment.cleanContent.contains("||") || comment.cleanContent.contains("~!")
            var spoilerRevealed by remember { mutableStateOf(!isSpoiler) }

            if (isSpoiler && !spoilerRevealed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.88f))
                        .clickable { spoilerRevealed = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SPOILER",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                }
            } else {
                Text(
                    text = comment.cleanContent.replace("||", "").replace("~!", "").replace("!~", ""),
                    color = Color(0xFFD4D7E5),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        // Shared collection preview
        comment.sharedCollectionJson?.let { rawJson ->
            val collectionObj = remember(rawJson) { runCatching { JSONObject(rawJson) }.getOrNull() }
            if (collectionObj != null) {
                val animeArray = collectionObj.optJSONArray("anime")
                val animeList = remember(animeArray) {
                    val list = mutableListOf<JSONObject>()
                    if (animeArray != null) {
                        for (i in 0 until animeArray.length()) {
                            animeArray.optJSONObject(i)?.let { list.add(it) }
                        }
                    }
                    list
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E212E))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "📦 Collection · ${collectionObj.optString("name", "Anime picks")}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (animeList.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            animeList.take(15).forEach { a ->
                                val cover = a.optString("cover")
                                Column(modifier = Modifier.width(58.dp)) {
                                    if (cover.startsWith("http")) {
                                        Image(
                                            painter = rememberAsyncImagePainter(cover),
                                            contentDescription = a.optString("title"),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(72.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(72.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF2C2F40))
                                        )
                                    }
                                    Text(
                                        text = a.optString("title"),
                                        color = Color(0xFFC0C4D6),
                                        fontSize = 8.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Image attachments
        if (comment.imageUrls.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val attContext = LocalContext.current

                comment.imageUrls.forEach { url ->

                    val attReq = remember(url) { ImageRequest.Builder(attContext).data(url).crossfade(true).memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).build() }

                    Image(

                        painter = rememberAsyncImagePainter(model = attReq),
                        contentDescription = "Attachment",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }

        // Bottom Bar: Like Pill & Thread Replies
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Like Pill
            val isUpvoted = comment.userVoteType == 1
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF1F212D))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { onVote(comment.commentId, 1) }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = "Like",
                    tint = if (isUpvoted) MaterialTheme.colorScheme.primary else Color(0xFF8E92A4),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "${comment.upvotes}",
                    color = if (isUpvoted) MaterialTheme.colorScheme.primary else Color(0xFFA5A9BD),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Thread replies toggle
            val totalRepliesCount = comment.replyCount
            if (totalRepliesCount > 0) {
                Text(
                    text = if (isThreadExpanded) "Hide replies" else "View $totalRepliesCount ${if (totalRepliesCount == 1) "reply" else "replies"}",
                    color = Color(0xFFA78BFA),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            isThreadExpanded = !isThreadExpanded
                            if (isThreadExpanded && replies.isEmpty()) {
                                scope.launch {
                                    loadingReplies = true
                                    replies = DantotsuCommentsManager.fetchReplies(context, comment.commentId)
                                    loadingReplies = false
                                }
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        // Expanded Thread Replies with Reddit-style curved branch system
        if (isThreadExpanded) {
            if (loadingReplies) {
                Box(modifier = Modifier.fillMaxWidth().padding(4.dp), contentAlignment = Alignment.Center) {
                    ThemeDotCircularProgressIndicator(color = Color.White, size = 20.dp)
                }
            } else if (replies.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 4.dp)
                ) {
                    replies.forEachIndexed { index, reply ->
                        val isLast = index == replies.lastIndex
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    val strokeW = 1.6.dp.toPx()
                                    val lineColor = Color(0x668B5CF6)
                                    val startX = 6.dp.toPx()
                                    val curveR = 6.dp.toPx()
                                    val branchY = 18.dp.toPx()

                                    if (!isLast) {
                                        drawLine(
                                            color = lineColor,
                                            start = Offset(startX, 0f),
                                            end = Offset(startX, size.height),
                                            strokeWidth = strokeW
                                        )
                                    } else {
                                        drawLine(
                                            color = lineColor,
                                            start = Offset(startX, 0f),
                                            end = Offset(startX, (branchY - curveR).coerceAtLeast(0f)),
                                            strokeWidth = strokeW
                                        )
                                    }
                                    val branchPath = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(startX, (branchY - curveR).coerceAtLeast(0f))
                                        quadraticBezierTo(startX, branchY, startX + curveR, branchY)
                                        lineTo(18.dp.toPx(), branchY)
                                    }
                                    drawPath(branchPath, lineColor, style = Stroke(strokeW))
                                }
                                .padding(start = 18.dp, bottom = 4.dp)
                        ) {
                            DantotsuCommentCard(
                                comment = reply,
                                onReply = onReply,
                                onVote = onVote
                            )
                        }
                    }
                }
            }
        }
    }
}
