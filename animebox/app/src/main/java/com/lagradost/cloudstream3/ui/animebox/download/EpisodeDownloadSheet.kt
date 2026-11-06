package com.lagradost.cloudstream3.ui.animebox.download

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.rememberAsyncImagePainter
import com.lagradost.cloudstream3.ui.animebox.CloudDownloadIcon
import com.lagradost.cloudstream3.ui.animebox.api.EpisodeMeta
import com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine
import com.lagradost.cloudstream3.ui.animebox.settings.PixelSettingsIcon
import com.lagradost.cloudstream3.ui.animebox.settings.SettingsIconType
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class DirectDownloadOption(
    val title: String,
    val quality: String,
    val size: String,
    val url: String,
    val provider: String = "Direct Stream",
    val referer: String = "",
    val subtitleUrl: String = "",
    val subtitlesJson: String = "[]",
    val introStart: Long = 0L,
    val introEnd: Long = 0L,
    val outroStart: Long = 0L,
    val outroEnd: Long = 0L,
    val streamType: String = "sub"
)

@Composable
fun EpisodeDownloadSheet(
    show: Boolean,
    anilistId: Int,
    animeTitle: String,
    coverUrl: String,
    episodesCount: Int,
    episodeMetaMap: Map<Int, EpisodeMeta>,
    onDismiss: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val downloadManager = remember { AnimeDownloadManager.getInstance(context) }

    // Live reactive download state
    val downloadedEpisodes by downloadManager.episodesState.collectAsState()

    var selectedEpForQuality by remember { mutableStateOf<Int?>(null) }
    var availableOptions by remember { mutableStateOf<List<DirectDownloadOption>>(emptyList()) }
    var isLoadingQualities by remember { mutableStateOf(false) }

    var showWifiDialog by remember { mutableStateOf(false) }
    var pendingDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun checkWifiAndExecute(action: () -> Unit) {
        val prefs = context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE)
        val wifiOnly = prefs.getBoolean("wifi_only_downloads", false)
        val isWifi = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val net = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(net)
            caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true || caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true
        } catch (_: Exception) { true }

        if (wifiOnly && !isWifi) {
            pendingDownloadAction = action
            showWifiDialog = true
        } else {
            action()
        }
    }

    val totalEps = episodesCount.coerceAtLeast(1)
    val downloadedCount = downloadedEpisodes.count { it.anilistId == anilistId && it.status == DownloadStatus.COMPLETED }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0A0A0C)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF18181C))
                        ) {
                            PixelSettingsIcon(
                                type = SettingsIconType.CHEVRON_LEFT,
                                tint = Color.White,
                                sizeDp = 20
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Downloads",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Offline Playback with Subtitles & Skip Intro",
                                fontSize = 12.sp,
                                color = Color(0xFF9E9EA4),
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF18181C))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFC0C0C6),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Anime Summary Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141418))
                        .border(1.dp, Color(0xFF222228), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = coverUrl),
                            contentDescription = animeTitle,
                            modifier = Modifier
                                .size(width = 54.dp, height = 76.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = animeTitle,
                                color = Color.White,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF2A2A32))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$totalEps Episodes",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (downloadedCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF1E3A2B))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "$downloadedCount Ready Offline",
                                            color = Color(0xFF4ADE80),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Batch Download Full Season Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.White, Color(0xFFE5E7EB))
                            )
                        )
                        .clickable {
                            checkWifiAndExecute {
                                val prefs = context.getSharedPreferences("AnimeBoxPlayer", Context.MODE_PRIVATE)
                                val preferredLang = if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context)) {
                                    val lastAud = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getLastUsedAudio(context)
                                    val lastSub = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getLastUsedSubMode(context)
                                    if (lastSub == "Hard Sub") "hardsub"
                                    else if (lastAud == "English") "dub"
                                    else if (lastAud == "Hindi") "hindi"
                                    else "sub"
                                } else {
                                    val sAud = prefs.getString("selectedAudio", "Japanese (Original)")
                                    if (sAud == "English") "dub" else if (sAud == "Hindi") "hindi" else "sub"
                                }
                                val langLabel = if (preferredLang == "hindi") "Hindi Dub" else if (preferredLang == "dub") "English Dub" else "Japanese Sub"
                                Toast.makeText(context, "Queuing full season for download ($langLabel)...", Toast.LENGTH_SHORT).show()
                                coroutineScope.launch {
                                    for (ep in 1..totalEps) {
                                        val isAlreadyDone = downloadManager.isEpisodeDownloaded(anilistId, ep)
                                        if (!isAlreadyDone) {
                                            val targetLang = preferredLang
                                            var streamInfo = AnimeStreamExtractorEngine.getStreamInfo(context, anilistId, ep, targetLang)
                                            var actualLang = targetLang
                                            if ((streamInfo?.get("hls") as? String).isNullOrEmpty() && targetLang == "hardsub") {
                                                streamInfo = AnimeStreamExtractorEngine.getStreamInfo(context, anilistId, ep, "sub")
                                                actualLang = "sub"
                                            }
                                            val directHls = (streamInfo?.get("hls") as? String) ?: ""
                                            val directReferer = (streamInfo?.get("referer") as? String) ?: ""
                                            val directSub = (streamInfo?.get("subtitle") as? String) ?: ""
                                            val subsJson = (streamInfo?.get("subtitlesJson") as? String) ?: "[]"
                                            val iStart = (streamInfo?.get("introStart") as? Long) ?: 0L
                                            val iEnd = (streamInfo?.get("introEnd") as? Long) ?: 0L
                                            val oStart = (streamInfo?.get("outroStart") as? Long) ?: 0L
                                            val oEnd = (streamInfo?.get("outroEnd") as? Long) ?: 0L
                                            if (directHls.isNotEmpty()) {
                                                val epMeta = episodeMetaMap[ep]
                                                val specificCover = epMeta?.imageUrl?.ifEmpty { coverUrl } ?: coverUrl
                                                val qualityTag = if (actualLang == "hindi") "1080p Hindi" else if (actualLang == "dub") "1080p Dub" else if (actualLang == "hardsub") "1080p HSub" else "1080p Sub"
                                                downloadManager.enqueueDownload(
                                                    anilistId = anilistId,
                                                    animeTitle = animeTitle,
                                                    episodeNum = ep,
                                                    episodeTitle = epMeta?.title ?: "Episode $ep",
                                                    coverUrl = specificCover,
                                                    showCoverUrl = coverUrl,
                                                    quality = qualityTag,
                                                    resolvedUrl = directHls,
                                                    referer = directReferer,
                                                    subtitleUrl = directSub,
                                                    subtitlesJson = subsJson,
                                                    introStart = iStart,
                                                    introEnd = iEnd,
                                                    outroStart = oStart,
                                                    outroEnd = oEnd,
                                                    streamType = actualLang
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = CloudDownloadIcon,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download Full Season (All Episodes)",
                            color = Color.Black,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Episode List with 16:9 thumbnails and status
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items((1..totalEps).toList()) { epNum ->
                        val meta = episodeMetaMap[epNum]
                        val epTitle = meta?.title ?: "Episode $epNum"
                        val epThumb = meta?.imageUrl?.ifEmpty { coverUrl } ?: coverUrl

                        val epDownload = downloadedEpisodes.find { it.anilistId == anilistId && it.episodeNumber == epNum }
                        val isDownloaded = epDownload?.status == DownloadStatus.COMPLETED
                        val isDownloading = epDownload?.status == DownloadStatus.DOWNLOADING
                        val progress = epDownload?.downloadProgress ?: 0
                        val speedText = epDownload?.downloadSpeedText ?: ""

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF141418))
                                .border(
                                    1.dp,
                                    if (isDownloading) Color.White.copy(alpha = 0.3f) else Color(0xFF202026),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (isDownloading) {
                                        coroutineScope.launch {
                                            downloadManager.cancelDownload(anilistId, epNum)
                                            Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                                        }
                                    } else if (!isDownloaded) {
                                        selectedEpForQuality = epNum
                                        isLoadingQualities = true
                                        availableOptions = emptyList()

                                        coroutineScope.launch {
                                            // Subtitled Stream Option (Japanese Soft Sub)
                                            val subDeferred = async {
                                                try {
                                                    val streamInfo = AnimeStreamExtractorEngine.getStreamInfo(context, anilistId, epNum, "sub")
                                                    val directMp4 = (streamInfo?.get("mp4Url") as? String)?.takeIf { it.isNotEmpty() }
                                                    val directHls = directMp4 ?: ((streamInfo?.get("hls") as? String) ?: "")
                                                    val directRef = (streamInfo?.get("referer") as? String) ?: ""
                                                    val directSub = (streamInfo?.get("subtitle") as? String) ?: ""
                                                    val subsJson = (streamInfo?.get("subtitlesJson") as? String) ?: "[]"
                                                    val iStart = (streamInfo?.get("introStart") as? Long) ?: 0L
                                                    val iEnd = (streamInfo?.get("introEnd") as? Long) ?: 0L
                                                    val oStart = (streamInfo?.get("outroStart") as? Long) ?: 0L
                                                    val oEnd = (streamInfo?.get("outroEnd") as? Long) ?: 0L
                                                    if (directHls.isNotEmpty()) {
                                                        DirectDownloadOption(
                                                            title = "Japanese Soft Sub (1080p HD)",
                                                            quality = "1080p Sub",
                                                            size = "Full HD HLS",
                                                            url = directHls,
                                                            provider = "Direct Stream",
                                                            referer = directRef,
                                                            subtitleUrl = directSub,
                                                            subtitlesJson = subsJson,
                                                            introStart = iStart,
                                                            introEnd = iEnd,
                                                            outroStart = oStart,
                                                            outroEnd = oEnd,
                                                            streamType = "sub"
                                                        )
                                                    } else null
                                                } catch (_: Exception) { null }
                                            }

                                            // Hard Sub Stream Option (Japanese Burned-in Subtitles / HSub)
                                            val hardsubDeferred = async {
                                                try {
                                                    val streamInfo = AnimeStreamExtractorEngine.getStreamInfo(context, anilistId, epNum, "hardsub")
                                                    val directMp4 = (streamInfo?.get("mp4Url") as? String)?.takeIf { it.isNotEmpty() }
                                                    val directHls = directMp4 ?: ((streamInfo?.get("hls") as? String) ?: "")
                                                    val directRef = (streamInfo?.get("referer") as? String) ?: ""
                                                    val directSub = (streamInfo?.get("subtitle") as? String) ?: ""
                                                    val subsJson = (streamInfo?.get("subtitlesJson") as? String) ?: "[]"
                                                    val iStart = (streamInfo?.get("introStart") as? Long) ?: 0L
                                                    val iEnd = (streamInfo?.get("introEnd") as? Long) ?: 0L
                                                    val oStart = (streamInfo?.get("outroStart") as? Long) ?: 0L
                                                    val oEnd = (streamInfo?.get("outroEnd") as? Long) ?: 0L
                                                    if (directHls.isNotEmpty()) {
                                                        DirectDownloadOption(
                                                            title = "Japanese Hard Sub / HSub (1080p HD)",
                                                            quality = "1080p HSub",
                                                            size = "Burned-in Subtitles",
                                                            url = directHls,
                                                            provider = "Direct Stream",
                                                            referer = directRef,
                                                            subtitleUrl = directSub,
                                                            subtitlesJson = subsJson,
                                                            introStart = iStart,
                                                            introEnd = iEnd,
                                                            outroStart = oStart,
                                                            outroEnd = oEnd,
                                                            streamType = "hardsub"
                                                        )
                                                    } else null
                                                } catch (_: Exception) { null }
                                            }

                                            // English Dub Stream Option
                                            val dubDeferred = async {
                                                try {
                                                    val streamInfo = AnimeStreamExtractorEngine.getStreamInfo(context, anilistId, epNum, "dub")
                                                    val directMp4 = (streamInfo?.get("mp4Url") as? String)?.takeIf { it.isNotEmpty() }
                                                    val directHls = directMp4 ?: ((streamInfo?.get("hls") as? String) ?: "")
                                                    val directRef = (streamInfo?.get("referer") as? String) ?: ""
                                                    val directSub = (streamInfo?.get("subtitle") as? String) ?: ""
                                                    val subsJson = (streamInfo?.get("subtitlesJson") as? String) ?: "[]"
                                                    val iStart = (streamInfo?.get("introStart") as? Long) ?: 0L
                                                    val iEnd = (streamInfo?.get("introEnd") as? Long) ?: 0L
                                                    val oStart = (streamInfo?.get("outroStart") as? Long) ?: 0L
                                                    val oEnd = (streamInfo?.get("outroEnd") as? Long) ?: 0L
                                                    if (directHls.isNotEmpty()) {
                                                        DirectDownloadOption(
                                                            title = "English Dubbed (HD)",
                                                            quality = "1080p Dub",
                                                            size = "HD Stream",
                                                            url = directHls,
                                                            provider = "Direct Stream",
                                                            referer = directRef,
                                                            subtitleUrl = directSub,
                                                            subtitlesJson = subsJson,
                                                            introStart = iStart,
                                                            introEnd = iEnd,
                                                            outroStart = oStart,
                                                            outroEnd = oEnd,
                                                            streamType = "dub"
                                                        )
                                                    } else null
                                                } catch (_: Exception) { null }
                                            }

                                             // Hindi Dub Stream Option (if available)
                                            val hindiDeferred = async {
                                                if (!com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isLanguageEnabled("hindi")) return@async null
                                                try {
                                                    val streamInfo = AnimeStreamExtractorEngine.getStreamInfo(context, anilistId, epNum, "hindi")
                                                    val directMp4 = (streamInfo?.get("mp4Url") as? String)?.takeIf { it.isNotEmpty() }
                                                    val directHls = directMp4 ?: ((streamInfo?.get("hls") as? String) ?: "")
                                                    val directRef = (streamInfo?.get("referer") as? String) ?: ""
                                                    val directSub = (streamInfo?.get("subtitle") as? String) ?: ""
                                                    val subsJson = (streamInfo?.get("subtitlesJson") as? String) ?: "[]"
                                                    val iStart = (streamInfo?.get("introStart") as? Long) ?: 0L
                                                    val iEnd = (streamInfo?.get("introEnd") as? Long) ?: 0L
                                                    val oStart = (streamInfo?.get("outroStart") as? Long) ?: 0L
                                                    val oEnd = (streamInfo?.get("outroEnd") as? Long) ?: 0L
                                                    if (directHls.isNotEmpty()) {
                                                        DirectDownloadOption(
                                                            title = "Hindi Dubbed (HD)",
                                                            quality = "1080p Hindi",
                                                            size = "HD Stream",
                                                            url = directHls,
                                                            provider = "Direct Stream",
                                                            referer = directRef,
                                                            subtitleUrl = directSub,
                                                            subtitlesJson = subsJson,
                                                            introStart = iStart,
                                                            introEnd = iEnd,
                                                            outroStart = oStart,
                                                            outroEnd = oEnd,
                                                            streamType = "hindi"
                                                        )
                                                    } else null
                                                } catch (_: Exception) { null }
                                            }

                                            // French VF Stream Option (if available)
                                            val frenchDeferred = async {
                                                if (!com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isLanguageEnabled("french")) return@async null
                                                try {
                                                    val streamInfo = AnimeStreamExtractorEngine.getStreamInfo(context, anilistId, epNum, "french")
                                                    val directHls = (streamInfo?.get("hls") as? String) ?: ""
                                                    val directRef = (streamInfo?.get("referer") as? String) ?: ""
                                                    val directSub = (streamInfo?.get("subtitle") as? String) ?: ""
                                                    val subsJson = (streamInfo?.get("subtitlesJson") as? String) ?: "[]"
                                                    if (directHls.isNotEmpty()) {
                                                        DirectDownloadOption(
                                                            title = "French Dub / VF (HD)",
                                                            quality = "1080p French (VF)",
                                                            size = "HD Stream",
                                                            url = directHls,
                                                            provider = (streamInfo?.get("provider") as? String) ?: "Direct Stream",
                                                            referer = directRef,
                                                            subtitleUrl = directSub,
                                                            subtitlesJson = subsJson,
                                                            streamType = "french"
                                                        )
                                                    } else null
                                                } catch (_: Exception) { null }
                                            }

                                            // Italian ITA Stream Option (if available)
                                            val italianDeferred = async {
                                                if (!com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isLanguageEnabled("italian")) return@async null
                                                try {
                                                    val streamInfo = AnimeStreamExtractorEngine.getStreamInfo(context, anilistId, epNum, "italian")
                                                    val directHls = (streamInfo?.get("hls") as? String) ?: ""
                                                    val directRef = (streamInfo?.get("referer") as? String) ?: ""
                                                    val directSub = (streamInfo?.get("subtitle") as? String) ?: ""
                                                    val subsJson = (streamInfo?.get("subtitlesJson") as? String) ?: "[]"
                                                    if (directHls.isNotEmpty()) {
                                                        DirectDownloadOption(
                                                            title = "Italian Dub / ITA (HD)",
                                                            quality = "1080p Italian (ITA)",
                                                            size = "HD Stream",
                                                            url = directHls,
                                                            provider = (streamInfo?.get("provider") as? String) ?: "Direct Stream",
                                                            referer = directRef,
                                                            subtitleUrl = directSub,
                                                            subtitlesJson = subsJson,
                                                            streamType = "italian"
                                                        )
                                                    } else null
                                                } catch (_: Exception) { null }
                                            }

                                            // Spanish Latino Stream Option (if available)
                                            val spanishLatinoDeferred = async {
                                                if (!com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isLanguageEnabled("spanish_latino")) return@async null
                                                try {
                                                    val streamInfo = AnimeStreamExtractorEngine.getStreamInfo(context, anilistId, epNum, "spanish_latino")
                                                    val directHls = (streamInfo?.get("hls") as? String) ?: ""
                                                    val directRef = (streamInfo?.get("referer") as? String) ?: ""
                                                    val directSub = (streamInfo?.get("subtitle") as? String) ?: ""
                                                    val subsJson = (streamInfo?.get("subtitlesJson") as? String) ?: "[]"
                                                    if (directHls.isNotEmpty()) {
                                                        DirectDownloadOption(
                                                            title = "Spanish Dub / Latino (HD)",
                                                            quality = "1080p Spanish (Latino)",
                                                            size = "HD Stream",
                                                            url = directHls,
                                                            provider = (streamInfo?.get("provider") as? String) ?: "Direct Stream",
                                                            referer = directRef,
                                                            subtitleUrl = directSub,
                                                            subtitlesJson = subsJson,
                                                            streamType = "spanish_latino"
                                                        )
                                                    } else null
                                                } catch (_: Exception) { null }
                                            }

                                            // Spanish Castellano Stream Option (if available)
                                            val spanishCastellanoDeferred = async {
                                                if (!com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isLanguageEnabled("spanish_castellano")) return@async null
                                                try {
                                                    val streamInfo = AnimeStreamExtractorEngine.getStreamInfo(context, anilistId, epNum, "spanish_castellano")
                                                    val directHls = (streamInfo?.get("hls") as? String) ?: ""
                                                    val directRef = (streamInfo?.get("referer") as? String) ?: ""
                                                    val directSub = (streamInfo?.get("subtitle") as? String) ?: ""
                                                    val subsJson = (streamInfo?.get("subtitlesJson") as? String) ?: "[]"
                                                    if (directHls.isNotEmpty()) {
                                                        DirectDownloadOption(
                                                            title = "Spanish Dub / Castellano (HD)",
                                                            quality = "1080p Spanish (Castellano)",
                                                            size = "HD Stream",
                                                            url = directHls,
                                                            provider = (streamInfo?.get("provider") as? String) ?: "Direct Stream",
                                                            referer = directRef,
                                                            subtitleUrl = directSub,
                                                            subtitlesJson = subsJson,
                                                            streamType = "spanish_castellano"
                                                        )
                                                    } else null
                                                } catch (_: Exception) { null }
                                            }

                                            // German Deutsch Stream Option (if available)
                                            val germanDeferred = async {
                                                if (!com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isLanguageEnabled("german")) return@async null
                                                try {
                                                    val streamInfo = AnimeStreamExtractorEngine.getStreamInfo(context, anilistId, epNum, "german")
                                                    val directHls = (streamInfo?.get("hls") as? String) ?: ""
                                                    val directRef = (streamInfo?.get("referer") as? String) ?: ""
                                                    val directSub = (streamInfo?.get("subtitle") as? String) ?: ""
                                                    val subsJson = (streamInfo?.get("subtitlesJson") as? String) ?: "[]"
                                                    if (directHls.isNotEmpty()) {
                                                        DirectDownloadOption(
                                                            title = "German Dub / Deutsch (HD)",
                                                            quality = "1080p German (Deutsch)",
                                                            size = "HD Stream",
                                                            url = directHls,
                                                            provider = (streamInfo?.get("provider") as? String) ?: "Direct Stream",
                                                            referer = directRef,
                                                            subtitleUrl = directSub,
                                                            subtitlesJson = subsJson,
                                                            streamType = "german"
                                                        )
                                                    } else null
                                                } catch (_: Exception) { null }
                                            }

                                            val hardsubOpt = hardsubDeferred.await()
                                            val subOpt = subDeferred.await()
                                            val dubOpt = dubDeferred.await()
                                            val hindiOpt = hindiDeferred.await()
                                            val frenchOpt = frenchDeferred.await()
                                            val italianOpt = italianDeferred.await()
                                            val spanishLatinoOpt = spanishLatinoDeferred.await()
                                            val spanishCastellanoOpt = spanishCastellanoDeferred.await()
                                            val germanOpt = germanDeferred.await()

                                            availableOptions = listOfNotNull(
                                                hardsubOpt, subOpt, dubOpt, hindiOpt,
                                                frenchOpt, italianOpt, spanishLatinoOpt, spanishCastellanoOpt, germanOpt
                                            )
                                            isLoadingQualities = false
                                        }
                                    }
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 16:9 Thumbnail preview
                            Box(
                                modifier = Modifier
                                    .width(88.dp)
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF222228))
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(model = epThumb),
                                    contentDescription = epTitle,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(topEnd = 6.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "EP $epNum",
                                        color = Color.White,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Episode $epNum",
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isDownloading) "Downloading • $progress% • $speedText" else epTitle,
                                    color = if (isDownloading) Color.White else Color(0xFF9E9EA4),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (isDownloading) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = (progress / 100f).coerceIn(0f, 1f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = Color.White,
                                        trackColor = Color(0xFF2A2A32)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isDownloaded) Color.White
                                        else if (isDownloading) Color(0xFF2A2A32)
                                        else Color(0xFF1E1E24)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDownloaded) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Downloaded",
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else if (isDownloading) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = CloudDownloadIcon,
                                        contentDescription = "Download",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quality selection dialog for chosen episode
        if (selectedEpForQuality != null) {
            val epNum = selectedEpForQuality!!
            val meta = episodeMetaMap[epNum]
            val epTitle = meta?.title ?: "Episode $epNum"

            Dialog(onDismissRequest = { selectedEpForQuality = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF141418))
                        .border(1.dp, Color(0xFF282830), RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Download Episode $epNum",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = epTitle,
                                    color = Color(0xFF9E9EA4),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { selectedEpForQuality = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF8E918F),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isLoadingQualities) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Fetching streams & subtitles...", color = Color(0xFF9E9EA4), fontSize = 13.sp)
                            }
                        } else if (availableOptions.isEmpty()) {
                            Text(
                                text = "No stream sources available for this episode.",
                                color = Color(0xFF9E9EA4),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                availableOptions.forEach { opt ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1C1C22))
                                            .border(1.dp, Color(0xFF2A2A34), RoundedCornerShape(12.dp))
                                            .clickable {
                                                val specificCover = meta?.imageUrl?.ifEmpty { coverUrl } ?: coverUrl
                                                downloadManager.enqueueDownload(
                                                    anilistId = anilistId,
                                                    animeTitle = animeTitle,
                                                    episodeNum = epNum,
                                                    episodeTitle = epTitle,
                                                    coverUrl = specificCover,
                                                    quality = opt.quality,
                                                    resolvedUrl = opt.url,
                                                    referer = opt.referer,
                                                    subtitleUrl = opt.subtitleUrl,
                                                    subtitlesJson = opt.subtitlesJson,
                                                    introStart = opt.introStart,
                                                    introEnd = opt.introEnd,
                                                    outroStart = opt.outroStart,
                                                    outroEnd = opt.outroEnd,
                                                    streamType = opt.streamType
                                                )
                                                Toast.makeText(context, "Started downloading ${opt.quality}", Toast.LENGTH_SHORT).show()
                                                selectedEpForQuality = null
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = opt.title,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "${opt.quality} • ${opt.size}",
                                                    color = Color(0xFF9E9EA4),
                                                    fontSize = 11.5.sp
                                                )
                                                if (opt.subtitleUrl.isNotEmpty()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(Color(0xFF2B2B38))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text("CC Sub", color = Color(0xFFB0B0C0), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                if (opt.introEnd > 0L) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(Color(0xFF23352B))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text("Skip Intro", color = Color(0xFF6EE7B7), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                        Icon(
                                            imageVector = CloudDownloadIcon,
                                            contentDescription = "Download",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Wi-Fi Only Confirmation Dialog
        if (showWifiDialog) {
            AlertDialog(
                onDismissRequest = { showWifiDialog = false },
                title = { Text("Download Over Wi-Fi Only", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "You are currently on mobile cellular data. Would you like to turn off 'Download Over Wi-Fi Only' to proceed?",
                        color = Color(0xFFD1D1D6),
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("wifi_only_downloads", false).apply()
                            showWifiDialog = false
                            pendingDownloadAction?.invoke()
                            pendingDownloadAction = null
                        }
                    ) {
                        Text("Turn Off & Download", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWifiDialog = false; pendingDownloadAction = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E1E24)
            )
        }
    }
}
