@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lagradost.cloudstream3.ui.animebox

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import kotlin.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.animebox.history.WatchHistoryManager
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import org.json.JSONObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import coil3.compose.rememberAsyncImagePainter
import android.app.Activity
import android.content.ContextWrapper

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) {
            return ctx
        }
        ctx = ctx.baseContext
    }
    return null
}

class AnimeBoxPlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var progressTracker: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    private fun getPrefs(): SharedPreferences =
        getSharedPreferences("AnimeBoxPlayerPrefs", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle back button properly
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipReceiver, android.content.IntentFilter("com.lagradost.cloudstream3.PIP_CONTROL"), RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipReceiver, android.content.IntentFilter("com.lagradost.cloudstream3.PIP_CONTROL"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Hide status/navigation bars for full screen playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val hlsUrl = intent.getStringExtra("hlsUrl") ?: ""
        val referer = intent.getStringExtra("referer") ?: ""
        val subtitleUrl = intent.getStringExtra("subtitleUrl") ?: ""
        val anilistId = intent.getIntExtra("anilistId", 0)
        val episodeNum = intent.getIntExtra("episode", 1)
        val animeTitle = intent.getStringExtra("animeTitle") ?: "Anime Show"
        val coverUrl = intent.getStringExtra("coverUrl") ?: ""
        val showCoverUrl = intent.getStringExtra("showCoverUrl") ?: coverUrl
        val totalEpisodes = if (anilistId == 21 || anilistId == 235) {
            9999
        } else {
            intent.getIntExtra("totalEpisodes", 0)
        }
        val streamType = intent.getStringExtra("streamType") ?: "sub"
        val fromContinueWatching = intent.getBooleanExtra("fromContinueWatching", false)

        setContent {
            MaterialTheme {
                VideoPlayerScreen(
                    hlsUrl = hlsUrl,
                    referer = referer,
                    subtitleUrl = subtitleUrl,
                    anilistId = anilistId,
                    episodeNum = episodeNum,
                    animeTitle = animeTitle,
                    coverUrl = coverUrl,
                    showCoverUrl = showCoverUrl,
                    totalEpisodes = totalEpisodes,
                    streamType = streamType,
                    fromContinueWatching = fromContinueWatching
                )
            }
        }
    }

    @OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun VideoPlayerScreen(
        hlsUrl: String,
        referer: String,
        subtitleUrl: String,
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String,
        coverUrl: String,
        showCoverUrl: String,
        totalEpisodes: Int,
        streamType: String,
        fromContinueWatching: Boolean
    ) {
        val context = LocalContext.current
        val historyManager = remember { WatchHistoryManager(context) }
        val coroutineScope = rememberCoroutineScope()
        val prefs = remember { getPrefs() }

        // ─── Load persisted settings (Session retention or fresh default) ──────
        val activeAudioExtra = remember { (context as? android.app.Activity)?.intent?.getStringExtra("activeAudio") }
        val activeSubExtra = remember { (context as? android.app.Activity)?.intent?.getStringExtra("activeSub") }
        val savedAudio = remember { activeAudioExtra ?: "Japanese (Original)" }
        val savedSub = remember { activeSubExtra ?: "English (VTT)" }
        val savedFontSize = remember { prefs.getFloat("subFontSize", 20f) } // Default to 20f (Large)
        val savedTextColor = remember { prefs.getInt("subTextColor", android.graphics.Color.WHITE) }
        val savedBgOpacity = remember { prefs.getInt("subBgOpacity", 128) }
        val savedSpeed = remember { prefs.getFloat("currentSpeed", 1.0f) }

        // ─── Intro/Outro timestamps ────────────────────────────────────────────
        val introStart = remember { (context as? android.app.Activity)?.intent?.getLongExtra("introStart", 0L) ?: 0L }
        val introEnd   = remember { (context as? android.app.Activity)?.intent?.getLongExtra("introEnd", 0L) ?: 0L }
        val outroStart = remember { (context as? android.app.Activity)?.intent?.getLongExtra("outroStart", 0L) ?: 0L }
        val outroEnd   = remember { (context as? android.app.Activity)?.intent?.getLongExtra("outroEnd", 0L) ?: 0L }

        fun parseSkipTime(value: Long): Long {
            return if (value in 1..9999L) value * 1000L else value
        }

        var isInPipMode by remember { mutableStateOf(false) }
        DisposableEffect(Unit) {
            AnimeBoxPlayerActivity.onPipModeChanged = {
                isInPipMode = it
            }
            onDispose {
                AnimeBoxPlayerActivity.onPipModeChanged = null
            }
        }

        val timelineThemeSetting = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getPlayerTimelineTheme(context) }
        val customTimelineHex = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getCustomTimelineColor(context) }
        val playerAccentColor = remember(timelineThemeSetting, customTimelineHex) {
            when (timelineThemeSetting) {
                "red" -> Color(0xFFE50914)
                "cyan" -> Color(0xFF00E5FF)
                "gold" -> Color(0xFFFFD700)
                "green" -> Color(0xFF00E676)
                "white" -> Color(0xFFFFFFFF)
                "custom" -> try { Color(android.graphics.Color.parseColor(if (customTimelineHex.startsWith("#")) customTimelineHex else "#$customTimelineHex")) } catch (_: Exception) { Color(0xFFD0BCFF) }
                else -> Color(0xFFD0BCFF)
            }
        }

        val skipIntroBtnColor = Color.White

        val brightnessModeSetting = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getBrightnessMode(context) }
        val volumeModeSetting = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getVolumeMode(context) }
        val skipIntroEnabledSetting = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isSkipIntroEnabled(context) }

        // Gesture Slider States
        var showBrightnessSlider by remember { mutableStateOf(false) }
        var brightnessValue by remember { mutableFloatStateOf(0.5f) }
        var showVolumeSlider by remember { mutableStateOf(false) }
        var volumeValue by remember { mutableFloatStateOf(0.5f) }

        // Auto-hide gesture sliders
        LaunchedEffect(showBrightnessSlider) {
            if (showBrightnessSlider) {
                delay(1500)
                showBrightnessSlider = false
            }
        }
        LaunchedEffect(showVolumeSlider) {
            if (showVolumeSlider) {
                delay(1500)
                showVolumeSlider = false
            }
        }

        // ─── Current mutable episode & stream state ─────────
        var currentEpisodeNum by remember { mutableStateOf(episodeNum) }
        var currentCoverUrl by remember { mutableStateOf(coverUrl) }
        var currentIntroStart by remember { mutableStateOf(introStart) }
        var currentIntroEnd   by remember { mutableStateOf(introEnd) }
        var currentOutroStart by remember { mutableStateOf(outroStart) }
        var currentOutroEnd   by remember { mutableStateOf(outroEnd) }

        var currentHlsUrl by remember { mutableStateOf(hlsUrl) }
        var currentReferer by remember { mutableStateOf(referer) }
        var currentSubtitleUrl by remember { mutableStateOf(subtitleUrl) }
        var currentBackupHls by remember { mutableStateOf((context as? android.app.Activity)?.intent?.getStringExtra("backupHls") ?: "") }
        var currentBackupProvider by remember { mutableStateOf((context as? android.app.Activity)?.intent?.getStringExtra("backupProvider") ?: "") }
        val initialStreamType = remember {
            if (activeAudioExtra != null) {
                streamType
            } else {
                "sub"
            }
        }
        var currentStreamType by remember { mutableStateOf(initialStreamType) }
        var isReloadingStream by remember { mutableStateOf(false) }

        LaunchedEffect(currentHlsUrl) {
            if (currentHlsUrl.isEmpty()) {
                isReloadingStream = true
                coroutineScope.launch {
                    val fbMap = fetchAnimeScraperFallback(anilistId, currentEpisodeNum, currentStreamType)
                    isReloadingStream = false
                    if (fbMap != null && (fbMap["hls"] as? String)?.isNotEmpty() == true) {
                        val fbHls = fbMap["hls"] as String
                        currentHlsUrl = fbHls
                        currentReferer = (fbMap["referer"] as? String) ?: ""
                        val fbSub = (fbMap["subtitle"] as? String) ?: ""
                        if (fbSub.isNotEmpty()) {
                            currentSubtitleUrl = fbSub
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Failed to load episode stream", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // ─── Player state variables ────────────────────────────────────────────
        var isPlaying by remember { mutableStateOf(false) }
        var currentPosition by remember { mutableStateOf(0L) }
        var duration by remember { mutableStateOf(0L) }
        var controlsVisible by remember { mutableStateOf(true) }
        var isLocked by remember { mutableStateOf(false) }
        var showUnlockNotification by remember { mutableStateOf(false) }
        var lockedControlsVisible by remember { mutableStateOf(false) }
        var currentSpeed by remember { mutableStateOf(savedSpeed) }
        var isNextEpLoading by remember { mutableStateOf(false) }
        var showAudioSubtitlesPanel by remember { mutableStateOf(false) }
        var showSpeedQualityPanel by remember { mutableStateOf(false) }
        var currentQuality by remember { mutableStateOf("Auto") }
        var isBuffering by remember { mutableStateOf(false) }
        var subFontSize by remember { mutableStateOf(savedFontSize) }
        var subTextColor by remember { mutableStateOf(savedTextColor) }
        var subBgOpacity by remember { mutableStateOf(savedBgOpacity) }
        var showSubtitleStyleSettings by remember { mutableStateOf(false) }
        var playerViewInstance by remember { mutableStateOf<PlayerView?>(null) }
        // Use outer state (not shadowed) for selected audio/sub
        var selectedSub by remember { mutableStateOf(savedSub) }
        var selectedAudio by remember { mutableStateOf(savedAudio) }
        
        val hindiJsonStr = remember { (context as? android.app.Activity)?.intent?.getStringExtra("hindiStreamsJson") ?: "" }
        var hindiStreamsList by remember {
            mutableStateOf<List<Map<String, String>>>(
                try {
                    if (hindiJsonStr.isNotEmpty()) {
                        val arr = org.json.JSONArray(hindiJsonStr)
                        val list = mutableListOf<Map<String, String>>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(mapOf("hls" to obj.getString("hls"), "referer" to obj.getString("referer")))
                        }
                        list
                    } else emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            )
        }
        var currentHindiIndex by remember { mutableStateOf(0) }
        var candidateHlsList by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
        var currentCandidateIndex by remember { mutableStateOf(0) }

        LaunchedEffect(currentStreamType) {
            selectedAudio = if (currentStreamType == "hindi") "Hindi" else if (currentStreamType == "dub") "English" else "Japanese (Original)"
        }
        var showEpisodesPanel by remember { mutableStateOf(false) }
        var episodeMetaMap by remember { mutableStateOf<Map<Int, com.lagradost.cloudstream3.ui.animebox.api.EpisodeMeta>>(emptyMap()) }
        var episodeImages by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
        var episodeTitles by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
        var futureEps by remember { mutableStateOf<Set<Int>>(emptySet()) }
        // Resize modes: 0=FIT, 1=FILL, 2=ZOOM
        var resizeMode by remember { mutableStateOf(0) }
        var showResizeToast by remember { mutableStateOf(false) }
        var resizeToastText by remember { mutableStateOf("") }

        var showMorePanel by remember { mutableStateOf(false) }

        // Resume dialog
        val savedProgress = remember(currentEpisodeNum, fromContinueWatching) {
            if (fromContinueWatching) historyManager.getSavedProgress(anilistId, currentEpisodeNum) else 0L
        }
        var showResumeDialog by remember(currentEpisodeNum, fromContinueWatching) {
            mutableStateOf(fromContinueWatching && savedProgress > 0L)
        }
        var seekOnStart by remember { mutableStateOf<Long?>(null) }

        var startPosition by remember { mutableStateOf(0L) }

        fun switchToEpisode(targetEp: Int) {
            if (targetEp < 1 || (totalEpisodes > 0 && targetEp > totalEpisodes)) return
            if (isNextEpLoading) return

            isNextEpLoading = true
            currentPosition = 0L
            duration = 0L
            startPosition = 0L
            showResumeDialog = false
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()

            coroutineScope.launch {
                val streamInfo = withContext(Dispatchers.IO) {
                    fetchStreamInfo(anilistId, targetEp, currentStreamType)
                }
                isNextEpLoading = false

                if (streamInfo != null && (streamInfo["hls"] as? String)?.isNotEmpty() == true) {
                    val targetCover = withContext(Dispatchers.IO) {
                        if (anilistId == 21 || anilistId == 235) {
                            val tmdbId = if (anilistId == 21) 37854 else 30983
                            val allEps = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getTmdbAllEpisodes(tmdbId)
                            allEps[targetEp]?.imageUrl ?: showCoverUrl
                        } else {
                            try {
                                val mappingUrl = "https://api.ani.zip/mappings?anilist_id=$anilistId"
                                val request = okhttp3.Request.Builder().url(mappingUrl).build()
                                okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                                    if (response.isSuccessful) {
                                        val j = JSONObject(response.body?.string() ?: "")
                                        if (j.has("episodes")) {
                                            val episodes = j.getJSONObject("episodes")
                                            if (episodes.has(targetEp.toString())) {
                                                episodes.getJSONObject(targetEp.toString()).optString("image", showCoverUrl)
                                            } else showCoverUrl
                                        } else showCoverUrl
                                    } else showCoverUrl
                                }
                            } catch (e: Exception) { showCoverUrl }
                        }
                    }

                    currentEpisodeNum = targetEp
                    currentCoverUrl = targetCover
                    currentPosition = 0L
                    duration = 0L
                    startPosition = 0L
                    showResumeDialog = false

                    currentIntroStart = (streamInfo["introStart"] as? Long) ?: 0L
                    currentIntroEnd   = (streamInfo["introEnd"] as? Long) ?: 0L
                    currentOutroStart = (streamInfo["outroStart"] as? Long) ?: 0L
                    currentOutroEnd   = (streamInfo["outroEnd"] as? Long) ?: 0L

                    currentBackupHls = (streamInfo["backupHls"] as? String) ?: ""
                    currentBackupProvider = (streamInfo["backupProvider"] as? String) ?: ""

                    currentSubtitleUrl = (streamInfo["subtitle"] as? String) ?: ""
                    currentReferer     = (streamInfo["referer"] as? String) ?: ""
                    currentHlsUrl       = (streamInfo["hls"] as? String) ?: ""

                    if (streamInfo.containsKey("hlsStreams")) {
                        @Suppress("UNCHECKED_CAST")
                        val cList = streamInfo["hlsStreams"] as? List<Map<String, String>>
                        if (!cList.isNullOrEmpty()) {
                            candidateHlsList = cList
                            currentCandidateIndex = 0
                        }
                    }

                    if (streamInfo.containsKey("hindiStreams")) {
                        @Suppress("UNCHECKED_CAST")
                        val hList = streamInfo["hindiStreams"] as? List<Map<String, String>>
                        if (hList != null) {
                            hindiStreamsList = hList
                            currentHindiIndex = 0
                        }
                    }
                } else {
                    android.widget.Toast.makeText(context, "Failed to load episode stream", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ─── Build player ──────────────────────────────────────────────────────
        val player = remember(currentHlsUrl, currentReferer, currentStreamType) {
            buildPlayer(context, currentHlsUrl, currentReferer, currentSubtitleUrl, streamType = currentStreamType, startPositionMs = startPosition, playImmediately = !showResumeDialog)
        }

        // Apply resize mode to PlayerView when it changes
        LaunchedEffect(resizeMode, playerViewInstance) {
            playerViewInstance?.resizeMode = when (resizeMode) {
                1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }

        // Apply saved playback speed
        LaunchedEffect(player, currentSpeed) {
            player.setPlaybackSpeed(currentSpeed)
        }

        // Show resize toast briefly
        LaunchedEffect(showResizeToast) {
            if (showResizeToast) {
                delay(2000)
                showResizeToast = false
            }
        }

        val showSkipIntro = skipIntroEnabledSetting && !showResumeDialog &&
            currentPosition in (parseSkipTime(currentIntroStart) until parseSkipTime(currentIntroEnd)) && currentIntroEnd > 0L
        val showSkipOutro = skipIntroEnabledSetting && !showResumeDialog &&
            currentPosition in (parseSkipTime(currentOutroStart) until parseSkipTime(currentOutroEnd)) && currentOutroEnd > 0L

        LaunchedEffect(isLocked) {
            if (isLocked) {
                showUnlockNotification = true
                lockedControlsVisible = true
                delay(3000)
                showUnlockNotification = false
                lockedControlsVisible = false
            }
        }

        fun isFutureDate(dateStr: String): Boolean {
            return try {
                val cleanStr = if (dateStr.contains("T")) {
                    dateStr.substringBefore("+").substringBefore("Z")
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

        // Fetch episode images and titles from AniZip or TMDB
        LaunchedEffect(anilistId) {
            coroutineScope.launch {
                try {
                    val metaMap = if (anilistId == 21 || anilistId == 235) {
                        val tmdbId = if (anilistId == 21) 37854 else 30983
                        com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getTmdbAllEpisodes(tmdbId)
                    } else {
                        com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getEpisodeMetadata(anilistId)
                    }
                    val imgMap = mutableMapOf<Int, String>()
                    val titleMap = mutableMapOf<Int, String>()
                    val futSet = mutableSetOf<Int>()
                    
                    metaMap.forEach { (epNum, meta) ->
                        imgMap[epNum] = meta.imageUrl
                        titleMap[epNum] = meta.title
                        if (meta.airdate.isNotEmpty() && isFutureDate(meta.airdate)) {
                            futSet.add(epNum)
                        }
                    }
                    
                    episodeMetaMap = metaMap
                    episodeImages = imgMap
                    episodeTitles = titleMap
                    futureEps = futSet
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        LaunchedEffect(lockedControlsVisible, isLocked) {
            if (isLocked && lockedControlsVisible) {
                delay(4000)
                lockedControlsVisible = false
            }
        }

        // Apply subtitle style
        LaunchedEffect(playerViewInstance, subFontSize, subTextColor, subBgOpacity) {
            playerViewInstance?.subtitleView?.apply {
                val captionStyle = androidx.media3.ui.CaptionStyleCompat(
                    subTextColor,
                    android.graphics.Color.argb(subBgOpacity, 0, 0, 0),
                    android.graphics.Color.TRANSPARENT,
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    android.graphics.Color.BLACK,
                    null
                )
                setStyle(captionStyle)
                // Raise subtitle above timeline bar
                setBottomPaddingFraction(0.20f)
                val sizeFraction = when (subFontSize) {
                    12f -> 0.04f
                    20f -> 0.07f
                    else -> 0.0533f
                }
                setFractionalTextSize(sizeFraction)
            }
        }

        // Apply subtitle visibility (Off vs On)
        LaunchedEffect(selectedSub, player) {
            if (selectedSub == "Off" || selectedSub.contains("Hard Sub")) {
                // Disable subtitle track rendering
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                playerViewInstance?.subtitleView?.visibility = View.INVISIBLE
            } else {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage("en")
                    .build()
                playerViewInstance?.subtitleView?.visibility = View.VISIBLE
            }
        }

        // Update player states in response to events
        DisposableEffect(player) {
            exoPlayer = player
            currentPosition = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)

            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    isBuffering = player.playbackState == Player.STATE_BUFFERING
                    currentPosition = player.currentPosition.coerceAtLeast(0L)
                    duration = player.duration.coerceAtLeast(0L)
                }
                override fun onPlaybackStateChanged(state: Int) {
                    duration = player.duration.coerceAtLeast(0L)
                    currentPosition = player.currentPosition.coerceAtLeast(0L)
                    isBuffering = state == Player.STATE_BUFFERING
                }
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    currentPosition = player.currentPosition.coerceAtLeast(0L)
                    duration = player.duration.coerceAtLeast(0L)
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    error.printStackTrace()
                    if (currentStreamType == "hindi" && hindiStreamsList.isNotEmpty() && currentHindiIndex + 1 < hindiStreamsList.size) {
                        currentHindiIndex++
                        val nextStream = hindiStreamsList[currentHindiIndex]
                        android.widget.Toast.makeText(context, "Hindi stream source failed. Trying next source...", android.widget.Toast.LENGTH_SHORT).show()
                        currentHlsUrl = nextStream["hls"] ?: ""
                        currentReferer = nextStream["referer"] ?: ""
                    } else if (candidateHlsList.isNotEmpty() && currentCandidateIndex + 1 < candidateHlsList.size) {
                        currentCandidateIndex++
                        val nextCandidate = candidateHlsList[currentCandidateIndex]
                        val nextHls = nextCandidate["hls"] ?: ""
                        val nextRef = nextCandidate["referer"] ?: ""
                        if (nextHls.isNotEmpty()) {
                            android.widget.Toast.makeText(context, "Stream failed. Trying next server candidate...", android.widget.Toast.LENGTH_SHORT).show()
                            currentHlsUrl = nextHls
                            currentReferer = nextRef
                        }
                    } else if (currentBackupHls.isNotEmpty() && currentHlsUrl != currentBackupHls) {
                        android.widget.Toast.makeText(context, "Primary stream failed. Switching to backup...", android.widget.Toast.LENGTH_SHORT).show()
                        currentHlsUrl = currentBackupHls
                        val savedPos = player.currentPosition
                        coroutineScope.launch {
                            delay(500)
                            player.seekTo(savedPos)
                            player.play()
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Primary stream failed. Switching to fallback API...", android.widget.Toast.LENGTH_SHORT).show()
                        isReloadingStream = true
                        coroutineScope.launch {
                            val fbMap = fetchAnimeScraperFallback(anilistId, currentEpisodeNum, currentStreamType)
                            isReloadingStream = false
                            if (fbMap != null && (fbMap["hls"] as? String)?.isNotEmpty() == true) {
                                val fbHls = fbMap["hls"] as String
                                if (fbMap.containsKey("hlsStreams")) {
                                    @Suppress("UNCHECKED_CAST")
                                    val cList = fbMap["hlsStreams"] as? List<Map<String, String>>
                                    if (!cList.isNullOrEmpty()) {
                                        candidateHlsList = cList
                                        currentCandidateIndex = 0
                                    }
                                }
                                currentHlsUrl = fbHls
                                currentReferer = (fbMap["referer"] as? String) ?: ""
                                val fbSub = (fbMap["subtitle"] as? String) ?: ""
                                if (fbSub.isNotEmpty()) {
                                    currentSubtitleUrl = fbSub
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Unable to load stream for episode $currentEpisodeNum", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            player.addListener(listener)

            val tracker = object : Runnable {
                override fun run() {
                    val currentPos = player.currentPosition
                    val dur = player.duration
                    if (dur > 0 && currentPos > 0) {
                        val finalCover = if (totalEpisodes == 1) showCoverUrl else currentCoverUrl
                        historyManager.saveWatchProgress(
                            anilistId = anilistId,
                            animeTitle = animeTitle,
                            coverImageUrl = finalCover,
                            episodeNumber = currentEpisodeNum,
                            progressPositionMs = currentPos,
                            totalDurationMs = dur
                        )
                    }
                    handler.postDelayed(this, 5000)
                }
            }
            progressTracker = tracker
            handler.postDelayed(tracker, 5000)

            onDispose {
                progressTracker?.let { handler.removeCallbacks(it) }
                player.removeListener(listener)
                player.release()
                if (exoPlayer == player) {
                    exoPlayer = null
                }
            }
        }

        // Position polling
        LaunchedEffect(player, isPlaying) {
            if (isPlaying) {
                while (true) {
                    currentPosition = player.currentPosition.coerceAtLeast(0L)
                    duration = player.duration.coerceAtLeast(0L)
                    delay(250)
                }
            } else {
                currentPosition = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.coerceAtLeast(0L)
            }
        }

        // Auto-hide controls
        LaunchedEffect(controlsVisible, isPlaying) {
            if (controlsVisible && isPlaying) {
                delay(4000)
                controlsVisible = false
            }
        }

        // ─── Resume Dialog is now moved to the bottom of main UI Box to draw on top of everything ───

        // Dynamically adjust subtitle bottom margin depending on controls visibility
        LaunchedEffect(controlsVisible, isLocked, playerViewInstance) {
            val density = context.resources.displayMetrics.density
            val targetMargin = if (controlsVisible && !isLocked) (110 * density).toInt() else (35 * density).toInt()
            playerViewInstance?.subtitleView?.let { subView ->
                val lp = (subView.layoutParams as? android.widget.FrameLayout.LayoutParams)
                    ?: android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
                if (lp.bottomMargin != targetMargin) {
                    lp.bottomMargin = targetMargin
                    subView.layoutParams = lp
                }
            }
        }

        // ─── Main UI ───────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                        playerViewInstance = this
                        // Apply initial subtitle visibility
                        if (selectedSub == "Off" || selectedSub.contains("Hard Sub")) {
                            subtitleView?.visibility = View.INVISIBLE
                        }
                        
                        // Push SubtitleView layout margin up dynamically!
                        val density = ctx.resources.displayMetrics.density
                        subtitleView?.let { subView ->
                            val targetMargin = if (controlsVisible && !isLocked) (110 * density).toInt() else (35 * density).toInt()
                            val lp = (subView.layoutParams as? android.widget.FrameLayout.LayoutParams)
                                ?: android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
                            lp.bottomMargin = targetMargin
                            subView.layoutParams = lp
                            
                            // Safe layout listener to prevent ExoPlayer resets
                            subView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                                val currentLp = (v.layoutParams as? android.widget.FrameLayout.LayoutParams)
                                    ?: android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
                                val currentTarget = if (controlsVisible && !isLocked) (110 * density).toInt() else (35 * density).toInt()
                                if (currentLp.bottomMargin != currentTarget) {
                                    currentLp.bottomMargin = currentTarget
                                    v.layoutParams = currentLp
                                }
                            }
                        }
                    }
                },
                update = { view ->
                    view.player = player
                    if (selectedSub == "Off" || selectedSub.contains("Hard Sub")) {
                        view.subtitleView?.visibility = View.INVISIBLE
                    } else {
                        view.subtitleView?.visibility = View.VISIBLE
                    }
                    val density = view.context.resources.displayMetrics.density
                    view.subtitleView?.let { subView ->
                        val lp = (subView.layoutParams as? android.widget.FrameLayout.LayoutParams)
                            ?: android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
                        val targetMargin = if (controlsVisible && !isLocked) (110 * density).toInt() else (35 * density).toInt()
                        if (lp.bottomMargin != targetMargin) {
                            lp.bottomMargin = targetMargin
                            subView.layoutParams = lp
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (!isInPipMode) {
                // Resize toast notification
            if (showResizeToast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(resizeToastText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Tap/double-tap & gesture drag overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(brightnessModeSetting, volumeModeSetting) {
                        if (brightnessModeSetting == "gesture" || volumeModeSetting == "gesture") {
                            detectVerticalDragGestures(
                                onDragStart = { offset: Offset ->
                                    if (isLocked) return@detectVerticalDragGestures
                                    val width = size.width
                                    if (offset.x < width / 2 && brightnessModeSetting == "gesture") {
                                        showBrightnessSlider = true
                                        showVolumeSlider = false
                                    } else if (offset.x >= width / 2 && volumeModeSetting == "gesture") {
                                        showVolumeSlider = true
                                        showBrightnessSlider = false
                                    }
                                },
                                onDragEnd = {
                                    showBrightnessSlider = false
                                    showVolumeSlider = false
                                },
                                onDragCancel = {
                                    showBrightnessSlider = false
                                    showVolumeSlider = false
                                },
                                onVerticalDrag = { change: PointerInputChange, dragAmount: Float ->
                                    if (isLocked) return@detectVerticalDragGestures
                                    try {
                                        change.consume()
                                    } catch (_: Exception) {}
                                    val width = size.width
                                    val deltaY = -dragAmount / 400f
                                    if (change.position.x < width / 2 && brightnessModeSetting == "gesture") {
                                        showBrightnessSlider = true
                                        val act = context as? android.app.Activity
                                        val currentAttr = act?.window?.attributes
                                        val currentB = if ((currentAttr?.screenBrightness ?: -1f) < 0f) 0.5f else currentAttr?.screenBrightness ?: 0.5f
                                        val newB = (currentB + deltaY).coerceIn(0.05f, 1.0f)
                                        brightnessValue = newB
                                        if (act != null) {
                                            val lp = act.window.attributes
                                            lp.screenBrightness = newB
                                            act.window.attributes = lp
                                        }
                                    } else if (change.position.x >= width / 2 && volumeModeSetting == "gesture") {
                                        showVolumeSlider = true
                                        val newVolFraction = (volumeValue + deltaY).coerceIn(0f, 1f)
                                        volumeValue = newVolFraction
                                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                                        if (audioManager != null) {
                                            val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                            if (maxVol > 0) {
                                                val targetVol = kotlin.math.round(newVolFraction * maxVol).toInt().coerceIn(0, maxVol)
                                                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                    .pointerInput(player) {
                        detectTapGestures(
                            onTap = {
                                if (isLocked) {
                                    lockedControlsVisible = !lockedControlsVisible
                                } else {
                                    controlsVisible = !controlsVisible
                                }
                            },
                            onDoubleTap = { offset ->
                                if (!isLocked) {
                                    val width = size.width
                                    if (offset.x < width / 2) {
                                        val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
                                        player.seekTo(target)
                                        currentPosition = target
                                    } else {
                                        val target = (player.currentPosition + 10000L).coerceAtMost(player.duration)
                                        player.seekTo(target)
                                        currentPosition = target
                                    }
                                    player.play()
                                }
                            }
                        )
                    }
            )

            if (isNextEpLoading || isReloadingStream) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFD0BCFF))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isReloadingStream) "Reloading stream server..." else "Loading Next Episode...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Locked screen notification
            if (isLocked && showUnlockNotification) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Screen Locked", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // Unlock button when locked
            AnimatedVisibility(
                visible = isLocked && lockedControlsVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(24.dp)
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_player_020),
                            contentDescription = "Unlock",
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // ─── Skip Intro/Outro buttons — always visible when in window ─────
            if (showSkipIntro) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = {
                            val target = parseSkipTime(currentIntroEnd)
                            player.seekTo(target)
                            currentPosition = target
                            player.play()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = skipIntroBtnColor.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(bottom = 120.dp, end = 24.dp)
                            .border(1.dp, skipIntroBtnColor, RoundedCornerShape(8.dp))
                    ) {
                        Text("Skip Intro", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            if (showSkipOutro) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = {
                            val target = parseSkipTime(currentOutroEnd)
                            player.seekTo(target)
                            currentPosition = target
                            player.play()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = skipIntroBtnColor.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(bottom = 120.dp, end = 24.dp)
                            .border(1.dp, skipIntroBtnColor, RoundedCornerShape(8.dp))
                    ) {
                        Text("Skip Outro", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // ─── Gesture Brightness Thin-Line Vertical Slider (Left Side) ───────
            AnimatedVisibility(
                visible = showBrightnessSlider && brightnessModeSetting == "gesture" && !isLocked,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 64.dp, bottom = 40.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.wrapContentSize()
                ) {
                    // Custom Sun Icon at top of slider line
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_brightness),
                        contentDescription = "Brightness",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Thin vertical track line (6dp width, 150dp height)
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(150.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF555555)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(brightnessValue.coerceIn(0f, 1f))
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }

            // ─── Gesture Volume Thin-Line Vertical Slider (Right Side) ──────────
            AnimatedVisibility(
                visible = showVolumeSlider && volumeModeSetting == "gesture" && !isLocked,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 64.dp, bottom = 40.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.wrapContentSize()
                ) {
                    // Custom Volume ON/OFF Icon at top of slider line
                    Icon(
                        painter = painterResource(
                            id = if (volumeValue > 0f) R.drawable.ic_player_volume_on else R.drawable.ic_player_volume_off
                        ),
                        contentDescription = "Volume",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Thin vertical track line (6dp width, 150dp height)
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(150.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF555555)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(volumeValue.coerceIn(0f, 1f))
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }

            // ─── Normal Controls Overlay ───────────────────────────────────────
            AnimatedVisibility(
                visible = controlsVisible && !isLocked,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.8f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                ) {
                    // 1. Top Controls Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp) // Super large touch target
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        this@AnimeBoxPlayerActivity.finish()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_baseline_arrow_back_ios_24),
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = animeTitle,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Episode $currentEpisodeNum",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.LightGray
                                )
                            }
                        }

                        // Right: Chromecast + Picture-in-Picture buttons
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Custom Cast button
                            IconButton(onClick = {
                                android.widget.Toast.makeText(context, "Chromecast connecting...", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_player_010),
                                    contentDescription = "Cast",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // Custom PiP button
                            IconButton(onClick = {
                                val act = context as? android.app.Activity
                                try {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        act?.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setActions(emptyList()).build())
                                    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                        @Suppress("DEPRECATION")
                                        act?.enterPictureInPictureMode()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_player_011),
                                    contentDescription = "PiP",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // 2. Center Playback Buttons (Redesigned with high quality assets and previous/next episode actions)
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
                                    player.seekTo(target)
                                    currentPosition = target
                                    player.play()
                                }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_player_006),
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(64.dp))

                        // Play/Pause
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(92.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (!isBuffering) {
                                        if (isPlaying) player.pause() else player.play()
                                    }
                                }
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    color = Color(0xFFD0BCFF),
                                    modifier = Modifier.size(40.dp)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = if (isPlaying) R.drawable.ic_player_002 else R.drawable.ic_player_001),
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(54.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(64.dp))

                        // Forward 10s
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    val target = (player.currentPosition + 10000L).coerceAtMost(player.duration)
                                    player.seekTo(target)
                                    currentPosition = target
                                    player.play()
                                }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_player_005),
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    // 4. Bottom Controls Area
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Timeline row with yellow skip intro/outro stamps drawn using Canvas
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Slider(
                                    value = currentPosition.toFloat(),
                                    onValueChange = {
                                        currentPosition = it.toLong()
                                        player.seekTo(it.toLong())
                                        player.play()
                                    },
                                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                                    modifier = Modifier.fillMaxWidth(),
                                    thumb = {
                                        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                            Box(modifier = Modifier.size(10.dp).background(playerAccentColor, CircleShape))
                                        }
                                    },
                                    track = { sliderState ->
                                        androidx.compose.foundation.Canvas(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                        ) {
                                            val width = this.size.width
                                            val height = this.size.height
                                            val strokeWidth = height
                                            val centerY = height / 2

                                            // Draw unplayed line
                                            this.drawLine(
                                                color = Color.White.copy(alpha = 0.2f),
                                                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                                                end = androidx.compose.ui.geometry.Offset(width, centerY),
                                                strokeWidth = strokeWidth,
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                                            )

                                            // Draw played line
                                            val playedFraction = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                                            this.drawLine(
                                                color = playerAccentColor,
                                                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                                                end = androidx.compose.ui.geometry.Offset(playedFraction * width, centerY),
                                                strokeWidth = strokeWidth,
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                                            )

                                            // Draw skip intro highlight
                                            val pIntroStart = parseSkipTime(currentIntroStart)
                                            val pIntroEnd = parseSkipTime(currentIntroEnd)
                                            if (duration > 0 && pIntroEnd > pIntroStart && pIntroStart in 0..duration && pIntroEnd in pIntroStart..duration) {
                                                this.drawLine(
                                                    color = playerAccentColor.copy(alpha = 0.4f),
                                                    start = androidx.compose.ui.geometry.Offset((pIntroStart.toFloat() / duration.toFloat()) * width, centerY),
                                                    end = androidx.compose.ui.geometry.Offset((pIntroEnd.toFloat() / duration.toFloat()) * width, centerY),
                                                    strokeWidth = strokeWidth,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                            }

                                            // Draw skip outro highlight
                                            val pOutroStart = parseSkipTime(currentOutroStart)
                                            val pOutroEnd = parseSkipTime(currentOutroEnd)
                                            if (duration > 0 && pOutroEnd > pOutroStart && pOutroStart in 0..duration && pOutroEnd in pOutroStart..duration) {
                                                this.drawLine(
                                                    color = playerAccentColor.copy(alpha = 0.4f),
                                                    start = androidx.compose.ui.geometry.Offset((pOutroStart.toFloat() / duration.toFloat()) * width, centerY),
                                                    end = androidx.compose.ui.geometry.Offset((pOutroEnd.toFloat() / duration.toFloat()) * width, centerY),
                                                    strokeWidth = strokeWidth,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            val remaining = (duration - currentPosition).coerceAtLeast(0L)
                            
                            // Aspect Ratio crop button next to duration text
                            IconButton(
                                onClick = {
                                    resizeMode = (resizeMode + 1) % 3
                                    val modeName = when (resizeMode) {
                                        1 -> "Fill"
                                        2 -> "Zoom"
                                        else -> "Fit"
                                    }
                                    resizeToastText = "Video: $modeName"
                                    showResizeToast = true
                                    playerViewInstance?.resizeMode = when (resizeMode) {
                                        1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_player_015),
                                    contentDescription = "Resize Aspect",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${formatTime(currentPosition)} / -${formatTime(remaining)}",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Bottom icon buttons row (Exact 5 buttons matching the screenshot: Block -> Audio & Subs -> Series -> Quality -> Next Episode)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Block (Lock)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isLocked = true }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_player_019),
                                    contentDescription = "Block",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Block", color = Color.White, fontSize = 11.sp)
                            }

                            // 2. Audio & Subtitles
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showAudioSubtitlesPanel = true }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_player_024),
                                    contentDescription = "Audio & Subtitles",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Audio & Subs", color = Color.White, fontSize = 11.sp)
                            }

                            // 3. Series (Episodes list)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showEpisodesPanel = true }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_player_017),
                                    contentDescription = "Series",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Series", color = Color.White, fontSize = 11.sp)
                            }

                            // 4. Quality
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showSpeedQualityPanel = true }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_player_007),
                                    contentDescription = "Quality",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Quality", color = Color.White, fontSize = 11.sp)
                            }

                            // 5. Next Episode
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (currentEpisodeNum < totalEpisodes) {
                                            switchToEpisode(currentEpisodeNum + 1)
                                        } else {
                                            android.widget.Toast.makeText(context, "No next episode", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_player_005),
                                    contentDescription = "Next Episode",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Next Ep", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ─── Audio & Subtitles Drawer ──────────────────────────────────────
            if (showAudioSubtitlesPanel) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showAudioSubtitlesPanel = false }
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(360.dp)
                        .background(Color(0xFF0F0F0F).copy(alpha = 0.65f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .align(Alignment.CenterEnd)
                        .clickable(enabled = false) {}
                        .padding(24.dp)
                ) {
                    if (showSubtitleStyleSettings) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { showSubtitleStyleSettings = false }
                                    .padding(bottom = 16.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Back to Subtitles", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Row(modifier = Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("FONT SIZE", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                    val sizes = listOf(12f, 16f, 20f)
                                    val sizeLabels = listOf("Small", "Medium", "Large")
                                    sizes.forEachIndexed { idx, size ->
                                        val isSel = subFontSize == size
                                        Text(
                                            text = sizeLabels[idx],
                                            color = if (isSel) Color(0xFFD0BCFF) else Color.White,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp,
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                subFontSize = size
                                                prefs.edit().putFloat("subFontSize", size).apply()
                                            }.padding(vertical = 8.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("TEXT COLOR", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                    val colors = listOf(
                                        android.graphics.Color.WHITE to "White",
                                        android.graphics.Color.YELLOW to "Yellow",
                                        android.graphics.Color.rgb(208, 188, 255) to "Light Purple"
                                    )
                                    colors.forEach { (colorVal, colorLabel) ->
                                        val isSel = subTextColor == colorVal
                                        Text(
                                            text = colorLabel,
                                            color = if (isSel) Color(0xFFD0BCFF) else Color.White,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp,
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                subTextColor = colorVal
                                                prefs.edit().putInt("subTextColor", colorVal).apply()
                                            }.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(24.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("BACKGROUND OPACITY", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                    val opacities = listOf(0 to "None (0%)", 76 to "Light (30%)", 128 to "Medium (50%)", 178 to "Dark (70%)")
                                    opacities.forEach { (opacityVal, opacityLabel) ->
                                        val isSel = subBgOpacity == opacityVal
                                        Text(
                                            text = opacityLabel,
                                            color = if (isSel) Color(0xFFD0BCFF) else Color.White,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp,
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                subBgOpacity = opacityVal
                                                prefs.edit().putInt("subBgOpacity", opacityVal).apply()
                                            }.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Audio Track Column
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                                        Text("AUDIO TRACK", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    val audioTracks = listOf("Japanese (Original)", "English", "Hindi", "Fallback (Sub)")
                                    audioTracks.forEach { track ->
                                        val isSel = track == selectedAudio
                                        Text(
                                            text = track,
                                            color = if (isSel) Color(0xFFD0BCFF) else Color.White,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (track != selectedAudio) {
                                                        selectedAudio = track
                                                        prefs.edit().putString("selectedAudio", track).apply()
                                                        val newType = if (track == "Fallback (Sub)") "fallback" else if (track == "Hindi") "hindi" else if (track == "English") "dub" else "sub"
                                                        currentStreamType = newType
                                                        isReloadingStream = true
                                                        startPosition = player.currentPosition
                                                        coroutineScope.launch {
                                                            val streamInfo = withContext(Dispatchers.IO) {
                                                                fetchStreamInfo(anilistId, currentEpisodeNum, newType)
                                                            }
                                                            isReloadingStream = false
                                                            val hasHls = streamInfo != null && (streamInfo["hls"] as? String)?.isNotEmpty() == true
                                                            if (hasHls) {
                                                                currentHlsUrl = streamInfo!!["hls"] as String
                                                                currentReferer = (streamInfo["referer"] as? String) ?: ""
                                                                currentSubtitleUrl = (streamInfo["subtitle"] as? String) ?: ""
                                                                currentBackupHls = (streamInfo["backupHls"] as? String) ?: ""
                                                                currentBackupProvider = (streamInfo["backupProvider"] as? String) ?: ""
                                                                
                                                                val newIntroStart = (streamInfo["introStart"] as? Long) ?: 0L
                                                                val newIntroEnd = (streamInfo["introEnd"] as? Long) ?: 0L
                                                                val newOutroStart = (streamInfo["outroStart"] as? Long) ?: 0L
                                                                val newOutroEnd = (streamInfo["outroEnd"] as? Long) ?: 0L
                                                                if (newIntroEnd > 0L) {
                                                                    currentIntroStart = newIntroStart
                                                                    currentIntroEnd = newIntroEnd
                                                                }
                                                                if (newOutroEnd > 0L) {
                                                                    currentOutroStart = newOutroStart
                                                                    currentOutroEnd = newOutroEnd
                                                                }

                                                                if (newType == "hindi" && streamInfo.containsKey("hindiStreams")) {
                                                                    @Suppress("UNCHECKED_CAST")
                                                                    val list = streamInfo["hindiStreams"] as? List<Map<String, String>>
                                                                    if (list != null) {
                                                                        hindiStreamsList = list
                                                                        currentHindiIndex = 0
                                                                    }
                                                                }
                                                            } else if (newType == "hindi" && hindiStreamsList.isNotEmpty()) {
                                                                val first = hindiStreamsList.first()
                                                                currentHlsUrl = first["hls"] ?: ""
                                                                currentReferer = first["referer"] ?: ""
                                                                currentHindiIndex = 0
                                                            } else {
                                                                android.widget.Toast.makeText(context, "Audio track not available for this episode", android.widget.Toast.LENGTH_SHORT).show()
                                                                currentStreamType = "sub"
                                                            }
                                                        }
                                                        showAudioSubtitlesPanel = false
                                                    }
                                                }
                                                .padding(vertical = 12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(24.dp))

                                // Subtitles Column
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("SUBTITLES", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                                    val subOptions = mutableListOf("Off", "English (VTT)")
                                    if (currentBackupHls.isNotEmpty()) {
                                        subOptions.add("Hard Sub")
                                    }
                                    subOptions.add("Fallback (Sub)")
                                    subOptions.forEach { sub ->
                                        val isSel = sub == selectedSub || (sub == "Hard Sub" && selectedSub.contains("Hard Sub"))
                                        Text(
                                            text = sub,
                                            color = if (isSel) Color(0xFFD0BCFF) else Color.White,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (sub != selectedSub) {
                                                        val oldSub = selectedSub
                                                        selectedSub = sub
                                                        prefs.edit().putString("selectedSub", sub).apply()
                                                        
                                                        if (sub == "Hard Sub") {
                                                            startPosition = player.currentPosition
                                                            if (currentBackupHls.isNotEmpty()) {
                                                                currentHlsUrl = currentBackupHls
                                                            }
                                                        } else if (sub == "Fallback (Sub)") {
                                                            startPosition = player.currentPosition
                                                            isReloadingStream = true
                                                            coroutineScope.launch {
                                                                val fallbackInfo = withContext(Dispatchers.IO) {
                                                                    fetchAnimeScraperFallback(anilistId, currentEpisodeNum)
                                                                }
                                                                isReloadingStream = false
                                                                if (fallbackInfo != null) {
                                                                    val fbSub = (fallbackInfo["subtitle"] as? String) ?: ""
                                                                    if (fbSub.isNotEmpty()) {
                                                                        currentSubtitleUrl = fbSub
                                                                    }
                                                                }
                                                            }
                                                        } else if (oldSub.contains("Hard Sub")) {
                                                             isReloadingStream = true
                                                             val newType = if (selectedAudio == "Hindi") "hindi" else if (selectedAudio == "English") "dub" else "sub"
                                                             startPosition = player.currentPosition
                                                            coroutineScope.launch {
                                                                val streamInfo = withContext(Dispatchers.IO) {
                                                                    fetchStreamInfo(anilistId, currentEpisodeNum, newType)
                                                                }
                                                                isReloadingStream = false
                                                                if (streamInfo != null) {
                                                                    currentHlsUrl = streamInfo["hls"] as String
                                                                    currentReferer = streamInfo["referer"] as String
                                                                    currentSubtitleUrl = (streamInfo["subtitle"] as? String) ?: ""
                                                                    currentBackupHls = (streamInfo["backupHls"] as? String) ?: ""
                                                                    currentBackupProvider = (streamInfo["backupProvider"] as? String) ?: ""
                                                                }
                                                            }
                                                        }
                                                        android.widget.Toast.makeText(context, "Subtitles: $sub", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .padding(vertical = 12.dp)
                                        )
                                    }
                                }
                            }

                            // Customize Styling row neatly arranged at the bottom
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.15f))
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSubtitleStyleSettings = true }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Customize Styling",
                                    tint = Color(0xFFD0BCFF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Customize Styling",
                                    color = Color(0xFFD0BCFF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // ─── Speed & Quality Drawer ────────────────────────────────────────
            if (showSpeedQualityPanel) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showSpeedQualityPanel = false }
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(360.dp)
                        .background(Color(0xFF0F0F0F).copy(alpha = 0.65f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .align(Alignment.CenterEnd)
                        .clickable(enabled = false) {}
                        .padding(24.dp)
                ) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PLAYBACK SPEED", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                            val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                            val speedLabels = mapOf(0.75f to "0.75x", 1.0f to "1.0x (Normal)", 1.25f to "1.25x", 1.5f to "1.5x", 2.0f to "2.0x")
                            speeds.forEach { speed ->
                                val isSel = currentSpeed == speed
                                Text(
                                    text = speedLabels[speed] ?: "${speed}x",
                                    color = if (isSel) Color(0xFFD0BCFF) else Color.White,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        currentSpeed = speed
                                        player.setPlaybackSpeed(speed)
                                        prefs.edit().putFloat("currentSpeed", speed).apply()
                                        android.widget.Toast.makeText(context, "Playback speed: ${speedLabels[speed]}", android.widget.Toast.LENGTH_SHORT).show()
                                    }.padding(vertical = 12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text("VIDEO QUALITY", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                            val qualityOptions = listOf("Auto", "1080p", "720p", "480p", "360p")
                            qualityOptions.forEach { quality ->
                                val isSel = currentQuality == quality
                                Text(
                                    text = quality,
                                    color = if (isSel) Color(0xFFD0BCFF) else Color.White,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        currentQuality = quality
                                        val (maxW, maxH) = when (quality) {
                                            "1080p" -> Pair(1920, 1080)
                                            "720p" -> Pair(1280, 720)
                                            "480p" -> Pair(854, 480)
                                            "360p" -> Pair(640, 360)
                                            else -> Pair(Int.MAX_VALUE, Int.MAX_VALUE)
                                        }
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon().setMaxVideoSize(maxW, maxH).build()
                                        android.widget.Toast.makeText(context, "Quality: $quality", android.widget.Toast.LENGTH_SHORT).show()
                                    }.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ─── Episodes Panel ────────────────────────────────────────────────
            if (showEpisodesPanel) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showEpisodesPanel = false },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(Color(0xFF0F0F0F).copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .clickable(enabled = false) {}
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Episodes", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { showEpisodesPanel = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        val activeEpisodes = episodeImages.keys.sorted().filter { it !in futureEps }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(activeEpisodes) { ep ->
                                val isCurrent = ep == currentEpisodeNum
                                val defaultImgUrl = episodeImages[ep] ?: ""
                                var imgUrl by remember(ep, defaultImgUrl) { mutableStateOf(defaultImgUrl) }
                                
                                if (anilistId == 21 || anilistId == 235) {
                                    LaunchedEffect(ep) {
                                        val tmdbId = if (anilistId == 21) 37854 else 30983
                                        val meta = episodeMetaMap[ep]
                                        if (meta != null) {
                                            val tmdbImg = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getTmdbEpisodeImage(tmdbId, meta.seasonNumber, meta.episodeNumber)
                                            if (tmdbImg.isNotEmpty()) {
                                                imgUrl = tmdbImg
                                            }
                                        }
                                    }
                                }
                                
                                val epTitle = episodeTitles[ep]
                                Column(
                                    modifier = Modifier
                                        .width(200.dp)
                                        .clickable {
                                            showEpisodesPanel = false
                                            if (ep != currentEpisodeNum) {
                                                switchToEpisode(ep)
                                            }
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(112.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isCurrent) 2.dp else 0.dp,
                                                color = if (isCurrent) Color.White else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .background(Color(0xFF1A1A1A))
                                    ) {
                                        if (!imgUrl.isNullOrEmpty()) {
                                            val painter = rememberAsyncImagePainter(model = imgUrl)
                                            androidx.compose.foundation.Image(
                                                painter = painter,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            val backdropUrl = if (totalEpisodes == 1) showCoverUrl else coverUrl
                                            if (!backdropUrl.isNullOrEmpty()) {
                                                val painter = rememberAsyncImagePainter(model = backdropUrl)
                                                androidx.compose.foundation.Image(
                                                    painter = painter,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                            }
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("EP. $ep", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        // Current episode overlay indicator
                                        if (isCurrent) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(6.dp)
                                                    .background(Color.White, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("Now Playing", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = if (isCurrent) Color.White else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Ep $ep",
                                            color = if (isCurrent) Color.White else Color.LightGray,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    // Episode title
                                    if (!epTitle.isNullOrEmpty()) {
                                        Text(
                                            text = epTitle,
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 2.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }

            // Loading overlay for next episode
            if (isNextEpLoading || isReloadingStream) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFD0BCFF))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isReloadingStream) "Switching Audio Track..." else "Loading Next Episode...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // ─── Resume Dialog (Drawn at the bottom of Main UI Box so it remains on top of everything) ───
            if (showResumeDialog && savedProgress > 0L) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .clickable(enabled = false) {}, // Intercept clicks
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Resume Playback", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Continue from ${formatTime(savedProgress)}?",
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            TextButton(onClick = {
                                showResumeDialog = false
                                player.seekTo(0L)
                                player.play()
                            }) {
                                Text("Start Over", color = Color.Gray)
                            }
                            Button(
                                onClick = {
                                    showResumeDialog = false
                                    player.seekTo(savedProgress)
                                    player.play()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF))
                            ) {
                                Text("Resume", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    }

    @OptIn(UnstableApi::class)
    private fun buildPlayer(
        context: android.content.Context,
        hlsUrl: String,
        referer: String,
        subtitleUrl: String,
        streamType: String = "sub",
        startPositionMs: Long = 0L,
        playImmediately: Boolean = true
    ): ExoPlayer {
        val cleanRef = if (referer.contains("?")) referer.substring(0, referer.indexOf("?")) else referer
        val refererHost = try {
            val uri = Uri.parse(hlsUrl)
            "${uri.scheme}://${uri.host}/"
        } catch (e: Exception) { cleanRef }
        val originHeader = try {
            val refUri = if (cleanRef.isNotEmpty()) Uri.parse(cleanRef) else Uri.parse(hlsUrl)
            "${refUri.scheme}://${refUri.host}"
        } catch (e: Exception) { "" }

        val finalReferer = if (cleanRef.isNotEmpty()) cleanRef else refererHost
        val requestProperties = mutableMapOf("Referer" to finalReferer)
        if (originHeader.isNotEmpty()) requestProperties["Origin"] = originHeader

        val sslContext = try {
            javax.net.ssl.SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                }), java.security.SecureRandom())
            }
        } catch (e: Exception) { null }

        val okHttpClientBuilder = okhttp3.OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)

        if (sslContext != null) {
            okHttpClientBuilder.sslSocketFactory(sslContext.socketFactory, object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            })
            okHttpClientBuilder.hostnameVerifier { _, _ -> true }
        }

        val okHttpClient = okHttpClientBuilder.build()

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(requestProperties)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val subtitleConfig = if (subtitleUrl.isNotEmpty()) {
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("en")
                .setLabel("English")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                .build()
        } else null

        val isMp4 = hlsUrl.contains(".mp4", ignoreCase = true)
        val mimeType = if (isMp4) MimeTypes.VIDEO_MP4 else MimeTypes.APPLICATION_M3U8

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(hlsUrl)
            .setMimeType(mimeType)
        if (subtitleConfig != null) {
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }
        val mediaItem = mediaItemBuilder.build()

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

        // Aggressive buffer tuning for instant HLS start (1.5s initial buffer instead of 10s wait)
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                                        1500, // minBufferMs
                50000, // maxBufferMs
                1000, // bufferForPlaybackMs
                1500  // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                setMediaSource(mediaSource)
                val preferredLang = if (streamType == "hindi") "hi" else if (streamType == "dub") "en" else "ja"
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setPreferredAudioLanguage(preferredLang)
                    .setPreferredTextLanguage("en")
                    .setSelectUndeterminedTextLanguage(true)
                    .setIgnoredTextSelectionFlags(0)
                    .build()
                prepare()
                seekTo(startPositionMs.coerceAtLeast(0L))
                playWhenReady = true
            }
    }

    private fun extractSubFromUrl(url: String): String {
        if (url.contains("sub=")) {
            val idx = url.indexOf("sub=")
            val subPart = url.substring(idx + 4)
            val endIdx = subPart.indexOf("&")
            val raw = if (endIdx != -1) subPart.substring(0, endIdx) else subPart
            return try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { raw }
        }
        return ""
    }

    private suspend fun fetchAnimeScraperFallback(anilistId: Int, episodeNum: Int, type: String = "sub"): Map<String, Any?>? {
        return withContext(Dispatchers.IO) {
            val sType = if (type == "dub") "dub" else "sub"
            val urls = listOf(
                "${com.lagradost.cloudstream3.BuildConfig.VERCEL_SCRAPER_API}/default/$anilistId/$sType/$episodeNum",
                "${com.lagradost.cloudstream3.BuildConfig.VERCEL_MULTIMOVIE_API}/api/anime?anilistId=$anilistId&episode=$episodeNum&type=$type"
            )
            for (url in urls) {
                try {
                    val req = okhttp3.Request.Builder().url(url).build()
                    okhttp3.OkHttpClient().newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val jsonStr = resp.body?.string() ?: ""
                            if (jsonStr.isNotEmpty()) {
                                val json = JSONObject(jsonStr)
                                val map = parseStreamInfoFromJson(json, sType)?.toMutableMap()
                                if (map != null && (map["hls"] as? String)?.isNotEmpty() == true) {
                                    val iStart = (map["introStart"] as? Long) ?: 0L
                                    val iEnd   = (map["introEnd"] as? Long) ?: 0L
                                    if (iStart == 0L && iEnd == 0L) {
                                        try {
                                            val subUrl = "${com.lagradost.cloudstream3.BuildConfig.VERCEL_MULTIMOVIE_API}/api/anime?anilistId=$anilistId&episode=$episodeNum&type=sub"
                                            val subReq = okhttp3.Request.Builder().url(subUrl).build()
                                            okhttp3.OkHttpClient().newCall(subReq).execute().use { subRes ->
                                                if (subRes.isSuccessful) {
                                                    val subJson = JSONObject(subRes.body?.string() ?: "")
                                                    val subInfo = parseStreamInfoFromJson(subJson, "sub")
                                                    if (subInfo != null) {
                                                        val pIntroStart = (subInfo["introStart"] as? Long) ?: 0L
                                                        val pIntroEnd   = (subInfo["introEnd"] as? Long) ?: 0L
                                                        val pOutroStart = (subInfo["outroStart"] as? Long) ?: 0L
                                                        val pOutroEnd   = (subInfo["outroEnd"] as? Long) ?: 0L
                                                        if (pIntroEnd > 0L) {
                                                            map["introStart"] = pIntroStart
                                                            map["introEnd"]   = pIntroEnd
                                                        }
                                                        if (pOutroEnd > 0L) {
                                                            map["outroStart"] = pOutroStart
                                                            map["outroEnd"]   = pOutroEnd
                                                        }
                                                        val subSubtitle = (subInfo["subtitle"] as? String) ?: ""
                                                        if ((map["subtitle"] as? String).isNullOrEmpty() && subSubtitle.isNotEmpty()) {
                                                            map["subtitle"] = subSubtitle
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {}
                                    }
                                    return@withContext map
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            null
        }
    }

    private suspend fun fetchStreamInfo(anilistId: Int, episodeNum: Int, type: String): Map<String, Any?>? {
        return withContext(Dispatchers.IO) {
            if (type == "fallback") {
                return@withContext fetchAnimeScraperFallback(anilistId, episodeNum)
            }
            if (type == "hindi") {
                // 1. Query fast anidrive API first
                try {
                    val aniDriveUrl = "https://anidrive-stream-finder.lovable.app/api/public/stream?anilist=$anilistId&ep=$episodeNum"
                    val req = okhttp3.Request.Builder().url(aniDriveUrl).build()
                    okhttp3.OkHttpClient().newCall(req).execute().use { resp ->
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
                                            val subUrl = "${com.lagradost.cloudstream3.BuildConfig.VERCEL_MULTIMOVIE_API}/api/anime?anilistId=$anilistId&episode=$episodeNum&type=sub"
                                            val subReq = okhttp3.Request.Builder().url(subUrl).build()
                                            okhttp3.OkHttpClient().newCall(subReq).execute().use { subRes ->
                                                if (subRes.isSuccessful) {
                                                    val subJson = JSONObject(subRes.body?.string() ?: "")
                                                    val subInfo = parseStreamInfoFromJson(subJson)
                                                    if (subInfo != null) {
                                                        subtitleUrl = (subInfo["subtitle"] as? String) ?: ""
                                                        introStart = (subInfo["introStart"] as? Long) ?: 0L
                                                        introEnd = (subInfo["introEnd"] as? Long) ?: 0L
                                                        outroStart = (subInfo["outroStart"] as? Long) ?: 0L
                                                        outroEnd = (subInfo["outroEnd"] as? Long) ?: 0L
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {}

                                        val resMap = mutableMapOf<String, Any?>(
                                            "hls" to mainUrl,
                                            "referer" to "",
                                            "subtitle" to subtitleUrl,
                                            "introStart" to introStart,
                                            "introEnd" to introEnd,
                                            "outroStart" to outroStart,
                                            "outroEnd" to outroEnd
                                        )
                                        if (qualitiesList.isNotEmpty()) {
                                            resMap["hindiStreams"] = qualitiesList
                                            resMap["hindiQualities"] = qualitiesList
                                        }
                                        return@withContext resMap
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }

                // 2. Fallback to multimovieapi Hindi endpoint if anidrive fails
                val hindiUrl = "${com.lagradost.cloudstream3.BuildConfig.VERCEL_MULTIMOVIE_API}/api/hindi?anilistId=$anilistId&episode=$episodeNum"
                val hindiReq = okhttp3.Request.Builder().url(hindiUrl).build()
                val hindiList = mutableListOf<Map<String, String>>()
                try {
                    okhttp3.OkHttpClient().newCall(hindiReq).execute().use { response ->
                        if (response.isSuccessful) {
                            val json = JSONObject(response.body?.string() ?: "")
                            if (json.has("streams")) {
                                val streams = json.getJSONArray("streams")
                                for (i in 0 until streams.length()) {
                                    val s = streams.getJSONObject(i)
                                    val provider = s.optString("provider", s.optString("name", s.optString("server", "")))
                                    if (s.has("hls") && !s.isNull("hls")) {
                                        val hls = s.getString("hls")
                                        val headers = s.optJSONObject("headers")
                                        var referer = if (headers != null) {
                                            val r1 = headers.optString("Referer", "")
                                            val r2 = headers.optString("referer", "")
                                            val r3 = headers.optString("Origin", "")
                                            if (r1.isNotEmpty()) r1 else if (r2.isNotEmpty()) r2 else r3
                                        } else ""
                                        if (referer.isEmpty() || referer.contains("premilkyway") || referer.contains("m3u8")) {
                                            val embedUrl = s.optString("url", s.optString("link", ""))
                                            if (embedUrl.isNotEmpty()) {
                                                referer = embedUrl
                                            } else if (hls.contains("premilkyway")) {
                                                referer = "https://hanerix.com/"
                                            }
                                        }
                                        hindiList.add(mapOf("hls" to hls, "referer" to referer, "provider" to provider))
                                    }
                                }
                                hindiList.sortBy { item ->
                                    val prov = (item["provider"] ?: "").lowercase()
                                    when {
                                        prov.contains("rpmshare") || prov.contains("rpm") -> 0
                                        prov.contains("upnshare") || prov.contains("upn") -> 1
                                        else -> 2
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }

                var subtitleUrl = ""
                var introStart = 0L; var introEnd = 0L
                var outroStart = 0L; var outroEnd = 0L
                val subUrl = "${com.lagradost.cloudstream3.BuildConfig.VERCEL_MULTIMOVIE_API}/api/anime?anilistId=$anilistId&episode=$episodeNum&type=sub"
                val subReq = okhttp3.Request.Builder().url(subUrl).build()
                try {
                    okhttp3.OkHttpClient().newCall(subReq).execute().use { subRes ->
                        if (subRes.isSuccessful) {
                            val subJson = JSONObject(subRes.body?.string() ?: "")
                            val subInfo = parseStreamInfoFromJson(subJson)
                            if (subInfo != null) {
                                subtitleUrl = (subInfo["subtitle"] as? String) ?: ""
                                introStart = (subInfo["introStart"] as? Long) ?: 0L
                                introEnd = (subInfo["introEnd"] as? Long) ?: 0L
                                outroStart = (subInfo["outroStart"] as? Long) ?: 0L
                                outroEnd = (subInfo["outroEnd"] as? Long) ?: 0L
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }

                if (hindiList.isNotEmpty()) {
                    val first = hindiList.first()
                    val result = mutableMapOf<String, Any?>(
                        "hls" to first["hls"],
                        "referer" to first["referer"],
                        "subtitle" to subtitleUrl,
                        "introStart" to introStart,
                        "introEnd" to introEnd,
                        "outroStart" to outroStart,
                        "outroEnd" to outroEnd,
                        "hindiStreams" to hindiList
                    )
                    result
                } else null
            } else {
                val url = "${com.lagradost.cloudstream3.BuildConfig.VERCEL_MULTIMOVIE_API}/api/anime?anilistId=$anilistId&episode=$episodeNum&type=$type"
                val request = okhttp3.Request.Builder().url(url).build()
                try {
                    val result = okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val json = JSONObject(response.body?.string() ?: "")
                            val map = parseStreamInfoFromJson(json, type)?.toMutableMap()
                            
                            // If type is dub and no subtitle found, fetch sub to get subtitles
                            if (type == "dub" && (map?.get("subtitle") as? String).isNullOrEmpty()) {
                                val subUrl = "${com.lagradost.cloudstream3.BuildConfig.VERCEL_MULTIMOVIE_API}/api/anime?anilistId=$anilistId&episode=$episodeNum&type=sub"
                                val subReq = okhttp3.Request.Builder().url(subUrl).build()
                                okhttp3.OkHttpClient().newCall(subReq).execute().use { subRes ->
                                    if (subRes.isSuccessful) {
                                        val subJson = JSONObject(subRes.body?.string() ?: "")
                                        val subInfo = parseStreamInfoFromJson(subJson)
                                        val subSubtitle = subInfo?.get("subtitle") as? String
                                        if (!subSubtitle.isNullOrEmpty()) {
                                            map?.put("subtitle", subSubtitle)
                                        }
                                    }
                                }
                            }
                            map
                        } else null
                    }
                    if (result != null && (result["hls"] as? String)?.isNotEmpty() == true) {
                        result
                    } else {
                        fetchAnimeScraperFallback(anilistId, episodeNum, type)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    fetchAnimeScraperFallback(anilistId, episodeNum, type)
                }
            }
        }
    }

    private fun parseStreamInfoFromJson(json: JSONObject, sType: String = "sub"): Map<String, Any?>? {
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
                            val uri = Uri.parse(u)
                            "${uri.scheme}://${uri.host}/"
                        } catch (e: Exception) { "" }
                        val server = s.optString("server", s.optString("name", s.optString("provider", ""))).lowercase()
                        val isDefault = s.optBoolean("default", false)

                        val subCandidate = extractSubFromUrl(embed).ifEmpty { extractSubFromUrl(rawRef) }
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
            return mapOf(
                "hls" to hlsUrl,
                "referer" to referer,
                "subtitle" to subtitleUrl,
                "introStart" to introStart,
                "introEnd" to introEnd,
                "outroStart" to outroStart,
                "outroEnd" to outroEnd,
                "backupHls" to "",
                "backupProvider" to "",
                "hlsStreams" to hlsList
            )
        }

        return if (json.has("primary")) {
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

            // Parse chapters array for intro/outro
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
    }

    private fun playPreviousEpisode(
        context: android.content.Context,
        anilistId: Int,
        prevEpNum: Int,
        animeTitle: String,
        streamType: String,
        showCoverUrl: String,
        totalEpisodes: Int,
        activeAudio: String = "Japanese (Original)",
        activeSub: String = "English (VTT)",
        onLoadingStateChanged: (Boolean) -> Unit
    ) {
        onLoadingStateChanged(true)
        val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        coroutineScope.launch {
            val json = withContext(Dispatchers.IO) {
                val url = "${com.lagradost.cloudstream3.BuildConfig.VERCEL_MULTIMOVIE_API}/api/anime?anilistId=$anilistId&episode=$prevEpNum&type=$streamType"
                val request = okhttp3.Request.Builder().url(url).build()
                try {
                    okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                        if (response.isSuccessful) JSONObject(response.body?.string() ?: "") else null
                    }
                } catch (e: Exception) { e.printStackTrace(); null }
            }

            val streamInfo = json?.let { parseStreamInfoFromJson(it) }

            onLoadingStateChanged(false)
            if (streamInfo != null) {
                val prevEpCover = withContext(Dispatchers.IO) {
                    if (anilistId == 21 || anilistId == 235) {
                        val tmdbId = if (anilistId == 21) 37854 else 30983
                        val allEps = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getTmdbAllEpisodes(tmdbId)
                        val prevMeta = allEps[prevEpNum]
                        prevMeta?.imageUrl ?: showCoverUrl
                    } else {
                        try {
                            val mappingUrl = "https://api.ani.zip/mappings?anilist_id=$anilistId"
                            val request = okhttp3.Request.Builder().url(mappingUrl).build()
                            okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val j = JSONObject(response.body?.string() ?: "")
                                    if (j.has("episodes")) {
                                        val episodes = j.getJSONObject("episodes")
                                        if (episodes.has(prevEpNum.toString())) {
                                            episodes.getJSONObject(prevEpNum.toString()).optString("image", showCoverUrl)
                                        } else showCoverUrl
                                    } else showCoverUrl
                                } else showCoverUrl
                            }
                        } catch (e: Exception) { showCoverUrl }
                    }
                }

                val intent = Intent(context, AnimeBoxPlayerActivity::class.java).apply {
                    putExtra("hlsUrl", streamInfo["hls"] as String)
                    putExtra("referer", streamInfo["referer"] as String)
                    putExtra("subtitleUrl", (streamInfo["subtitle"] as? String) ?: "")
                    putExtra("introStart", streamInfo["introStart"] as Long)
                    putExtra("introEnd", streamInfo["introEnd"] as Long)
                    putExtra("outroStart", streamInfo["outroStart"] as Long)
                    putExtra("outroEnd", streamInfo["outroEnd"] as Long)
                    putExtra("anilistId", anilistId)
                    putExtra("episode", prevEpNum)
                    putExtra("animeTitle", animeTitle)
                    putExtra("coverUrl", prevEpCover)
                    putExtra("showCoverUrl", showCoverUrl)
                    putExtra("totalEpisodes", totalEpisodes)
                    putExtra("streamType", streamType)
                    putExtra("fromContinueWatching", false)
                    putExtra("activeAudio", activeAudio)
                    putExtra("activeSub", activeSub)
                    if (streamInfo.containsKey("backupHls")) {
                        putExtra("backupHls", streamInfo["backupHls"] as? String)
                        putExtra("backupProvider", streamInfo["backupProvider"] as? String)
                    }
                }
                context.startActivity(intent)
                (context as? android.app.Activity)?.finish()
            } else {
                android.widget.Toast.makeText(context, "Failed to load previous episode stream", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playNextEpisode(
        context: android.content.Context,
        anilistId: Int,
        nextEpNum: Int,
        animeTitle: String,
        streamType: String,
        showCoverUrl: String,
        totalEpisodes: Int,
        activeAudio: String = "Japanese (Original)",
        activeSub: String = "English (VTT)",
        onLoadingStateChanged: (Boolean) -> Unit
    ) {
        onLoadingStateChanged(true)
        val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        coroutineScope.launch {
            val json = withContext(Dispatchers.IO) {
                val url = "${com.lagradost.cloudstream3.BuildConfig.VERCEL_MULTIMOVIE_API}/api/anime?anilistId=$anilistId&episode=$nextEpNum&type=$streamType"
                val request = okhttp3.Request.Builder().url(url).build()
                try {
                    okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                        if (response.isSuccessful) JSONObject(response.body?.string() ?: "") else null
                    }
                } catch (e: Exception) { e.printStackTrace(); null }
            }

            val streamInfo = json?.let { parseStreamInfoFromJson(it) }

            onLoadingStateChanged(false)
            if (streamInfo != null) {
                val nextEpCover = withContext(Dispatchers.IO) {
                    if (anilistId == 21 || anilistId == 235) {
                        val tmdbId = if (anilistId == 21) 37854 else 30983
                        val allEps = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getTmdbAllEpisodes(tmdbId)
                        val nextMeta = allEps[nextEpNum]
                        nextMeta?.imageUrl ?: showCoverUrl
                    } else {
                        try {
                            val mappingUrl = "https://api.ani.zip/mappings?anilist_id=$anilistId"
                            val request = okhttp3.Request.Builder().url(mappingUrl).build()
                            okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val j = JSONObject(response.body?.string() ?: "")
                                    if (j.has("episodes")) {
                                        val episodes = j.getJSONObject("episodes")
                                        if (episodes.has(nextEpNum.toString())) {
                                            episodes.getJSONObject(nextEpNum.toString()).optString("image", showCoverUrl)
                                        } else showCoverUrl
                                    } else showCoverUrl
                                } else showCoverUrl
                            }
                        } catch (e: Exception) { showCoverUrl }
                    }
                }

                val intent = Intent(context, AnimeBoxPlayerActivity::class.java).apply {
                    putExtra("hlsUrl", streamInfo["hls"] as String)
                    putExtra("referer", streamInfo["referer"] as String)
                    putExtra("subtitleUrl", (streamInfo["subtitle"] as? String) ?: "")
                    putExtra("introStart", streamInfo["introStart"] as Long)
                    putExtra("introEnd", streamInfo["introEnd"] as Long)
                    putExtra("outroStart", streamInfo["outroStart"] as Long)
                    putExtra("outroEnd", streamInfo["outroEnd"] as Long)
                    putExtra("anilistId", anilistId)
                    putExtra("episode", nextEpNum)
                    putExtra("animeTitle", animeTitle)
                    putExtra("coverUrl", nextEpCover)
                    putExtra("showCoverUrl", showCoverUrl)
                    putExtra("totalEpisodes", totalEpisodes)
                    putExtra("streamType", streamType)
                    putExtra("fromContinueWatching", false)
                    putExtra("activeAudio", activeAudio)
                    putExtra("activeSub", activeSub)
                }
                context.startActivity(intent)
                (context as? android.app.Activity)?.finish()
            } else {
                android.widget.Toast.makeText(context, "Failed to load episode stream", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private var activePlayerForPip: ExoPlayer? = null

    private val pipReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.lagradost.cloudstream3.PIP_CONTROL") {
                val controlType = intent.getIntExtra("control_type", 0)
                activePlayerForPip?.let { player ->
                    when (controlType) {
                        1 -> { // Rewind 10s
                            val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
                            player.seekTo(target)
                        }
                        2 -> { // Play/Pause
                            if (player.isPlaying) player.pause() else player.play()
                            updatePipParams(player.isPlaying)
                        }
                        3 -> { // Forward 10s
                            val target = (player.currentPosition + 10000L).coerceAtMost(player.duration.coerceAtLeast(0L))
                            player.seekTo(target)
                        }
                    }
                }
            }
        }
    }

    private fun updatePipParams(isPlaying: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val params = android.app.PictureInPictureParams.Builder()
                    .setActions(emptyList())
                    .build()
                setPictureInPictureParams(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val params = android.app.PictureInPictureParams.Builder()
                    .setActions(emptyList())
                    .build()
                setPictureInPictureParams(params)
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isCurrentlyInPip = isInPictureInPictureMode
        onPipModeChanged?.invoke(isInPictureInPictureMode)
    }

    override fun onStop() {
        super.onStop()
        if (isCurrentlyInPip || isFinishing) {
            try {
                exoPlayer?.pause()
                exoPlayer?.stop()
                exoPlayer?.release()
                exoPlayer = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (!isFinishing) {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isCurrentlyInPip = false
        try {
            exoPlayer?.pause()
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            unregisterReceiver(pipReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        var onPipModeChanged: ((Boolean) -> Unit)? = null
        var isCurrentlyInPip: Boolean = false
    }
}
