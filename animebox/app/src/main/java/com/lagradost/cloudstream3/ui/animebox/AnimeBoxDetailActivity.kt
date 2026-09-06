package com.lagradost.cloudstream3.ui.animebox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import com.lagradost.cloudstream3.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.alpha
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.foundation.border
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import coil3.compose.rememberAsyncImagePainter
import com.lagradost.cloudstream3.ui.animebox.api.AniListClient
import com.lagradost.cloudstream3.ui.animebox.api.AniZipClient
import com.lagradost.cloudstream3.ui.animebox.api.EpisodeMeta
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import com.lagradost.cloudstream3.ui.animebox.history.WatchHistoryManager
import com.lagradost.cloudstream3.ui.animebox.library.LibraryManager
import com.lagradost.cloudstream3.ui.animebox.AnimeBrief
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper

data class AnimeCharacter(
    val name: String,
    val imageUrl: String,
    val role: String,
    val actorName: String,
    val actorImageUrl: String
)

data class AnimeRelation(
    val id: Int,
    val title: String,
    val coverUrl: String,
    val relationType: String,
    val format: String = ""
)

data class AnimeRecommendation(
    val id: Int,
    val title: String,
    val coverUrl: String,
    val format: String
)

data class NextAiringEpisode(
    val airingAt: Long,
    val timeUntilAiring: Int,
    val episode: Int
)

data class AnimeDetail(
    val id: Int,
    val title: String,
    val description: String,
    val coverUrl: String,
    val bannerUrl: String,
    val episodesCount: Int,
    val score: Int,
    val genres: List<String>,
    val status: String = "",
    val format: String = "",
    val releaseYear: Int = 0,
    val isAdult: Boolean = false,
    val idMal: Int = 0,
    val trailerId: String = "",
    val characters: List<AnimeCharacter> = emptyList(),
    val relations: List<AnimeRelation> = emptyList(),
    val recommendations: List<AnimeRecommendation> = emptyList(),
    val nextAiring: NextAiringEpisode? = null
)

class AnimeBoxDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val anilistId = intent.getIntExtra("anilistId", 0)
        if (anilistId == 0) {
            finish()
            return
        }

        setContent {
            val themeRevision by AnimeBoxThemeHelper.themeRevisionFlow.collectAsState()
            val primaryColor = remember(themeRevision) { AnimeBoxThemeHelper.getPrimaryColor(this@AnimeBoxDetailActivity) }
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = primaryColor,
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                ),
                typography = androidx.compose.material3.Typography().copy(
                    bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    bodySmall = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    titleSmall = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    labelLarge = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    labelMedium = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    labelSmall = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    headlineMedium = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    headlineSmall = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    displayLarge = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    displayMedium = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    displaySmall = androidx.compose.ui.text.TextStyle(fontFamily = AnimeBoxThemeHelper.MotoGoogleSansFontFamily)
                )
            ) {
                DetailScreen(anilistId)
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activeNetwork = cm?.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun isFutureDate(dateStr: String): Boolean {
        if (dateStr.isEmpty()) return false
        return try {
            val cleanStr = if (dateStr.contains("T")) {
                dateStr.substringBefore("T")
            } else {
                dateStr
            }
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val airdate = sdf.parse(cleanStr)
            val today = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 23)
                set(java.util.Calendar.MINUTE, 59)
                set(java.util.Calendar.SECOND, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }.time
            airdate != null && airdate.after(today)
        } catch (e: Exception) {
            false
        }
    }



    // Helper function to resolve episode cover images
    private suspend fun getResolvedEpisodeCover(anilistId: Int, episodeNum: Int, defaultUrl: String, meta: EpisodeMeta?): String = withContext(Dispatchers.IO) {
        if (com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId)) {
            val scCover = com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getAllEpisodeCovers(this@AnimeBoxDetailActivity)[episodeNum]
            if (!scCover.isNullOrEmpty()) return@withContext scCover
        }
        val tmdbId = AniZipClient.getLongRunningTmdbId(anilistId)
        if (tmdbId != null && meta != null) {
            val tmdbImg = AniZipClient.getTmdbEpisodeImage(tmdbId, meta.seasonNumber, meta.episodeNumber)
            if (tmdbImg.isNotEmpty()) return@withContext tmdbImg
        }
        return@withContext meta?.imageUrl?.ifEmpty { defaultUrl } ?: defaultUrl
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DetailScreen(anilistId: Int) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val coroutineScope = rememberCoroutineScope()
        val historyManager = remember { WatchHistoryManager(this@AnimeBoxDetailActivity) }
        val libraryManager = remember { LibraryManager(this@AnimeBoxDetailActivity) }
        
        val initialTitle = intent.getStringExtra("initialTitle") ?: ""
        val initialCover = intent.getStringExtra("initialCoverUrl") ?: ""
        val initialBanner = intent.getStringExtra("initialBannerUrl") ?: ""
        val initialDesc = intent.getStringExtra("initialDescription") ?: ""
        val initialGenres = intent.getStringArrayListExtra("initialGenres") ?: arrayListOf()
        val initialScore = intent.getIntExtra("initialScore", 0)
        val initialEpCount = intent.getIntExtra("initialEpisodesCount", 0)

        var detail by remember(anilistId) {
            mutableStateOf(
                if (initialTitle.isNotEmpty()) {
                    AnimeDetail(
                        id = anilistId,
                        title = initialTitle,
                        description = initialDesc,
                        coverUrl = initialCover,
                        bannerUrl = initialBanner,
                        episodesCount = initialEpCount,
                        score = initialScore,
                        genres = initialGenres
                    )
                } else null
            )
        }
        var episodeMetaMap by remember(anilistId) { mutableStateOf<Map<Int, EpisodeMeta>>(emptyMap()) }
        var shinChanSpecials by remember(anilistId) { mutableStateOf<List<com.lagradost.cloudstream3.ui.animebox.api.ShinChanSpecialItem>>(emptyList()) }
        var resolvedBackdropUrl by remember(anilistId) { mutableStateOf("") }
        var isBackdropResolving by remember(anilistId) { mutableStateOf(true) }
        var animeLogoUrl by remember(anilistId) { mutableStateOf("") }
        var isInLibrary by remember(anilistId) { mutableStateOf(false) }
        var isLoading by remember(anilistId) { mutableStateOf(true) }
        var isStreamLoading by remember(anilistId) { mutableStateOf(false) }
        var isTrailerPlaying by remember(anilistId) { mutableStateOf(false) }
        var trailerStreamUrl by remember(anilistId) { mutableStateOf<String?>(null) }
        var isCastExpanded by remember(anilistId) { mutableStateOf(true) }
        var showTrailerConfirmDialog by remember(anilistId) { mutableStateOf(false) }
        var isMuted by remember(anilistId) { mutableStateOf(true) }
        
        val prefs = remember { getSharedPreferences("AnimeBoxPlayer", android.content.Context.MODE_PRIVATE) }
        fun getPreferredStreamType(): String {
            return if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(this@AnimeBoxDetailActivity)) {
                val lastAud = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getLastUsedAudio(this@AnimeBoxDetailActivity)
                val lastSub = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getLastUsedSubMode(this@AnimeBoxDetailActivity)
                if (lastSub == "Hard Sub") "hardsub"
                else if (lastAud == "English") "dub"
                else if (lastAud == "Hindi") "hindi"
                else "sub"
            } else {
                val sAud = prefs.getString("selectedAudio", "Japanese (Original)")
                if (sAud == "English") "dub" else if (sAud == "Hindi") "hindi" else "sub"
            }
        }

        var episodeSearchQuery by remember(anilistId) { mutableStateOf("") }
        var selectedSectionTab by remember(anilistId) { mutableStateOf(0) }
        val defaultEpMode = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getDefaultEpisodeViewMode(this@AnimeBoxDetailActivity) }
        var episodeViewMode by remember(anilistId) { mutableStateOf(defaultEpMode) }
        var visibleEpisodesCount by remember(anilistId) { mutableStateOf(50) }
        var episodeSortOrder by remember(anilistId) { mutableStateOf("asc") }
        var isDescriptionExpanded by remember(anilistId) { mutableStateOf(false) }
        var showAddToListDialog by remember(anilistId) { mutableStateOf(false) }
        var showDownloadSeasonDialog by remember(anilistId) { mutableStateOf(false) }
        var episodeToDownload by remember(anilistId) { mutableStateOf<Pair<Int, String>?>(null) }
        val downloadManager = remember { com.lagradost.cloudstream3.ui.animebox.download.AnimeDownloadManager.getInstance(this@AnimeBoxDetailActivity) }
        val allEpisodeStates by com.lagradost.cloudstream3.ui.animebox.download.AnimeDownloadManager.episodesState.collectAsState()
        var showKitsuMyListRestrictionDialog by remember(anilistId) { mutableStateOf(false) }
        val openReportExtra = remember { intent.getBooleanExtra("openReportDialog", false) }
        val reportEpisodeExtra = remember { intent.getIntExtra("reportEpisode", 1) }
        val reportAnimeTitleExtra = remember { intent.getStringExtra("reportAnimeTitle") ?: (initialTitle.ifEmpty { "Anime Episode" }) }
        var showReportDialog by remember { mutableStateOf(openReportExtra) }

        LaunchedEffect(anilistId) {
            resolvedBackdropUrl = ""
            isBackdropResolving = true
            animeLogoUrl = ""
            isTrailerPlaying = false
            trailerStreamUrl = null
            isLoading = true

            isInLibrary = libraryManager.isInLibrary(anilistId)

            val startEp = historyManager.getWatchHistory().find { it.anilistId == anilistId }?.episodeNumber ?: 1
            launch(Dispatchers.IO) {
                com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.prefetchStreamInfo(
                    this@AnimeBoxDetailActivity,
                    anilistId,
                    startEp,
                    "sub"
                )
            }

            launch(Dispatchers.IO) {
                val detailsDeferred = async { AniListClient.getAnimeDetails(anilistId) }
                val tmdbId = AniZipClient.getLongRunningTmdbId(anilistId)
                val metaDeferred = async {
                    if (tmdbId != null) AniZipClient.getTmdbAllEpisodes(tmdbId)
                    else AniZipClient.getEpisodeMetadata(anilistId)
                }

                val detailsResponse = detailsDeferred.await()
                var parsed = if (detailsResponse != null) parseDetails(detailsResponse) else null
                val isShinChanDetail = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId)
                if (!isShinChanDetail && (parsed == null || com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.isKitsuModeActive)) {
                    val kitsuDetail = com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.getAnimeDetail(anilistId, initialTitle)
                    if (kitsuDetail != null) {
                        parsed = kitsuDetail
                    }
                }

                if (parsed != null && parsed.isAdult) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AnimeBoxDetailActivity, "This content is blocked.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }

                if (parsed != null && parsed.trailerId.isEmpty()) {
                    val fallbackTrailer = resolveFallbackTrailer(anilistId, parsed.idMal, parsed.title)
                    if (fallbackTrailer.isNotEmpty()) {
                        parsed = parsed.copy(trailerId = fallbackTrailer)
                    }
                }

                val isMovie = (parsed?.format?.uppercase() == "MOVIE") || (parsed?.episodesCount == 1) || (intent.getIntExtra("episodes", 0) == 1) || com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.isStaticMovie(anilistId)
                val bestBackdrop = AniZipClient.getBestBackdropUrl(anilistId, parsed?.bannerUrl ?: initialBanner, isMovie = isMovie)
                val meta = metaDeferred.await()
                val logo = AniZipClient.getAnimeLogoUrl(anilistId, isMovie = isMovie)

                val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId)
                val effectiveLogo = if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_LOGO_URL else logo
                val effectiveCover = if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_POSTER_URL else when {
                    parsed != null && parsed.coverUrl.isNotBlank() -> parsed.coverUrl
                    initialCover.isNotBlank() -> initialCover
                    else -> ""
                }
                val latestShinChanCount = if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.getLatestEpisodeNumber() else 0

                if (isShinChan) {
                    com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.loadBundledAssetIfNeeded(this@AnimeBoxDetailActivity)
                    com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.loadShinChanEpisodes(this@AnimeBoxDetailActivity)
                    com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.triggerBackgroundSync(this@AnimeBoxDetailActivity)
                    val specialsList = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.getShinChanSpecials()
                    withContext(Dispatchers.Main) {
                        shinChanSpecials = specialsList
                    }
                }

                val finalEpisodeMetaMap = if (isShinChan) {
                    val scCovers = com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getAllEpisodeCovers(this@AnimeBoxDetailActivity)
                    val maxShinChan = maxOf(com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getLatestEpisodeNumber(), 1349)
                    val scMap = mutableMapOf<Int, EpisodeMeta>()
                    for (ep in 1..maxShinChan) {
                        val cover = scCovers[ep] ?: meta[ep]?.imageUrl ?: ""
                        scMap[ep] = EpisodeMeta(
                            title = "Episode $ep",
                            imageUrl = cover,
                            seasonNumber = 1,
                            episodeNumber = ep,
                            runtime = 24
                        )
                    }
                    scMap
                } else {
                    meta
                }

                withContext(Dispatchers.Main) {
                    if (parsed != null) {
                        detail = parsed.copy(
                            coverUrl = effectiveCover,
                            episodesCount = if (isShinChan && latestShinChanCount > 0) latestShinChanCount else parsed.episodesCount
                        )
                        isInLibrary = libraryManager.isInLibrary(parsed.id)
                    }
                    episodeMetaMap = finalEpisodeMetaMap
                    resolvedBackdropUrl = if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_BACKDROP_URL else bestBackdrop.ifEmpty { detail?.bannerUrl?.ifEmpty { "" } ?: initialBanner.ifEmpty { "" } }
                    if (effectiveLogo.isNotEmpty()) {
                        animeLogoUrl = effectiveLogo
                    }
                    isBackdropResolving = false
                    isLoading = false

                    val isTrailerOn = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isTrailerEnabled(this@AnimeBoxDetailActivity)
                    val isAutoplayPreviewsOn = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isAutoplayPreviewsEnabled(this@AnimeBoxDetailActivity)
                    val shouldAutoplayTrailer = isTrailerOn && isAutoplayPreviewsOn
                    if (shouldAutoplayTrailer && parsed != null && parsed.trailerId.isNotEmpty()) {
                        isTrailerPlaying = true
                    }
                }
            }
        }

        Scaffold(
            bottomBar = {
                NetflixBottomNav(
                    onTabSelected = { tabIndex ->
                        val intent = Intent(this@AnimeBoxDetailActivity, AnimeBoxMainActivity::class.java).apply {
                            putExtra("selectTab", tabIndex)
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            },
            containerColor = Color(0xFF000000)
        ) { paddingValues ->
            if (isLoading) {
                AnimeBoxDetailSkeletonLoading(modifier = Modifier.padding(paddingValues))
            } else {
                detail?.let { d ->
                    val watchHistory = remember { historyManager.getWatchHistory() }
                    val lastWatched = watchHistory.find { it.anilistId == anilistId }
                    
                    val isMovie = (d.format.uppercase() == "MOVIE") || (d.episodesCount == 1)
                    val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId)
                    val isLongRunning = AniZipClient.getLongRunningTmdbId(anilistId) != null
                    val rawEpisodesList = if (isMovie && d.episodesCount <= 1) {
                        listOf(1)
                    } else if (isShinChan) {
                        val maxShinChan = maxOf(com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getLatestEpisodeNumber(), d.episodesCount, 1349)
                        (1..maxShinChan).toList()
                    } else if (episodeMetaMap.isNotEmpty()) {
                        val isReleasing = d.status.contains("RELEASING", ignoreCase = true) || d.nextAiring != null
                        episodeMetaMap.keys.sorted().filter { epNum ->
                            if (!isLongRunning && !isReleasing && d.episodesCount > 0 && epNum > d.episodesCount) {
                                false
                            } else {
                                val meta = episodeMetaMap[epNum]
                                if (meta != null) {
                                    if (meta.airdate.isNotEmpty()) {
                                        !isFutureDate(meta.airdate)
                                    } else {
                                        true
                                    }
                                } else {
                                    false
                                }
                            }
                        }
                    } else {
                        val count = if (d.episodesCount > 0) {
                            d.episodesCount
                        } else if (d.nextAiring != null) {
                            maxOf(1, d.nextAiring.episode - 1)
                        } else {
                            1
                        }
                        (1..count).toList()
                    }

                    val episodesList = if (isMovie && d.episodesCount <= 1) {
                        listOf(1)
                    } else if (rawEpisodesList.isEmpty()) {
                        (1..(if (d.episodesCount > 0) d.episodesCount else 1)).toList()
                    } else {
                        rawEpisodesList
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF16181D),
                                        Color(0xFF0F1014),
                                        Color(0xFF07080A),
                                        Color(0xFF000000)
                                    )
                                )
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = paddingValues.calculateBottomPadding())
                                .verticalScroll(rememberScrollState())
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                            ) {
                                if (isTrailerPlaying && d.trailerId.isNotEmpty()) {
                                    val context = LocalContext.current
                                    val coroutineScope = rememberCoroutineScope()
                                    var isTrailerStreamLoading by remember(d.trailerId) { mutableStateOf(trailerStreamUrl == null) }

                                    LaunchedEffect(isTrailerPlaying, d.trailerId) {
                                        if (isTrailerPlaying && trailerStreamUrl == null) {
                                            isTrailerStreamLoading = true
                                            val extracted = extractTrailerWithNewPipe(d.trailerId)
                                            trailerStreamUrl = extracted
                                            isTrailerStreamLoading = false
                                        }
                                    }

                                    if (trailerStreamUrl != null) {
                                        val exoPlayer = remember(trailerStreamUrl) {
                                            ExoPlayer.Builder(context).build().apply {
                                                val mediaItem = MediaItem.fromUri(trailerStreamUrl!!)
                                                setMediaItem(mediaItem)
                                                repeatMode = Player.REPEAT_MODE_ONE
                                                volume = if (isMuted) 0f else 1f
                                                playWhenReady = true
                                                prepare()
                                            }
                                        }

                                        LaunchedEffect(isMuted) {
                                            exoPlayer.volume = if (isMuted) 0f else 1f
                                        }

                                        DisposableEffect(exoPlayer) {
                                            onDispose {
                                                exoPlayer.release()
                                            }
                                        }

                                        AndroidView(
                                            factory = { ctx ->
                                                PlayerView(ctx).apply {
                                                    player = exoPlayer
                                                    useController = false
                                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (isTrailerStreamLoading) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = primaryColor,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    } else {
                                        AndroidView(
                                            factory = { ctx ->
                                                android.webkit.WebView(ctx).apply {
                                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                                    )
                                                    setBackgroundColor(android.graphics.Color.BLACK)
                                                    settings.apply {
                                                        javaScriptEnabled = true
                                                        domStorageEnabled = true
                                                        mediaPlaybackRequiresUserGesture = false
                                                        allowFileAccess = true
                                                        loadWithOverviewMode = true
                                                        useWideViewPort = true
                                                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                                    }
                                                    webChromeClient = android.webkit.WebChromeClient()
                                                    webViewClient = android.webkit.WebViewClient()
                                                    val embedHtml = """
                                                        <!DOCTYPE html>
                                                        <html>
                                                        <head>
                                                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                                        <style>
                                                        * { margin: 0; padding: 0; box-sizing: border-box; }
                                                        html, body { width: 100%; height: 100%; background-color: #000000; overflow: hidden; }
                                                        .video-container { position: relative; width: 100vw; height: 100vh; }
                                                        iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: none; }
                                                        </style>
                                                        </head>
                                                        <body>
                                                        <div class="video-container">
                                                        <iframe src="https://www.youtube.com/embed/${d.trailerId}?autoplay=1&playsinline=1&controls=1&fs=0&rel=0&enablejsapi=1" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>
                                                        </div>
                                                        </body>
                                                        </html>
                                                    """.trimIndent()
                                                    loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "UTF-8", null)
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(70.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color(0x7016181D),
                                                        Color(0xFF16181D)
                                                    )
                                                )
                                            )
                                    )

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = 16.dp, bottom = 48.dp)
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .clickable { isMuted = !isMuted },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isMuted) VolumeOffIcon else VolumeUpIcon,
                                            contentDescription = if (isMuted) "Unmute" else "Mute",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    if (isBackdropResolving && resolvedBackdropUrl.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF101115)),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            val fallbackPoster = d.coverUrl.ifEmpty { initialCover }
                                            if (fallbackPoster.isNotEmpty()) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(model = fallbackPoster),
                                                    contentDescription = d.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .blur(22.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.65f))
                                                )
                                            }
                                            LinearProgressIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(3.dp),
                                                color = primaryColor,
                                                trackColor = Color(0xFF23242E)
                                            )
                                        }
                                    } else {
                                        val finalBackdrop = resolvedBackdropUrl.ifEmpty { d.coverUrl.ifEmpty { initialCover } }
                                        val isUsingCoverBlur = resolvedBackdropUrl.isEmpty()
                                        if (finalBackdrop.isNotEmpty()) {
                                            Image(
                                                painter = rememberAsyncImagePainter(model = finalBackdrop),
                                                contentDescription = d.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .then(if (isUsingCoverBlur) Modifier.blur(22.dp) else Modifier)
                                            )
                                            if (isUsingCoverBlur) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.45f))
                                                )
                                            }
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color(0x30000000),
                                                        Color(0xFF16181D)
                                                    )
                                                )
                                            )
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val overlapPx = 45.dp.roundToPx()
                                        // Reduce measured layout height so next elements (countdown card) follow the visible bottom of the poster immediately
                                        layout(placeable.width, (placeable.height - overlapPx).coerceAtLeast(0)) {
                                            placeable.placeRelative(0, -overlapPx)
                                        }
                                    },
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(105.dp)
                                        .height(155.dp)
                                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(8.dp), spotColor = Color.Black)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF161616))
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = d.coverUrl.ifEmpty { initialCover }),
                                        contentDescription = d.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    if (animeLogoUrl.isNotEmpty()) {
                                        Image(
                                            painter = rememberAsyncImagePainter(model = animeLogoUrl),
                                            contentDescription = d.title,
                                            contentScale = ContentScale.Fit,
                                            alignment = Alignment.BottomStart,
                                            modifier = Modifier
                                                .heightIn(min = 45.dp, max = 75.dp)
                                                .fillMaxWidth()
                                        )
                                    } else if (!isBackdropResolving) {
                                        Text(
                                            text = d.title,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val yearStr = if (d.releaseYear > 0) "${d.releaseYear}" else "2024"
                                        val formatStr = when (d.format.uppercase()) {
                                            "MOVIE" -> "Movie"
                                            "OVA" -> "OVA"
                                            "ONA" -> "ONA"
                                            "SPECIAL" -> "Special"
                                            else -> if (d.episodesCount == 1) "Movie" else "TV Series"
                                        }
                                        val statusStr = when (d.status.uppercase()) {
                                            "FINISHED" -> "Completed"
                                            "RELEASING" -> "Ongoing"
                                            "NOT_YET_RELEASED" -> "Upcoming"
                                            else -> ""
                                        }

                                        Text(text = yearStr, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "•", color = Color(0xFF6B7280), fontSize = 11.5.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = formatStr, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        
                                        if (statusStr.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "•", color = Color(0xFF6B7280), fontSize = 11.5.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = statusStr, color = primaryColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }

                                        if (d.isAdult) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "•", color = Color(0xFF6B7280), fontSize = 11.5.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFDC2626), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 4.dp, vertical = 0.5.dp)
                                            ) {
                                                Text(
                                                    text = "18+",
                                                    color = Color.White,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    lineHeight = 10.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "•", color = Color(0xFF6B7280), fontSize = 11.5.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        // Official IMDb Golden Yellow Badge
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(0xFFF5C518))
                                                .padding(horizontal = 4.5.dp, vertical = 1.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "IMDb",
                                                color = Color.Black,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = (-0.3).sp,
                                                style = TextStyle(
                                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            val nextAiring = d.nextAiring
                            val hasUpcomingEpisode = nextAiring != null && (nextAiring.airingAt > System.currentTimeMillis() / 1000)
                            if (hasUpcomingEpisode && nextAiring != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                NextEpisodeCountdownCard(nextAiring = nextAiring)
                                Spacer(modifier = Modifier.height(4.dp))
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            val playEpNum = lastWatched?.episodeNumber ?: 1
                            val buttonText = if (lastWatched != null) "Resume Episode $playEpNum" else "Watch Now"
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        isStreamLoading = true
                                        coroutineScope.launch {
                                            val targetEpMeta = episodeMetaMap[playEpNum]
                                            val resolvedEpCover = getResolvedEpisodeCover(d.id, playEpNum, d.coverUrl, targetEpMeta)
                                            fetchAndPlayStream(d.id, playEpNum, d.title, resolvedEpCover, getPreferredStreamType(), d.coverUrl, d.episodesCount)
                                            isStreamLoading = false
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(3.dp)
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = buttonText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.Black
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(52.dp)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFF2B2E38))
                                        .clickable {
                                            if (com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.isKitsuModeActive || AniListClient.lastErrorMessage != null) {
                                                showKitsuMyListRestrictionDialog = true
                                            } else {
                                                showAddToListDialog = true
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isInLibrary) androidx.compose.material.icons.Icons.Default.Check else androidx.compose.material.icons.Icons.Default.Add,
                                        contentDescription = "My List",
                                        tint = if (isInLibrary) primaryColor else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            if (!isDescriptionExpanded) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = d.description,
                                        fontSize = 13.5.sp,
                                        color = Color(0xFFBDC7D5),
                                        lineHeight = 20.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Clip
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color(0xCC000000),
                                                        Color(0xFF000000)
                                                    )
                                                )
                                            )
                                            .clickable { isDescriptionExpanded = true }
                                            .padding(top = 18.dp, bottom = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Read More",
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = d.description,
                                        fontSize = 13.5.sp,
                                        color = Color(0xFFBDC7D5),
                                        lineHeight = 20.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isDescriptionExpanded = false }
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Read Less",
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            val actContext = LocalContext.current
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, "Check out ${d.title} on FireFly!\nhttps://anilist.co/anime/${d.id}")
                                                type = "text/plain"
                                            }
                                            actContext.startActivity(Intent.createChooser(sendIntent, "Share ${d.title}"))
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = ShareBoxArrowIcon,
                                        contentDescription = "Share",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Share",
                                        color = Color(0xFFBDC7D5),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val scoreDisplay = if (d.score > 0) "${d.score}% Community Score" else "No Score Yet"
                                            Toast.makeText(actContext, "${d.title}: $scoreDisplay", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    UserScoreCircularProgress(
                                        scorePercentage = d.score,
                                        size = 32.dp,
                                        strokeWidth = 2.8.dp,
                                        accentColor = primaryColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "User Score",
                                        color = Color(0xFFBDC7D5),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                val activeAnimeDownloads = allEpisodeStates.filter {
                                    it.anilistId == anilistId &&
                                    (it.status == com.lagradost.cloudstream3.ui.animebox.download.DownloadStatus.DOWNLOADING ||
                                     it.status == com.lagradost.cloudstream3.ui.animebox.download.DownloadStatus.PENDING)
                                }
                                val isAnyDownloading = activeAnimeDownloads.isNotEmpty()
                                val overallProgress = if (isAnyDownloading) {
                                    (activeAnimeDownloads.map { it.downloadProgress }.average()).toInt()
                                } else 0

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            showDownloadSeasonDialog = true
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    if (isAnyDownloading) {
                                        Box(
                                            modifier = Modifier.size(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                progress = (overallProgress / 100f).coerceIn(0f, 1f),
                                                color = Color.White,
                                                trackColor = Color(0xFF2E2E34),
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                text = "$overallProgress%",
                                                color = Color.White,
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Downloading",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        Icon(
                                            imageVector = CloudDownloadIcon,
                                            contentDescription = "Download",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Download",
                                            color = Color(0xFFBDC7D5),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (isTrailerPlaying) {
                                                isTrailerPlaying = false
                                                trailerStreamUrl = null
                                            } else {
                                                val targetTrailer = d.trailerId
                                                val isTrailerActive = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isTrailerEnabled(this@AnimeBoxDetailActivity)
                                                if (targetTrailer.isNotEmpty()) {
                                                    if (isTrailerActive) {
                                                        isTrailerPlaying = true
                                                    } else {
                                                        showTrailerConfirmDialog = true
                                                    }
                                                } else {
                                                    coroutineScope.launch {
                                                        Toast.makeText(actContext, "Finding trailer...", Toast.LENGTH_SHORT).show()
                                                        val resolved = resolveFallbackTrailer(anilistId, d.idMal, d.title)
                                                        if (resolved.isNotEmpty()) {
                                                            detail = d.copy(trailerId = resolved)
                                                            if (isTrailerActive) {
                                                                isTrailerPlaying = true
                                                            } else {
                                                                showTrailerConfirmDialog = true
                                                            }
                                                        } else {
                                                            Toast.makeText(actContext, "Trailer not available for this anime", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    if (isTrailerPlaying) {
                                        PlayingAudioWaveIndicator(
                                            waveColor = primaryColor,
                                            barWidth = 2.5.dp,
                                            spacing = 1.5.dp,
                                            modifier = Modifier.size(width = 20.dp, height = 18.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = CustomSegmentedPlayIcon,
                                            contentDescription = "Trailer",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isTrailerPlaying) "Playing" else "Trailer",
                                        color = if (isTrailerPlaying) primaryColor else Color(0xFFBDC7D5),
                                        fontSize = 11.sp,
                                        fontWeight = if (isTrailerPlaying) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }

                        Spacer(modifier = Modifier.height(6.dp))

                        Spacer(modifier = Modifier.height(10.dp))

                        if (d.characters.isNotEmpty() && anilistId != 158198 && anilistId != 83307 && !d.title.contains("Ninja Hattori-kun Returns", ignoreCase = true)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isCastExpanded = !isCastExpanded }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Cast & Characters",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Icon(
                                    imageVector = if (isCastExpanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isCastExpanded) "Collapse" else "Expand",
                                    tint = Color(0xFFBDC7D5),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            if (isCastExpanded) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    items(d.characters) { char ->
                                        CharacterCastCard(char)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                    val isShinChanAnime = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(d.id)
                    val hasSpecials = isShinChanAnime && shinChanSpecials.isNotEmpty()

                    TabRow(
                        selectedTabIndex = selectedSectionTab,
                        containerColor = Color.Black,
                        contentColor = Color.White,
                        indicator = { tabPositions ->
                            if (selectedSectionTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedSectionTab]),
                                    color = Color.White
                                )
                            }
                        },
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Tab(
                            selected = selectedSectionTab == 0,
                            onClick = { selectedSectionTab = 0 },
                            text = { Text("Episodes", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        )
                        if (hasSpecials) {
                            Tab(
                                selected = selectedSectionTab == 1,
                                onClick = { selectedSectionTab = 1 },
                                text = { Text("Specials (${shinChanSpecials.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                            )
                        }
                        Tab(
                            selected = selectedSectionTab == (if (hasSpecials) 2 else 1),
                            onClick = { selectedSectionTab = if (hasSpecials) 2 else 1 },
                            text = { Text("More Like This", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        )
                        Tab(
                            selected = selectedSectionTab == (if (hasSpecials) 3 else 2),
                            onClick = { selectedSectionTab = if (hasSpecials) 3 else 2 },
                            text = { Text("Related", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        )
                    }

                    val activeSectionTab = if (hasSpecials) {
                        selectedSectionTab
                    } else {
                        if (selectedSectionTab >= 1) selectedSectionTab + 1 else selectedSectionTab
                    }

                    when (activeSectionTab) {
                        0 -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E1E24))
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = CustomSearchIcon,
                                        contentDescription = "Search",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = episodeSearchQuery,
                                        onValueChange = { episodeSearchQuery = it },
                                        singleLine = true,
                                        cursorBrush = SolidColor(Color.White),
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        decorationBox = { innerTextField ->
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                if (episodeSearchQuery.isEmpty()) {
                                                    Text("Search episode...", color = Color.Gray, fontSize = 13.5.sp)
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                    if (episodeSearchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { episodeSearchQuery = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(imageVector = androidx.compose.material.icons.Icons.Default.Check, contentDescription = "Clear", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    IconButton(
                                        onClick = { 
                                            episodeViewMode = if (episodeViewMode == "image") "number" else "image"
                                            com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setDefaultEpisodeViewMode(this@AnimeBoxDetailActivity, episodeViewMode)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (episodeViewMode == "image") GridViewIcon else ListViewIcon,
                                            contentDescription = "Change View Mode",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(2.dp))

                                    IconButton(
                                        onClick = { episodeSortOrder = if (episodeSortOrder == "asc") "desc" else "asc" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = SortIconVector,
                                            contentDescription = "Sort",
                                            tint = if (episodeSortOrder == "desc") primaryColor else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            val sortedEpisodes = if (episodeSortOrder == "desc") {
                                episodesList.reversed()
                            } else {
                                episodesList
                            }

                            val filteredEpisodes = if (episodeSearchQuery.isNotEmpty()) {
                                sortedEpisodes.filter { epNum ->
                                    val meta = episodeMetaMap[epNum]
                                    val epTitle = meta?.title ?: "Episode $epNum"
                                    epTitle.contains(episodeSearchQuery, ignoreCase = true) || epNum.toString() == episodeSearchQuery.trim()
                                }
                            } else {
                                sortedEpisodes.take(visibleEpisodesCount)
                            }

                            if (isStreamLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                            .height(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = primaryColor)
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (filteredEpisodes.isEmpty()) {
                                            Text(
                                                text = "No episodes found matching \"$episodeSearchQuery\"",
                                                color = Color.Gray,
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(vertical = 16.dp)
                                            )
                                        } else {
                                            if (episodeViewMode == "image") {
                                                val isMovie = (d.format.uppercase() == "MOVIE") || (d.episodesCount == 1)
                                                filteredEpisodes.forEach { epNum ->
                                                    val meta = episodeMetaMap[epNum]
                                                    var epTitle = meta?.title ?: "Episode $epNum"
                                                    if (isShinChanAnime) {
                                                        if (epTitle.any { it.code in 0x3040..0x309F || it.code in 0x30A0..0x30FF || it.code in 0x4E00..0x9FAF } || epTitle.isBlank() || epTitle.equals("null", ignoreCase = true)) {
                                                            epTitle = "Episode $epNum"
                                                        }
                                                    }
                                                    val defaultCardImage = if (isShinChanAnime) {
                                                        com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getAllEpisodeCovers(this@AnimeBoxDetailActivity)[epNum] ?: meta?.imageUrl ?: d.coverUrl
                                                    } else {
                                                        meta?.imageUrl ?: d.coverUrl
                                                    }
                                                    EpisodeRowCard(
                                                        anilistId = d.id,
                                                        episodeNum = epNum,
                                                        title = epTitle,
                                                        defaultImageUrl = defaultCardImage,
                                                        meta = meta,
                                                        isLastWatched = (lastWatched != null && lastWatched.episodeNumber == epNum),
                                                        isMovie = isMovie,
                                                        movieBackdropUrl = resolvedBackdropUrl,
                                                        onClick = {
                                                            isStreamLoading = true
                                                            coroutineScope.launch {
                                                                val resolvedEpCover = getResolvedEpisodeCover(d.id, epNum, d.coverUrl, meta)
                                                                fetchAndPlayStream(d.id, epNum, d.title, resolvedEpCover, getPreferredStreamType(), d.coverUrl, d.episodesCount)
                                                                isStreamLoading = false
                                                            }
                                                        },
                                                        onDownloadClick = {
                                                            episodeToDownload = Pair(epNum, epTitle)
                                                        }
                                                    )
                                                }
                                            } else {
                                                 val numCols = 6
                                                 val chunkedNumbers = filteredEpisodes.chunked(numCols)
                                                 Column(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     verticalArrangement = Arrangement.spacedBy(8.dp)
                                                 ) {
                                                     chunkedNumbers.forEach { rowCols ->
                                                         Row(
                                                             modifier = Modifier.fillMaxWidth(),
                                                             horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                         ) {
                                                             rowCols.forEach { epNum ->
                                                                 val meta = episodeMetaMap[epNum]
                                                                 val isLast = (lastWatched != null && lastWatched.episodeNumber == epNum)
                                                                 Box(
                                                                     modifier = Modifier
                                                                         .weight(1f)
                                                                         .aspectRatio(1f)
                                                                         .clip(RoundedCornerShape(10.dp))
                                                                         .background(if (isLast) primaryColor else Color(0xFF2C2D35))
                                                                         .clickable {
                                                                             isStreamLoading = true
                                                                             coroutineScope.launch {
                                                                                 val resolvedEpCover = getResolvedEpisodeCover(d.id, epNum, d.coverUrl, meta)
                                                                                 fetchAndPlayStream(d.id, epNum, d.title, resolvedEpCover, getPreferredStreamType(), d.coverUrl, d.episodesCount)
                                                                                 isStreamLoading = false
                                                                             }
                                                                         },
                                                                     contentAlignment = Alignment.Center
                                                                 ) {
                                                                     if (isLast) {
                                                                         PlayingAudioWaveIndicator(
                                                                             modifier = Modifier
                                                                                 .align(Alignment.TopEnd)
                                                                                 .padding(top = 6.dp, end = 6.dp)
                                                                                 .height(8.dp),
                                                                             waveColor = Color.Black,
                                                                             barWidth = 1.6.dp,
                                                                             spacing = 1.2.dp
                                                                         )
                                                                     }

                                                                     Text(
                                                                         text = "$epNum",
                                                                         color = if (isLast) Color.Black else Color(0xFFE2E2EA),
                                                                         fontSize = 14.sp,
                                                                         fontWeight = if (isLast) FontWeight.Bold else FontWeight.SemiBold
                                                                     )
                                                                 }
                                                             }
                                                             if (rowCols.size < numCols) {
                                                                 repeat(numCols - rowCols.size) {
                                                                     Spacer(modifier = Modifier.weight(1f))
                                                                 }
                                                             }
                                                         }
                                                     }
                                                 }
                                            }

                                            if (episodeSearchQuery.isEmpty() && episodesList.size > visibleEpisodesCount) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = { visibleEpisodesCount += 50 },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(44.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = primaryColor,
                                                        contentColor = Color.Black
                                                    ),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "Load More Episodes",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (shinChanSpecials.isEmpty()) {
                                        Text(
                                            text = "No special episodes found.",
                                            color = Color.Gray,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    } else {
                                        val chunkedSpecials = shinChanSpecials.chunked(2)
                                        chunkedSpecials.forEach { rowPair ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                rowPair.forEach { sp ->
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color(0xFF1E1E24))
                                                            .clickable {
                                                                isStreamLoading = true
                                                                coroutineScope.launch {
                                                                    fetchAndPlayStream(
                                                                        d.id,
                                                                        1,
                                                                        "${d.title} - ${sp.title}",
                                                                        sp.coverImageUrl.ifEmpty { d.coverUrl },
                                                                        getPreferredStreamType(),
                                                                        d.coverUrl,
                                                                        d.episodesCount,
                                                                        explicitUrl = sp.mobileUrl
                                                                    )
                                                                    isStreamLoading = false
                                                                }
                                                            }
                                                    ) {
                                                        Column {
                                                            Image(
                                                                painter = rememberAsyncImagePainter(model = sp.coverImageUrl.ifEmpty { d.coverUrl }),
                                                                contentDescription = sp.title,
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(105.dp),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                            Text(
                                                                text = sp.title,
                                                                color = Color.White,
                                                                fontSize = 12.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.padding(8.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                if (rowPair.size < 2) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                val filteredRecs = d.recommendations.filter { rec ->
                                    val format = rec.format.uppercase()
                                    format == "TV" || format == "MOVIE" || format == "TV_SHORT"
                                }

                                if (filteredRecs.isEmpty()) {
                                    Text(
                                        text = "No similar animes found.",
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                } else {
                                    val chunkedRecs = filteredRecs.chunked(2)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        chunkedRecs.forEach { rowPair ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                rowPair.forEach { rec ->
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        DetailAnimeGridPosterCard(
                                                            id = rec.id,
                                                            title = rec.title,
                                                            coverUrl = rec.coverUrl,
                                                            badgeText = if (rec.format.uppercase() == "MOVIE") "Movie" else "",
                                                            epCount = 12,
                                                            onClick = {
                                                                val intent = Intent(this@AnimeBoxDetailActivity, AnimeBoxDetailActivity::class.java).apply {
                                                                    putExtra("anilistId", rec.id)
                                                                }
                                                                startActivity(intent)
                                                            }
                                                        )
                                                    }
                                                }
                                                if (rowPair.size < 2) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            3 -> {
                                val relatedList = d.relations.filter { rel ->
                                    val formatUpper = rel.format.uppercase()
                                    val relTypeUpper = rel.relationType.uppercase()
                                    val isTvOrMovie = (formatUpper == "TV" || formatUpper == "MOVIE" || formatUpper == "TV_SHORT")
                                    val isSequelPrequelOrMovieSideStory = (relTypeUpper == "SEQUEL" || relTypeUpper == "PREQUEL" || (formatUpper == "MOVIE" && relTypeUpper == "SIDE_STORY"))
                                    isTvOrMovie && isSequelPrequelOrMovieSideStory
                                }

                                if (relatedList.isEmpty()) {
                                    Text(
                                        text = "No related prequels or sequels found.",
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                } else {
                                    val chunkedRels = relatedList.chunked(2)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        chunkedRels.forEach { rowPair ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                rowPair.forEach { rel ->
                                                    val badgeText = when {
                                                        rel.format.equals("MOVIE", ignoreCase = true) -> "Movie"
                                                        rel.relationType.equals("SEQUEL", ignoreCase = true) -> "Sequel"
                                                        rel.relationType.equals("PREQUEL", ignoreCase = true) -> "Prequel"
                                                        rel.format.equals("SPECIAL", ignoreCase = true) -> "Special"
                                                        else -> rel.relationType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                                                    }

                                                    Box(modifier = Modifier.weight(1f)) {
                                                        DetailAnimeGridPosterCard(
                                                            id = rel.id,
                                                            title = rel.title,
                                                            coverUrl = rel.coverUrl,
                                                            badgeText = badgeText,
                                                            epCount = 12,
                                                            onClick = {
                                                                val intent = Intent(this@AnimeBoxDetailActivity, AnimeBoxDetailActivity::class.java).apply {
                                                                    putExtra("anilistId", rel.id)
                                                                }
                                                                startActivity(intent)
                                                            }
                                                        )
                                                    }
                                                }
                                                if (rowPair.size < 2) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                    }

                    // Floating Top Schedule Banner for Ongoing Series
                    val isOngoing = (d.nextAiring != null) || 
                            d.status.contains("RELEASING", ignoreCase = true) || 
                            d.status.contains("Ongoing", ignoreCase = true)
                    
                    var showScheduleBanner by remember(d.id) { mutableStateOf(isOngoing) }
                    var bannerDismissed by remember(d.id) { mutableStateOf(false) }
                    val bannerScope = rememberCoroutineScope()
                    val bannerOffsetX = remember(d.id) { androidx.compose.animation.core.Animatable(0f) }

                    AnimatedVisibility(
                        visible = showScheduleBanner && !bannerDismissed && isOngoing && isNetworkAvailable(this@AnimeBoxDetailActivity),
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(150)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(bannerOffsetX.value.roundToInt(), 0) }
                                .alpha((1f - (abs(bannerOffsetX.value) / 320f)).coerceIn(0f, 1f))
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            bannerScope.launch {
                                                if (abs(bannerOffsetX.value) > 100f) {
                                                    val target = if (bannerOffsetX.value > 0) 1200f else -1200f
                                                    bannerOffsetX.animateTo(target, androidx.compose.animation.core.tween(200))
                                                    bannerDismissed = true
                                                    showScheduleBanner = false
                                                } else {
                                                    bannerOffsetX.animateTo(0f, androidx.compose.animation.core.tween(150))
                                                }
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            bannerScope.launch {
                                                bannerOffsetX.snapTo(bannerOffsetX.value + dragAmount)
                                            }
                                        }
                                    )
                                }
                                .shadow(elevation = 18.dp, shape = RoundedCornerShape(14.dp), spotColor = Color.Black)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF141418))
                                .clickable {
                                    val intent = Intent(this@AnimeBoxDetailActivity, AnimeBoxScheduleActivity::class.java)
                                    startActivity(intent)
                                    showScheduleBanner = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Chibi Character Avatar
                                Image(
                                    painter = painterResource(id = R.drawable.firefly_chibi_suggestion),
                                    contentDescription = "Schedule Guide",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "Never miss an episode!",
                                            color = Color.White,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        IconButton(
                                            onClick = {
                                                bannerDismissed = true
                                                showScheduleBanner = false
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close",
                                                tint = Color(0xFF7A7D8F),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = "Check out the weekly release schedule to track your favorite airing anime",
                                        color = Color(0xFF9E9EA8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 15.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF24252E))
                                            .clickable {
                                                val intent = Intent(this@AnimeBoxDetailActivity, AnimeBoxScheduleActivity::class.java)
                                                startActivity(intent)
                                                showScheduleBanner = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = "Calendar",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "View Release Schedule",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showTrailerConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showTrailerConfirmDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setTrailerEnabled(this@AnimeBoxDetailActivity, true)
                            showTrailerConfirmDialog = false
                            trailerStreamUrl = null
                            isTrailerPlaying = true
                        }) {
                            Text("Enable & Play", color = primaryColor, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTrailerConfirmDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    },
                    title = {
                        Text(
                            text = "Enable Trailer Mode",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    text = {
                        Text(
                            text = "Trailer playback is currently disabled in Settings. Would you like to enable it now to play trailers?",
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    },
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    textContentColor = Color.LightGray
                )
            }

            if (showAddToListDialog && detail != null) {
                val d = detail!!
                val animeBrief = AnimeBrief(
                    id = d.id,
                    title = d.title,
                    coverUrl = d.coverUrl,
                    bannerUrl = d.bannerUrl,
                    description = d.description,
                    genres = d.genres,
                    averageScore = d.score,
                    logoUrl = animeLogoUrl,
                    episodes = d.episodesCount,
                    status = ""
                )
                val currentCat = if (isInLibrary) libraryManager.getAnimeCategory(d.id) else "Plan to Watch"

                com.lagradost.cloudstream3.ui.animebox.library.SelectWatchStatusDialog(
                    isInLibrary = isInLibrary,
                    currentCategory = currentCat,
                    onSelectStatus = { selectedStatus ->
                        libraryManager.addOrUpdateLibraryItem(animeBrief, selectedStatus)
                        isInLibrary = true
                        showAddToListDialog = false
                        Toast.makeText(this@AnimeBoxDetailActivity, "Added to \"$selectedStatus\"", Toast.LENGTH_SHORT).show()
                    },
                    onRemoveFromLibrary = {
                        libraryManager.removeLibraryItem(d.id)
                        isInLibrary = false
                        showAddToListDialog = false
                        Toast.makeText(this@AnimeBoxDetailActivity, "Removed from Library", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = {
                        showAddToListDialog = false
                    }
                )
            }

            if (episodeToDownload != null && detail != null) {
                    val (epNum, epTitle) = episodeToDownload!!
                    val d = detail!!
                    val preferredLang = getPreferredStreamType()
                    var selectedAudioLang by remember { mutableStateOf(preferredLang) }
                    var isStartingDownload by remember { mutableStateOf(false) }

                    Dialog(
                        onDismissRequest = { if (!isStartingDownload) episodeToDownload = null },
                        properties = DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                                .clickable { if (!isStartingDownload) episodeToDownload = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF121318))
                                    .clickable(enabled = false) {}
                                    .padding(horizontal = 18.dp, vertical = 22.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Download Episode $epNum",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = d.title,
                                    color = Color(0xFF8E8E9B),
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                val audioOptions = listOf(
                                    Triple("sub", "Japanese (Original)", "Original Japanese Audio • English Subtitles"),
                                    Triple("dub", "English Dubbed", "English Voice Audio"),
                                    Triple("hindi", "Hindi Dubbed", "Hindi Voice Audio (If Available)")
                                )

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    audioOptions.forEach { (type, label, desc) ->
                                        val isSelected = selectedAudioLang == type
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) Color.White else Color(0xFF1D1E26))
                                                .clickable { selectedAudioLang = type }
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) Color.Black else Color.White,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 15.sp,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = {
                                        isStartingDownload = true
                                        val langChoice = selectedAudioLang
                                        val targetLang = langChoice
                                        coroutineScope.launch {
                                            Toast.makeText(this@AnimeBoxDetailActivity, "Starting download for Episode $epNum...", Toast.LENGTH_SHORT).show()
                                            var streamInfo = withContext(Dispatchers.IO) {
                                                val okSpecialUrl = com.lagradost.cloudstream3.ui.animebox.extractors.PokemonHindiExtractor.POKEMON_OKRU_SPECIAL_URLS[d.id]
                                                val useOkRu = when {
                                                    com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(d.id) -> (targetLang == "sub" || targetLang == "hardsub")
                                                    okSpecialUrl != null -> (targetLang == "dub" || targetLang == "sub" || targetLang == "hardsub")
                                                    else -> false
                                                }
                                                if (useOkRu) {
                                                    val scStream = com.lagradost.cloudstream3.ui.animebox.extractors.ShinChanOkRuExtractor.extractShinChanStream(d.id, epNum, preferDirectMp4 = true, explicitVideoUrl = okSpecialUrl)
                                                    if (scStream != null && (scStream.mp4Url?.isNotEmpty() == true || scStream.hlsUrl.isNotEmpty())) {
                                                        mapOf(
                                                            "hls" to (scStream.mp4Url ?: scStream.hlsUrl),
                                                            "referer" to scStream.referer,
                                                            "subtitle" to "",
                                                            "subtitlesJson" to "[]"
                                                        )
                                                    } else null
                                                } else {
                                                    com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(this@AnimeBoxDetailActivity, d.id, epNum, targetLang, d.title)
                                                }
                                            }
                                            var directHls = (streamInfo?.get("hls") as? String) ?: ""
                                            var actualStreamType = targetLang
                                            if (directHls.isEmpty() && targetLang == "hardsub") {
                                                streamInfo = withContext(Dispatchers.IO) {
                                                    com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(this@AnimeBoxDetailActivity, d.id, epNum, "sub", d.title)
                                                }
                                                directHls = (streamInfo?.get("hls") as? String) ?: ""
                                                actualStreamType = "sub"
                                            }

                                            val directRef = (streamInfo?.get("referer") as? String) ?: ""
                                            val directSub = (streamInfo?.get("subtitle") as? String) ?: ""
                                            val subsJson = (streamInfo?.get("subtitlesJson") as? String) ?: "[]"
                                            val iStart = (streamInfo?.get("introStart") as? Long) ?: 0L
                                            val iEnd = (streamInfo?.get("introEnd") as? Long) ?: 0L
                                            val oStart = (streamInfo?.get("outroStart") as? Long) ?: 0L
                                            val oEnd = (streamInfo?.get("outroEnd") as? Long) ?: 0L

                                            if (directHls.isNotEmpty()) {
                                                val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(d.id)
                                                val epMeta = episodeMetaMap[epNum]
                                                val epCover = if (isShinChan) {
                                                    com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getAllEpisodeCovers()[epNum] ?: d.coverUrl
                                                } else epMeta?.imageUrl?.ifEmpty { d.coverUrl } ?: d.coverUrl
                                                val showPoster = if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_POSTER_URL else d.coverUrl.ifEmpty { initialCover }
                                                val epBackdrop = if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_BACKDROP_URL else resolvedBackdropUrl.ifEmpty { d.bannerUrl }
                                                val epLogo = if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_LOGO_URL else animeLogoUrl
                                                val langTag = if (actualStreamType == "hindi") "Hindi Dub" else if (actualStreamType == "dub") "English Dub" else if (actualStreamType == "hardsub") "HSub" else "Sub"

                                                downloadManager.enqueueDownload(
                                                    anilistId = d.id,
                                                    animeTitle = d.title,
                                                    episodeNum = epNum,
                                                    episodeTitle = epTitle,
                                                    coverUrl = epCover,
                                                    showCoverUrl = showPoster,
                                                    quality = "1080p $langTag",
                                                    resolvedUrl = directHls,
                                                    referer = directRef,
                                                    subtitleUrl = directSub,
                                                    subtitlesJson = subsJson,
                                                    introStart = iStart,
                                                    introEnd = iEnd,
                                                    outroStart = oStart,
                                                    outroEnd = oEnd,
                                                    streamType = actualStreamType,
                                                    providedBackdrop = epBackdrop,
                                                    providedLogo = epLogo
                                                )
                                                Toast.makeText(this@AnimeBoxDetailActivity, "Episode $epNum queued for download", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(this@AnimeBoxDetailActivity, "Audio track unavailable for Episode $epNum", Toast.LENGTH_SHORT).show()
                                            }
                                            episodeToDownload = null
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isStartingDownload
                                ) {
                                    if (isStartingDownload) {
                                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Download", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                TextButton(
                                    onClick = { episodeToDownload = null },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                ) {
                                    Text(
                                        text = "Cancel",
                                        color = Color(0xFF8E8E9B),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                if (showDownloadSeasonDialog && detail != null) {
                    val d = detail!!
                    val totalSeasonEps = if (d.episodesCount > 0) d.episodesCount else if (episodeMetaMap.isNotEmpty()) episodeMetaMap.size else 12
                    var selectedAudioLang by remember { mutableStateOf("sub") }
                    var isStartingSeasonDownload by remember { mutableStateOf(false) }

                    Dialog(
                        onDismissRequest = { if (!isStartingSeasonDownload) showDownloadSeasonDialog = false },
                        properties = DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                                .clickable { if (!isStartingSeasonDownload) showDownloadSeasonDialog = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF121318))
                                    .clickable(enabled = false) {}
                                    .padding(horizontal = 18.dp, vertical = 22.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Download Whole Season",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$totalSeasonEps Episodes • ${d.title}",
                                    color = Color(0xFF8E8E9B),
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                val audioOptions = listOf(
                                    Triple("sub", "Japanese (Original)", "Original Japanese Audio • English Subtitles"),
                                    Triple("dub", "English Dubbed", "English Voice Audio"),
                                    Triple("hindi", "Hindi Dubbed", "Hindi Voice Audio (If Available)")
                                )

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    audioOptions.forEach { (type, label, desc) ->
                                        val isSelected = selectedAudioLang == type
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) Color.White else Color(0xFF1D1E26))
                                                .clickable { selectedAudioLang = type }
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) Color.Black else Color.White,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 15.sp,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = {
                                        isStartingSeasonDownload = true
                                        val langChoice = selectedAudioLang
                                        val targetLang = langChoice
                                        coroutineScope.launch {
                                            Toast.makeText(this@AnimeBoxDetailActivity, "Starting downloads for all $totalSeasonEps episodes...", Toast.LENGTH_LONG).show()
                                            val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(d.id)
                                            val showPoster = if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_POSTER_URL else d.coverUrl.ifEmpty { initialCover }
                                            val epBackdrop = if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_BACKDROP_URL else resolvedBackdropUrl.ifEmpty { d.bannerUrl }
                                            val epLogo = if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_LOGO_URL else animeLogoUrl

                                            for (epNum in 1..totalSeasonEps) {
                                                val epMeta = episodeMetaMap[epNum]
                                                val epTitle = epMeta?.title ?: "Episode $epNum"
                                                val epCover = if (isShinChan) {
                                                    com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getAllEpisodeCovers(this@AnimeBoxDetailActivity)[epNum] ?: epMeta?.imageUrl ?: d.coverUrl
                                                } else epMeta?.imageUrl?.ifEmpty { d.coverUrl } ?: d.coverUrl

                                                var streamInfo = withContext(Dispatchers.IO) {
                                                    val okSpecialUrl = com.lagradost.cloudstream3.ui.animebox.extractors.PokemonHindiExtractor.POKEMON_OKRU_SPECIAL_URLS[d.id]
                                                    val useOkRu = when {
                                                        com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(d.id) -> (targetLang == "sub" || targetLang == "hardsub")
                                                        okSpecialUrl != null -> (targetLang == "dub" || targetLang == "sub" || targetLang == "hardsub")
                                                        else -> false
                                                    }
                                                    if (useOkRu) {
                                                        val scStream = com.lagradost.cloudstream3.ui.animebox.extractors.ShinChanOkRuExtractor.extractShinChanStream(d.id, epNum, preferDirectMp4 = true, explicitVideoUrl = okSpecialUrl)
                                                        if (scStream != null && (scStream.mp4Url?.isNotEmpty() == true || scStream.hlsUrl.isNotEmpty())) {
                                                            mapOf(
                                                                "hls" to (scStream.mp4Url ?: scStream.hlsUrl),
                                                                "referer" to scStream.referer,
                                                                "subtitle" to "",
                                                                "subtitlesJson" to "[]"
                                                            )
                                                        } else null
                                                    } else {
                                                        com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(this@AnimeBoxDetailActivity, d.id, epNum, targetLang, d.title)
                                                    }
                                                }
                                                var directHls = (streamInfo?.get("hls") as? String) ?: ""
                                                var actualStreamType = targetLang
                                                if (directHls.isEmpty() && targetLang == "hardsub") {
                                                    streamInfo = withContext(Dispatchers.IO) {
                                                        com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(this@AnimeBoxDetailActivity, d.id, epNum, "sub", d.title)
                                                    }
                                                    directHls = (streamInfo?.get("hls") as? String) ?: ""
                                                    actualStreamType = "sub"
                                                }

                                                val directRef = (streamInfo?.get("referer") as? String) ?: ""
                                                val directSub = (streamInfo?.get("subtitle") as? String) ?: ""
                                                val subsJson = (streamInfo?.get("subtitlesJson") as? String) ?: "[]"
                                                val iStart = (streamInfo?.get("introStart") as? Long) ?: 0L
                                                val iEnd = (streamInfo?.get("introEnd") as? Long) ?: 0L
                                                val oStart = (streamInfo?.get("outroStart") as? Long) ?: 0L
                                                val oEnd = (streamInfo?.get("outroEnd") as? Long) ?: 0L

                                                if (directHls.isNotEmpty()) {
                                                    val langTag = if (actualStreamType == "hindi") "Hindi Dub" else if (actualStreamType == "dub") "English Dub" else if (actualStreamType == "hardsub") "HSub" else "Sub"
                                                    downloadManager.enqueueDownload(
                                                        anilistId = d.id,
                                                        animeTitle = d.title,
                                                        episodeNum = epNum,
                                                        episodeTitle = epTitle,
                                                        coverUrl = epCover,
                                                        showCoverUrl = showPoster,
                                                        quality = "1080p $langTag",
                                                        resolvedUrl = directHls,
                                                        referer = directRef,
                                                        subtitleUrl = directSub,
                                                        subtitlesJson = subsJson,
                                                        introStart = iStart,
                                                        introEnd = iEnd,
                                                        outroStart = oStart,
                                                        outroEnd = oEnd,
                                                        streamType = actualStreamType,
                                                        providedBackdrop = epBackdrop,
                                                        providedLogo = epLogo
                                                    )
                                                }
                                            }
                                            Toast.makeText(this@AnimeBoxDetailActivity, "All $totalSeasonEps episodes queued for download", Toast.LENGTH_SHORT).show()
                                            showDownloadSeasonDialog = false
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isStartingSeasonDownload
                                ) {
                                    if (isStartingSeasonDownload) {
                                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Download Season ($totalSeasonEps Episodes)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                TextButton(
                                    onClick = { showDownloadSeasonDialog = false },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                ) {
                                    Text(
                                        text = "Cancel",
                                        color = Color(0xFF8E8E9B),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

            if (showKitsuMyListRestrictionDialog) {
                AlertDialog(
                    onDismissRequest = { showKitsuMyListRestrictionDialog = false },
                    confirmButton = {
                        TextButton(onClick = { showKitsuMyListRestrictionDialog = false }) {
                            Text("OK", color = primaryColor, fontWeight = FontWeight.Bold)
                        }
                    },
                    title = {
                        Text(
                            text = "My List Unavailable in Fallback Mode",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    },
                    text = {
                        Text(
                            text = "Adding anime to My List is only supported when connected to AniList. Please try again once AniList servers are back online.",
                            color = Color.LightGray,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp
                        )
                    },
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    textContentColor = Color.LightGray
                )
            }

            if (showTrailerConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showTrailerConfirmDialog = false },
                    title = {
                        Text(
                            text = "Autoplay Previews & Trailers",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    },
                    text = {
                        Text(
                            text = "To stream official anime trailers and previews directly on the detail page, enable Autoplay Previews & Trailers.",
                            color = Color.LightGray,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setAutoplayPreviewsEnabled(this@AnimeBoxDetailActivity, true)
                                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setTrailerEnabled(this@AnimeBoxDetailActivity, true)
                                showTrailerConfirmDialog = false
                                isTrailerPlaying = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Turn On & Play",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTrailerConfirmDialog = false }) {
                            Text("Cancel", color = Color.Gray, fontSize = 13.5.sp)
                        }
                    },
                    containerColor = Color(0xFF1E1E24),
                    titleContentColor = Color.White,
                    textContentColor = Color.LightGray
                )
            }

            if (showReportDialog) {
                val coroutineScope = rememberCoroutineScope()
                IssueReportDialog(
                    animeTitle = reportAnimeTitleExtra,
                    episodeNum = reportEpisodeExtra,
                    onDismiss = { showReportDialog = false },
                    onSubmit = { userName, email, issueType, description ->
                        showReportDialog = false
                        Toast.makeText(this@AnimeBoxDetailActivity, "Submitting report...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            val actProfile = com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager.getActiveProfile(this@AnimeBoxDetailActivity)
                            val syncUid = com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager.resolveSyncUserId(this@AnimeBoxDetailActivity, actProfile)
                            val success = com.lagradost.cloudstream3.ui.animebox.notifications.SupabaseReportManager.submitReport(
                                userName = userName,
                                email = email,
                                animeTitle = reportAnimeTitleExtra,
                                anilistId = anilistId,
                                episodeNumber = reportEpisodeExtra,
                                issueType = issueType,
                                description = description,
                                userId = syncUid
                            )
                            if (success) {
                                Toast.makeText(this@AnimeBoxDetailActivity, "Report received for Ep $reportEpisodeExtra! Our team is on it.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@AnimeBoxDetailActivity, "Report saved. Thank you for your feedback!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

    @Composable
    private fun IssueReportDialog(
        animeTitle: String,
        episodeNum: Int,
        onDismiss: () -> Unit,
        onSubmit: (userName: String, email: String, issueType: String, description: String) -> Unit
    ) {
        val context = LocalContext.current
        val primaryColor = MaterialTheme.colorScheme.primary
        val actProfile = remember { com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager.getActiveProfile(context) }
        val userProfile = remember(actProfile) { com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager.getProfiles(context).find { it.id == actProfile } }
        val anilistUser = remember(actProfile) { com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager.getAniListUser(context, actProfile) }

        val defaultDisplayName = remember {
            val baseName = userProfile?.name ?: "User"
            if (anilistUser != null) {
                "$baseName [AniList: @${anilistUser.username} (ID: ${anilistUser.id})]"
            } else {
                baseName
            }
        }

        var userName by remember { mutableStateOf(defaultDisplayName) }
        var email by remember { mutableStateOf("") }
        var selectedIssueType by remember { mutableStateOf("Stream Not Loading") }
        var description by remember { mutableStateOf("") }

        val issueTypes = listOf(
            "Stream Not Loading",
            "Audio Desync / Missing",
            "Subtitle Missing / Broken",
            "Frequent Buffering",
            "Wrong Episode / Video",
            "Other Issue"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 320.dp, max = 420.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF282828))
                    .clickable(enabled = false) {}
                    .padding(22.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Report Playback Issue",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(imageVector = androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E24))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = animeTitle,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = " · Episode $episodeNum",
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Your Name", color = Color(0xFFBBBBBB), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1C1C20))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Email (Optional)", color = Color(0xFFBBBBBB), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicTextField(
                        value = email,
                        onValueChange = { email = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                        decorationBox = { innerTextField ->
                            if (email.isEmpty()) {
                                Text("For updates regarding this fix", color = Color(0xFF666666), fontSize = 13.sp)
                            }
                            innerTextField()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1C1C20))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Issue Category", color = Color(0xFFBBBBBB), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(issueTypes) { type ->
                            val isSel = type == selectedIssueType
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) primaryColor else Color(0xFF1E1E24))
                                    .clickable { selectedIssueType = type }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = type,
                                    color = if (isSel) Color.Black else Color(0xFFCCCCCC),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Description", color = Color(0xFFBBBBBB), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicTextField(
                        value = description,
                        onValueChange = { description = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                        decorationBox = { innerTextField ->
                            if (description.isEmpty()) {
                                Text("Describe what happened...", color = Color(0xFF666666), fontSize = 12.5.sp)
                            }
                            innerTextField()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1C1C20))
                            .padding(10.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onSubmit(userName, email, selectedIssueType, description)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Text("Submit Report", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun UserScoreCircularProgress(
        scorePercentage: Int,
        modifier: Modifier = Modifier,
        size: Dp = 32.dp,
        strokeWidth: Dp = 2.8.dp,
        accentColor: Color = AnimeBoxThemeHelper.COLOR_LIGHT_RED
    ) {
        val progress = (scorePercentage.coerceIn(0, 100)) / 100f
        Box(
            modifier = modifier
                .size(size)
                .background(Color(0xFF08080C), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = Color.White.copy(alpha = 0.2f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
            if (scorePercentage > 0) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 1.dp)
                ) {
                    Text(
                        text = "$scorePercentage",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 10.sp
                    )
                    Text(
                        text = "%",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 5.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 6.sp,
                        modifier = Modifier.padding(top = 0.5.dp)
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 1.dp)
                ) {
                    Text(
                        text = "0",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 10.sp
                    )
                    Text(
                        text = "%",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 5.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 6.sp,
                        modifier = Modifier.padding(top = 0.5.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun DetailAnimeGridPosterCard(
        id: Int,
        title: String,
        coverUrl: String,
        badgeText: String = "",
        epCount: Int = 12,
        onShowKitsuRestriction: (() -> Unit)? = null,
        onClick: () -> Unit
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val libraryManager = remember { LibraryManager(this@AnimeBoxDetailActivity) }
        var inLibrary by remember(id) { mutableStateOf(libraryManager.isInLibrary(id)) }
        val context = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.678f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161616))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = coverUrl),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (badgeText.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .shadow(elevation = 6.dp, shape = RoundedCornerShape(6.dp), spotColor = Color.Black)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xE60D0D14))
                            .border(1.2.dp, primaryColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeText.uppercase(),
                            color = primaryColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable {
                            if (com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.isKitsuModeActive || AniListClient.lastErrorMessage != null) {
                                if (onShowKitsuRestriction != null) {
                                    onShowKitsuRestriction()
                                } else {
                                    android.widget.Toast.makeText(context, "Adding anime to My List is only supported when connected to AniList.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val animeBrief = AnimeBrief(
                                    id = id,
                                    title = title,
                                    coverUrl = coverUrl,
                                    bannerUrl = "",
                                    description = "",
                                    genres = emptyList(),
                                    averageScore = 0,
                                    logoUrl = "",
                                    episodes = epCount,
                                    status = ""
                                )
                                val isNowInLibrary = libraryManager.toggleLibraryItem(animeBrief)
                                inLibrary = isNowInLibrary
                                val toastText = if (isNowInLibrary) "Added to My List" else "Removed from My List"
                                android.widget.Toast.makeText(context, toastText, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (inLibrary) androidx.compose.material.icons.Icons.Default.Check else androidx.compose.material.icons.Icons.Default.Add,
                        contentDescription = "My List Toggle",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(Color.Black.copy(alpha = 0.75f)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CC", color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Black, lineHeight = 8.sp)
                    }
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "$epCount",
                        color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(7.dp))
                    Text("|", color = Color.Gray.copy(alpha = 0.5f), fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(7.dp))

                    CustomMicIcon(
                        color = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "$epCount",
                        color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    @Composable
    fun CustomMicIcon(color: Color, modifier: Modifier = Modifier) {
        Icon(
            imageVector = CustomMicVector,
            contentDescription = "Mic Icon",
            tint = color,
            modifier = modifier
        )
    }

    @Composable
    fun NetflixBottomNav(selectedTab: Int = -1, onTabSelected: (Int) -> Unit) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val activeProfId = ProfileManager.getActiveProfile(context)
        val activeProfileObj = ProfileManager.getProfiles(context)
            .find { it.id == activeProfId }
        val activeName = activeProfileObj?.name ?: "Guest"
        val avatarUrl = activeProfileObj?.avatarUrl ?: ""
        val profilePainter = remember(avatarUrl) {
            if (avatarUrl.startsWith("android.resource://")) {
                val resName = avatarUrl.substringAfterLast("/")
                val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                if (resId != 0) resId else avatarUrl
            } else {
                avatarUrl
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121212))
                .navigationBarsPadding()
                .padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isHomeSelected = selectedTab == 0
                val homeColor = if (isHomeSelected) Color.White else Color(0xFF8E8E93)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(0) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isHomeSelected) CustomHomeFilledIcon else CustomHomeOutlineIcon,
                        contentDescription = "Home",
                        tint = if (isHomeSelected) Color.Unspecified else homeColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Home",
                        color = homeColor,
                        fontSize = 11.sp,
                        fontWeight = if (isHomeSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                val isSearchSelected = selectedTab == 1
                val searchColor = if (isSearchSelected) Color.White else Color(0xFF8E8E93)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(1) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = CustomSearchIcon,
                        contentDescription = "Search",
                        tint = searchColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Search",
                        color = searchColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSearchSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                val isDownloadsSelected = selectedTab == 2
                val downloadsColor = if (isDownloadsSelected) Color.White else Color(0xFF8E8E93)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(2) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = CustomDownloadsNavbarIcon,
                        contentDescription = "Downloads",
                        tint = downloadsColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Downloads",
                        color = downloadsColor,
                        fontSize = 11.sp,
                        fontWeight = if (isDownloadsSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                val isListSelected = selectedTab == 3
                val listColor = if (isListSelected) Color.White else Color(0xFF8E8E93)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(3) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = CustomMyListIcon,
                        contentDescription = "My Lists",
                        tint = listColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "My Lists",
                        color = listColor,
                        fontSize = 11.sp,
                        fontWeight = if (isListSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                val isSpaceSelected = selectedTab == 4
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(4) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF222228)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUrl.isNotEmpty()) {
                            Image(
                                painter = rememberAsyncImagePainter(model = profilePainter),
                                contentDescription = activeName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = activeName.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "My Space",
                        color = if (isSpaceSelected) Color.White else Color(0xFF8E8E93),
                        fontSize = 11.sp,
                        fontWeight = if (isSpaceSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }

    @Composable
    fun CharacterCastCard(character: AnimeCharacter) {
        Column(
            modifier = Modifier.width(96.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1C1C24))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = character.imageUrl),
                    contentDescription = character.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = character.name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (character.actorName.isNotEmpty()) {
                Text(
                    text = character.actorName,
                    color = Color(0xFF8E8E9B),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    fun EpisodeRowCard(
        anilistId: Int,
        episodeNum: Int,
        title: String,
        defaultImageUrl: String,
        meta: EpisodeMeta?,
        isLastWatched: Boolean = false,
        isMovie: Boolean = false,
        movieBackdropUrl: String = "",
        onClick: () -> Unit,
        onDownloadClick: () -> Unit
    ) {
        var imageUrl by remember(episodeNum, defaultImageUrl, movieBackdropUrl, isMovie) {
            val initial = if (isMovie && movieBackdropUrl.isNotEmpty()) {
                movieBackdropUrl
            } else {
                defaultImageUrl
            }
            mutableStateOf(initial)
        }

        val tmdbId = AniZipClient.getLongRunningTmdbId(anilistId)
        LaunchedEffect(episodeNum, defaultImageUrl) {
            if (!isMovie) {
                if (meta != null && meta.imageUrl.isNotEmpty()) {
                    imageUrl = meta.imageUrl
                } else if (tmdbId != null && meta != null) {
                    val tmdbImg = AniZipClient.getTmdbEpisodeImage(tmdbId, meta.seasonNumber, meta.episodeNumber)
                    if (tmdbImg.isNotEmpty()) {
                        imageUrl = tmdbImg
                    }
                } else {
                    val epMap = AniZipClient.getEpisodeMetadata(anilistId)
                    val still = epMap[episodeNum]?.imageUrl ?: ""
                    if (still.isNotEmpty()) {
                        imageUrl = still
                    }
                }
            }
        }

        val isMovieUsingBlurredPoster = isMovie && (movieBackdropUrl.isEmpty() || imageUrl == defaultImageUrl)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(135.dp)
                    .height(78.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1C1C24))
            ) {
                val isSpoilerBlur = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isBlurEpisodeSpoilersEnabled(this@AnimeBoxDetailActivity) && !isMovie
                val shouldBlur = isMovieUsingBlurredPoster || isSpoilerBlur

                Image(
                    painter = rememberAsyncImagePainter(model = imageUrl),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (shouldBlur) Modifier.blur(22.dp) else Modifier)
                )

                if (isSpoilerBlur) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.38f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_eye_spoiler),
                            contentDescription = "Spoiler Hidden",
                            modifier = Modifier.size(24.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(alpha = 0.9f))
                        )
                    }
                } else if (isMovieUsingBlurredPoster) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                }

                if (meta?.isFiller == true) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFC107))
                            .padding(horizontal = 6.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "Filler",
                            color = Color.Black,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                
                val runtimeMin = if (meta?.runtime != null && meta.runtime > 0) meta.runtime else 24
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 0.5.dp)
                ) {
                    Text(
                        text = "${runtimeMin}m",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                val epTitle = if (!title.isBlank() && !title.equals("null", ignoreCase = true) && !title.startsWith("Episode ", ignoreCase = true)) title else (meta?.title ?: "Episode $episodeNum")
                
                Text(
                    text = "$episodeNum. $epTitle",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(3.dp))

                val overviewText = if (!meta?.overview.isNullOrEmpty() && !meta!!.overview.equals("null", ignoreCase = true)) meta!!.overview else "Episode $episodeNum"
                Text(
                    text = overviewText,
                    color = Color(0xFF8E8E9B),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val actCtx = LocalContext.current
            val epStateList by com.lagradost.cloudstream3.ui.animebox.download.AnimeDownloadManager.episodesState.collectAsState()
            val currentEpStatus = epStateList.find { it.anilistId == anilistId && it.episodeNumber == episodeNum }
            val isCompleted = currentEpStatus?.status == com.lagradost.cloudstream3.ui.animebox.download.DownloadStatus.COMPLETED
            val isDownloading = currentEpStatus?.status == com.lagradost.cloudstream3.ui.animebox.download.DownloadStatus.DOWNLOADING ||
                                currentEpStatus?.status == com.lagradost.cloudstream3.ui.animebox.download.DownloadStatus.PENDING

            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (!isCompleted && !isDownloading) {
                            onDownloadClick()
                        } else if (isCompleted) {
                            Toast.makeText(actCtx, "Episode $episodeNum is already downloaded", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    isDownloading -> {
                        val prog = currentEpStatus?.downloadProgress ?: 0
                        Box(
                            modifier = Modifier.size(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = (prog / 100f).coerceIn(0f, 1f),
                                color = Color.White,
                                trackColor = Color(0xFF2E2E34),
                                strokeWidth = 2.2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                            if (prog > 0) {
                                Text(
                                    text = "$prog%",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    isCompleted -> {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Check,
                            contentDescription = "Downloaded",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = CustomDownloadsNavbarIcon,
                            contentDescription = "Download Episode",
                            tint = Color(0xFFBDC7D5),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }


    private suspend fun fetchAndPlayStream(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String,
        episodeCoverUrl: String,
        type: String,
        showCoverUrl: String,
        totalEpisodes: Int,
        explicitUrl: String? = null
    ) {
        if (AnimeBoxPlayerActivity.isCurrentlyInPip) {
            Toast.makeText(this, "Please close Picture-in-Picture mode first to play another episode", Toast.LENGTH_SHORT).show()
            return
        }

        val okSpecialUrl = com.lagradost.cloudstream3.ui.animebox.extractors.PokemonHindiExtractor.POKEMON_OKRU_SPECIAL_URLS[anilistId]
        val useOkRu = when {
            !explicitUrl.isNullOrEmpty() -> true
            com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId) -> (type == "sub" || type == "hardsub")
            okSpecialUrl != null -> (type == "dub" || type == "sub" || type == "hardsub")
            else -> false
        }
        if (useOkRu) {
            val shinChanRes = com.lagradost.cloudstream3.ui.animebox.extractors.ShinChanOkRuExtractor.extractShinChanStream(
                anilistId,
                episodeNum,
                animeTitle,
                explicitVideoUrl = explicitUrl ?: okSpecialUrl
            )
            if (shinChanRes != null && shinChanRes.hlsUrl.isNotEmpty()) {
                val histManager = WatchHistoryManager(this)
                val savedProgress = histManager.getSavedProgress(anilistId, episodeNum)
                val intent = Intent(this, AnimeBoxPlayerActivity::class.java).apply {
                    putExtra("hlsUrl", shinChanRes.hlsUrl)
                    putExtra("referer", shinChanRes.referer)
                    putExtra("subtitleUrl", "")
                    putExtra("anilistId", anilistId)
                    putExtra("episode", episodeNum)
                    putExtra("animeTitle", animeTitle)
                    putExtra("coverUrl", episodeCoverUrl)
                    putExtra("showCoverUrl", showCoverUrl)
                    putExtra("totalEpisodes", totalEpisodes)
                    putExtra("streamType", if (type == "dub") "dub" else "hardsub")
                    putExtra("fromContinueWatching", savedProgress > 0L)
                }
                startActivity(intent)
                return
            }
        }

        val histManager = WatchHistoryManager(this)
        val savedProgress = histManager.getSavedProgress(anilistId, episodeNum)

        // 1. Direct offline playback if episode is already downloaded
        val downloadedEp = com.lagradost.cloudstream3.ui.animebox.download.AnimeDownloadManager.getInstance(this).getDownloadedEpisode(anilistId, episodeNum)
        if (downloadedEp != null && downloadedEp.localFilePath.isNotEmpty() && java.io.File(downloadedEp.localFilePath).exists()) {
            val intent = Intent(this, AnimeBoxPlayerActivity::class.java).apply {
                putExtra("localFilePath", downloadedEp.localFilePath)
                putExtra("isOffline", true)
                putExtra("animeTitle", animeTitle)
                putExtra("anilistId", anilistId)
                putExtra("episode", episodeNum)
                putExtra("showCoverUrl", showCoverUrl)
                putExtra("coverUrl", episodeCoverUrl)
                putExtra("subtitleUrl", downloadedEp.localSubtitlePath)
                putExtra("subtitlesJson", downloadedEp.subtitlesJson)
                putExtra("introStart", downloadedEp.introStart)
                putExtra("introEnd", downloadedEp.introEnd)
                putExtra("outroStart", downloadedEp.outroStart)
                putExtra("outroEnd", downloadedEp.outroEnd)
                putExtra("streamType", downloadedEp.streamType)
                putExtra("fromContinueWatching", savedProgress > 0L)
            }
            startActivity(intent)
            return
        }

        var actualType = type
        var showFallbackDialogInPlayer = false
        var streamInfo = com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(this, anilistId, episodeNum, type, animeTitle)
        if (streamInfo == null && type != "sub") {
            val subInfo = com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(this, anilistId, episodeNum, "sub", animeTitle)
            if (subInfo != null) {
                streamInfo = subInfo
                actualType = "sub"
                showFallbackDialogInPlayer = true
            }
        }

        if (streamInfo != null && (streamInfo["hls"] as? String)?.isNotEmpty() == true) {
            if (AnimeBoxPlayerActivity.isCurrentlyInPip) {
                Toast.makeText(this, "Please close Picture-in-Picture mode first to play another episode", Toast.LENGTH_SHORT).show()
                return
            }
            // Check for saved progress to show resume dialog in player
            val histManager = WatchHistoryManager(this)
            val savedProgress = histManager.getSavedProgress(anilistId, episodeNum)
            val intent = Intent(this, AnimeBoxPlayerActivity::class.java).apply {
                putExtra("hlsUrl", (streamInfo["hls"] as? String) ?: "")
                putExtra("referer", (streamInfo["referer"] as? String) ?: "https://megaplay.buzz/")
                putExtra("subtitleUrl", (streamInfo["subtitle"] as? String) ?: "")
                putExtra("introStart", (streamInfo["introStart"] as? Number)?.toLong() ?: 0L)
                putExtra("introEnd", (streamInfo["introEnd"] as? Number)?.toLong() ?: 0L)
                putExtra("outroStart", (streamInfo["outroStart"] as? Number)?.toLong() ?: 0L)
                putExtra("outroEnd", (streamInfo["outroEnd"] as? Number)?.toLong() ?: 0L)
                putExtra("anilistId", anilistId)
                putExtra("episode", episodeNum)
                putExtra("animeTitle", animeTitle)
                putExtra("coverUrl", episodeCoverUrl)
                putExtra("showCoverUrl", showCoverUrl)
                putExtra("totalEpisodes", totalEpisodes)
                putExtra("backupHls", (streamInfo["backupHls"] as? String) ?: "")
                putExtra("backupProvider", (streamInfo["backupProvider"] as? String) ?: "")
                putExtra("hindiStreamsJson", (streamInfo["hindiStreamsJson"] as? String) ?: "")
                putExtra("streamType", actualType)
                putExtra("showAudioFallbackWarning", showFallbackDialogInPlayer)
                putExtra("requestedAudioType", type)
                // fromContinueWatching=true triggers resume dialog if savedProgress > 0
                putExtra("fromContinueWatching", savedProgress > 0L)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "No source available for this anime episode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseDetails(jsonString: String): AnimeDetail? {
        return try {
            val obj = JSONObject(jsonString).getJSONObject("data").getJSONObject("Media")
            val id = obj.getInt("id")
            val titleObj = if (obj.has("title") && !obj.isNull("title")) obj.getJSONObject("title") else JSONObject()
            val eng = titleObj.optString("english", "").let { if (it.equals("null", ignoreCase = true)) "" else it }
            val rom = titleObj.optString("romaji", "").let { if (it.equals("null", ignoreCase = true)) "" else it }
            val nativeTitle = titleObj.optString("native", "").let { if (it.equals("null", ignoreCase = true)) "" else it }
            val title = if (eng.isNotEmpty()) eng else if (rom.isNotEmpty()) rom else if (nativeTitle.isNotEmpty()) nativeTitle else "Anime"

            val coverUrl = if (obj.has("coverImage") && !obj.isNull("coverImage")) {
                val cov = obj.getJSONObject("coverImage")
                val xl = cov.optString("extraLarge", "")
                val lg = cov.optString("large", "")
                if (xl.isNotEmpty()) xl else lg
            } else ""

            val bannerUrl = if (obj.has("bannerImage") && !obj.isNull("bannerImage")) {
                obj.getString("bannerImage")
            } else ""

            val rawDesc = if (obj.has("description") && !obj.isNull("description")) {
                obj.getString("description")
            } else ""
            val cleanDesc = rawDesc.replace(Regex("<[^>]*>"), "")

            val isLongRunning = AniZipClient.getLongRunningTmdbId(id) != null
            val episodes = if (obj.has("episodes") && !obj.isNull("episodes")) {
                obj.getInt("episodes")
            } else {
                0
            }

            val score = if (obj.has("averageScore") && !obj.isNull("averageScore")) {
                obj.getInt("averageScore")
            } else 0

            val genresList = mutableListOf<String>()
            if (obj.has("genres") && !obj.isNull("genres")) {
                val genresArray = obj.getJSONArray("genres")
                for (i in 0 until genresArray.length()) {
                    genresList.add(genresArray.getString(i))
                }
            }

            val isAdult = if (obj.has("isAdult") && !obj.isNull("isAdult")) {
                obj.getBoolean("isAdult")
            } else false

            val idMal = if (obj.has("idMal") && !obj.isNull("idMal")) obj.getInt("idMal") else 0

            var trailerId = ""
            if (obj.has("trailer") && !obj.isNull("trailer")) {
                val trailerObj = obj.optJSONObject("trailer")
                val site = trailerObj?.optString("site", "")?.lowercase() ?: ""
                val tid = trailerObj?.optString("id", "") ?: ""
                if (site == "youtube" || site.isEmpty()) {
                    trailerId = tid
                }
            }

            val charactersList = mutableListOf<AnimeCharacter>()
            if (obj.has("characters") && !obj.isNull("characters")) {
                val charObj = obj.optJSONObject("characters")
                if (charObj != null && charObj.has("edges") && !charObj.isNull("edges")) {
                    val edges = charObj.optJSONArray("edges")
                    if (edges != null) {
                        for (i in 0 until edges.length()) {
                            val edge = edges.optJSONObject(i) ?: continue
                            val role = edge.optString("role", "")
                            
                            val node = edge.optJSONObject("node") ?: continue
                            val nameObj = node.optJSONObject("name")
                            val charName = nameObj?.optString("full", "") ?: ""
                            val imageObj = node.optJSONObject("image")
                            val charImage = imageObj?.optString("large", "") ?: ""
                            
                            var actorName = ""
                            var actorImage = ""
                            if (edge.has("voiceActors") && !edge.isNull("voiceActors")) {
                                val voiceActors = edge.optJSONArray("voiceActors")
                                if (voiceActors != null && voiceActors.length() > 0) {
                                    val actor = voiceActors.optJSONObject(0)
                                    val actorNameObj = actor?.optJSONObject("name")
                                    actorName = actorNameObj?.optString("full", "") ?: ""
                                    val actorImageObj = actor?.optJSONObject("image")
                                    actorImage = actorImageObj?.optString("large", "") ?: ""
                                }
                            }
                            if (charName.isNotEmpty() && charactersList.none { it.name.equals(charName, ignoreCase = true) }) {
                                charactersList.add(AnimeCharacter(charName, charImage, role, actorName, actorImage))
                            }
                        }
                    }
                }
            }

            val relationsList = mutableListOf<AnimeRelation>()
            if (obj.has("relations") && !obj.isNull("relations")) {
                val relObj = obj.getJSONObject("relations")
                if (relObj.has("edges")) {
                    val edges = relObj.getJSONArray("edges")
                    for (i in 0 until edges.length()) {
                        val edge = edges.getJSONObject(i)
                        val relType = edge.optString("relationType", "")
                        if (edge.has("node") && !edge.isNull("node")) {
                            val node = edge.getJSONObject("node")
                            if (com.lagradost.cloudstream3.ui.animebox.api.AniListClient.isBlockedMedia(node)) continue
                            val relId = node.getInt("id")
                            val relTitleObj = node.getJSONObject("title")
                            val relTitle = if (relTitleObj.has("english") && !relTitleObj.isNull("english")) {
                                relTitleObj.getString("english")
                            } else {
                                relTitleObj.getString("romaji")
                            }
                            val relCover = node.getJSONObject("coverImage").optString("large", "")
                            val format = node.optString("format", "")
                            relationsList.add(AnimeRelation(relId, relTitle, relCover, relType, format))
                        }
                    }
                }
            }

            val recommendationsList = mutableListOf<AnimeRecommendation>()
            if (obj.has("recommendations") && !obj.isNull("recommendations")) {
                val recObj = obj.getJSONObject("recommendations")
                if (recObj.has("nodes")) {
                    val nodes = recObj.getJSONArray("nodes")
                    for (i in 0 until nodes.length()) {
                        val nodeObj = nodes.getJSONObject(i)
                        if (nodeObj.has("mediaRecommendation") && !nodeObj.isNull("mediaRecommendation")) {
                            val media = nodeObj.getJSONObject("mediaRecommendation")
                            if (com.lagradost.cloudstream3.ui.animebox.api.AniListClient.isBlockedMedia(media)) continue
                            val recId = media.getInt("id")
                            val recTitleObj = media.getJSONObject("title")
                            val recTitle = if (recTitleObj.has("english") && !recTitleObj.isNull("english")) {
                                recTitleObj.getString("english")
                            } else {
                                recTitleObj.getString("romaji")
                            }
                            val recCover = media.getJSONObject("coverImage").optString("large", "")
                            val format = media.optString("format", "")
                            recommendationsList.add(AnimeRecommendation(recId, recTitle, recCover, format))
                        }
                    }
                }
            }

            var nextAiring: NextAiringEpisode? = null
            if (obj.has("nextAiringEpisode") && !obj.isNull("nextAiringEpisode")) {
                val naObj = obj.getJSONObject("nextAiringEpisode")
                val airingAt = naObj.getLong("airingAt")
                val timeUntil = naObj.getInt("timeUntilAiring")
                val epNum = naObj.getInt("episode")
                nextAiring = NextAiringEpisode(airingAt, timeUntil, epNum)
            }
            val status = if (obj.has("status") && !obj.isNull("status")) obj.getString("status") else ""
            val format = if (obj.has("format") && !obj.isNull("format")) obj.getString("format") else ""
            val releaseYear = if (obj.has("startDate") && !obj.isNull("startDate")) {
                val sd = obj.getJSONObject("startDate")
                if (sd.has("year") && !sd.isNull("year")) sd.getInt("year") else 0
            } else 0

            AnimeDetail(
                id = id,
                title = title,
                description = cleanDesc,
                coverUrl = coverUrl,
                bannerUrl = bannerUrl,
                episodesCount = episodes,
                score = score,
                genres = genresList,
                status = status,
                format = format,
                releaseYear = releaseYear,
                isAdult = isAdult,
                idMal = idMal,
                trailerId = trailerId,
                characters = charactersList,
                relations = relationsList,
                recommendations = recommendationsList,
                nextAiring = nextAiring
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun resolveFallbackTrailer(anilistId: Int, malId: Int, title: String): String = withContext(Dispatchers.IO) {
        // 1. Try Jikan API via MAL ID
        if (malId > 0) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val req = okhttp3.Request.Builder().url("https://api.jikan.moe/v4/anime/$malId").build()
                val resp = client.newCall(req).execute().use { it.body?.string() }
                if (!resp.isNullOrEmpty()) {
                    val jData = org.json.JSONObject(resp).optJSONObject("data")
                    val jTrailer = jData?.optJSONObject("trailer")
                    val ytId = jTrailer?.optString("youtube_id", "") ?: ""
                    if (ytId.isNotEmpty()) return@withContext ytId
                }
            } catch (_: Exception) {}
        }
        // 2. Try AniZip mappings for MAL ID
        try {
            val aniZip = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getAniZipJson(anilistId)
            val mappingsMalId = aniZip?.optJSONObject("mappings")?.optInt("mal_id", 0) ?: 0
            if (mappingsMalId > 0 && mappingsMalId != malId) {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val req = okhttp3.Request.Builder().url("https://api.jikan.moe/v4/anime/$mappingsMalId").build()
                val resp = client.newCall(req).execute().use { it.body?.string() }
                if (!resp.isNullOrEmpty()) {
                    val jData = org.json.JSONObject(resp).optJSONObject("data")
                    val jTrailer = jData?.optJSONObject("trailer")
                    val ytId = jTrailer?.optString("youtube_id", "") ?: ""
                    if (ytId.isNotEmpty()) return@withContext ytId
                }
            }
        } catch (_: Exception) {}
        ""
    }

    // Extract Direct MP4 / M3U8 video stream for YouTube trailer using NewPipe Extractor
    private suspend fun extractTrailerWithNewPipe(videoId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Ensure NewPipe is initialized
                try {
                    org.schabi.newpipe.extractor.NewPipe.init(com.lagradost.cloudstream3.DownloaderTestImpl.getInstance())
                } catch (_: Exception) {}

                val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                val streamInfo = org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(watchUrl)

                // 1. Prefer combined video+audio streams
                val videoAudioStreams = streamInfo.videoStreams.orEmpty()
                if (videoAudioStreams.isNotEmpty()) {
                    val bestStream = videoAudioStreams.maxByOrNull { it.height }
                    if (bestStream != null && !bestStream.content.isNullOrBlank()) {
                        return@withContext bestStream.content
                    }
                }

                // 2. Fallback to HLS stream if available
                if (!streamInfo.hlsUrl.isNullOrBlank()) {
                    return@withContext streamInfo.hlsUrl
                }

                // 3. Fallback to video-only streams
                val videoOnlyStreams = streamInfo.videoOnlyStreams.orEmpty()
                if (videoOnlyStreams.isNotEmpty()) {
                    val bestVideo = videoOnlyStreams.maxByOrNull { it.height }
                    if (bestVideo != null && !bestVideo.content.isNullOrBlank()) {
                        return@withContext bestVideo.content
                    }
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}

// Custom VolumeUp icon built programmatically
val VolumeUpIcon: ImageVector
    get() = ImageVector.Builder(
        name = "VolumeUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(3f, 9f)
        verticalLineTo(15f)
        horizontalLineTo(7f)
        lineTo(12f, 20f)
        verticalLineTo(4f)
        lineTo(7f, 9f)
        horizontalLineTo(3f)
        close()
        moveTo(16.5f, 12f)
        curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
        verticalLineTo(16.02f)
        curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
        close()
        moveTo(14f, 3.23f)
        verticalLineTo(5.29f)
        curveTo(16.89f, 6.15f, 19f, 8.83f, 19f, 12f)
        curveTo(19f, 15.17f, 16.89f, 17.85f, 14f, 18.71f)
        verticalLineTo(20.77f)
        curveTo(18.01f, 19.86f, 21f, 16.28f, 21f, 12f)
        curveTo(21f, 7.72f, 18.01f, 4.14f, 14f, 3.23f)
        close()
    }.build()

// Custom VolumeOff icon built programmatically
val VolumeOffIcon: ImageVector
    get() = ImageVector.Builder(
        name = "VolumeOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(4.34f, 2.93f)
        lineTo(2.93f, 4.34f)
        lineTo(7.29f, 8.7f)
        horizontalLineTo(3f)
        verticalLineTo(15.3f)
        horizontalLineTo(7f)
        lineTo(12f, 20.3f)
        verticalLineTo(13.41f)
        lineTo(16.29f, 17.7f)
        curveTo(15.62f, 18.2f, 14.85f, 18.57f, 14f, 18.72f)
        verticalLineTo(20.78f)
        curveTo(15.39f, 20.59f, 16.69f, 19.97f, 17.78f, 19.19f)
        lineTo(19.66f, 21.07f)
        lineTo(21.07f, 19.66f)
        lineTo(4.34f, 2.93f)
        close()
        moveTo(12f, 4f)
        lineTo(9.91f, 6.09f)
        lineTo(12f, 8.18f)
        verticalLineTo(4f)
        close()
        moveTo(19f, 12f)
        curveTo(19f, 8.83f, 16.89f, 6.15f, 14f, 5.29f)
        verticalLineTo(7.35f)
        curveTo(15.48f, 8.09f, 16.5f, 9.61f, 16.5f, 11.38f)
        curveTo(16.5f, 12.18f, 16.22f, 12.92f, 15.76f, 13.51f)
        lineTo(17.24f, 14.99f)
        curveTo(18.33f, 14.19f, 19f, 13.17f, 19f, 12f)
        close()
    }.build()

// Custom Sort icon built programmatically
val SortIconVector: ImageVector
    get() = ImageVector.Builder(
        name = "SortIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(3f, 18f)
        horizontalLineTo(9f)
        verticalLineTo(16f)
        horizontalLineTo(3f)
        verticalLineTo(18f)
        close()
        moveTo(3f, 6f)
        verticalLineTo(8f)
        horizontalLineTo(21f)
        verticalLineTo(6f)
        horizontalLineTo(3f)
        close()
        moveTo(3f, 13f)
        horizontalLineTo(15f)
        verticalLineTo(11f)
        horizontalLineTo(3f)
        verticalLineTo(13f)
        close()
    }.build()


// Animated Audio Wave Indicator
@Composable
fun PlayingAudioWaveIndicator(
    modifier: Modifier = Modifier,
    waveColor: Color = AnimeBoxThemeHelper.COLOR_LIGHT_RED,
    barWidth: Dp = 2.dp,
    spacing: Dp = 1.5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_waves")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h4"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(modifier = Modifier.width(barWidth).fillMaxHeight(h1).clip(RoundedCornerShape(1.dp)).background(waveColor))
        Box(modifier = Modifier.width(barWidth).fillMaxHeight(h2).clip(RoundedCornerShape(1.dp)).background(waveColor))
        Box(modifier = Modifier.width(barWidth).fillMaxHeight(h3).clip(RoundedCornerShape(1.dp)).background(waveColor))
        Box(modifier = Modifier.width(barWidth).fillMaxHeight(h4).clip(RoundedCornerShape(1.dp)).background(waveColor))
    }
}

// Share Box with Upward Arrow Icon (Matching user image: arrow clearly lifted above open box)
val ShareBoxArrowIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ShareBoxArrow",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2.0f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
    ) {
        // Arrow shaft pointing up
        moveTo(12f, 13.5f)
        lineTo(12f, 2f)
        // Arrow head chevron up
        moveTo(7f, 6.5f)
        lineTo(12f, 1.5f)
        lineTo(17f, 6.5f)
        // Open box outline placed lower down so there is a large clear gap
        moveTo(7.5f, 10f)
        horizontalLineTo(5f)
        curveTo(4.45f, 10f, 4f, 10.45f, 4f, 11f)
        verticalLineTo(20f)
        curveTo(4f, 20.55f, 4.45f, 21f, 5f, 21f)
        horizontalLineTo(19f)
        curveTo(19.55f, 21f, 20f, 20.55f, 20f, 20f)
        verticalLineTo(11f)
        curveTo(20f, 10.45f, 19.55f, 10f, 19f, 10f)
        horizontalLineTo(16.5f)
    }.build()

// Movie Film Reel / Clapboard Icon for Trailer
val MovieReelIcon: ImageVector
    get() = ImageVector.Builder(
        name = "MovieReel",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(18f, 4f)
        lineTo(20f, 8f)
        horizontalLineTo(17f)
        lineTo(15f, 4f)
        horizontalLineTo(13f)
        lineTo(15f, 8f)
        horizontalLineTo(12f)
        lineTo(10f, 4f)
        horizontalLineTo(8f)
        lineTo(10f, 8f)
        horizontalLineTo(7f)
        lineTo(5f, 4f)
        horizontalLineTo(4f)
        curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
        verticalLineTo(18f)
        curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
        verticalLineTo(4f)
        horizontalLineTo(18f)
        close()
        moveTo(10f, 15.5f)
        verticalLineTo(10.5f)
        lineTo(15f, 13f)
        lineTo(10f, 15.5f)
        close()
    }.build()

// Cloud Download Icon for Anime Detail Page (after User Score)
val CloudDownloadIcon: ImageVector
    get() = ImageVector.Builder(
        name = "CloudDownload",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(19.35f, 10.04f)
        curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
        curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
        curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
        curveTo(0f, 17.31f, 2.69f, 20f, 6f, 20f)
        horizontalLineTo(19f)
        curveTo(21.76f, 20f, 24f, 17.76f, 24f, 15f)
        curveTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f)
        close()
        moveTo(17f, 13f)
        lineTo(12f, 18f)
        lineTo(7f, 13f)
        horizontalLineTo(10f)
        verticalLineTo(9f)
        horizontalLineTo(14f)
        verticalLineTo(13f)
        horizontalLineTo(17f)
        close()
    }.build()

// Grid Box View Icon
val GridViewIcon: ImageVector
    get() = ImageVector.Builder(
        name = "GridView",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(3f, 3f)
        horizontalLineTo(10f)
        verticalLineTo(10f)
        horizontalLineTo(3f)
        close()
        moveTo(14f, 3f)
        horizontalLineTo(21f)
        verticalLineTo(10f)
        horizontalLineTo(14f)
        close()
        moveTo(3f, 14f)
        horizontalLineTo(10f)
        verticalLineTo(21f)
        horizontalLineTo(3f)
        close()
        moveTo(14f, 14f)
        horizontalLineTo(21f)
        verticalLineTo(21f)
        horizontalLineTo(14f)
        close()
    }.build()

// List / Image View Icon
val ListViewIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ListView",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(3f, 4f)
        horizontalLineTo(21f)
        verticalLineTo(8f)
        horizontalLineTo(3f)
        close()
        moveTo(3f, 10f)
        horizontalLineTo(21f)
        verticalLineTo(14f)
        horizontalLineTo(3f)
        close()
        moveTo(3f, 16f)
        horizontalLineTo(21f)
        verticalLineTo(20f)
        horizontalLineTo(3f)
        close()
    }.build()

@Composable
fun AnimeBoxDetailSkeletonLoading(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "detail_skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.58f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val shimmerColor = Color(0xFF23242B).copy(alpha = alpha)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Large Hero Backdrop Skeleton with bottom gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(235.dp)
                .background(shimmerColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x60000000),
                                Color(0xFF000000)
                            )
                        )
                    )
            )
        }

        // 2. Poster & Info Header Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Overlapping Poster card
            Box(
                modifier = Modifier
                    .width(115.dp)
                    .height(165.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmerColor)
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Right Info Column (Title, Subtitle, Score / Year badges)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerColor)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerColor)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerColor)
                    )
                    Box(
                        modifier = Modifier
                            .width(55.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerColor)
                    )
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerColor)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Action Buttons Row (Play Button + Action Buttons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmerColor)
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(shimmerColor)
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(shimmerColor)
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(shimmerColor)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Genre Pills Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.width(65.dp).height(26.dp).clip(RoundedCornerShape(4.dp)).background(shimmerColor))
            Box(modifier = Modifier.width(85.dp).height(26.dp).clip(RoundedCornerShape(4.dp)).background(shimmerColor))
            Box(modifier = Modifier.width(70.dp).height(26.dp).clip(RoundedCornerShape(4.dp)).background(shimmerColor))
            Box(modifier = Modifier.width(90.dp).height(26.dp).clip(RoundedCornerShape(4.dp)).background(shimmerColor))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Synopsis Text Lines
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(3.dp)).background(shimmerColor))
            Box(modifier = Modifier.fillMaxWidth(0.95f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(shimmerColor))
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(shimmerColor))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 6. Episodes List Skeleton
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.width(100.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).background(shimmerColor))
            
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(135.dp)
                            .height(78.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(shimmerColor)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(3.dp)).background(shimmerColor))
                        Box(modifier = Modifier.fillMaxWidth(0.6f).height(11.dp).clip(RoundedCornerShape(3.dp)).background(shimmerColor))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun NextEpisodeCountdownCard(
    nextAiring: NextAiringEpisode,
    modifier: Modifier = Modifier
) {
    var currentTimeSec by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(nextAiring.airingAt) {
        while (true) {
            currentTimeSec = System.currentTimeMillis() / 1000
            kotlinx.coroutines.delay(1000L)
        }
    }
    val diffSec = maxOf(0L, nextAiring.airingAt - currentTimeSec)
    if (diffSec <= 0) return

    val days = diffSec / 86400
    val hours = (diffSec % 86400) / 3600
    val minutes = (diffSec % 3600) / 60
    val seconds = diffSec % 60

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF14151C))
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Episode label + Subtitle (NO purple dot, clean and minimal)
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "EPISODE ${nextAiring.episode}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                    letterSpacing = 0.4.sp
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "Next Episode Airs In",
                    color = Color(0xFF8B8E9E),
                    fontSize = 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }

            // Right: Time Units in clean pure white (NO purple)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.5.dp)
            ) {
                if (days > 0) {
                    CountdownTimeUnit(value = days.toString().padStart(2, '0'), unit = "d")
                    Text(text = ":", color = Color(0xFF55596A), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                CountdownTimeUnit(value = hours.toString().padStart(2, '0'), unit = "h")
                Text(text = ":", color = Color(0xFF55596A), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                CountdownTimeUnit(value = minutes.toString().padStart(2, '0'), unit = "m")
                Text(text = ":", color = Color(0xFF55596A), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                CountdownTimeUnit(value = seconds.toString().padStart(2, '0'), unit = "s")
            }
        }
    }
}

@Composable
fun CountdownTimeUnit(value: String, unit: String) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFF20222B))
            .padding(horizontal = 6.dp, vertical = 3.5.dp)
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = unit,
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 8.5.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
            modifier = Modifier.padding(bottom = 0.5.dp)
        )
    }
}

