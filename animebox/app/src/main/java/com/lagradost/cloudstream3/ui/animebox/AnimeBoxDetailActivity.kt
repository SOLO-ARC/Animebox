package com.lagradost.cloudstream3.ui.animebox

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.List
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    val isAdult: Boolean = false,
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
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD0BCFF),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                DetailScreen(anilistId)
            }
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

    // Helper function to resolve TMDB cover images for AniList IDs 21 and 235
    private suspend fun getResolvedEpisodeCover(anilistId: Int, episodeNum: Int, defaultUrl: String, meta: EpisodeMeta?): String {
        return meta?.imageUrl ?: defaultUrl
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DetailScreen(anilistId: Int) {
        val coroutineScope = rememberCoroutineScope()
        val historyManager = remember { WatchHistoryManager(this@AnimeBoxDetailActivity) }
        val libraryManager = remember { LibraryManager(this@AnimeBoxDetailActivity) }
        
        var detail by remember { mutableStateOf<AnimeDetail?>(null) }
        var episodeMetaMap by remember { mutableStateOf<Map<Int, EpisodeMeta>>(emptyMap()) }
        var resolvedBackdropUrl by remember { mutableStateOf("") }
        var isMuted by remember { mutableStateOf(true) }
        
        var isLoading by remember { mutableStateOf(true) }
        var isStreamLoading by remember { mutableStateOf(false) }
        
        val prefs = remember { getSharedPreferences("AnimeBoxPlayer", android.content.Context.MODE_PRIVATE) }
        val savedAudio = remember { prefs.getString("selectedAudio", "Japanese (Original)") }
        val streamType by remember { mutableStateOf(if (savedAudio == "Hindi") "hindi" else if (savedAudio == "English") "dub" else "sub") }

        var isInLibrary by remember { mutableStateOf(false) }

        // Search, Tabs, View, and Sorting states
        var episodeSearchQuery by remember { mutableStateOf("") }
        var selectedSectionTab by remember { mutableStateOf(0) } // 0: Episodes, 1: More Like This, 2: Related
        val defaultEpMode = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getDefaultEpisodeViewMode(this@AnimeBoxDetailActivity) }
        var episodeViewMode by remember { mutableStateOf(defaultEpMode) } // "image" or "number"
        var visibleEpisodesCount by remember { mutableStateOf(50) }
        var episodeSortOrder by remember { mutableStateOf("asc") } // "asc" or "desc"

        LaunchedEffect(anilistId) {
            coroutineScope.launch {
                val detailsDeferred = async(Dispatchers.IO) { AniListClient.getAnimeDetails(anilistId) }
                val episodeMetaDeferred = async(Dispatchers.IO) {
                    if (anilistId == 21 || anilistId == 235) {
                        val tmdbId = if (anilistId == 21) 37854 else 30983
                        AniZipClient.getTmdbAllEpisodes(tmdbId)
                    } else {
                        AniZipClient.getEpisodeMetadata(anilistId)
                    }
                }
                val aniZipBackdropDeferred = async(Dispatchers.IO) { AniZipClient.getAniZipBackdropUrl(anilistId) }
                val tmdbBackdropDeferred = async(Dispatchers.IO) { AniZipClient.getTmdbBackdropUrl(anilistId) }

                val detailsResponse = detailsDeferred.await()
                if (detailsResponse != null) {
                    val parsed = parseDetails(detailsResponse)
                    if (parsed != null && parsed.isAdult) {
                        Toast.makeText(this@AnimeBoxDetailActivity, "This content is blocked.", Toast.LENGTH_SHORT).show()
                        finish()
                        return@launch
                    }
                    detail = parsed
                }
                
                episodeMetaMap = episodeMetaDeferred.await()
                val aniZipBackdrop = aniZipBackdropDeferred.await()
                val tmdbBackdrop = tmdbBackdropDeferred.await()
                
                var backdrop = aniZipBackdrop
                if (backdrop.isEmpty()) {
                    backdrop = tmdbBackdrop
                }
                if (backdrop.isEmpty()) {
                    backdrop = detail?.bannerUrl ?: ""
                }
                if (backdrop.isEmpty()) {
                    backdrop = detail?.coverUrl ?: ""
                }
                resolvedBackdropUrl = backdrop
                
                // Check library status
                if (detail != null) {
                    isInLibrary = libraryManager.isInLibrary(detail!!.id)
                }
                
                isLoading = false
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("") },
                    navigationIcon = {
                        IconButton(
                            onClick = { finish() },
                            modifier = Modifier
                                .padding(start = 12.dp) // Shift back button to the right
                                .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                        ) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                NetflixBottomNav(
                    onTabSelected = { tabIndex ->
                        val intent = Intent(this@AnimeBoxDetailActivity, AnimeBoxMainActivity::class.java).apply {
                            putExtra("selectTab", tabIndex)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            },
            containerColor = Color(0xFF000000) // Pitch Black
        ) { paddingValues ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFD0BCFF))
                }
            } else {
                detail?.let { d ->
                    val watchHistory = remember { historyManager.getWatchHistory() }
                    val lastWatched = watchHistory.find { it.anilistId == anilistId }
                    
                    val episodesList = episodeMetaMap.keys.sorted().filter { epNum ->
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

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = paddingValues.calculateBottomPadding())
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 1. YouTube Trailer or Backdrop Image
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                        ) {
                            val isTrailerSettingOn = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isTrailerEnabled(this@AnimeBoxDetailActivity) }
                            if (d.trailerId.isNotEmpty() && isTrailerSettingOn) {
                                var webViewRef by remember { mutableStateOf<WebView?>(null) }
                                var showBlurOverlay by remember { mutableStateOf(true) }
                                var timerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                                
                                // Box to clip the zoomed WebView child
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clipToBounds() // Clips scaled margins
                                ) {
                                    AndroidView(
                                        factory = { context ->
                                            WebView(context).apply {
                                                layoutParams = android.view.ViewGroup.LayoutParams(
                                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                                )
                                                settings.javaScriptEnabled = true
                                                settings.domStorageEnabled = true
                                                settings.databaseEnabled = true
                                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                                settings.mediaPlaybackRequiresUserGesture = false
                                                
                                                isFocusable = true
                                                isFocusableInTouchMode = true
                                                requestFocus()
                                                
                                                webChromeClient = object : WebChromeClient() {
                                                    override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                                                        request?.grant(request.resources)
                                                    }
                                                }
                                                
                                                // Javascript interface to report player state changes
                                                addJavascriptInterface(object {
                                                    @android.webkit.JavascriptInterface
                                                    fun onPlayerStateChange(state: Int) {
                                                        coroutineScope.launch(Dispatchers.Main) {
                                                            if (state == 1) { // PLAYING
                                                                timerJob?.cancel()
                                                                timerJob = launch {
                                                                    showBlurOverlay = true
                                                                    kotlinx.coroutines.delay(6500)
                                                                    showBlurOverlay = false
                                                                }
                                                            } else if (state == 0 || state == -1 || state == 3) { // ENDED, UNSTARTED, BUFFERING
                                                                timerJob?.cancel()
                                                                showBlurOverlay = true
                                                            }
                                                        }
                                                    }
                                                }, "AndroidInterface")

                                                webViewClient = object : WebViewClient() {
                                                    override fun onPageFinished(view: WebView?, url: String?) {
                                                        super.onPageFinished(view, url)
                                                    }
                                                }
                                                
                                                val html = """
                                                    <!DOCTYPE html>
                                                    <html>
                                                    <head>
                                                      <style>
                                                        body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background-color: #000; }
                                                        #player { width: 100%; height: 100%; }
                                                      </style>
                                                    </head>
                                                    <body>
                                                      <div id="player"></div>
                                                      <script>
                                                        var tag = document.createElement('script');
                                                        tag.src = "https://www.youtube.com/iframe_api";
                                                        var firstScriptTag = document.getElementsByTagName('script')[0];
                                                        firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                                                        var player;
                                                        function onYouTubeIframeAPIReady() {
                                                          player = new YT.Player('player', {
                                                            height: '100%',
                                                            width: '100%',
                                                            videoId: '${d.trailerId}',
                                                            playerVars: {
                                                              'autoplay': 1,
                                                              'mute': 1,
                                                              'controls': 0,
                                                              'modestbranding': 1,
                                                              'rel': 0,
                                                              'fs': 0,
                                                              'iv_load_policy': 3,
                                                              'cc_load_policy': 0,
                                                              'disablekb': 0,
                                                              'playsinline': 1,
                                                              'showinfo': 0
                                                            },
                                                            events: {
                                                              'onReady': onPlayerReady,
                                                              'onStateChange': onPlayerStateChange
                                                            }
                                                          });
                                                        }

                                                        function onPlayerReady(event) {
                                                          event.target.playVideo();
                                                          event.target.mute();
                                                        }

                                                        function onPlayerStateChange(event) {
                                                          if (window.AndroidInterface) {
                                                            window.AndroidInterface.onPlayerStateChange(event.data);
                                                          }
                                                          if (event.data === 0) { // ENDED
                                                            player.playVideo();
                                                          }
                                                        }
                                                        
                                                        window.addEventListener('message', function(e) {
                                                          try {
                                                            var data = JSON.parse(e.data);
                                                            if (data.event === 'command') {
                                                              if (data.func === 'mute') {
                                                                player.mute();
                                                              } else if (data.func === 'unMute') {
                                                                player.unMute();
                                                              }
                                                            }
                                                          } catch (err) {}
                                                        });
                                                      </script>
                                                    </body>
                                                    </html>
                                                """.trimIndent()
                                                
                                                loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
                                                webViewRef = this
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .scale(1.35f) // Zoom by 35%
                                    )
                                }

                                LaunchedEffect(isMuted) {
                                    val action = if (isMuted) "mute" else "unMute"
                                    webViewRef?.evaluateJavascript(
                                        "window.postMessage('{\"event\":\"command\",\"func\":\"$action\",\"args\":\"\"}', '*');",
                                        null
                                    )
                                }

                                val lifecycleOwner = LocalLifecycleOwner.current
                                DisposableEffect(lifecycleOwner) {
                                    val observer = LifecycleEventObserver { _, event ->
                                        when (event) {
                                            Lifecycle.Event.ON_PAUSE -> {
                                                webViewRef?.onPause()
                                            }
                                            Lifecycle.Event.ON_RESUME -> {
                                                webViewRef?.onResume()
                                            }
                                            Lifecycle.Event.ON_DESTROY -> {
                                                webViewRef?.destroy()
                                                timerJob?.cancel()
                                            }
                                            else -> {}
                                        }
                                    }
                                    lifecycleOwner.lifecycle.addObserver(observer)
                                    onDispose {
                                        lifecycleOwner.lifecycle.removeObserver(observer)
                                        webViewRef?.destroy()
                                        timerJob?.cancel()
                                    }
                                }

                                // Interactive blocker overlay (Placed on top in Compose to block all user interactions)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color(0x30000000),
                                                    Color(0xFF000000)
                                                )
                                            )
                                        )
                                        .clickable(enabled = false) {} // Consumes touch gestures
                                )

                                // Blur loading overlay on top
                                if (showBlurOverlay) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.95f)) // heavy dim
                                            .clickable(enabled = false) {}, // Intercept clicks
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            CircularProgressIndicator(color = Color(0xFFD0BCFF))
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "loading...",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // Mute overlay button
                                IconButton(
                                    onClick = { isMuted = !isMuted },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) VolumeOffIcon else VolumeUpIcon,
                                        contentDescription = "Toggle Mute",
                                        tint = Color.White
                                    )
                                }
                            } else {
                                Image(
                                    painter = rememberAsyncImagePainter(model = resolvedBackdropUrl),
                                    contentDescription = d.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color(0x30000000),
                                                    Color(0xFF000000)
                                                )
                                            )
                                        )
                                )
                            }
                        }

                        // 2. Poster, Title and Metadata row (Fix clipping by removing negative offset)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(model = d.coverUrl),
                                contentDescription = d.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(110.dp, 160.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Column(
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = d.title,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${d.score}% Match",
                                        fontSize = 13.sp,
                                        color = Color(0xFFD0BCFF), // Light purple
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "${episodesList.size} Episodes",
                                        fontSize = 13.sp,
                                        color = Color.LightGray
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = d.genres.joinToString(", "),
                                    fontSize = 12.sp,
                                    color = Color.White, // Genres white color
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (d.nextAiring != null) {
                                    val na = d.nextAiring
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val days = na.timeUntilAiring / 86400
                                    val hours = (na.timeUntilAiring % 86400) / 3600
                                    val timeStr = if (days > 0) "${days}d ${hours}h" else "${hours}h"
                                    
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Next Episode: Ep ${na.episode} airs in $timeStr",
                                            color = Color(0xFFD0BCFF), // Light purple
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Completed",
                                            color = Color(0xFFD0BCFF), // Light purple
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Play / Resume and My List Action Buttons
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            val playEpNum = lastWatched?.episodeNumber ?: 1
                            val buttonText = if (lastWatched != null) "Resume Episode $playEpNum" else "Play Episode 1"
                            
                            Button(
                                onClick = {
                                    isStreamLoading = true
                                    coroutineScope.launch {
                                        val targetEpMeta = episodeMetaMap[playEpNum]
                                        val resolvedEpCover = getResolvedEpisodeCover(d.id, playEpNum, d.coverUrl, targetEpMeta)
                                        fetchAndPlayStream(d.id, playEpNum, d.title, resolvedEpCover, streamType, d.coverUrl, d.episodesCount)
                                        isStreamLoading = false
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = buttonText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // My List Action
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        val animeBrief = AnimeBrief(
                                            id = d.id,
                                            title = d.title,
                                            coverUrl = d.coverUrl,
                                            bannerUrl = d.bannerUrl,
                                            description = d.description,
                                            genres = d.genres,
                                            averageScore = d.score,
                                            logoUrl = "",
                                            episodes = d.episodesCount,
                                            status = ""
                                        )
                                        val isNowInLibrary = libraryManager.toggleLibraryItem(animeBrief)
                                        isInLibrary = isNowInLibrary
                                        val toastText = if (isNowInLibrary) "Added to My List" else "Removed from My List"
                                        Toast.makeText(this@AnimeBoxDetailActivity, toastText, Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isInLibrary) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = "My List Status",
                                    tint = Color(0xFFD0BCFF) // Light purple
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "My List",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // 4. Description
                        Text(
                            text = d.description,
                            fontSize = 14.sp,
                            color = Color.LightGray,
                            lineHeight = 20.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 5. Cast and Characters List
                        if (d.characters.isNotEmpty()) {
                            Text(
                                text = "Cast & Characters",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(d.characters) { char ->
                                    CharacterCastCard(char)
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // 6. Section Tabs (Episodes | More Like This | Related)
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
                            Tab(
                                selected = selectedSectionTab == 1,
                                onClick = { selectedSectionTab = 1 },
                                text = { Text("More Like This", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                            )
                            Tab(
                                selected = selectedSectionTab == 2,
                                onClick = { selectedSectionTab = 2 },
                                text = { Text("Related", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                            )
                        }

                        // 7. Render Active Tab Content
                        when (selectedSectionTab) {
                            0 -> {
                                // EPISODES TAB
                                // View mode and Sorting selectors (Shift View Toggles to Left, Sort Toggle to Right)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left side: In-Place View Mode Toggle
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "View: ", color = Color.Gray, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (episodeViewMode == "image") "Image View" else "Number View",
                                            color = Color(0xFFD0BCFF),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                episodeViewMode = if (episodeViewMode == "image") "number" else "image"
                                            }
                                        )
                                    }

                                    // Right side: Sorting Toggle Order
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "Sort: ", color = Color.Gray, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (episodeSortOrder == "asc") "Oldest First" else "Newest First",
                                            color = Color(0xFFD0BCFF), // Light purple
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                episodeSortOrder = if (episodeSortOrder == "asc") "desc" else "asc"
                                            }
                                        )
                                    }
                                }

                                // Search bar
                                OutlinedTextField(
                                    value = episodeSearchQuery,
                                    onValueChange = { episodeSearchQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    placeholder = { Text("Search episode name or number...", color = Color.Gray) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFFD0BCFF),
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedPlaceholderColor = Color.Gray,
                                        unfocusedPlaceholderColor = Color.Gray
                                    )
                                )

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
                                        CircularProgressIndicator(color = Color(0xFFD0BCFF))
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
                                                // IMAGE THUMBNAIL VIEW
                                                filteredEpisodes.forEach { epNum ->
                                                    val meta = episodeMetaMap[epNum]
                                                    EpisodeRowCard(
                                                        anilistId = d.id,
                                                        episodeNum = epNum,
                                                        title = meta?.title ?: "Episode $epNum",
                                                        defaultImageUrl = meta?.imageUrl ?: d.coverUrl,
                                                        meta = meta,
                                                        isLastWatched = (lastWatched != null && lastWatched.episodeNumber == epNum),
                                                        onClick = {
                                                            isStreamLoading = true
                                                            coroutineScope.launch {
                                                                val resolvedEpCover = getResolvedEpisodeCover(d.id, epNum, d.coverUrl, meta)
                                                                fetchAndPlayStream(d.id, epNum, d.title, resolvedEpCover, streamType, d.coverUrl, d.episodesCount)
                                                                isStreamLoading = false
                                                            }
                                                        }
                                                    )
                                                }
                                            } else {
                                                // COMPACT NUMBER GRID VIEW (4 columns)
                                                val chunkedNumbers = filteredEpisodes.chunked(4)
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    chunkedNumbers.forEach { rowPair ->
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            rowPair.forEach { epNum ->
                                                                val meta = episodeMetaMap[epNum]
                                                                val isLast = (lastWatched != null && lastWatched.episodeNumber == epNum)
                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .aspectRatio(1.2f)
                                                                        .clip(RoundedCornerShape(12.dp))
                                                                        .background(if (isLast) Color(0xFFD0BCFF).copy(alpha = 0.15f) else Color(0xFF161616))
                                                                        .border(
                                                                            width = if (isLast) 1.5.dp else 1.dp,
                                                                            color = if (isLast) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.05f),
                                                                            shape = RoundedCornerShape(12.dp)
                                                                        )
                                                                        .clickable {
                                                                            isStreamLoading = true
                                                                            coroutineScope.launch {
                                                                                val resolvedEpCover = getResolvedEpisodeCover(d.id, epNum, d.coverUrl, meta)
                                                                                fetchAndPlayStream(d.id, epNum, d.title, resolvedEpCover, streamType, d.coverUrl, d.episodesCount)
                                                                                isStreamLoading = false
                                                                            }
                                                                        },
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = "$epNum",
                                                                        color = if (isLast) Color(0xFFD0BCFF) else Color.White,
                                                                        fontSize = 15.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }
                                                            }
                                                            if (rowPair.size < 4) {
                                                                repeat(4 - rowPair.size) {
                                                                    Spacer(modifier = Modifier.weight(1f))
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Show "Load More Episodes" if there's more to paginate and user isn't searching
                                            if (episodeSearchQuery.isEmpty() && episodesList.size > visibleEpisodesCount) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = { visibleEpisodesCount += 50 },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(44.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFFD0BCFF), // Light purple
                                                        contentColor = Color.Black // Dark text
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
                                // MORE LIKE THIS TAB (Filtered recommendations)
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
                            2 -> {
                                // RELATED TAB (Include TV/Movies & sequels/prequels/side story movies)
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
        onClick: () -> Unit
    ) {
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
                    .aspectRatio(0.678f) // Homepage aspect ratio for full poster without cut
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
                            .background(Color(0xFFD0BCFF), shape = RoundedCornerShape(4.dp)) // Light purple badge
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Add to My List top-right button matching home screen posters ditto
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable {
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
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (inLibrary) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = "My List Toggle",
                        tint = Color(0xFFFFB300), // Keep gold/yellow for poster-card My List button
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Bottom bar matching homepage PremiumAnimePosterCard
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(26.dp)
                        .background(Color.Black.copy(alpha = 0.7f)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CC", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "$epCount",
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                    Text("|", color = Color.Gray.copy(alpha = 0.5f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(8.dp))

                    CustomMicIcon(color = Color.White)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "$epCount",
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
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
        val context = androidx.compose.ui.platform.LocalContext.current
        val resId = remember(context) {
            context.resources.getIdentifier("ic_mic_custom", "drawable", context.packageName)
        }
        if (resId != 0) {
            Icon(
                painter = rememberAsyncImagePainter(model = resId),
                contentDescription = "Mic Icon",
                tint = color,
                modifier = modifier.size(18.dp)
            )
        } else {
            Box(
                modifier = modifier
                    .size(18.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp, 8.dp)
                        .background(color, RoundedCornerShape(1.dp))
                )
            }
        }
    }

    @Composable
    fun NetflixBottomNav(onTabSelected: (Int) -> Unit) {
        NavigationBar(
            containerColor = Color.Black,
            tonalElevation = 0.dp,
            modifier = Modifier.border(0.5.dp, Color(0xFF1E1E1E), RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
        ) {
            NavigationBarItem(
                selected = false,
                onClick = { onTabSelected(0) },
                icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Home") },
                label = { Text("Home", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFD0BCFF),
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = Color(0xFFD0BCFF),
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
            NavigationBarItem(
                selected = false,
                onClick = { onTabSelected(1) },
                icon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                label = { Text("Search", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFD0BCFF),
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = Color(0xFFD0BCFF),
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
            NavigationBarItem(
                selected = false,
                onClick = { onTabSelected(2) },
                icon = { Icon(imageVector = Icons.Default.List, contentDescription = "My List") },
                label = { Text("My List", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFD0BCFF),
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = Color(0xFFD0BCFF),
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }

    @Composable
    fun CharacterCastCard(character: AnimeCharacter) {
        Card(
            modifier = Modifier
                .width(130.dp)
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            shape = RoundedCornerShape(6.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = character.imageUrl),
                        contentDescription = character.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (character.role.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .background(
                                    if (character.role.equals("MAIN", ignoreCase = true)) Color(0xFFD0BCFF) else Color.DarkGray, // Light purple
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = character.role,
                                color = if (character.role.equals("MAIN", ignoreCase = true)) Color.Black else Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Column(modifier = Modifier.padding(6.dp)) {
                    Text(
                        text = character.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (character.actorName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = character.actorName,
                            color = Color.Gray,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
        onClick: () -> Unit
    ) {
        var imageUrl by remember(episodeNum, defaultImageUrl) { mutableStateOf(defaultImageUrl) }

        if (anilistId == 21 || anilistId == 235) {
            LaunchedEffect(episodeNum) {
                val tmdbId = if (anilistId == 21) 37854 else 30983
                if (meta != null) {
                    val tmdbImg = AniZipClient.getTmdbEpisodeImage(tmdbId, meta.seasonNumber, meta.episodeNumber)
                    if (tmdbImg.isNotEmpty()) {
                        imageUrl = tmdbImg
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(Color.Transparent)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(152.dp, 84.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = imageUrl),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Compact bottom-left translucent Play capsule badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(9.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Play",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
            ) {
                val seasonStr = "S${meta?.seasonNumber ?: 1}"
                val displayTitle = if (title.startsWith("Episode ", ignoreCase = true)) "" else title
                val fullHeader = if (displayTitle.isNotEmpty()) "$seasonStr E$episodeNum - $displayTitle" else "$seasonStr E$episodeNum"

                Text(
                    text = fullHeader,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val dateStr = if (!meta?.airdate.isNullOrEmpty()) "${meta!!.airdate}  " else ""
                val durationStr = if (meta?.runtime != null && meta.runtime > 0) "${meta.runtime} min" else "24 min"
                Text(
                    text = "$dateStr$durationStr",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                val overviewText = if (!meta?.overview.isNullOrEmpty()) meta!!.overview else "Episode $episodeNum of $title"
                Text(
                    text = overviewText,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }




















    private suspend fun fetchAndPlayStream(anilistId: Int, episodeNum: Int, animeTitle: String, episodeCoverUrl: String, type: String, showCoverUrl: String, totalEpisodes: Int) {
        var streamInfo = withContext(Dispatchers.IO) {
            if (type == "hindi") {
                // 1. Try anidrive API first
                try {
                    val aniDriveUrl = "https://anidrive-stream-finder.lovable.app/api/public/stream?anilist=$anilistId&ep=$episodeNum"
                    val req = Request.Builder().url(aniDriveUrl).build()
                    OkHttpClient().newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val jsonStr = resp.body?.string() ?: ""
                            if (jsonStr.isNotEmpty()) {
                                val json = JSONObject(jsonStr)
                                if (json.optBoolean("success", false) && json.has("data")) {
                                    val data = json.getJSONObject("data")
                                    val rawUrl = data.optString("url", "")
                                    val qualitiesList = mutableListOf<Map<String, String>>()
                                    if (data.has("qualities") && !data.isNull("qualities")) {
                                        val qArr = data.getJSONArray("qualities")
                                        for (i in 0 until qArr.length()) {
                                            val q = qArr.getJSONObject(i)
                                            val label = q.optString("label", "")
                                            val qUrl = q.optString("url", "")
                                            if (label.isNotEmpty() && qUrl.isNotEmpty()) {
                                                qualitiesList.add(mapOf("label" to label, "url" to qUrl, "hls" to qUrl, "referer" to ""))
                                            }
                                        }
                                    }

                                    fun getQualityRank(lbl: String): Int {
                                        val l = lbl.lowercase()
                                        return when {
                                            l.contains("1080") -> 1080
                                            l.contains("720") -> 720
                                            l.contains("480") -> 480
                                            l.contains("360") -> 360
                                            l.contains("240") -> 240
                                            else -> 0
                                        }
                                    }
                                    qualitiesList.sortByDescending { getQualityRank(it["label"] ?: "") }

                                    val mainUrl = if (qualitiesList.isNotEmpty()) qualitiesList.first()["url"] ?: rawUrl else rawUrl
                                    if (mainUrl.isNotEmpty()) {
                                        var subtitleUrl = ""
                                        var introStart = 0L; var introEnd = 0L
                                        var outroStart = 0L; var outroEnd = 0L
                                        try {
                                            val subUrl = "https://multimovieapi-main.vercel.app/api/anime?anilistId=$anilistId&episode=$episodeNum&type=sub"
                                            val subReq = Request.Builder().url(subUrl).build()
                                            OkHttpClient().newCall(subReq).execute().use { subRes ->
                                                if (subRes.isSuccessful) {
                                                    val subJson = JSONObject(subRes.body?.string() ?: "")
                                                    if (subJson.has("response")) {
                                                        val respObj = subJson.getJSONObject("response")
                                                        val containerKey = if (respObj.has("ssub")) "ssub" else if (respObj.has("sdub")) "sdub" else respObj.keys().asSequence().firstOrNull()
                                                        if (containerKey != null) {
                                                            val subObj = respObj.getJSONObject(containerKey)
                                                            if (subObj.has("subtitles") && !subObj.isNull("subtitles")) {
                                                                val subtitles = subObj.getJSONArray("subtitles")
                                                                for (i in 0 until subtitles.length()) {
                                                                    val sub = subtitles.getJSONObject(i)
                                                                    val label = sub.optString("label", sub.optString("language", "")).lowercase()
                                                                    val file = sub.optString("file", sub.optString("url", ""))
                                                                    if ((label.contains("english") || label.contains("en") || subtitleUrl.isEmpty()) && file.isNotEmpty()) {
                                                                        subtitleUrl = file
                                                                        if (label.contains("english")) break
                                                                    }
                                                                }
                                                            }
                                                            if (subObj.has("intro") && !subObj.isNull("intro")) {
                                                                val intro = subObj.getJSONObject("intro")
                                                                introStart = intro.optLong("start", 0L)
                                                                introEnd = intro.optLong("end", 0L)
                                                            }
                                                            if (subObj.has("outro") && !subObj.isNull("outro")) {
                                                                val outro = subObj.getJSONObject("outro")
                                                                outroStart = outro.optLong("start", 0L)
                                                                outroEnd = outro.optLong("end", 0L)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {}

                                        val hindiListJson = if (qualitiesList.isNotEmpty()) {
                                            org.json.JSONArray().apply {
                                                qualitiesList.forEach { put(JSONObject(it)) }
                                            }.toString()
                                        } else ""

                                        return@withContext mapOf(
                                            "hls" to mainUrl,
                                            "referer" to "",
                                            "subtitle" to subtitleUrl,
                                            "introStart" to introStart,
                                            "introEnd" to introEnd,
                                            "outroStart" to outroStart,
                                            "outroEnd" to outroEnd,
                                            "hindiStreamsJson" to hindiListJson
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }

                // 2. Fallback to multimovieapi Hindi endpoint if anidrive fails
                val hindiUrl = "https://multimovieapi-main.vercel.app/api/hindi?anilistId=$anilistId&episode=$episodeNum"
                val hindiReq = Request.Builder().url(hindiUrl).build()
                val hindiList = mutableListOf<Map<String, String>>()
                try {
                    OkHttpClient().newCall(hindiReq).execute().use { response ->
                        if (response.isSuccessful) {
                            val json = JSONObject(response.body?.string() ?: "")
                            if (json.has("streams")) {
                                val streams = json.getJSONArray("streams")
                                for (i in 0 until streams.length()) {
                                    val s = streams.getJSONObject(i)
                                    if (s.has("hls") && !s.isNull("hls")) {
                                        val hls = s.getString("hls")
                                        val headers = s.optJSONObject("headers")
                                        val referer = headers?.optString("Referer", "") ?: ""
                                        hindiList.add(mapOf("hls" to hls, "referer" to referer))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }

                var subtitleUrl = ""
                var introStart = 0L; var introEnd = 0L
                var outroStart = 0L; var outroEnd = 0L
                try {
                    val subUrl = "https://multimovieapi-main.vercel.app/api/anime?anilistId=$anilistId&episode=$episodeNum&type=sub"
                    val subReq = Request.Builder().url(subUrl).build()
                    OkHttpClient().newCall(subReq).execute().use { subRes ->
                        if (subRes.isSuccessful) {
                            val subJson = JSONObject(subRes.body?.string() ?: "")
                            if (subJson.has("response")) {
                                val respObj = subJson.getJSONObject("response")
                                val containerKey = if (respObj.has("ssub")) "ssub" else if (respObj.has("sdub")) "sdub" else respObj.keys().asSequence().firstOrNull()
                                if (containerKey != null) {
                                    val subObj = respObj.getJSONObject(containerKey)
                                    if (subObj.has("subtitles") && !subObj.isNull("subtitles")) {
                                        val subtitles = subObj.getJSONArray("subtitles")
                                        for (i in 0 until subtitles.length()) {
                                            val sub = subtitles.getJSONObject(i)
                                            val label = sub.optString("label", sub.optString("language", "")).lowercase()
                                            val file = sub.optString("file", sub.optString("url", ""))
                                            if ((label.contains("english") || label.contains("en") || subtitleUrl.isEmpty()) && file.isNotEmpty()) {
                                                subtitleUrl = file
                                                if (label.contains("english")) break
                                            }
                                        }
                                    }
                                    if (subObj.has("intro") && !subObj.isNull("intro")) {
                                        val intro = subObj.getJSONObject("intro")
                                        introStart = intro.optLong("start", 0L)
                                        introEnd = intro.optLong("end", 0L)
                                    }
                                    if (subObj.has("outro") && !subObj.isNull("outro")) {
                                        val outro = subObj.getJSONObject("outro")
                                        outroStart = outro.optLong("start", 0L)
                                        outroEnd = outro.optLong("end", 0L)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}

                if (hindiList.isNotEmpty()) {
                    val first = hindiList.first()
                    val hindiListJson = org.json.JSONArray().apply {
                        hindiList.forEach { put(JSONObject(it)) }
                    }.toString()
                    
                    mapOf(
                        "hls" to first["hls"],
                        "referer" to first["referer"],
                        "subtitle" to subtitleUrl,
                        "introStart" to introStart,
                        "introEnd" to introEnd,
                        "outroStart" to outroStart,
                        "outroEnd" to outroEnd,
                        "hindiStreamsJson" to hindiListJson
                    )
                } else null
            } else {
                val url = "https://multimovieapi-main.vercel.app/api/anime?anilistId=$anilistId&episode=$episodeNum&type=$type"
                val request = Request.Builder().url(url).build()
                try {
                    OkHttpClient().newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val json = JSONObject(response.body?.string() ?: "")
                            if (json.has("primary")) {
                                val primary = json.getJSONObject("primary")
                                val hls = primary.getString("hls")
                                val headers = primary.getJSONObject("headers")
                                val referer = if (headers.has("Referer")) headers.getString("Referer") else ""

                                var subtitleUrl = ""
                                if (primary.has("tracks") && !primary.isNull("tracks")) {
                                    val tracks = primary.getJSONArray("tracks")
                                    for (i in 0 until tracks.length()) {
                                        val track = tracks.getJSONObject(i)
                                        val lang = track.optString("lang", "").lowercase()
                                        val urlTrack = track.optString("url", "")
                                        if (lang.contains("english") || lang.contains("en") || subtitleUrl.isEmpty()) {
                                            subtitleUrl = urlTrack
                                            if (lang.contains("english")) break
                                        }
                                    }
                                }

                                // Parse chapters array for intro/outro timestamps
                                var introStart = 0L; var introEnd = 0L
                                var outroStart = 0L; var outroEnd = 0L
                                if (primary.has("chapters") && !primary.isNull("chapters")) {
                                    val chapters = primary.getJSONArray("chapters")
                                    for (i in 0 until chapters.length()) {
                                        val ch = chapters.getJSONObject(i)
                                        when (ch.optString("title", "").lowercase()) {
                                            "intro" -> { introStart = ch.optLong("start", 0L); introEnd = ch.optLong("end", 0L) }
                                            "outro" -> { outroStart = ch.optLong("start", 0L); outroEnd = ch.optLong("end", 0L) }
                                        }
                                    }
                                }

                                 var backupHls = ""
                                 var backupProvider = ""
                                 if (json.has("backup") && !json.isNull("backup")) {
                                     val backup = json.getJSONObject("backup")
                                     backupHls = backup.optString("hls", "")
                                     backupProvider = backup.optString("provider", "")
                                 }

                                 // If type is dub and no subtitle found, fetch sub to get subtitles
                                 if (type == "dub" && subtitleUrl.isEmpty()) {
                                     val subUrl = "https://multimovieapi-main.vercel.app/api/anime?anilistId=$anilistId&episode=$episodeNum&type=sub"
                                     val subReq = Request.Builder().url(subUrl).build()
                                     try {
                                         OkHttpClient().newCall(subReq).execute().use { subRes ->
                                             if (subRes.isSuccessful) {
                                                 val subJson = JSONObject(subRes.body?.string() ?: "")
                                                 if (subJson.has("primary")) {
                                                     val subPrim = subJson.getJSONObject("primary")
                                                     if (subPrim.has("tracks") && !subPrim.isNull("tracks")) {
                                                         val subTracks = subPrim.getJSONArray("tracks")
                                                         for (k in 0 until subTracks.length()) {
                                                             val t = subTracks.getJSONObject(k)
                                                             val lang = t.optString("lang", "").lowercase()
                                                             val urlTrack = t.optString("url", "")
                                                             if (lang.contains("english") || lang.contains("en") || subtitleUrl.isEmpty()) {
                                                                 subtitleUrl = urlTrack
                                                                 if (lang.contains("english")) break
                                                             }
                                                         }
                                                     }
                                                 }
                                             }
                                         }
                                     } catch (e: Exception) {}
                                 }

                                  if (hls.isNotEmpty()) {
                                      mapOf(
                                          "hls" to hls,
                                          "referer" to referer,
                                          "subtitle" to subtitleUrl,
                                          "introStart" to introStart,
                                          "introEnd" to introEnd,
                                          "outroStart" to outroStart,
                                          "outroEnd" to outroEnd,
                                          "backupHls" to backupHls,
                                          "backupProvider" to backupProvider
                                      )
                                  } else null
                             } else null
                        } else null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

        if (streamInfo == null || (streamInfo["hls"] as? String).isNullOrEmpty()) {
            streamInfo = withContext(Dispatchers.IO) {
                try {
                    val sType = if (type == "dub") "dub" else "sub"
                    val url = "https://anime-scraper-v1.vercel.app/default/$anilistId/$sType/$episodeNum"
                    val req = Request.Builder().url(url).build()
                    OkHttpClient().newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val jsonStr = resp.body?.string() ?: ""
                            if (jsonStr.isNotEmpty()) {
                                val json = JSONObject(jsonStr)
                                val streamContainers = mutableListOf<JSONObject>()

                                if (json.has("response") && !json.isNull("response")) {
                                    val respObj = json.optJSONObject("response")
                                    if (respObj != null) {
                                        if (sType == "dub" && respObj.has("sdub") && respObj.optJSONObject("sdub") != null) {
                                            streamContainers.add(respObj.getJSONObject("sdub"))
                                        }
                                        if (respObj.has("ssub") && respObj.optJSONObject("ssub") != null) {
                                            streamContainers.add(respObj.getJSONObject("ssub"))
                                        }
                                        if (respObj.has("sdub") && respObj.optJSONObject("sdub") != null && !streamContainers.contains(respObj.optJSONObject("sdub"))) {
                                            streamContainers.add(respObj.getJSONObject("sdub"))
                                        }
                                        respObj.keys().forEach { k ->
                                            val obj = respObj.optJSONObject(k)
                                            if (obj != null && !streamContainers.contains(obj)) {
                                                streamContainers.add(obj)
                                            }
                                        }
                                    }
                                }

                                if (json.has("streams") && !json.isNull("streams")) {
                                    streamContainers.add(json)
                                }
                                val rootKeys = listOf("primary", "data", "result", "fallback")
                                for (rk in rootKeys) {
                                    if (json.has(rk) && !json.isNull(rk)) {
                                        val obj = json.optJSONObject(rk)
                                        if (obj != null && !streamContainers.contains(obj)) {
                                            streamContainers.add(obj)
                                        }
                                    }
                                }
                                if (streamContainers.isEmpty()) {
                                    streamContainers.add(json)
                                }

                                var megapHlsUrl = ""
                                var megapReferer = ""
                                var defaultHlsUrl = ""
                                var defaultReferer = ""
                                var fallbackHlsUrl = ""
                                var fallbackReferer = ""
                                var embeddedSubUrl = ""
                                var subtitleUrl = ""
                                var introStart = 0L; var introEnd = 0L
                                var outroStart = 0L; var outroEnd = 0L
                                val hlsList = mutableListOf<Map<String, String>>()

                                for (container in streamContainers) {
                                    if (container.has("streams") && !container.isNull("streams")) {
                                        val streams = container.getJSONArray("streams")
                                        for (i in 0 until streams.length()) {
                                            val s = streams.optJSONObject(i) ?: continue
                                            val t = s.optString("type", "").lowercase()
                                            val u = s.optString("url", s.optString("m3u8", s.optString("file", s.optString("stream", ""))))
                                            val isHls = t == "hls" || t == "stream" || t == "mp4" || u.contains(".m3u8") || u.contains("/stream/") || u.contains("master") || u.contains("index") || u.contains(".mp4") || u.startsWith("http")
                                            if (u.isNotEmpty() && isHls) {
                                                val embed = s.optString("embed", "")
                                                val rawRef = s.optString("referer", if (embed.isNotEmpty()) embed else "")
                                                val cleanRef = if (rawRef.contains("?")) rawRef.substring(0, rawRef.indexOf("?")) else rawRef
                                                val ref = if (cleanRef.isNotEmpty()) cleanRef else try {
                                                    val uri = android.net.Uri.parse(u)
                                                    "${uri.scheme}://${uri.host}/"
                                                } catch (e: Exception) { "" }
                                                val server = s.optString("server", s.optString("name", s.optString("provider", ""))).lowercase()
                                                val isDefault = s.optBoolean("default", false)

                                                fun extractSub(link: String): String {
                                                    if (link.contains("sub=")) {
                                                        val idx = link.indexOf("sub=")
                                                        val raw = link.substring(idx + 4).let { val e = it.indexOf("&"); if (e != -1) it.substring(0, e) else it }
                                                        return try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (ex: Exception) { raw }
                                                    }
                                                    return ""
                                                }

                                                val subCandidate = extractSub(embed).ifEmpty { extractSub(rawRef) }
                                                if (subCandidate.isNotEmpty() && embeddedSubUrl.isEmpty()) {
                                                    embeddedSubUrl = subCandidate
                                                }

                                                val item = mapOf("hls" to u, "referer" to ref, "server" to server)
                                                if (!hlsList.any { it["hls"] == u }) {
                                                    hlsList.add(item)
                                                }

                                                val refLower = rawRef.lowercase()
                                                if ((refLower.contains("megaplay") || refLower.contains("megap") || server.contains("megaplay") || server.contains("megap")) && megapHlsUrl.isEmpty()) {
                                                    megapHlsUrl = u
                                                    megapReferer = ref
                                                } else if (isDefault && defaultHlsUrl.isEmpty()) {
                                                    defaultHlsUrl = u
                                                    defaultReferer = ref
                                                } else if (fallbackHlsUrl.isEmpty()) {
                                                    fallbackHlsUrl = u
                                                    fallbackReferer = ref
                                                }
                                            }
                                        }
                                    }

                                    if (subtitleUrl.isEmpty() && container.has("subtitles") && !container.isNull("subtitles")) {
                                        val subtitles = container.getJSONArray("subtitles")
                                        for (i in 0 until subtitles.length()) {
                                            val sub = subtitles.getJSONObject(i)
                                            val label = sub.optString("label", sub.optString("language", "")).lowercase()
                                            val file = sub.optString("file", sub.optString("url", ""))
                                            if ((label.contains("english") || label.contains("en") || subtitleUrl.isEmpty()) && file.isNotEmpty()) {
                                                subtitleUrl = file
                                                if (label.contains("english")) break
                                            }
                                        }
                                    }

                                    if (introEnd == 0L && container.has("intro") && !container.isNull("intro")) {
                                        val intro = container.getJSONObject("intro")
                                        introStart = intro.optLong("start", 0L)
                                        introEnd = intro.optLong("end", 0L)
                                    }

                                    if (outroEnd == 0L && container.has("outro") && !container.isNull("outro")) {
                                        val outro = container.getJSONObject("outro")
                                        outroStart = outro.optLong("start", 0L)
                                        outroEnd = outro.optLong("end", 0L)
                                    }
                                }

                                var hlsUrl = when {
                                    megapHlsUrl.isNotEmpty() -> megapHlsUrl
                                    defaultHlsUrl.isNotEmpty() -> defaultHlsUrl
                                    fallbackHlsUrl.isNotEmpty() -> fallbackHlsUrl
                                    hlsList.isNotEmpty() -> hlsList.first()["hls"] ?: ""
                                    else -> json.optString("m3u8", json.optString("url", json.optString("hls", "")))
                                }
                                var referer = when {
                                    megapHlsUrl.isNotEmpty() -> megapReferer
                                    defaultHlsUrl.isNotEmpty() -> defaultReferer
                                    fallbackHlsUrl.isNotEmpty() -> fallbackReferer
                                    hlsList.isNotEmpty() -> hlsList.first()["referer"] ?: ""
                                    else -> ""
                                }

                                if (subtitleUrl.isEmpty() && embeddedSubUrl.isNotEmpty()) {
                                    subtitleUrl = embeddedSubUrl
                                }

                                if (hlsUrl.isNotEmpty()) {
                                    mapOf(
                                        "hls" to hlsUrl,
                                        "referer" to referer,
                                        "subtitle" to subtitleUrl,
                                        "introStart" to introStart,
                                        "introEnd" to introEnd,
                                        "outroStart" to outroStart,
                                        "outroEnd" to outroEnd
                                    )
                                } else null
                            } else null
                        } else null
                    }
                } catch (e: Exception) { null }
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
                putExtra("hlsUrl", streamInfo["hls"] as String)
                putExtra("referer", streamInfo["referer"] as String)
                putExtra("subtitleUrl", (streamInfo["subtitle"] as? String) ?: "")
                putExtra("introStart", streamInfo["introStart"] as Long)
                putExtra("introEnd", streamInfo["introEnd"] as Long)
                putExtra("outroStart", streamInfo["outroStart"] as Long)
                putExtra("outroEnd", streamInfo["outroEnd"] as Long)
                putExtra("anilistId", anilistId)
                putExtra("episode", episodeNum)
                putExtra("animeTitle", animeTitle)
                putExtra("coverUrl", episodeCoverUrl)
                putExtra("showCoverUrl", showCoverUrl)
                putExtra("totalEpisodes", totalEpisodes)
                putExtra("backupHls", (streamInfo["backupHls"] as? String) ?: "")
                putExtra("backupProvider", (streamInfo["backupProvider"] as? String) ?: "")
                putExtra("hindiStreamsJson", (streamInfo["hindiStreamsJson"] as? String) ?: "")
                putExtra("streamType", type)
                // fromContinueWatching=true triggers resume dialog if savedProgress > 0
                putExtra("fromContinueWatching", savedProgress > 0L)
            }
            startActivity(intent)
        }
    }

    private fun parseDetails(jsonString: String): AnimeDetail? {
        return try {
            val obj = JSONObject(jsonString).getJSONObject("data").getJSONObject("Media")
            val id = obj.getInt("id")
            val titleObj = if (obj.has("title") && !obj.isNull("title")) obj.getJSONObject("title") else JSONObject()
            val eng = titleObj.optString("english", "")
            val rom = titleObj.optString("romaji", "")
            val title = if (eng.isNotEmpty()) eng else if (rom.isNotEmpty()) rom else "Anime"

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

            val episodes = if (obj.has("episodes") && !obj.isNull("episodes")) {
                obj.getInt("episodes")
            } else 12

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

            var trailerId = ""
            if (obj.has("trailer") && !obj.isNull("trailer")) {
                val trailerObj = obj.getJSONObject("trailer")
                val site = trailerObj.optString("site", "").lowercase()
                if (site == "youtube") {
                    trailerId = trailerObj.optString("id", "")
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
                            if (charName.isNotEmpty()) {
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

            AnimeDetail(
                id = id,
                title = title,
                description = cleanDesc,
                coverUrl = coverUrl,
                bannerUrl = bannerUrl,
                episodesCount = episodes,
                score = score,
                genres = genresList,
                isAdult = isAdult,
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
