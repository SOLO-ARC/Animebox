@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lagradost.cloudstream3.ui.animebox

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import kotlin.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import org.json.JSONArray
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import coil3.compose.rememberAsyncImagePainter
import android.app.Activity
import android.content.ContextWrapper

data class PendingPublicSubData(
    val anilistId: Int,
    val episodeNum: Int,
    val animeTitle: String,
    val langCode: String,
    val langName: String,
    val vttContent: String,
    val uploaderName: String
)

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

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

data class PlayerRelatedAnime(
    val id: Int,
    val title: String,
    val coverUrl: String,
    val format: String
)

data class FallbackDialogData(
    val title: String,
    val description: String,
    val primaryButtonText: String,
    val onPrimaryAction: () -> Unit
)

val AiSparklesIcon: ImageVector
    get() = ImageVector.Builder(
        name = "AiSparkles",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White)
    ) {
        moveTo(19f, 9f)
        lineTo(20.25f, 6.25f)
        lineTo(23f, 5f)
        lineTo(20.25f, 3.75f)
        lineTo(19f, 1f)
        lineTo(17.75f, 3.75f)
        lineTo(15f, 5f)
        lineTo(17.75f, 6.25f)
        close()
        moveTo(9f, 18f)
        lineTo(6.5f, 12.5f)
        lineTo(1f, 10f)
        lineTo(6.5f, 7.5f)
        lineTo(9f, 2f)
        lineTo(11.5f, 7.5f)
        lineTo(17f, 10f)
        lineTo(11.5f, 12.5f)
        close()
        moveTo(19f, 15f)
        lineTo(17.75f, 17.75f)
        lineTo(15f, 19f)
        lineTo(17.75f, 20.25f)
        lineTo(19f, 23f)
        lineTo(20.25f, 20.25f)
        lineTo(23f, 19f)
        lineTo(20.25f, 17.75f)
        close()
    }.build()

val YtPopupDownloadIcon: ImageVector
    get() = ImageVector.Builder(
        name = "YtDownload",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(12f, 3.5f)
        lineTo(12f, 14.5f)
        moveTo(7.5f, 10.5f)
        lineTo(12f, 15f)
        lineTo(16.5f, 10.5f)
        moveTo(5f, 19.5f)
        horizontalLineTo(19f)
    }.build()

val YtPopupShareIcon: ImageVector
    get() = ImageVector.Builder(
        name = "YtShare",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(5f, 18.5f)
        curveTo(5f, 12.5f, 8.5f, 9.5f, 14f, 9.5f)
        moveTo(11.5f, 5.5f)
        lineTo(19f, 9.5f)
        lineTo(11.5f, 13.5f)
    }.build()

val YtPopupFlagIcon: ImageVector
    get() = ImageVector.Builder(
        name = "YtFlag",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(5f, 21f)
        lineTo(5f, 4f)
        lineTo(13.5f, 4f)
        lineTo(14f, 6.5f)
        lineTo(19.5f, 6.5f)
        lineTo(19.5f, 13.5f)
        lineTo(13.5f, 13.5f)
        lineTo(13f, 11f)
        lineTo(5f, 11f)
    }.build()

suspend fun translateVttSubtitle(context: Context, vttUrl: String, targetLangCode: String): String? = withContext(Dispatchers.IO) {
    try {
        if (vttUrl.isEmpty()) return@withContext null
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = okhttp3.Request.Builder().url(vttUrl).build()
        val rawVtt = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body?.string() ?: ""
        }
        if (rawVtt.isEmpty()) return@withContext null

        val lines = rawVtt.lines()
        val resultLines = lines.toMutableList()
        val cuesToTranslate = mutableListOf<Pair<Int, String>>()

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("WEBVTT") || line.contains("-->") || line.all { it.isDigit() } || line.startsWith("NOTE") || line.startsWith("STYLE")) {
                continue
            }
            cuesToTranslate.add(i to lines[i])
        }

        if (cuesToTranslate.isEmpty()) return@withContext null

        val chunkSize = 25
        for (chunk in cuesToTranslate.chunked(chunkSize)) {
            val combinedText = chunk.joinToString("\n") { it.second }
            val encoded = java.net.URLEncoder.encode(combinedText, "UTF-8")
            val transUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLangCode&dt=t&q=$encoded"
            val transReq = okhttp3.Request.Builder().url(transUrl).build()
            val transResponse = client.newCall(transReq).execute().use { res ->
                if (res.isSuccessful) res.body?.string() ?: "" else ""
            }
            if (transResponse.isNotEmpty()) {
                val jsonArr = org.json.JSONArray(transResponse)
                val sentencesArr = jsonArr.optJSONArray(0)
                if (sentencesArr != null) {
                    val sb = StringBuilder()
                    for (k in 0 until sentencesArr.length()) {
                        val item = sentencesArr.optJSONArray(k)
                        if (item != null) {
                            sb.append(item.optString(0, ""))
                        }
                    }
                    val transLines = sb.toString().split("\n")
                    for (idx in chunk.indices) {
                        if (idx < transLines.size && transLines[idx].isNotBlank()) {
                            val targetLineIndex = chunk[idx].first
                            resultLines[targetLineIndex] = transLines[idx]
                        }
                    }
                }
            }
        }

        val finalVtt = resultLines.joinToString("\n")
        val cacheFile = java.io.File(context.cacheDir, "sub_trans_${targetLangCode}_${System.currentTimeMillis()}.vtt")
        cacheFile.writeText(finalVtt)
        cacheFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

class AnimeBoxPlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var progressTracker: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    private fun getPrefs(): SharedPreferences =
        getSharedPreferences("AnimeBoxPlayerPrefs", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCurrentlyInPip = false

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

        val intentData = intent.data
        val localFilePathExtra = intent.getStringExtra("localFilePath") ?: ""
        val isExternalAction = intent.action == Intent.ACTION_VIEW || (intentData != null && intent.getStringExtra("hlsUrl").isNullOrEmpty())

        val hlsUrl = when {
            localFilePathExtra.isNotEmpty() -> localFilePathExtra
            intentData != null -> intentData.toString()
            else -> intent.getStringExtra("hlsUrl") ?: ""
        }
        val referer = intent.getStringExtra("referer") ?: ""
        val subtitleUrl = intent.getStringExtra("subtitleUrl") ?: ""
        val anilistId = intent.getIntExtra("anilistId", 0)
        val episodeNum = intent.getIntExtra("episode", 1)
        val defaultTitle = if (isExternalAction) {
            intentData?.lastPathSegment?.substringBeforeLast(".") ?: "Video Player"
        } else {
            "Anime Show"
        }
        val animeTitle = intent.getStringExtra("animeTitle") ?: defaultTitle
        val coverUrl = intent.getStringExtra("coverUrl") ?: ""
        val showCoverUrl = intent.getStringExtra("showCoverUrl") ?: coverUrl
        val totalEpisodes = if (com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getLongRunningTmdbId(anilistId) != null) {
            9999
        } else {
            intent.getIntExtra("totalEpisodes", if (isExternalAction || localFilePathExtra.isNotEmpty()) 1 else 0)
        }
        val streamType = intent.getStringExtra("streamType") ?: "sub"
        val fromContinueWatching = intent.getBooleanExtra("fromContinueWatching", false)

        setContent {
            val playerThemeColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.getPrimaryColor(this@AnimeBoxPlayerActivity)
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = playerThemeColor,
                    background = Color(0xFF000000),
                    surface = Color(0xFF16161A)
                ),
                typography = androidx.compose.material3.Typography().copy(
                    bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    bodySmall = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    titleSmall = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    labelLarge = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    labelMedium = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    labelSmall = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    headlineMedium = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    headlineSmall = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    displayLarge = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    displayMedium = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily),
                    displaySmall = androidx.compose.ui.text.TextStyle(fontFamily = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.MotoGoogleSansFontFamily)
                )
            ) {
                val isOfflineDownload = intent.getBooleanExtra("isOffline", false)
                val isExternalPlayback = (isExternalAction || anilistId <= 0) && !isOfflineDownload
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
                    fromContinueWatching = fromContinueWatching,
                    isExternalAction = isExternalPlayback
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
        fromContinueWatching: Boolean,
        isExternalAction: Boolean = false
    ) {
        val context = LocalContext.current
        val historyManager = remember { WatchHistoryManager(context) }
        val coroutineScope = rememberCoroutineScope()
        val prefs = remember { getPrefs() }

        fun canSaveHistory(targetAnilistId: Int): Boolean {
            return !isExternalAction && targetAnilistId > 0
        }

        // ─── Load persisted settings (Session retention or fresh default) ──────
        val activeAudioExtra = remember { (context as? android.app.Activity)?.intent?.getStringExtra("activeAudio") }
        val activeSubExtra = remember { (context as? android.app.Activity)?.intent?.getStringExtra("activeSub") }
        val savedAudio = remember { activeAudioExtra ?: "Japanese (Original)" }
        val savedSub = remember { activeSubExtra ?: "English (VTT)" }
        val savedFontSize = remember { prefs.getFloat("subFontSize", 24f) }
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

        val seekStepSeconds = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getSeekDuration(context) }
        var isInPipMode by remember { mutableStateOf(false) }
        var seekOverlayState by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
        var seekOverlayKey by remember { mutableIntStateOf(0) }

        LaunchedEffect(seekOverlayKey) {
            if (seekOverlayState != null) {
                kotlinx.coroutines.delay(700L)
                seekOverlayState = null
            }
        }

        DisposableEffect(Unit) {
            AnimeBoxPlayerActivity.onPipModeChanged = {
                isInPipMode = it
            }
            onDispose {
                AnimeBoxPlayerActivity.onPipModeChanged = null
            }
        }

        val appThemeSetting = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getAppTheme(context) }
        val timelineThemeSetting = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getPlayerTimelineTheme(context) }
        val customTimelineHex = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getCustomTimelineColor(context) }
        val playerAccentColor = remember(timelineThemeSetting, customTimelineHex, appThemeSetting) {
            when (timelineThemeSetting) {
                "light_red", "red" -> Color(0xFFFF5252)
                "cyan" -> Color(0xFF00E5FF)
                "gold", "amber" -> Color(0xFFFFAB00)
                "green", "emerald" -> Color(0xFF00E676)
                "white" -> Color(0xFFFFFFFF)
                "lavender" -> Color(0xFFD0BCFF)
                "custom" -> try { Color(android.graphics.Color.parseColor(if (customTimelineHex.startsWith("#")) customTimelineHex else "#$customTimelineHex")) } catch (_: Exception) { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getAppThemeColor(context) }
                else -> com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getAppThemeColor(context)
            }
        }

        val skipIntroBtnColor = Color.White

        val brightnessModeSetting = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getBrightnessMode(context) }
        val volumeModeSetting = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getVolumeMode(context) }
        val skipIntroEnabledSetting = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isSkipIntroEnabled(context) }

        // System audio manager & initial states
        val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager }
        val initialVolume = remember {
            if (audioManager != null) {
                val current = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                if (max > 0) current.toFloat() / max.toFloat() else 0.5f
            } else 0.5f
        }
        val initialBrightness = remember {
            val act = context.findActivity()
            val b = act?.window?.attributes?.screenBrightness ?: -1f
            if (b >= 0f) b else 0.5f
        }

        // Gesture Slider States
        var showBrightnessSlider by remember { mutableStateOf(false) }
        var brightnessValue by remember { mutableFloatStateOf(initialBrightness) }
        var showVolumeSlider by remember { mutableStateOf(false) }
        var volumeValue by remember { mutableFloatStateOf(initialVolume) }

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

        // ─── Current mutable episode & anime state ─────────
        var currentAnilistId by remember { mutableIntStateOf(anilistId) }
        var currentAnimeTitle by remember { mutableStateOf(animeTitle) }
        var currentShowCoverUrl by remember { mutableStateOf(showCoverUrl) }
        var currentEpisodeNum by remember { mutableStateOf(episodeNum) }
        var currentCoverUrl by remember { mutableStateOf(coverUrl) }
        var currentIntroStart by remember { mutableStateOf(introStart) }
        var currentIntroEnd   by remember { mutableStateOf(introEnd) }
        var currentOutroStart by remember { mutableStateOf(outroStart) }
        var currentOutroEnd   by remember { mutableStateOf(outroEnd) }

        var currentHlsUrl by remember { mutableStateOf(hlsUrl) }
        var currentReferer by remember { mutableStateOf(referer) }
        var currentSubtitleUrl by remember { mutableStateOf(subtitleUrl) }
        var currentProvider by remember { mutableStateOf((context as? android.app.Activity)?.intent?.getStringExtra("provider") ?: "") }
        val originalSubtitleUrl = remember(subtitleUrl, currentEpisodeNum) { subtitleUrl }
        var liveSubtitleCues by remember { mutableStateOf<List<com.lagradost.cloudstream3.ui.animebox.api.LiveSubtitleCue>>(emptyList()) }
        var showSaveToPublicCard by remember { mutableStateOf(false) }
        var pendingPublicSubInfo by remember { mutableStateOf<PendingPublicSubData?>(null) }
        var publicSubtitlesList by remember { mutableStateOf<List<com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitleItem>>(emptyList()) }

        LaunchedEffect(currentAnilistId, currentEpisodeNum) {
            liveSubtitleCues = emptyList()
            showSaveToPublicCard = false
            pendingPublicSubInfo = null
            if (currentAnilistId > 0 && currentEpisodeNum > 0) {
                val publicSubs = com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitlesManager.fetchPublicSubtitles(currentAnilistId, currentEpisodeNum)
                publicSubtitlesList = publicSubs
            }
            if (currentAnilistId > 0 && (currentShowCoverUrl.isBlank() || currentShowCoverUrl.contains("backdrop", ignoreCase = true) || currentShowCoverUrl.contains("banner", ignoreCase = true))) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val details = com.lagradost.cloudstream3.ui.animebox.api.AniListClient.getAnimeDetails(currentAnilistId)
                    if (details != null) {
                        try {
                            val media = org.json.JSONObject(details).getJSONObject("data").getJSONObject("Media")
                            val coverObj = media.optJSONObject("coverImage")
                            val p = coverObj?.optString("extraLarge", coverObj.optString("large", "")) ?: ""
                            if (p.isNotBlank()) {
                                currentShowCoverUrl = p
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        LaunchedEffect(showSaveToPublicCard, pendingPublicSubInfo) {
            if (showSaveToPublicCard && pendingPublicSubInfo != null) {
                delay(18000L)
                showSaveToPublicCard = false
            }
        }

        var currentBackupHls by remember { mutableStateOf((context as? android.app.Activity)?.intent?.getStringExtra("backupHls") ?: "") }
        var currentBackupProvider by remember { mutableStateOf((context as? android.app.Activity)?.intent?.getStringExtra("backupProvider") ?: "") }
        
        val showAudioFallbackWarningExtra = remember { (context as? android.app.Activity)?.intent?.getBooleanExtra("showAudioFallbackWarning", false) ?: false }
        val requestedAudioTypeExtra = remember { (context as? android.app.Activity)?.intent?.getStringExtra("requestedAudioType") ?: "" }

        val rememberedAudio = remember {
            if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context)) {
                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getLastUsedAudio(context)
            } else {
                savedAudio
            }
        }
        val rememberedSubMode = remember {
            if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context)) {
                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getLastUsedSubMode(context)
            } else {
                savedSub
            }
        }

        val initialStreamType = remember {
            if (showAudioFallbackWarningExtra) {
                "sub"
            } else if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context)) {
                if (rememberedSubMode == "Hard Sub") "hardsub"
                else if (rememberedAudio == "English") "dub"
                else if (rememberedAudio == "Hindi") "hindi"
                else "sub"
            } else if (activeAudioExtra != null) {
                streamType
            } else {
                if (streamType.isNotEmpty()) streamType else "sub"
            }
        }
        val isOfflineMode = remember {
            (context as? android.app.Activity)?.intent?.getBooleanExtra("isOffline", false) == true ||
            (context as? android.app.Activity)?.intent?.getStringExtra("localFilePath")?.isNotEmpty() == true ||
            (context as? android.app.Activity)?.intent?.action == Intent.ACTION_VIEW ||
            hlsUrl.startsWith("content://") || hlsUrl.startsWith("file://") || hlsUrl.startsWith("/")
        }
        var currentStreamType by remember { mutableStateOf(initialStreamType) }
        var subPreset by remember { mutableStateOf(prefs.getString("subPreset", "Default") ?: "Default") }
        var selectedSub by remember {
            mutableStateOf(
                if (isOfflineMode) {
                    if (currentStreamType == "hardsub") "Hard Sub" else "English (VTT)"
                } else if (currentStreamType == "hardsub" && subtitleUrl.isNotEmpty() && (referer.contains("animeapps", ignoreCase = true) || referer.contains("anibd", ignoreCase = true) || currentProvider.contains("anibd", ignoreCase = true) || currentProvider.contains("anidb", ignoreCase = true))) {
                    "English (Soft Sub)"
                } else if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context)) {
                    rememberedSubMode
                } else {
                    savedSub
                }
            )
        }
        var selectedAudio by remember {
            mutableStateOf(
                if (showAudioFallbackWarningExtra) {
                    "Japanese (Original)"
                } else if (activeAudioExtra != null) {
                    if (streamType == "dub") "English" else if (streamType == "hindi") "Hindi" else "Japanese (Original)"
                } else if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context)) {
                    if (currentStreamType == "sub" && rememberedAudio == "English") "Japanese (Original)" else rememberedAudio
                } else {
                    savedAudio
                }
            )
        }
        var isReloadingStream by remember { mutableStateOf(false) }
        var fallbackDialogData by remember { mutableStateOf<FallbackDialogData?>(null) }
        var showStreamReportPill by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (!isOfflineMode && showAudioFallbackWarningExtra && com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isShowFallbackDialogsEnabled(context)) {
                val reqLangName = if (requestedAudioTypeExtra == "hindi") "Hindi Dub" else if (requestedAudioTypeExtra == "dub") "English Dub" else "Hard Sub"
                fallbackDialogData = FallbackDialogData(
                    title = "$reqLangName Unavailable",
                    description = "$reqLangName is not available for this anime episode. Switched to original Japanese audio.",
                    primaryButtonText = "OK",
                    onPrimaryAction = {}
                )
            }
        }

        LaunchedEffect(currentHlsUrl) {
            if (!isOfflineMode && currentHlsUrl.isEmpty()) {
                isReloadingStream = true
                coroutineScope.launch {
                    val fbMap = fetchStreamInfo(anilistId, currentEpisodeNum, currentStreamType)
                    isReloadingStream = false
                    if (fbMap != null && (fbMap["hls"] as? String)?.isNotEmpty() == true) {
                        val fbHls = fbMap["hls"] as String
                        currentHlsUrl = fbHls
                        currentReferer = (fbMap["referer"] as? String) ?: ""
                        currentProvider = (fbMap["provider"] as? String) ?: ""
                        val fbSub = (fbMap["subtitle"] as? String) ?: ""
                        if (fbSub.isNotEmpty()) {
                            currentSubtitleUrl = fbSub
                        }
                    } else {
                        if (currentStreamType == "sub") {
                            fallbackDialogData = FallbackDialogData(
                                title = "Soft Sub Stream Unavailable",
                                description = "Soft subtitle stream could not be loaded for Episode $currentEpisodeNum. Would you like to switch to Hard Sub?",
                                primaryButtonText = "Switch to Hard Sub",
                                onPrimaryAction = {
                                    currentStreamType = "hardsub"
                                    isReloadingStream = true
                                    coroutineScope.launch {
                                        val hsMap = fetchStreamInfo(anilistId, currentEpisodeNum, "hardsub")
                                        isReloadingStream = false
                                        if (hsMap != null && (hsMap["hls"] as? String)?.isNotEmpty() == true) {
                                            currentHlsUrl = hsMap["hls"] as String
                                            currentReferer = (hsMap["referer"] as? String) ?: ""
                                            currentProvider = (hsMap["provider"] as? String) ?: ""
                                            val subUrl = (hsMap["subtitle"] as? String) ?: ""
                                            val isAniDb = currentProvider.contains("anibd", ignoreCase = true) || currentProvider.contains("anidb", ignoreCase = true) || currentReferer.contains("animeapps", ignoreCase = true) || currentReferer.contains("anibd", ignoreCase = true)
                                            if (isAniDb && subUrl.isNotEmpty()) {
                                                currentSubtitleUrl = subUrl
                                                subPreset = "Hardsub"
                                                prefs.edit().putString("subPreset", "Hardsub").apply()
                                                selectedSub = "English (Soft Sub)"
                                                prefs.edit().putString("selectedSub", "English (Soft Sub)").apply()
                                            } else {
                                                currentSubtitleUrl = ""
                                                selectedSub = "Hard Sub"
                                            }
                                        } else {
                                            android.widget.Toast.makeText(context, "Hard Sub stream not available", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        } else {
                            fallbackDialogData = FallbackDialogData(
                                title = "Anime not found or stream unavailable",
                                description = "Try changing the language, it may work.",
                                primaryButtonText = "Retry",
                                onPrimaryAction = {
                                    isReloadingStream = true
                                    coroutineScope.launch {
                                        val retryMap = fetchStreamInfo(anilistId, currentEpisodeNum, currentStreamType)
                                        isReloadingStream = false
                                        if (retryMap != null && (retryMap["hls"] as? String)?.isNotEmpty() == true) {
                                            currentHlsUrl = retryMap["hls"] as String
                                            currentReferer = (retryMap["referer"] as? String) ?: ""
                                            currentProvider = (retryMap["provider"] as? String) ?: ""
                                            val fbSub = (retryMap["subtitle"] as? String) ?: ""
                                            if (fbSub.isNotEmpty()) {
                                                currentSubtitleUrl = fbSub
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        showStreamReportPill = true
                    }
                }
            }
        }

        LaunchedEffect(currentHlsUrl, currentStreamType, currentProvider, currentSubtitleUrl) {
            val isAniDb = currentProvider.contains("anibd", ignoreCase = true) ||
                    currentProvider.contains("anidb", ignoreCase = true) ||
                    currentReferer.contains("animeapps", ignoreCase = true) ||
                    currentReferer.contains("anibd", ignoreCase = true)

            if (isAniDb && (currentStreamType == "hardsub" || selectedSub.contains("Hard Sub", ignoreCase = true)) && currentSubtitleUrl.isNotEmpty()) {
                if (!subPreset.equals("Hardsub", ignoreCase = true)) {
                    subPreset = "Hardsub"
                    prefs.edit().putString("subPreset", "Hardsub").apply()
                }
                if (selectedSub.contains("Hard Sub", ignoreCase = true) || selectedSub == "Off") {
                    selectedSub = "English (Soft Sub)"
                    prefs.edit().putString("selectedSub", "English (Soft Sub)").apply()
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
        var audioSubTab by remember { mutableIntStateOf(0) }
        var showSpeedQualityPanel by remember { mutableStateOf(false) }
        var speedQualityTab by remember { mutableIntStateOf(0) }
        var currentQuality by remember { mutableStateOf("Auto") }
        var isBuffering by remember { mutableStateOf(false) }
        var subFontSize by remember { mutableStateOf(savedFontSize) }
        var subTextColor by remember { mutableStateOf(savedTextColor) }
        var subBgOpacity by remember { mutableStateOf(savedBgOpacity) }
        var subEdgeType by remember { mutableIntStateOf(prefs.getInt("subEdgeType", androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE)) }
        var showSubtitleStyleSettings by remember { mutableStateOf(false) }
        var showCastDialog by remember { mutableStateOf(false) }

        LaunchedEffect(showCastDialog) {
            if (showCastDialog) {
                delay(7000L)
                showCastDialog = false
            }
        }
        var isExternalAction = remember { (context as? android.app.Activity)?.intent?.action == Intent.ACTION_VIEW }
        var playerViewInstance by remember { mutableStateOf<PlayerView?>(null) }

        val subtitlePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri: android.net.Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignore if takePersistableUriPermission is not supported for this provider
                }
                currentSubtitleUrl = uri.toString()
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Custom Subtitle"
                selectedSub = fileName
                android.widget.Toast.makeText(context, "Loaded subtitle: $fileName", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
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

        // ─── Special Thanking Banner State (Shin Chan & Pokémon) ─────────
        val isShinChanContent = remember(currentAnilistId, currentAnimeTitle) {
            com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(currentAnilistId, currentAnimeTitle)
        }
        val isPokemonContent = remember(currentAnilistId, currentAnimeTitle) {
            currentAnilistId == 527 || com.lagradost.cloudstream3.ui.animebox.extractors.PokemonHindiExtractor.isPokemon(currentAnilistId, currentAnimeTitle)
        }

        val isThankingAudioActive = when {
            isShinChanContent -> (selectedAudio.contains("Japanese", ignoreCase = true) || selectedSub.contains("Hard Sub", ignoreCase = true) || currentStreamType != "hindi")
            isPokemonContent -> (selectedAudio.equals("Hindi", ignoreCase = true) || currentStreamType == "hindi")
            else -> false
        }

        var showThankingBanner by remember { mutableStateOf(false) }
        var hasShownThankingForEpisode by remember { mutableStateOf<String?>(null) }

        // Trigger on playback start with matching audio and auto-hide in 5.5 to 6 seconds.
        // Seeking 10s forward/backward or pause/play will NOT re-trigger this.
        LaunchedEffect(currentEpisodeNum, isThankingAudioActive, isPlaying) {
            val sessionKey = "${currentEpisodeNum}_${isThankingAudioActive}"
            if (isPlaying && isThankingAudioActive) {
                if (hasShownThankingForEpisode != sessionKey) {
                    hasShownThankingForEpisode = sessionKey
                    showThankingBanner = true
                    delay(5800)
                    showThankingBanner = false
                }
            } else if (!isThankingAudioActive) {
                showThankingBanner = false
            }
        }

        val dubJsonStr = remember { (context as? android.app.Activity)?.intent?.getStringExtra("dubStreamsJson") ?: "" }
        var dubStreamsList by remember {
            mutableStateOf<List<Map<String, String>>>(
                try {
                    if (dubJsonStr.isNotEmpty()) {
                        val arr = org.json.JSONArray(dubJsonStr)
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
        var currentDubIndex by remember { mutableStateOf(0) }

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
        var relatedAnimeList by remember { mutableStateOf<List<PlayerRelatedAnime>>(emptyList()) }
        var animePosterUrl by remember { mutableStateOf(showCoverUrl.ifEmpty { coverUrl }) }
        // Resize modes: 0=FIT, 1=FILL, 2=ZOOM
        var resizeMode by remember { mutableStateOf(0) }
        var showResizeToast by remember { mutableStateOf(false) }
        var resizeToastText by remember { mutableStateOf("") }

        var showMorePanel by remember { mutableStateOf(false) }

        // Dynamic Quality & AI Graphics Enhancer
        var videoTrackHeights by remember { mutableStateOf<List<Int>>(emptyList()) }
        var isGraphicsUpscalerEnabled by remember { mutableStateOf(prefs.getBoolean("graphics_upscaler_enabled", false)) }
        var enhancerPreset by remember {
            mutableStateOf(
                com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.fromId(
                    prefs.getString("enhancer_preset_id", com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.ULTRA_CLARITY.id) ?: "ultra_clarity"
                )
            )
        }
        var enhancerSharpness by remember { mutableFloatStateOf(prefs.getFloat("enhancer_sharpness", 0.20f)) }
        var enhancerContrast by remember { mutableFloatStateOf(prefs.getFloat("enhancer_contrast", 1.06f)) }
        var enhancerSaturation by remember { mutableFloatStateOf(prefs.getFloat("enhancer_saturation", 1.06f)) }
        var enhancerLineClarity by remember { mutableFloatStateOf(prefs.getFloat("enhancer_line_clarity", 0.40f)) }
        var showShaderSettings by remember { mutableStateOf(false) }
        val deviceSpecs = remember {
            val cores = Runtime.getRuntime().availableProcessors()
            val maxMemMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
            val tier = if (cores >= 8 && maxMemMb >= 384) "Ultra AI Tier" else if (cores >= 6) "High Performance" else "Balanced Safe"
            Triple(cores, maxMemMb, tier)
        }

        // Subtitle auto-translation & on-device AI model states
        var isTranslatingSub by remember { mutableStateOf(false) }
        var showTranslateSubDialog by remember { mutableStateOf(false) }
        var translatedSubMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var modelDownloadPrompt by remember { mutableStateOf<com.lagradost.cloudstream3.ui.animebox.api.AiModelInfo?>(null) }
        var localModelWarningPrompt by remember { mutableStateOf<com.lagradost.cloudstream3.ui.animebox.api.AiModelInfo?>(null) }
        var speechApiNoticeMessage by remember { mutableStateOf<String?>(null) }
        var showSpeechApiNoticeBanner by remember { mutableStateOf(false) }
        var activeModelDownloadProgress by remember { mutableStateOf<com.lagradost.cloudstream3.ui.animebox.api.ModelDownloadProgress?>(null) }
        var activeModelDownloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        LaunchedEffect(showSpeechApiNoticeBanner) {
            if (showSpeechApiNoticeBanner) {
                kotlinx.coroutines.delay(6000L)
                showSpeechApiNoticeBanner = false
            }
        }

        LaunchedEffect(anilistId, currentEpisodeNum) {
            val savedSubs = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.getSavedSubtitlesForEpisode(context, anilistId, currentEpisodeNum)
            val newMap = mutableMapOf<String, String>()
            for (s in savedSubs) {
                val label = if (s.langCode.equals("en", true)) "English (AI Translated)" else "${s.langName} (AI Translated)"
                newMap[label] = s.filePath
            }
            translatedSubMap = newMap
            val activePath = newMap[selectedSub]
                ?: (if (selectedSub.contains("English", ignoreCase = true)) newMap["English (AI Translated)"] else null)
                ?: (if (currentSubtitleUrl.isEmpty() && newMap.containsKey("English (AI Translated)")) newMap["English (AI Translated)"] else null)
            if (activePath != null) {
                val file = java.io.File(activePath)
                if (file.exists() && file.length() > 150) {
                    val cues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(file.readText())
                    if (cues.size >= 2) {
                        liveSubtitleCues = cues
                        if (!selectedSub.contains("Translated", ignoreCase = true) && !selectedSub.contains("AI", ignoreCase = true)) {
                            selectedSub = newMap.entries.firstOrNull { it.value == activePath }?.key ?: "English (AI Translated)"
                        }
                    }
                }
            }
        }

        // Resume dialog
        val savedProgress = remember(currentEpisodeNum, fromContinueWatching) {
            if (fromContinueWatching) historyManager.getSavedProgress(anilistId, currentEpisodeNum) else 0L
        }
        var showResumeDialog by remember(currentEpisodeNum, fromContinueWatching) {
            mutableStateOf(fromContinueWatching && savedProgress > 0L)
        }
        var seekOnStart by remember { mutableStateOf<Long?>(null) }

        var startPosition by remember { mutableStateOf(0L) }
        var isEpisodeReported by remember { mutableStateOf(false) }

        LaunchedEffect(showStreamReportPill) {
            if (showStreamReportPill) {
                delay(6000)
                showStreamReportPill = false
            }
        }

        LaunchedEffect(anilistId) {
            if (anilistId > 0 && (animePosterUrl.isEmpty() || animePosterUrl.contains("backdrop", ignoreCase = true) || animePosterUrl.contains("banner", ignoreCase = true) || animePosterUrl.contains("episode", ignoreCase = true))) {
                val realPoster = withContext(Dispatchers.IO) {
                    val cover = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getAnimeCover(anilistId)
                    if (cover.isNotEmpty()) cover else {
                        com.lagradost.cloudstream3.ui.animebox.api.AniListClient.getAnimeCover(anilistId)
                    }
                }
                if (realPoster.isNotEmpty()) {
                    animePosterUrl = realPoster
                }
            }
        }

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
                        if (com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId)) {
                            return@withContext com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getEpisodeCoverUrl(targetEp, context)
                                ?: com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_BACKDROP_URL
                        }
                        val isMovie = com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.isStaticMovie(anilistId)
                        var epImg = ""
                        if (isMovie) {
                            epImg = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getBestBackdropUrl(anilistId, isMovie = true)
                        }
                        if (epImg.isBlank()) {
                            val tmdbId = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getLongRunningTmdbId(anilistId)
                            if (tmdbId != null) {
                                val allEps = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getTmdbAllEpisodes(tmdbId)
                                epImg = allEps[targetEp]?.imageUrl ?: ""
                            }
                        }
                        if (epImg.isBlank()) {
                            try {
                                val epMeta = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getEpisodeMetadata(anilistId)
                                epImg = epMeta[targetEp]?.imageUrl ?: ""
                            } catch (_: Exception) {}
                        }
                        if (epImg.isBlank()) {
                            // Fallback to show backdrop so Continue Watching never shows a black box
                            epImg = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getBestBackdropUrl(anilistId, isMovie = isMovie)
                        }
                        if (epImg.isBlank()) {
                            epImg = showCoverUrl.ifEmpty { coverUrl }
                        }
                        epImg
                    }

                    currentEpisodeNum = targetEp
                    currentCoverUrl = targetCover
                    currentPosition = 0L
                    duration = 0L
                    startPosition = 0L
                    showResumeDialog = false

                    if (canSaveHistory(anilistId)) {
                        val validPortraitPoster = when {
                            animePosterUrl.isNotBlank() && !animePosterUrl.contains("backdrop", ignoreCase = true) && !animePosterUrl.contains("banner", ignoreCase = true) && !animePosterUrl.contains("episode", ignoreCase = true) -> animePosterUrl
                            showCoverUrl.isNotBlank() && !showCoverUrl.contains("backdrop", ignoreCase = true) && !showCoverUrl.contains("banner", ignoreCase = true) && !showCoverUrl.contains("episode", ignoreCase = true) -> showCoverUrl
                            coverUrl.isNotBlank() && !coverUrl.contains("backdrop", ignoreCase = true) && !coverUrl.contains("banner", ignoreCase = true) && !coverUrl.contains("episode", ignoreCase = true) -> coverUrl
                            else -> animePosterUrl.ifEmpty { showCoverUrl.ifEmpty { coverUrl } }
                        }
                        historyManager.saveWatchProgress(
                            anilistId = anilistId,
                            animeTitle = animeTitle,
                            coverImageUrl = targetCover,
                            episodeNumber = targetEp,
                            progressPositionMs = 1000L,
                            totalDurationMs = 1440000L,
                            showCoverUrl = validPortraitPoster
                        )
                    }

                    currentIntroStart = (streamInfo["introStart"] as? Long) ?: 0L
                    currentIntroEnd   = (streamInfo["introEnd"] as? Long) ?: 0L
                    currentOutroStart = (streamInfo["outroStart"] as? Long) ?: 0L
                    currentOutroEnd   = (streamInfo["outroEnd"] as? Long) ?: 0L

                    currentBackupHls = (streamInfo["backupHls"] as? String) ?: ""
                    currentBackupProvider = (streamInfo["backupProvider"] as? String) ?: ""

                    currentProvider = (streamInfo["provider"] as? String) ?: ""
                    val subUrl = (streamInfo["subtitle"] as? String) ?: ""
                    val prov = currentProvider
                    val isAniDb = prov.contains("anibd", ignoreCase = true) || prov.contains("anidb", ignoreCase = true) || ((streamInfo["referer"] as? String) ?: "").contains("animeapps", ignoreCase = true)
                    if (isAniDb && currentStreamType == "hardsub" && subUrl.isNotEmpty()) {
                        currentSubtitleUrl = subUrl
                        subPreset = "Hardsub"
                        prefs.edit().putString("subPreset", "Hardsub").apply()
                        selectedSub = "English (Soft Sub)"
                    } else if (currentStreamType == "hardsub") {
                        currentSubtitleUrl = ""
                        selectedSub = "Hard Sub"
                    } else {
                        currentSubtitleUrl = subUrl
                    }
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

                    // Background pre-fetch next episode (N+1) so Next Episode loads in 0ms
                    coroutineScope.launch(Dispatchers.IO) {
                        com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.prefetchStreamInfo(
                            context,
                            anilistId,
                            targetEp + 1,
                            currentStreamType
                        )
                    }
                } else {
                    android.widget.Toast.makeText(context, "Failed to load episode stream", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun switchToRelatedAnime(newAnilistId: Int, newTitle: String, newCover: String, targetEp: Int = 1) {
            isReloadingStream = true
            coroutineScope.launch {
                val sInfo = withContext(Dispatchers.IO) {
                    var info = com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(context, newAnilistId, targetEp, currentStreamType, newTitle)
                    if (info == null && currentStreamType != "sub") {
                        info = com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(context, newAnilistId, targetEp, "sub", newTitle)
                    }
                    info
                }
                isReloadingStream = false
                if (sInfo != null && (sInfo["hls"] as? String)?.isNotEmpty() == true) {
                    currentAnilistId = newAnilistId
                    currentAnimeTitle = newTitle
                    currentShowCoverUrl = newCover
                    currentEpisodeNum = targetEp
                    currentCoverUrl = newCover
                    currentPosition = 0L
                    duration = 0L
                    startPosition = 0L
                    showResumeDialog = false

                    if (canSaveHistory(newAnilistId)) {
                        historyManager.saveWatchProgress(
                            anilistId = newAnilistId,
                            animeTitle = newTitle,
                            coverImageUrl = newCover,
                            episodeNumber = targetEp,
                            progressPositionMs = 1000L,
                            totalDurationMs = 1440000L,
                            showCoverUrl = newCover
                        )
                    }

                    currentIntroStart = (sInfo["introStart"] as? Number)?.toLong() ?: 0L
                    currentIntroEnd   = (sInfo["introEnd"] as? Number)?.toLong() ?: 0L
                    currentOutroStart = (sInfo["outroStart"] as? Number)?.toLong() ?: 0L
                    currentOutroEnd   = (sInfo["outroEnd"] as? Number)?.toLong() ?: 0L

                    currentBackupHls = (sInfo["backupHls"] as? String) ?: ""
                    currentBackupProvider = (sInfo["backupProvider"] as? String) ?: ""

                    currentProvider = (sInfo["provider"] as? String) ?: ""
                    val relSubUrl = (sInfo["subtitle"] as? String) ?: ""
                    val relProv = currentProvider
                    val relIsAniDb = relProv.contains("anibd", ignoreCase = true) || relProv.contains("anidb", ignoreCase = true) || ((sInfo["referer"] as? String) ?: "").contains("animeapps", ignoreCase = true)
                    if (relIsAniDb && currentStreamType == "hardsub" && relSubUrl.isNotEmpty()) {
                        currentSubtitleUrl = relSubUrl
                        subPreset = "Hardsub"
                        prefs.edit().putString("subPreset", "Hardsub").apply()
                        selectedSub = "English (Soft Sub)"
                    } else if (currentStreamType == "hardsub") {
                        currentSubtitleUrl = ""
                        selectedSub = "Hard Sub"
                    } else {
                        currentSubtitleUrl = relSubUrl
                    }
                    currentReferer     = (sInfo["referer"] as? String) ?: ""
                    currentHlsUrl       = (sInfo["hls"] as? String) ?: ""

                    showEpisodesPanel = false
                } else {
                    fallbackDialogData = FallbackDialogData(
                        title = "Stream Unavailable",
                        description = "Could not load stream for $newTitle. Try changing the language or report the issue.",
                        primaryButtonText = "Retry",
                        onPrimaryAction = {
                            switchToRelatedAnime(newAnilistId, newTitle, newCover, targetEp)
                        }
                    )
                }
            }
        }

        // ─── Build player ──────────────────────────────────────────────────────
        val player = remember(currentHlsUrl, currentReferer, currentStreamType) {
            buildPlayer(context, currentHlsUrl, currentReferer, currentSubtitleUrl, streamType = currentStreamType, startPositionMs = startPosition, playImmediately = !showResumeDialog)
        }

        var liveTranslatingStatus by remember { mutableStateOf<String?>(null) }

        // Track available stream resolutions dynamically
        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    val heights = mutableSetOf<Int>()
                    for (group in tracks.groups) {
                        if (group.type == C.TRACK_TYPE_VIDEO) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                if (format.height > 0) {
                                    heights.add(format.height)
                                }
                            }
                        }
                    }
                    if (heights.isNotEmpty()) {
                        videoTrackHeights = heights.sortedDescending()
                    }
                }

                override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                    val isAiSub = isTranslatingSub || liveSubtitleCues.isNotEmpty() || selectedSub.contains("AI", ignoreCase = true) || selectedSub.contains("Translated", ignoreCase = true) || selectedSub.contains("Public", ignoreCase = true)
                    if (isAiSub) {
                        // AI / Translated subtitles are actively rendered by LaunchedEffect; do not clear or hide
                        return
                    }
                    if (selectedSub != "Off" && !selectedSub.contains("Hard Sub")) {
                        playerViewInstance?.subtitleView?.visibility = View.VISIBLE
                        playerViewInstance?.subtitleView?.setCues(cueGroup.cues)
                    } else {
                        playerViewInstance?.subtitleView?.setCues(emptyList())
                        playerViewInstance?.subtitleView?.visibility = View.INVISIBLE
                    }
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.height > 0 && !videoTrackHeights.contains(videoSize.height)) {
                        videoTrackHeights = (videoTrackHeights + videoSize.height).distinct().sortedDescending()
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (isOfflineMode) {
                        fallbackDialogData = FallbackDialogData(
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
                    showStreamReportPill = true
                    isEpisodeReported = false
                    fallbackDialogData = FallbackDialogData(
                        title = "Playback Error",
                        description = "Could not load video for this episode. You can retry, change audio/subtitles, or report the issue.",
                        primaryButtonText = "Retry",
                        onPrimaryAction = {
                            isReloadingStream = true
                            coroutineScope.launch {
                                val fbMap = fetchStreamInfo(anilistId, currentEpisodeNum, currentStreamType)
                                isReloadingStream = false
                                if (fbMap != null && (fbMap["hls"] as? String)?.isNotEmpty() == true) {
                                    currentHlsUrl = fbMap["hls"] as String
                                    currentReferer = (fbMap["referer"] as? String) ?: ""
                                    currentProvider = (fbMap["provider"] as? String) ?: ""
                                    val fbSub = (fbMap["subtitle"] as? String) ?: ""
                                    if (fbSub.isNotEmpty()) {
                                        currentSubtitleUrl = fbSub
                                    }
                                }
                            }
                        }
                    )
                    // Seamless automatic failover when a stream error occurs (online only)
                    coroutineScope.launch {
                        if (currentBackupHls.isNotEmpty() && currentHlsUrl != currentBackupHls) {
                            val pos = player.currentPosition
                            currentHlsUrl = currentBackupHls
                            startPosition = pos
                        } else {
                            val fbMap = fetchStreamInfo(anilistId, currentEpisodeNum, currentStreamType)
                            if (fbMap != null && (fbMap["hls"] as? String)?.isNotEmpty() == true && fbMap["hls"] != currentHlsUrl) {
                                val pos = player.currentPosition
                                currentHlsUrl = fbMap["hls"] as String
                                currentReferer = (fbMap["referer"] as? String) ?: ""
                                currentProvider = (fbMap["provider"] as? String) ?: ""
                                val fbSub = (fbMap["subtitle"] as? String) ?: ""
                                if (fbSub.isNotEmpty()) {
                                    currentSubtitleUrl = fbSub
                                }
                                startPosition = pos
                            }
                        }
                    }
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
            }
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

        // Fetch episode images and titles from AniZip, TMDB, or ShinChanSupabaseManager (online mode only)
        LaunchedEffect(currentAnilistId, isOfflineMode) {
            if (!isOfflineMode) {
                coroutineScope.launch {
                    val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(currentAnilistId)
                    try {
                        if (isShinChan) {
                            com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.loadBundledAssetIfNeeded(context)
                            com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.loadShinChanEpisodes(context)
                            val covers = com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getAllEpisodeCovers(context)
                            val maxEp = maxOf(com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getLatestEpisodeNumber(), 1349)
                            val imgMap = mutableMapOf<Int, String>()
                            val titleMap = mutableMapOf<Int, String>()
                            for (ep in 1..maxEp) {
                                imgMap[ep] = covers[ep] ?: ""
                                titleMap[ep] = "Episode $ep"
                            }
                            episodeImages = imgMap
                            episodeTitles = titleMap
                        } else {
                            val tmdbId = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getLongRunningTmdbId(currentAnilistId)
                            val metaMap = if (tmdbId != null) {
                                com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getTmdbAllEpisodes(tmdbId)
                            } else {
                                com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getEpisodeMetadata(currentAnilistId)
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
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Fetch real anime poster and top 2-3 specific related anime
                    try {
                        val detailsJson = com.lagradost.cloudstream3.ui.animebox.api.AniListClient.getAnimeDetails(currentAnilistId)
                        if (detailsJson != null) {
                            val mediaObj = org.json.JSONObject(detailsJson).getJSONObject("data").getJSONObject("Media")
                            
                            // Extract real anime poster
                            if (mediaObj.has("coverImage") && !mediaObj.isNull("coverImage")) {
                                val cov = mediaObj.getJSONObject("coverImage")
                                val poster = cov.optString("extraLarge", "").ifEmpty { cov.optString("large", "") }
                                if (poster.isNotEmpty()) {
                                    animePosterUrl = poster
                                }
                            }

                            // Extract 2-3 specific related anime matching Detail Activity Related Section exactly
                            val relList = mutableListOf<PlayerRelatedAnime>()
                            if (mediaObj.has("relations") && !mediaObj.isNull("relations")) {
                                val relObj = mediaObj.getJSONObject("relations")
                                if (relObj.has("edges")) {
                                    val edges = relObj.getJSONArray("edges")
                                    for (i in 0 until edges.length()) {
                                        val edge = edges.getJSONObject(i)
                                        val relType = edge.optString("relationType", "")
                                        if (edge.has("node") && !edge.isNull("node")) {
                                            val node = edge.getJSONObject("node")
                                            if (com.lagradost.cloudstream3.ui.animebox.api.AniListClient.isBlockedMedia(node)) continue
                                            val relId = node.getInt("id")
                                            if (relId == currentAnilistId) continue
                                            val relTitleObj = node.getJSONObject("title")
                                            val relTitle = if (relTitleObj.has("english") && !relTitleObj.isNull("english")) {
                                                relTitleObj.getString("english")
                                            } else {
                                                relTitleObj.getString("romaji")
                                            }
                                            val relCover = node.getJSONObject("coverImage").optString("large", "")
                                            val format = node.optString("format", "")
                                            val formatUpper = format.uppercase()
                                            val relTypeUpper = relType.uppercase()
                                            val isTvOrMovie = (formatUpper == "TV" || formatUpper == "MOVIE" || formatUpper == "TV_SHORT")
                                            val isSequelPrequelOrMovieSideStory = (relTypeUpper == "SEQUEL" || relTypeUpper == "PREQUEL" || (formatUpper == "MOVIE" && relTypeUpper == "SIDE_STORY"))
                                            if (!isTvOrMovie || !isSequelPrequelOrMovieSideStory) continue
                                            val shortTitle = if (relTitle.length > 24) relTitle.take(22) + "…" else relTitle
                                            relList.add(PlayerRelatedAnime(relId, shortTitle, relCover, format))
                                        }
                                    }
                                }
                            }
                            if (relList.isNotEmpty()) {
                                relatedAnimeList = relList.distinctBy { it.id }.take(3)
                            }
                        }

                        // Fallback to Kitsu relations if AniList down or no relations found (skip for Shin Chan)
                        if (!isShinChan && (relatedAnimeList.isEmpty() || detailsJson == null)) {
                            val kitsuId = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getKitsuIdFromAnilist(currentAnilistId) ?: currentAnilistId
                            val kitsuRelations = com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.getAnimeRelations(kitsuId)
                            if (kitsuRelations.isNotEmpty()) {
                                relatedAnimeList = kitsuRelations.map { rel ->
                                    val shortTitle = if (rel.title.length > 24) rel.title.take(22) + "…" else rel.title
                                    PlayerRelatedAnime(rel.id, shortTitle, rel.coverUrl, rel.format)
                                }.distinctBy { it.id }.take(3)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        LaunchedEffect(lockedControlsVisible, isLocked) {
            if (isLocked && lockedControlsVisible) {
                delay(4000)
                lockedControlsVisible = false
            }
        }

        // Initial progress record for Continue Watching on launch
        LaunchedEffect(Unit) {
            if (canSaveHistory(anilistId)) {
                val isMovie = com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.isStaticMovie(anilistId)
                var initialCover = currentCoverUrl
                if (isMovie) {
                    withContext(Dispatchers.IO) {
                        val mBackdrop = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getBestBackdropUrl(anilistId, isMovie = true)
                        if (mBackdrop.isNotBlank()) {
                            initialCover = mBackdrop
                            currentCoverUrl = mBackdrop
                        }
                    }
                } else {
                    if (totalEpisodes == 1 && showCoverUrl.isNotBlank()) {
                        initialCover = showCoverUrl
                    }
                    val tmdbId = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getLongRunningTmdbId(anilistId)
                    if (tmdbId != null && (initialCover.isBlank() || !initialCover.contains("tmdb.org"))) {
                        withContext(Dispatchers.IO) {
                            try {
                                val allEps = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getTmdbAllEpisodes(tmdbId)
                                val epImg = allEps[currentEpisodeNum]?.imageUrl ?: ""
                                if (epImg.isNotBlank()) {
                                    initialCover = epImg
                                    currentCoverUrl = epImg
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
                val validPoster = when {
                    animePosterUrl.isNotBlank() && !animePosterUrl.contains("backdrop", ignoreCase = true) && !animePosterUrl.contains("banner", ignoreCase = true) && !animePosterUrl.contains("episode", ignoreCase = true) -> animePosterUrl
                    showCoverUrl.isNotBlank() && !showCoverUrl.contains("backdrop", ignoreCase = true) && !showCoverUrl.contains("banner", ignoreCase = true) && !showCoverUrl.contains("episode", ignoreCase = true) -> showCoverUrl
                    coverUrl.isNotBlank() && !coverUrl.contains("backdrop", ignoreCase = true) && !coverUrl.contains("banner", ignoreCase = true) && !coverUrl.contains("episode", ignoreCase = true) -> coverUrl
                    else -> animePosterUrl.ifEmpty { showCoverUrl.ifEmpty { coverUrl } }
                }
                historyManager.saveWatchProgress(
                    anilistId = anilistId,
                    animeTitle = animeTitle,
                    coverImageUrl = initialCover,
                    episodeNumber = currentEpisodeNum,
                    progressPositionMs = startPosition.coerceAtLeast(1000L),
                    totalDurationMs = 1440000L,
                    showCoverUrl = validPoster
                )
            }
        }

        // Apply subtitle style (Presets & Hardsub styling from Screenshot 2)
        LaunchedEffect(playerViewInstance, subFontSize, subTextColor, subBgOpacity, subEdgeType, subPreset) {
            playerViewInstance?.subtitleView?.apply {
                val isHardsub = subPreset.equals("Hardsub", ignoreCase = true)
                val subTypeface = if (isHardsub) {
                    // Rounded bold sans-serif matching Screenshot 2
                    android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                } else {
                    androidx.core.content.res.ResourcesCompat.getFont(context, R.font.google_sans)
                        ?: android.graphics.Typeface.SANS_SERIF
                }
                val captionStyle = if (isHardsub) {
                    androidx.media3.ui.CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        android.graphics.Color.BLACK,
                        subTypeface
                    )
                } else {
                    androidx.media3.ui.CaptionStyleCompat(
                        subTextColor,
                        android.graphics.Color.argb(subBgOpacity, 0, 0, 0),
                        android.graphics.Color.TRANSPARENT,
                        subEdgeType,
                        android.graphics.Color.BLACK,
                        subTypeface
                    )
                }
                setStyle(captionStyle)
                setApplyEmbeddedStyles(!isHardsub)
                setApplyEmbeddedFontSizes(!isHardsub)
                setBottomPaddingFraction(0.08f)
                val sizeFraction = if (isHardsub) {
                    0.058f
                } else {
                    when {
                        subFontSize <= 14f -> 0.045f
                        subFontSize >= 24f -> 0.065f
                        subFontSize >= 20f -> 0.055f
                        else -> 0.050f
                    }
                }
                setFractionalTextSize(sizeFraction)
            }
        }

        // Configure track selection only when subtitle mode / track state actually changes
        LaunchedEffect(player, selectedSub, isTranslatingSub, liveSubtitleCues.isNotEmpty(), isInPipMode) {
            try {
                if (isInPipMode || selectedSub == "Off" || selectedSub.contains("Hard Sub")) {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                } else {
                    val isAiSub = selectedSub.contains("Translated", ignoreCase = true) ||
                            selectedSub.contains("AI", ignoreCase = true) ||
                            selectedSub.contains("Public", ignoreCase = true) ||
                            isTranslatingSub ||
                            (liveSubtitleCues.isNotEmpty() && !selectedSub.contains("Hard Sub", ignoreCase = true))

                    if (isAiSub) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                    } else {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setPreferredTextLanguage("en")
                            .setSelectUndeterminedTextLanguage(true)
                            .setIgnoredTextSelectionFlags(0)
                            .build()
                    }
                }
            } catch (_: Throwable) {}
        }

        // Real-time subtitle cue synchronization for standard tracks and AI Translated tracks
        LaunchedEffect(selectedSub, liveSubtitleCues, currentPosition, isTranslatingSub, isInPipMode, playerViewInstance, subFontSize, subTextColor, subBgOpacity, subEdgeType, subPreset) {
            try {
                val subView = playerViewInstance?.subtitleView ?: return@LaunchedEffect

                // ─── Re-apply EXACT same style for both AI & native subtitles ───
                try {
                    val isHardsub = subPreset.equals("Hardsub", ignoreCase = true)
                    val subTypeface = if (isHardsub) {
                        android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                    } else {
                        androidx.core.content.res.ResourcesCompat.getFont(subView.context, R.font.google_sans)
                            ?: android.graphics.Typeface.SANS_SERIF
                    }
                    val captionStyle = if (isHardsub) {
                        androidx.media3.ui.CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            subTypeface
                        )
                    } else {
                        androidx.media3.ui.CaptionStyleCompat(
                            subTextColor,
                            android.graphics.Color.argb(subBgOpacity, 0, 0, 0),
                            android.graphics.Color.TRANSPARENT,
                            subEdgeType,
                            android.graphics.Color.BLACK,
                            subTypeface
                        )
                    }
                    subView.setStyle(captionStyle)
                    subView.setApplyEmbeddedStyles(!isHardsub)
                    subView.setApplyEmbeddedFontSizes(!isHardsub)
                    subView.setBottomPaddingFraction(0.08f)
                    val sizeFraction = if (isHardsub) {
                        0.058f
                    } else {
                        when {
                            subFontSize <= 14f -> 0.045f
                            subFontSize >= 24f -> 0.065f
                            subFontSize >= 20f -> 0.055f
                            else -> 0.050f
                        }
                    }
                    subView.setFractionalTextSize(sizeFraction)
                } catch (_: Throwable) {}

                try {
                    subView.bringToFront()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        subView.elevation = 100f
                    }
                } catch (_: Throwable) {}

                if (isInPipMode || selectedSub == "Off" || selectedSub.contains("Hard Sub")) {
                    subView.setCues(emptyList())
                    subView.visibility = View.INVISIBLE
                    return@LaunchedEffect
                }

                val isAiSub = selectedSub.contains("Translated", ignoreCase = true) ||
                        selectedSub.contains("AI", ignoreCase = true) ||
                        selectedSub.contains("Public", ignoreCase = true) ||
                        isTranslatingSub ||
                        (liveSubtitleCues.isNotEmpty() && !selectedSub.contains("Hard Sub", ignoreCase = true))

                if (isAiSub) {
                    val activeCue = liveSubtitleCues.firstOrNull { currentPosition in it.startMs..it.endMs }
                    if (activeCue != null && activeCue.translatedText.isNotBlank() && com.lagradost.cloudstream3.ui.animebox.api.SenseVoiceAudioTranscriber.isValidDialogueCue(activeCue.translatedText)) {
                        val stripped = com.lagradost.cloudstream3.ui.animebox.api.SenseVoiceAudioTranscriber.stripSpeakerPrefix(activeCue.translatedText)
                        val rawLines = splitIntoSubtitleLines(stripped).filter { it.isNotBlank() }
                        var cleanText = rawLines.joinToString("\n")
                        cleanText = normalizePunctuationSpacing(cleanText)
                        val nativeCue = androidx.media3.common.text.Cue.Builder()
                            .setText(cleanText)
                            .build()
                        subView.visibility = View.VISIBLE
                        subView.setCues(listOf(nativeCue))
                    } else {
                        subView.setCues(emptyList())
                    }
                } else {
                    subView.visibility = View.VISIBLE
                    subView.setCues(player.currentCues.cues)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        // Apply AI Graphics Enhancer bitrate tuning
        LaunchedEffect(isGraphicsUpscalerEnabled, player) {
            try {
                player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                val params = player.trackSelectionParameters.buildUpon()
                if (isGraphicsUpscalerEnabled) {
                    params.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                        .setMaxVideoBitrate(Int.MAX_VALUE)
                        .setForceHighestSupportedBitrate(true)
                } else {
                    params.setForceHighestSupportedBitrate(false)
                }
                player.trackSelectionParameters = params.build()
            } catch (e: Exception) {
                e.printStackTrace()
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
                    val rawDur = player.duration.coerceAtLeast(0L)
                    duration = if (isOfflineMode && rawDur in 1L..10_000L) maxOf(rawDur, maxOf(currentPosition + 30_000L, 1440_000L)) else rawDur
                }
                override fun onPlaybackStateChanged(state: Int) {
                    val rawDur = player.duration.coerceAtLeast(0L)
                    duration = if (isOfflineMode && rawDur in 1L..10_000L) maxOf(rawDur, maxOf(player.currentPosition + 30_000L, 1440_000L)) else rawDur
                    currentPosition = player.currentPosition.coerceAtLeast(0L)
                    isBuffering = state == Player.STATE_BUFFERING

                    if (state == Player.STATE_READY && player.playWhenReady && !player.isPlaying) {
                        player.play()
                    }

                    if (state == Player.STATE_ENDED) {
                        val autoplayNext = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isAutoplayNextEpisodeEnabled(context)
                        if (autoplayNext && !isNextEpLoading && (totalEpisodes == 0 || currentEpisodeNum < totalEpisodes)) {
                            switchToEpisode(currentEpisodeNum + 1)
                        }
                    }
                }
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    currentPosition = player.currentPosition.coerceAtLeast(0L)
                    duration = player.duration.coerceAtLeast(0L)
                }
                override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                    val isAiSub = isTranslatingSub || liveSubtitleCues.isNotEmpty() ||
                            selectedSub.contains("AI", ignoreCase = true) ||
                            selectedSub.contains("Translated", ignoreCase = true) ||
                            selectedSub.contains("Public", ignoreCase = true)
                    if (selectedSub != "Off" && !selectedSub.contains("Hard Sub") && !isAiSub) {
                        playerViewInstance?.subtitleView?.visibility = View.VISIBLE
                        playerViewInstance?.subtitleView?.setCues(cueGroup.cues)
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    error.printStackTrace()
                    if (isOfflineMode) {
                        // In offline mode, do not trigger online stream fetch or toasts
                        return
                    }
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
                        android.widget.Toast.makeText(context, "Reloading stream...", android.widget.Toast.LENGTH_SHORT).show()
                        isReloadingStream = true
                        coroutineScope.launch {
                            val fbMap: Map<String, Any?>? = fetchStreamInfo(anilistId, currentEpisodeNum, currentStreamType)
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
                                if (currentStreamType == "sub") {
                                    fallbackDialogData = FallbackDialogData(
                                        title = "Soft Sub Playback Error",
                                        description = "There was an error playing the soft subtitle stream. Would you like to switch to the Hard Sub version?",
                                        primaryButtonText = "Switch to Hard Sub",
                                        onPrimaryAction = {
                                            currentStreamType = "hardsub"
                                            selectedSub = "Hard Sub"
                                            isReloadingStream = true
                                            coroutineScope.launch {
                                                val savedPos = player.currentPosition
                                                val hsMap = fetchStreamInfo(anilistId, currentEpisodeNum, "hardsub")
                                                isReloadingStream = false
                                                if (hsMap != null && (hsMap["hls"] as? String)?.isNotEmpty() == true) {
                                                    currentHlsUrl = hsMap["hls"] as String
                                                    currentReferer = (hsMap["referer"] as? String) ?: ""
                                                    currentSubtitleUrl = ""
                                                    selectedSub = "Hard Sub"
                                                    delay(400)
                                                    if (savedPos > 0L) {
                                                        player.seekTo(savedPos)
                                                    }
                                                    player.play()
                                                } else {
                                                    android.widget.Toast.makeText(context, "Hard Sub stream not available", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                    showStreamReportPill = true
                                } else {
                                    android.widget.Toast.makeText(context, "Unable to load stream for episode $currentEpisodeNum", android.widget.Toast.LENGTH_SHORT).show()
                                }
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
                    if (dur > 0 && currentPos > 0 && canSaveHistory(anilistId)) {
                        val finalCover = if (totalEpisodes == 1) showCoverUrl else currentCoverUrl
                        val validPoster = when {
                            animePosterUrl.isNotBlank() && !animePosterUrl.contains("backdrop", ignoreCase = true) && !animePosterUrl.contains("banner", ignoreCase = true) && !animePosterUrl.contains("episode", ignoreCase = true) -> animePosterUrl
                            showCoverUrl.isNotBlank() && !showCoverUrl.contains("backdrop", ignoreCase = true) && !showCoverUrl.contains("banner", ignoreCase = true) && !showCoverUrl.contains("episode", ignoreCase = true) -> showCoverUrl
                            coverUrl.isNotBlank() && !coverUrl.contains("backdrop", ignoreCase = true) && !coverUrl.contains("banner", ignoreCase = true) && !coverUrl.contains("episode", ignoreCase = true) -> coverUrl
                            else -> animePosterUrl.ifEmpty { showCoverUrl.ifEmpty { coverUrl } }
                        }
                        historyManager.saveWatchProgress(
                            anilistId = anilistId,
                            animeTitle = animeTitle,
                            coverImageUrl = finalCover,
                            episodeNumber = currentEpisodeNum,
                            progressPositionMs = currentPos,
                            totalDurationMs = dur,
                            showCoverUrl = validPoster
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
                    val pv = (android.view.LayoutInflater.from(ctx).inflate(
                        com.lagradost.cloudstream3.R.layout.animebox_player_view,
                        null
                    ) as? PlayerView) ?: PlayerView(ctx)
                    pv.apply {
                        useController = false
                        this.player = player
                        playerViewInstance = this
                        resizeMode = when (resizeMode) {
                            1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                            2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        if (player.playWhenReady) {
                            player.play()
                        }
                        // Apply initial subtitle visibility
                        if (selectedSub == "Off" || selectedSub.contains("Hard Sub")) {
                            subtitleView?.visibility = View.INVISIBLE
                        }
                        
                        // Ensure SubtitleView is detached from AspectRatioFrameLayout and attached directly to PlayerView
                        // so that resizing, zooming (crop), or stretching the video surface never pushes subtitles off-screen or scales them.
                        subtitleView?.let { subView ->
                            val parent = subView.parent as? android.view.ViewGroup
                            if (parent != null && parent !== this) {
                                parent.removeView(subView)
                                this.addView(subView, android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                ))
                            }
                            // Force SubtitleView to be the top-most child on top of all panels, controls, and dialogs
                            subView.bringToFront()
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                subView.elevation = 50f
                            }
                        }

                        // Push SubtitleView layout margin up dynamically!
                        val density = ctx.resources.displayMetrics.density
                        subtitleView?.let { subView ->
                            val targetMargin = if (controlsVisible && !isLocked) (110 * density).toInt() else (35 * density).toInt()
                            val lp = (subView.layoutParams as? android.widget.FrameLayout.LayoutParams)
                                ?: android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
                            lp.bottomMargin = targetMargin
                            subView.layoutParams = lp

                            // Safe layout listener to prevent ExoPlayer resets + ALWAYS keep subtitles on top layer
                            subView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                                val currentLp = (v.layoutParams as? android.widget.FrameLayout.LayoutParams)
                                    ?: android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
                                val currentTarget = if (controlsVisible && !isLocked) (110 * density).toInt() else (35 * density).toInt()
                                if (currentLp.bottomMargin != currentTarget) {
                                    currentLp.bottomMargin = currentTarget
                                    v.layoutParams = currentLp
                                }
                                // Ensure subtitles stay top-most even after other views are added/laid out
                                v.bringToFront()
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                    v.elevation = 50f
                                }
                            }
                        }
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = when (resizeMode) {
                        1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    // Re-apply subtitle top-layer guarantee on every update pass (panels may have been added)
                    view.subtitleView?.let { subView ->
                        subView.bringToFront()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            subView.elevation = 50f
                        }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        val nativeEffect = if (isGraphicsUpscalerEnabled) {
                            com.lagradost.cloudstream3.ui.animebox.enhancer.AnimeGraphicsEnhancer.createNativeRenderEffect(
                                sharpness = enhancerSharpness,
                                contrast = enhancerContrast,
                                saturation = enhancerSaturation,
                                lineClarity = enhancerLineClarity
                            )
                        } else null
                        // Apply effect exclusively to the video texture surface so SubtitleView is NOT affected!
                        view.videoSurfaceView?.setRenderEffect(nativeEffect)
                        view.setRenderEffect(null)
                    }
                    try {
                        view.subtitleView?.let { subView ->
                            val parent = subView.parent as? android.view.ViewGroup
                            if (parent != null && parent !== view) {
                                parent.removeView(subView)
                                view.addView(subView, android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                ))
                            }
                        }
                    } catch (_: Throwable) {}
                    if (isInPipMode || selectedSub == "Off" || selectedSub.contains("Hard Sub")) {
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
                                    controlsVisible = true
                                    val width = size.width
                                    if (offset.x < width / 2) {
                                        val target = (player.currentPosition - (seekStepSeconds * 1000L)).coerceAtLeast(0L)
                                        player.seekTo(target)
                                        currentPosition = target
                                        val curSec = if (seekOverlayState?.first == false) (seekOverlayState?.second ?: 0) + seekStepSeconds else seekStepSeconds
                                        seekOverlayState = false to curSec
                                        seekOverlayKey++
                                    } else {
                                        val target = (player.currentPosition + (seekStepSeconds * 1000L)).coerceAtMost(player.duration)
                                        player.seekTo(target)
                                        currentPosition = target
                                        val curSec = if (seekOverlayState?.first == true) (seekOverlayState?.second ?: 0) + seekStepSeconds else seekStepSeconds
                                        seekOverlayState = true to curSec
                                        seekOverlayKey++
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
                        CircularProgressIndicator(color = playerAccentColor)
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
                            tint = playerAccentColor,
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xD9333338)),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(bottom = 120.dp, end = 24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Skip Intro", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xD9333338)),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(bottom = 120.dp, end = 24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Skip Outro", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = currentAnimeTitle,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Episode $currentEpisodeNum",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.LightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Right: Chromecast + Picture-in-Picture buttons
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Custom Cast button
                            IconButton(onClick = {
                                showCastDialog = true
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

                    // 2. Center Playback Buttons (Matching Reference Screenshot: Skip -10s, Large Solid White Play/Pause, Skip +10s)
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s (open circular arrow with centered "10")
                        val isRewindActive = seekOverlayState?.first == false
                        val rewindSec = seekOverlayState?.second ?: seekStepSeconds
                        val rewindScale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isRewindActive) 1.15f else 1.0f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                            ),
                            label = "rewindScale"
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .scale(rewindScale)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    val target = (player.currentPosition - (seekStepSeconds * 1000L)).coerceAtLeast(0L)
                                    player.seekTo(target)
                                    currentPosition = target
                                    player.play()
                                    val curSec = if (seekOverlayState?.first == false) (seekOverlayState?.second ?: 0) + seekStepSeconds else seekStepSeconds
                                    seekOverlayState = false to curSec
                                    seekOverlayKey++
                                }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Skip10CircleButton(
                                    isForward = false,
                                    onClick = {
                                        val target = (player.currentPosition - (seekStepSeconds * 1000L)).coerceAtLeast(0L)
                                        player.seekTo(target)
                                        currentPosition = target
                                        player.play()
                                        val curSec = if (seekOverlayState?.first == false) (seekOverlayState?.second ?: 0) + seekStepSeconds else seekStepSeconds
                                        seekOverlayState = false to curSec
                                        seekOverlayKey++
                                    }
                                )
                                if (isRewindActive) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "-${rewindSec}s",
                                        color = playerAccentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(48.dp))

                        // Large Solid White Play / Pause Button (Prominent Hero Size)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(112.dp)
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
                                    color = playerAccentColor,
                                    modifier = Modifier.size(52.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) PlayerSolidPauseIcon else PlayerSolidPlayIcon,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(88.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(48.dp))

                        // Forward 10s (open circular arrow with centered "10")
                        val isForwardActive = seekOverlayState?.first == true
                        val forwardSec = seekOverlayState?.second ?: seekStepSeconds
                        val forwardScale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isForwardActive) 1.15f else 1.0f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                            ),
                            label = "forwardScale"
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .scale(forwardScale)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    val target = (player.currentPosition + (seekStepSeconds * 1000L)).coerceAtMost(player.duration)
                                    player.seekTo(target)
                                    currentPosition = target
                                    player.play()
                                    val curSec = if (seekOverlayState?.first == true) (seekOverlayState?.second ?: 0) + seekStepSeconds else seekStepSeconds
                                    seekOverlayState = true to curSec
                                    seekOverlayKey++
                                }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Skip10CircleButton(
                                    isForward = true,
                                    onClick = {
                                        val target = (player.currentPosition + (seekStepSeconds * 1000L)).coerceAtMost(player.duration)
                                        player.seekTo(target)
                                        currentPosition = target
                                        player.play()
                                        val curSec = if (seekOverlayState?.first == true) (seekOverlayState?.second ?: 0) + seekStepSeconds else seekStepSeconds
                                        seekOverlayState = true to curSec
                                        seekOverlayKey++
                                    }
                                )
                                if (isForwardActive) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "+${forwardSec}s",
                                        color = playerAccentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
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

                        // Bottom icon buttons row (Adapts seamlessly for online anime vs offline/outside videos)
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
                                    .clickable {
                                        showAudioSubtitlesPanel = true
                                        controlsVisible = false
                                    }
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

                            if (!isOfflineMode) {
                                // 3. Series (Episodes list)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            showEpisodesPanel = true
                                            controlsVisible = false
                                        }
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
                            }

                            // 4. Speed & Quality
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        showSpeedQualityPanel = true
                                        controlsVisible = false
                                    }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_player_007),
                                    contentDescription = if (isOfflineMode) "Speed" else "Quality",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(if (isOfflineMode) "Speed" else "Quality", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ─── Persona-Style Special Thanking Dialogue Box (Centered Top-Middle) ───
            AnimatedVisibility(
                visible = showThankingBanner && isThankingAudioActive,
                enter = fadeIn(tween(350)) + scaleIn(tween(400, easing = FastOutSlowInEasing), initialScale = 0.85f) + slideInVertically(initialOffsetY = { -it / 3 }),
                exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.85f) + slideOutVertically(targetOffsetY = { -it / 3 })
            ) {
                val thankSubber = if (isShinChanContent) "Shinchan OTAKU" else "Blissey's Husband"
                val thankMsg = if (isShinChanContent) {
                    "Thanks to \"Shinchan OTAKU\" for subbing these episodes! Support him on his OK.ru account."
                } else {
                    "Special thanks to \"Blissey's Husband\" for providing Hindi dub episodes!"
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 22.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showThankingBanner = false },
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // 1. Chibi Character (Firefly holding heart)
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .offset(y = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_thank_chibi),
                                contentDescription = "Chibi",
                                modifier = Modifier
                                    .size(76.dp)
                                    .shadow(12.dp, shape = CircleShape, spotColor = Color(0xFFFF4081)),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // 2. Speech Bubble Tail (Pointing directly from dialogue box towards her mouth)
                        Canvas(
                            modifier = Modifier
                                .size(14.dp, 20.dp)
                                .offset(x = 1.dp, y = (-18).dp)
                        ) {
                            val path = Path().apply {
                                moveTo(size.width, 0f)
                                lineTo(0f, size.height * 0.5f)
                                lineTo(size.width, size.height)
                                close()
                            }
                            drawPath(path, color = Color(0xF5141522))
                            drawPath(
                                path,
                                color = Color(0xFFFF4081),
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // 3. Persona Dialogue Bubble Body
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xF8151725),
                                            Color(0xF20F101A)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.8.dp,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFFF4081), // Neon Pink
                                            Color(0xFF9C27B0), // Persona Purple
                                            Color(0xFF00E5FF)  // Electric Cyan
                                        )
                                    ),
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            // Top Bar with Angled Badge & Subber
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFFFF1493), Color(0xFFFF69B4))
                                                )
                                            )
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "SPECIAL THANKS ♥",
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = thankSubber,
                                        color = Color(0xFFFFD54F),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "✕",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            Text(
                                text = thankMsg,
                                color = Color(0xFFF1F3FF),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ─── Persona-Style Speech Recognition / API Status Dialogue Box (Centered Top-Middle) ───
            AnimatedVisibility(
                visible = showSpeechApiNoticeBanner && !speechApiNoticeMessage.isNullOrBlank(),
                enter = fadeIn(tween(350)) + scaleIn(tween(400, easing = FastOutSlowInEasing), initialScale = 0.85f) + slideInVertically(initialOffsetY = { -it / 3 }),
                exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.85f) + slideOutVertically(targetOffsetY = { -it / 3 })
            ) {
                val notice = speechApiNoticeMessage ?: ""
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 22.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showSpeechApiNoticeBanner = false },
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // 1. Chibi Character
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .offset(y = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_thank_chibi),
                                contentDescription = "Chibi",
                                modifier = Modifier
                                    .size(76.dp)
                                    .shadow(12.dp, shape = CircleShape, spotColor = Color(0xFF00E5FF)),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // 2. Speech Bubble Tail
                        Canvas(
                            modifier = Modifier
                                .size(14.dp, 20.dp)
                                .offset(x = 1.dp, y = (-18).dp)
                        ) {
                            val path = Path().apply {
                                moveTo(size.width, 0f)
                                lineTo(0f, size.height * 0.5f)
                                lineTo(size.width, size.height)
                                close()
                            }
                            drawPath(path, color = Color(0xF5141522))
                            drawPath(
                                path,
                                color = Color(0xFF00E5FF),
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // 3. Persona Dialogue Bubble Body
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xF8151725),
                                            Color(0xF20F101A)
                                        )
                                    )
                                )
                                .border(
                                    1.5.dp,
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF00E5FF),
                                            Color(0xFF7C4DFF)
                                        )
                                    ),
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFF00B0FF), Color(0xFF00E5FF))
                                                )
                                            )
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "CLOUD AI SPEECH",
                                            color = Color.Black,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Status & Notice",
                                        color = Color(0xFFFFD54F),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "✕",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            Text(
                                text = notice,
                                color = Color(0xFFF1F3FF),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ─── Persona-Style Community Public Subtitles Save Dialogue Card ───
            AnimatedVisibility(
                visible = showSaveToPublicCard && pendingPublicSubInfo != null,
                enter = fadeIn(tween(350)) + scaleIn(tween(400, easing = FastOutSlowInEasing), initialScale = 0.85f) + slideInVertically(initialOffsetY = { -it / 3 }),
                exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.85f) + slideOutVertically(targetOffsetY = { -it / 3 })
            ) {
                val info = pendingPublicSubInfo ?: return@AnimatedVisibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 22.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Row(
                        modifier = Modifier.widthIn(max = 540.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .offset(y = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_thank_chibi),
                                contentDescription = "Chibi",
                                modifier = Modifier
                                    .size(76.dp)
                                    .shadow(12.dp, shape = CircleShape, spotColor = Color(0xFF00E5FF)),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Canvas(
                            modifier = Modifier
                                .size(14.dp, 20.dp)
                                .offset(x = 1.dp, y = (-18).dp)
                        ) {
                            val path = Path().apply {
                                moveTo(size.width, 0f)
                                lineTo(0f, size.height * 0.5f)
                                lineTo(size.width, size.height)
                                close()
                            }
                            drawPath(path, color = Color(0xF5141522))
                            drawPath(
                                path,
                                color = Color(0xFF00E5FF),
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xF8151725),
                                            Color(0xF20F101A)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.8.dp,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF00E5FF),
                                            Color(0xFF3D5AFE),
                                            Color(0xFFFF4081)
                                        )
                                    ),
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFF00E5FF), Color(0xFF3D5AFE))
                                                )
                                            )
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "COMMUNITY SUBTITLE",
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "by ${info.uploaderName}",
                                        color = Color(0xFFFFD54F),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "✕",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { showSaveToPublicCard = false }
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "AI Transcription complete for Ep ${info.episodeNum} (${info.langName})! Share to Public Subtitles so other anime fans can enjoy?",
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { showSaveToPublicCard = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33333E)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Skip", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        showSaveToPublicCard = false
                                        coroutineScope.launch {
                                            val success = com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitlesManager.uploadPublicSubtitle(
                                                context = context,
                                                anilistId = info.anilistId,
                                                episodeNum = info.episodeNum,
                                                animeTitle = info.animeTitle,
                                                langCode = info.langCode,
                                                langName = info.langName,
                                                vttContent = info.vttContent,
                                                customUploaderName = info.uploaderName
                                            )
                                            if (success) {
                                                android.widget.Toast.makeText(context, "Saved to Public Subtitles! Thank you for contributing!", android.widget.Toast.LENGTH_LONG).show()
                                                val updated = com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitlesManager.fetchPublicSubtitles(info.anilistId, info.episodeNum)
                                                publicSubtitlesList = updated
                                            } else {
                                                android.widget.Toast.makeText(context, "Failed to share subtitle to Public.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Save to Public", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ─── Persona-Style Casting Coming Soon Dialogue Box (Centered Top-Middle) ───
            AnimatedVisibility(
                visible = showCastDialog,
                enter = fadeIn(tween(350)) + scaleIn(tween(400, easing = FastOutSlowInEasing), initialScale = 0.85f) + slideInVertically(initialOffsetY = { -it / 3 }),
                exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.85f) + slideOutVertically(targetOffsetY = { -it / 3 })
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 22.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showCastDialog = false },
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // 1. Chibi Character Avatar (Firefly mascot)
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .offset(y = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_thank_chibi),
                                contentDescription = "Chibi",
                                modifier = Modifier
                                    .size(76.dp)
                                    .shadow(12.dp, shape = CircleShape, spotColor = Color(0xFFFF4081)),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // 2. Speech Bubble Tail
                        Canvas(
                            modifier = Modifier
                                .size(14.dp, 20.dp)
                                .offset(x = 1.dp, y = (-18).dp)
                        ) {
                            val path = Path().apply {
                                moveTo(size.width, 0f)
                                lineTo(0f, size.height * 0.5f)
                                lineTo(size.width, size.height)
                                close()
                            }
                            drawPath(path, color = Color(0xF5141522))
                            drawPath(
                                path,
                                color = Color(0xFFFF4081),
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // 3. Persona Dialogue Bubble Body
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xF8151725),
                                            Color(0xF20F101A)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.8.dp,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFFF4081), // Neon Pink
                                            Color(0xFF9C27B0), // Persona Purple
                                            Color(0xFF00E5FF)  // Electric Cyan
                                        )
                                    ),
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            // Top Bar with Badge & Title
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFFFF1493), Color(0xFFFF69B4))
                                                )
                                            )
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "CASTING",
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Coming Soon ✨",
                                        color = Color(0xFFFFD54F),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "✕",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { showCastDialog = false }
                                )
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            Text(
                                text = "TV and device casting is currently being crafted with love and will be available in an upcoming update! Thank you for enjoying AnimeBox.",
                                color = Color(0xFFF1F3FF),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ─── Audio & Subtitles Drawer ──────────────────────────────────────
            AudioSubtitlesDrawerPanel(
                show = showAudioSubtitlesPanel,
                audioSubTab = audioSubTab,
                onTabChange = { audioSubTab = it },
                showSubtitleStyleSettings = showSubtitleStyleSettings,
                onToggleStyleSettings = { showSubtitleStyleSettings = it },
                selectedAudio = selectedAudio,
                selectedSub = selectedSub,
                currentBackupHls = currentBackupHls,
                translatedSubMap = translatedSubMap,
                subFontSize = subFontSize,
                subTextColor = subTextColor,
                subBgOpacity = subBgOpacity,
                subEdgeType = subEdgeType,
                subPreset = subPreset,
                isTranslatingSub = isTranslatingSub,
                isExternalVideo = isExternalAction,
                onPickCustomSubtitle = {
                    try {
                        subtitlePickerLauncher.launch(arrayOf("text/*", "application/x-subrip", "*/*"))
                    } catch (e: Exception) {
                        try {
                            subtitlePickerLauncher.launch(arrayOf("*/*"))
                        } catch (ex: Exception) {
                            android.widget.Toast.makeText(context, "Cannot open file picker", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onClose = { showAudioSubtitlesPanel = false },
                onAudioSelected = { track ->
                    if (track != selectedAudio) {
                        val newType = if (track == "Hindi") "hindi" else if (track == "English") "dub" else "sub"
                        if ((track == "English" || track == "Hindi") && selectedSub == "Hard Sub") {
                            selectedSub = "Off"
                            prefs.edit().putString("selectedSub", "Off").apply()
                            if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context)) {
                                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setLastUsedSubMode(context, "Off")
                            }
                        }
                        isReloadingStream = true
                        startPosition = player.currentPosition
                        coroutineScope.launch {
                            val streamInfo = withContext(Dispatchers.IO) {
                                fetchStreamInfo(anilistId, currentEpisodeNum, newType)
                            }
                            isReloadingStream = false
                            val hasHls = streamInfo != null && (streamInfo["hls"] as? String)?.isNotEmpty() == true
                            if (hasHls) {
                                selectedAudio = track
                                currentStreamType = newType
                                prefs.edit().putString("selectedAudio", track).apply()
                                if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context)) {
                                    com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setLastUsedAudio(context, track)
                                }
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
                                android.widget.Toast.makeText(context, "Audio: $track", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isShowFallbackDialogsEnabled(context)) {
                                    val (tTitle, tDesc) = if (track == "Hindi") {
                                        "Hindi Dub Unavailable" to "Hindi dubbed audio is currently not available for this anime episode. Would you like to switch to Japanese audio?"
                                    } else {
                                        "English Dub Unavailable" to "English dubbed audio is not available for this anime episode. Would you like to switch to the original Japanese audio?"
                                    }
                                    fallbackDialogData = FallbackDialogData(
                                        title = tTitle,
                                        description = tDesc,
                                        primaryButtonText = "Switch to Japanese",
                                        onPrimaryAction = {
                                            selectedAudio = "Japanese (Original)"
                                            currentStreamType = "sub"
                                            if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context)) {
                                                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setLastUsedAudio(context, "Japanese (Original)")
                                            }
                                            coroutineScope.launch {
                                                isReloadingStream = true
                                                val sInfo = withContext(Dispatchers.IO) {
                                                    fetchStreamInfo(anilistId, currentEpisodeNum, "sub")
                                                }
                                                isReloadingStream = false
                                                if (sInfo != null) {
                                                    currentHlsUrl = sInfo["hls"] as String
                                                    currentReferer = sInfo["referer"] as String
                                                    currentSubtitleUrl = (sInfo["subtitle"] as? String) ?: ""
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    android.widget.Toast.makeText(context, "$track audio is not available for this anime", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        showAudioSubtitlesPanel = false
                    }
                },
                onSubSelected = { sub ->
                    if (sub != selectedSub) {
                        val oldSub = selectedSub
                        selectedSub = sub
                        prefs.edit().putString("selectedSub", sub).apply()
                        if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context)) {
                            com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setLastUsedSubMode(context, sub)
                        }
                        
                        if (sub == "Hard Sub") {
                            selectedAudio = "Japanese (Original)"
                            currentStreamType = "hardsub"
                            prefs.edit().putString("selectedAudio", "Japanese (Original)").apply()
                            if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context)) {
                                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setLastUsedAudio(context, "Japanese (Original)")
                            }
                            startPosition = player.currentPosition
                            isReloadingStream = true
                            coroutineScope.launch {
                                val hardInfo = withContext(Dispatchers.IO) {
                                    fetchStreamInfo(anilistId, currentEpisodeNum, "hardsub")
                                }
                                isReloadingStream = false
                                if (hardInfo != null && (hardInfo["hls"] as? String)?.isNotEmpty() == true) {
                                    currentHlsUrl = hardInfo["hls"] as String
                                    currentReferer = (hardInfo["referer"] as? String) ?: ""
                                    currentProvider = (hardInfo["provider"] as? String) ?: ""
                                    val prov = currentProvider
                                    val subUrl = (hardInfo["subtitle"] as? String) ?: ""
                                    val isAniDb = prov.contains("anibd", ignoreCase = true) || prov.contains("anidb", ignoreCase = true) || currentReferer.contains("animeapps", ignoreCase = true) || currentReferer.contains("anibd", ignoreCase = true)
                                    if (isAniDb && subUrl.isNotEmpty()) {
                                        currentSubtitleUrl = subUrl
                                        subPreset = "Hardsub"
                                        prefs.edit().putString("subPreset", "Hardsub").apply()
                                        selectedSub = "English (Soft Sub)"
                                        prefs.edit().putString("selectedSub", "English (Soft Sub)").apply()
                                        android.widget.Toast.makeText(context, "AniBD: Hard Sub with Soft Subtitles", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        currentSubtitleUrl = ""
                                        selectedSub = "Hard Sub"
                                        android.widget.Toast.makeText(context, "Subtitles: Hard Sub (Japanese Audio)", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isShowFallbackDialogsEnabled(context)) {
                                        fallbackDialogData = FallbackDialogData(
                                            title = "Hard Subtitles Unavailable",
                                            description = "• Soft Subtitles: Text is overlaid dynamically and can be styled, colored, or translated.\n• Hard Subtitles: Subtitles are permanently burned into the video stream for maximum compatibility.\n\nHard Subtitles are not available for this episode. Would you like to switch to Soft Subtitles (English VTT)?",
                                            primaryButtonText = "Switch to Soft Sub",
                                            onPrimaryAction = {
                                                selectedSub = "English (VTT)"
                                                if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context)) {
                                                    com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setLastUsedSubMode(context, "English (VTT)")
                                                }
                                                coroutineScope.launch {
                                                    isReloadingStream = true
                                                    val sInfo = withContext(Dispatchers.IO) {
                                                        fetchStreamInfo(anilistId, currentEpisodeNum, if (selectedAudio == "English") "dub" else "sub")
                                                    }
                                                    isReloadingStream = false
                                                    if (sInfo != null) {
                                                        currentHlsUrl = sInfo["hls"] as String
                                                        currentReferer = sInfo["referer"] as String
                                                        currentProvider = (sInfo["provider"] as? String) ?: ""
                                                        currentSubtitleUrl = (sInfo["subtitle"] as? String) ?: ""
                                                    }
                                                }
                                            }
                                        )
                                    } else {
                                        android.widget.Toast.makeText(context, "Hard Subtitles not available for this episode", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else if (sub == "English (Soft Sub)") {
                            selectedSub = "English (Soft Sub)"
                            subPreset = "Hardsub"
                            prefs.edit().putString("selectedSub", "English (Soft Sub)").putString("subPreset", "Hardsub").apply()
                            liveSubtitleCues = emptyList()
                        } else if (sub == "English (VTT)") {
                            selectedSub = "English (VTT)"
                            prefs.edit().putString("selectedSub", "English (VTT)").apply()
                            liveSubtitleCues = emptyList()

                            if (currentStreamType == "hardsub") {
                                currentStreamType = "sub"
                                startPosition = player.currentPosition
                                isReloadingStream = true
                                coroutineScope.launch {
                                    val softInfo = withContext(Dispatchers.IO) {
                                        fetchStreamInfo(anilistId, currentEpisodeNum, "sub")
                                    }
                                    isReloadingStream = false
                                    if (softInfo != null && (softInfo["hls"] as? String)?.isNotEmpty() == true) {
                                        currentHlsUrl = softInfo["hls"] as String
                                        currentReferer = (softInfo["referer"] as? String) ?: ""
                                        val softSub = (softInfo["subtitle"] as? String) ?: ""
                                        if (softSub.isNotEmpty()) {
                                            currentSubtitleUrl = softSub
                                        }
                                        android.widget.Toast.makeText(context, "Subtitles: English (VTT) - Soft Sub", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                val origSub = if (originalSubtitleUrl.isNotEmpty()) originalSubtitleUrl else subtitleUrl
                                if (origSub.isNotEmpty()) {
                                    currentSubtitleUrl = origSub
                                }
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                    .setPreferredTextLanguage("en")
                                    .setSelectUndeterminedTextLanguage(true)
                                    .setIgnoredTextSelectionFlags(0)
                                    .build()
                                playerViewInstance?.subtitleView?.visibility = View.VISIBLE
                                val nativeCues = player.currentCues.cues
                                playerViewInstance?.subtitleView?.setCues(nativeCues)
                                android.widget.Toast.makeText(context, "Subtitles: English (VTT)", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else if (sub.contains("Translated", ignoreCase = true) || sub.contains("AI", ignoreCase = true) || translatedSubMap.containsKey(sub)) {
                            var transFilePath = translatedSubMap[sub]
                            if (transFilePath == null && sub.contains("English", ignoreCase = true)) {
                                transFilePath = translatedSubMap["English (AI Translated)"]
                            }
                            if (transFilePath == null) {
                                val allLangs = listOf(com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.ENGLISH_MODEL) + com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.SUPPORTED_LANGUAGES
                                val langInfo = allLangs.firstOrNull { sub.contains(it.langName, ignoreCase = true) }
                                if (langInfo != null) {
                                    val savedFile = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.getSavedSubtitleFile(context, anilistId, currentEpisodeNum, langInfo.langCode)
                                    if (savedFile.exists() && savedFile.length() > 150) {
                                        val cues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(savedFile.readText())
                                        if (cues.size >= 2) {
                                            transFilePath = savedFile.absolutePath
                                            translatedSubMap = translatedSubMap + (sub to transFilePath)
                                        }
                                    }
                                }
                            }
                            if (transFilePath != null) {
                                val f = java.io.File(transFilePath)
                                if (f.exists() && f.length() > 150) {
                                    val vttContent = f.readText()
                                    val cues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(vttContent)
                                    if (cues.size >= 2) {
                                        liveSubtitleCues = cues
                                        val allLangs = listOf(com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.ENGLISH_MODEL) + com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.SUPPORTED_LANGUAGES
                                        val targetLang = allLangs.firstOrNull { sub.contains(it.langName, ignoreCase = true) } ?: com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.ENGLISH_MODEL
                                        val isAudioModeSub = (originalSubtitleUrl.isEmpty() && currentSubtitleUrl.isEmpty()) || sub.contains("SenseVoice", ignoreCase = true) || sub.contains("AI Translated", ignoreCase = true)
                                        if (isAudioModeSub && anilistId > 0 && currentEpisodeNum > 0 && !com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitlesManager.hasSavedToPublic(context, anilistId, currentEpisodeNum, targetLang.langCode)) {
                                            pendingPublicSubInfo = PendingPublicSubData(
                                                anilistId = anilistId,
                                                episodeNum = currentEpisodeNum,
                                                animeTitle = animeTitle,
                                                langCode = targetLang.langCode,
                                                langName = targetLang.langName,
                                                vttContent = vttContent,
                                                uploaderName = com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitlesManager.getEffectiveUploaderName(context)
                                            )
                                            showSaveToPublicCard = true
                                        }
                                    }
                                }
                            }
                            selectedSub = sub
                            prefs.edit().putString("selectedSub", sub).apply()

                            if (currentStreamType == "hardsub") {
                                currentStreamType = "sub"
                                startPosition = player.currentPosition
                                isReloadingStream = true
                                coroutineScope.launch {
                                    val softInfo = withContext(Dispatchers.IO) {
                                        fetchStreamInfo(anilistId, currentEpisodeNum, "sub")
                                    }
                                    isReloadingStream = false
                                    if (softInfo != null && (softInfo["hls"] as? String)?.isNotEmpty() == true) {
                                        currentHlsUrl = softInfo["hls"] as String
                                        currentReferer = (softInfo["referer"] as? String) ?: ""
                                        val softSub = (softInfo["subtitle"] as? String) ?: ""
                                        if (softSub.isNotEmpty()) {
                                            currentSubtitleUrl = softSub
                                        }
                                    }
                                }
                            }
                            android.widget.Toast.makeText(context, "Subtitles: $sub", android.widget.Toast.LENGTH_SHORT).show()
                        } else if (sub == "Off") {
                            selectedSub = "Off"
                            prefs.edit().putString("selectedSub", "Off").apply()
                            liveSubtitleCues = emptyList()
                            android.widget.Toast.makeText(context, "Subtitles: Off", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onFontSizeChanged = { size ->
                    subFontSize = size
                    prefs.edit().putFloat("subFontSize", size).apply()
                },
                onTextColorChanged = { colorVal ->
                    subTextColor = colorVal
                    prefs.edit().putInt("subTextColor", colorVal).apply()
                },
                onBgOpacityChanged = { opacityVal ->
                    subBgOpacity = opacityVal
                    prefs.edit().putInt("subBgOpacity", opacityVal).apply()
                },
                onEdgeTypeChanged = { edgeTypeVal ->
                    subEdgeType = edgeTypeVal
                    prefs.edit().putInt("subEdgeType", edgeTypeVal).apply()
                },
                onPresetSelected = { presetName, fSize, tColor, bgOp, edge ->
                    subPreset = presetName
                    subFontSize = fSize
                    subTextColor = tColor
                    subBgOpacity = bgOp
                    subEdgeType = edge
                    prefs.edit()
                        .putString("subPreset", presetName)
                        .putFloat("subFontSize", fSize)
                        .putInt("subTextColor", tColor)
                        .putInt("subBgOpacity", bgOp)
                        .putInt("subEdgeType", edge)
                        .apply()
                },
                onRequestTranslate = {
                    showTranslateSubDialog = true
                },
                isAudioMode = currentSubtitleUrl.isEmpty() && selectedSub != "Hard Sub",
                publicSubtitlesList = publicSubtitlesList,
                onPublicSubSelected = { pubSub ->
                    try {
                        val subKey = "${pubSub.langName} (Public)"
                        selectedSub = subKey
                        prefs.edit().putString("selectedSub", subKey).apply()
                        if (currentStreamType == "hardsub") {
                            currentStreamType = "sub"
                            coroutineScope.launch {
                                try {
                                    val fbMap = fetchStreamInfo(anilistId, currentEpisodeNum, "sub")
                                    if (fbMap != null && (fbMap["hls"] as? String)?.isNotEmpty() == true) {
                                        currentHlsUrl = fbMap["hls"] as String
                                        currentReferer = (fbMap["referer"] as? String) ?: ""
                                        val fbSub = (fbMap["subtitle"] as? String) ?: ""
                                        if (fbSub.isNotEmpty()) {
                                            currentSubtitleUrl = fbSub
                                        }
                                    }
                                } catch (_: Throwable) {}
                            }
                        }
                        val cues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(pubSub.vttContent)
                        if (cues.isNotEmpty()) {
                            liveSubtitleCues = cues
                            val targetAnilistId = if (currentAnilistId > 0) currentAnilistId else anilistId
                            val safeLangCode = pubSub.langCode.replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")
                            val savedFile = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.getSavedSubtitleFile(context, targetAnilistId, currentEpisodeNum, safeLangCode)
                            try {
                                savedFile.parentFile?.mkdirs()
                                savedFile.writeText(pubSub.vttContent)
                                translatedSubMap = translatedSubMap + (subKey to savedFile.absolutePath)
                            } catch (_: Exception) {}
                        }
                        android.widget.Toast.makeText(context, "Loaded ${pubSub.langName} public subtitles by ${pubSub.uploaderName}", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        android.widget.Toast.makeText(context, "Loaded ${pubSub.langName} public subtitles", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // ─── Speed & Quality Drawer ────────────────────────────────────────
            SpeedQualityDrawerPanel(
                show = showSpeedQualityPanel,
                speedQualityTab = speedQualityTab,
                onTabChange = { speedQualityTab = it },
                currentQuality = currentQuality,
                videoTrackHeights = videoTrackHeights,
                isGraphicsUpscalerEnabled = isGraphicsUpscalerEnabled,
                showShaderSettings = showShaderSettings,
                enhancerPreset = enhancerPreset,
                enhancerSharpness = enhancerSharpness,
                enhancerContrast = enhancerContrast,
                enhancerSaturation = enhancerSaturation,
                enhancerLineClarity = enhancerLineClarity,
                deviceSpecs = deviceSpecs,
                currentSpeed = currentSpeed,
                onClose = {
                    showShaderSettings = false
                    showSpeedQualityPanel = false
                },
                onQualitySelected = { qualKey ->
                    currentQuality = qualKey
                    val (maxW, maxH) = when {
                        qualKey == "Auto" -> Pair(Int.MAX_VALUE, Int.MAX_VALUE)
                        qualKey.endsWith("p") -> {
                            val h = qualKey.removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE
                            val w = (h * 16) / 9
                            Pair(w, h)
                        }
                        else -> Pair(Int.MAX_VALUE, Int.MAX_VALUE)
                    }
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon().setMaxVideoSize(maxW, maxH).build()
                    android.widget.Toast.makeText(context, "Quality: $qualKey", android.widget.Toast.LENGTH_SHORT).show()
                },
                onSpeedSelected = { spd, label ->
                    currentSpeed = spd
                    player.setPlaybackSpeed(spd)
                    prefs.edit().putFloat("playbackSpeed", spd).apply()
                    android.widget.Toast.makeText(context, "Playback speed: $label", android.widget.Toast.LENGTH_SHORT).show()
                },
                onUpscalerToggled = { enabled ->
                    isGraphicsUpscalerEnabled = enabled
                    prefs.edit().putBoolean("graphics_upscaler_enabled", enabled).apply()
                    android.widget.Toast.makeText(
                        context,
                        if (enabled) "✨ Graphics Enhancer Activated" else "Graphics Enhancer Disabled",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                onToggleShaderSettings = { showShaderSettings = it },
                onPresetSelected = { preset ->
                    enhancerPreset = preset
                    enhancerSharpness = preset.defaultSharpness
                    enhancerContrast = preset.defaultContrast
                    enhancerSaturation = preset.defaultSaturation
                    enhancerLineClarity = preset.defaultLineClarity
                    prefs.edit()
                        .putString("enhancer_preset_id", preset.id)
                        .putFloat("enhancer_sharpness", preset.defaultSharpness)
                        .putFloat("enhancer_contrast", preset.defaultContrast)
                        .putFloat("enhancer_saturation", preset.defaultSaturation)
                        .putFloat("enhancer_line_clarity", preset.defaultLineClarity)
                        .apply()
                },
                onSharpnessChanged = { v ->
                    enhancerSharpness = v
                    enhancerPreset = com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.CUSTOM
                    prefs.edit()
                        .putString("enhancer_preset_id", com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.CUSTOM.id)
                        .putFloat("enhancer_sharpness", v)
                        .apply()
                },
                onContrastChanged = { v ->
                    enhancerContrast = v
                    enhancerPreset = com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.CUSTOM
                    prefs.edit()
                        .putString("enhancer_preset_id", com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.CUSTOM.id)
                        .putFloat("enhancer_contrast", v)
                        .apply()
                },
                onSaturationChanged = { v ->
                    enhancerSaturation = v
                    enhancerPreset = com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.CUSTOM
                    prefs.edit()
                        .putString("enhancer_preset_id", com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.CUSTOM.id)
                        .putFloat("enhancer_saturation", v)
                        .apply()
                },
                onLineClarityChanged = { v ->
                    enhancerLineClarity = v
                    enhancerPreset = com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.CUSTOM
                    prefs.edit()
                        .putString("enhancer_preset_id", com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.CUSTOM.id)
                        .putFloat("enhancer_line_clarity", v)
                        .apply()
                }
            )

            // ─── Episodes Series Carousel Panel ───────────────────────────────────
            val currentActiveLanguageState = if (selectedSub == "Hard Sub" || currentStreamType == "hardsub") {
                "hardsub"
            } else if (selectedAudio == "Hindi" || currentStreamType == "hindi") {
                "hindi"
            } else if (selectedAudio == "English" || currentStreamType == "dub") {
                "dub"
            } else {
                "sub"
            }

            EpisodesDrawerPanel(
                show = showEpisodesPanel,
                currentEpisodeNum = currentEpisodeNum,
                totalEpisodes = totalEpisodes,
                animeTitle = currentAnimeTitle,
                coverUrl = currentCoverUrl,
                showCoverUrl = currentShowCoverUrl,
                animePosterUrl = animePosterUrl,
                anilistId = currentAnilistId,
                futureEps = futureEps,
                episodeMetaMap = episodeMetaMap,
                episodeImages = episodeImages,
                episodeTitles = episodeTitles,
                relatedAnimeList = relatedAnimeList,
                activeStreamType = currentActiveLanguageState,
                onClose = { showEpisodesPanel = false },
                onEpisodeSelected = { ep ->
                    showEpisodesPanel = false
                    if (ep != currentEpisodeNum) {
                        switchToEpisode(ep)
                    }
                },
                onSwitchToRelatedAnime = { relId, relTitle, relCover, targetEp ->
                    showEpisodesPanel = false
                    switchToRelatedAnime(relId, relTitle, relCover, targetEp)
                }
            )

            // Loading overlay for next episode
            if (isNextEpLoading || isReloadingStream) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = playerAccentColor)
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


            // ─── Resume Dialog ───
            if (showResumeDialog && savedProgress > 0L) {
                ResumeDialogContent(
                    savedProgress = savedProgress,
                    player = player,
                    onDismiss = { showResumeDialog = false }
                )
            }

            // ─── Smart Fallback Dialog ───
            if (fallbackDialogData != null) {
                val fData = fallbackDialogData!!
                SmartFallbackDialogContent(
                    title = fData.title,
                    description = fData.description,
                    primaryButtonText = fData.primaryButtonText,
                    onPrimaryAction = fData.onPrimaryAction,
                    onDismiss = { dontShowAgain ->
                        if (dontShowAgain) {
                            com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setShowFallbackDialogsEnabled(context, false)
                        }
                        fallbackDialogData = null
                    }
                )
            }

        // ─── Subtitle Auto-Translation Dialog ───
        if (showTranslateSubDialog) {
            TranslationDialogContent(
                currentSubtitleUrl = currentSubtitleUrl,
                subtitleUrl = subtitleUrl,
                originalSubtitleUrl = originalSubtitleUrl,
                selectedSub = selectedSub,
                currentHlsUrl = currentHlsUrl,
                currentReferer = currentReferer,
                anilistId = anilistId,
                currentEpisodeNum = currentEpisodeNum,
                animeTitle = animeTitle,
                coverUrl = showCoverUrl,
                player = player,
                prefs = prefs,
                coroutineScope = coroutineScope,
                onDismiss = { showTranslateSubDialog = false },
                onTranslatingStateChanged = { isTranslatingSub = it },
                onTranslatedSubMapChanged = { translatedSubMap = it },
                translatedSubMap = translatedSubMap,
                onSelectedSubChanged = { selectedSub = it },
                onLiveStatusChanged = { liveTranslatingStatus = it },
                onLiveCuesChanged = { liveSubtitleCues = it },
                onRequestModelDownload = { info ->
                    modelDownloadPrompt = info
                },
                onRequestLocalModelWarning = { info ->
                    localModelWarningPrompt = info
                },
                isAudioMode = currentSubtitleUrl.isEmpty() && selectedSub != "Hard Sub",
                publicSubtitlesList = publicSubtitlesList,
                onTriggerSaveToPublic = { pending ->
                    pendingPublicSubInfo = pending
                    showSaveToPublicCard = true
                }
            )
        }

        // ─── Local AI Model High-End Device Warning Dialog (Persona Style UI) ───
        localModelWarningPrompt?.let { modelInfo ->
            LocalModelWarningDialog(
                modelInfo = modelInfo,
                onContinue = {
                    val info = modelInfo
                    localModelWarningPrompt = null
                    com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.setSelectedSpeechModel(context, info.langCode)
                    val isDl = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.isModelDownloaded(context, info.langCode)
                    if (!isDl) {
                        modelDownloadPrompt = info
                    } else {
                        android.widget.Toast.makeText(context, "Selected on-device ${info.langName}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = {
                    localModelWarningPrompt = null
                }
            )
        }

        // ─── AI Model Download Prompt Dialog (Resume Style UI) ───
        modelDownloadPrompt?.let { modelInfo ->
            ModelDownloadPromptDialog(
                modelInfo = modelInfo,
                onConfirm = {
                    val infoToDownload = modelInfo
                    activeModelDownloadProgress = com.lagradost.cloudstream3.ui.animebox.api.ModelDownloadProgress(
                        langName = infoToDownload.langName,
                        langCode = infoToDownload.langCode,
                        percent = 0,
                        downloadedBytes = 0,
                        totalBytes = infoToDownload.sizeBytes,
                        speedBps = 0
                    )
                    modelDownloadPrompt = null
                    val isSenseVoice = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.JAPANESE_SPEECH_MODELS.any { it.langCode.equals(infoToDownload.langCode, true) }
                    activeModelDownloadJob?.cancel()
                    activeModelDownloadJob = coroutineScope.launch {
                        try {
                            val savedFile = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.isCompleteSavedSubtitle(context, anilistId, currentEpisodeNum, "en")
                            if (savedFile != null) {
                                val existingCues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(savedFile.readText())
                                if (existingCues.size >= 5) {
                                    liveSubtitleCues = existingCues
                                    translatedSubMap = translatedSubMap + ("English (AI Translated)" to savedFile.absolutePath)
                                    android.widget.Toast.makeText(context, "Loaded saved English subtitles", android.widget.Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                            }
                            val success = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.downloadModel(
                                context,
                                infoToDownload.langCode
                            ) { progress ->
                                activeModelDownloadProgress = progress
                            }
                            activeModelDownloadProgress = null
                            if (success) {
                                if (isSenseVoice) {
                                    com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.setSelectedSpeechModel(context, infoToDownload.langCode)
                                    val transLabel = "English (AI Translated)"
                                    selectedSub = transLabel
                                    prefs.edit().putString("selectedSub", transLabel).apply()
                                    val savedFile = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.isCompleteSavedSubtitle(context, anilistId, currentEpisodeNum, "en")
                                    if (savedFile != null) {
                                        val existingCues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(savedFile.readText())
                                        if (existingCues.size >= 5) {
                                            liveSubtitleCues = existingCues
                                            translatedSubMap = translatedSubMap + (transLabel to savedFile.absolutePath)
                                            android.widget.Toast.makeText(context, "Loaded saved English subtitles", android.widget.Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                    }
                                    isTranslatingSub = true
                                    liveTranslatingStatus = "Transcribing Japanese audio with ${infoToDownload.langName}..."
                                    liveSubtitleCues = emptyList()
                                    val localFilePath = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.transcribeAndTranslateEpisodeAudio(
                                        context = context,
                                        hlsOrVideoUrl = currentHlsUrl,
                                        referer = currentReferer,
                                        targetLangCode = "en",
                                        speechModelCode = infoToDownload.langCode,
                                        anilistId = anilistId,
                                        episodeNum = currentEpisodeNum,
                                        animeTitle = animeTitle,
                                        coverUrl = showCoverUrl,
                                        knownSubtitleUrl = if (originalSubtitleUrl.isNotEmpty()) originalSubtitleUrl else subtitleUrl.ifEmpty { currentSubtitleUrl },
                                        onProgress = { cur, total, tempPath, cues ->
                                            liveTranslatingStatus = "Transcribing Japanese audio ($cur/$total)"
                                            liveSubtitleCues = cues
                                            if (!translatedSubMap.containsKey(transLabel)) {
                                                translatedSubMap = translatedSubMap + (transLabel to tempPath)
                                            }
                                        }
                                    )
                                    isTranslatingSub = false
                                    liveTranslatingStatus = null
                                    if (localFilePath != null) {
                                        translatedSubMap = translatedSubMap + (transLabel to localFilePath)
                                        val f = java.io.File(localFilePath)
                                        if (f.exists() && f.length() > 150) {
                                            val vttTxt = f.readText()
                                            liveSubtitleCues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(vttTxt)
                                            pendingPublicSubInfo = PendingPublicSubData(
                                                anilistId = anilistId,
                                                episodeNum = currentEpisodeNum,
                                                animeTitle = animeTitle,
                                                langCode = "en",
                                                langName = infoToDownload.langName,
                                                vttContent = vttTxt,
                                                uploaderName = com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitlesManager.getEffectiveUploaderName(context)
                                            )
                                            showSaveToPublicCard = true
                                        }
                                        android.widget.Toast.makeText(context, "Subtitles generated from Japanese audio with ${infoToDownload.langName}!", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        android.widget.Toast.makeText(context, "Failed to transcribe audio. Please try again.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val isEn = infoToDownload.langCode.equals("en", true)
                                    val transLabel = if (isEn) "English (AI Translated)" else "${infoToDownload.langName} (AI Translated)"
                                    selectedSub = transLabel
                                    prefs.edit().putString("selectedSub", transLabel).apply()
                                    val savedFile = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.isCompleteSavedSubtitle(context, anilistId, currentEpisodeNum, infoToDownload.langCode)
                                    if (savedFile != null) {
                                        val existingCues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(savedFile.readText())
                                        if (existingCues.size >= 5) {
                                            liveSubtitleCues = existingCues
                                            translatedSubMap = translatedSubMap + (transLabel to savedFile.absolutePath)
                                            android.widget.Toast.makeText(context, "Loaded saved ${infoToDownload.langName} subtitles", android.widget.Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                    }
                                    val isAudioMode = currentSubtitleUrl.isEmpty() && selectedSub != "Hard Sub"
                                    if (isAudioMode) {
                                        isTranslatingSub = true
                                        val activeSpeech = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.getSelectedSpeechModel(context)
                                        liveTranslatingStatus = "Transcribing Japanese audio with ${activeSpeech.langName}..."
                                        liveSubtitleCues = emptyList()
                                        val localFilePath = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.transcribeAndTranslateEpisodeAudio(
                                            context = context,
                                            hlsOrVideoUrl = currentHlsUrl,
                                            referer = currentReferer,
                                            targetLangCode = infoToDownload.langCode,
                                            speechModelCode = activeSpeech.langCode,
                                            anilistId = anilistId,
                                            episodeNum = currentEpisodeNum,
                                            animeTitle = animeTitle,
                                            coverUrl = showCoverUrl,
                                            knownSubtitleUrl = if (originalSubtitleUrl.isNotEmpty()) originalSubtitleUrl else subtitleUrl.ifEmpty { currentSubtitleUrl },
                                            onProgress = { cur, total, tempPath, cues ->
                                                liveTranslatingStatus = "Transcribing Japanese audio ($cur/$total)"
                                                liveSubtitleCues = cues
                                                if (!translatedSubMap.containsKey(transLabel)) {
                                                    translatedSubMap = translatedSubMap + (transLabel to tempPath)
                                                }
                                            }
                                        )
                                        isTranslatingSub = false
                                        liveTranslatingStatus = null
                                        if (localFilePath != null) {
                                            translatedSubMap = translatedSubMap + (transLabel to localFilePath)
                                            val f = java.io.File(localFilePath)
                                            if (f.exists() && f.length() > 150) {
                                                liveSubtitleCues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(f.readText())
                                            }
                                            android.widget.Toast.makeText(context, "Subtitles translated to ${infoToDownload.langName}!", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Failed to transcribe audio. Please try again.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        liveTranslatingStatus = "Translating subtitles to ${infoToDownload.langName}..."
                                        liveSubtitleCues = emptyList()
                                        val subToTranslate = when {
                                            originalSubtitleUrl.isNotEmpty() -> originalSubtitleUrl
                                            currentSubtitleUrl.isNotEmpty() -> currentSubtitleUrl
                                            subtitleUrl.isNotEmpty() -> subtitleUrl
                                            else -> ""
                                        }
                                        val localFilePath = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.translateSubtitleVtt(
                                            context = context,
                                            vttUrlOrPath = subToTranslate,
                                            targetLangCode = infoToDownload.langCode,
                                            hlsUrl = currentHlsUrl,
                                            referer = currentReferer,
                                            anilistId = anilistId,
                                            episodeNum = currentEpisodeNum,
                                            animeTitle = animeTitle,
                                            coverUrl = showCoverUrl,
                                            onProgress = { cur, total, tempPath, cues ->
                                                liveTranslatingStatus = "Translating to ${infoToDownload.langName} ($cur/$total)"
                                                liveSubtitleCues = cues
                                                if (!translatedSubMap.containsKey(transLabel)) {
                                                    translatedSubMap = translatedSubMap + (transLabel to tempPath)
                                                }
                                            }
                                        )
                                        isTranslatingSub = false
                                        liveTranslatingStatus = null
                                        if (localFilePath != null) {
                                            translatedSubMap = translatedSubMap + (transLabel to localFilePath)
                                            val f = java.io.File(localFilePath)
                                            if (f.exists() && f.length() > 150) {
                                                liveSubtitleCues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(f.readText())
                                            }
                                            android.widget.Toast.makeText(context, "Subtitles translated to ${infoToDownload.langName}!", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Failed to translate subtitles. Please try again.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Download failed for ${infoToDownload.langName} AI model.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (t: Throwable) {
                            isTranslatingSub = false
                            liveTranslatingStatus = null
                            activeModelDownloadProgress = null
                            android.widget.Toast.makeText(context, "Transcription error: ${t.localizedMessage ?: "Failed to process"}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = { modelDownloadPrompt = null }
            )
        }

        // ─── AI Model Download Progress Overlay (App Updater Style UI) ───
        activeModelDownloadProgress?.let { progress ->
            ModelDownloadProgressOverlay(
                progress = progress,
                onCancel = {
                    activeModelDownloadJob?.cancel()
                    activeModelDownloadProgress = null
                }
            )
        }

        // ─── Floating Live Subtitle Translation Indicator (Top Right) ───
        if (liveTranslatingStatus != null && !isInPipMode) {
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
                            color = playerAccentColor,
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
    }
}
}

    @Composable
    private fun AudioSubtitlesDrawerPanel(
        show: Boolean,
        audioSubTab: Int,
        onTabChange: (Int) -> Unit,
        showSubtitleStyleSettings: Boolean,
        onToggleStyleSettings: (Boolean) -> Unit,
        selectedAudio: String,
        selectedSub: String,
        currentBackupHls: String,
        translatedSubMap: Map<String, String>,
        subFontSize: Float,
        subTextColor: Int,
        subBgOpacity: Int,
        subEdgeType: Int,
        subPreset: String,
        isTranslatingSub: Boolean,
        isExternalVideo: Boolean = false,
        onPickCustomSubtitle: () -> Unit = {},
        onClose: () -> Unit,
        onAudioSelected: (String) -> Unit,
        onSubSelected: (String) -> Unit,
        onFontSizeChanged: (Float) -> Unit,
        onTextColorChanged: (Int) -> Unit,
        onBgOpacityChanged: (Int) -> Unit,
        onEdgeTypeChanged: (Int) -> Unit,
        onPresetSelected: (presetName: String, fSize: Float, tColor: Int, bgOp: Int, edge: Int) -> Unit,
        onRequestTranslate: () -> Unit,
        isAudioMode: Boolean = false,
        publicSubtitlesList: List<com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitleItem> = emptyList(),
        onPublicSubSelected: (com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitleItem) -> Unit = {}
    ) {
        if (!show) return
        val context = LocalContext.current
        val playerAccentColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.getPrimaryColor(context)

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { onClose() }
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(360.dp)
                    .background(Color(0xFF141416).copy(alpha = 0.98f), RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .align(Alignment.CenterEnd)
                    .clickable(enabled = false) {}
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                if (showSubtitleStyleSettings) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { onToggleStyleSettings(false) }
                                .padding(bottom = 18.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_arrow_back_ios_24),
                                contentDescription = "Back",
                                tint = playerAccentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Back to Audio & Subs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        // PRESET STYLES (Matching Quality section box styling)
                        Text("PRESET STYLES", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        val presets = listOf(
                            Triple("Default", "Standard subtitles with balanced outline & background", Triple(24f, android.graphics.Color.WHITE, Pair(128, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE))),
                            Triple("Hardsub", "Solid bold white with bold black anime outline", Triple(24f, android.graphics.Color.WHITE, Pair(0, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE))),
                            Triple("Yellow", "Classic anime yellow with black outline", Triple(24f, android.graphics.Color.YELLOW, Pair(0, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE))),
                            Triple("Cinema", "White text with drop shadow and tint", Triple(24f, android.graphics.Color.WHITE, Pair(76, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW))),
                            Triple("Box", "High contrast with solid background box", Triple(24f, android.graphics.Color.WHITE, Pair(178, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE))),
                            Triple("Lavender", "Lavender purple text with outline", Triple(24f, android.graphics.Color.rgb(208, 188, 255), Pair(0, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE)))
                        )
                        presets.forEach { (pName, pDesc, pConfig) ->
                            val isSel = subPreset == pName
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) Color(0xFF2C2C33) else Color.Transparent)
                                    .clickable {
                                        val fSize = pConfig.first
                                        val tColor = pConfig.second
                                        val bgOp = pConfig.third.first
                                        val edge = pConfig.third.second
                                        onPresetSelected(pName, fSize, tColor, bgOp, edge)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(modifier = Modifier.size(20.dp).padding(top = 2.dp), contentAlignment = Alignment.Center) {
                                        if (isSel) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = playerAccentColor, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pName, color = if (isSel) Color.White else Color(0xFFE5E5EA), fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(pDesc, color = if (isSel) Color(0xFFA5A5AD) else Color(0xFF8E8E9F), fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Text("OUTLINE THICKNESS", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        val edgeTypes = listOf(
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE to "None",
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW to "Drop Shadow (Black)",
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE to "Standard Outline (Solid Black)"
                        )
                        edgeTypes.forEach { (edgeVal, edgeLabel) ->
                            val isSel = subEdgeType == edgeVal
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) Color(0xFF2C2C33) else Color.Transparent)
                                    .clickable { onEdgeTypeChanged(edgeVal) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                        if (isSel) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = playerAccentColor, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(edgeLabel, color = if (isSel) Color.White else Color(0xFFE5E5EA), fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Text("FONT SIZE", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        val sizes = listOf(14f to "Small (14sp)", 18f to "Medium (18sp)", 20f to "Large (20sp)", 24f to "Extra Large (24sp)")
                        sizes.forEach { (size, label) ->
                            val isSel = subFontSize == size
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) Color(0xFF2C2C33) else Color.Transparent)
                                    .clickable { onFontSizeChanged(size) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                        if (isSel) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = playerAccentColor, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(label, color = if (isSel) Color.White else Color(0xFFE5E5EA), fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Text("TEXT COLOR", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        val colors = listOf(
                            android.graphics.Color.WHITE to "White",
                            android.graphics.Color.YELLOW to "Yellow",
                            android.graphics.Color.CYAN to "Cyan",
                            android.graphics.Color.rgb(208, 188, 255) to "Lavender Purple",
                            android.graphics.Color.rgb(167, 243, 208) to "Mint Green"
                        )
                        colors.forEach { (colorVal, colorLabel) ->
                            val isSel = subTextColor == colorVal
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) Color(0xFF2C2C33) else Color.Transparent)
                                    .clickable { onTextColorChanged(colorVal) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                        if (isSel) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = playerAccentColor, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(colorLabel, color = if (isSel) Color.White else Color(0xFFE5E5EA), fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Text("BACKGROUND OPACITY", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        val opacities = listOf(
                            0 to "Transparent (0%)",
                            76 to "Subtle (30%)",
                            128 to "Balanced (50%)",
                            200 to "High Contrast (80%)",
                            255 to "Solid Black (100%)"
                        )
                        opacities.forEach { (opacityVal, opacityLabel) ->
                            val isSel = subBgOpacity == opacityVal
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) Color(0xFF2C2C33) else Color.Transparent)
                                    .clickable { onBgOpacityChanged(opacityVal) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                        if (isSel) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = playerAccentColor, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(opacityLabel, color = if (isSel) Color.White else Color(0xFFE5E5EA), fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header + Tab switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isExternalVideo) {
                                Row(
                                    modifier = Modifier
                                        .background(Color(0xFF222228), RoundedCornerShape(10.dp))
                                        .padding(3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (audioSubTab == 0) Color(0xFF32323C) else Color.Transparent)
                                            .clickable { onTabChange(0) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Audio", color = if (audioSubTab == 0) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (audioSubTab == 1) Color(0xFF32323C) else Color.Transparent)
                                            .clickable { onTabChange(1) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Subtitles", color = if (audioSubTab == 1) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (audioSubTab == 2) Color(0xFF32323C) else Color.Transparent)
                                            .clickable { onTabChange(2) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Public Subs", color = if (audioSubTab == 2) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    }
                                }
                            } else {
                                Text(
                                    text = "Subtitles",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            IconButton(
                                onClick = { onClose() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (!isExternalVideo && audioSubTab == 0) {
                                Text(
                                    text = "Audio track for current episode",
                                    color = Color(0xFFE0E0E6),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier.padding(bottom = 16.dp, start = 2.dp)
                                )
                                val audioTracks = listOf(
                                    "Japanese (Original)" to "Original broadcast Japanese audio",
                                    "English" to "English dubbed audio track",
                                    "Hindi" to "Hindi dubbed audio track"
                                )
                                audioTracks.forEach { (track, subLabel) ->
                                    val isSel = track == selectedAudio
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSel) Color(0xFF2C2C33) else Color.Transparent)
                                            .clickable { onAudioSelected(track) }
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            Box(modifier = Modifier.size(20.dp).padding(top = 2.dp), contentAlignment = Alignment.Center) {
                                                if (isSel) {
                                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = playerAccentColor, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(track, color = if (isSel) Color.White else Color(0xFFE5E5EA), fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                                                Text(subLabel, color = if (isSel) Color(0xFFA5A5AD) else Color(0xFF8E8E9F), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            } else if (!isExternalVideo && audioSubTab == 2) {
                                Text(
                                    text = "Community uploaded subtitles",
                                    color = Color(0xFFE0E0E6),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier.padding(bottom = 16.dp, start = 2.dp)
                                )

                                if (publicSubtitlesList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp, horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_baseline_translate_24),
                                                contentDescription = null,
                                                tint = Color(0xFF6B6B7A),
                                                modifier = Modifier.size(38.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "No public subtitles yet",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Transcribe Japanese audio to contribute the first community subtitle for this episode!",
                                                color = Color(0xFF8E8E9F),
                                                fontSize = 11.5.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                } else {
                                    publicSubtitlesList.forEach { pubSub ->
                                        val subKey = "${pubSub.langName} (Public)"
                                        val isSel = selectedSub == subKey
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSel) Color(0xFF2C2C33) else Color.Transparent)
                                                .clickable { onPublicSubSelected(pubSub) }
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.Top) {
                                                Box(modifier = Modifier.size(20.dp).padding(top = 2.dp), contentAlignment = Alignment.Center) {
                                                    if (isSel) {
                                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = playerAccentColor, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${pubSub.langName} Subtitles",
                                                        color = if (isSel) Color.White else Color(0xFFE5E5EA),
                                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 15.sp,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "Uploaded by ${pubSub.uploaderName}",
                                                        color = if (isSel) Color(0xFFFFD54F) else Color(0xFF8E8E9F),
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = if (isExternalVideo) "Subtitles for external video" else "Subtitles for current video",
                                    color = Color(0xFFE0E0E6),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier.padding(bottom = 16.dp, start = 2.dp)
                                )

                                if (isExternalVideo) {
                                    // Custom subtitle upload / import button for external video
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(playerAccentColor.copy(alpha = 0.15f))
                                            .border(1.dp, playerAccentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                            .clickable { onPickCustomSubtitle() }
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Upload Subtitle",
                                                tint = playerAccentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "Select Subtitle File (.srt / .vtt)",
                                                    color = Color.White,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Import local subtitle from device storage",
                                                    color = Color(0xFFA0A0AB),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                val subOptions = mutableListOf<Pair<String, String>>()
                                if (isExternalVideo) {
                                    subOptions.add("Off" to "No subtitles displayed")
                                    if (selectedSub.isNotEmpty() && selectedSub != "Off") {
                                        subOptions.add(selectedSub to "Loaded local subtitle file")
                                    }
                                } else {
                                    subOptions.add("Off" to "No subtitles displayed")
                                    subOptions.add("English (VTT)" to "Standard English soft subtitles")
                                    if (selectedSub == "English (Soft Sub)" || subPreset.equals("Hardsub", ignoreCase = true)) {
                                        subOptions.add("English (Soft Sub)" to "AniBD soft subtitles (Hardsub styled)")
                                    }
                                    if (selectedAudio != "English" && selectedAudio != "Hindi") {
                                        subOptions.add("Hard Sub" to "Burned-in hardcoded subtitle stream")
                                    }
                                    translatedSubMap.keys.forEach { transKey ->
                                        subOptions.add(transKey to "Auto AI translated subtitle track")
                                    }
                                }

                                subOptions.forEach { (sub, subLabel) ->
                                    val isSel = sub == selectedSub
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSel) Color(0xFF2C2C33) else Color.Transparent)
                                            .clickable { onSubSelected(sub) }
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            Box(modifier = Modifier.size(20.dp).padding(top = 2.dp), contentAlignment = Alignment.Center) {
                                                if (isSel) {
                                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = playerAccentColor, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(sub, color = if (isSel) Color.White else Color(0xFFE5E5EA), fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                                                Text(subLabel, color = if (isSel) Color(0xFFA5A5AD) else Color(0xFF8E8E9F), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!isAudioMode || com.lagradost.cloudstream3.BuildConfig.ENABLE_TRANSCRIPTION) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF222228))
                                    .clickable { onRequestTranslate() }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AiSparklesIcon,
                                    contentDescription = "Translate Subtitles with AI",
                                    tint = playerAccentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isAudioMode) "Transcribe & Translate Audio with AI" else "Translate Subtitles with AI",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF222228))
                                .clickable { onToggleStyleSettings(true) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Customize Styling",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Subtitle Appearance",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SpeedQualityDrawerPanel(
        show: Boolean,
        speedQualityTab: Int,
        onTabChange: (Int) -> Unit,
        currentQuality: String,
        videoTrackHeights: List<Int>,
        isGraphicsUpscalerEnabled: Boolean,
        showShaderSettings: Boolean,
        enhancerPreset: com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset,
        enhancerSharpness: Float,
        enhancerContrast: Float,
        enhancerSaturation: Float,
        enhancerLineClarity: Float,
        deviceSpecs: Triple<Int, Int, String>,
        currentSpeed: Float,
        onClose: () -> Unit,
        onQualitySelected: (String) -> Unit,
        onSpeedSelected: (Float, String) -> Unit,
        onUpscalerToggled: (Boolean) -> Unit,
        onToggleShaderSettings: (Boolean) -> Unit,
        onPresetSelected: (com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset) -> Unit,
        onSharpnessChanged: (Float) -> Unit,
        onContrastChanged: (Float) -> Unit,
        onSaturationChanged: (Float) -> Unit,
        onLineClarityChanged: (Float) -> Unit
    ) {
        if (!show) return
        val context = LocalContext.current
        val playerAccentColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.getPrimaryColor(context)

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { onClose() }
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(360.dp)
                    .background(Color(0xFF141416).copy(alpha = 0.98f), RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .align(Alignment.CenterEnd)
                    .clickable(enabled = false) {}
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // ── Shader / Enhancer Settings Sub-Panel ──
                if (showShaderSettings) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { onToggleShaderSettings(false) }
                                .padding(bottom = 16.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_arrow_back_ios_24),
                                contentDescription = "Back",
                                tint = playerAccentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Back to Quality", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        // Master Toggle (Matching Quality box styling)
                        Text("AI GRAPHICS ENHANCER", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isGraphicsUpscalerEnabled) Color(0xFF2C2C33) else Color.Transparent)
                                .clickable { onUpscalerToggled(!isGraphicsUpscalerEnabled) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isGraphicsUpscalerEnabled) "Active · Real-time CAS Sharpening" else "Enhancer Disabled",
                                        color = if (isGraphicsUpscalerEnabled) Color.White else Color(0xFFE5E5EA),
                                        fontWeight = if (isGraphicsUpscalerEnabled) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Hardware GPU edge sharpening & HDR color engine",
                                        color = if (isGraphicsUpscalerEnabled) Color(0xFFA5A5AD) else Color(0xFF8E8E9F),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier
                                        .size(width = 44.dp, height = 24.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isGraphicsUpscalerEnabled) playerAccentColor else Color(0xFF3A3A44))
                                        .padding(2.dp),
                                    contentAlignment = if (isGraphicsUpscalerEnabled) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Text("ENHANCER PRESET MODES", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                        // Preset cards matching Quality section style
                        val allPresets = listOf(
                            com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.ULTRA_CLARITY,
                            com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.INK_MASTER_HDR,
                            com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.CAS_RAZOR_SHARP,
                            com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.OLED_VIVID
                        )

                        allPresets.forEach { preset ->
                            val isSelected = isGraphicsUpscalerEnabled && enhancerPreset == preset
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFF2C2C33) else Color.Transparent)
                                    .clickable(enabled = isGraphicsUpscalerEnabled) { onPresetSelected(preset) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier.size(20.dp).padding(top = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = playerAccentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = preset.title,
                                                color = if (!isGraphicsUpscalerEnabled) Color(0xFF6E6E7F) else if (isSelected) Color.White else Color(0xFFE5E5EA),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 15.sp
                                            )
                                            if (preset == com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.ULTRA_CLARITY) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(playerAccentColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text("BEST", color = playerAccentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = preset.subtitle,
                                            color = if (!isGraphicsUpscalerEnabled) Color(0xFF505060) else if (isSelected) Color(0xFFA5A5AD) else Color(0xFF8E8E9F),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Text("FINE-TUNING CONTROLS", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                        // Sliders for individual parameters
                        val controls = listOf(
                            Triple("Edge Sharpening (CAS)", enhancerSharpness to { v: Float -> onSharpnessChanged(v) }, "0.00" to "2.50"),
                            Triple("Inked Line Clarity", enhancerLineClarity to { v: Float -> onLineClarityChanged(v) }, "0.0x" to "2.0x"),
                            Triple("Color Vibrance Pop", enhancerSaturation to { v: Float -> onSaturationChanged(v) }, "1.0x" to "1.8x"),
                            Triple("Dynamic HDR Contrast", enhancerContrast to { v: Float -> onContrastChanged(v) }, "1.0x" to "1.6x")
                        )

                        controls.forEach { (name, stateAndSetter, rangeLabels) ->
                            val (currentVal, setter) = stateAndSetter
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.5.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF19191E))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = name,
                                            color = if (isGraphicsUpscalerEnabled) Color.White else Color(0xFF6E6E7F),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (name == "Edge Sharpening (CAS)") String.format("%.2f", currentVal) else String.format("%.2fx", currentVal),
                                            color = if (isGraphicsUpscalerEnabled) playerAccentColor else Color(0xFF6E6E7F),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    val (minV, maxV) = when (name) {
                                        "Edge Sharpening (CAS)" -> 0.0f to 2.5f
                                        "Inked Line Clarity" -> 0.0f to 2.0f
                                        "Color Vibrance Pop" -> 1.0f to 1.8f
                                        else -> 1.0f to 1.6f
                                    }
                                    Slider(
                                        value = currentVal.coerceIn(minV, maxV),
                                        onValueChange = { setter(it) },
                                        valueRange = minV..maxV,
                                        enabled = isGraphicsUpscalerEnabled,
                                        colors = SliderDefaults.colors(
                                            thumbColor = if (isGraphicsUpscalerEnabled) playerAccentColor else Color(0xFF505060),
                                            activeTrackColor = if (isGraphicsUpscalerEnabled) playerAccentColor else Color(0xFF404050),
                                            inactiveTrackColor = Color(0xFF282830)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(28.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "GPU Pipeline: AGSL / Vulkan Hardware Shaders · ${deviceSpecs.first} Cores · ${deviceSpecs.second}MB RAM · ${deviceSpecs.third}",
                            color = Color(0xFF5A5A6A),
                            fontSize = 9.5.sp,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                } else {
                    // ── Main Quality / Speed Panel ──
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header + Tab switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(Color(0xFF222228), RoundedCornerShape(10.dp))
                                    .padding(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (speedQualityTab == 0) Color(0xFF32323C) else Color.Transparent)
                                        .clickable { onTabChange(0) }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Quality", color = if (speedQualityTab == 0) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (speedQualityTab == 1) Color(0xFF32323C) else Color.Transparent)
                                        .clickable { onTabChange(1) }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Speed", color = if (speedQualityTab == 1) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                            IconButton(
                                onClick = { onClose() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (speedQualityTab == 0) {
                                Text(
                                    text = "Quality for current video · ${if (currentQuality == "Auto") "Auto (${if (videoTrackHeights.isNotEmpty()) "${videoTrackHeights.first()}p" else "1080p"})" else currentQuality}",
                                    color = Color(0xFFE0E0E6),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier.padding(bottom = 16.dp, start = 2.dp)
                                )

                                val qualityOptions = remember(videoTrackHeights) {
                                    val list = mutableListOf<Pair<String, Pair<String, String>>>()
                                    list.add("Auto" to ("Auto (recommended)" to "Adjusts to give you the best experience for your conditions"))
                                    if (videoTrackHeights.isNotEmpty()) {
                                        for (h in videoTrackHeights) {
                                            val label = "${h}p"
                                            list.add(label to (label to ""))
                                        }
                                    } else {
                                        listOf("1080p", "720p", "480p", "360p").forEach {
                                            list.add(it to (it to ""))
                                        }
                                    }
                                    list
                                }

                                qualityOptions.forEach { (qualKey, details) ->
                                    val (title, subtitle) = details
                                    val isSel = currentQuality == qualKey
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSel) Color(0xFF2C2C33) else Color.Transparent)
                                            .clickable { onQualitySelected(qualKey) }
                                            .padding(horizontal = 16.dp, vertical = if (subtitle.isNotEmpty()) 12.dp else 14.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = if (subtitle.isNotEmpty()) Alignment.Top else Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(20.dp).padding(top = if (subtitle.isNotEmpty()) 2.dp else 0.dp), contentAlignment = Alignment.Center) {
                                                if (isSel) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = playerAccentColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(title, color = if (isSel) Color.White else Color(0xFFE5E5EA), fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                                                if (subtitle.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(subtitle, color = if (isSel) Color(0xFFA5A5AD) else Color(0xFF8E8E9F), fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // ── Graphics Enhancer Button (same style as "Subtitle Appearance") ──
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF222228))
                                        .clickable { onToggleShaderSettings(true) }
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = AiSparklesIcon,
                                        contentDescription = "Graphics Enhancer",
                                        tint = if (isGraphicsUpscalerEnabled) playerAccentColor else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Graphics Enhancer",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    if (isGraphicsUpscalerEnabled) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(playerAccentColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("ON", color = playerAccentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Playback Speed",
                                    color = Color(0xFFE0E0E6),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier.padding(bottom = 16.dp, start = 2.dp)
                                )
                                val speedOptions = listOf(
                                    0.5f to "0.5x",
                                    0.75f to "0.75x",
                                    1.0f to "1.0x (Normal)",
                                    1.25f to "1.25x",
                                    1.5f to "1.5x",
                                    2.0f to "2.0x"
                                )
                                speedOptions.forEach { (spd, label) ->
                                    val isSel = currentSpeed == spd
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSel) Color(0xFF2C2C33) else Color.Transparent)
                                            .clickable { onSpeedSelected(spd, label) }
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                                if (isSel) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = playerAccentColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(label, color = if (isSel) Color.White else Color(0xFFE5E5EA), fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AnimatedPlayingWave(
        modifier: Modifier = Modifier,
        barColor: Color = Color.White
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "wave")
        val height1 by infiniteTransition.animateFloat(
            initialValue = 6f,
            targetValue = 22f,
            animationSpec = infiniteRepeatable(
                animation = tween(450, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar1"
        )
        val height2 by infiniteTransition.animateFloat(
            initialValue = 18f,
            targetValue = 7f,
            animationSpec = infiniteRepeatable(
                animation = tween(380, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar2"
        )
        val height3 by infiniteTransition.animateFloat(
            initialValue = 8f,
            targetValue = 24f,
            animationSpec = infiniteRepeatable(
                animation = tween(520, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar3"
        )
        val height4 by infiniteTransition.animateFloat(
            initialValue = 20f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(410, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar4"
        )

        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(height1.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(height2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(height3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(height4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }

    @Composable
    private fun EpisodesDrawerPanel(
        show: Boolean,
        currentEpisodeNum: Int,
        totalEpisodes: Int,
        animeTitle: String,
        coverUrl: String,
        showCoverUrl: String,
        animePosterUrl: String,
        anilistId: Int,
        futureEps: Set<Int>,
        episodeMetaMap: Map<Int, com.lagradost.cloudstream3.ui.animebox.api.EpisodeMeta>,
        episodeImages: Map<Int, String>,
        episodeTitles: Map<Int, String>,
        relatedAnimeList: List<PlayerRelatedAnime>,
        activeStreamType: String = "sub",
        onClose: () -> Unit,
        onEpisodeSelected: (Int) -> Unit,
        onSwitchToRelatedAnime: (Int, String, String, Int) -> Unit
    ) {
        if (!show) return
        val context = LocalContext.current
        val downloadCoroutineScope = rememberCoroutineScope()
        var episodeToDownloadInPlayer by remember { mutableStateOf<Triple<Int, String, String>?>(null) }
        var isStartingDownloadInPlayer by remember { mutableStateOf(false) }

        val activeEpisodes = remember(episodeImages, futureEps, totalEpisodes) {
            val mapped = episodeImages.keys.sorted().filter { it !in futureEps }
            if (mapped.isNotEmpty()) mapped else (1..(if (totalEpisodes > 0 && totalEpisodes < 9999) totalEpisodes else 1)).toList()
        }

        // Generate Season or Batch Filter Chips for current anime
        val seasons = remember(episodeMetaMap, activeEpisodes) {
            val sMap = mutableMapOf<Int, MutableList<Int>>()
            activeEpisodes.forEach { ep ->
                val sNum = episodeMetaMap[ep]?.seasonNumber ?: 1
                sMap.getOrPut(sNum) { mutableListOf() }.add(ep)
            }
            sMap
        }

        // Chips: "All", (Seasons if multi-season), and Related Anime titles
        val chips = remember(seasons, activeEpisodes, relatedAnimeList) {
            val list = mutableListOf<String>()
            list.add("All")
            if (seasons.size > 1) {
                seasons.keys.sorted().forEach { sNum ->
                    list.add("Season $sNum")
                }
            }
            // Only add specific related anime titles from Related section (no batch pagination chips)
            relatedAnimeList.forEach { rel ->
                if (rel.title !in list) {
                    list.add(rel.title)
                }
            }
            list
        }

        var selectedChip by remember { mutableStateOf("All") }

        // When a related anime chip is selected:
        val selectedRelatedAnime = remember(selectedChip, relatedAnimeList) {
            relatedAnimeList.find { it.title == selectedChip }
        }
        val isMovieRel = selectedRelatedAnime != null && selectedRelatedAnime.format.equals("MOVIE", ignoreCase = true)

        var relatedEpisodesMeta by remember { mutableStateOf<Map<Int, com.lagradost.cloudstream3.ui.animebox.api.EpisodeMeta>>(emptyMap()) }
        var relatedEpisodesImages by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
        var relatedEpisodesTitles by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
        var isLoadingRelatedEps by remember { mutableStateOf(false) }

        LaunchedEffect(selectedRelatedAnime) {
            val rel = selectedRelatedAnime
            if (rel != null && !isMovieRel) {
                isLoadingRelatedEps = true
                try {
                    val metaMap = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getEpisodeMetadata(rel.id)
                    val imgMap = mutableMapOf<Int, String>()
                    val titleMap = mutableMapOf<Int, String>()
                    metaMap.forEach { (epNum, meta) ->
                        imgMap[epNum] = meta.imageUrl
                        titleMap[epNum] = meta.title
                    }
                    relatedEpisodesMeta = metaMap
                    relatedEpisodesImages = imgMap
                    relatedEpisodesTitles = titleMap
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoadingRelatedEps = false
                }
            }
        }

        val displayedEpisodes = remember(activeEpisodes, selectedChip, episodeMetaMap, selectedRelatedAnime, relatedEpisodesImages, isMovieRel) {
            if (selectedRelatedAnime != null) {
                if (isMovieRel) {
                    listOf(1)
                } else {
                    val relKeys = relatedEpisodesImages.keys.sorted()
                    if (relKeys.isNotEmpty()) relKeys else listOf(1)
                }
            } else {
                when {
                    selectedChip == "All" -> activeEpisodes
                    selectedChip.startsWith("Season ") -> {
                        val sNum = selectedChip.removePrefix("Season ").toIntOrNull() ?: 1
                        val filtered = activeEpisodes.filter { (episodeMetaMap[it]?.seasonNumber ?: 1) == sNum }
                        if (filtered.isNotEmpty()) filtered else activeEpisodes
                    }
                    else -> activeEpisodes
                }
            }
        }

        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        var menuEpisode by remember { mutableStateOf<Int?>(null) }
        val playerAccentColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.getPrimaryColor(context)

        // Auto-scroll to currently playing episode if viewing current show
        LaunchedEffect(show, currentEpisodeNum, displayedEpisodes, selectedRelatedAnime) {
            if (show && selectedRelatedAnime == null) {
                val index = displayedEpisodes.indexOf(currentEpisodeNum)
                if (index >= 0) {
                    listState.scrollToItem(index.coerceAtLeast(0))
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Backdrop with blurred top portion and deep black bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onClose() }
            ) {
                // Top portion with frosted blur overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                        .align(Alignment.TopCenter)
                        .blur(16.dp)
                        .background(Color.Black.copy(alpha = 0.50f))
                )

                // Full deep black gradient (translucent at top fading to solid black at bottom)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.40f),
                                    Color.Black.copy(alpha = 0.82f),
                                    Color.Black.copy(alpha = 0.98f),
                                    Color(0xFF040406)
                                )
                            )
                        )
                )
            }

            // Prominently Lifted Carousel Container (comfortably raised above bottom)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 76.dp)
                    .clickable(enabled = false) {}
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "More videos",
                            color = Color.White,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = { onClose() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Filter Chips Row
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(chips) { chip ->
                            val isSelected = chip == selectedChip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color.White else Color(0xFF222228))
                                    .clickable {
                                        selectedChip = chip
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = chip,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Large Horizontal Episodes Carousel (width = 320.dp)
                    if (displayedEpisodes.isEmpty() || isLoadingRelatedEps) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingRelatedEps) {
                                CircularProgressIndicator(color = playerAccentColor, modifier = Modifier.size(32.dp))
                            } else {
                                Text("No episodes available", color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    } else {
                        LazyRow(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(displayedEpisodes) { ep ->
                                val isCurrent = (selectedRelatedAnime == null) && (ep == currentEpisodeNum)
                                val currentAnimeName = selectedRelatedAnime?.title ?: animeTitle
                                val defaultImgUrl = if (selectedRelatedAnime != null) {
                                    relatedEpisodesImages[ep] ?: selectedRelatedAnime.coverUrl
                                } else {
                                    episodeImages[ep] ?: ""
                                }
                                var imgUrl by remember(ep, defaultImgUrl, selectedRelatedAnime) { mutableStateOf(defaultImgUrl) }

                                if (selectedRelatedAnime == null) {
                                    val tmdbId = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getLongRunningTmdbId(anilistId)
                                    if (tmdbId != null) {
                                        LaunchedEffect(ep) {
                                            val meta = episodeMetaMap[ep]
                                            if (meta != null) {
                                                val tmdbImg = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getTmdbEpisodeImage(tmdbId, meta.seasonNumber, meta.episodeNumber)
                                                if (tmdbImg.isNotEmpty()) {
                                                    imgUrl = tmdbImg
                                                }
                                            }
                                        }
                                    }
                                }

                                val epTitle = if (selectedRelatedAnime != null) relatedEpisodesTitles[ep] else episodeTitles[ep]

                                Column(
                                    modifier = Modifier
                                        .width(320.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (selectedRelatedAnime != null) {
                                                onSwitchToRelatedAnime(selectedRelatedAnime.id, selectedRelatedAnime.title, selectedRelatedAnime.coverUrl, ep)
                                            } else {
                                                onEpisodeSelected(ep)
                                            }
                                        }
                                ) {
                                    // 16:9 Thumbnail
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1E1E26))
                                    ) {
                                        val fallbackPoster = selectedRelatedAnime?.coverUrl?.ifEmpty { animePosterUrl } ?: animePosterUrl
                                        val isUsingFallbackPoster = imgUrl.isEmpty() && fallbackPoster.isNotEmpty() && !isMovieRel
                                        val isSpoilerBlur = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isBlurEpisodeSpoilersEnabled(context) && !isMovieRel
                                        val shouldBlurThumb = isUsingFallbackPoster || isSpoilerBlur
                                        val finalImg = if (isMovieRel) {
                                            selectedRelatedAnime!!.coverUrl
                                        } else if (imgUrl.isNotEmpty()) {
                                            imgUrl
                                        } else {
                                            fallbackPoster
                                        }
                                        if (finalImg.isNotEmpty()) {
                                            val painter = rememberAsyncImagePainter(model = finalImg)
                                            androidx.compose.foundation.Image(
                                                painter = painter,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .then(if (shouldBlurThumb) Modifier.blur(22.dp) else Modifier),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        }

                                        if (isSpoilerBlur) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.38f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                androidx.compose.foundation.Image(
                                                    painter = painterResource(id = R.drawable.ic_eye_spoiler),
                                                    contentDescription = "Spoiler Hidden",
                                                    modifier = Modifier.size(28.dp),
                                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(alpha = 0.9f))
                                                )
                                            }
                                        }

                                        // Dark gradient overlay on thumbnail bottom
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                                                    )
                                                )
                                        )

                                        // Top Right: Bold "EP.XX" or "MOVIE" Badge
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = if (isMovieRel) "MOVIE" else "EP.$ep",
                                                color = Color(0xFF1E1B4B),
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1
                                            )
                                        }

                                        // Bottom Left: "Watch FULL episode/movie"
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(start = 8.dp, bottom = 8.dp)
                                        ) {
                                            Text(
                                                text = if (isMovieRel) "Watch FULL\nmovie" else "Watch FULL\nepisode",
                                                color = Color.White,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 13.5.sp,
                                                style = androidx.compose.ui.text.TextStyle(
                                                    shadow = androidx.compose.ui.graphics.Shadow(
                                                        color = Color.Black,
                                                        offset = Offset(1f, 1f),
                                                        blurRadius = 3f
                                                    )
                                                )
                                            )
                                        }

                                        // Bottom Right: Duration Badge
                                        val durationText = if (isMovieRel) {
                                            "Movie"
                                        } else if (selectedRelatedAnime != null) {
                                            relatedEpisodesMeta[ep]?.runtime?.let { if (it > 0) "${it}:00" else "24:00" } ?: "24:00"
                                        } else {
                                            episodeMetaMap[ep]?.runtime?.let { if (it > 0) "${it}:00" else "24:00" } ?: "24:00"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(end = 8.dp, bottom = 8.dp)
                                                .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = durationText,
                                                color = Color.White,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        // Active playing wave overlay
                                        if (isCurrent) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.45f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AnimatedPlayingWave(barColor = Color.White)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Metadata below thumbnail (Poster Avatar + Title + Subtitle + 3-dots icon)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Proper Anime Poster Avatar
                                        val currentPoster = selectedRelatedAnime?.coverUrl ?: animePosterUrl
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF282832)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (currentPoster.isNotEmpty()) {
                                                val avatarPainter = rememberAsyncImagePainter(model = currentPoster)
                                                androidx.compose.foundation.Image(
                                                    painter = avatarPainter,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                            } else {
                                                Text(
                                                    text = "$ep",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            val fullTitle = if (isMovieRel) {
                                                selectedRelatedAnime!!.title
                                            } else if (!epTitle.isNullOrEmpty() && epTitle != "Episode $ep") {
                                                "Ep $ep — $epTitle"
                                            } else {
                                                "Episode $ep"
                                            }
                                            Text(
                                                text = fullTitle,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val subLine = if (isMovieRel) {
                                                "${selectedRelatedAnime!!.title} · Movie · Sub/Dub"
                                            } else {
                                                "$currentAnimeName · Ep $ep · Sub/Dub"
                                            }
                                            Text(
                                                text = subLine,
                                                color = Color(0xFFA5A5B0),
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        Box {
                                            IconButton(
                                                onClick = { menuEpisode = ep },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "More",
                                                    tint = Color.LightGray,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            androidx.compose.material3.DropdownMenu(
                                                expanded = (menuEpisode == ep),
                                                onDismissRequest = { menuEpisode = null },
                                                shape = RoundedCornerShape(12.dp),
                                                containerColor = Color(0xFF282828),
                                                tonalElevation = 0.dp,
                                                shadowElevation = 8.dp,
                                                border = null,
                                                modifier = Modifier.widthIn(min = 210.dp, max = 250.dp)
                                            ) {
                                                val epTarget = ep
                                                val currentAnimeId = selectedRelatedAnime?.id ?: anilistId
                                                val currentAnimeName = selectedRelatedAnime?.title ?: animeTitle
                                                val currentCover = selectedRelatedAnime?.coverUrl ?: animePosterUrl

                                                // Option 1: Download video (downloads directly with the user's active/preference stream type)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            menuEpisode = null
                                                            val epTarget = ep
                                                            val currentAnimeId = selectedRelatedAnime?.id ?: anilistId
                                                            val currentAnimeName = selectedRelatedAnime?.title ?: animeTitle
                                                            val currentCover = selectedRelatedAnime?.coverUrl ?: animePosterUrl
                                                            val currentEpMeta = if (selectedRelatedAnime != null) relatedEpisodesMeta[epTarget] else episodeMetaMap[epTarget]
                                                            val currentEpTitle = if (selectedRelatedAnime != null) relatedEpisodesTitles[epTarget] else episodeTitles[epTarget]
                                                            val chosenType = if (activeStreamType.isEmpty()) "sub" else activeStreamType
                                                            val langTag = when (chosenType) {
                                                                "hardsub" -> "HSub"
                                                                "hindi" -> "Hindi Dub"
                                                                "dub" -> "English Dub"
                                                                else -> "Sub"
                                                            }
                                                            Toast.makeText(context, "Downloading Episode $epTarget ($langTag)...", Toast.LENGTH_SHORT).show()

                                                            downloadCoroutineScope.launch {
                                                                var streamInfo = withContext(Dispatchers.IO) {
                                                                    com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(context, currentAnimeId, epTarget, chosenType, currentAnimeName)
                                                                }
                                                                var directHls = (streamInfo?.get("hls") as? String) ?: ""
                                                                var actualStreamType = chosenType

                                                                if (directHls.isEmpty() && chosenType == "hardsub") {
                                                                    streamInfo = withContext(Dispatchers.IO) {
                                                                        com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(context, currentAnimeId, epTarget, "sub", currentAnimeName)
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
                                                                    val specificCover = currentEpMeta?.imageUrl?.ifEmpty { currentCover } ?: currentCover
                                                                    val qualityLabel = when (actualStreamType) {
                                                                        "hardsub" -> "1080p HSub"
                                                                        "hindi" -> "1080p Hindi"
                                                                        "dub" -> "1080p Dub"
                                                                        else -> "1080p Sub"
                                                                    }

                                                                    com.lagradost.cloudstream3.ui.animebox.download.AnimeDownloadManager.getInstance(context).enqueueDownload(
                                                                        anilistId = currentAnimeId,
                                                                        animeTitle = currentAnimeName,
                                                                        episodeNum = epTarget,
                                                                        episodeTitle = currentEpTitle ?: "Episode $epTarget",
                                                                        coverUrl = specificCover,
                                                                        showCoverUrl = currentCover,
                                                                        quality = qualityLabel,
                                                                        resolvedUrl = directHls,
                                                                        referer = directRef,
                                                                        subtitleUrl = directSub,
                                                                        subtitlesJson = subsJson,
                                                                        introStart = iStart,
                                                                        introEnd = iEnd,
                                                                        outroStart = oStart,
                                                                        outroEnd = oEnd,
                                                                        streamType = actualStreamType,
                                                                        providedBackdrop = animePosterUrl,
                                                                        providedLogo = ""
                                                                    )
                                                                    Toast.makeText(context, "Episode $epTarget queued for download ($qualityLabel)", Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    Toast.makeText(context, "Stream unavailable for Episode $epTarget ($langTag)", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = YtPopupDownloadIcon,
                                                        contentDescription = "Download video",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Text(
                                                        text = "Download video",
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Normal
                                                    )
                                                }

                                                // Option 2: Open anime detail page (opens in app + enters PiP + closes series episode sheet)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            menuEpisode = null
                                                            onClose() // Close series/episodes drawer
                                                            val act = context.findActivity()
                                                            if (act != null) {
                                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                                    try {
                                                                        val params = android.app.PictureInPictureParams.Builder()
                                                                            .setActions(emptyList())
                                                                            .build()
                                                                        act.enterPictureInPictureMode(params)
                                                                    } catch (e: Exception) {
                                                                        act.enterPictureInPictureMode()
                                                                    }
                                                                } else {
                                                                    try { act.enterPictureInPictureMode() } catch (_: Exception) {}
                                                                }
                                                            }
                                                            val intent = Intent(context, AnimeBoxDetailActivity::class.java).apply {
                                                                putExtra("anilistId", currentAnimeId)
                                                                putExtra("initialTitle", currentAnimeName)
                                                                putExtra("initialCoverUrl", currentCover)
                                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                            }
                                                            context.startActivity(intent)
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = YtPopupShareIcon,
                                                        contentDescription = "Open anime detail page",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Text(
                                                        text = "Open anime detail page",
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Normal
                                                    )
                                                }

                                                // Option 3: Report
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            menuEpisode = null
                                                            val act = context.findActivity()
                                                            val repIntent = Intent(context, AnimeBoxDetailActivity::class.java).apply {
                                                                putExtra("anilistId", currentAnimeId)
                                                                putExtra("initialTitle", currentAnimeName)
                                                                putExtra("initialCoverUrl", currentCover)
                                                                putExtra("openReportDialog", true)
                                                                putExtra("reportEpisode", ep)
                                                                putExtra("reportAnimeTitle", currentAnimeName)
                                                            }
                                                            context.startActivity(repIntent)
                                                            act?.finish()
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = YtPopupFlagIcon,
                                                        contentDescription = "Report",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Text(
                                                        text = "Report",
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Normal
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
            }
        }
    }

    @Composable
    private fun ResumeDialogContent(
        savedProgress: Long,
        player: ExoPlayer,
        onDismiss: () -> Unit
    ) {
        val context = LocalContext.current
        val playerAccentColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.getPrimaryColor(context)

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
                        onDismiss()
                        player.seekTo(0L)
                        player.play()
                    }) {
                        Text("Start Over", color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            onDismiss()
                            player.seekTo(savedProgress)
                            player.play()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = playerAccentColor)
                    ) {
                        Text("Resume", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
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
        onDismiss: (dontShowAgain: Boolean) -> Unit
    ) {
        val context = LocalContext.current
        val playerAccentColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.getPrimaryColor(context)
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
                            val act = context.findActivity()
                            val actIntent = (context as? android.app.Activity)?.intent
                            val targetAnilistId = actIntent?.getIntExtra("anilistId", 0) ?: 0
                            val targetAnimeTitle = actIntent?.getStringExtra("animeTitle") ?: ""
                            val targetCoverUrl = actIntent?.getStringExtra("coverUrl") ?: ""
                            val targetEpisode = actIntent?.getIntExtra("episode", 1) ?: 1
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
    private fun LocalModelWarningDialog(
        modelInfo: com.lagradost.cloudstream3.ui.animebox.api.AiModelInfo,
        onContinue: () -> Unit,
        onDismiss: () -> Unit
    ) {
        val context = LocalContext.current
        val playerAccentColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.getPrimaryColor(context)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                verticalAlignment = Alignment.Bottom
            ) {
                // 1. Chibi Character with glowing spot shadow
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .offset(y = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_thank_chibi),
                        contentDescription = "Chibi",
                        modifier = Modifier
                            .size(76.dp)
                            .shadow(12.dp, shape = CircleShape, spotColor = Color(0xFFFFB300)),
                        contentScale = ContentScale.Fit
                    )
                }

                // 2. Speech Bubble Tail
                Canvas(
                    modifier = Modifier
                        .size(14.dp, 20.dp)
                        .offset(x = 1.dp, y = (-18).dp)
                ) {
                    val path = Path().apply {
                        moveTo(size.width, 0f)
                        lineTo(0f, size.height * 0.5f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(path, color = Color(0xF5141522))
                    drawPath(
                        path,
                        color = Color(0xFFFFB300),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 3. Persona Dialogue Bubble Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xF8151725),
                                    Color(0xF20F101A)
                                )
                            )
                        )
                        .border(
                            width = 1.8.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFFB300), // Amber
                                    Color(0xFFFF5722), // Deep Orange
                                    Color(0xFFFF4081)  // Neon Pink
                                )
                            ),
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    // Top Bar with Angled Badge & Model Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFFF9800), Color(0xFFFF5722))
                                        )
                                    )
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "AI DEVICE REQUIREMENT ⚡",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = modelInfo.langName,
                                color = Color(0xFFFFD54F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "✕",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onDismiss() }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "On-device AI speech models (${modelInfo.langName}) run 100% locally on your phone's processor.\n\n⚠️ Requires a capable processor (Snapdragon 8 / Dimensity 8000+) & 6GB+ RAM. On lower-end hardware, real-time generation may cause slight device warmth or processing delays.",
                        color = Color(0xFFF1F3FF),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x2AFFFFFF)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = onContinue,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = playerAccentColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("I Understand", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ModelDownloadPromptDialog(
        modelInfo: com.lagradost.cloudstream3.ui.animebox.api.AiModelInfo,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        val context = LocalContext.current
        val playerAccentColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.getPrimaryColor(context)
        val sizeMb = modelInfo.sizeBytes / (1024 * 1024)
        val isSpeechModel = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.JAPANESE_SPEECH_MODELS.any { it.langCode.equals(modelInfo.langCode, true) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                verticalAlignment = Alignment.Bottom
            ) {
                // 1. Chibi Character with glowing spot shadow
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .offset(y = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_thank_chibi),
                        contentDescription = "Chibi",
                        modifier = Modifier
                            .size(76.dp)
                            .shadow(14.dp, shape = CircleShape, spotColor = Color(0xFFFF4081)),
                        contentScale = ContentScale.Fit
                    )
                }

                // 2. Speech Bubble Tail
                Canvas(
                    modifier = Modifier
                        .size(14.dp, 20.dp)
                        .offset(x = 1.dp, y = (-18).dp)
                ) {
                    val path = Path().apply {
                        moveTo(size.width, 0f)
                        lineTo(0f, size.height * 0.5f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(path, color = Color(0xF5141522))
                    drawPath(
                        path,
                        color = Color(0xFFFF4081),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 3. Persona Dialogue Bubble Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xF8151725),
                                    Color(0xF20F101A)
                                )
                            )
                        )
                        .border(
                            width = 1.8.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFF4081), // Neon Pink
                                    Color(0xFF9C27B0), // Persona Purple
                                    Color(0xFF00E5FF)  // Electric Cyan
                                )
                            ),
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    // Top Bar with Angled Badge & Model Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFFFF4081),
                                                Color(0xFF9C27B0)
                                            )
                                        )
                                    )
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isSpeechModel) "DOWNLOAD SPEECH AI" else "DOWNLOAD SUBTITLE AI",
                                    color = Color(0xFFE2E4EE),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = modelInfo.langName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "✕",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onDismiss() }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isSpeechModel) {
                            "Download on-device ${modelInfo.langName} AI model (~$sizeMb MB)?\n\nRuns 100% locally on your device for high-precision Japanese speech recognition with frame-perfect timestamps."
                        } else {
                            "Download on-device AI translation model for ${modelInfo.langName} (~$sizeMb MB)?\n\nOnce downloaded, subtitle translation works at high speed and 100% offline without internet."
                        },
                        color = Color(0xFFF1F3FF),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x2AFFFFFF)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = playerAccentColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Download (~$sizeMb MB)", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ModelDownloadProgressOverlay(
        progress: com.lagradost.cloudstream3.ui.animebox.api.ModelDownloadProgress?,
        onCancel: () -> Unit
    ) {
        if (progress == null) return
        val context = LocalContext.current
        val playerAccentColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.getPrimaryColor(context)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E22))
                    .padding(24.dp)
            ) {
                Text(
                    text = "Downloading Subtitle Model",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Downloading Subtitle model (${progress.langName})...",
                    color = Color.White,
                    fontSize = 14.5.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color(0xFF33333E))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((progress.percent / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(playerAccentColor)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                val dlMb = String.format(java.util.Locale.US, "%.1f", progress.downloadedBytes / (1024f * 1024f))
                val totMb = String.format(java.util.Locale.US, "%.1f MB", progress.totalBytes / (1024f * 1024f))
                val speedMb = String.format(java.util.Locale.US, "%.1f MB/s", progress.speedBps / (1024f * 1024f))
                Text(
                    text = "${progress.percent}% (${dlMb} MB / ${totMb}) • ${speedMb}",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text("CANCEL", color = playerAccentColor, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }
            }
        }
    }

    @Composable
    private fun TranslationDialogContent(
        currentSubtitleUrl: String,
        subtitleUrl: String,
        originalSubtitleUrl: String = "",
        selectedSub: String,
        currentHlsUrl: String,
        currentReferer: String = "",
        anilistId: Int = 0,
        currentEpisodeNum: Int = 0,
        animeTitle: String = "",
        coverUrl: String = "",
        player: ExoPlayer,
        prefs: SharedPreferences,
        coroutineScope: kotlinx.coroutines.CoroutineScope,
        onDismiss: () -> Unit,
        onTranslatingStateChanged: (Boolean) -> Unit,
        onTranslatedSubMapChanged: (Map<String, String>) -> Unit,
        translatedSubMap: Map<String, String>,
        onSelectedSubChanged: (String) -> Unit,
        onLiveStatusChanged: (String?) -> Unit = {},
        onLiveCuesChanged: (List<com.lagradost.cloudstream3.ui.animebox.api.LiveSubtitleCue>) -> Unit = {},
        onRequestModelDownload: (com.lagradost.cloudstream3.ui.animebox.api.AiModelInfo) -> Unit,
        onRequestLocalModelWarning: (com.lagradost.cloudstream3.ui.animebox.api.AiModelInfo) -> Unit = {},
        isAudioMode: Boolean = false,
        publicSubtitlesList: List<com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitleItem> = emptyList(),
        onTriggerSaveToPublic: (PendingPublicSubData) -> Unit = {}
    ) {
        val context = LocalContext.current
        val playerAccentColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.getPrimaryColor(context)
        var selectedTier by remember {
            mutableStateOf(if (com.lagradost.cloudstream3.BuildConfig.SHOW_PRO_MODELS) com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.getSelectedTranslationTier(context) else "normal")
        }
        val speechModels = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.JAPANESE_SPEECH_MODELS
        var activeSpeechModel by remember {
            mutableStateOf(com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.getSelectedSpeechModel(context))
        }

        val targetLanguagesList = if (selectedTier == "pro") {
            if (isAudioMode) {
                listOf(com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.PRO_ENGLISH_MODEL) + com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.PRO_TRANSLATION_LANGUAGES
            } else {
                com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.PRO_TRANSLATION_LANGUAGES
            }
        } else {
            if (isAudioMode) {
                listOf(com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.ENGLISH_MODEL) + com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.NORMAL_TRANSLATION_LANGUAGES
            } else {
                com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.NORMAL_TRANSLATION_LANGUAGES
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(310.dp)
                    .heightIn(max = 470.dp)
                    .shadow(10.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF222226))
                    .clickable(enabled = false) {}
                    .padding(vertical = 14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = AiSparklesIcon,
                                contentDescription = null,
                                tint = playerAccentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAudioMode) "AI Audio Subtitles" else "AI Subtitle Translation",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp
                            )
                        }
                        IconButton(
                            onClick = { onDismiss() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isAudioMode) "Transcribe Japanese audio directly with AI Speech recognition" else "Select language to translate active subtitle track with AI",
                        color = Color(0xFFA5A5AD),
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    if (com.lagradost.cloudstream3.BuildConfig.SHOW_PRO_MODELS) {
                        // Tier Selector: Normal (Google ML Kit ~30MB) vs Pro (Deep Neural ~190MB)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF18181C))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selectedTier == "normal") playerAccentColor else Color.Transparent)
                                    .clickable {
                                        selectedTier = "normal"
                                        com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.setSelectedTranslationTier(context, "normal")
                                    }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Normal (Fast)",
                                    color = if (selectedTier == "normal") Color.White else Color(0xFF9E9EA4),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selectedTier == "normal") FontWeight.Bold else FontWeight.Medium
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selectedTier == "pro") playerAccentColor else Color.Transparent)
                                    .clickable {
                                        selectedTier = "pro"
                                        com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.setSelectedTranslationTier(context, "pro")
                                    }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Pro (High Accuracy)",
                                    color = if (selectedTier == "pro") Color.White else Color(0xFF9E9EA4),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selectedTier == "pro") FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (isAudioMode) {
                        // Speech Model Quality / Engine Selector (Cloud AI, SenseVoice, Whisper)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Speech Recognition Engine:",
                            color = Color(0xFFA5A5AD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            speechModels.forEach { sModel ->
                                val isSelected = activeSpeechModel.langCode.equals(sModel.langCode, true)
                                val isDl = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.isModelDownloaded(context, sModel.langCode)
                                val isSense = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.SENSEVOICE_MODELS.any { it.langCode.equals(sModel.langCode, true) }
                                val shortLabel = when (sModel.langCode) {
                                    "sensevoice_pro_anime" -> "SenseVoice · CJK"
                                    "whisper_base" -> "Whisper Base"
                                    "whisper_small" -> "Whisper Small"
                                    else -> sModel.langName.take(18)
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) playerAccentColor else Color(0xFF1E1E22))
                                        .clickable {
                                            onDismiss()
                                            onRequestLocalModelWarning(sModel)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isDl) "✓ $shortLabel" else shortLabel,
                                            color = if (isSelected) Color.White else Color(0xFFB0B0B8),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        if (isSense) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(if (isSelected) Color.White.copy(alpha = 0.25f) else Color(0xFF2E7D32).copy(alpha = 0.35f))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "REC",
                                                    color = if (isSelected) Color.White else Color(0xFFA5D6A7),
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(targetLanguagesList) { modelInfo ->
                            val langName = modelInfo.langName
                            val langCode = modelInfo.langCode
                            val isEnglishTarget = langCode.startsWith("en", ignoreCase = true)
                            val isModelDownloaded = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.isModelDownloaded(context, langCode)
                            val isCurrentTrans = selectedSub.contains(langName)
                            val sizeMb = modelInfo.sizeBytes / (1024 * 1024)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            if (isAudioMode) {
                                                // Check selected Japanese Speech Model first
                                                val isSpeechDl = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.isSpeechModelDownloaded(context, activeSpeechModel.langCode)
                                                if (!isSpeechDl) {
                                                    onDismiss()
                                                    onRequestLocalModelWarning(activeSpeechModel)
                                                    return@clickable
                                                }
                                                // If language model not downloaded, request download
                                                if (!isModelDownloaded) {
                                                    onDismiss()
                                                    onRequestModelDownload(modelInfo)
                                                    return@clickable
                                                }

                                                onDismiss()
                                                val transLabel = if (isEnglishTarget) "English (AI Translated)" else "$langName (Translated)"
                                                onSelectedSubChanged(transLabel)
                                                prefs.edit().putString("selectedSub", transLabel).apply()

                                                val savedFile = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.isCompleteSavedSubtitle(context, anilistId, currentEpisodeNum, langCode)
                                                if (savedFile != null) {
                                                    val existingCues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(savedFile.readText())
                                                    if (existingCues.size >= 5) {
                                                        onLiveCuesChanged(existingCues)
                                                        if (!translatedSubMap.containsKey(transLabel)) {
                                                            onTranslatedSubMapChanged(translatedSubMap + (transLabel to savedFile.absolutePath))
                                                        }
                                                        if (anilistId > 0 && currentEpisodeNum > 0 && !com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitlesManager.hasSavedToPublic(context, anilistId, currentEpisodeNum, langCode)) {
                                                            onTriggerSaveToPublic(
                                                                PendingPublicSubData(
                                                                    anilistId = anilistId,
                                                                    episodeNum = currentEpisodeNum,
                                                                    animeTitle = animeTitle,
                                                                    langCode = langCode,
                                                                    langName = modelInfo.langName,
                                                                    vttContent = savedFile.readText(),
                                                                    uploaderName = com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitlesManager.getEffectiveUploaderName(context)
                                                                )
                                                            )
                                                        }
                                                        android.widget.Toast.makeText(context, "Loaded saved $langName subtitles", android.widget.Toast.LENGTH_SHORT).show()
                                                        return@clickable
                                                    }
                                                }

                                                onTranslatingStateChanged(true)
                                                onLiveStatusChanged("Transcribing Japanese audio with ${activeSpeechModel.langName}...")
                                                coroutineScope.launch {
                                                    try {
                                                        onLiveCuesChanged(emptyList())
                                                        val localFilePath = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.transcribeAndTranslateEpisodeAudio(
                                                            context = context,
                                                            hlsOrVideoUrl = currentHlsUrl,
                                                            referer = currentReferer,
                                                            targetLangCode = langCode,
                                                            speechModelCode = activeSpeechModel.langCode,
                                                            anilistId = anilistId,
                                                            episodeNum = currentEpisodeNum,
                                                            animeTitle = animeTitle,
                                                            coverUrl = coverUrl,
                                                            knownSubtitleUrl = if (originalSubtitleUrl.isNotEmpty()) originalSubtitleUrl else subtitleUrl.ifEmpty { currentSubtitleUrl },
                                                            onProgress = { cur, total, tempPath, cues ->
                                                                onLiveStatusChanged("Transcribing Japanese audio ($cur/$total)")
                                                                onLiveCuesChanged(cues)
                                                                if (!translatedSubMap.containsKey(transLabel)) {
                                                                    onTranslatedSubMapChanged(translatedSubMap + (transLabel to tempPath))
                                                                }
                                                            }
                                                        )
                                                        onTranslatingStateChanged(false)
                                                        onLiveStatusChanged(null)
                                                        if (localFilePath != null) {
                                                            val updatedMap = translatedSubMap + (transLabel to localFilePath)
                                                            onTranslatedSubMapChanged(updatedMap)
                                                            val f = java.io.File(localFilePath)
                                                            if (f.exists() && f.length() > 150) {
                                                                val vttTxt = f.readText()
                                                                onLiveCuesChanged(com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(vttTxt))
                                                                onTriggerSaveToPublic(
                                                                    PendingPublicSubData(
                                                                        anilistId = anilistId,
                                                                        episodeNum = currentEpisodeNum,
                                                                        animeTitle = animeTitle,
                                                                        langCode = langCode,
                                                                        langName = modelInfo.langName,
                                                                        vttContent = vttTxt,
                                                                        uploaderName = com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitlesManager.getEffectiveUploaderName(context)
                                                                    )
                                                                )
                                                            }
                                                            android.widget.Toast.makeText(context, "Subtitles generated from Japanese audio with ${activeSpeechModel.langName}!", android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            android.widget.Toast.makeText(context, "Failed to transcribe audio. Please try again.", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (t: Throwable) {
                                                        onTranslatingStateChanged(false)
                                                        onLiveStatusChanged(null)
                                                        android.widget.Toast.makeText(context, "Transcription error: ${t.localizedMessage ?: "Unknown error"}", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else {
                                                // Subtitle file translation mode
                                                if (!isModelDownloaded) {
                                                    onDismiss()
                                                    onRequestModelDownload(modelInfo)
                                                } else {
                                                    onDismiss()
                                                    val transLabel = if (isEnglishTarget) "English (AI Translated)" else "$langName (AI Translated)"
                                                    onSelectedSubChanged(transLabel)
                                                    val savedFile = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.isCompleteSavedSubtitle(context, anilistId, currentEpisodeNum, langCode)
                                                    if (savedFile != null) {
                                                        val existingCues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(savedFile.readText())
                                                        if (existingCues.size >= 5) {
                                                            onLiveCuesChanged(existingCues)
                                                            val updatedMap = translatedSubMap + (transLabel to savedFile.absolutePath)
                                                            onTranslatedSubMapChanged(updatedMap)
                                                            android.widget.Toast.makeText(context, "Loaded saved $langName subtitles", android.widget.Toast.LENGTH_SHORT).show()
                                                            return@clickable
                                                        }
                                                    }

                                                    onTranslatingStateChanged(true)
                                                    onLiveStatusChanged("Translating to $langName...")
                                                    coroutineScope.launch {
                                                        try {
                                                            onLiveCuesChanged(emptyList())
                                                            val subToTranslate = when {
                                                                originalSubtitleUrl.isNotEmpty() -> originalSubtitleUrl
                                                                currentSubtitleUrl.isNotEmpty() -> currentSubtitleUrl
                                                                subtitleUrl.isNotEmpty() -> subtitleUrl
                                                                else -> ""
                                                            }
                                                            val localFilePath = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.translateSubtitleVtt(
                                                                context = context,
                                                                vttUrlOrPath = subToTranslate,
                                                                targetLangCode = langCode,
                                                                hlsUrl = currentHlsUrl,
                                                                referer = currentReferer,
                                                                anilistId = anilistId,
                                                                episodeNum = currentEpisodeNum,
                                                                animeTitle = animeTitle,
                                                                coverUrl = coverUrl,
                                                                onProgress = { cur, total, tempPath, cues ->
                                                                    onLiveStatusChanged("Translating to $langName ($cur/$total)")
                                                                    onLiveCuesChanged(cues)
                                                                    if (!translatedSubMap.containsKey(transLabel)) {
                                                                        onTranslatedSubMapChanged(translatedSubMap + (transLabel to tempPath))
                                                                    }
                                                                }
                                                            )
                                                            onTranslatingStateChanged(false)
                                                            onLiveStatusChanged(null)
                                                            if (localFilePath != null) {
                                                                val updatedMap = translatedSubMap + (transLabel to localFilePath)
                                                                onTranslatedSubMapChanged(updatedMap)
                                                                val f = java.io.File(localFilePath)
                                                                if (f.exists() && f.length() > 150) {
                                                                    onLiveCuesChanged(com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(f.readText()))
                                                                }
                                                                android.widget.Toast.makeText(context, "Subtitles translated to $langName!", android.widget.Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                android.widget.Toast.makeText(context, "Failed to translate subtitles. Please try again.", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        } catch (t: Throwable) {
                                                            onTranslatingStateChanged(false)
                                                            onLiveStatusChanged(null)
                                                            android.widget.Toast.makeText(context, "Translation error: ${t.localizedMessage ?: "Unknown error"}", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (t: Throwable) {
                                            t.printStackTrace()
                                        }
                                    }
                                    .background(if (isCurrentTrans) Color(0xFF33333A) else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isEnglishTarget && isAudioMode) "English (AI Audio Sub)" else langName,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isCurrentTrans) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isEnglishTarget && isAudioMode) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(playerAccentColor.copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "PRIMARY",
                                                color = playerAccentColor,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (isModelDownloaded) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF2E7D32).copy(alpha = 0.25f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (selectedTier == "pro") "PRO READY" else "READY",
                                            color = Color(0xFF81C784),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF3A3A44))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = YtPopupDownloadIcon,
                                            contentDescription = "Download Model",
                                            tint = Color(0xFFE2E8F0),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "${sizeMb}MB",
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        if (publicSubtitlesList.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "PUBLIC SUBTITLES (COMMUNITY)",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }

                            items(publicSubtitlesList) { pubItem ->
                                val pubLabel = "${pubItem.langName} (Public)"
                                val isSelected = selectedSub == pubLabel
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            try {
                                                onDismiss()
                                                onSelectedSubChanged(pubLabel)
                                                prefs.edit().putString("selectedSub", pubLabel).apply()
                                                val cues = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.parseVttToCues(pubItem.vttContent)
                                                if (cues.isNotEmpty()) {
                                                    onLiveCuesChanged(cues)
                                                    val safeLangCode = pubItem.langCode.replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")
                                                    val savedFile = com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.getSavedSubtitleFile(context, anilistId, currentEpisodeNum, safeLangCode)
                                                    try {
                                                        savedFile.parentFile?.mkdirs()
                                                        savedFile.writeText(pubItem.vttContent)
                                                        onTranslatedSubMapChanged(translatedSubMap + (pubLabel to savedFile.absolutePath))
                                                    } catch (_: Exception) {}
                                                    android.widget.Toast.makeText(context, "Loaded ${pubItem.langName} public subtitles by ${pubItem.uploaderName}!", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (t: Throwable) {
                                                t.printStackTrace()
                                            }
                                        }
                                        .background(if (isSelected) Color(0xFF33333A) else Color.Transparent)
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_baseline_translate_24),
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "${pubItem.langName} Subtitles",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = "Uploaded by ${pubItem.uploaderName}",
                                                color = Color(0xFFFFD54F),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "PUBLIC",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
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

    private fun normalizePunctuationSpacing(text: String): String {
        if (text.isBlank()) return text
        return try {
            var res = text
            res = Regex("""([.!?])([A-Za-z\u3040-\u30ff\u4e00-\u9fff])""").replace(res, "$1 $2")
            res = Regex("""([、。！？；：])([A-Za-z\u3040-\u30ff\u4e00-\u9fff])""").replace(res, "$1 $2")
            res = Regex("""(\.\.\.)([A-Za-z])""").replace(res, "$1 $2")
            res = Regex("""([.!?])\s{2,}([A-Za-z])""").replace(res, "$1 $2")
            res = Regex("""\s+\.""").replace(res, ".")
            res
        } catch (_: Throwable) {
            text
        }
    }

    private fun splitIntoSubtitleLines(text: String, maxCharsPerLine: Int = 42): List<String> {
        if (text.isBlank()) return emptyList()
        return try {
            // Flatten any pre-existing artificial newlines into a continuous clean sentence
            var clean = text.replace("\r", " ").replace("\n", " ").replace(Regex("""\s+"""), " ").trim()
            clean = normalizePunctuationSpacing(clean)
            if (clean.isEmpty()) return emptyList()

            // Deduplicate repeated sentences (e.g. "Change the map. Change the map." -> "Change the map.")
            val sentences = clean.split(Regex("""(?<=[.?!])\s+""")).map { it.trim() }.filter { it.isNotBlank() }
            if (sentences.size >= 2 && sentences.distinct().size == 1) {
                clean = sentences[0]
            }

            // If short enough for a single line, keep on 1 line
            if (clean.length <= maxCharsPerLine) {
                return listOf(clean)
            }

            val words = clean.split(" ").filter { it.isNotEmpty() }
            if (words.size <= 2) return listOf(clean)

            val totalChars = clean.length
            val idealMidpoint = totalChars / 2

            var bestIndex = -1
            var bestScore = Double.MAX_VALUE

            var runningLength = 0
            for (i in 0 until words.size - 1) {
                runningLength += words[i].length + (if (i > 0) 1 else 0)
                val remainingLength = totalChars - runningLength - 1

                // Length imbalance penalty (prefer line 1 and line 2 to have balanced width)
                val diff = kotlin.math.abs(runningLength - idealMidpoint).toDouble()

                // Punctuation bonuses: naturally break after commas, question marks, exclamation marks, periods, semicolons
                val word = words[i]
                val hasClausePunctuation = word.endsWith(",") || word.endsWith(";") || word.endsWith(":") || word.endsWith("—")
                val hasSentencePunctuation = word.endsWith(".") || word.endsWith("?") || word.endsWith("!") || word.endsWith("...")

                var penalty = diff
                if (hasSentencePunctuation) {
                    penalty -= 12.0
                } else if (hasClausePunctuation) {
                    penalty -= 8.0
                }

                // Strong penalty against dangling orphan words (< 6 chars or single word)
                if (runningLength < 8 || remainingLength < 8) {
                    penalty += 20.0
                }
                if (i == 0 || i == words.size - 2) {
                    penalty += 10.0
                }

                if (penalty < bestScore) {
                    bestScore = penalty
                    bestIndex = i
                }
            }

            if (bestIndex in 0 until words.size - 1) {
                val line1 = words.subList(0, bestIndex + 1).joinToString(" ").trim()
                val line2 = words.subList(bestIndex + 1, words.size).joinToString(" ").trim()
                if (line1.equals(line2, ignoreCase = true)) {
                    return listOf(line1)
                }
                return listOf(line1, line2)
            }

            listOf(clean)
        } catch (_: Throwable) {
            listOf(text)
        }
    }

    @Composable
    private fun LiveSubtitleOverlay(
        cues: List<com.lagradost.cloudstream3.ui.animebox.api.LiveSubtitleCue>,
        currentPositionMs: Long,
        fontSize: Float,
        textColor: Int,
        bgOpacity: Int,
        edgeType: Int = androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        subPreset: String = "Default",
        modifier: Modifier = Modifier
    ) {
        if (cues.isEmpty()) return
        val activeCue = remember(currentPositionMs, cues) {
            cues.firstOrNull { currentPositionMs in it.startMs..it.endMs }
        } ?: return

        val rawLines = remember(activeCue.translatedText) {
            splitIntoSubtitleLines(activeCue.translatedText)
        }
        val lines = rawLines.filter { it.isNotBlank() }.distinct()
        if (lines.isEmpty()) return

        val context = LocalContext.current
        val isHardsub = subPreset.equals("Hardsub", ignoreCase = true)
        val googleSansFontFamily = remember {
            try {
                androidx.compose.ui.text.font.FontFamily(
                    androidx.compose.ui.text.font.Font(R.font.google_sans, FontWeight.Normal),
                    androidx.compose.ui.text.font.Font(R.font.google_sans, FontWeight.Medium),
                    androidx.compose.ui.text.font.Font(R.font.google_sans, FontWeight.Bold),
                    androidx.compose.ui.text.font.Font(R.font.google_sans, FontWeight.SemiBold)
                )
            } catch (_: Throwable) {
                androidx.compose.ui.text.font.FontFamily.SansSerif
            }
        }
        val activeFontFamily = if (isHardsub) androidx.compose.ui.text.font.FontFamily.SansSerif else googleSansFontFamily

        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val hasBg = !isHardsub && bgOpacity > 0
            val bgColor = if (hasBg) Color.Black.copy(alpha = (bgOpacity / 255f).coerceIn(0f, 1f)) else Color.Transparent
            val textToDisplay = lines.joinToString("\n")

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .padding(
                        horizontal = if (hasBg) 12.dp else 4.dp,
                        vertical = if (hasBg) 6.dp else 2.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                    if (isHardsub) {
                        // Bold crisp vector stroke outline matching the user's hardsub reference screenshot
                        Text(
                            text = textToDisplay,
                            color = Color.Black,
                            fontSize = fontSize.sp,
                            fontFamily = activeFontFamily,
                            fontWeight = FontWeight.Black,
                            lineHeight = (fontSize * 1.32f).sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = androidx.compose.ui.text.TextStyle(
                                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 11.5f,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        )
                    } else if (edgeType == androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE) {
                        // 4-direction stroke shadow for crisp, readable subtitle text contrast matching native ExoPlayer
                        listOf(
                            Offset(-1.8f, -1.8f),
                            Offset(1.8f, -1.8f),
                            Offset(-1.8f, 1.8f),
                            Offset(1.8f, 1.8f),
                            Offset(0f, -2.2f),
                            Offset(0f, 2.2f),
                            Offset(-2.2f, 0f),
                            Offset(2.2f, 0f)
                        ).forEach { shadowOffset ->
                            Text(
                                text = textToDisplay,
                                color = Color.Black,
                                fontSize = fontSize.sp,
                                fontFamily = googleSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (fontSize * 1.35f).sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black,
                                        offset = shadowOffset,
                                        blurRadius = 0f
                                    )
                                )
                            )
                        }
                    }

                    // Main text layer matching native styling
                    Text(
                        text = textToDisplay,
                        color = if (isHardsub) Color.White else Color(textColor),
                        fontSize = fontSize.sp,
                        fontFamily = activeFontFamily,
                        fontWeight = if (isHardsub) FontWeight.Black else FontWeight.Bold,
                        lineHeight = if (isHardsub) (fontSize * 1.32f).sp else (fontSize * 1.35f).sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = if (!isHardsub && edgeType == androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW) {
                            androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    offset = Offset(2.5f, 2.5f),
                                    blurRadius = 3f
                                )
                            )
                        } else {
                            androidx.compose.ui.text.TextStyle()
                        }
                    )
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
        val isLocalFile = hlsUrl.startsWith("/") || hlsUrl.startsWith("file://") || hlsUrl.startsWith("content://")

        // Configure data source based on local or remote playback
        val dataSourceFactory: androidx.media3.datasource.DataSource.Factory = if (isLocalFile) {
            androidx.media3.datasource.DefaultDataSource.Factory(context)
        } else {
            val effectiveRef = when {
                hlsUrl.contains("4animo", ignoreCase = true) -> "https://cdn.4animo.xyz/"
                hlsUrl.contains("vidhawk", ignoreCase = true) -> "https://vidhawk.buzz/"
                hlsUrl.contains("playeng", ignoreCase = true) || hlsUrl.contains("nukitashi", ignoreCase = true) || hlsUrl.contains("animeapps", ignoreCase = true) -> "https://playeng.animeapps.top/"
                hlsUrl.contains("anidb", ignoreCase = true) || hlsUrl.contains("animex", ignoreCase = true) -> "https://animex.one/"
                hlsUrl.contains("ok.ru", ignoreCase = true) || hlsUrl.contains("okcdn.ru", ignoreCase = true) || hlsUrl.contains("mycdn.me", ignoreCase = true) -> "https://ok.ru/"
                hlsUrl.contains("sssrr.org", ignoreCase = true) || hlsUrl.contains("abyss", ignoreCase = true) -> "https://abyssplayer.com/"
                hlsUrl.contains("vidmoly", ignoreCase = true) || hlsUrl.contains("vmnow", ignoreCase = true) -> "https://vidmoly.net/"
                hlsUrl.contains("videas", ignoreCase = true) -> "https://app.videas.fr/"
                hlsUrl.contains("animedekho", ignoreCase = true) -> "https://animedekho.app/"
                hlsUrl.contains("vidcache", ignoreCase = true) || hlsUrl.contains("animegg", ignoreCase = true) -> "https://www.animegg.org/"
                hlsUrl.contains("groovy.monster", ignoreCase = true) || hlsUrl.contains("razorshell", ignoreCase = true) -> "https://argon.razorshell.space/"
                referer.isNotEmpty() -> referer
                else -> ""
            }

            val cleanRef = if (effectiveRef.contains("?")) effectiveRef.substring(0, effectiveRef.indexOf("?")) else effectiveRef
            val refererHost = try {
                val uri = Uri.parse(hlsUrl)
                "${uri.scheme}://${uri.host}/"
            } catch (e: Exception) { cleanRef }
            val originHeader = try {
                val refUri = if (cleanRef.isNotEmpty()) Uri.parse(cleanRef) else Uri.parse(hlsUrl)
                "${refUri.scheme}://${refUri.host}"
            } catch (e: Exception) { "" }

            val isGroovyOrArgon = hlsUrl.contains("groovy.monster", ignoreCase = true) || hlsUrl.contains("razorshell", ignoreCase = true)
            val isVidcacheOrAnimeGG = hlsUrl.contains("vidcache", ignoreCase = true) || hlsUrl.contains("animegg", ignoreCase = true)
            val finalReferer = when {
                isGroovyOrArgon -> if (cleanRef.contains("argon.razorshell.space")) cleanRef else "https://argon.razorshell.space/"
                isVidcacheOrAnimeGG -> "https://www.animegg.org/"
                cleanRef.isNotEmpty() -> cleanRef
                else -> refererHost
            }
            val requestProperties = mutableMapOf("Referer" to finalReferer)
            if (isGroovyOrArgon) {
                requestProperties["Origin"] = "https://argon.razorshell.space"
                requestProperties["sec-ch-ua"] = """"Chromium";v="124", "Google Chrome";v="124""""
                requestProperties["sec-ch-ua-mobile"] = "?0"
                requestProperties["sec-ch-ua-platform"] = """"Windows""""
            } else if (isVidcacheOrAnimeGG) {
                requestProperties["Origin"] = "https://www.animegg.org"
                requestProperties["sec-ch-ua"] = """"Chromium";v="124", "Google Chrome";v="124""""
                requestProperties["sec-ch-ua-mobile"] = "?0"
                requestProperties["sec-ch-ua-platform"] = """"Windows""""
            } else if (originHeader.isNotEmpty()) {
                requestProperties["Origin"] = originHeader
            }

            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

            val cronetEngine = try {
                com.lagradost.cloudstream3.ui.player.CS3IPlayer.tryCreateEngine(context, 10L * 1024 * 1024)
            } catch (_: Throwable) { null }

            val httpDataSourceFactory: androidx.media3.datasource.HttpDataSource.Factory = if (cronetEngine != null) {
                androidx.media3.datasource.cronet.CronetDataSource.Factory(cronetEngine, java.util.concurrent.Executors.newSingleThreadExecutor())
                    .setUserAgent(userAgent)
                    .setConnectionTimeoutMs(15000)
                    .setReadTimeoutMs(20000)
                    .setResetTimeoutOnRedirects(true)
                    .setHandleSetCookieRequests(true)
                    .setDefaultRequestProperties(requestProperties)
            } else {
                val okHttpClient = try {
                    com.lagradost.cloudstream3.app.baseClient.newBuilder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                } catch (_: Throwable) {
                    val okHttpClientBuilder = okhttp3.OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    okHttpClientBuilder.build()
                }
                OkHttpDataSource.Factory(okHttpClient)
                    .setUserAgent(userAgent)
                    .setDefaultRequestProperties(requestProperties)
            }

            DefaultDataSource.Factory(context, httpDataSourceFactory)
        }

        val effectiveSubtitle = when {
            isLocalFile -> {
                val act = context.findActivity()
                val aId = act?.intent?.getIntExtra("anilistId", 0) ?: 0
                val ep = act?.intent?.getIntExtra("episode", 1) ?: 1
                val passedSub = act?.intent?.getStringExtra("subtitleUrl") ?: ""

                val candidates = mutableListOf<java.io.File>()
                val downloadMgr = com.lagradost.cloudstream3.ui.animebox.download.AnimeDownloadManager.getInstance(context)
                val dnEp = downloadMgr.getDownloadedEpisode(aId, ep)
                if (dnEp != null && dnEp.localSubtitlePath.isNotEmpty() && (dnEp.localSubtitlePath.startsWith("/") || dnEp.localSubtitlePath.startsWith("file://"))) {
                    val f = if (dnEp.localSubtitlePath.startsWith("file://")) java.io.File(java.net.URI(dnEp.localSubtitlePath).path) else java.io.File(dnEp.localSubtitlePath)
                    candidates.add(f)
                }
                candidates.add(java.io.File(downloadMgr.baseDir, "subtitles/sub_${aId}_${ep}.vtt"))
                candidates.add(java.io.File(downloadMgr.baseDir, "subtitles/sub_${aId}_${ep}.srt"))
                if (passedSub.isNotEmpty() && (passedSub.startsWith("/") || passedSub.startsWith("file://"))) {
                    val f = if (passedSub.startsWith("file://")) java.io.File(java.net.URI(passedSub).path) else java.io.File(passedSub)
                    candidates.add(f)
                }
                if (subtitleUrl.isNotEmpty() && (subtitleUrl.startsWith("/") || subtitleUrl.startsWith("file://"))) {
                    val f = if (subtitleUrl.startsWith("file://")) java.io.File(java.net.URI(subtitleUrl).path) else java.io.File(subtitleUrl)
                    candidates.add(f)
                }
                val downloadDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (downloadDir != null) {
                    candidates.add(java.io.File(downloadDir, "AnimeBox/subtitles/sub_${aId}_${ep}.vtt"))
                    candidates.add(java.io.File(downloadDir, "AnimeBox/subtitles/sub_${aId}_${ep}.srt"))
                    candidates.add(java.io.File(downloadDir, "subtitles/sub_${aId}_${ep}.vtt"))
                    candidates.add(java.io.File(downloadDir, "subtitles/sub_${aId}_${ep}.srt"))
                }
                val extDir = context.getExternalFilesDir(null)
                if (extDir != null) {
                    candidates.add(java.io.File(extDir, "AnimeBox/subtitles/sub_${aId}_${ep}.vtt"))
                    candidates.add(java.io.File(extDir, "AnimeBox/subtitles/sub_${aId}_${ep}.srt"))
                }
                val filesDir = context.filesDir
                candidates.add(java.io.File(filesDir, "downloads/AnimeBox/subtitles/sub_${aId}_${ep}.vtt"))
                candidates.add(java.io.File(filesDir, "downloads/AnimeBox/subtitles/sub_${aId}_${ep}.srt"))

                if (hlsUrl.startsWith("/") || hlsUrl.startsWith("file://")) {
                    try {
                        val videoFile = if (hlsUrl.startsWith("file://")) java.io.File(java.net.URI(hlsUrl).path) else java.io.File(hlsUrl)
                        val parent = videoFile.parentFile
                        if (parent != null) {
                            candidates.add(java.io.File(parent, "sub.vtt"))
                            candidates.add(java.io.File(parent, "local.vtt"))
                            candidates.add(java.io.File(parent, "sub.srt"))
                            candidates.add(java.io.File(parent, "sub_${aId}_${ep}.vtt"))
                            candidates.add(java.io.File(parent, "sub_${aId}_${ep}.srt"))
                            candidates.add(java.io.File(parent, "subtitles/sub_${aId}_${ep}.vtt"))
                            candidates.add(java.io.File(parent, "subtitles/sub_${aId}_${ep}.srt"))
                            candidates.add(java.io.File(parent, "${videoFile.nameWithoutExtension}.vtt"))
                            candidates.add(java.io.File(parent, "${videoFile.nameWithoutExtension}.srt"))
                            val grandParent = parent.parentFile
                            if (grandParent != null) {
                                candidates.add(java.io.File(grandParent, "subtitles/sub_${aId}_${ep}.vtt"))
                                candidates.add(java.io.File(grandParent, "subtitles/sub_${aId}_${ep}.srt"))
                                val baseName = parent.name.removeSuffix("_bundle")
                                candidates.add(java.io.File(grandParent, "$baseName.vtt"))
                                candidates.add(java.io.File(grandParent, "$baseName.srt"))
                            }
                        }
                    } catch (_: Exception) {}
                }

                val found = candidates.firstOrNull { it.exists() && it.length() > 0 }
                found?.absolutePath ?: (if (subtitleUrl.isNotEmpty() && subtitleUrl.startsWith("http")) subtitleUrl else "")
            }
            subtitleUrl.isNotEmpty() -> subtitleUrl
            else -> ""
        }

        val subtitleConfig = if (effectiveSubtitle.isNotEmpty()) {
            val subUri = when {
                effectiveSubtitle.startsWith("/") -> Uri.fromFile(java.io.File(effectiveSubtitle))
                effectiveSubtitle.startsWith("file://") -> try { Uri.fromFile(java.io.File(java.net.URI(effectiveSubtitle).path)) } catch (_: Exception) { Uri.parse(effectiveSubtitle) }
                else -> Uri.parse(effectiveSubtitle)
            }
            val isVttContent = try {
                if (effectiveSubtitle.startsWith("/") || effectiveSubtitle.startsWith("file://")) {
                    val f = if (effectiveSubtitle.startsWith("file://")) java.io.File(java.net.URI(effectiveSubtitle).path) else java.io.File(effectiveSubtitle)
                    if (f.exists()) {
                        val firstLine = f.bufferedReader().use { it.readLine() ?: "" }
                        firstLine.contains("WEBVTT")
                    } else false
                } else false
            } catch (_: Exception) { false }

            val subMime = when {
                isVttContent || effectiveSubtitle.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
                effectiveSubtitle.endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
                effectiveSubtitle.endsWith(".ass", ignoreCase = true) || effectiveSubtitle.endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
                else -> MimeTypes.TEXT_VTT
            }
            val detectedSubLang = when {
                effectiveSubtitle.contains("_es_") -> "es"
                effectiveSubtitle.contains("_fr_") -> "fr"
                effectiveSubtitle.contains("_de_") -> "de"
                effectiveSubtitle.contains("_it_") -> "it"
                effectiveSubtitle.contains("_pt_") -> "pt"
                effectiveSubtitle.contains("_ru_") -> "ru"
                effectiveSubtitle.contains("_hi_") -> "hi"
                effectiveSubtitle.contains("_ja_") -> "ja"
                effectiveSubtitle.contains("_ar_") -> "ar"
                effectiveSubtitle.contains("_id_") -> "id"
                else -> "en"
            }

            MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType(subMime)
                .setLanguage(detectedSubLang)
                .setLabel("Subtitle")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                .build()
        } else null

        val effectiveHlsUrl = try {
            if (isLocalFile) {
                val f = if (hlsUrl.startsWith("file://")) java.io.File(java.net.URI(hlsUrl).path) else java.io.File(hlsUrl)
                if (f.exists() && f.length() < 1000) {
                    val content = f.readText().trim()
                    if (content.startsWith("#EXT-LOCAL-HLS:")) {
                        content.substringAfter("#EXT-LOCAL-HLS:").trim()
                    } else if (content.startsWith("#EXTM3U")) {
                        f.absolutePath
                    } else hlsUrl
                } else {
                    val bundleM3u8 = java.io.File(f.parentFile, "${f.nameWithoutExtension}_bundle/local.m3u8")
                    if (bundleM3u8.exists()) bundleM3u8.absolutePath else hlsUrl
                }
            } else hlsUrl
        } catch (_: Exception) { hlsUrl }

        val mediaUri = when {
            effectiveHlsUrl.startsWith("/") -> Uri.fromFile(java.io.File(effectiveHlsUrl))
            effectiveHlsUrl.startsWith("file://") -> try { Uri.fromFile(java.io.File(java.net.URI(effectiveHlsUrl).path)) } catch (_: Exception) { Uri.parse(effectiveHlsUrl) }
            else -> Uri.parse(effectiveHlsUrl)
        }

        val mediaItemBuilder = MediaItem.Builder().setUri(mediaUri)

        val isLocalMpegTs = try {
            if (isLocalFile) {
                val f = if (effectiveHlsUrl.startsWith("file://")) java.io.File(java.net.URI(effectiveHlsUrl).path) else java.io.File(effectiveHlsUrl)
                if (f.exists() && f.length() > 188) {
                    val head = ByteArray(188)
                    f.inputStream().use { it.read(head) }
                    head[0] == 0x47.toByte()
                } else false
            } else false
        } catch (_: Exception) { false }

        // Explicit MIME type eliminates slow sequential extractor sniffing for both local files & streams
        val detectedMime = when {
            effectiveHlsUrl.contains(".m3u8", ignoreCase = true) ||
            effectiveHlsUrl.contains("4animo", ignoreCase = true) ||
            effectiveHlsUrl.contains("vidhawk", ignoreCase = true) ||
            effectiveHlsUrl.contains("playeng", ignoreCase = true) ||
            effectiveHlsUrl.contains("anidb", ignoreCase = true) ||
            effectiveHlsUrl.contains("megaplay", ignoreCase = true) ||
            effectiveHlsUrl.contains("kotocdn", ignoreCase = true) ||
            effectiveHlsUrl.contains("miruro", ignoreCase = true) ||
            effectiveHlsUrl.contains("bakayaro", ignoreCase = true) ||
            effectiveHlsUrl.contains("anikage", ignoreCase = true) ||
            effectiveHlsUrl.contains("pahe", ignoreCase = true) ||
            effectiveHlsUrl.contains("groovy.monster", ignoreCase = true) ||
            effectiveHlsUrl.contains("razorshell", ignoreCase = true) ||
            effectiveHlsUrl.contains("kwik", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            isLocalMpegTs || effectiveHlsUrl.contains(".ts", ignoreCase = true) -> MimeTypes.VIDEO_MP2T
            effectiveHlsUrl.contains(".mp4", ignoreCase = true) && !isLocalMpegTs -> MimeTypes.VIDEO_MP4
            effectiveHlsUrl.contains(".mkv", ignoreCase = true) -> MimeTypes.VIDEO_MATROSKA
            effectiveHlsUrl.contains(".webm", ignoreCase = true) -> MimeTypes.VIDEO_WEBM
            effectiveHlsUrl.contains(".fd", ignoreCase = true) || effectiveHlsUrl.contains("sssrr.org", ignoreCase = true) -> MimeTypes.VIDEO_MP4
            isLocalFile && !isLocalMpegTs && !effectiveHlsUrl.contains(".m3u8", ignoreCase = true) -> MimeTypes.VIDEO_MP4
            else -> null
        }
        if (detectedMime != null) {
            mediaItemBuilder.setMimeType(detectedMime)
        }

        if (subtitleConfig != null) {
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }
        val mediaItem = mediaItemBuilder.build()

        val extractorsFactory = com.lagradost.cloudstream3.ui.player.UpdatedDefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setFragmentedMp4ExtractorFlags(
                androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME or
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

        val mediaSourceFactory = DefaultMediaSourceFactory(
            dataSourceFactory,
            extractorsFactory
        )

        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

        // Instant startup tuning: 0ms pre-buffer wait for local playback to start playing on tick 0
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (isLocalFile) 100 else 1500, // minBufferMs
                if (isLocalFile) 2000 else 50000, // maxBufferMs
                if (isLocalFile) 0 else 1000, // bufferForPlaybackMs (0ms for instant start on local files)
                if (isLocalFile) 0 else 1500  // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                setMediaSource(mediaSource)
                val preferredLang = if (streamType == "hindi") "hi" else if (streamType == "dub") "en" else "ja"
                val initialSubLang = if (subtitleConfig != null) subtitleConfig.language ?: "en" else "en"
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setPreferredAudioLanguage(preferredLang)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage(initialSubLang)
                    .setSelectUndeterminedTextLanguage(true)
                    .setIgnoredTextSelectionFlags(0)
                    .build()
                prepare()
                if (startPositionMs > 0L) {
                    seekTo(startPositionMs)
                }
                playWhenReady = playImmediately
                if (playImmediately) {
                    play()
                }
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

    private suspend fun fetchStreamInfo(anilistId: Int, episodeNum: Int, type: String): Map<String, Any?>? {
        val okSpecialUrl = com.lagradost.cloudstream3.ui.animebox.extractors.PokemonHindiExtractor.POKEMON_OKRU_SPECIAL_URLS[anilistId]
        val useOkRu = when {
            com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId) -> (type == "sub" || type == "hardsub")
            okSpecialUrl != null -> (type == "dub" || type == "sub" || type == "hardsub")
            else -> false
        }
        if (useOkRu) {
            val scStream = com.lagradost.cloudstream3.ui.animebox.extractors.ShinChanOkRuExtractor.extractShinChanStream(anilistId, episodeNum, explicitVideoUrl = okSpecialUrl)
            if (scStream != null && scStream.hlsUrl.isNotEmpty()) {
                return mapOf(
                    "hls" to scStream.hlsUrl,
                    "referer" to scStream.referer,
                    "subtitle" to "",
                    "subtitlesJson" to "[]"
                )
            }
        }
        return com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(this, anilistId, episodeNum, type, intent.getStringExtra("animeTitle"))
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
                "provider" to json.optString("provider", ""),
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
                "backupProvider" to backupProvider,
                "provider" to json.optString("provider", "")
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
            val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId)
            val useShinChanOkRu = isShinChan && (streamType == "sub" || streamType == "hardsub")
            val streamInfo = if (useShinChanOkRu) {
                val sc = withContext(Dispatchers.IO) { com.lagradost.cloudstream3.ui.animebox.extractors.ShinChanOkRuExtractor.extractShinChanStream(anilistId, prevEpNum) }
                if (sc != null && sc.hlsUrl.isNotEmpty()) {
                    mapOf(
                        "hls" to sc.hlsUrl,
                        "referer" to sc.referer,
                        "subtitle" to "",
                        "introStart" to 0L,
                        "introEnd" to 0L,
                        "outroStart" to 0L,
                        "outroEnd" to 0L
                    )
                } else null
            } else {
                com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(
                    context, anilistId, prevEpNum, streamType, animeTitle
                )
            }

            onLoadingStateChanged(false)
            if (streamInfo != null) {
                val prevEpCover = withContext(Dispatchers.IO) {
                    if (isShinChan) {
                        com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getAllEpisodeCovers(context)[prevEpNum] ?: showCoverUrl
                    } else {
                        val tmdbId = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getLongRunningTmdbId(anilistId)
                        if (tmdbId != null) {
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
                }

                val intent = Intent(context, AnimeBoxPlayerActivity::class.java).apply {
                    putExtra("hlsUrl", streamInfo["hls"] as String)
                    putExtra("referer", streamInfo["referer"] as String)
                    putExtra("subtitleUrl", (streamInfo["subtitle"] as? String) ?: "")
                    putExtra("introStart", (streamInfo["introStart"] as? Long) ?: 0L)
                    putExtra("introEnd", (streamInfo["introEnd"] as? Long) ?: 0L)
                    putExtra("outroStart", (streamInfo["outroStart"] as? Long) ?: 0L)
                    putExtra("outroEnd", (streamInfo["outroEnd"] as? Long) ?: 0L)
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
            val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId)
            val useShinChanOkRu = isShinChan && (streamType == "sub" || streamType == "hardsub")
            val streamInfo = if (useShinChanOkRu) {
                val sc = withContext(Dispatchers.IO) { com.lagradost.cloudstream3.ui.animebox.extractors.ShinChanOkRuExtractor.extractShinChanStream(anilistId, nextEpNum) }
                if (sc != null && sc.hlsUrl.isNotEmpty()) {
                    mapOf(
                        "hls" to sc.hlsUrl,
                        "referer" to sc.referer,
                        "subtitle" to "",
                        "introStart" to 0L,
                        "introEnd" to 0L,
                        "outroStart" to 0L,
                        "outroEnd" to 0L
                    )
                } else null
            } else {
                com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(
                    context, anilistId, nextEpNum, streamType, animeTitle
                )
            }

            onLoadingStateChanged(false)
            if (streamInfo != null) {
                val nextEpCover = withContext(Dispatchers.IO) {
                    if (isShinChan) {
                        com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getAllEpisodeCovers(context)[nextEpNum] ?: showCoverUrl
                    } else {
                        val tmdbId = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getLongRunningTmdbId(anilistId)
                        if (tmdbId != null) {
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
                }

                val intent = Intent(context, AnimeBoxPlayerActivity::class.java).apply {
                    putExtra("hlsUrl", streamInfo["hls"] as String)
                    putExtra("referer", streamInfo["referer"] as String)
                    putExtra("subtitleUrl", (streamInfo["subtitle"] as? String) ?: "")
                    putExtra("introStart", (streamInfo["introStart"] as? Long) ?: 0L)
                    putExtra("introEnd", (streamInfo["introEnd"] as? Long) ?: 0L)
                    putExtra("outroStart", (streamInfo["outroStart"] as? Long) ?: 0L)
                    putExtra("outroEnd", (streamInfo["outroEnd"] as? Long) ?: 0L)
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
            val p = exoPlayer
            if (p != null && p.isPlaying && !isFinishing && !isCurrentlyInPip) {
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
        if (isCurrentlyInPip && !isInPictureInPictureMode) {
            // When PiP is dismissed / closed by the user, Android calls onStop().
            // Terminate task completely and release player so no extra window is left behind.
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
        } else if (isFinishing) {
            try {
                exoPlayer?.pause()
                exoPlayer?.stop()
                exoPlayer?.release()
                exoPlayer = null
            } catch (e: Exception) {
                e.printStackTrace()
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

// ─── Custom Player Vector Icons and Skip 10s Composable (Matching Reference Screenshot 1) ───

val FastForwardPlayerIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "FastForwardPlayer",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.White)
    ) {
        moveTo(4f, 18f)
        lineTo(11f, 12f)
        lineTo(4f, 6f)
        close()
        moveTo(11f, 18f)
        lineTo(18f, 12f)
        lineTo(11f, 6f)
        close()
        moveTo(18.5f, 6f)
        horizontalLineTo(20.5f)
        verticalLineTo(18f)
        horizontalLineTo(18.5f)
        close()
    }.build()

val PlayerSolidPlayIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "PlayerSolidPlay",
        defaultWidth = 64.dp,
        defaultHeight = 64.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.White)
    ) {
        moveTo(8f, 5.14f)
        curveTo(8f, 4.36f, 8.85f, 3.88f, 9.52f, 4.28f)
        lineTo(19.56f, 10.22f)
        curveTo(20.21f, 10.61f, 20.21f, 11.55f, 19.56f, 11.94f)
        lineTo(9.52f, 17.88f)
        curveTo(8.85f, 18.28f, 8f, 17.8f, 8f, 17.02f)
        close()
    }.build()

val PlayerSolidPauseIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "PlayerSolidPause",
        defaultWidth = 56.dp,
        defaultHeight = 56.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.White)
    ) {
        moveTo(6f, 5f)
        curveTo(6f, 4.45f, 6.45f, 4f, 7f, 4f)
        horizontalLineTo(9f)
        curveTo(9.55f, 4f, 10f, 4.45f, 10f, 5f)
        verticalLineTo(19f)
        curveTo(10f, 19.55f, 9.55f, 20f, 9f, 20f)
        horizontalLineTo(7f)
        curveTo(6.45f, 20f, 6f, 19.55f, 6f, 19f)
        close()
        moveTo(14f, 5f)
        curveTo(14f, 4.45f, 14.45f, 4f, 15f, 4f)
        horizontalLineTo(17f)
        curveTo(17.55f, 4f, 18f, 4.45f, 18f, 5f)
        verticalLineTo(19f)
        curveTo(18f, 19.55f, 17.55f, 20f, 17f, 20f)
        horizontalLineTo(15f)
        curveTo(14.45f, 20f, 14f, 19.55f, 14f, 19f)
        close()
    }.build()

@Composable
fun Skip10CircleButton(
    isForward: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(112.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        Icon(
            painter = painterResource(id = if (isForward) R.drawable.netflix_skip_forward else R.drawable.netflix_skip_back),
            contentDescription = if (isForward) "Forward 10" else "Rewind 10",
            tint = Color.White,
            modifier = Modifier.size(82.dp)
        )
        Text(
            text = "10",
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Black,
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
            ),
            modifier = Modifier.offset(y = 2.dp)
        )
    }
}

