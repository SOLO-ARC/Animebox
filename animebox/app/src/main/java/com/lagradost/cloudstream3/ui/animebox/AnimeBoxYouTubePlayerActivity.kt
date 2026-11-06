@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lagradost.cloudstream3.ui.animebox

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.lagradost.cloudstream3.ui.animebox.components.ThemeDotCircularProgressIndicator
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.rememberAsyncImagePainter
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.animebox.api.AniZipClient
import com.lagradost.cloudstream3.ui.animebox.download.AnimeDownloadManager
import com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine
import com.lagradost.cloudstream3.ui.animebox.history.WatchHistoryManager
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings
import com.lagradost.cloudstream3.ui.animebox.settings.ServerOption
import com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AnimeBoxYouTubePlayerActivity : ComponentActivity() {

    companion object {
        var isCurrentlyInPip: Boolean = false
        var activePlayerForPip: ExoPlayer? = null
    }

    private var exoPlayer: ExoPlayer? = null
    private var isPipMode: Boolean = false
    var isPipState by mutableStateOf(false)

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val rational = Rational(16, 9)
                val pipParams = PictureInPictureParams.Builder()
                    .setAspectRatio(rational)
                    .build()
                enterPictureInPictureMode(pipParams)
            } catch (_: Exception) {
                try {
                    @Suppress("DEPRECATION")
                    enterPictureInPictureMode()
                } catch (_: Exception) {}
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            @Suppress("DEPRECATION")
            try { enterPictureInPictureMode() } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hardware acceleration & Fullscreen immersive setup for 0-lag 60/120fps video rendering
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isPipMode) {
                    try {
                        val rational = Rational(16, 9)
                        val pipParams = PictureInPictureParams.Builder()
                            .setAspectRatio(rational)
                            .build()
                        enterPictureInPictureMode(pipParams)
                        return
                    } catch (_: Exception) {
                    }
                }
                finish()
            }
        })

        setContent {
            YouTubePlayerContent()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && exoPlayer?.isPlaying == true && !isPipMode) {
            try {
                val rational = Rational(16, 9)
                val pipParams = PictureInPictureParams.Builder()
                    .setAspectRatio(rational)
                    .build()
                enterPictureInPictureMode(pipParams)
            } catch (_: Exception) {
            }
        }
    }

    private var wasInPip: Boolean = false

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode = isInPictureInPictureMode
        isCurrentlyInPip = isInPictureInPictureMode
        isPipState = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            wasInPip = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isPipMode) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onStop() {
        super.onStop()
        val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isInPictureInPictureMode
        } else false

        if (wasInPip || (isCurrentlyInPip && !inPip)) {
            // When PiP is dismissed / closed by the user (cross button, swipe away, dismiss), Android calls onStop().
            // Stop and release playback completely and finish task so the video player stops playing immediately!
            wasInPip = false
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
                finishAndRemoveTask()
            } catch (_: Exception) {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wasInPip = false
        isCurrentlyInPip = false
        activePlayerForPip = null
        try {
            exoPlayer?.pause()
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
        } catch (_: Exception) {}
    }

    @Composable
    private fun YouTubePlayerContent() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        // Extract Intent Parameters
        val initialHlsUrl = intent.getStringExtra("hlsUrl") ?: ""
        val initialReferer = intent.getStringExtra("referer") ?: ""
        val initialSubtitleUrl = intent.getStringExtra("subtitleUrl") ?: ""
        val initialSubtitlesJson = intent.getStringExtra("subtitlesJson") ?: "[]"
        val anilistId = intent.getIntExtra("anilistId", 0)
        val initialEpisode = intent.getIntExtra("episode", 1)
        val animeTitle = intent.getStringExtra("animeTitle") ?: "Anime Video"
        val coverUrl = intent.getStringExtra("coverUrl") ?: ""
        val showCoverUrl = intent.getStringExtra("showCoverUrl")?.takeIf { it.isNotBlank() } ?: coverUrl
        val totalEpisodesIntent = intent.getIntExtra("totalEpisodes", 0)
        val initialStreamType = intent.getStringExtra("streamType") ?: "sub"
        val isAdultAnime = intent.getBooleanExtra("isAdult", false) ||
            initialStreamType.contains("hentai", ignoreCase = true) ||
            (anilistId > 0 && com.lagradost.cloudstream3.ui.animebox.api.AniListClient.isAdultAnimeCached(anilistId))
        val effectiveInitialStreamType = if (isAdultAnime) "hentai-jp" else initialStreamType
        val initialAudio = intent.getStringExtra("activeAudio") ?: "Japanese (Original)"
        val localFilePathExtra = intent.getStringExtra("localFilePath") ?: ""
        val isOfflineDownload = intent.getBooleanExtra("isOffline", false)

        // Stream and Audio/Server states
        var currentHlsUrl by remember { mutableStateOf(initialHlsUrl) }
        var currentReferer by remember { mutableStateOf(initialReferer) }
        var currentEpisodeNum by remember { mutableIntStateOf(initialEpisode) }
        var currentSubtitleUrl by remember { mutableStateOf(initialSubtitleUrl) }
        var currentSubtitlesJson by remember { mutableStateOf(initialSubtitlesJson) }
        var currentStreamType by remember { mutableStateOf(effectiveInitialStreamType) }
        var selectedAudio by remember { mutableStateOf(initialAudio) }
        val isLocalFile by remember { mutableStateOf(localFilePathExtra.isNotBlank() && File(localFilePathExtra).exists()) }
        val localFilePath by remember { mutableStateOf(localFilePathExtra) }

        // Server options from ServerPriorityManager
        var availableServers by remember { mutableStateOf<List<ServerOption>>(emptyList()) }
        var selectedServerName by remember { mutableStateOf("Default Server") }

        // Dynamic Episodes state
        var episodesList by remember { mutableStateOf<List<YouTubeEpisodeItem>>(emptyList()) }
        var totalEpisodes by remember { mutableIntStateOf(totalEpisodesIntent) }
        var isEpisodesLoading by remember { mutableStateOf(false) }
        var isNextEpLoading by remember { mutableStateOf(false) }
        var isReloadingStream by remember { mutableStateOf(false) }
        var reloadingMessage by remember { mutableStateOf("Loading Next Episode...") }

        // Playback state
        var isPlaying by remember { mutableStateOf(true) }
        var currentPosition by remember { mutableLongStateOf(0L) }
        var duration by remember { mutableLongStateOf(1L) }
        var bufferedPosition by remember { mutableLongStateOf(0L) }
        var isBuffering by remember { mutableStateOf(true) }
        var resizeMode by remember { mutableIntStateOf(0) } // 0: Fit, 1: Fill, 2: Zoom, 3: 16:9

        // Controls visibility & locking
        var controlsVisible by remember { mutableStateOf(true) }
        var isLocked by remember { mutableStateOf(false) }
        var lastUserInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

        // Gestures (Brightness, Volume, 2x Hold, Double-tap seek)
        val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
        val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
        var currentVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
        var volumeProgress by remember { mutableFloatStateOf((currentVolume.toFloat() / maxVolume).coerceIn(0f, 1f)) }
        var currentBrightness by remember {
            val cur = (context as? Activity)?.window?.attributes?.screenBrightness ?: 0.5f
            mutableFloatStateOf(if (cur < 0) 0.5f else cur)
        }
        var showVolumeIndicator by remember { mutableStateOf(false) }
        var showBrightnessIndicator by remember { mutableStateOf(false) }
        var isHolding2x by remember { mutableStateOf(false) }
        var doubleTapSeekFeedback by remember { mutableStateOf<String?>(null) } // "+10s" or "-10s"

        // Drawers & Sheets
        var showEpisodesDrawer by remember { mutableStateOf(false) }
        var showSettingsSheet by remember { mutableStateOf(false) }
        var showDownloadSheet by remember { mutableStateOf(false) }
        var currentSettingsTab by remember { mutableStateOf<SettingsTab?>(null) }
        var fallbackDialogData by remember { mutableStateOf<YouTubeFallbackDialogData?>(null) }

        // Subtitle appearance preferences (loaded from AnimeBoxPlayerPrefs, matching Player 1 & My Space)
        val subPrefs = remember { context.getSharedPreferences("AnimeBoxPlayerPrefs", Context.MODE_PRIVATE) }
        var subPreset by remember { mutableStateOf(subPrefs.getString("subPreset", "Default") ?: "Default") }
        val isHardsub = subPreset.equals("Hardsub", ignoreCase = true)
        var subFontSize by remember { mutableFloatStateOf(subPrefs.getFloat("subFontSize", 20f)) }
        var subTextColor by remember { mutableIntStateOf(subPrefs.getInt("subTextColor", android.graphics.Color.WHITE)) }
        var subBgOpacity by remember { mutableIntStateOf(subPrefs.getInt("subBgOpacity", 0)) }
        var subEdgeType by remember { mutableIntStateOf(subPrefs.getInt("subEdgeType", androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE)) }
        var subOutlineWidth by remember { mutableFloatStateOf(subPrefs.getFloat("subOutlineWidth", 12f)) }
        var subFontFamily by remember { mutableStateOf(subPrefs.getString("subFontFamily", "Default (Google Sans)") ?: "Default (Google Sans)") }
        val subVerticalOffsetDp = remember { subPrefs.getInt("subVerticalOffset", 0) }
        val subTypeface = remember(subFontFamily) {
            com.lagradost.cloudstream3.ui.animebox.subtitles.SubtitleFontManager.getTypeface(context, subFontFamily)
        }

        // Subtitle instance and parsed cue state for 100% reliable rendering
        var playerViewInstance by remember { mutableStateOf<PlayerView?>(null) }
        var liveSubtitleCues by remember { mutableStateOf<List<com.lagradost.cloudstream3.ui.animebox.api.LiveSubtitleCue>>(emptyList()) }
        var activeSubtitleText by remember { mutableStateOf("") }
        var translatedSubMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var publicSubtitlesList by remember { mutableStateOf<List<com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitleItem>>(emptyList()) }

        // Groq AI Transcription state
        var isGroqTranscribing by remember { mutableStateOf(false) }
        var groqProgressText by remember { mutableStateOf("") }
        var groqErrorMessage by remember { mutableStateOf<String?>(null) }
        var showGroqErrorDialog by remember { mutableStateOf(false) }
        var showGroqApiKeyDialog by remember { mutableStateOf(false) }

        // Subtitle initial resolution matching Player 1
        val rememberedSubMode = remember {
            if (AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context)) {
                AnimeBoxSettings.getLastUsedSubMode(context)
            } else {
                "English (VTT)"
            }
        }
        val isNonJapSubOff = remember { AnimeBoxSettings.isNonJapaneseSubOff(context) }
        val isNonJapaneseAudio = currentStreamType == "dub" || (!selectedAudio.contains("Japanese", ignoreCase = true) && !selectedAudio.contains("Jap", ignoreCase = true))

        val effectiveInitialSub = remember {
            when {
                isNonJapSubOff && isNonJapaneseAudio -> "Off"
                initialSubtitleUrl.isNotBlank() -> "English"
                rememberedSubMode.isNotBlank() && !rememberedSubMode.equals("Off", ignoreCase = true) -> rememberedSubMode
                else -> "English"
            }
        }

        var selectedSubtitleLang by remember { mutableStateOf(effectiveInitialSub) }
        var isCcEnabled by remember { mutableStateOf(effectiveInitialSub != "Off" && (currentSubtitleUrl.isNotBlank() || currentSubtitlesJson.length > 2)) }

        // Graphic Enhancer / Anime 4K
        val initialEnhancerPresetId = remember { AnimeBoxSettings.getDefaultGraphicsEnhancerPreset(context) }
        var isEnhancerEnabled by remember { mutableStateOf(initialEnhancerPresetId != "off") }
        var selectedEnhancerPreset by remember {
            mutableStateOf(
                com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.fromId(initialEnhancerPresetId)
            )
        }

        // Audio Enhancer (loaded from AnimeAudioEnhancer prefs, matching Player 1)
        val audioEnhancerPrefs = remember { com.lagradost.cloudstream3.ui.animebox.enhancer.AnimeAudioEnhancer.getPrefs(context) }
        var isAudioEnhancerEnabled by remember {
            mutableStateOf(audioEnhancerPrefs.getBoolean(com.lagradost.cloudstream3.ui.animebox.enhancer.AnimeAudioEnhancer.KEY_ENABLED, false))
        }
        var selectedAudioPreset by remember {
            mutableStateOf(
                com.lagradost.cloudstream3.ui.animebox.enhancer.AudioEnhancerPreset.fromId(
                    audioEnhancerPrefs.getString(
                        com.lagradost.cloudstream3.ui.animebox.enhancer.AnimeAudioEnhancer.KEY_PRESET_ID,
                        com.lagradost.cloudstream3.ui.animebox.enhancer.AudioEnhancerPreset.STUDIO_BALANCED.id
                    ) ?: com.lagradost.cloudstream3.ui.animebox.enhancer.AudioEnhancerPreset.STUDIO_BALANCED.id
                )
            )
        }

        // Auto ML Kit Translation
        var autoMlKitLang by remember {
            mutableStateOf(AnimeBoxSettings.getAutomaticMlKitTranslationLanguage(context))
        }
        var isTranslatingMlKit by remember { mutableStateOf(false) }
        var liveTranslatingStatus by remember { mutableStateOf<String?>(null) }

        // Settings & Features
        var autoplayNext by remember {
            mutableStateOf(AnimeBoxSettings.isAutoplayNextEpisodeEnabled(context))
        }
        var autoSkipIntro by remember {
            mutableStateOf(AnimeBoxSettings.isSkipIntroEnabled(context))
        }
        var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
        val availableQualities by remember { mutableStateOf(listOf("Auto", "1080p", "720p", "480p", "360p")) }
        var selectedQuality by remember { mutableStateOf("Auto") }
        var isLiked by remember { mutableStateOf(false) }
        var isDisliked by remember { mutableStateOf(false) }

        // AniSkip Timestamps
        var introStartTime by remember { mutableLongStateOf(intent.getLongExtra("introStart", 0L)) }
        var introEndTime by remember { mutableLongStateOf(intent.getLongExtra("introEnd", 0L)) }
        var outroStartTime by remember { mutableLongStateOf(intent.getLongExtra("outroStart", 0L)) }
        var outroEndTime by remember { mutableLongStateOf(intent.getLongExtra("outroEnd", 0L)) }

        val timelineColor = remember { AnimeBoxSettings.getYouTubePlayerTimelineColor(context) }

        val historyManager = remember { WatchHistoryManager(context) }

        // Helper to build/rebuild MediaSource with DoH DNS
        fun buildMediaSource(url: String, ref: String, subUrl: String): MediaSource {
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            val reqProps = mutableMapOf<String, String>()
            if (ref.isNotEmpty()) reqProps["Referer"] = ref

            val okHttpBuilder = OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)

            // Proper DNS configuration applied from app settings
            val client = AnimeBoxDnsHelper.applyDns(okHttpBuilder, context).build()
            val httpDataSourceFactory = OkHttpDataSource.Factory(client)
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(reqProps)
            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

            val mediaItemBuilder = MediaItem.Builder().setUri(url)

            val subConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
            if (subUrl.isNotEmpty()) {
                val mime = if (subUrl.contains(".ass", ignoreCase = true)) MimeTypes.TEXT_SSA else MimeTypes.TEXT_VTT
                subConfigs.add(
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subUrl))
                        .setMimeType(mime)
                        .setLanguage("en")
                        .setLabel("English")
                        .setSelectionFlags(if (isCcEnabled) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                )
            }

            // Also parse subtitles from currentSubtitlesJson if present
            try {
                if (currentSubtitlesJson.isNotBlank() && currentSubtitlesJson != "[]") {
                    val jArr = JSONArray(currentSubtitlesJson)
                    for (i in 0 until jArr.length()) {
                        val obj = jArr.optJSONObject(i) ?: continue
                        val sUrl = obj.optString("url").ifEmpty { obj.optString("file") }
                        val sLang = obj.optString("lang").ifEmpty { obj.optString("label", "Sub $i") }
                        if (sUrl.isNotBlank() && sUrl != subUrl) {
                            val mime = if (sUrl.contains(".ass", ignoreCase = true)) MimeTypes.TEXT_SSA else MimeTypes.TEXT_VTT
                            subConfigs.add(
                                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sUrl))
                                    .setMimeType(mime)
                                    .setLanguage(sLang.lowercase())
                                    .setLabel(sLang)
                                    .setSelectionFlags(0)
                                    .build()
                            )
                        }
                    }
                }
            } catch (_: Throwable) {}

            if (subConfigs.isNotEmpty()) {
                mediaItemBuilder.setSubtitleConfigurations(subConfigs)
            }

            val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setFragmentedMp4ExtractorFlags(
                    androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_TFDT_BOX or
                    androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS
                )
                .setMp4ExtractorFlags(
                    androidx.media3.extractor.mp4.Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS
                )
                .setTsExtractorFlags(
                    androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                    androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
                )
                .setTsExtractorTimestampSearchBytes(3000 * 188)

            val mediaItem = mediaItemBuilder.build()
            return DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory).createMediaSource(mediaItem)
        }

        // Tuning parameters matching Player 1 for ultra-low latency & zero lag
        val loadControl = remember {
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    if (isLocalFile) 2500 else 1500, // minBufferMs
                    if (isLocalFile) 15000 else 50000, // maxBufferMs
                    if (isLocalFile) 0 else 1000, // bufferForPlaybackMs (instant start)
                    if (isLocalFile) 250 else 1500  // bufferForPlaybackAfterRebufferMs
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }

        val audioAttributes = remember {
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
        }

        // Initialize ExoPlayer synchronously so PlayerView binds with NO BLANK SCREEN
        val player = remember {
            ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                .setAudioAttributes(audioAttributes, true)
                .build().also { p ->
                    exoPlayer = p
                    activePlayerForPip = p
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isCcEnabled)
                        .setSelectUndeterminedTextLanguage(true)
                        .build()
                }
        }

        LaunchedEffect(playbackSpeed, isHolding2x, player) {
            player.playbackParameters = PlaybackParameters(if (isHolding2x) 2.0f else playbackSpeed)
        }

        // Stream Extractor function with Hentai Waterfall, OkRu, and AnimeStreamExtractorEngine (100% parity with Player 1)
        suspend fun fetchStreamInfo(
            targetAnilistId: Int,
            episodeNum: Int,
            type: String,
            forceRefresh: Boolean = false,
            targetServer: String? = null
        ): Map<String, Any?>? {
            val aTitle = animeTitle
            val isAdult = isAdultAnime ||
                type.contains("hentai", ignoreCase = true) ||
                (targetAnilistId > 0 && com.lagradost.cloudstream3.ui.animebox.api.AniListClient.isAdultAnime(targetAnilistId, aTitle))

            if (isAdult) {
                val intentAltTitles: List<String> = intent.getStringArrayListExtra("altTitles")?.toList().orEmpty()
                val fetchedDetailTitles: List<String> = if (intentAltTitles.isEmpty() && targetAnilistId > 0) {
                    runCatching {
                        val rawJson = com.lagradost.cloudstream3.ui.animebox.api.AniListClient.getAnimeDetails(targetAnilistId)
                        val mediaObj = if (!rawJson.isNullOrBlank()) org.json.JSONObject(rawJson).optJSONObject("data")?.optJSONObject("Media") else null
                        val titleObj = mediaObj?.optJSONObject("title")
                        val synsArr = mediaObj?.optJSONArray("synonyms")
                        val syns = mutableListOf<String>()
                        if (synsArr != null) {
                            for (i in 0 until synsArr.length()) {
                                val s = synsArr.optString(i, "")
                                if (s.isNotBlank()) syns.add(s)
                            }
                        }
                        listOfNotNull(
                            titleObj?.optString("romaji", "")?.takeIf { it.isNotBlank() },
                            titleObj?.optString("english", "")?.takeIf { it.isNotBlank() },
                            titleObj?.optString("native", "")?.takeIf { it.isNotBlank() }
                        ) + syns
                    }.getOrDefault(emptyList())
                } else emptyList()

                val searchTitles: List<String> = (
                    intentAltTitles +
                    fetchedDetailTitles +
                    listOfNotNull(
                        aTitle,
                        aTitle.replace("Junyuu-chuu", "").replace("Junyuuchuu", "").trim(),
                        aTitle.replace("-", " ").trim(),
                        aTitle.replace("migi", "migui", ignoreCase = true),
                        aTitle.replace("migui", "migi", ignoreCase = true)
                    )
                ).map { it.trim() }.filter { it.isNotBlank() }.distinct()

                val prefServer = ServerPriorityManager.getPreferredServer(context, "hentai-jp")
                val chosenServer = if (targetServer.isNullOrBlank() || targetServer == "auto") prefServer else targetServer
                val isDubRequested = type.contains("dub", ignoreCase = true)

                suspend fun tryRe(): Map<String, Any?>? {
                    val rRes = runCatching {
                        com.lagradost.cloudstream3.ui.animebox.extractors.ReAnimeExtractor.extract(targetAnilistId, episodeNum, "sub")
                    }.getOrNull()
                    if (rRes != null && rRes.hlsUrl.isNotEmpty()) {
                        val sJson = org.json.JSONArray().apply { rRes.subtitles.forEach { put(org.json.JSONObject(it)) } }.toString()
                        return mapOf(
                            "hls" to rRes.hlsUrl,
                            "referer" to rRes.referer,
                            "subtitle" to rRes.subtitleUrl,
                            "subtitlesJson" to sJson,
                            "provider" to rRes.provider
                        )
                    }
                    return null
                }

                suspend fun tryHm(): Map<String, Any?>? {
                    val hmRes = runCatching {
                        com.lagradost.cloudstream3.ui.animebox.extractors.HentaiMamaExtractor.getStreamForAnime(
                            aTitle, episodeNum, searchTitles,
                            releaseYear = intent.getIntExtra("releaseYear", 0),
                            totalEpisodes = intent.getIntExtra("totalEpisodes", 0),
                            anilistId = targetAnilistId
                        )
                    }.getOrNull()
                    if (hmRes != null && hmRes.streamUrl.isNotEmpty()) {
                        return mapOf(
                            "hls" to hmRes.streamUrl,
                            "referer" to hmRes.referer,
                            "subtitle" to "",
                            "subtitlesJson" to "[]",
                            "provider" to hmRes.provider
                        )
                    }
                    return null
                }

                suspend fun tryHh(): Map<String, Any?>? {
                    val hhRes = runCatching {
                        com.lagradost.cloudstream3.ui.animebox.extractors.HentaiHavenExtractor.getStreamForAnime(aTitle, episodeNum, searchTitles)
                    }.getOrNull()
                    if (hhRes != null && hhRes.streamUrl.isNotEmpty()) {
                        return mapOf(
                            "hls" to hhRes.streamUrl,
                            "referer" to hhRes.referer,
                            "subtitle" to hhRes.subtitleUrl,
                            "subtitlesJson" to hhRes.subtitlesJson,
                            "provider" to hhRes.provider
                        )
                    }
                    return null
                }

                suspend fun tryHaho(): Map<String, Any?>? {
                    val primaryHahoTitle = searchTitles.firstOrNull() ?: aTitle
                    val hahoRes = runCatching {
                        com.lagradost.cloudstream3.ui.animebox.extractors.HahoMoeExtractor.getStream(
                            title = primaryHahoTitle,
                            episodeNum = episodeNum,
                            preferDub = isDubRequested,
                            searchTitles = searchTitles
                        )
                    }.getOrNull()
                    if (hahoRes != null && hahoRes.streamUrl.isNotEmpty()) {
                        if (isDubRequested && targetServer == null && !hahoRes.isDub) {
                            return null
                        }
                        return mapOf(
                            "hls" to hahoRes.streamUrl,
                            "referer" to hahoRes.referer,
                            "subtitle" to "",
                            "subtitlesJson" to "[]",
                            "provider" to "Haho Moe",
                            "isDub" to hahoRes.isDub
                        )
                    }
                    return null
                }

                // English audio (dub): prioritize Haho.moe first, then fall back if needed
                if (isDubRequested && targetServer == null) {
                    val hahoDub = tryHaho()
                    if (hahoDub != null) return hahoDub
                }

                val isHiiroNiKoku = targetAnilistId == 4731 ||
                    aTitle.contains("hiiro ni koku", ignoreCase = true) ||
                    aTitle.contains("hiiro no koku", ignoreCase = true) ||
                    aTitle.contains("time colored scarlet", ignoreCase = true)

                val serversToRun = when {
                    isHiiroNiKoku || chosenServer.equals("hahomoe", ignoreCase = true) || isDubRequested ->
                        listOf(::tryHaho, ::tryHm, ::tryRe, ::tryHh)
                    chosenServer.equals("reanime", ignoreCase = true) ->
                        listOf(::tryRe, ::tryHm, ::tryHaho, ::tryHh)
                    chosenServer.equals("hentaihaven", ignoreCase = true) ->
                        listOf(::tryHh, ::tryHm, ::tryHaho, ::tryRe)
                    else ->
                        listOf(::tryHm, ::tryHaho, ::tryRe, ::tryHh)
                }

                for (runner in serversToRun) {
                    val res = runner()
                    if (res != null) return res
                }
                return null
            }

            val okSpecialUrl = com.lagradost.cloudstream3.ui.animebox.extractors.PokemonHindiExtractor.POKEMON_OKRU_SPECIAL_URLS[targetAnilistId]
            val useOkRu = when {
                com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(targetAnilistId) -> (type == "sub" || type == "hardsub")
                okSpecialUrl != null -> (type == "dub" || type == "sub" || type == "hardsub")
                else -> false
            }
            if (useOkRu) {
                val scStream = com.lagradost.cloudstream3.ui.animebox.extractors.ShinChanOkRuExtractor.extractShinChanStream(targetAnilistId, episodeNum, explicitVideoUrl = okSpecialUrl)
                if (scStream != null && scStream.hlsUrl.isNotEmpty()) {
                    return mapOf(
                        "hls" to scStream.hlsUrl,
                        "referer" to scStream.referer,
                        "subtitle" to "",
                        "subtitlesJson" to "[]"
                    )
                }
            }

            return AnimeStreamExtractorEngine.getStreamInfo(
                context,
                targetAnilistId,
                episodeNum,
                type,
                aTitle,
                forceRefresh = forceRefresh,
                serverOverride = targetServer
            )
        }

        // Unified Episode Switcher with Watch History & Pre-fetch (100% same logic as Player 1)
        fun switchToEpisode(targetEp: Int, targetServer: String? = null, forceReload: Boolean = false) {
            if (targetEp < 1 || (totalEpisodes > 0 && targetEp > totalEpisodes)) return
            if (isNextEpLoading) return
            reloadingMessage = "Loading Episode $targetEp..."
            isNextEpLoading = true
            currentPosition = 0L
            duration = 1L
            try {
                player.stop()
                player.clearMediaItems()
            } catch (_: Throwable) {}

            coroutineScope.launch {
                delay(15000)
                if (isNextEpLoading) isNextEpLoading = false
            }

            coroutineScope.launch {
                val effectiveStreamType = if (isAdultAnime) "hentai-jp" else currentStreamType
                val sInfo = withContext(Dispatchers.IO) {
                    fetchStreamInfo(
                        anilistId,
                        targetEp,
                        effectiveStreamType,
                        forceRefresh = forceReload,
                        targetServer = targetServer
                    )
                }

                if (sInfo != null && sInfo["hls"] is String && (sInfo["hls"] as String).isNotBlank()) {
                    val newHls = sInfo["hls"] as String
                    val newRef = (sInfo["referer"] as? String).orEmpty()
                    val newSub = (sInfo["subtitle"] as? String).orEmpty()
                    val newSubsJson = (sInfo["subtitlesJson"] as? String).orEmpty()
                    if (newSubsJson.isNotBlank() && newSubsJson != "[]") {
                        currentSubtitlesJson = newSubsJson
                    }
                    currentEpisodeNum = targetEp
                    currentHlsUrl = newHls
                    currentReferer = newRef
                    currentSubtitleUrl = newSub
                    introStartTime = (sInfo["introStart"] as? Long) ?: (sInfo["introStart"] as? Number)?.toLong() ?: 0L
                    introEndTime = (sInfo["introEnd"] as? Long) ?: (sInfo["introEnd"] as? Number)?.toLong() ?: 0L
                    outroStartTime = (sInfo["outroStart"] as? Long) ?: (sInfo["outroStart"] as? Number)?.toLong() ?: 0L
                    outroEndTime = (sInfo["outroEnd"] as? Long) ?: (sInfo["outroEnd"] as? Number)?.toLong() ?: 0L

                    // Save watch progress to history matching Player 1
                    try {
                        historyManager.saveWatchProgress(
                            anilistId = anilistId,
                            animeTitle = animeTitle,
                            coverImageUrl = showCoverUrl,
                            episodeNumber = targetEp,
                            progressPositionMs = 1000L,
                            totalDurationMs = 1440000L,
                            showCoverUrl = showCoverUrl,
                            isOffline = false
                        )
                    } catch (_: Throwable) {}

                    // Pre-fetch next episode (N+1)
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            AnimeStreamExtractorEngine.prefetchStreamInfo(
                                context, anilistId, targetEp + 1, effectiveStreamType
                            )
                        } catch (_: Throwable) {}
                    }

                    // Subtitle parity matching Player 1
                    if (isNonJapSubOff && isNonJapaneseAudio) {
                        selectedSubtitleLang = "Off"
                        isCcEnabled = false
                    } else if (newSub.isNotBlank() || currentSubtitlesJson.length > 2) {
                        if (selectedSubtitleLang == "Off") {
                            selectedSubtitleLang = "English"
                            isCcEnabled = true
                        }
                    }

                    val mediaSource = buildMediaSource(newHls, newRef, newSub)
                    player.setMediaSource(mediaSource)
                    player.prepare()
                    player.playWhenReady = true
                    // isNextEpLoading stays true until STATE_READY in onPlaybackStateChanged
                } else {
                    isNextEpLoading = false
                    isEpisodesLoading = false
                    isReloadingStream = false

                    val enabledServers = ServerPriorityManager
                        .getServersForCategory(adultAwareServerCategory(context, currentStreamType))
                        .filter { it.key != ServerPriorityManager.SERVER_AUTO }
                        .map { it.key to it.displayName }

                    if (AnimeBoxSettings.isShowFallbackDialogsEnabled(context)) {
                        fallbackDialogData = YouTubeFallbackDialogData(
                            title = "Episode $targetEp Unavailable",
                            description = "Could not load video for Episode $targetEp. You can retry, switch server, or report the issue.",
                            primaryButtonText = "Retry",
                            serverOptions = enabledServers,
                            onPrimaryAction = {
                                fallbackDialogData = null
                                switchToEpisode(targetEp, forceReload = true)
                            },
                            onServerSelected = { sKey ->
                                fallbackDialogData = null
                                ServerPriorityManager.setPreferredServer(context, adultAwareServerCategory(context, currentStreamType), sKey)
                                selectedServerName = enabledServers.find { it.first == sKey }?.second ?: sKey
                                switchToEpisode(targetEp, targetServer = sKey, forceReload = true)
                            }
                        )
                    }
                }
            }
        }

        // Groq AI Transcription launcher
        fun launchGroqTranscription(apiKey: String) {
            if (isGroqTranscribing) return
            isGroqTranscribing = true
            groqProgressText = "Connecting to Groq Whisper API..."
            val transLabel = "English (Groq AI)"
            selectedSubtitleLang = transLabel
            isCcEnabled = true

            try {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                playerViewInstance?.subtitleView?.setCues(emptyList())
                activeSubtitleText = ""
            } catch (_: Throwable) {}

            coroutineScope.launch {
                try {
                    val streamUrl = currentHlsUrl.ifBlank { initialHlsUrl }
                    val ref = currentReferer

                    val exoAudioTrackLang = try {
                        var detectedTrackLang = ""
                        val tracks = player.currentTracks
                        for (group in tracks.groups) {
                            if (group.type == C.TRACK_TYPE_AUDIO && group.isSelected) {
                                for (i in 0 until group.length) {
                                    if (group.isTrackSelected(i)) {
                                        val fmt = group.getTrackFormat(i)
                                        val langOrLabel = listOfNotNull(fmt.language, fmt.label).joinToString(" ").trim()
                                        if (langOrLabel.isNotBlank() && !langOrLabel.equals("und", ignoreCase = true)) {
                                            detectedTrackLang = langOrLabel
                                            break
                                        }
                                    }
                                }
                            }
                            if (detectedTrackLang.isNotBlank()) break
                        }
                        detectedTrackLang
                    } catch (_: Throwable) { "" }

                    val activeWatchingAudioLang = when {
                        selectedAudio.isNotBlank() && !selectedAudio.contains("Japanese", ignoreCase = true) -> selectedAudio
                        currentStreamType.isNotBlank() && currentStreamType != "sub" && currentStreamType != "hardsub" && currentStreamType != "raw" -> currentStreamType
                        exoAudioTrackLang.isNotBlank() -> exoAudioTrackLang
                        selectedAudio.isNotBlank() -> selectedAudio
                        else -> currentStreamType.ifBlank { "ja" }
                    }

                    val result = com.lagradost.cloudstream3.ui.animebox.api.GroqCloudTranscriber.transcribeEpisodeAudio(
                        context = context,
                        apiKey = apiKey,
                        hlsOrVideoUrl = streamUrl,
                        referer = ref,
                        targetLangCode = "en",
                        sourceAudioLang = activeWatchingAudioLang,
                        anilistId = anilistId,
                        episodeNum = currentEpisodeNum,
                        animeTitle = animeTitle,
                        coverUrl = showCoverUrl,
                        backdropUrl = intent.getStringExtra("backdropUrl") ?: "",
                        logoUrl = intent.getStringExtra("logoUrl") ?: "",
                        onProgress = { cur, total, msg, cues ->
                            coroutineScope.launch(Dispatchers.Main) {
                                groqProgressText = msg
                                if (cues.isNotEmpty()) {
                                    if (selectedSubtitleLang != transLabel) {
                                        selectedSubtitleLang = transLabel
                                    }
                                    liveSubtitleCues = cues.sortedBy { it.startMs }
                                }
                            }
                        }
                    )

                    isGroqTranscribing = false
                    when (result) {
                        is com.lagradost.cloudstream3.ui.animebox.api.GroqTranscribeResult.Success -> {
                            selectedSubtitleLang = transLabel
                            translatedSubMap = translatedSubMap + (transLabel to result.subtitleFilePath)
                            val f = java.io.File(result.subtitleFilePath)
                            val vttTxt = if (f.exists() && f.length() > 100) f.readText() else ""
                            if (vttTxt.isNotBlank()) {
                                liveSubtitleCues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(vttTxt).sortedBy { it.startMs }
                            }
                            Toast.makeText(context, "Groq AI Subtitles ready (${result.cuesCount} cues)", Toast.LENGTH_LONG).show()
                        }
                        is com.lagradost.cloudstream3.ui.animebox.api.GroqTranscribeResult.Error -> {
                            groqErrorMessage = result.message
                            showGroqErrorDialog = true
                        }
                    }
                } catch (t: Throwable) {
                    isGroqTranscribing = false
                    groqErrorMessage = t.localizedMessage ?: "Unexpected error during cloud transcription."
                    showGroqErrorDialog = true
                }
            }
        }

        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                    val isAiSub = isTranslatingMlKit || isGroqTranscribing || liveSubtitleCues.isNotEmpty() ||
                            selectedSubtitleLang.contains("AI", ignoreCase = true) ||
                            selectedSubtitleLang.contains("Groq", ignoreCase = true) ||
                            selectedSubtitleLang.contains("Translated", ignoreCase = true) ||
                            selectedSubtitleLang.contains("Public", ignoreCase = true)
                    if (!isAiSub && isCcEnabled && selectedSubtitleLang != "Off") {
                        if (isHardsub) {
                            val lines = cueGroup.cues.mapNotNull { it.text?.toString() }.filter { it.isNotBlank() }
                            activeSubtitleText = lines.joinToString("\n")
                            playerViewInstance?.subtitleView?.setCues(emptyList())
                            playerViewInstance?.subtitleView?.visibility = android.view.View.INVISIBLE
                        } else {
                            activeSubtitleText = ""
                            playerViewInstance?.subtitleView?.visibility = android.view.View.VISIBLE
                            playerViewInstance?.subtitleView?.setCues(cueGroup.cues)
                        }
                    }
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    applyVideoAspectRatioMode(playerViewInstance, resizeMode, videoSize)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) {
                        val d = player.duration
                        if (d > 0L) duration = d
                        applyVideoAspectRatioMode(playerViewInstance, resizeMode, player.videoSize)
                        isNextEpLoading = false
                        isReloadingStream = false
                        isEpisodesLoading = false
                    } else if (state == Player.STATE_ENDED) {
                        val maxEps = if (totalEpisodes > 0) totalEpisodes else 9999
                        if (autoplayNext && currentEpisodeNum < maxEps && !isNextEpLoading) {
                            switchToEpisode(currentEpisodeNum + 1)
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    error.printStackTrace()
                    isNextEpLoading = false
                    isReloadingStream = false
                    isEpisodesLoading = false
                    if (isLocalFile || isOfflineDownload) {
                        fallbackDialogData = YouTubeFallbackDialogData(
                            title = "Offline Playback Error",
                            description = "Could not play this offline episode. Try tapping Retry to reload the file.",
                            primaryButtonText = "Retry",
                            onPrimaryAction = {
                                player.seekTo(0L)
                                player.prepare()
                                player.play()
                            }
                        )
                        return
                    }

                    val enabledServers = ServerPriorityManager
                        .getServersForCategory(adultAwareServerCategory(context, currentStreamType))
                        .filter { it.key != ServerPriorityManager.SERVER_AUTO }
                        .map { it.key to it.displayName }

                    if (AnimeBoxSettings.isShowFallbackDialogsEnabled(context)) {
                        fallbackDialogData = YouTubeFallbackDialogData(
                            title = "Playback Error",
                            description = "Could not load video for this episode. You can retry, switch server, or report the issue.",
                            primaryButtonText = "Retry",
                            serverOptions = enabledServers,
                            onPrimaryAction = {
                                fallbackDialogData = null
                                reloadingMessage = "Retrying Stream..."
                                isReloadingStream = true
                                coroutineScope.launch {
                                    val sInfo = withContext(Dispatchers.IO) {
                                        fetchStreamInfo(
                                            anilistId, currentEpisodeNum, if (isAdultAnime) "hentai-jp" else currentStreamType, forceRefresh = true
                                        )
                                    }
                                    if (sInfo != null && sInfo["hls"] is String && (sInfo["hls"] as String).isNotBlank()) {
                                        val newHls = sInfo["hls"] as String
                                        val newRef = (sInfo["referer"] as? String).orEmpty()
                                        val newSub = (sInfo["subtitle"] as? String).orEmpty()
                                        val newSubsJson = (sInfo["subtitlesJson"] as? String).orEmpty()
                                        if (newSubsJson.isNotBlank() && newSubsJson != "[]") {
                                            currentSubtitlesJson = newSubsJson
                                        }
                                        currentHlsUrl = newHls
                                        currentReferer = newRef
                                        currentSubtitleUrl = newSub
                                        introStartTime = (sInfo["introStart"] as? Long) ?: (sInfo["introStart"] as? Number)?.toLong() ?: 0L
                                        introEndTime = (sInfo["introEnd"] as? Long) ?: (sInfo["introEnd"] as? Number)?.toLong() ?: 0L
                                        outroStartTime = (sInfo["outroStart"] as? Long) ?: (sInfo["outroStart"] as? Number)?.toLong() ?: 0L
                                        outroEndTime = (sInfo["outroEnd"] as? Long) ?: (sInfo["outroEnd"] as? Number)?.toLong() ?: 0L
                                        val mediaSource = buildMediaSource(newHls, newRef, newSub)
                                        player.setMediaSource(mediaSource)
                                        player.prepare()
                                        player.playWhenReady = true
                                    } else {
                                        isReloadingStream = false
                                    }
                                }
                            },
                            onServerSelected = { sKey ->
                                fallbackDialogData = null
                                reloadingMessage = "Switching Server..."
                                isReloadingStream = true
                                coroutineScope.launch {
                                    ServerPriorityManager.setPreferredServer(context, adultAwareServerCategory(context, currentStreamType), sKey)
                                    selectedServerName = enabledServers.find { it.first == sKey }?.second ?: sKey
                                    val sInfo = withContext(Dispatchers.IO) {
                                        fetchStreamInfo(
                                            anilistId, currentEpisodeNum, if (isAdultAnime) "hentai-jp" else currentStreamType, forceRefresh = true, targetServer = sKey
                                        )
                                    }
                                    if (sInfo != null && sInfo["hls"] is String && (sInfo["hls"] as String).isNotBlank()) {
                                        val newHls = sInfo["hls"] as String
                                        val newRef = (sInfo["referer"] as? String).orEmpty()
                                        val newSub = (sInfo["subtitle"] as? String).orEmpty()
                                        val newSubsJson = (sInfo["subtitlesJson"] as? String).orEmpty()
                                        if (newSubsJson.isNotBlank() && newSubsJson != "[]") {
                                            currentSubtitlesJson = newSubsJson
                                        }
                                        currentHlsUrl = newHls
                                        currentReferer = newRef
                                        currentSubtitleUrl = newSub
                                        introStartTime = (sInfo["introStart"] as? Long) ?: (sInfo["introStart"] as? Number)?.toLong() ?: 0L
                                        introEndTime = (sInfo["introEnd"] as? Long) ?: (sInfo["introEnd"] as? Number)?.toLong() ?: 0L
                                        outroStartTime = (sInfo["outroStart"] as? Long) ?: (sInfo["outroStart"] as? Number)?.toLong() ?: 0L
                                        outroEndTime = (sInfo["outroEnd"] as? Long) ?: (sInfo["outroEnd"] as? Number)?.toLong() ?: 0L
                                        val mediaSource = buildMediaSource(newHls, newRef, newSub)
                                        player.setMediaSource(mediaSource)
                                        player.prepare()
                                        player.playWhenReady = true
                                    } else {
                                        isReloadingStream = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }

        LaunchedEffect(resizeMode, playerViewInstance) {
            applyVideoAspectRatioMode(playerViewInstance, resizeMode, player.videoSize)
        }

        LaunchedEffect(player, isEnhancerEnabled, selectedEnhancerPreset) {
            try {
                com.lagradost.cloudstream3.ui.animebox.enhancer.anime4k.Anime4kPlayerBinder.apply(
                    player = player,
                    enabled = isEnhancerEnabled,
                    preset = selectedEnhancerPreset,
                    halfPrecision = false,
                    restartIfNeeded = true
                )
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        // Real-time Audio Enhancer & Equalizer hook
        DisposableEffect(player, isAudioEnhancerEnabled, selectedAudioPreset) {
            val sessionId = try { player.audioSessionId } catch (_: Exception) { android.media.AudioManager.AUDIO_SESSION_ID_GENERATE }
            if (sessionId != 0 && sessionId != android.media.AudioManager.AUDIO_SESSION_ID_GENERATE) {
                com.lagradost.cloudstream3.ui.animebox.enhancer.AnimeAudioEnhancer.attachToSession(
                    audioSessionId = sessionId,
                    enabled = isAudioEnhancerEnabled,
                    bass = selectedAudioPreset.defaultBass,
                    vocal = selectedAudioPreset.defaultVocal,
                    treble = selectedAudioPreset.defaultTreble,
                    virtual = selectedAudioPreset.defaultVirtualizer,
                    loudness = selectedAudioPreset.defaultLoudness
                )
            }
            onDispose {}
        }

        // Discover and auto-select available subtitles (Groq AI, saved AI files, public community subs, and provider VTT)
        LaunchedEffect(currentEpisodeNum, anilistId, currentSubtitlesJson, currentStreamType, selectedAudio) {
            val targetAnilistId = if (anilistId > 0) anilistId else animeTitle.hashCode().coerceAtLeast(1)
            val newMap = mutableMapOf<String, String>()

            // 1. Fetch public subtitles
            if (targetAnilistId > 0 && currentEpisodeNum > 0) {
                withContext(Dispatchers.IO) {
                    try {
                        val pubSubs = com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitlesManager.fetchPublicSubtitles(targetAnilistId, currentEpisodeNum)
                        withContext(Dispatchers.Main) {
                            publicSubtitlesList = pubSubs
                        }
                    } catch (_: Throwable) {}
                }
            }

            // 2. Scan saved AI subtitles
            try {
                val savedDir = File(context.filesDir, "ai_subtitles")
                if (savedDir.exists()) {
                    val prefix = "${targetAnilistId}_ep${currentEpisodeNum}_"
                    savedDir.listFiles()?.forEach { f ->
                        if (f.name.startsWith(prefix) && f.extension == "vtt") {
                            val rest = f.name.removePrefix(prefix).removeSuffix(".vtt")
                            val parts = rest.split("_")
                            val srcRaw = parts.getOrNull(0) ?: "Internet"
                            val langRaw = parts.getOrNull(1) ?: "Subtitles"
                            val src = srcRaw.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                            val lang = langRaw.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                            val isGroq = src.contains("Groq", ignoreCase = true) || lang.contains("Groq", ignoreCase = true)
                            val subKey = if (isGroq) "$lang (Public Subtitle from Groq)" else "$lang ($src)"
                            newMap[subKey] = f.absolutePath
                        }
                    }
                }
            } catch (_: Throwable) {}

            // 3. Process Groq public subtitles
            try {
                publicSubtitlesList.forEach { pubSub ->
                    val isGroq = pubSub.uploaderName.contains("Groq", ignoreCase = true) ||
                            pubSub.vttContent.contains("Groq", ignoreCase = true) ||
                            pubSub.langName.contains("Groq", ignoreCase = true)
                    if (isGroq) {
                        val subKey = "${pubSub.langName} (Public Subtitle from Groq)"
                        val safeLangCode = pubSub.langCode.replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")
                        val savedFile = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.getSavedSubtitleFile(context, targetAnilistId, currentEpisodeNum, safeLangCode)
                        if (!savedFile.exists() || savedFile.length() <= 150) {
                            if (pubSub.vttContent.isNotBlank()) {
                                try {
                                    savedFile.parentFile?.mkdirs()
                                    savedFile.writeText(pubSub.vttContent)
                                } catch (_: Throwable) {}
                            }
                        }
                        if (savedFile.exists() && savedFile.length() > 150) {
                            newMap[subKey] = savedFile.absolutePath
                        }
                    }
                }
            } catch (_: Throwable) {}

            // 4. Subtitles from provider json
            try {
                if (currentSubtitlesJson.isNotBlank() && currentSubtitlesJson != "[]") {
                    val subsArr = JSONArray(currentSubtitlesJson)
                    for (i in 0 until subsArr.length()) {
                        val subObj = subsArr.optJSONObject(i) ?: continue
                        val subUrl = subObj.optString("url", subObj.optString("file", ""))
                        val rawLang = subObj.optString("lang", subObj.optString("label", subObj.optString("language", subObj.optString("name", "Unknown")))).trim()
                        val kind = subObj.optString("kind", "").lowercase()
                        if (isThumbnailSubtitleTrack(subUrl, rawLang) || kind.contains("thumbnail")) continue
                        if (subUrl.isNotEmpty()) {
                            val cleanLang = when {
                                rawLang.equals("en", true) || rawLang.startsWith("en-", true) || rawLang.startsWith("eng", true) || rawLang.contains("english", true) -> "English"
                                rawLang.equals("ja", true) || rawLang.startsWith("ja-", true) || rawLang.startsWith("jpn", true) || rawLang.startsWith("jap", true) || rawLang.contains("japanese", true) -> "Japanese"
                                rawLang.equals("hi", true) || rawLang.startsWith("hi-", true) || rawLang.startsWith("hin", true) || rawLang.contains("hindi", true) -> "Hindi"
                                rawLang.equals("fr", true) || rawLang.startsWith("fr-", true) || rawLang.startsWith("fre", true) || rawLang.startsWith("fra", true) || rawLang.contains("french", true) -> "French"
                                rawLang.equals("es", true) || rawLang.startsWith("es-", true) || rawLang.startsWith("spa", true) || rawLang.contains("spanish", true) -> "Spanish"
                                rawLang.equals("de", true) || rawLang.startsWith("de-", true) || rawLang.startsWith("ger", true) || rawLang.startsWith("deu", true) || rawLang.contains("german", true) -> "German"
                                rawLang.equals("it", true) || rawLang.startsWith("it-", true) || rawLang.startsWith("ita", true) || rawLang.contains("italian", true) -> "Italian"
                                rawLang.isNotBlank() -> rawLang.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                else -> "Unknown"
                            }
                            val subKey = if (cleanLang.contains("(VTT)", ignoreCase = true)) cleanLang else "$cleanLang (VTT)"
                            if (!newMap.containsKey(subKey)) {
                                newMap[subKey] = subUrl
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}

            // 5. Attach Dubtitle track
            if (currentSubtitleUrl.isNotEmpty() && !isThumbnailSubtitleTrack(currentSubtitleUrl) && (currentStreamType == "dub" || selectedAudio.equals("English", ignoreCase = true) || selectedAudio.contains("Dub", ignoreCase = true))) {
                newMap["English (AI Dubtitle)"] = currentSubtitleUrl
            }

            translatedSubMap = newMap

            // Auto-selection resolution matching Player 1 priorities
            val isEngDub = currentStreamType.equals("dub", ignoreCase = true) || selectedAudio.contains("English", ignoreCase = true) || selectedAudio.contains("Dub", ignoreCase = true)
            val hasEnglishVtt = newMap.containsKey("English (VTT)") || newMap.containsKey("English")
            val englishGroqKey = newMap.keys.firstOrNull { it.contains("English", ignoreCase = true) && it.contains("Groq", ignoreCase = true) }
            val anyGroqKey = newMap.keys.firstOrNull { it.contains("Groq", ignoreCase = true) }
            val bestGroqKey = englishGroqKey ?: anyGroqKey

            val autoMlKitLangPref = AnimeBoxSettings.getAutomaticMlKitTranslationLanguage(context).trim().lowercase().removeSuffix("_pro")
            val autoMlKitTargetLabel = if (autoMlKitLangPref.isNotEmpty() && autoMlKitLangPref != "off" && currentStreamType != "hardsub") {
                val ln = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.getLanguageName(autoMlKitLangPref)
                if (autoMlKitLangPref == "en") "English (AI Translated)" else "$ln (AI Translated)"
            } else null

            val isNonJapSubOff = AnimeBoxSettings.isNonJapaneseSubOff(context)
            val isNonJapaneseAudio = !selectedAudio.equals("Japanese", ignoreCase = true) && !selectedAudio.contains("Japanese", ignoreCase = true) && !selectedAudio.contains("Jap", ignoreCase = true)

            val resolvedSub: String = when {
                autoMlKitTargetLabel != null && newMap.containsKey(autoMlKitTargetLabel) -> autoMlKitTargetLabel
                isEngDub && bestGroqKey != null -> bestGroqKey
                isNonJapSubOff && isNonJapaneseAudio -> "Off"
                isEngDub && newMap.containsKey("English (AI Dubtitle)") -> "English (AI Dubtitle)"
                hasEnglishVtt -> if (newMap.containsKey("English (VTT)")) "English (VTT)" else "English"
                bestGroqKey != null -> bestGroqKey
                newMap.containsKey("English (AI Translated)") -> "English (AI Translated)"
                newMap.isNotEmpty() -> newMap.keys.first()
                else -> "Off"
            }

            selectedSubtitleLang = resolvedSub
            isCcEnabled = resolvedSub != "Off"
        }

        // Fetch and parse VTT subtitles into live cues for selected subtitle
        LaunchedEffect(selectedSubtitleLang, translatedSubMap) {
            val pathOrUrl = translatedSubMap[selectedSubtitleLang] ?: if (selectedSubtitleLang != "Off") currentSubtitleUrl else ""
            if (pathOrUrl.isBlank() || selectedSubtitleLang == "Off") {
                liveSubtitleCues = emptyList()
                activeSubtitleText = ""
                return@LaunchedEffect
            }
            withContext(Dispatchers.IO) {
                try {
                    val content = if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                        val okHttpBuilder = OkHttpClient.Builder()
                            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                            .followRedirects(true)
                            .followSslRedirects(true)
                        val client = AnimeBoxDnsHelper.applyDns(okHttpBuilder, context).build()
                        val reqBuilder = okhttp3.Request.Builder()
                            .url(pathOrUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        if (currentReferer.isNotEmpty()) reqBuilder.header("Referer", currentReferer)
                        client.newCall(reqBuilder.build()).execute().use { it.body?.string() }
                    } else {
                        val f = File(pathOrUrl)
                        if (f.exists()) f.readText() else null
                    }
                    if (!content.isNullOrBlank()) {
                        val cues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(content)
                        withContext(Dispatchers.Main) {
                            liveSubtitleCues = cues.sortedBy { it.startMs }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            liveSubtitleCues = emptyList()
                        }
                    }
                } catch (_: Throwable) {
                    withContext(Dispatchers.Main) {
                        liveSubtitleCues = emptyList()
                    }
                }
            }
        }

        // Automatic ML Kit Subtitle Translation (Triggered when enabled in My Space - Parity with Player 1)
        var autoMlKitDoneKey by remember { mutableStateOf("") }
        LaunchedEffect(anilistId, currentEpisodeNum, currentHlsUrl, currentSubtitleUrl) {
            val autoLangCode = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings
                .getAutomaticMlKitTranslationLanguage(context)
                .trim()
                .lowercase()
                .removeSuffix("_pro")

            if (autoLangCode.isNotEmpty() && autoLangCode != "off" && currentStreamType != "hardsub" && currentHlsUrl.isNotEmpty()) {
                val targetAnilistId = if (anilistId > 0) anilistId else animeTitle.hashCode().coerceAtLeast(1)
                val langName = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.getLanguageName(autoLangCode)
                val label = if (autoLangCode == "en") "English (AI Translated)" else "$langName (AI Translated)"

                var sourceVtt = currentSubtitleUrl.takeIf { it.isNotEmpty() && !it.contains("mlkit_trans") && !it.contains("ai_subtitles") } ?: initialSubtitleUrl
                if (sourceVtt.isEmpty()) {
                    delay(350)
                    sourceVtt = currentSubtitleUrl.takeIf { it.isNotEmpty() && !it.contains("mlkit_trans") && !it.contains("ai_subtitles") } ?: initialSubtitleUrl
                }

                val currentKey = "${targetAnilistId}_${currentEpisodeNum}_${autoLangCode}_${sourceVtt.hashCode()}"
                if (autoMlKitDoneKey != currentKey) {
                    val savedFile = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager
                        .getSavedSubtitleFile(context, targetAnilistId, currentEpisodeNum, autoLangCode)
                    if (savedFile.exists() && savedFile.length() > 150) {
                        val cues = withContext(Dispatchers.IO) {
                            com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(savedFile.readText())
                        }
                        if (cues.size >= 2) {
                            autoMlKitDoneKey = currentKey
                            val newMap = translatedSubMap.toMutableMap()
                            newMap[label] = savedFile.absolutePath
                            translatedSubMap = newMap
                            currentSubtitleUrl = savedFile.absolutePath
                            selectedSubtitleLang = label
                            liveSubtitleCues = cues
                            try {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                            } catch (_: Throwable) {}
                            playerViewInstance?.subtitleView?.visibility = android.view.View.VISIBLE
                            liveTranslatingStatus = "Translating to $langName (${cues.size}/${cues.size})"
                            delay(1800)
                            if (!isTranslatingMlKit) {
                                liveTranslatingStatus = null
                            }
                            return@LaunchedEffect
                        }
                    }

                    if (sourceVtt.isEmpty() && targetAnilistId > 0) {
                        val subStreamInfo = withContext(Dispatchers.IO) {
                            com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(
                                context, targetAnilistId, currentEpisodeNum, if (isAdultAnime) "hentai-jp" else "sub", animeTitle
                            )
                        }
                        sourceVtt = (subStreamInfo?.get("subtitle") as? String ?: "").trim()
                    }

                    if (sourceVtt.isNotEmpty() || currentHlsUrl.isNotEmpty()) {
                        autoMlKitDoneKey = currentKey
                        isTranslatingMlKit = true
                        liveTranslatingStatus = "Translating to $langName..."
                        val localFilePath = com.lagradost.cloudstream3.ui.animebox.api.GoogleMlKitTranslatorManager.translateVttWithMlKit(
                            context = context,
                            vttUrlOrPath = sourceVtt,
                            targetLangCode = autoLangCode,
                            hlsUrl = currentHlsUrl,
                            referer = currentReferer,
                            anilistId = targetAnilistId,
                            episodeNum = currentEpisodeNum,
                            animeTitle = animeTitle,
                            coverUrl = showCoverUrl,
                            onProgress = { cur, total, tempPath, partialCues ->
                                liveTranslatingStatus = "Translating to $langName ($cur/$total)"
                                if (partialCues.isNotEmpty()) {
                                    liveSubtitleCues = partialCues
                                    currentSubtitleUrl = tempPath
                                    selectedSubtitleLang = label
                                    try {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .build()
                                    } catch (_: Throwable) {}
                                    playerViewInstance?.subtitleView?.visibility = android.view.View.VISIBLE
                                }
                            }
                        )
                        isTranslatingMlKit = false
                        liveTranslatingStatus = null

                        if (localFilePath != null) {
                            val newMap = translatedSubMap.toMutableMap()
                            newMap[label] = localFilePath
                            translatedSubMap = newMap
                            currentSubtitleUrl = localFilePath
                            selectedSubtitleLang = label
                            val finalCues = withContext(Dispatchers.IO) {
                                com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(java.io.File(localFilePath).readText())
                            }
                            liveSubtitleCues = finalCues
                            isCcEnabled = true
                            try {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                            } catch (_: Throwable) {}
                            playerViewInstance?.subtitleView?.visibility = android.view.View.VISIBLE
                        }
                    }
                }
            }
        }

        // Real-time subtitle cue synchronization with player position
        LaunchedEffect(isCcEnabled, selectedSubtitleLang, liveSubtitleCues, currentPosition, isHardsub, playerViewInstance) {
            val subView = playerViewInstance?.subtitleView ?: return@LaunchedEffect
            if (!isCcEnabled || selectedSubtitleLang == "Off") {
                subView.setCues(emptyList())
                activeSubtitleText = ""
                subView.visibility = android.view.View.INVISIBLE
                return@LaunchedEffect
            }

            val isAiSub = isTranslatingMlKit || isGroqTranscribing || liveSubtitleCues.isNotEmpty() ||
                    selectedSubtitleLang.contains("AI", ignoreCase = true) ||
                    selectedSubtitleLang.contains("Groq", ignoreCase = true) ||
                    selectedSubtitleLang.contains("Translated", ignoreCase = true) ||
                    selectedSubtitleLang.contains("Public", ignoreCase = true)

            if (isAiSub) {
                val activeCue = liveSubtitleCues.firstOrNull { currentPosition in it.startMs..it.endMs }
                val cueText = activeCue?.translatedText?.trim().orEmpty()
                if (isHardsub) {
                    activeSubtitleText = cueText
                    subView.setCues(emptyList())
                    subView.visibility = android.view.View.INVISIBLE
                } else {
                    activeSubtitleText = ""
                    subView.visibility = android.view.View.VISIBLE
                    if (cueText.isNotBlank()) {
                        val cue = androidx.media3.common.text.Cue.Builder()
                            .setText(cueText)
                            .build()
                        subView.setCues(listOf(cue))
                    } else {
                        subView.setCues(emptyList())
                    }
                }
            } else {
                if (isHardsub) {
                    subView.setCues(emptyList())
                    subView.visibility = android.view.View.INVISIBLE
                } else {
                    activeSubtitleText = ""
                    subView.visibility = android.view.View.VISIBLE
                }
            }
        }

        // Apply subtitle style customization directly to SubtitleView
        LaunchedEffect(subPreset, subFontSize, subTextColor, subBgOpacity, subEdgeType, subTypeface, isCcEnabled, isHardsub, playerViewInstance) {
            val subView = playerViewInstance?.subtitleView ?: return@LaunchedEffect
            val backgroundColor = if (subBgOpacity > 0) {
                android.graphics.Color.argb(subBgOpacity, 0, 0, 0)
            } else {
                android.graphics.Color.TRANSPARENT
            }
            val captionStyle = androidx.media3.ui.CaptionStyleCompat(
                subTextColor,
                backgroundColor,
                android.graphics.Color.TRANSPARENT,
                if (isHardsub) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE else subEdgeType,
                android.graphics.Color.BLACK,
                subTypeface
            )
            subView.setStyle(captionStyle)
            subView.setApplyEmbeddedStyles(!isHardsub)
            subView.setApplyEmbeddedFontSizes(!isHardsub)
            subView.setBottomPaddingFraction(0.0f)
            val sizeFraction = if (isHardsub) {
                0.065f
            } else {
                when {
                    subFontSize <= 14f -> 0.045f
                    subFontSize >= 24f -> 0.065f
                    subFontSize >= 20f -> 0.055f
                    else -> 0.050f
                }
            }
            subView.setFractionalTextSize(sizeFraction)
            subView.visibility = if (isCcEnabled) android.view.View.VISIBLE else android.view.View.GONE
        }

        // Load initial stream into player immediately
        LaunchedEffect(Unit) {
            if (isLocalFile) {
                val localItem = MediaItem.fromUri(android.net.Uri.fromFile(File(localFilePath)))
                player.setMediaItem(localItem)
                player.prepare()
                player.playWhenReady = true
            } else if (currentHlsUrl.isNotEmpty()) {
                val mediaSource = buildMediaSource(currentHlsUrl, currentReferer, currentSubtitleUrl)
                player.setMediaSource(mediaSource)
                player.prepare()
                player.playWhenReady = true
            } else if (anilistId > 0) {
                coroutineScope.launch {
                    val sInfo = AnimeStreamExtractorEngine.getStreamInfo(
                        context, anilistId, currentEpisodeNum, if (isAdultAnime) "hentai-jp" else currentStreamType, animeTitle
                    )
                    if (sInfo != null && sInfo["hls"] is String) {
                        val newHls = sInfo["hls"] as String
                        val newRef = (sInfo["referer"] as? String).orEmpty()
                        val newSub = (sInfo["subtitle"] as? String).orEmpty()
                        currentHlsUrl = newHls
                        currentReferer = newRef
                        currentSubtitleUrl = newSub
                        introStartTime = (sInfo["introStart"] as? Long) ?: (sInfo["introStart"] as? Number)?.toLong() ?: -1L
                        introEndTime = (sInfo["introEnd"] as? Long) ?: (sInfo["introEnd"] as? Number)?.toLong() ?: -1L
                        outroStartTime = (sInfo["outroStart"] as? Long) ?: (sInfo["outroStart"] as? Number)?.toLong() ?: -1L
                        outroEndTime = (sInfo["outroEnd"] as? Long) ?: (sInfo["outroEnd"] as? Number)?.toLong() ?: -1L
                        val mediaSource = buildMediaSource(newHls, newRef, newSub)
                        player.setMediaSource(mediaSource)
                        player.prepare()
                        player.playWhenReady = true
                    }
                }
            }

            // Load available servers
            val sCategory = if (isAdultAnime) {
                "hentai-jp"
            } else if (currentStreamType.contains("dub", ignoreCase = true)) {
                "dub"
            } else if (currentStreamType.contains("hindi", ignoreCase = true)) {
                "hindi"
            } else {
                "sub"
            }
            val serverList = ServerPriorityManager.getServersForCategory(sCategory)
            availableServers = serverList
            val pref = ServerPriorityManager.getPreferredServer(context, sCategory)
            selectedServerName = serverList.find { it.key == pref }?.displayName ?: serverList.firstOrNull()?.displayName ?: "Auto Server"
        }

        // Periodic ticker for position and watch history
        LaunchedEffect(Unit) {
            while (true) {
                delay(300)
                val pPos = player.currentPosition
                if (pPos >= 0L) {
                    currentPosition = pPos
                }
                val pDur = player.duration
                if (pDur > 0L) {
                    duration = pDur
                }
                val pBuf = player.bufferedPosition
                if (pBuf >= 0L) {
                    bufferedPosition = pBuf
                }

                // Auto-skip intro if enabled
                if (autoSkipIntro && introStartTime > 0 && introEndTime > introStartTime) {
                    if (currentPosition in introStartTime until introEndTime) {
                        player.seekTo(introEndTime + 500)
                    }
                }

                // Save history
                if (anilistId > 0 && duration > 10_000 && currentPosition > 3_000) {
                    historyManager.saveWatchProgress(
                        anilistId = anilistId,
                        animeTitle = animeTitle,
                        coverImageUrl = showCoverUrl,
                        episodeNumber = currentEpisodeNum,
                        progressPositionMs = currentPosition,
                        totalDurationMs = duration,
                        showCoverUrl = showCoverUrl,
                        isOffline = isOfflineDownload
                    )
                }

                // Auto hide controls after 4 seconds
                if (controlsVisible && !isLocked && isPlaying) {
                    if (System.currentTimeMillis() - lastUserInteractionTime > 4000) {
                        controlsVisible = false
                    }
                }
            }
        }

        // Fetch AniZip episodes with exact same logic as Anime Detail screen
        LaunchedEffect(anilistId, currentEpisodeNum) {
            withContext(Dispatchers.IO) {
                try {
                    val tmdbId = if (anilistId == 918) null else AniZipClient.getLongRunningTmdbId(anilistId)
                    val epsMap = if (tmdbId != null) {
                        AniZipClient.getTmdbAllEpisodes(tmdbId)
                    } else {
                        AniZipClient.getEpisodeMetadata(anilistId)
                    }

                    val total = if (epsMap.isNotEmpty()) epsMap.size else if (totalEpisodesIntent > 0) totalEpisodesIntent else 24
                    totalEpisodes = total

                    val list = (1..total).map { epNum ->
                        val epMeta = epsMap[epNum]
                        val rawCover = epMeta?.imageUrl?.takeIf { it.isNotBlank() } ?: showCoverUrl
                        YouTubeEpisodeItem(
                            number = epNum,
                            title = epMeta?.title?.takeIf { it.isNotBlank() } ?: "Episode $epNum",
                            imageUrl = rawCover,
                            airDate = epMeta?.airdate ?: ""
                        )
                    }
                    withContext(Dispatchers.Main) {
                        episodesList = list
                    }
                } catch (_: Exception) {
                    if (episodesList.isEmpty() && totalEpisodesIntent > 0) {
                        val fallbackList = (1..totalEpisodesIntent).map { epNum ->
                            YouTubeEpisodeItem(epNum, "Episode $epNum", showCoverUrl, "")
                        }
                        withContext(Dispatchers.Main) {
                            episodesList = fallbackList
                        }
                    }
                }
            }
        }

        // PiP Mode Rendering: when in Picture-in-Picture mode, hide all controls and render only video surface
        if (isPipState || isPipMode) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        playerViewInstance = this
                        this.player = player
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        applyVideoAspectRatioMode(this, resizeMode, player.videoSize)
                        subtitleView?.apply {
                            setApplyEmbeddedStyles(!isHardsub)
                            setApplyEmbeddedFontSizes(!isHardsub)
                            setBottomPaddingFraction(0.0f)
                            visibility = if (isCcEnabled) android.view.View.VISIBLE else android.view.View.GONE
                        }
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    playerViewInstance = view
                    applyVideoAspectRatioMode(view, resizeMode, player.videoSize)
                    if (view.player != player) {
                        view.player = player
                    }
                    val density = view.context.resources.displayMetrics.density
                    view.subtitleView?.let { subView ->
                        val lp = (subView.layoutParams as? android.widget.FrameLayout.LayoutParams)
                            ?: android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        val targetMargin = (8 * density).toInt()
                        if (lp.bottomMargin != targetMargin) {
                            lp.bottomMargin = targetMargin
                            subView.layoutParams = lp
                        }
                    }
                    view.subtitleView?.visibility = if (isCcEnabled && !isHardsub && selectedSubtitleLang != "Off") android.view.View.VISIBLE else android.view.View.GONE
                },
                modifier = Modifier.fillMaxSize()
            )
            return
        }

        // Gesture Detection Root Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(isLocked) {
                    if (isLocked) {
                        detectTapGestures(
                            onTap = {
                                controlsVisible = !controlsVisible
                                lastUserInteractionTime = System.currentTimeMillis()
                            }
                        )
                    } else {
                        detectTapGestures(
                            onPress = {
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    if (isHolding2x) {
                                        isHolding2x = false
                                        player.playbackParameters = PlaybackParameters(playbackSpeed)
                                    }
                                }
                            },
                            onTap = {
                                if (isHolding2x) {
                                    isHolding2x = false
                                    player.playbackParameters = PlaybackParameters(playbackSpeed)
                                } else {
                                    controlsVisible = !controlsVisible
                                    lastUserInteractionTime = System.currentTimeMillis()
                                }
                            },
                            onDoubleTap = { offset ->
                                if (isHolding2x) {
                                    isHolding2x = false
                                    player.playbackParameters = PlaybackParameters(playbackSpeed)
                                }
                                val screenWidth = size.width
                                if (offset.x < screenWidth * 0.35f) {
                                    player.seekTo(max(0L, player.currentPosition - 10000))
                                    doubleTapSeekFeedback = "-10s"
                                    coroutineScope.launch {
                                        delay(800)
                                        doubleTapSeekFeedback = null
                                    }
                                } else if (offset.x > screenWidth * 0.65f) {
                                    player.seekTo(min(duration, player.currentPosition + 10000))
                                    doubleTapSeekFeedback = "+10s"
                                    coroutineScope.launch {
                                        delay(800)
                                        doubleTapSeekFeedback = null
                                    }
                                }
                                lastUserInteractionTime = System.currentTimeMillis()
                            },
                            onLongPress = {
                                isHolding2x = true
                                player.playbackParameters = PlaybackParameters(2.0f)
                            }
                        )
                    }
                }
                .pointerInput(isLocked) {
                    if (!isLocked) {
                        detectDragGestures(
                            onDragStart = {
                                if (isHolding2x) {
                                    isHolding2x = false
                                    player.playbackParameters = PlaybackParameters(playbackSpeed)
                                }
                            },
                            onDragEnd = {
                                showVolumeIndicator = false
                                showBrightnessIndicator = false
                                if (isHolding2x) {
                                    isHolding2x = false
                                    player.playbackParameters = PlaybackParameters(playbackSpeed)
                                }
                            },
                            onDragCancel = {
                                showVolumeIndicator = false
                                showBrightnessIndicator = false
                                if (isHolding2x) {
                                    isHolding2x = false
                                    player.playbackParameters = PlaybackParameters(playbackSpeed)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val screenWidth = size.width
                                val isRightSide = change.position.x > screenWidth / 2f
                                val delta = -dragAmount.y / (size.height * 0.85f)

                                if (isRightSide) {
                                    showVolumeIndicator = true
                                    showBrightnessIndicator = false
                                    volumeProgress = (volumeProgress + delta).coerceIn(0f, 1f)
                                    val target = (volumeProgress * maxVolume).roundToInt()
                                    if (target != currentVolume) {
                                        currentVolume = target
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                    }
                                } else {
                                    showBrightnessIndicator = true
                                    showVolumeIndicator = false
                                    val target = (currentBrightness + delta * 1.1f).coerceIn(0.05f, 1.0f)
                                    currentBrightness = target
                                    (context as? Activity)?.window?.let { win ->
                                        val lp = win.attributes
                                        lp.screenBrightness = target
                                        win.attributes = lp
                                    }
                                }
                                lastUserInteractionTime = System.currentTimeMillis()
                            }
                        )
                    }
                }
        ) {
            // Player Surface View (Directly bound to ExoPlayer with transparent shutter for instant video rendering)
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        playerViewInstance = this
                        this.player = player
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        applyVideoAspectRatioMode(this, resizeMode, player.videoSize)
                        subtitleView?.apply {
                            setApplyEmbeddedStyles(!isHardsub)
                            setApplyEmbeddedFontSizes(!isHardsub)
                            setBottomPaddingFraction(0.0f)
                            visibility = if (isCcEnabled) android.view.View.VISIBLE else android.view.View.GONE
                        }
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    playerViewInstance = view
                    applyVideoAspectRatioMode(view, resizeMode, player.videoSize)
                    if (view.player != player) {
                        view.player = player
                    }
                    val density = view.context.resources.displayMetrics.density
                    view.subtitleView?.let { subView ->
                        val lp = (subView.layoutParams as? android.widget.FrameLayout.LayoutParams)
                            ?: android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        val targetMargin = if (controlsVisible) (105 * density).toInt() else (35 * density).toInt()
                        if (lp.bottomMargin != targetMargin) {
                            lp.bottomMargin = targetMargin
                            subView.layoutParams = lp
                        }
                    }
                    view.subtitleView?.visibility = if (isCcEnabled && !isHardsub && selectedSubtitleLang != "Off") android.view.View.VISIBLE else android.view.View.GONE
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2X Fast Forward Top Pill Indicator
            if (isHolding2x) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x99000000))
                        .clickable {
                            isHolding2x = false
                            player.playbackParameters = PlaybackParameters(playbackSpeed)
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.yt_player_playback),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "2X Speed",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Brightness Overlay Pill
            if (showBrightnessIndicator) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.yt_player_brightness),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${(currentBrightness * 100).roundToInt()}%",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Volume Overlay Pill (Ultra Smooth Float Interpolation)
            if (showVolumeIndicator) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val iconRes = if (currentVolume == 0) R.drawable.yt_player_volume_off else R.drawable.yt_player_volume
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        val pct = (volumeProgress * 100).roundToInt()
                        Text(
                            text = "$pct%",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Double Tap Seek Feedback Pill
            doubleTapSeekFeedback?.let { feedback ->
                Box(
                    modifier = Modifier
                        .align(if (feedback.startsWith("-")) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 48.dp)
                        .clip(CircleShape)
                        .background(Color(0x73000000))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = feedback,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Lock Screen UI Overlay
            if (isLocked) {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x55000000))
                    ) {
                        YouTubePlayerGlassButton(
                            iconRes = R.drawable.yt_player_lock,
                            contentDescription = "Tap to unlock",
                            onClick = {
                                isLocked = false
                                controlsVisible = true
                                lastUserInteractionTime = System.currentTimeMillis()
                                Toast.makeText(context, "Screen unlocked", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 24.dp)
                        )
                    }
                }
            } else {
                // Main YouTube Style Controls Overlay
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x40000000))
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        // 1. TOP BAR
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Down Chevron / Back button (enters PiP mode)
                            IconButton(
                                onClick = { enterPipMode() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.yt_player_arrow_down),
                                    contentDescription = "PiP Mode",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Anime Title & Episode Subtitle
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = animeTitle,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Episode $currentEpisodeNum",
                                    color = Color(0xFFB0B0B8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }

                            // Autoplay Custom Switch (clean neutral pill with black play icon inside white thumb)
                            YouTubeAutoplaySwitch(
                                checked = autoplayNext,
                                onCheckedChange = { checked ->
                                    autoplayNext = checked
                                    AnimeBoxSettings.setAutoplayNextEpisodeEnabled(context, checked)
                                    lastUserInteractionTime = System.currentTimeMillis()
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Lock Screen Button (pure transparent icon, no dark circle background)
                            IconButton(
                                onClick = {
                                    isLocked = true
                                    controlsVisible = false
                                    Toast.makeText(context, "Screen locked", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.yt_player_lock),
                                    contentDescription = "Lock Screen",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Subtitle CC Button (pure transparent icon, no dark circle background)
                            IconButton(
                                onClick = {
                                    isCcEnabled = !isCcEnabled
                                    player.trackSelectionParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isCcEnabled)
                                        .setSelectUndeterminedTextLanguage(true)
                                        .build()
                                    if (!isCcEnabled) {
                                        selectedSubtitleLang = "Off"
                                        playerViewInstance?.subtitleView?.setCues(emptyList())
                                    } else {
                                        if (selectedSubtitleLang == "Off") {
                                            selectedSubtitleLang = "English"
                                        }
                                    }
                                    playerViewInstance?.subtitleView?.visibility = if (isCcEnabled) android.view.View.VISIBLE else android.view.View.GONE
                                    lastUserInteractionTime = System.currentTimeMillis()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = if (isCcEnabled) R.drawable.yt_player_closed_caption_filled else R.drawable.yt_player_closed_caption),
                                    contentDescription = "Subtitles",
                                    tint = if (isCcEnabled) Color.White else Color(0x99FFFFFF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Cast Button (pure transparent icon, no dark circle background)
                            IconButton(
                                onClick = {
                                    Toast.makeText(context, "Screen cast ready", Toast.LENGTH_SHORT).show()
                                    lastUserInteractionTime = System.currentTimeMillis()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.yt_player_screen_cast),
                                    contentDescription = "Cast",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Settings Gear Button (pure transparent icon, no dark circle background)
                            IconButton(
                                onClick = {
                                    showSettingsSheet = true
                                    lastUserInteractionTime = System.currentTimeMillis()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.yt_player_settings),
                                    contentDescription = "Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 2. CENTER CONTROLS (Prev - Play/Pause/Replay - Next)
                        // Hidden while increasing brightness or volume so they do not overlap
                        if (!showVolumeIndicator && !showBrightnessIndicator) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(40.dp)
                            ) {
                                // Previous Episode Button
                                YouTubePlayerGlassButton(
                                    iconRes = R.drawable.yt_player_prev,
                                    contentDescription = "Previous Episode",
                                    size = 48.dp,
                                    iconSize = 22.dp,
                                    enabled = currentEpisodeNum > 1,
                                    onClick = {
                                        switchToEpisode(currentEpisodeNum - 1)
                                        lastUserInteractionTime = System.currentTimeMillis()
                                    }
                                )

                                // Play / Pause / Replay Button
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x73000000))
                                        .clickable {
                                            if (player.playbackState == Player.STATE_ENDED) {
                                                player.seekTo(0)
                                                player.play()
                                            } else if (player.isPlaying) {
                                                player.pause()
                                            } else {
                                                player.play()
                                            }
                                            lastUserInteractionTime = System.currentTimeMillis()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isBuffering) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(32.dp),
                                            strokeWidth = 3.dp
                                        )
                                    } else {
                                        val iconRes = when {
                                            player.playbackState == Player.STATE_ENDED -> R.drawable.yt_player_replay
                                            isPlaying -> R.drawable.yt_player_pause
                                            else -> R.drawable.yt_player_play
                                        }
                                        Icon(
                                            painter = painterResource(id = iconRes),
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                // Next Episode Button
                                YouTubePlayerGlassButton(
                                    iconRes = R.drawable.yt_player_next,
                                    contentDescription = "Next Episode",
                                    size = 48.dp,
                                    iconSize = 22.dp,
                                    enabled = totalEpisodes == 0 || currentEpisodeNum < totalEpisodes,
                                    onClick = {
                                        switchToEpisode(currentEpisodeNum + 1)
                                        lastUserInteractionTime = System.currentTimeMillis()
                                    }
                                )
                            }
                        }

                        // 3. BOTTOM CONTROLS & TIMELINE (Exact Layout from Screenshots 1 & 2)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                        ) {
                            // ROW 1: Above Timeline (Time pill on left, Resize & Minimize/Exit on right)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Time Pill (00:09 / 23:40)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0x73000000))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Resize & Fullscreen Exit
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Skip Intro / Outro Pill
                                    val isIntro = introEndTime > introStartTime && currentPosition in introStartTime..introEndTime
                                    val isOutro = outroEndTime > outroStartTime && currentPosition in outroStartTime..outroEndTime
                                    if (isIntro || isOutro) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(Color(0x99000000))
                                                .clickable {
                                                    val target = if (isIntro) introEndTime + 500 else outroEndTime + 500
                                                    player.seekTo(target)
                                                    currentPosition = target
                                                    lastUserInteractionTime = System.currentTimeMillis()
                                                }
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (isIntro) "Skip Intro" else "Skip Outro",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = FastForwardPlayerIcon,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Content scale toggle
                                    YouTubePlayerGlassButton(
                                        iconRes = R.drawable.yt_player_resize,
                                        contentDescription = "Aspect Ratio",
                                        onClick = {
                                            resizeMode = (resizeMode + 1) % 4
                                            val modeName = when (resizeMode) {
                                                1 -> "Fill"
                                                2 -> "Zoom"
                                                3 -> "16:9"
                                                else -> "Fit"
                                            }
                                            Toast.makeText(context, "Video: $modeName", Toast.LENGTH_SHORT).show()
                                            applyVideoAspectRatioMode(playerViewInstance, resizeMode, player.videoSize)
                                            lastUserInteractionTime = System.currentTimeMillis()
                                        }
                                    )

                                    // Fullscreen exit button (acts strictly as player closing button)
                                    YouTubePlayerGlassButton(
                                        iconRes = R.drawable.yt_player_minimize,
                                        contentDescription = "Close Player",
                                        onClick = {
                                            (context as? Activity)?.finish()
                                        }
                                    )
                                }
                            }

                            // ROW 2: YouTube Red Seekbar with Scrubber Thumb & Highlighted Intro/Outro
                            YouTubeRedSeekbar(
                                currentPosition = currentPosition,
                                duration = duration,
                                bufferedPosition = bufferedPosition,
                                introStartTime = introStartTime,
                                introEndTime = introEndTime,
                                outroStartTime = outroStartTime,
                                outroEndTime = outroEndTime,
                                timelineColor = timelineColor,
                                onSeek = { targetMs ->
                                    player.seekTo(targetMs)
                                    lastUserInteractionTime = System.currentTimeMillis()
                                }
                            )

                            // ROW 3: Below Timeline (Like/Dislike pill on left, Episodes button on right)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Like / Dislike reaction pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0x73000000))
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                isLiked = !isLiked
                                                if (isLiked) isDisliked = false
                                                lastUserInteractionTime = System.currentTimeMillis()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = if (isLiked) R.drawable.yt_player_like else R.drawable.yt_player_like_outlined),
                                                contentDescription = "Like",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(16.dp)
                                                .background(Color(0x44FFFFFF))
                                        )
                                        IconButton(
                                            onClick = {
                                                isDisliked = !isDisliked
                                                if (isDisliked) isLiked = false
                                                lastUserInteractionTime = System.currentTimeMillis()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = if (isDisliked) R.drawable.yt_player_dislike else R.drawable.yt_player_dislike_outlined),
                                                contentDescription = "Dislike",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Episodes Drawer Button
                                YouTubePlayerGlassButton(
                                    iconRes = R.drawable.yt_player_episodes,
                                    contentDescription = "Episodes",
                                    onClick = {
                                        showEpisodesDrawer = !showEpisodesDrawer
                                        lastUserInteractionTime = System.currentTimeMillis()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 4. EPISODES SIDE DRAWER (With Colorful Glass Effect matching Screenshot 2)
            AnimatedVisibility(
                visible = showEpisodesDrawer,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(370.dp)
                    .align(Alignment.CenterEnd)
            ) {
                EpisodesSideDrawer(
                    animeTitle = animeTitle,
                    currentEpisode = currentEpisodeNum,
                    episodes = episodesList,
                    onClose = { showEpisodesDrawer = false },
                    onEpisodeClick = { targetEp ->
                        showEpisodesDrawer = false
                        if (targetEp == currentEpisodeNum) return@EpisodesSideDrawer
                        switchToEpisode(targetEp)
                    },
                    onDownloadEpisode = { _ ->
                        showEpisodesDrawer = false
                        showDownloadSheet = true
                    }
                )
            }

            // 5. SETTINGS BOTTOM SHEET (Screenshot 3 - Clean Neutral Toggles, No Red)
            if (showSettingsSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showSettingsSheet = false
                        currentSettingsTab = null
                    },
                    containerColor = Color(0xFF141416),
                    dragHandle = {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 10.dp)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF333338))
                        )
                    }
                ) {
                    when (currentSettingsTab) {
                        SettingsTab.QUALITY -> {
                            QualitySelectionSheet(
                                qualities = availableQualities,
                                selectedQuality = selectedQuality,
                                onQualitySelected = { q ->
                                    selectedQuality = q
                                    currentSettingsTab = null
                                    showSettingsSheet = false
                                }
                            )
                        }
                        SettingsTab.SPEED -> {
                            SpeedSelectionSheet(
                                currentSpeed = playbackSpeed,
                                onSpeedSelected = { spd ->
                                    playbackSpeed = spd
                                    isHolding2x = false
                                    player.playbackParameters = PlaybackParameters(spd)
                                    currentSettingsTab = null
                                    showSettingsSheet = false
                                }
                            )
                        }
                        SettingsTab.AUDIO -> {
                            AudioSelectionSheet(
                                currentAudio = selectedAudio,
                                isAdultAnime = isAdultAnime,
                                onAudioSelected = { aud ->
                                    selectedAudio = aud
                                    val newType = when (aud) {
                                        "Hindi" -> "hindi"
                                        "English" -> "dub"
                                        "French (VF)" -> "french"
                                        "Italian (ITA)" -> "italian"
                                        "Spanish (Latino)" -> "spanish_latino"
                                        "Spanish (Castellano)" -> "spanish_castellano"
                                        "German (Deutsch)" -> "german"
                                        else -> if (isAdultAnime) "hentai-jp" else "sub"
                                    }
                                    currentStreamType = newType
                                    AnimeBoxSettings.setLastUsedAudio(context, aud)
                                    currentSettingsTab = null
                                    showSettingsSheet = false

                                    // Reload server list for new audio category
                                    val sCategory = if (isAdultAnime) "hentai-jp" else if (newType.contains("dub")) "dub" else if (newType.contains("hindi")) "hindi" else "sub"
                                    val serverList = ServerPriorityManager.getServersForCategory(sCategory)
                                    availableServers = serverList
                                    val pref = ServerPriorityManager.getPreferredServer(context, sCategory)
                                    selectedServerName = serverList.find { it.key == pref }?.displayName ?: serverList.firstOrNull()?.displayName ?: "Auto Server"

                                    // Switch stream to the selected audio track
                                    showSettingsSheet = false
                                    reloadingMessage = "Switching Audio Track..."
                                    isReloadingStream = true
                                    coroutineScope.launch {
                                        val curPos = player.currentPosition
                                        val sInfo = withContext(Dispatchers.IO) {
                                            fetchStreamInfo(
                                                anilistId, currentEpisodeNum, newType, forceRefresh = true
                                            )
                                        }
                                        if (sInfo != null && sInfo["hls"] is String && (sInfo["hls"] as String).isNotEmpty()) {
                                            val newHls = sInfo["hls"] as String
                                            val newRef = (sInfo["referer"] as? String).orEmpty()
                                            val newSub = (sInfo["subtitle"] as? String).orEmpty()
                                            val newSubsJson = (sInfo["subtitlesJson"] as? String).orEmpty()
                                            if (newSubsJson.isNotBlank() && newSubsJson != "[]") {
                                                currentSubtitlesJson = newSubsJson
                                            }
                                            currentHlsUrl = newHls
                                            currentReferer = newRef
                                            currentSubtitleUrl = newSub
                                            introStartTime = (sInfo["introStart"] as? Long) ?: (sInfo["introStart"] as? Number)?.toLong() ?: 0L
                                            introEndTime = (sInfo["introEnd"] as? Long) ?: (sInfo["introEnd"] as? Number)?.toLong() ?: 0L
                                            outroStartTime = (sInfo["outroStart"] as? Long) ?: (sInfo["outroStart"] as? Number)?.toLong() ?: 0L
                                            outroEndTime = (sInfo["outroEnd"] as? Long) ?: (sInfo["outroEnd"] as? Number)?.toLong() ?: 0L

                                            // Subtitle auto-selection parity matching Player 1
                                            val isAudioNonJap = newType.contains("dub", ignoreCase = true) || !aud.contains("Japanese", ignoreCase = true)
                                            if (isNonJapSubOff && isAudioNonJap) {
                                                selectedSubtitleLang = "Off"
                                                isCcEnabled = false
                                            } else if (newSub.isNotBlank() || currentSubtitlesJson.length > 2) {
                                                if (selectedSubtitleLang == "Off") {
                                                    selectedSubtitleLang = "English"
                                                    isCcEnabled = true
                                                }
                                            }

                                            val mediaSource = buildMediaSource(newHls, newRef, newSub)
                                            player.setMediaSource(mediaSource)
                                            player.prepare()
                                            if (curPos > 0L) player.seekTo(curPos)
                                            player.playWhenReady = true
                                        } else {
                                            isReloadingStream = false
                                            val enabledServers = ServerPriorityManager
                                                .getServersForCategory(adultAwareServerCategory(context, newType))
                                                .filter { it.key != ServerPriorityManager.SERVER_AUTO }
                                                .map { it.key to it.displayName }

                                            fallbackDialogData = YouTubeFallbackDialogData(
                                                title = "$aud Audio Unavailable",
                                                description = "Could not load the $aud voice track for Episode $currentEpisodeNum. Would you like to switch to Japanese (Original) or report this episode?",
                                                primaryButtonText = "Switch to Japanese",
                                                serverOptions = enabledServers,
                                                onPrimaryAction = {
                                                    selectedAudio = "Japanese (Original)"
                                                    currentStreamType = if (isAdultAnime) "hentai-jp" else "sub"
                                                    reloadingMessage = "Switching Audio Track..."
                                                    isReloadingStream = true
                                                    coroutineScope.launch {
                                                        val subInfo = withContext(Dispatchers.IO) {
                                                            fetchStreamInfo(
                                                                anilistId, currentEpisodeNum, if (isAdultAnime) "hentai-jp" else "sub"
                                                            )
                                                        }
                                                        if (subInfo != null && subInfo["hls"] is String) {
                                                            val hls = subInfo["hls"] as String
                                                            val ref = (subInfo["referer"] as? String).orEmpty()
                                                            val sub = (subInfo["subtitle"] as? String).orEmpty()
                                                            val newSubsJson = (subInfo["subtitlesJson"] as? String).orEmpty()
                                                            if (newSubsJson.isNotBlank() && newSubsJson != "[]") {
                                                                currentSubtitlesJson = newSubsJson
                                                            }
                                                            currentHlsUrl = hls
                                                            currentReferer = ref
                                                            currentSubtitleUrl = sub
                                                            player.setMediaSource(buildMediaSource(hls, ref, sub))
                                                            player.prepare()
                                                            if (curPos > 0L) player.seekTo(curPos)
                                                            player.playWhenReady = true
                                                        } else {
                                                            isReloadingStream = false
                                                        }
                                                    }
                                                },
                                                onServerSelected = { sKey ->
                                                    fallbackDialogData = null
                                                    ServerPriorityManager.setPreferredServer(context, adultAwareServerCategory(context, newType), sKey)
                                                    selectedServerName = enabledServers.find { it.first == sKey }?.second ?: sKey
                                                    reloadingMessage = "Switching Server..."
                                                    isReloadingStream = true
                                                    coroutineScope.launch {
                                                        val srvInfo = withContext(Dispatchers.IO) {
                                                            fetchStreamInfo(
                                                                anilistId, currentEpisodeNum, newType, forceRefresh = true, targetServer = sKey
                                                            )
                                                        }
                                                        if (srvInfo != null && srvInfo["hls"] is String && (srvInfo["hls"] as String).isNotBlank()) {
                                                            val hls = srvInfo["hls"] as String
                                                            val ref = (srvInfo["referer"] as? String).orEmpty()
                                                            val sub = (srvInfo["subtitle"] as? String).orEmpty()
                                                            val newSubsJson = (srvInfo["subtitlesJson"] as? String).orEmpty()
                                                            if (newSubsJson.isNotBlank() && newSubsJson != "[]") {
                                                                currentSubtitlesJson = newSubsJson
                                                            }
                                                            currentHlsUrl = hls
                                                            currentReferer = ref
                                                            currentSubtitleUrl = sub
                                                            player.setMediaSource(buildMediaSource(hls, ref, sub))
                                                            player.prepare()
                                                            if (curPos > 0L) player.seekTo(curPos)
                                                            player.playWhenReady = true
                                                        } else {
                                                            isReloadingStream = false
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        SettingsTab.SUBTITLES -> {
                            SubtitleSelectionSheet(
                                currentSubtitlesJson = currentSubtitlesJson,
                                translatedSubMap = translatedSubMap,
                                selectedSubtitle = selectedSubtitleLang,
                                onSubtitleSelected = { lang ->
                                    selectedSubtitleLang = lang
                                    currentSettingsTab = null
                                    showSettingsSheet = false
                                    if (lang == "Off") {
                                        isCcEnabled = false
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .build()
                                        playerViewInstance?.subtitleView?.setCues(emptyList())
                                        playerViewInstance?.subtitleView?.visibility = android.view.View.GONE
                                        activeSubtitleText = ""
                                    } else {
                                        isCcEnabled = true
                                        val pathOrUrl = translatedSubMap[lang]
                                        if (!pathOrUrl.isNullOrBlank()) {
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                .build()
                                        } else {
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                .setSelectUndeterminedTextLanguage(true)
                                                .build()
                                            val foundUrl = findSubtitleUrlForLanguage(lang, currentSubtitlesJson)
                                            if (!foundUrl.isNullOrEmpty()) {
                                                currentSubtitleUrl = foundUrl
                                            }
                                        }
                                        playerViewInstance?.subtitleView?.visibility = if (!isHardsub) android.view.View.VISIBLE else android.view.View.INVISIBLE
                                    }
                                }
                            )
                        }
                        SettingsTab.SUBTITLE_APPEARANCE -> {
                            SubtitleAppearanceSheet(
                                currentPreset = subPreset,
                                currentFontSize = subFontSize,
                                currentTextColor = subTextColor,
                                onPresetSelected = { preset, fontSize, textColor, bgOpacity, edgeType, outlineWidth ->
                                    subPreset = preset
                                    subFontSize = fontSize
                                    subTextColor = textColor
                                    subBgOpacity = bgOpacity
                                    subEdgeType = edgeType
                                    subOutlineWidth = outlineWidth
                                    subPrefs.edit()
                                        .putString("subPreset", preset)
                                        .putFloat("subFontSize", fontSize)
                                        .putInt("subTextColor", textColor)
                                        .putInt("subBgOpacity", bgOpacity)
                                        .putInt("subEdgeType", edgeType)
                                        .putFloat("subOutlineWidth", outlineWidth)
                                        .apply()
                                    currentSettingsTab = null
                                    showSettingsSheet = false
                                    Toast.makeText(context, "$preset subtitle style applied", Toast.LENGTH_SHORT).show()
                                },
                                onFontSizeSelected = { size ->
                                    subFontSize = size
                                    subPrefs.edit().putFloat("subFontSize", size).apply()
                                    currentSettingsTab = null
                                    showSettingsSheet = false
                                },
                                onTextColorSelected = { color ->
                                    subTextColor = color
                                    subPrefs.edit().putInt("subTextColor", color).apply()
                                    currentSettingsTab = null
                                    showSettingsSheet = false
                                }
                            )
                        }
                        SettingsTab.SERVER -> {
                            ServerSelectionSheet(
                                servers = availableServers,
                                selectedServer = selectedServerName,
                                onServerSelected = { server: ServerOption ->
                                    selectedServerName = server.displayName
                                    val sCategory = if (isAdultAnime) {
                                        "hentai-jp"
                                    } else if (currentStreamType.contains("dub", ignoreCase = true)) {
                                        "dub"
                                    } else if (currentStreamType.contains("hindi", ignoreCase = true)) {
                                        "hindi"
                                    } else {
                                        "sub"
                                    }
                                    ServerPriorityManager.setPreferredServer(context, sCategory, server.key)
                                    currentSettingsTab = null
                                    showSettingsSheet = false

                                    // Reload stream with preferred server
                                    reloadingMessage = "Switching Server..."
                                    isReloadingStream = true
                                    coroutineScope.launch {
                                        val curPos = player.currentPosition
                                        val sInfo = withContext(Dispatchers.IO) {
                                            fetchStreamInfo(
                                                anilistId, currentEpisodeNum, if (isAdultAnime) "hentai-jp" else currentStreamType, forceRefresh = true, targetServer = server.key
                                            )
                                        }
                                        if (sInfo != null && sInfo["hls"] is String && (sInfo["hls"] as String).isNotEmpty()) {
                                            val newHls = sInfo["hls"] as String
                                            val newRef = (sInfo["referer"] as? String).orEmpty()
                                            val newSub = (sInfo["subtitle"] as? String).orEmpty()
                                            val newSubsJson = (sInfo["subtitlesJson"] as? String).orEmpty()
                                            if (newSubsJson.isNotBlank() && newSubsJson != "[]") {
                                                currentSubtitlesJson = newSubsJson
                                            }
                                            currentHlsUrl = newHls
                                            currentReferer = newRef
                                            currentSubtitleUrl = newSub
                                            val mediaSource = buildMediaSource(newHls, newRef, newSub)
                                            player.setMediaSource(mediaSource)
                                            player.prepare()
                                            if (curPos > 0L) player.seekTo(curPos)
                                            player.playWhenReady = true
                                        } else {
                                            isReloadingStream = false
                                        }
                                    }
                                }
                            )
                        }
                        SettingsTab.ANIME4K -> {
                            Anime4KSelectionSheet(
                                isEnabled = isEnhancerEnabled,
                                currentPreset = selectedEnhancerPreset,
                                onPresetSelected = { preset, enabled ->
                                    isEnhancerEnabled = enabled
                                    if (preset != null) {
                                        selectedEnhancerPreset = preset
                                        AnimeBoxSettings.setDefaultGraphicsEnhancerPreset(context, preset.id)
                                        Toast.makeText(context, "${preset.title} activated", Toast.LENGTH_SHORT).show()
                                    } else {
                                        AnimeBoxSettings.setDefaultGraphicsEnhancerPreset(context, "off")
                                        Toast.makeText(context, "Enhancer turned off", Toast.LENGTH_SHORT).show()
                                    }
                                    currentSettingsTab = null
                                    showSettingsSheet = false
                                }
                            )
                        }
                        SettingsTab.AUDIO_ENHANCER -> {
                            AudioEnhancerSelectionSheet(
                                isEnabled = isAudioEnhancerEnabled,
                                currentPreset = selectedAudioPreset,
                                onPresetSelected = { preset, enabled ->
                                    isAudioEnhancerEnabled = enabled
                                    audioEnhancerPrefs.edit().putBoolean(com.lagradost.cloudstream3.ui.animebox.enhancer.AnimeAudioEnhancer.KEY_ENABLED, enabled).apply()
                                    if (preset != null) {
                                        selectedAudioPreset = preset
                                        audioEnhancerPrefs.edit()
                                            .putString(com.lagradost.cloudstream3.ui.animebox.enhancer.AnimeAudioEnhancer.KEY_PRESET_ID, preset.id)
                                            .putFloat(com.lagradost.cloudstream3.ui.animebox.enhancer.AnimeAudioEnhancer.KEY_BASS, preset.defaultBass)
                                            .putFloat(com.lagradost.cloudstream3.ui.animebox.enhancer.AnimeAudioEnhancer.KEY_VOCAL, preset.defaultVocal)
                                            .putFloat(com.lagradost.cloudstream3.ui.animebox.enhancer.AnimeAudioEnhancer.KEY_TREBLE, preset.defaultTreble)
                                            .putFloat(com.lagradost.cloudstream3.ui.animebox.enhancer.AnimeAudioEnhancer.KEY_VIRTUALIZER, preset.defaultVirtualizer)
                                            .putFloat(com.lagradost.cloudstream3.ui.animebox.enhancer.AnimeAudioEnhancer.KEY_LOUDNESS, preset.defaultLoudness)
                                            .apply()
                                        Toast.makeText(context, "${preset.title} activated", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Audio enhancer disabled", Toast.LENGTH_SHORT).show()
                                    }
                                    currentSettingsTab = null
                                    showSettingsSheet = false
                                }
                            )
                        }
                        SettingsTab.MLKIT -> {
                            MlKitTranslationSheet(
                                currentLang = autoMlKitLang,
                                onLangSelected = { langCode ->
                                    autoMlKitLang = langCode
                                    AnimeBoxSettings.setAutomaticMlKitTranslationLanguage(context, langCode)
                                    currentSettingsTab = null
                                    showSettingsSheet = false
                                    if (langCode != "off" && currentSubtitleUrl.isNotEmpty()) {
                                        coroutineScope.launch {
                                            isTranslatingMlKit = true
                                            val langName = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.getLanguageName(langCode)
                                            val label = if (langCode == "en") "English (AI Translated)" else "$langName (AI Translated)"
                                            liveTranslatingStatus = "Translating to $langName..."
                                            try {
                                                val translated = com.lagradost.cloudstream3.ui.animebox.api.GoogleMlKitTranslatorManager.translateVttWithMlKit(
                                                    context = context,
                                                    vttUrlOrPath = currentSubtitleUrl,
                                                    targetLangCode = langCode,
                                                    hlsUrl = currentHlsUrl,
                                                    referer = currentReferer,
                                                    anilistId = anilistId,
                                                    episodeNum = currentEpisodeNum,
                                                    animeTitle = animeTitle,
                                                    coverUrl = showCoverUrl,
                                                    onProgress = { cur, total, tempPath, partialCues ->
                                                        liveTranslatingStatus = "Translating to $langName ($cur/$total)"
                                                        if (partialCues.isNotEmpty()) {
                                                            liveSubtitleCues = partialCues
                                                            currentSubtitleUrl = tempPath
                                                            selectedSubtitleLang = label
                                                            try {
                                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                                    .buildUpon()
                                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                                    .build()
                                                            } catch (_: Throwable) {}
                                                            isCcEnabled = true
                                                        }
                                                    }
                                                )
                                                if (translated != null && File(translated).exists()) {
                                                    currentSubtitleUrl = translated
                                                    val newMap = translatedSubMap.toMutableMap()
                                                    newMap[label] = translated
                                                    translatedSubMap = newMap
                                                    selectedSubtitleLang = label
                                                    isCcEnabled = true
                                                    try {
                                                        player.trackSelectionParameters = player.trackSelectionParameters
                                                            .buildUpon()
                                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                            .build()
                                                    } catch (_: Throwable) {}
                                                    val vttTxt = File(translated).readText()
                                                    if (vttTxt.isNotBlank()) {
                                                        val cues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(vttTxt)
                                                        liveSubtitleCues = cues.sortedBy { it.startMs }
                                                    }
                                                    Toast.makeText(context, "Subtitles translated to $langName!", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (t: Throwable) {
                                                t.printStackTrace()
                                            } finally {
                                                isTranslatingMlKit = false
                                                liveTranslatingStatus = null
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        else -> {
                            SettingsOverviewSheet(
                                selectedQuality = selectedQuality,
                                selectedAudio = selectedAudio,
                                selectedSubtitle = selectedSubtitleLang,
                                selectedServer = selectedServerName,
                                subPreset = subPreset,
                                subFontSize = subFontSize,
                                isEnhancerEnabled = isEnhancerEnabled,
                                selectedEnhancerPreset = selectedEnhancerPreset,
                                isAudioEnhancerEnabled = isAudioEnhancerEnabled,
                                selectedAudioPreset = selectedAudioPreset,
                                autoMlKitLang = autoMlKitLang,
                                playbackSpeed = playbackSpeed,
                                autoSkipIntro = autoSkipIntro,
                                autoplayNext = autoplayNext,
                                isGroqTranscribing = isGroqTranscribing,
                                groqProgressText = groqProgressText,
                                onTabSelect = { tab -> currentSettingsTab = tab },
                                onTriggerGroq = {
                                    showSettingsSheet = false
                                    val groqKey = AnimeBoxSettings.getGroqApiKey(context)
                                    if (groqKey.isBlank()) {
                                        showGroqApiKeyDialog = true
                                    } else {
                                        launchGroqTranscription(groqKey)
                                    }
                                },
                                onToggleAutoSkip = { checked ->
                                    autoSkipIntro = checked
                                    AnimeBoxSettings.setSkipIntroEnabled(context, checked)
                                },
                                onToggleAutoNext = { checked ->
                                    autoplayNext = checked
                                    AnimeBoxSettings.setAutoplayNextEpisodeEnabled(context, checked)
                                },
                                onOpenDownload = {
                                    showSettingsSheet = false
                                    showDownloadSheet = true
                                }
                            )
                        }
                    }
                }
            }

            // 6. DOWNLOAD SHEET (Without external/internal storage option, clean neutral accents)
            if (showDownloadSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showDownloadSheet = false },
                    containerColor = Color(0xFF141416),
                    dragHandle = {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 10.dp)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF333338))
                        )
                    }
                ) {
                    DownloadEpisodeSheetContent(
                        animeTitle = animeTitle,
                        episodeNum = currentEpisodeNum,
                        availableQualities = availableQualities.filter { it != "Auto" },
                        onStartDownload = { chosenQuality ->
                            showDownloadSheet = false
                            if (currentHlsUrl.isNotBlank()) {
                                AnimeDownloadManager.getInstance(context).enqueueDownload(
                                    anilistId = anilistId,
                                    animeTitle = animeTitle,
                                    episodeNum = currentEpisodeNum,
                                    episodeTitle = "Episode $currentEpisodeNum",
                                    coverUrl = showCoverUrl,
                                    showCoverUrl = showCoverUrl,
                                    quality = chosenQuality,
                                    resolvedUrl = currentHlsUrl,
                                    referer = currentReferer,
                                    subtitleUrl = currentSubtitleUrl
                                )
                                Toast.makeText(context, "Download started for Episode $currentEpisodeNum", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Stream URL not ready for download", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // Hardsub Preset Compose Subtitle Overlay (Matching Player 1 Subtitle Appearance with stroke)
            if (isHardsub && activeSubtitleText.isNotBlank() && isCcEnabled && selectedSubtitleLang != "Off") {
                val hardsubFontFamily = remember {
                    try { androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.trebuchet_ms_bold)) }
                    catch (_: Throwable) {
                        try { androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.productsans_bold)) }
                        catch (_: Throwable) { androidx.compose.ui.text.font.FontFamily.Default }
                    }
                }
                val targetBottomMargin = if (isPipState || isPipMode) 8.dp else if (controlsVisible) (105 + subVerticalOffsetDp).dp else (35 + subVerticalOffsetDp).dp
                val pipFontSizeSp = 11.sp
                val effectiveFontSize = if (isPipState || isPipMode) pipFontSizeSp else subFontSize.sp
                val effectiveStrokeWidth = if (isPipState || isPipMode) 6.0f else 13.5f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = targetBottomMargin)
                        .padding(horizontal = if (isPipState || isPipMode) 8.dp else 24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .padding(horizontal = if (isPipState || isPipMode) 4.dp else 6.dp, vertical = if (isPipState || isPipMode) 1.dp else 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 1. Vector Stroke Layer
                        Text(
                            text = activeSubtitleText,
                            color = Color.Black,
                            fontSize = effectiveFontSize,
                            fontFamily = hardsubFontFamily,
                            fontWeight = FontWeight.Black,
                            lineHeight = (effectiveFontSize.value * 1.30f).sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = androidx.compose.ui.text.TextStyle(
                                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = effectiveStrokeWidth,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        )
                        // 2. Pure White Text Layer
                        Text(
                            text = activeSubtitleText,
                            color = Color.White,
                            fontSize = effectiveFontSize,
                            fontFamily = hardsubFontFamily,
                            fontWeight = FontWeight.Black,
                            lineHeight = (effectiveFontSize.value * 1.30f).sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // ML Kit Subtitle Translation Floating Progress Indicator (Top Right - Parity with Player 1)
            if (liveTranslatingStatus != null && !isPipState && !isPipMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 16.dp, end = 20.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xE614141A))
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                color = Color(0xFFFF2222),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = liveTranslatingStatus ?: "",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Groq Transcribing Floating Progress Pill
            GroqTranscribingProgressPill(
                isGroqTranscribing = isGroqTranscribing,
                groqProgressText = groqProgressText,
                playerAccentColor = Color(0xFFFF2222)
            )

            // Groq Dialogs
            GroqApiKeyDialog(
                show = showGroqApiKeyDialog,
                onDismiss = { showGroqApiKeyDialog = false },
                onSaveKey = { key ->
                    AnimeBoxSettings.setGroqApiKey(context, key)
                    showGroqApiKeyDialog = false
                    launchGroqTranscription(key)
                }
            )
            GroqErrorDialog(
                show = showGroqErrorDialog,
                errorMessage = groqErrorMessage ?: "",
                onDismiss = { showGroqErrorDialog = false },
                onChangeKey = {
                    showGroqErrorDialog = false
                    showGroqApiKeyDialog = true
                }
            )

            // Match the outro action window: Next Episode appears at the top center (100% like Player 1)
            val pOutroStart = parseSkipTime(outroStartTime)
            val pOutroEnd = parseSkipTime(outroEndTime)
            val showSkipOutro = pOutroStart > 0 && pOutroEnd > pOutroStart && currentPosition in pOutroStart..pOutroEnd
            if (showSkipOutro && (totalEpisodes <= 0 || currentEpisodeNum < totalEpisodes)) {
                NextEpisodeSkipPrompt { switchToEpisode(currentEpisodeNum + 1) }
            }

            // Loading overlay for next episode & stream reloading (100% same as Player 1)
            if (isNextEpLoading || isReloadingStream || isEpisodesLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ThemeDotCircularProgressIndicator(
                            color = timelineColor,
                            size = 56.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = reloadingMessage,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Stream Fallback & Report Dialog (Ditto 100% same as Player 1)
            val currentFallback = fallbackDialogData
            if (currentFallback != null) {
                SmartFallbackDialogContent(
                    title = currentFallback.title,
                    description = currentFallback.description,
                    primaryButtonText = currentFallback.primaryButtonText,
                    onPrimaryAction = currentFallback.onPrimaryAction,
                    serverOptions = currentFallback.serverOptions,
                    onServerSelected = currentFallback.onServerSelected,
                    targetAnilistId = anilistId,
                    targetAnimeTitle = animeTitle,
                    targetCoverUrl = showCoverUrl,
                    targetEpisode = currentEpisodeNum,
                    streamCategory = currentStreamType,
                    onDismiss = { dontShowAgain ->
                        if (dontShowAgain) {
                            AnimeBoxSettings.setShowFallbackDialogsEnabled(context, false)
                        }
                        fallbackDialogData = null
                    }
                )
            }
        }
    }

    // --- REUSABLE GLASSY COMPONENTS ---

    @Composable
    private fun YouTubePlayerGlassButton(
        iconRes: Int,
        contentDescription: String?,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        size: androidx.compose.ui.unit.Dp = 40.dp,
        iconSize: androidx.compose.ui.unit.Dp = 20.dp,
        isPill: Boolean = false,
        enabled: Boolean = true
    ) {
        val shape = if (isPill) RoundedCornerShape(12.dp) else CircleShape
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(if (enabled) Color(0x73000000) else Color(0x33000000))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                tint = if (enabled) Color.White else Color(0x66FFFFFF),
                modifier = Modifier.size(iconSize)
            )
        }
    }

    @Composable
    private fun YouTubeAutoplaySwitch(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        val thumbOffset by animateDpAsState(
            targetValue = if (checked) 22.dp else 2.dp,
            animationSpec = tween(durationMillis = 200),
            label = "AutoplayThumb"
        )
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(26.dp)
                .clip(CircleShape)
                .background(if (checked) Color(0x88000000) else Color(0x44000000))
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.yt_player_play),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }

    private fun parseSkipTime(value: Long): Long = if (value in 1..9999L) value * 1000L else value

    @Composable
    private fun NextEpisodeSkipPrompt(onClick: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize()) {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color.White),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Next Episode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Image(
                        painter = painterResource(id = R.drawable.ic_player_005),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun SmartFallbackDialogContent(
        title: String,
        description: String,
        primaryButtonText: String,
        onPrimaryAction: () -> Unit,
        serverOptions: List<Pair<String, String>> = emptyList(),
        onServerSelected: ((String) -> Unit)? = null,
        targetAnilistId: Int = 0,
        targetAnimeTitle: String = "",
        targetCoverUrl: String = "",
        targetEpisode: Int = 1,
        streamCategory: String = "sub",
        onDismiss: (dontShowAgain: Boolean) -> Unit
    ) {
        val context = LocalContext.current
        val playerAccentColor = AnimeBoxSettings.getYouTubePlayerTimelineColor(context)
        var dontShowAgain by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .clickable { onDismiss(dontShowAgain) },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 24.dp)
                    .clickable(enabled = false) {}
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = description,
                    color = Color(0xFFCCCCCC),
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            onDismiss(dontShowAgain)
                            onPrimaryAction()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = playerAccentColor,
                            contentColor = Color(0xFF141218)
                        ),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text(
                            text = primaryButtonText,
                            color = Color(0xFF141218),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Button(
                        onClick = {
                            onDismiss(dontShowAgain)
                            val act = (context as? Activity)
                            val repIntent = Intent(context, AnimeBoxDetailActivity::class.java).apply {
                                putExtra("anilistId", targetAnilistId)
                                putExtra("initialTitle", targetAnimeTitle)
                                putExtra("initialCoverUrl", targetCoverUrl)
                                putExtra("openReportDialog", true)
                                putExtra("reportEpisode", targetEpisode)
                                putExtra("reportAnimeTitle", targetAnimeTitle)
                            }
                            context.startActivity(repIntent)
                            act?.finish()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF23222A),
                            contentColor = Color(0xFFE6E1E5)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text(
                            text = "Report Issue",
                            color = Color(0xFFE6E1E5),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }

                // Server Switching Tags
                if (serverOptions.isNotEmpty() && onServerSelected != null) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Switch Server",
                        color = Color(0xFF9E9EA6),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val currentPrefServer = remember {
                        ServerPriorityManager.getPreferredServer(
                            context,
                            adultAwareServerCategory(context, streamCategory)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        serverOptions.forEach { (key, displayName) ->
                            val isActive = key.equals(currentPrefServer, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isActive) playerAccentColor.copy(alpha = 0.22f) else Color(0xFF26252C))
                                    .clickable { onServerSelected(key) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = displayName,
                                    color = if (isActive) playerAccentColor else Color(0xFFD0D0D8),
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { dontShowAgain = !dontShowAgain }
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF6366F1),
                            checkmarkColor = Color.White,
                            uncheckedColor = Color.Gray
                        ),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Don't show this message again",
                        color = Color(0xFF888888),
                        fontSize = 11.5.sp
                    )
                }
            }
        }
    }

    @Composable
    private fun YouTubeRedSeekbar(
        currentPosition: Long,
        duration: Long,
        bufferedPosition: Long,
        introStartTime: Long = 0L,
        introEndTime: Long = 0L,
        outroStartTime: Long = 0L,
        outroEndTime: Long = 0L,
        timelineColor: Color = Color(0xFFE50914),
        onSeek: (Long) -> Unit
    ) {
        var isDragging by remember { mutableStateOf(false) }
        var dragProgress by remember { mutableFloatStateOf(0f) }

        val progress = if (isDragging) dragProgress else if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val bufferProgress = if (duration > 0) (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f

        val pIntroStart = parseSkipTime(introStartTime)
        val pIntroEnd = parseSkipTime(introEndTime)
        val pOutroStart = parseSkipTime(outroStartTime)
        val pOutroEnd = parseSkipTime(outroEndTime)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        val targetFrac = (offset.x / size.width).coerceIn(0f, 1f)
                        val targetMs = (targetFrac * duration).toLong()
                        onSeek(targetMs)
                    }
                }
                .pointerInput(duration) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            val targetMs = (dragProgress * duration).toLong()
                            onSeek(targetMs)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                val width = size.width
                val centerY = size.height / 2f
                val strokePx = 3.dp.toPx()

                // 1. Track Background (Translucent White)
                drawLine(
                    color = Color(0x44FFFFFF),
                    start = androidx.compose.ui.geometry.Offset(0f, centerY),
                    end = androidx.compose.ui.geometry.Offset(width, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )

                // 2. Buffered Progress
                if (bufferProgress > 0f) {
                    drawLine(
                        color = Color(0x88FFFFFF),
                        start = androidx.compose.ui.geometry.Offset(0f, centerY),
                        end = androidx.compose.ui.geometry.Offset(width * bufferProgress, centerY),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }

                // 3. Highlighted Skip Intro Segment (Matching Player 1)
                if (duration > 0 && pIntroEnd > pIntroStart && pIntroStart in 0..duration) {
                    val sX = (pIntroStart.toFloat() / duration.toFloat()).coerceIn(0f, 1f) * width
                    val eX = (pIntroEnd.toFloat() / duration.toFloat()).coerceIn(0f, 1f) * width
                    if (eX > sX) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.55f),
                            start = androidx.compose.ui.geometry.Offset(sX, centerY),
                            end = androidx.compose.ui.geometry.Offset(eX, centerY),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Butt
                        )
                    }
                }

                // 4. Highlighted Skip Outro Segment (Matching Player 1)
                if (duration > 0 && pOutroEnd > pOutroStart && pOutroStart in 0..duration) {
                    val sX = (pOutroStart.toFloat() / duration.toFloat()).coerceIn(0f, 1f) * width
                    val eX = (pOutroEnd.toFloat() / duration.toFloat()).coerceIn(0f, 1f) * width
                    if (eX > sX) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.55f),
                            start = androidx.compose.ui.geometry.Offset(sX, centerY),
                            end = androidx.compose.ui.geometry.Offset(eX, centerY),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Butt
                        )
                    }
                }

                // 5. Elapsed Played Progress (Dedicated Timeline Color)
                if (progress > 0f) {
                    drawLine(
                        color = timelineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, centerY),
                        end = androidx.compose.ui.geometry.Offset(width * progress, centerY),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }

                // 6. Scrubber Thumb Circle (Dedicated Timeline Color)
                val thumbRadius = if (isDragging) 8.dp.toPx() else 6.dp.toPx()
                val thumbX = (progress * width).coerceIn(0f, width)
                drawCircle(
                    color = timelineColor,
                    radius = thumbRadius,
                    center = androidx.compose.ui.geometry.Offset(thumbX, centerY)
                )
            }
        }
    }

    // --- EPISODES SIDE DRAWER WITH COLORFUL GLASS EFFECT ---

    data class YouTubeEpisodeItem(
        val number: Int,
        val title: String,
        val imageUrl: String,
        val airDate: String
    )

    @Composable
    private fun EpisodesSideDrawer(
        animeTitle: String,
        currentEpisode: Int,
        episodes: List<YouTubeEpisodeItem>,
        onClose: () -> Unit,
        onEpisodeClick: (Int) -> Unit,
        onDownloadEpisode: (Int) -> Unit
    ) {
        var isNewestFirst by remember { mutableStateOf(false) }
        val displayedList = remember(episodes, isNewestFirst) {
            if (isNewestFirst) episodes.reversed() else episodes
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0x00000000),
                            Color(0xD910121A),
                            Color(0xF5131520)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header (Title, Subtitle, Close)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Episodes",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = animeTitle,
                            color = Color(0xFFB0B0B8),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(id = R.drawable.yt_player_close),
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Oldest / Newest Filter Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EpisodeFilterPill(
                        label = "Oldest",
                        isSelected = !isNewestFirst,
                        onClick = { isNewestFirst = false }
                    )
                    EpisodeFilterPill(
                        label = "Newest",
                        isSelected = isNewestFirst,
                        onClick = { isNewestFirst = true }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Episodes List with Colorful Translucent Glass Cards
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedList, key = { it.number }) { ep ->
                        val isCurrent = ep.number == currentEpisode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCurrent) Color(0x35FFFFFF) else Color(0x221E202B))
                                .clickable { onEpisodeClick(ep.number) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 16:9 Thumbnail with EP Badge
                            Box(
                                modifier = Modifier
                                    .width(114.dp)
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF222226))
                            ) {
                                val imagePainter = rememberAsyncImagePainter(model = ep.imageUrl)
                                Image(
                                    painter = imagePainter,
                                    contentDescription = ep.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xCC000000))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "EP ${ep.number}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Title & Info
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = ep.title,
                                    color = if (isCurrent) Color(0xFFFFFFFF) else Color(0xFFE2E2E8),
                                    fontSize = 13.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (ep.airDate.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = ep.airDate,
                                        color = Color(0xFF8E8E93),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // 3-dots more menu
                            IconButton(
                                onClick = { onDownloadEpisode(ep.number) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.yt_player_more_vertical),
                                    contentDescription = "Options",
                                    tint = Color(0xFF8E8E93),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EpisodeFilterPill(
        label: String,
        isSelected: Boolean,
        onClick: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) Color.White else Color(0x35FFFFFF))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }

    // --- SETTINGS SHEETS (Clean Neutral Styling, No Red) ---

    private enum class SettingsTab {
        QUALITY, SPEED, AUDIO, SUBTITLES, SUBTITLE_APPEARANCE, SERVER, ANIME4K, AUDIO_ENHANCER, MLKIT
    }

    @Composable
    private fun SettingsOverviewSheet(
        selectedQuality: String,
        selectedAudio: String,
        selectedSubtitle: String,
        selectedServer: String,
        subPreset: String,
        subFontSize: Float,
        isEnhancerEnabled: Boolean,
        selectedEnhancerPreset: com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset,
        isAudioEnhancerEnabled: Boolean,
        selectedAudioPreset: com.lagradost.cloudstream3.ui.animebox.enhancer.AudioEnhancerPreset,
        autoMlKitLang: String,
        playbackSpeed: Float,
        autoSkipIntro: Boolean,
        autoplayNext: Boolean,
        isGroqTranscribing: Boolean,
        groqProgressText: String,
        onTabSelect: (SettingsTab) -> Unit,
        onTriggerGroq: () -> Unit,
        onToggleAutoSkip: (Boolean) -> Unit,
        onToggleAutoNext: (Boolean) -> Unit,
        onOpenDownload: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            SettingsSheetItem(
                iconRes = R.drawable.yt_player_quality,
                title = "Quality",
                subtitle = selectedQuality,
                onClick = { onTabSelect(SettingsTab.QUALITY) }
            )
            SettingsSheetItem(
                iconRes = R.drawable.yt_player_audio,
                title = "Audio track",
                subtitle = selectedAudio,
                onClick = { onTabSelect(SettingsTab.AUDIO) }
            )
            SettingsSheetItem(
                iconRes = R.drawable.yt_player_closed_caption,
                title = "Subtitles",
                subtitle = selectedSubtitle,
                onClick = { onTabSelect(SettingsTab.SUBTITLES) }
            )
            SettingsSheetItem(
                imageVector = CustomSubVector,
                title = "Subtitle appearance",
                subtitle = "$subPreset (${subFontSize.toInt()}sp)",
                onClick = { onTabSelect(SettingsTab.SUBTITLE_APPEARANCE) }
            )
            SettingsSheetItem(
                iconRes = R.drawable.ic_groq_logo,
                title = "Cloud AI Transcribe (Groq)",
                subtitle = if (isGroqTranscribing) groqProgressText.ifBlank { "Transcribing..." } else "Whisper AI",
                onClick = onTriggerGroq
            )
            SettingsSheetItem(
                iconRes = R.drawable.yt_player_server,
                title = "Server",
                subtitle = selectedServer,
                onClick = { onTabSelect(SettingsTab.SERVER) }
            )
            SettingsSheetItem(
                imageVector = SolarFourKLinearIcon,
                title = "Anime 4K / Enhancer",
                subtitle = if (isEnhancerEnabled) selectedEnhancerPreset.title else "Off",
                onClick = { onTabSelect(SettingsTab.ANIME4K) }
            )
            SettingsSheetItem(
                imageVector = SolarSmartSpeakerBoldIcon,
                title = "Audio Enhancer",
                subtitle = if (isAudioEnhancerEnabled) selectedAudioPreset.title else "Off",
                onClick = { onTabSelect(SettingsTab.AUDIO_ENHANCER) }
            )
            SettingsSheetItem(
                iconRes = R.drawable.yt_player_closed_caption_filled,
                title = "Auto ML Kit Translation",
                subtitle = if (autoMlKitLang.isNotBlank() && autoMlKitLang != "off") autoMlKitLang.uppercase() else "Off",
                onClick = { onTabSelect(SettingsTab.MLKIT) }
            )
            SettingsSheetItem(
                iconRes = R.drawable.yt_player_download,
                title = "Download",
                subtitle = "Available",
                onClick = onOpenDownload
            )
            SettingsSheetItem(
                iconRes = R.drawable.yt_player_playback,
                title = "Playback speed",
                subtitle = if (playbackSpeed == 1.0f) "1.0x" else "${playbackSpeed}x",
                onClick = { onTabSelect(SettingsTab.SPEED) }
            )
            SettingsSheetToggleItem(
                imageVector = FastForwardPlayerIcon,
                title = "Auto Skip",
                checked = autoSkipIntro,
                onCheckedChange = onToggleAutoSkip
            )
            SettingsSheetToggleItem(
                iconRes = R.drawable.yt_player_next,
                title = "Auto Next",
                checked = autoplayNext,
                onCheckedChange = onToggleAutoNext
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun SubtitleAppearanceSheet(
        currentPreset: String,
        currentFontSize: Float,
        currentTextColor: Int,
        onPresetSelected: (preset: String, fontSize: Float, textColor: Int, bgOpacity: Int, edgeType: Int, outlineWidth: Float) -> Unit,
        onFontSizeSelected: (Float) -> Unit,
        onTextColorSelected: (Int) -> Unit
    ) {
        val presets = listOf("Default", "Hardsub", "Yellow", "Cinema", "Box", "Lavender")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Subtitle Appearance",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "Style Preset",
                color = Color(0xFF8E8E93),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            presets.forEach { p ->
                val isSel = currentPreset.equals(p, ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            when (p) {
                                "Default" -> onPresetSelected("Default", 24f, android.graphics.Color.WHITE, 128, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, 12f)
                                "Hardsub" -> onPresetSelected("Hardsub", 24f, android.graphics.Color.WHITE, 0, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, 14f)
                                "Yellow" -> onPresetSelected("Yellow", 24f, android.graphics.Color.YELLOW, 0, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, 12f)
                                "Cinema" -> onPresetSelected("Cinema", 24f, android.graphics.Color.WHITE, 76, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, 0f)
                                "Box" -> onPresetSelected("Box", 24f, android.graphics.Color.WHITE, 178, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE, 0f)
                                "Lavender" -> onPresetSelected("Lavender", 24f, android.graphics.Color.rgb(208, 188, 255), 0, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, 12f)
                            }
                        }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = p,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSel) {
                        Icon(
                            painter = painterResource(id = R.drawable.yt_player_tick),
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Font Size",
                color = Color(0xFF8E8E93),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val fontSizes = listOf(
                18f to "Small (18sp)",
                22f to "Medium (22sp)",
                24f to "Normal (24sp)",
                28f to "Large (28sp)",
                32f to "Extra Large (32sp)"
            )
            fontSizes.forEach { (sz, label) ->
                val isSel = currentFontSize == sz
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onFontSizeSelected(sz) }
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSel) {
                        Icon(
                            painter = painterResource(id = R.drawable.yt_player_tick),
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun AudioEnhancerSelectionSheet(
        isEnabled: Boolean,
        currentPreset: com.lagradost.cloudstream3.ui.animebox.enhancer.AudioEnhancerPreset,
        onPresetSelected: (preset: com.lagradost.cloudstream3.ui.animebox.enhancer.AudioEnhancerPreset?, enabled: Boolean) -> Unit
    ) {
        val presets = com.lagradost.cloudstream3.ui.animebox.enhancer.AudioEnhancerPreset.values()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Audio Enhancer",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Off / Bypass option
            val isOffSel = !isEnabled
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPresetSelected(null, false) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Off (Hardware Bypass)",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (isOffSel) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = "Original stereo audio without hardware equalization",
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp
                    )
                }
                if (isOffSel) {
                    Icon(
                        painter = painterResource(id = R.drawable.yt_player_tick),
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            for (p in presets) {
                val isSel = isEnabled && p == currentPreset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPresetSelected(p, true) }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = p.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                        if (p.subtitle.isNotBlank()) {
                            Text(
                                text = p.subtitle,
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (isSel) {
                        Icon(
                            painter = painterResource(id = R.drawable.yt_player_tick),
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun Anime4KSelectionSheet(
        isEnabled: Boolean,
        currentPreset: com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset,
        onPresetSelected: (preset: com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset?, enabled: Boolean) -> Unit
    ) {
        val presets = com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.values()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Graphic Enhancer / Anime 4K",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Off option
            val isOffSel = !isEnabled
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPresetSelected(null, false) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Off (Original Video)",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (isOffSel) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = "Standard video playback without GPU enhancement",
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp
                    )
                }
                if (isOffSel) {
                    Icon(
                        painter = painterResource(id = R.drawable.yt_player_tick),
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            for (p in presets) {
                val isSel = isEnabled && p == currentPreset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPresetSelected(p, true) }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = p.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                        if (p.subtitle.isNotBlank()) {
                            Text(
                                text = p.subtitle,
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (isSel) {
                        Icon(
                            painter = painterResource(id = R.drawable.yt_player_tick),
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun MlKitTranslationSheet(
        currentLang: String,
        onLangSelected: (String) -> Unit
    ) {
        val languages = listOf(
            "off" to "Off (Disabled)",
            "en" to "English",
            "es" to "Spanish",
            "hi" to "Hindi",
            "fr" to "French",
            "de" to "German",
            "it" to "Italian",
            "pt" to "Portuguese",
            "ru" to "Russian",
            "ja" to "Japanese"
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Auto ML Kit Translation",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            for ((code, name) in languages) {
                val isSel = code.equals(currentLang, ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLangSelected(code) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSel) {
                        Icon(
                            painter = painterResource(id = R.drawable.yt_player_tick),
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun SettingsSheetItem(
        iconRes: Int = 0,
        imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageVector != null) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = subtitle,
                color = Color(0xFFB0B0B8),
                fontSize = 14.sp
            )
        }
    }

    @Composable
    private fun SettingsSheetToggleItem(
        iconRes: Int = 0,
        imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
        title: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageVector != null) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4A4B53),
                    uncheckedThumbColor = Color(0xFF8E8E93),
                    uncheckedTrackColor = Color(0xFF23242A)
                )
            )
        }
    }

    @Composable
    private fun QualitySelectionSheet(
        qualities: List<String>,
        selectedQuality: String,
        onQualitySelected: (String) -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Select Quality",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            qualities.forEach { q ->
                val isSel = q == selectedQuality
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onQualitySelected(q) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = q,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSel) {
                        Icon(
                            painter = painterResource(id = R.drawable.yt_player_tick),
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun SpeedSelectionSheet(
        currentSpeed: Float,
        onSpeedSelected: (Float) -> Unit
    ) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Playback Speed",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            speeds.forEach { spd ->
                val isSel = spd == currentSpeed
                val label = if (spd == 1.0f) "Normal (1x)" else "${spd}x"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSpeedSelected(spd) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSel) {
                        Icon(
                            painter = painterResource(id = R.drawable.yt_player_tick),
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun AudioSelectionSheet(
        currentAudio: String,
        isAdultAnime: Boolean = false,
        onAudioSelected: (String) -> Unit
    ) {
        val allAudioTracks = listOf(
            "Japanese (Original)" to "Original broadcast Japanese voice track",
            "English" to "English dubbed voice track",
            "Hindi" to "Hindi dubbed voice track",
            "French (VF)" to "French dubbed voice track",
            "Italian (ITA)" to "Italian dubbed voice track",
            "Spanish (Latino)" to "Spanish Latino dubbed voice track",
            "Spanish (Castellano)" to "Spanish Castellano voice track",
            "German (Deutsch)" to "German dubbed voice track"
        )
        val audioTracks = allAudioTracks.filter { (track, _) ->
            if (isAdultAnime) {
                track.startsWith("Japanese", ignoreCase = true) || track.startsWith("English", ignoreCase = true)
            } else {
                com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isAudioLabelEnabled(track)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Audio Track",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            audioTracks.forEach { (aud, desc) ->
                val isSel = aud.equals(currentAudio, ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAudioSelected(aud) }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = aud,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = desc,
                            color = Color(0xFF9E9EA4),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    if (isSel) {
                        Icon(
                            painter = painterResource(id = R.drawable.yt_player_tick),
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun SubtitleSelectionSheet(
        currentSubtitlesJson: String,
        translatedSubMap: Map<String, String>,
        selectedSubtitle: String,
        onSubtitleSelected: (String) -> Unit
    ) {
        val subOptions = remember(currentSubtitlesJson, translatedSubMap) {
            val list = mutableListOf("Off")
            translatedSubMap.keys.forEach { k ->
                if (!list.contains(k)) list.add(k)
            }
            try {
                if (currentSubtitlesJson.isNotBlank() && currentSubtitlesJson != "[]") {
                    val arr = JSONArray(currentSubtitlesJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val lang = obj.optString("lang", "").ifBlank { obj.optString("label", "") }
                        if (lang.isNotBlank() && !list.contains(lang)) {
                            list.add(lang)
                        }
                    }
                }
            } catch (_: Exception) {}
            if (list.size == 1) {
                list.add("English")
            }
            list
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Subtitles",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            subOptions.forEach { sub ->
                val isSel = sub.equals(selectedSubtitle, ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSubtitleSelected(sub) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = sub,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSel) {
                        Icon(
                            painter = painterResource(id = R.drawable.yt_player_tick),
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun ServerSelectionSheet(
        servers: List<ServerOption>,
        selectedServer: String,
        onServerSelected: (ServerOption) -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Streaming Server",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            val list = if (servers.isEmpty()) {
                listOf(
                    ServerOption("auto", "Default Server", "Automatic best quality"),
                    ServerOption("hd1", "HD-1 (Fast)", "High speed stream"),
                    ServerOption("hd2", "HD-2 (Backup)", "Reliable stream")
                )
            } else servers

            for (srv in list) {
                val isSel = srv.displayName.equals(selectedServer, ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onServerSelected(srv) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = srv.displayName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                        if (srv.description.isNotBlank()) {
                            Text(
                                text = srv.description,
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (isSel) {
                        Icon(
                            painter = painterResource(id = R.drawable.yt_player_tick),
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun DownloadEpisodeSheetContent(
        animeTitle: String,
        episodeNum: Int,
        availableQualities: List<String>,
        onStartDownload: (String) -> Unit
    ) {
        var selectedQ by remember { mutableStateOf(availableQualities.firstOrNull() ?: "1080p") }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Download Episode $episodeNum",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = animeTitle,
                color = Color(0xFF8E8E93),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Quality",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                availableQualities.forEach { q ->
                    val isSel = q == selectedQ
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) Color.White else Color(0x33FFFFFF))
                            .clickable { selectedQ = q }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = q,
                            color = if (isSel) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onStartDownload(selectedQ) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.yt_player_download),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Download Now",
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun GroqTranscribingProgressPill(
        isGroqTranscribing: Boolean,
        groqProgressText: String,
        playerAccentColor: Color
    ) {
        if (isGroqTranscribing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    color = Color(0xEE141418),
                    shape = RoundedCornerShape(6.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_groq_logo),
                            contentDescription = "Groq Logo",
                            modifier = Modifier
                                .height(18.dp)
                                .width(50.dp),
                            contentScale = ContentScale.Fit
                        )
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            color = playerAccentColor,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = groqProgressText.ifBlank { "Transcribing with Groq Whisper..." },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun GroqApiKeyDialog(
        show: Boolean,
        onDismiss: () -> Unit,
        onSaveKey: (String) -> Unit
    ) {
        if (!show) return
        val context = LocalContext.current
        val initialKey = remember { AnimeBoxSettings.getGroqApiKey(context) }
        var keyInput by remember(initialKey) { mutableStateOf(initialKey) }

        Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val view = androidx.compose.ui.platform.LocalView.current
            DisposableEffect(view) {
                var parent = view.parent
                while (parent != null && parent !is DialogWindowProvider) {
                    parent = parent.parent
                }
                (parent as? DialogWindowProvider)?.window?.setWindowAnimations(0)
                onDispose {}
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .wrapContentHeight(),
                    color = Color(0xFF141418),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_groq_logo),
                                contentDescription = "Groq Logo",
                                modifier = Modifier.height(24.dp).width(64.dp),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                text = "Cloud AI Transcribe",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Enter your Groq API key to transcribe episode audio into clean subtitles using Whisper Large v3 directly in the cloud.",
                            color = Color(0xFFB0B0B8),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF222228))
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = keyInput,
                                onValueChange = { keyInput = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    if (keyInput.isEmpty()) {
                                        Text(
                                            text = "gsk_...",
                                            color = Color(0xFF6E6E7E),
                                            fontSize = 13.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Get your free API key at console.groq.com",
                            color = Color(0xFF7E7E90),
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Cancel", color = Color(0xFF8E8E98), fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val clean = keyInput.trim()
                                    if (clean.isNotBlank()) {
                                        onSaveKey(clean)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Save & Transcribe", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun GroqErrorDialog(
        show: Boolean,
        errorMessage: String,
        onDismiss: () -> Unit,
        onChangeKey: () -> Unit
    ) {
        if (!show) return
        Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .wrapContentHeight(),
                    color = Color(0xFF141418),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_groq_logo),
                                contentDescription = "Groq Logo",
                                modifier = Modifier.height(24.dp).width(64.dp),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                text = "Transcription Notice",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF222228))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = errorMessage.ifBlank { "An error occurred while calling Groq Whisper API." },
                                color = Color(0xFFFF9E9E),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Dismiss", color = Color(0xFF8E8E98), fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onDismiss()
                                    onChangeKey()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Change API Key", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isThumbnailSubtitleTrack(url: String?, label: String? = null): Boolean {
        if (url.isNullOrBlank()) return false
        val u = url.lowercase()
        val l = label?.lowercase().orEmpty()
        return u.contains("thumbnail") || u.contains("sprite") || u.contains("xywh=") ||
                l.contains("thumbnail") || l.contains("sprite")
    }

    private fun findSubtitleUrlForLanguage(subName: String, subtitlesJson: String): String? {
        if (subtitlesJson.isBlank() || subtitlesJson == "[]") return null
        return try {
            val cleanName = subName.replace("(VTT)", "").replace("(Soft Sub)", "").trim()
            val sArr = JSONArray(subtitlesJson)
            for (i in 0 until sArr.length()) {
                val o = sArr.optJSONObject(i) ?: continue
                val u = o.optString("url", o.optString("file", "")).trim()
                val l = o.optString("lang", o.optString("label", o.optString("language", o.optString("name", "")))).trim()
                if (u.isNotBlank()) {
                    if (l.equals(cleanName, ignoreCase = true) || l.equals(subName, ignoreCase = true)) {
                        return u
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val seconds = totalSec % 60
        val minutes = (totalSec / 60) % 60
        val hours = totalSec / 3600
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}

data class YouTubeFallbackDialogData(
    val title: String,
    val description: String,
    val primaryButtonText: String,
    val onPrimaryAction: () -> Unit,
    val serverOptions: List<Pair<String, String>> = emptyList(),
    val onServerSelected: ((String) -> Unit)? = null
)
