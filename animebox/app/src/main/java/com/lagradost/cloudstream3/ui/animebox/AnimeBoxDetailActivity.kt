package com.lagradost.cloudstream3.ui.animebox

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.ModalBottomSheet
import android.speech.RecognizerIntent
import android.app.Activity
import com.lagradost.cloudstream3.ui.animebox.library.StatusWatchingIcon
import com.lagradost.cloudstream3.ui.animebox.library.StatusPlanToWatchIcon
import com.lagradost.cloudstream3.ui.animebox.library.StatusCompletedIcon
import com.lagradost.cloudstream3.ui.animebox.library.StatusOnHoldIcon
import com.lagradost.cloudstream3.ui.animebox.library.StatusDroppedIcon
import androidx.compose.ui.graphics.graphicsLayer

import com.lagradost.cloudstream3.ui.animebox.extractors.HentaiMamaExtractor
import com.lagradost.cloudstream3.ui.animebox.extractors.HentaiHavenExtractor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import com.lagradost.cloudstream3.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import com.lagradost.cloudstream3.ui.animebox.api.AiChatMessage
import com.lagradost.cloudstream3.ui.animebox.api.AiPlayEpisodeAction
import com.lagradost.cloudstream3.ui.animebox.api.AiSearchImage
import com.lagradost.cloudstream3.ui.animebox.api.AiVideoResult
import com.lagradost.cloudstream3.ui.animebox.api.OpenRouterChatClient
import com.lagradost.cloudstream3.ui.animebox.api.WatchOrderMapItem
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings
import com.lagradost.cloudstream3.ui.animebox.download.AnimeDownloadManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.scale
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
import com.lagradost.cloudstream3.ui.animebox.api.WatchOrderManager
import com.lagradost.cloudstream3.ui.animebox.api.WatchOrderItem

data class QuickOverviewDetailAnime(
    val id: Int,
    val title: String,
    val coverUrl: String,
    val epCount: Int
)

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
    val format: String = "",
    val status: String = "",
    val episodes: Int? = null
)

data class AnimeRecommendation(
    val id: Int,
    val title: String,
    val coverUrl: String,
    val format: String,
    val status: String = "",
    val episodes: Int? = null
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
    val nextAiring: NextAiringEpisode? = null,
    val popularity: Int = 0,
    val favourites: Int = 0,
    val duration: Int = 0,
    val tags: List<String> = emptyList(),
    val nativeTitle: String = "",
    val romajiTitle: String = "",
    val synonyms: List<String> = emptyList()
)

data class DownloadQualityPromptState(
    val episodeNum: Int,
    val episodeTitle: String,
    val animeTitle: String,
    val availableQualities: List<String>,
    val preferredQuality: String,
    val isPreferredMissing: Boolean,
    val subtitleLanguages: List<String> = emptyList(),
    val preferredSubtitleLanguage: String = "English",
    val isSeason: Boolean = false,
    val currentServer: String = "",
    val availableServers: List<com.lagradost.cloudstream3.ui.animebox.settings.ServerOption> = emptyList(),
    val onServerSelected: (suspend (String) -> Pair<List<String>, List<String>>)? = null,
    val onQualitySelected: (String, String) -> Unit
)

private fun subtitleLanguageOptions(subtitlesJson: String): List<String> = runCatching {
    val array = org.json.JSONArray(subtitlesJson)
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val u = item.optString("url", item.optString("file", item.optString("src", item.optString("link", item.optString("subtitle", ""))))).lowercase()
        val kind = item.optString("kind", "").lowercase()
        val label = item.optString("lang", item.optString("label", item.optString("language", item.optString("name", item.optString("title", ""))))).trim()
        val code = label.lowercase()
        if (kind.contains("thumbnail") || u.contains("thumbnail") || u.contains("sprite") || u.contains("xywh=") || code.contains("thumbnail") || code.contains("sprite")) {
            return@mapNotNull null
        }
        when {
            code == "ar" || code.startsWith("ar-") || code.startsWith("ara") || code.contains("arabic") || code.contains("العربية") -> "Arabic"
            code == "en" || code.startsWith("en-") || code.startsWith("eng") || code.contains("english") -> "English"
            code == "ja" || code.startsWith("ja-") || code.startsWith("jpn") || code.startsWith("jap") || code.contains("japanese") -> "Japanese"
            code == "hi" || code.startsWith("hi-") || code.startsWith("hin") || code.contains("hindi") -> "Hindi"
            code == "fr" || code.startsWith("fr-") || code.startsWith("fre") || code.startsWith("fra") || code.contains("french") -> "French"
            code == "es" || code.startsWith("es-") || code.startsWith("spa") || code.contains("spanish") -> "Spanish"
            code == "de" || code.startsWith("de-") || code.startsWith("ger") || code.startsWith("deu") || code.contains("german") -> "German"
            code == "it" || code.startsWith("it-") || code.startsWith("ita") || code.contains("italian") -> "Italian"
            code == "pt" || code.startsWith("pt-") || code.startsWith("por") || code.contains("portuguese") -> "Portuguese"
            code == "ru" || code.startsWith("ru-") || code.startsWith("rus") || code.contains("russian") -> "Russian"
            code == "id" || code.startsWith("id-") || code.startsWith("ind") || code.contains("indonesian") -> "Indonesian"
            else -> label.takeIf { it.isNotBlank() }
        }
    }.distinct()
}.getOrDefault(emptyList())

private fun subtitleJsonForLanguage(subtitlesJson: String, selectedLanguage: String): String {
    if (subtitlesJson.isBlank() || selectedLanguage.isBlank() || selectedLanguage == "Any Available") return subtitlesJson
    return runCatching {
        val source = org.json.JSONArray(subtitlesJson)
        val matchingIndex = (0 until source.length()).firstOrNull { index ->
            val one = source.optJSONObject(index) ?: return@firstOrNull false
            val single = org.json.JSONArray().put(one)
            subtitleLanguageOptions(single.toString()).firstOrNull() == selectedLanguage
        } ?: return@runCatching subtitlesJson
        org.json.JSONArray().put(source.getJSONObject(matchingIndex)).toString()
    }.getOrDefault(subtitlesJson)
}

@Composable
fun CurvedHillWaveText(
    text: String = "Loading your watch order...",
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFB0A4C8),
    fontSize: androidx.compose.ui.unit.TextUnit = 13.5.sp,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val infiniteTransition = rememberInfiniteTransition(label = "curvedHillWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        text.forEachIndexed { index, char ->
            val yOffset = (kotlin.math.sin(phase + index * 0.38f) * 3.5f).dp
            Text(
                text = char.toString(),
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = Modifier.offset(y = yOffset)
            )
        }
    }
}

@Composable
fun DownloadQualityPromptDialog(
    state: DownloadQualityPromptState,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedSubtitleLanguage by remember(state.preferredSubtitleLanguage, state.subtitleLanguages) {
        mutableStateOf(state.preferredSubtitleLanguage.takeIf { it in state.subtitleLanguages } ?: state.subtitleLanguages.firstOrNull().orEmpty())
    }
    var currentChosenServer by remember(state.currentServer) {
        mutableStateOf(state.currentServer)
    }
    var dynamicQualities by remember(state.availableQualities) {
        mutableStateOf(state.availableQualities)
    }
    var dynamicSubtitles by remember(state.subtitleLanguages) {
        mutableStateOf(state.subtitleLanguages)
    }
    var isSwitchingServer by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF171717),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF292929)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = EpisodeDownloadIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = if (state.isPreferredMissing) "Quality Unavailable" else "Select Download Quality",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.episodeNum > 0) {
                        Text(
                            text = "Episode ${state.episodeNum}",
                            color = Color(0xFF9E9AA8),
                            fontSize = 12.sp
                        )
                    } else if (state.isSeason) {
                        Text(
                            text = "All Selected Episodes",
                            color = Color(0xFF9E9AA8),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.isPreferredMissing) {
                    Text(
                        text = "Your preferred quality (${state.preferredQuality}) is not available for this episode. Please choose an available quality below to download:",
                        color = Color(0xFFD4C8E2),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                } else {
                    Text(
                        text = "Choose your preferred video quality for this download:",
                        color = Color(0xFFD4C8E2),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (dynamicSubtitles.isNotEmpty()) {
                    Text("Subtitle language", color = Color(0xFFBDBDBD), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        dynamicSubtitles.forEach { language ->
                            Box(
                                Modifier.clip(RoundedCornerShape(9.dp))
                                    .background(if (selectedSubtitleLanguage == language) Color(0xFFE4E4E4) else Color(0xFF292929))
                                    .clickable(enabled = !isSwitchingServer) { selectedSubtitleLanguage = language }
                                    .padding(horizontal = 11.dp, vertical = 8.dp)
                            ) {
                                Text(language, color = if (selectedSubtitleLanguage == language) Color.Black else Color(0xFFCCCCCC), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                if (isSwitchingServer) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.height(8.dp))
                            Text("Loading server streams...", color = Color(0xFFAAAAAA), fontSize = 11.5.sp)
                        }
                    }
                } else {
                    dynamicQualities.forEach { quality ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    state.onQualitySelected(quality, selectedSubtitleLanguage)
                                    onDismiss()
                                },
                            color = Color(0xFF242424)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = quality,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFAAAAAA))
                            }
                        }
                    }
                }

                if (state.availableServers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Server", color = Color(0xFFBDBDBD), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        state.availableServers.forEach { srv ->
                            val isSelected = currentChosenServer.equals(srv.key, ignoreCase = true)
                            Box(
                                Modifier.clip(RoundedCornerShape(9.dp))
                                    .background(if (isSelected) Color(0xFFE4E4E4) else Color(0xFF292929))
                                    .clickable(enabled = !isSwitchingServer) {
                                        if (!isSelected && state.onServerSelected != null) {
                                            currentChosenServer = srv.key
                                            scope.launch {
                                                isSwitchingServer = true
                                                val (newQ, newS) = state.onServerSelected.invoke(srv.key)
                                                dynamicQualities = newQ
                                                dynamicSubtitles = newS
                                                if (selectedSubtitleLanguage !in newS) {
                                                    selectedSubtitleLanguage = newS.firstOrNull().orEmpty()
                                                }
                                                isSwitchingServer = false
                                            }
                                        }
                                    }
                                    .padding(horizontal = 11.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = srv.displayName,
                                    color = if (isSelected) Color.Black else Color(0xFFCCCCCC),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF9E9AA8), fontWeight = FontWeight.SemiBold)
            }
        }
    )
}


// Solar dialog-linear icon for chat comments & review
val SolarDialogLinearIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "SolarDialogLinear",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 13.8214 2.48697 15.5291 3.33782 17L2.5 21.5L7 20.6622C8.47087 21.513 10.1786 22 12 22Z"
        ).toNodes(),
        stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White),
        strokeLineWidth = 2.4f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M8 10.5H16M8 14H13.5"
        ).toNodes(),
        stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White),
        strokeLineWidth = 2.4f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
    ).build()

// Solar rounded star style for rating
val SolarStarRoundedIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "SolarStarRounded",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M9.15316 5.40838C10.4198 3.13613 11.0532 2 12 2C12.9468 2 13.5802 3.13612 14.8468 5.40837L15.3074 6.23439C15.8431 7.19532 16.111 7.67578 16.536 7.97728C16.9609 8.27877 17.5029 8.37256 18.5868 8.56015L19.5184 8.72141C22.0766 9.16431 23.3557 9.38576 23.6483 10.2646C23.9408 11.1434 23.0039 12.1818 21.1302 14.2586L20.4485 15.0142C19.6548 15.894 19.258 16.3339 19.0957 16.8208C18.9335 17.3076 19.0232 17.8863 19.2025 19.0436L19.3567 20.038C19.7797 22.7686 19.9912 24.1339 19.2255 24.6787C18.4598 25.2235 17.2949 24.6062 14.9652 23.3716L14.1354 22.9318C13.1691 22.42 12.686 22.164 12 22.164C11.314 22.164 10.8309 22.42 9.86461 22.9318L9.03478 23.3716C6.70508 24.6062 5.54023 25.2235 4.77454 24.6787C4.00885 24.1339 4.22031 22.7686 4.64325 20.038L4.79745 19.0436C4.97683 17.8863 5.06652 17.3076 4.90426 16.8208C4.742 16.3339 4.34516 15.894 3.5515 15.0142L2.86976 14.2586C0.996078 12.1818 0.0592398 11.1434 0.351745 10.2646C0.64425 9.38576 1.92338 9.16431 4.48161 8.72141L5.41324 8.56015C6.49714 8.37256 7.03909 8.27877 7.46405 7.97728C7.88902 7.67578 8.15689 7.19532 8.69264 6.23439L9.15316 5.40838Z"
        ).toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White)
    ).build()




val SolarClapperboardLinearIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "SolarClapperboardLinear",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M2 12C2 7.28595 2 4.92893 3.46447 3.46447C4.92893 2 7.28595 2 12 2C16.714 2 19.0711 2 20.5355 3.46447C22 4.92893 22 7.28595 22 12C22 16.714 22 19.0711 20.5355 20.5355C19.0711 22 16.714 22 12 22C7.28595 22 4.92893 22 3.46447 20.5355C2 19.0711 2 16.714 2 12Z"
        ).toNodes(),
        stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White),
        strokeLineWidth = 1.6f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M2 9.5H22M6.5 2L5 9.5M12 2L10.5 9.5M17.5 2L16 9.5"
        ).toNodes(),
        stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
    ).build()
}

val SolarCompassLinearIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "SolarCompassLinear",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z"
        ).toNodes(),
        stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White),
        strokeLineWidth = 1.6f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M15.5 8.5L13.5 13.5L8.5 15.5L10.5 10.5L15.5 8.5Z"
        ).toNodes(),
        stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ).build()
}

val SolarChecklistLinearIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "SolarChecklistLinear",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M3 5H11M3 12H11M3 19H11M15 6L17 8L21 4M15 13L17 15L21 11M15 20L17 22L21 18"
        ).toNodes(),
        stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White),
        strokeLineWidth = 1.6f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ).build()
}

val SolarPlayLinearIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "SolarPlayLinear",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z"
        ).toNodes(),
        stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White),
        strokeLineWidth = 1.6f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M10 8.5L16 12L10 15.5V8.5Z"
        ).toNodes(),
        stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ).build()
}

val SolarGamepadLinearIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "SolarGamepadLinear",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M6 6H18C20.2091 6 22 7.79086 22 10V14C22 16.2091 20.2091 18 18 18H6C3.79086 18 2 16.2091 2 14V10C2 7.79086 3.79086 6 6 6Z"
        ).toNodes(),
        stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White),
        strokeLineWidth = 1.6f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M6 12H10M8 10V14M15 11H15.01M18 13H18.01"
        ).toNodes(),
        stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
    ).build()
}

val SolarMicrophone3Icon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "SolarMicrophone3",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M12 2C9.79086 2 8 3.79086 8 6V11C8 13.2091 9.79086 15 12 15C14.2091 15 16 13.2091 16 11V6C16 3.79086 14.2091 2 12 2Z"
        ).toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White)
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M5 10V11C5 14.866 8.13401 18 12 18C15.866 18 19 14.866 19 11V10M12 18V22M9 22H15"
        ).toNodes(),
        stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ).build()
}

class AnimeBoxDetailActivity : ComponentActivity() {

    private var adultPlaybackWarningShown = false

    companion object {
        val detailMemoryCache = android.util.LruCache<Int, AnimeDetail>(50)
        val episodeMetaMemoryCache = android.util.LruCache<Int, Map<Int, EpisodeMeta>>(50)
        val fillerEpisodesMemoryCache = android.util.LruCache<Int, Set<Int>>(50)
        val logoMemoryCache = android.util.LruCache<Int, String>(50)
        val backdropMemoryCache = android.util.LruCache<Int, String>(50)
    }

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
    private suspend fun getResolvedEpisodeCover(anilistId: Int, episodeNum: Int, defaultUrl: String, meta: EpisodeMeta?, animeTitle: String = ""): String = withContext(Dispatchers.IO) {
        val custom = com.lagradost.cloudstream3.ui.animebox.api.EpisodeCoverUploader.getLocalOrUploadedEpisodeCover(this@AnimeBoxDetailActivity, anilistId, episodeNum, animeTitle)
            ?: com.lagradost.cloudstream3.ui.animebox.api.EpisodeCoverUploader.getCustomEpisodeCover(this@AnimeBoxDetailActivity, anilistId, episodeNum, animeTitle)
        if (!custom.isNullOrEmpty()) return@withContext custom

        if (anilistId == 213805) {
            val tmdbImg = AniZipClient.getTmdbEpisodeImage(283428, 1, 14 + episodeNum)
            if (tmdbImg.isNotEmpty()) return@withContext tmdbImg
        }
        if (anilistId == 207329) {
            val tmdbImg = AniZipClient.getTmdbEpisodeImage(313346, 1, episodeNum)
            if (tmdbImg.isNotEmpty()) return@withContext tmdbImg
        }
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
        val hasInitialData = initialTitle.isNotEmpty()

        var detail by remember(anilistId) {
            mutableStateOf(
                detailMemoryCache.get(anilistId) ?: if (hasInitialData) {
                    AnimeDetail(
                        id = anilistId,
                        title = initialTitle,
                        description = initialDesc,
                        coverUrl = initialCover,
                        bannerUrl = initialBanner,
                        // The route's summary count can be stale or estimated. Use only the detail API's count.
                        episodesCount = 0,
                        score = initialScore,
                        genres = initialGenres
                    )
                } else null
            )
        }
        val isAdultAnime = remember(detail) { (detail?.isAdult == true) || intent.getBooleanExtra("isAdult", false) }
        var episodeMetaMap by remember(anilistId) {
            mutableStateOf<Map<Int, EpisodeMeta>>(episodeMetaMemoryCache.get(anilistId) ?: emptyMap())
        }
        var hasLoadedEpisodeMetadata by remember(anilistId) {
            mutableStateOf(episodeMetaMemoryCache.get(anilistId) != null)
        }
        var fillerEpisodes by remember(anilistId) {
            mutableStateOf<Set<Int>>(fillerEpisodesMemoryCache.get(anilistId) ?: emptySet())
        }
        var shinChanSpecials by remember(anilistId) { mutableStateOf<List<com.lagradost.cloudstream3.ui.animebox.api.ShinChanSpecialItem>>(emptyList()) }
        var quickOverviewAnime by remember { mutableStateOf<QuickOverviewDetailAnime?>(null) }
        val cachedBackdrop = backdropMemoryCache.get(anilistId) ?: ""
        val cachedLogo = logoMemoryCache.get(anilistId) ?: ""
        var resolvedBackdropUrl by remember(anilistId) { mutableStateOf(cachedBackdrop) }
        var isBackdropResolving by remember(anilistId) { mutableStateOf(cachedBackdrop.isEmpty()) }
        var animeLogoUrl by remember(anilistId) { mutableStateOf(cachedLogo) }
        var hasResolvedAnimeLogo by remember(anilistId) { mutableStateOf(cachedLogo.isNotEmpty()) }
        val fastDetailLoadingEnabled = remember(anilistId) {
            com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isFastDetailLoadingEnabled(this@AnimeBoxDetailActivity)
        }
        var hasLoadedAnimeDetail by remember(anilistId) { mutableStateOf(detailMemoryCache.get(anilistId) != null) }
        var isInLibrary by remember(anilistId) { mutableStateOf(false) }
        var isLoading by remember(anilistId) { mutableStateOf(detail == null) }
        var isStreamLoading by remember(anilistId) { mutableStateOf(false) }
        var isTrailerPlaying by remember(anilistId) { mutableStateOf(false) }
        var trailerStreamUrl by remember(anilistId) { mutableStateOf<String?>(null) }
        var isCastExpanded by remember(anilistId) { mutableStateOf(true) }
        var showTrailerConfirmDialog by remember(anilistId) { mutableStateOf(false) }
        var isMuted by remember(anilistId) { mutableStateOf(true) }

        val detailLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(detailLifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    val isLowPerf = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isLowPerformanceMode(this@AnimeBoxDetailActivity)
                    if (isLowPerf) {
                        isTrailerPlaying = false
                    } else if (detail != null && detail!!.trailerId.isNotEmpty()) {
                        isTrailerPlaying = true
                    }
                }
            }
            detailLifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                detailLifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        var downloadQualityPrompt by remember(anilistId) { mutableStateOf<DownloadQualityPromptState?>(null) }

        var customViewsCount by remember(anilistId) {
            mutableStateOf(com.lagradost.cloudstream3.ui.animebox.api.AnimeViewCountManager.getExtraViews(anilistId))
        }

        LaunchedEffect(anilistId) {
            // Automatically increment view count when viewing anime page
            com.lagradost.cloudstream3.ui.animebox.api.AnimeViewCountManager.incrementView(anilistId) { updated ->
                customViewsCount = updated
            }
            // Also sync from Supabase database
            val fetched = com.lagradost.cloudstream3.ui.animebox.api.AnimeViewCountManager.fetchCustomViews(anilistId)
            if (fetched > customViewsCount) {
                customViewsCount = fetched
            }
        }
        
        var screenshotRevision by remember { mutableStateOf(0L) }
        LaunchedEffect(Unit) {
            com.lagradost.cloudstream3.ui.animebox.AnimeScreenshotEvents.refreshFlow.collect {
                screenshotRevision = System.currentTimeMillis()
            }
        }
        
        val prefs = remember { getSharedPreferences("AnimeBoxPlayer", android.content.Context.MODE_PRIVATE) }
        fun getPreferredStreamType(): String {
            return if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(this@AnimeBoxDetailActivity)) {
                val lastAud = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getLastUsedAudio(this@AnimeBoxDetailActivity)
                val lastSub = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getLastUsedSubMode(this@AnimeBoxDetailActivity)
                when {
                    lastAud == "English" -> "dub"
                    lastAud == "Hindi" -> "hindi"
                    lastAud == "French (VF)" -> "french"
                    lastAud == "Italian (ITA)" -> "italian"
                    lastAud == "Spanish (Latino)" -> "spanish_latino"
                    lastAud == "Spanish (Castellano)" -> "spanish_castellano"
                    lastAud == "German (Deutsch)" -> "german"
                    lastSub == "Hard Sub" -> "hardsub"
                    else -> "sub"
                }
            } else {
                val sAud = prefs.getString("selectedAudio", "Japanese (Original)")
                val sSub = prefs.getString("selectedSub", "English (VTT)")
                when (sAud) {
                    "English" -> "dub"
                    "Hindi" -> "hindi"
                    "French (VF)" -> "french"
                    "Italian (ITA)" -> "italian"
                    "Spanish (Latino)" -> "spanish_latino"
                    "Spanish (Castellano)" -> "spanish_castellano"
                    "German (Deutsch)" -> "german"
                    else -> if (sSub == "Hard Sub") "hardsub" else "sub"
                }
            }
        }

        var episodeSearchQuery by remember(anilistId) { mutableStateOf("") }
        var selectedSectionTab by remember(anilistId) { mutableStateOf(0) }
        val defaultEpMode = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getDefaultEpisodeViewMode(this@AnimeBoxDetailActivity) }
        var episodeViewMode by remember(anilistId) { mutableStateOf(defaultEpMode) }
        // Keep first layout and image work bounded; users can load the next page on demand.
        var visibleEpisodesCount by remember(anilistId) { mutableStateOf(24) }
        var episodeSortOrder by remember(anilistId) { mutableStateOf("asc") }
        var isDescriptionExpanded by remember(anilistId) { mutableStateOf(false) }
        var showAddToListDialog by remember(anilistId) { mutableStateOf(false) }
        var showWatchOrderDialog by remember(anilistId) { mutableStateOf(false) }
        var showAnimeReviewsDialog by remember(anilistId) { mutableStateOf(false) }
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
            val initialCachedBackdrop = backdropMemoryCache.get(anilistId) ?: ""
            val initialCachedLogo = logoMemoryCache.get(anilistId) ?: ""
            resolvedBackdropUrl = initialCachedBackdrop
            isBackdropResolving = initialCachedBackdrop.isEmpty()
            animeLogoUrl = initialCachedLogo
            isTrailerPlaying = false
            trailerStreamUrl = null
            if (detail == null) {
                isLoading = true
            }

            isInLibrary = libraryManager.isInLibrary(anilistId)

            val startEp = historyManager.getWatchHistory().find { it.anilistId == anilistId }?.episodeNumber ?: 1
            // Resolve the logo alongside the detail request. AniZip can derive its own title when
            // the route did not pass one, so waiting for the larger detail response is unnecessary.
            if (initialCachedLogo.isEmpty()) {
                launch(Dispatchers.IO) {
                    val logo = runCatching {
                        val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId)
                        if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_LOGO_URL
                        else AniZipClient.getAnimeLogoUrl(
                            anilistId,
                            isMovie = intent.getIntExtra("episodes", 0) == 1 || com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.isStaticMovie(anilistId)
                        )
                    }.getOrDefault("")
                    val cleanLogo = if (logo.contains("stjerner", ignoreCase = true) || logo.contains("null", ignoreCase = true)) "" else logo
                    if (cleanLogo.isNotEmpty()) logoMemoryCache.put(anilistId, cleanLogo)
                    withContext(Dispatchers.Main) {
                        animeLogoUrl = cleanLogo
                        hasResolvedAnimeLogo = true
                    }
                }
            }
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
                val tmdbId = if (anilistId == 918) null else AniZipClient.getLongRunningTmdbId(anilistId)
                val metaDeferred = async {
                    if (tmdbId != null) AniZipClient.getTmdbAllEpisodes(tmdbId)
                    else AniZipClient.getEpisodeMetadata(anilistId)
                }

                val detailsResponse = detailsDeferred.await()
                withContext(Dispatchers.Main) { hasLoadedAnimeDetail = true }
                var parsed = if (detailsResponse != null) parseDetails(detailsResponse) else null
                val isShinChanDetail = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId)
                if (!isShinChanDetail && (parsed == null || com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.isKitsuModeActive)) {
                    val kitsuDetail = com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.getAnimeDetail(anilistId, initialTitle)
                    if (kitsuDetail != null) {
                        parsed = kitsuDetail
                    }
                }

                val adultAllowed = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isAdultContentEnabled(this@AnimeBoxDetailActivity)
                if (parsed != null && parsed.isAdult && !adultAllowed) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AnimeBoxDetailActivity, "Enable 18+ Adult Content in My Space to view this.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }

                val isAdultAnimeEarly = (parsed?.isAdult == true) || intent.getBooleanExtra("isAdult", false)
                val isFiveCmEarly = anilistId == 1689
                val isMovie = isFiveCmEarly || (!isAdultAnimeEarly && ((parsed?.format?.uppercase() == "MOVIE") || (parsed?.episodesCount == 1) || (intent.getIntExtra("episodes", 0) == 1) || com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.isStaticMovie(anilistId)))

                val effectiveCover = if (isShinChanDetail) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_POSTER_URL else when {
                    parsed != null && parsed.coverUrl.isNotBlank() -> parsed.coverUrl
                    initialCover.isNotBlank() -> initialCover
                    else -> ""
                }

                // Immediately update detail and unblock loading on Main thread
                withContext(Dispatchers.Main) {
                    if (parsed != null) {
                        detail = parsed.copy(coverUrl = effectiveCover)
                        detailMemoryCache.put(anilistId, detail!!)
                        isInLibrary = libraryManager.isInLibrary(parsed.id)
                    }
                    isLoading = false

                    val isLowPerf = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isLowPerformanceMode(this@AnimeBoxDetailActivity)
                    val shouldAutoplayTrailer = !isLowPerf
                    if (shouldAutoplayTrailer && parsed != null && parsed.trailerId.isNotEmpty()) {
                        isTrailerPlaying = true
                    }
                }

                // 1. Asynchronously fetch TMDB overview if description is missing or short
                launch(Dispatchers.IO) {
                    val currentParsed = parsed
                    val isDoraemonOrShinChan = (initialTitle.contains("Shin Chan", ignoreCase = true) || initialTitle.contains("Shinchan", ignoreCase = true) || initialTitle.contains("Doraemon", ignoreCase = true) || (currentParsed?.title?.contains("Shin", ignoreCase = true) == true) || (currentParsed?.title?.contains("Doraemon", ignoreCase = true) == true))
                    if (currentParsed == null || currentParsed.description.isBlank() || currentParsed.description.length < 25 || isDoraemonOrShinChan) {
                        val mTmdbId = com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.getMovieTmdbId(anilistId)
                            ?: com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.getTmdbMapping(anilistId)
                            ?: AniZipClient.getLongRunningTmdbId(anilistId)
                    }
                }

                // 2. Asynchronously resolve fallback trailer
                launch(Dispatchers.IO) {
                    val currentParsed = parsed
                    if (currentParsed != null && currentParsed.trailerId.isEmpty()) {
                        val fallbackTrailer = resolveFallbackTrailer(anilistId, currentParsed.idMal, currentParsed.title)
                        if (fallbackTrailer.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                detail = detail?.copy(trailerId = fallbackTrailer)
                                detail?.let { detailMemoryCache.put(anilistId, it) }
                            }
                        }
                    }
                }

                // 3. Asynchronously resolve best backdrop
                launch(Dispatchers.IO) {
                    if (backdropMemoryCache.get(anilistId).isNullOrEmpty()) {
                        val bestBackdrop = if (isShinChanDetail) {
                            com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_BACKDROP_URL
                        } else {
                            AniZipClient.getBestBackdropUrl(anilistId, parsed?.bannerUrl ?: initialBanner, isMovie = isMovie)
                                .ifEmpty { detail?.bannerUrl?.ifEmpty { "" } ?: initialBanner.ifEmpty { "" } }
                        }
                        if (bestBackdrop.isNotEmpty()) backdropMemoryCache.put(anilistId, bestBackdrop)
                        withContext(Dispatchers.Main) {
                            resolvedBackdropUrl = bestBackdrop
                            isBackdropResolving = false
                        }
                    }
                }

                // 5. Asynchronously load episode metadata & ShinChan specials
                launch(Dispatchers.IO) {
                    if (isShinChanDetail) {
                        com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.loadBundledAssetIfNeeded(this@AnimeBoxDetailActivity)
                        com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.loadShinChanEpisodes(this@AnimeBoxDetailActivity)
                        com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.triggerBackgroundSync(this@AnimeBoxDetailActivity)
                        val specialsList = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.getShinChanSpecials()
                        val scCovers = com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getAllEpisodeCovers(this@AnimeBoxDetailActivity)
                        val maxShinChan = maxOf(com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getLatestEpisodeNumber(), 1349)
                        val scMap = mutableMapOf<Int, EpisodeMeta>()
                        for (ep in 1..maxShinChan) {
                            val cover = scCovers[ep] ?: ""
                            scMap[ep] = EpisodeMeta(
                                title = "Episode $ep",
                                imageUrl = cover,
                                seasonNumber = 1,
                                episodeNumber = ep,
                                runtime = 24
                            )
                        }
                        val latestShinChanCount = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.getLatestEpisodeNumber()
                        withContext(Dispatchers.Main) {
                            shinChanSpecials = specialsList
                            episodeMetaMap = scMap
                            hasLoadedEpisodeMetadata = true
                            episodeMetaMemoryCache.put(anilistId, scMap)
                            if (latestShinChanCount > 0) {
                                detail = detail?.copy(episodesCount = latestShinChanCount)
                                detail?.let { detailMemoryCache.put(anilistId, it) }
                            }
                        }
                    } else if (parsed?.isAdult == true || intent.getBooleanExtra("isAdult", false) || com.lagradost.cloudstream3.ui.animebox.api.AniListClient.isAdultAnime(anilistId, parsed?.title ?: initialTitle)) {
                        val targetTitle = parsed?.title ?: initialTitle
                        val altTitles = buildList {
                            add(targetTitle)
                            parsed?.title?.let { add(it) }
                            if (initialTitle.isNotBlank()) add(initialTitle)
                            parsed?.nativeTitle?.takeIf { it.isNotBlank() }?.let { add(it) }
                            parsed?.romajiTitle?.takeIf { it.isNotBlank() }?.let { add(it) }
                            parsed?.synonyms?.forEach { if (it.isNotBlank()) add(it) }
                            add(targetTitle.replace(Regex("""(\d+)([a-zA-Z]+)"""), "$1 $2"))
                            parsed?.romajiTitle?.takeIf { it.isNotBlank() }?.let { add(it.replace(Regex("""(\d+)([a-zA-Z]+)"""), "$1 $2")) }
                            add(targetTitle.replace("migi", "migui", ignoreCase = true))
                            add(targetTitle.replace("migui", "migi", ignoreCase = true))
                            add(targetTitle.replace("-", " "))
                        }.distinct()
                        val isHiiroNiKoku = anilistId == 4731 || targetTitle.contains("Hiiro no Koku", ignoreCase = true) || targetTitle.contains("Hiiro ni Koku", ignoreCase = true) || targetTitle.contains("The Time Colored Scarlet", ignoreCase = true)
                        val hmEpisodes = if (isHiiroNiKoku) emptyList() else try {
                            HentaiMamaExtractor.getEpisodeListForAnime(
                                targetTitle,
                                altTitles,
                                releaseYear = parsed?.releaseYear ?: 0,
                                totalEpisodes = parsed?.episodesCount ?: 0,
                                anilistId = anilistId
                            )
                        } catch (_: Exception) { emptyList() }
                        val hhEpisodes = if (hmEpisodes.isEmpty() || hmEpisodes.any { it.thumbnail.isEmpty() }) {
                            try {
                                HentaiHavenExtractor.getEpisodeListForAnime(targetTitle, altTitles)
                            } catch (_: Exception) { emptyList() }
                        } else emptyList()

                        // Some adult episode sources return titles and covers but omit their numeric field.
                        // Keep those rows by deriving the number from the title, then from source order.
                        val adultSourceEpisodes = buildList<Triple<Int, String, String>> {
                            hmEpisodes.forEachIndexed { index, episode ->
                                val titleNumber = Regex("(?i)(?:episode|ep|#)\\s*(\\d+)")
                                    .find(episode.title)?.groupValues?.getOrNull(1)?.toIntOrNull()
                                val number = episode.episodeNumber.takeIf { it > 0 } ?: titleNumber?.takeIf { it > 0 } ?: index + 1
                                add(Triple(number, episode.title, episode.thumbnail))
                            }
                            hhEpisodes.forEachIndexed { index, episode ->
                                val titleNumber = Regex("(?i)(?:episode|ep|#)\\s*(\\d+)")
                                    .find(episode.title)?.groupValues?.getOrNull(1)?.toIntOrNull()
                                val number = episode.episodeNumber.takeIf { it > 0 } ?: titleNumber?.takeIf { it > 0 } ?: index + 1
                                add(Triple(number, episode.title, episode.thumbnail))
                            }
                        }.distinctBy { it.first }.sortedBy { it.first }
                        val adultMap = mutableMapOf<Int, EpisodeMeta>()
                        val manualMap = AniZipClient.getEpisodeMetadata(anilistId)
                        if (manualMap.isNotEmpty()) {
                            adultMap.putAll(manualMap)
                        } else {
                            for ((ep, sourceTitle, sourceThumbnail) in adultSourceEpisodes) {
                                val epTitle = sourceTitle.ifBlank { "Episode $ep" }
                                var thumb = sourceThumbnail
                                if (thumb.isBlank()) thumb = AniZipClient.getTmdbAdultEpisodeImage(targetTitle, ep, altTitles = altTitles)
                                adultMap[ep] = EpisodeMeta(
                                    title = epTitle,
                                    imageUrl = thumb,
                                    seasonNumber = 1,
                                    episodeNumber = ep,
                                    runtime = 0
                                )
                            }
                            if (adultMap.isEmpty()) {
                                val knownCount = (parsed?.episodesCount ?: 0).takeIf { it > 0 }
                                    ?: intent.getIntExtra("initialEpisodesCount", 0)
                                if (knownCount > 0) {
                                    for (ep in 1..knownCount.coerceAtMost(100)) {
                                        adultMap[ep] = EpisodeMeta(
                                            title = "Episode $ep",
                                            imageUrl = "",
                                            seasonNumber = 1,
                                            episodeNumber = ep,
                                            runtime = 0
                                        )
                                    }
                                }
                            }
                        }
                        for ((ep, epMeta) in adultMap.toMap()) {
                            if (epMeta.imageUrl.isBlank()) {
                                val tmdbImg = AniZipClient.getTmdbAdultEpisodeImage(targetTitle, ep, altTitles = altTitles)
                                if (tmdbImg.isNotBlank()) {
                                    adultMap[ep] = epMeta.copy(imageUrl = tmdbImg)
                                }
                            }
                        }
                        if (parsed?.description.isNullOrBlank() || (parsed?.description?.length ?: 0) < 15) {
                            val desc = try {
                                HentaiMamaExtractor.getDescription(targetTitle, altTitles).ifEmpty {
                                    HentaiHavenExtractor.getDescription(targetTitle, altTitles)
                                }
                            } catch (_: Exception) { "" }
                            if (desc.isNotBlank()) {
                                parsed = parsed?.copy(description = desc)
                            }
                        }
                        val actualAvailableCount = adultMap.size
                        withContext(Dispatchers.Main) {
                            episodeMetaMap = adultMap
                            hasLoadedEpisodeMetadata = true
                            episodeMetaMemoryCache.put(anilistId, adultMap)
                            detail = detail?.copy(episodesCount = actualAvailableCount)
                            detail?.let { detailMemoryCache.put(anilistId, it) }
                        }
                    } else {
                        val meta = runCatching { metaDeferred.await() }.getOrDefault(emptyMap())
                        withContext(Dispatchers.Main) {
                            episodeMetaMap = meta
                            hasLoadedEpisodeMetadata = true
                            episodeMetaMemoryCache.put(anilistId, meta)
                        }
                    }
                }

                // 6. Asynchronously load filler episodes
                launch(Dispatchers.IO) {
                    val fillerSet = try {
                        com.lagradost.cloudstream3.ui.animebox.api.AnimeFillerManager.getFillerEpisodes(
                            this@AnimeBoxDetailActivity,
                            anilistId,
                            parsed?.idMal ?: 0,
                            parsed?.title ?: initialTitle
                        )
                    } catch (_: Exception) {
                        emptySet()
                    }
                    withContext(Dispatchers.Main) {
                        fillerEpisodes = fillerSet
                        fillerEpisodesMemoryCache.put(anilistId, fillerSet)
                    }
                }
            }
        }

        val autoPlayEpisode = remember { intent.getIntExtra("autoPlayEpisode", 0) }
        var hasTriggeredAutoPlay by remember(anilistId) { mutableStateOf(false) }
        LaunchedEffect(detail?.id, autoPlayEpisode) {
            val d = detail
            if (d != null && autoPlayEpisode > 0 && !hasTriggeredAutoPlay) {
                hasTriggeredAutoPlay = true
                isStreamLoading = true
                val epMeta = episodeMetaMap[autoPlayEpisode]
                val resolvedEpCover = getResolvedEpisodeCover(d.id, autoPlayEpisode, d.coverUrl, epMeta, d.title)
                fetchAndPlayStream(
                    d.id,
                    autoPlayEpisode,
                    d.title,
                    resolvedEpCover,
                    getPreferredStreamType(),
                    d.coverUrl,
                    d.episodesCount,
                    detailObj = d,
                    backdropUrl = resolvedBackdropUrl,
                    logoUrl = animeLogoUrl,
                    onAddedToLibrary = { isInLibrary = true }
                )
                isStreamLoading = false
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
            if (isLoading || (!fastDetailLoadingEnabled && (!hasLoadedAnimeDetail || !hasResolvedAnimeLogo || !hasLoadedEpisodeMetadata))) {
                AnimeBoxDetailSkeletonLoading(modifier = Modifier.padding(paddingValues))
            } else {
                detail?.let { d ->
                    val watchHistory = remember { historyManager.getWatchHistory() }
                    val lastWatched = watchHistory.find { it.anilistId == anilistId }
                    
                    val isFiveCm = anilistId == 1689
                    val isKnownTv = !isFiveCm && (d.format.equals("TV", ignoreCase = true) || d.format.equals("TV_SHORT", ignoreCase = true) || d.episodesCount > 1)
                    val isMovie = isFiveCm || (!isAdultAnime && !isKnownTv && ((d.format.uppercase() == "MOVIE") || (d.episodesCount == 1) || (intent.getIntExtra("episodes", 0) == 1) || com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.isStaticMovie(anilistId)))
                    val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId)
                    val isLongRunning = (AniZipClient.getLongRunningTmdbId(anilistId) != null && !isMovie) ||
                        d.title.contains("One Piece", ignoreCase = true) ||
                        d.title.contains("Detective Conan", ignoreCase = true) ||
                        d.title.contains("Case Closed", ignoreCase = true) ||
                        d.title.contains("Doraemon", ignoreCase = true) ||
                        d.title.contains("Shinchan", ignoreCase = true) ||
                        d.title.contains("Shin-chan", ignoreCase = true)
                    val rawEpisodesList = if (isMovie) {
                        listOf(1)
                    } else if (isAdultAnime) {
                        val count = if (episodeMetaMap.isNotEmpty()) (episodeMetaMap.keys.maxOrNull() ?: 0) else maxOf(
                            d.episodesCount,
                            intent.getIntExtra("initialEpisodesCount", 0),
                            episodeMetaMap.keys.maxOrNull() ?: 0
                        )
                        if (count > 0) {
                            ((1..count).toSet() + episodeMetaMap.keys).sorted()
                        } else if (episodeMetaMap.isNotEmpty()) {
                            episodeMetaMap.keys.sorted()
                        } else {
                            emptyList()
                        }
                    } else if (isShinChan) {
                        val maxShinChan = maxOf(com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getLatestEpisodeNumber(), d.episodesCount, 1349)
                        (1..maxShinChan).toList()
                    } else {
                        val count = if (d.episodesCount > 0) {
                            d.episodesCount
                        } else if (d.nextAiring != null) {
                            maxOf(1, d.nextAiring.episode - 1)
                        } else {
                            intent.getIntExtra("initialEpisodesCount", 0).takeIf { it > 0 } ?: (episodeMetaMap.keys.maxOrNull() ?: 0)
                        }
                        val isReleasing = d.status.contains("RELEASING", ignoreCase = true) || d.nextAiring != null
                        val combinedEpisodeNumbers = if (count > 0) {
                            ((1..count).toSet() + episodeMetaMap.keys).sorted()
                        } else if (episodeMetaMap.isNotEmpty()) {
                            episodeMetaMap.keys.sorted()
                        } else {
                            emptyList()
                        }
                        combinedEpisodeNumbers.filter { epNum ->
                            if (!isLongRunning && !isReleasing && d.episodesCount > 0 && epNum > d.episodesCount) {
                                false
                            } else {
                                val meta = episodeMetaMap[epNum]
                                if (meta != null && meta.airdate.isNotEmpty()) {
                                    !isFutureDate(meta.airdate)
                                } else {
                                    true
                                }
                            }
                        }
                    }

                    val upcomingAirEpisode = d.nextAiring?.let { na ->
                        val nowSec = System.currentTimeMillis() / 1000
                        if (na.airingAt > nowSec) na.episode else null
                    }

                    val episodesList = if (isMovie) {
                        listOf(1)
                    } else {
                        val baseList = if (rawEpisodesList.isEmpty()) {
                            if (d.episodesCount > 0) (1..d.episodesCount).toList() else emptyList()
                        } else {
                            rawEpisodesList
                        }
                        if (upcomingAirEpisode != null) {
                            baseList.filter { it < upcomingAirEpisode }
                        } else {
                            baseList
                        }
                    }

                    val seasonLabel = remember(d.title, episodeMetaMap, d.format, isLongRunning, isAdultAnime) {
                        val pokemonGen = com.lagradost.cloudstream3.ui.animebox.extractors.PokemonHindiExtractor.getPokemonGenerationLabel(d.id, d.title)
                        if (d.id == 918) {
                            "Season 1"
                        } else if (pokemonGen != null) {
                            pokemonGen
                        } else if (isAdultAnime) {
                            null
                        } else if (!isAdultAnime && (isMovie || d.format.equals("MOVIE", ignoreCase = true) || d.title.contains("Movie", ignoreCase = true))) {
                            null
                        } else if (isLongRunning) {
                            "Season 1"
                        } else {
                            val titleClean = d.title

                            // 1. Detect Part or Cour (e.g. "Part 2", "Part 1", "Cour 2")
                            val partRegex = Regex("""(?i)\b(Part\s*\d+|Cour\s*\d+)\b""")
                            val partMatch = partRegex.find(titleClean)?.value?.trim()?.split(" ")?.joinToString(" ") { word ->
                                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                            }

                            // 2. Detect Season number or Final Season
                            val seasonRegex = Regex("""(?i)\b(?:Season\s*(\d+)|(\d+)(?:st|nd|rd|th)\s+Season)\b""")
                            val sRegex = Regex("""(?i)\bS(\d+)\b""")
                            val finalSeasonRegex = Regex("""(?i)\bFinal\s+Season\b""")

                            val seasonNum = seasonRegex.find(titleClean)?.let { m ->
                                m.groupValues.getOrNull(1)?.ifEmpty { null } ?: m.groupValues.getOrNull(2)
                            } ?: sRegex.find(titleClean)?.groupValues?.getOrNull(1)

                            val baseSeason = when {
                                finalSeasonRegex.containsMatchIn(titleClean) -> "Final Season"
                                !seasonNum.isNullOrEmpty() -> "Season $seasonNum"
                                else -> {
                                    val metaSeason = episodeMetaMap.values.map { it.seasonNumber }.firstOrNull { it > 1 }
                                    if (metaSeason != null && metaSeason > 1) "Season $metaSeason" else null
                                }
                            }

                            // 3. Combine: If both Season and Part exist, represent both (e.g. "Season 3 Part 2")
                            when {
                                baseSeason != null && partMatch != null -> "$baseSeason $partMatch"
                                baseSeason != null -> baseSeason
                                partMatch != null -> partMatch
                                else -> "Season 1"
                            }
                        }
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
                                    .height(330.dp)
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

                                    // 1. Ambient Backdrop Background (Keeps the backdrop full size behind trailer)
                                    val trailerBackdropUrl = resolvedBackdropUrl.ifEmpty { d.bannerUrl.ifEmpty { initialBanner.ifEmpty { d.coverUrl.ifEmpty { initialCover } } } }
                                    if (trailerBackdropUrl.isNotEmpty()) {
                                        Image(
                                            painter = rememberAsyncImagePainter(model = trailerBackdropUrl),
                                            contentDescription = d.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .blur(16.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.60f))
                                        )
                                    }

                                    // 2. Strict 16:9 Trailer Player in Center (NOT zoomed or stretched like backdrop)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .align(Alignment.Center)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black)
                                    ) {
                                        if (trailerStreamUrl != null) {
                                            val exoPlayer = remember(trailerStreamUrl) {
                                                ExoPlayer.Builder(context).build().apply {
                                                    val mediaItem = MediaItem.fromUri(trailerStreamUrl!!)
                                                    setMediaItem(mediaItem)
                                                    repeatMode = Player.REPEAT_MODE_ONE
                                                    volume = if (isMuted) 0f else 1f
                                                    videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
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
                                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                                        keepScreenOn = true
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
                                                com.lagradost.cloudstream3.ui.animebox.components.ThemeDotCircularProgressIndicator(
                                                    color = primaryColor,
                                                    size = 40.dp
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
                                                            .video-container { position: relative; width: 100%; height: 100%; }
                                                            iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: none; }
                                                            </style>
                                                            </head>
                                                            <body>
                                                            <div class="video-container">
                                                            <iframe src="https://www.youtube.com/embed/${d.trailerId}?autoplay=1&playsinline=1&controls=1&fs=0&rel=0&enablejsapi=1&start=5" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>
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
                                    }

                                    // Top atmospheric gradient shadow for status bar and header when trailer is playing
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(95.dp)
                                            .align(Alignment.TopCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Black.copy(alpha = 0.85f),
                                                        Color.Black.copy(alpha = 0.50f),
                                                        Color.Black.copy(alpha = 0.18f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    )

                                    // Multi-layered atmospheric soft shadow fading seamlessly into #16181D
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(155.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color(0x1816181D),
                                                        Color(0x6616181D),
                                                        Color(0xBB16181D),
                                                        Color(0xFF16181D)
                                                    )
                                                )
                                            )
                                    )

                                    // Uneven organic vignette shadow layer (eliminates any straight horizontal boundary)
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(135.dp)
                                            .align(Alignment.BottomCenter)
                                    ) {
                                        val w = size.width
                                        val h = size.height
                                        val organicPath = Path().apply {
                                            moveTo(0f, h * 0.35f)
                                            quadraticBezierTo(w * 0.42f, h * 0.08f, w * 0.72f, h * 0.38f)
                                            quadraticBezierTo(w * 0.88f, h * 0.52f, w, h * 0.28f)
                                            lineTo(w, h)
                                            lineTo(0f, h)
                                            close()
                                        }
                                        drawPath(
                                            path = organicPath,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color(0x8816181D), Color(0xFF16181D)),
                                                startY = 0f,
                                                endY = h
                                            )
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = 16.dp, bottom = 48.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.75f))
                                                .clickable {
                                                    isTrailerPlaying = false
                                                    trailerStreamUrl = null
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                                contentDescription = "Stop Trailer",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
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
                                                    .then(if (isUsingCoverBlur || isAdultAnime) Modifier.blur(24.dp) else Modifier)
                                            )
                                            if (isUsingCoverBlur || isAdultAnime) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.55f))
                                                )
                                            }
                                        }
                                    }

                                    // (adult warning is drawn further below, on top of the backdrop gradients)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(165.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color(0x1816181D),
                                                        Color(0x6616181D),
                                                        Color(0xBB16181D),
                                                        Color(0xFF16181D)
                                                    )
                                                )
                                            )
                                    )

                                    // Uneven organic vignette shadow overlay (eliminates any straight horizontal boundary)
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .align(Alignment.BottomCenter)
                                    ) {
                                        val w = size.width
                                        val h = size.height
                                        val organicPath = Path().apply {
                                            moveTo(0f, h * 0.35f)
                                            quadraticBezierTo(w * 0.42f, h * 0.08f, w * 0.72f, h * 0.38f)
                                            quadraticBezierTo(w * 0.88f, h * 0.52f, w, h * 0.28f)
                                            lineTo(w, h)
                                            lineTo(0f, h)
                                            close()
                                        }
                                        drawPath(
                                            path = organicPath,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color(0x9916181D), Color(0xFF16181D)),
                                                startY = 0f,
                                                endY = h
                                            )
                                        )
                                    }

                                    // 18+ warning: nudged down so it never collides with the top Watch Order / Schedule
                                    // chibi banner, and drawn after the gradient/vignette so no effect covers or cuts it.
                                    // The banners are overlaid at the screen root, so they stay above this warning.
                                    if (isAdultAnime) {
                                        HentaiSpeechBubbleWarning(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .offset(y = 22.dp)
                                                .padding(horizontal = 18.dp)
                                                .fillMaxWidth()
                                                .zIndex(5f)
                                        )
                                    }

                                    // Circular trailer play button near backdrop (as in screenshot)
                                    val trailerCtx = LocalContext.current
                                    val trailerScope = rememberCoroutineScope()
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = 22.dp, bottom = 28.dp)
                                            .size(44.dp)
                                            .shadow(elevation = 8.dp, shape = CircleShape)
                                            .clip(CircleShape)
                                            .background(primaryColor.copy(alpha = 0.55f))
                                            .clickable {
                                                if (isTrailerPlaying) {
                                                    isTrailerPlaying = false
                                                    trailerStreamUrl = null
                                                } else {
                                                    val targetTrailer = d.trailerId
                                                    val isLowPerf = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isLowPerformanceMode(this@AnimeBoxDetailActivity)
                                                    if (targetTrailer.isNotEmpty()) {
                                                        if (isLowPerf) {
                                                            showTrailerConfirmDialog = true
                                                        } else {
                                                            isTrailerPlaying = true
                                                        }
                                                    } else {
                                                        trailerScope.launch {
                                                            Toast.makeText(trailerCtx, "Finding trailer...", Toast.LENGTH_SHORT).show()
                                                            val resolved = resolveFallbackTrailer(anilistId, d.idMal, d.title)
                                                            if (resolved.isNotEmpty()) {
                                                                detail = d.copy(trailerId = resolved)
                                                                if (isLowPerf) {
                                                                    showTrailerConfirmDialog = true
                                                                } else {
                                                                    isTrailerPlaying = true
                                                                }
                                                            } else {
                                                                Toast.makeText(trailerCtx, "Trailer not available for this anime", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = SolarPlayVector,
                                            contentDescription = "Watch Trailer",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val overlapPx = 48.dp.roundToPx()
                                        layout(placeable.width, (placeable.height - overlapPx).coerceAtLeast(0)) {
                                            placeable.placeRelative(0, -overlapPx)
                                        }
                                    },
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(106.dp)
                                        .height(156.dp)
                                        .shadow(elevation = 14.dp, shape = RoundedCornerShape(6.dp), spotColor = Color.Black)
                                        .clip(RoundedCornerShape(6.dp))
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
                                    val displayTitle = d.title.ifEmpty { initialTitle }
                                    var logoLoadFailed by remember(animeLogoUrl) { mutableStateOf(false) }
                                    val isValidLogo = animeLogoUrl.isNotBlank() &&
                                        !animeLogoUrl.contains("stjerner", ignoreCase = true) &&
                                        !animeLogoUrl.contains("null", ignoreCase = true) &&
                                        !logoLoadFailed

                                    if (isValidLogo) {
                                        Image(
                                            painter = rememberAsyncImagePainter(
                                                model = animeLogoUrl,
                                                onError = { logoLoadFailed = true }
                                            ),
                                            contentDescription = displayTitle,
                                            contentScale = ContentScale.Fit,
                                            alignment = Alignment.BottomStart,
                                            modifier = Modifier
                                                .heightIn(min = 45.dp, max = 75.dp)
                                                .fillMaxWidth()
                                        )
                                    } else {
                                        val displayLength = displayTitle.length
                                        Text(
                                            text = displayTitle.uppercase(),
                                            fontSize = if (displayLength > 32) 18.sp else if (displayLength > 18) 22.sp else 26.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                            color = Color.White,
                                            lineHeight = if (displayLength > 32) 22.sp else if (displayLength > 18) 26.sp else 30.sp,
                                            letterSpacing = 3.5.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    if (seasonLabel != null) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = seasonLabel,
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))

                                    val genresText = if (d.genres.isNotEmpty()) {
                                        d.genres.take(3).joinToString(" | ")
                                    } else {
                                        val formatStr = when (d.format.uppercase()) {
                                            "MOVIE" -> "Movie"
                                            "OVA" -> "OVA"
                                            "ONA" -> "ONA"
                                            "SPECIAL" -> "Special"
                                            else -> if (isAdultAnime) "TV Series" else if (d.episodesCount == 1) "Movie" else "TV Series"
                                        }
                                        "$formatStr | Animation"
                                    }
                                    Text(
                                        text = genresText,
                                        color = Color(0xFFBDC7D5),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val ratingOutOf5 = (d.score / 20f).coerceIn(0f, 5f)

                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            for (i in 1..5) {
                                                val starThreshold = i.toFloat()
                                                when {
                                                    ratingOutOf5 >= starThreshold -> {
                                                        Icon(
                                                            imageVector = SolarStarRoundedIcon,
                                                            contentDescription = null,
                                                            tint = primaryColor,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                    ratingOutOf5 >= starThreshold - 0.75f -> {
                                                        Box(modifier = Modifier.size(15.dp)) {
                                                            Icon(
                                                                imageVector = SolarStarRoundedIcon,
                                                                contentDescription = null,
                                                                tint = Color(0xFF484A54),
                                                                modifier = Modifier.matchParentSize()
                                                            )
                                                            Icon(
                                                                imageVector = StarHalfIcon,
                                                                contentDescription = null,
                                                                tint = primaryColor,
                                                                modifier = Modifier.matchParentSize()
                                                            )
                                                        }
                                                    }
                                                    else -> {
                                                        Icon(
                                                            imageVector = SolarStarRoundedIcon,
                                                            contentDescription = null,
                                                            tint = Color(0xFF484A54),
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        val formulaViews = (d.popularity / 100) + (d.favourites / 10)
                                        val baseViews = if (formulaViews > 0) formulaViews else if (d.score > 0) (d.score * 35 + (d.id % 500) + 1200) else 2873
                                        val viewsCount = baseViews + customViewsCount
                                        val formattedViews = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(viewsCount)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = ViewsEyeIcon,
                                                contentDescription = null,
                                                tint = Color(0xFF9E9EA8),
                                                modifier = Modifier.size(13.5.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "$formattedViews views",
                                                color = Color(0xFF9E9EA8),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        val yearStr = if (d.releaseYear > 0) "${d.releaseYear}" else ""
                                        if (yearStr.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = "•", color = Color(0xFF6B7280), fontSize = 11.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = yearStr, color = Color(0xFF9E9EA8), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
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
                            val buttonText = if (isMovie) (if (lastWatched != null) "Resume Movie" else "Watch Movie") else (if (lastWatched != null) "Resume Episode $playEpNum" else "Watch Now")
                            
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
                                            val resolvedEpCover = getResolvedEpisodeCover(d.id, playEpNum, d.coverUrl, targetEpMeta, d.title)
                                            fetchAndPlayStream(d.id, playEpNum, d.title, resolvedEpCover, getPreferredStreamType(), d.coverUrl, d.episodesCount, detailObj = d, backdropUrl = resolvedBackdropUrl, logoUrl = animeLogoUrl, onAddedToLibrary = { isInLibrary = true })
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
                                        imageVector = SolarPlayVector,
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
                        Spacer(modifier = Modifier.height(10.dp))

                        if (d.characters.isNotEmpty() && !isAdultAnime && anilistId != 158198 && anilistId != 83307 && !d.title.contains("Ninja Hattori-kun Returns", ignoreCase = true)) {
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

                        // Keyword Tags Row (Horizontal slideable row, monochrome styling, subtle 4dp corners, no thin border)
                        val animeKeywords = remember(d.tags, d.genres, isAdultAnime) {
                            if (isAdultAnime) (d.genres + d.tags).distinct() else (d.tags.ifEmpty { d.genres }).distinct()
                        }
                        if (animeKeywords.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                items(animeKeywords) { tag ->
                                    Box(
                                        modifier = Modifier
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF1C1C24))
                                            .clickable {
                                                val intent = Intent(this@AnimeBoxDetailActivity, AnimeBoxMainActivity::class.java).apply {
                                                    putExtra("selectTab", 1)
                                                    if (d.genres.contains(tag)) {
                                                        putExtra("searchCategoryMode", "GENRE")
                                                        putExtra("activeSearchGenre", tag)
                                                    } else {
                                                        putExtra("searchCategoryMode", "TAG")
                                                        putExtra("activeSearchTag", tag)
                                                    }
                                                    putExtra("isAdultTag", isAdultAnime)
                                                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                                }
                                                startActivity(intent)
                                            }
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tag,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
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
                            text = {
                                val adultTabCount = if (episodeMetaMap.isNotEmpty()) {
                                    episodeMetaMap.size
                                } else if (d.episodesCount > 0) {
                                    d.episodesCount
                                } else {
                                    intent.getIntExtra("initialEpisodesCount", 0)
                                }
                                // Always show the count in plain brackets. Some fonts turn "(6)" into a circled
                                // digit via ligatures, so ligatures are disabled and a joiner-breaker is inserted.
                                Text(
                                    text = when {
                                        isAdultAnime -> if (adultTabCount > 0) "Episodes (\u200C$adultTabCount\u200C)" else "Episodes"
                                        isMovie -> "Movie"
                                        d.episodesCount > 0 -> "Episodes (\u200C${d.episodesCount}\u200C)"
                                        else -> "Episodes"
                                    },
                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, clig 0, calt 0, dlig 0"),
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 15.sp
                                )
                            }
                        )
                        if (hasSpecials) {
                            Tab(
                                selected = selectedSectionTab == 1,
                                onClick = { selectedSectionTab = 1 },
                                text = { Text("Specials (\u200C${shinChanSpecials.size}\u200C)", style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, clig 0, calt 0, dlig 0"), fontWeight = FontWeight.Normal, fontSize = 15.sp) }
                            )
                        }
                        Tab(
                            selected = selectedSectionTab == (if (hasSpecials) 2 else 1),
                            onClick = { selectedSectionTab = if (hasSpecials) 2 else 1 },
                            text = { Text("More Like This", fontWeight = FontWeight.Normal, fontSize = 15.sp) }
                        )
                        Tab(
                            selected = selectedSectionTab == (if (hasSpecials) 3 else 2),
                            onClick = { selectedSectionTab = if (hasSpecials) 3 else 2 },
                            text = { Text("Related", fontWeight = FontWeight.Normal, fontSize = 15.sp) }
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
                                                    Text(if (isMovie) "Search movie..." else "Search episode...", color = Color.Gray, fontSize = 13.5.sp)
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
                                        com.lagradost.cloudstream3.ui.animebox.components.ThemeDotCircularProgressIndicator(
                                            color = primaryColor,
                                            size = 40.dp
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (filteredEpisodes.isEmpty() && episodeSearchQuery.isBlank() && d.episodesCount <= 0 && episodeMetaMap.isEmpty() && !hasLoadedEpisodeMetadata) {
                                            // Theme loader (same as the stream-loading state above) with reserved height so nothing shifts.
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(120.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                com.lagradost.cloudstream3.ui.animebox.components.ThemeDotCircularProgressIndicator(
                                                    color = primaryColor,
                                                    size = 40.dp
                                                )
                                            }
                                        } else if (filteredEpisodes.isEmpty()) {
                                            Text(
                                                text = if (episodeSearchQuery.isBlank() && d.episodesCount <= 0 && episodeMetaMap.isEmpty()) {
                                                    "Episode details are unavailable. Check your network connection and retry."
                                                } else {
                                                    "No episodes found matching \"$episodeSearchQuery\""
                                                },
                                                color = Color.Gray,
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(vertical = 16.dp)
                                            )
                                        } else {
                                            if (episodeViewMode == "image") {
                                                val isFiveCmDetail = anilistId == 1689
                                                val isKnownTv = !isFiveCmDetail && (d.format.equals("TV", ignoreCase = true) || d.format.equals("TV_SHORT", ignoreCase = true) || d.episodesCount > 1)
                                                val isMovie = isFiveCmDetail || (!isAdultAnime && !isKnownTv && ((d.format.uppercase() == "MOVIE") || (d.episodesCount == 1) || (intent.getIntExtra("episodes", 0) == 1) || com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.isStaticMovie(anilistId)))
                                                filteredEpisodes.forEach { epNum ->
                                                    val meta = episodeMetaMap[epNum]
                                                    var epTitle = if (isMovie) {
                                                        d.title
                                                    } else {
                                                        meta?.title ?: "Episode $epNum"
                                                    }
                                                    if (isShinChanAnime && !isMovie) {
                                                        if (epTitle.any { it.code in 0x3040..0x309F || it.code in 0x30A0..0x30FF || it.code in 0x4E00..0x9FAF } || epTitle.isBlank() || epTitle.equals("null", ignoreCase = true)) {
                                                            epTitle = "Episode $epNum"
                                                        }
                                                    }
                                                    val defaultCardImage = if (isShinChanAnime) {
                                                        com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getAllEpisodeCovers(this@AnimeBoxDetailActivity)[epNum] ?: meta?.imageUrl ?: d.coverUrl
                                                    } else {
                                                        meta?.imageUrl?.ifEmpty { d.coverUrl } ?: d.coverUrl
                                                    }
                                                    EpisodeRowCard(
                                                        anilistId = d.id,
                                                        episodeNum = epNum,
                                                        title = epTitle,
                                                        defaultImageUrl = defaultCardImage,
                                                        meta = meta,
                                                        isLastWatched = (lastWatched != null && lastWatched.episodeNumber == epNum),
                                                        isMovie = isMovie,
                                                        movieDuration = if (d.duration > 0) d.duration else 90,
                                                        movieTitle = d.title,
                                                        movieDescription = d.description,
                                                        fillerEpisodes = fillerEpisodes,
                                                        movieBackdropUrl = resolvedBackdropUrl,
                                                        animeTitle = d.title,
                                                        screenshotRevision = screenshotRevision,
                                                        onClick = {
                                                            isStreamLoading = true
                                                            coroutineScope.launch {
                                                                val resolvedEpCover = getResolvedEpisodeCover(d.id, epNum, d.coverUrl, meta, d.title)
                                                                fetchAndPlayStream(d.id, epNum, d.title, resolvedEpCover, getPreferredStreamType(), d.coverUrl, d.episodesCount, detailObj = d, backdropUrl = resolvedBackdropUrl, logoUrl = animeLogoUrl, onAddedToLibrary = { isInLibrary = true })
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
                                                                                 val resolvedEpCover = getResolvedEpisodeCover(d.id, epNum, d.coverUrl, meta, d.title)
                                                                                 fetchAndPlayStream(d.id, epNum, d.title, resolvedEpCover, getPreferredStreamType(), d.coverUrl, d.episodesCount, detailObj = d, backdropUrl = resolvedBackdropUrl, logoUrl = animeLogoUrl, onAddedToLibrary = { isInLibrary = true })
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
                                                Spacer(modifier = Modifier.height(12.dp))

                                                // ─── Professional Sleek Greyish-Dark "Load More Episodes" Button ───
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF22242B))
                                                        .clickable {
                                                             visibleEpisodesCount += 50
                                                        }
                                                        .padding(horizontal = 20.dp, vertical = 16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                                            contentDescription = "Expand",
                                                            tint = Color(0xFFE5E7EB),
                                                            modifier = Modifier.size(34.dp)
                                                        )

                                                        Spacer(modifier = Modifier.width(14.dp))

                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally
                                                        ) {
                                                            Text(
                                                                text = "Load More Episodes",
                                                                color = Color.White,
                                                                fontSize = 17.5.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                letterSpacing = 0.35.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(3.dp))
                                                            Text(
                                                                text = "Showing $visibleEpisodesCount of ${episodesList.size} episodes",
                                                                color = Color(0xFFB3B3BF),
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                    }
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
                                                                        explicitUrl = sp.mobileUrl,
                                                                        detailObj = d,
                                                                        backdropUrl = resolvedBackdropUrl,
                                                                        logoUrl = animeLogoUrl,
                                                                        onAddedToLibrary = { isInLibrary = true }
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
                                    val isReleased = !rec.status.equals("NOT_YET_RELEASED", ignoreCase = true)
                                    isReleased && (isAdultAnime || format == "TV" || format == "MOVIE" || format == "TV_SHORT" || format == "OVA" || format == "ONA" || format == "SPECIAL")
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
                                                    val isRecTv = (rec.episodes ?: 0) > 1 || rec.format.equals("TV", ignoreCase = true) || rec.format.equals("TV_SHORT", ignoreCase = true)
                                                    val isRecMovie = !isRecTv && (
                                                        rec.format.equals("MOVIE", ignoreCase = true) ||
                                                        com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.isStaticMovie(rec.id) ||
                                                        (rec.episodes == 1 && rec.format.equals("SPECIAL", ignoreCase = true))
                                                    )
                                                    val epCount = if (isRecMovie) 1 else (rec.episodes ?: 0)
                                                    val recPokemonGen = com.lagradost.cloudstream3.ui.animebox.extractors.PokemonHindiExtractor.getPokemonGenerationLabel(rec.id, rec.title)
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        DetailAnimeGridPosterCard(
                                                            id = rec.id,
                                                            title = rec.title,
                                                            coverUrl = rec.coverUrl,
                                                            badgeText = recPokemonGen ?: if (isRecMovie) "Movie" else "",
                                                            epCount = epCount,
                                                            isHentai = isAdultAnime,
                                                            onLongClick = {
                                                                quickOverviewAnime = QuickOverviewDetailAnime(rec.id, rec.title, rec.coverUrl, epCount)
                                                            },
                                                            onClick = {
                                                                val intent = Intent(this@AnimeBoxDetailActivity, AnimeBoxDetailActivity::class.java).apply {
                                                                    putExtra("anilistId", rec.id)
                                                                    putExtra("initialTitle", rec.title)
                                                                    putExtra("initialCoverUrl", rec.coverUrl)
                                                                    putExtra("initialEpisodesCount", epCount)
                                                                    if (isAdultAnime) putExtra("isAdult", true)
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
                                    val isReleased = !rel.status.equals("NOT_YET_RELEASED", ignoreCase = true)
                                    if (isAdultAnime) {
                                        val isNotPrintOrAdaptation = formatUpper != "MANGA" && formatUpper != "NOVEL" && formatUpper != "ONE_SHOT" && relTypeUpper != "ADAPTATION"
                                        val isAnimeFormat = formatUpper == "OVA" || formatUpper == "ONA" || formatUpper == "TV" || formatUpper == "TV_SHORT" || formatUpper == "SPECIAL" || formatUpper == "MOVIE"
                                        isReleased && isNotPrintOrAdaptation && (isAnimeFormat || formatUpper.isEmpty())
                                    } else {
                                        val isTvOrMovie = (formatUpper == "TV" || formatUpper == "MOVIE" || formatUpper == "TV_SHORT" || formatUpper == "OVA" || formatUpper == "ONA" || formatUpper == "SPECIAL")
                                        val isSequelPrequelOrMovieSideStory = (relTypeUpper == "SEQUEL" || relTypeUpper == "PREQUEL" || (formatUpper == "MOVIE" && relTypeUpper == "SIDE_STORY"))
                                        isReleased && isTvOrMovie && isSequelPrequelOrMovieSideStory
                                    }
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
                                                    val isRelTv = (rel.episodes ?: 0) > 1 || rel.format.equals("TV", ignoreCase = true) || rel.format.equals("TV_SHORT", ignoreCase = true)
                                                    val isRelMovie = !isRelTv && (
                                                        rel.format.equals("MOVIE", ignoreCase = true) ||
                                                        com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.isStaticMovie(rel.id) ||
                                                        (rel.episodes == 1 && (rel.format.equals("SPECIAL", ignoreCase = true) || rel.relationType.contains("MOVIE", ignoreCase = true)))
                                                    )
                                                    val relPokemonGen = com.lagradost.cloudstream3.ui.animebox.extractors.PokemonHindiExtractor.getPokemonGenerationLabel(rel.id, rel.title)
                                                    val badgeText = when {
                                                        relPokemonGen != null -> relPokemonGen
                                                        isRelMovie -> "Movie"
                                                        rel.relationType.equals("SEQUEL", ignoreCase = true) -> "Sequel"
                                                        rel.relationType.equals("PREQUEL", ignoreCase = true) -> "Prequel"
                                                        rel.format.equals("SPECIAL", ignoreCase = true) -> "Special"
                                                        else -> rel.relationType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                                                    }
                                                    val epCount = if (isRelMovie) 1 else (rel.episodes ?: 0)

                                                    Box(modifier = Modifier.weight(1f)) {
                                                        DetailAnimeGridPosterCard(
                                                            id = rel.id,
                                                            title = rel.title,
                                                            coverUrl = rel.coverUrl,
                                                            badgeText = badgeText,
                                                            epCount = epCount,
                                                            isHentai = isAdultAnime,
                                                            onLongClick = {
                                                                quickOverviewAnime = QuickOverviewDetailAnime(rel.id, rel.title, rel.coverUrl, epCount)
                                                            },
                                                            onClick = {
                                                                val intent = Intent(this@AnimeBoxDetailActivity, AnimeBoxDetailActivity::class.java).apply {
                                                                    putExtra("anilistId", rel.id)
                                                                    putExtra("initialTitle", rel.title)
                                                                    putExtra("initialCoverUrl", rel.coverUrl)
                                                                    putExtra("initialEpisodesCount", epCount)
                                                                    if (isAdultAnime) putExtra("isAdult", true)
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

                                Spacer(modifier = Modifier.height(16.dp))

                                // ─── Sleek Box-Style "Watch Order Guide" Button ───
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF22242B))
                                        .clickable {
                                            showWatchOrderDialog = true
                                        }
                                        .padding(horizontal = 20.dp, vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SolarBrokenLibraryIcon(
                                            tint = Color(0xFFE5E7EB),
                                            modifier = Modifier.size(32.dp)
                                        )

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "Watch Order Guide",
                                                color = Color.White,
                                                fontSize = 17.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.35.sp
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "Chronological storyline & release timeline",
                                                color = Color(0xFFB3B3BF),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
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

                    // Floating Top Watch Order Chibi Banner for Completed / Non-Ongoing Series
                    var showWatchOrderBanner by remember(d.id) { mutableStateOf(!isOngoing) }
                    var watchOrderBannerDismissed by remember(d.id) { mutableStateOf(false) }
                    val watchOrderBannerOffsetX = remember(d.id) { androidx.compose.animation.core.Animatable(0f) }

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
                                        .clip(androidx.compose.ui.graphics.RectangleShape)
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
                                            imageVector = SolarCalendarVector,
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

                    // Top Watch Order Chibi Banner for Completed / Non-Ongoing Anime
                    AnimatedVisibility(
                        visible = showWatchOrderBanner && !watchOrderBannerDismissed && !isOngoing && isNetworkAvailable(this@AnimeBoxDetailActivity),
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
                                .offset { IntOffset(watchOrderBannerOffsetX.value.roundToInt(), 0) }
                                .alpha((1f - (abs(watchOrderBannerOffsetX.value) / 320f)).coerceIn(0f, 1f))
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            bannerScope.launch {
                                                if (abs(watchOrderBannerOffsetX.value) > 100f) {
                                                    val target = if (watchOrderBannerOffsetX.value > 0) 1200f else -1200f
                                                    watchOrderBannerOffsetX.animateTo(target, androidx.compose.animation.core.tween(200))
                                                    watchOrderBannerDismissed = true
                                                    showWatchOrderBanner = false
                                                } else {
                                                    watchOrderBannerOffsetX.animateTo(0f, androidx.compose.animation.core.tween(150))
                                                }
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            bannerScope.launch {
                                                watchOrderBannerOffsetX.snapTo(watchOrderBannerOffsetX.value + dragAmount)
                                            }
                                        }
                                    )
                                }
                                .shadow(elevation = 18.dp, shape = RoundedCornerShape(14.dp), spotColor = Color.Black)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF141418))
                                .clickable {
                                    showWatchOrderDialog = true
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
                                    contentDescription = "Watch Order Guide",
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
                                            text = "Confused where to start?",
                                            color = Color.White,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        IconButton(
                                            onClick = {
                                                watchOrderBannerDismissed = true
                                                showWatchOrderBanner = false
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
                                        text = "Explore the chronological story and release order for this franchise",
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
                                                showWatchOrderDialog = true
                                            }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        SolarBrokenLibraryIcon(
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "View Watch Order",
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

            Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                Box(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 82.dp)
                        .size(56.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF171717))
                        .clickable { showAnimeReviewsDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    com.lagradost.cloudstream3.ui.animebox.comments.SolarChatRoundDotsBrokenIcon(modifier = Modifier.size(24.dp), tint = Color.White, strokeWidth = 1.8f)
                }
            }

            if (showTrailerConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showTrailerConfirmDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setLowPerformanceMode(this@AnimeBoxDetailActivity, false)
                            com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setTrailerEnabled(this@AnimeBoxDetailActivity, true)
                            showTrailerConfirmDialog = false
                            trailerStreamUrl = null
                            isTrailerPlaying = true
                        }) {
                            Text("Turn Off & Play", color = primaryColor, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showTrailerConfirmDialog = false
                            trailerStreamUrl = null
                            isTrailerPlaying = true
                        }) {
                            Text("Play Once", color = Color.Gray)
                        }
                    },
                    title = {
                        Text(
                            text = "Enable Trailer Autoplay",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    text = {
                        Text(
                            text = "Trailer autoplay is currently disabled by Low Performance Mode. Would you like to turn Low Performance Mode off to enable trailer autoplay?",
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    },
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    textContentColor = Color.LightGray
                )
            }


        // Quick Overview Modal Bottom Sheet (Long-Press on any poster in More Like This / Related)
        if (quickOverviewAnime != null) {
            val target = quickOverviewAnime!!
            ModalBottomSheet(
                onDismissRequest = { quickOverviewAnime = null },
                containerColor = Color(0xFF13141B),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF484A54))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(target.coverUrl),
                            contentDescription = target.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 46.dp, height = 64.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF22242D))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = target.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Add to My List",
                                color = Color(0xFF9E9EB2),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statuses = listOf(
                            Triple("Watching", StatusWatchingIcon, Color(0xFF22C55E)),
                            Triple("Plan to Watch", StatusPlanToWatchIcon, Color(0xFF3B82F6)),
                            Triple("Completed", StatusCompletedIcon, Color(0xFF10B981)),
                            Triple("On-Hold", StatusOnHoldIcon, Color(0xFFF59E0B)),
                            Triple("Dropped", StatusDroppedIcon, Color(0xFFEF4444))
                        )

                        statuses.forEach { (statusName, statusIcon, statusColor) ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF20222B))
                                    .clickable {
                                        val brief = AnimeBrief(
                                            id = target.id,
                                            title = target.title,
                                            coverUrl = target.coverUrl,
                                            bannerUrl = "",
                                            description = "",
                                            episodes = target.epCount
                                        )
                                        libraryManager.addOrUpdateLibraryItem(brief, statusName)
                                        Toast.makeText(this@AnimeBoxDetailActivity, "Marked as $statusName", Toast.LENGTH_SHORT).show()
                                        quickOverviewAnime = null
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = statusName,
                                    tint = statusColor,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = statusName,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp))
                }
            }
        }

            if (showWatchOrderDialog && detail != null) {
                WatchOrderDialog(
                    currentAnime = detail!!,
                    episodeMetaMap = episodeMetaMap,
                    onDismiss = { showWatchOrderDialog = false },
                    onSelectAnime = { targetId, targetItem ->
                        showWatchOrderDialog = false
                        if (targetId != detail!!.id) {
                            val intent = Intent(this@AnimeBoxDetailActivity, AnimeBoxDetailActivity::class.java).apply {
                                putExtra("anilistId", targetId)
                                if (targetItem != null) {
                                    putExtra("initialTitle", targetItem.title)
                                    putExtra("initialCoverUrl", targetItem.coverUrl.ifEmpty { targetItem.bannerUrl })
                                    putExtra("initialEpisodesCount", targetItem.episodes ?: 0)
                                }
                            }
                            startActivity(intent)
                        }
                    },
                    onPlayEpisode = { targetAnimeId, epNum, targetTitle, targetFranchiseItem ->
                        showWatchOrderDialog = false
                        val d = detail!!
                        val targetSeason = OpenRouterChatClient.extractSeasonNumber(targetTitle)
                        val currentSeason = OpenRouterChatClient.extractSeasonNumber(d.title) ?: 1
                        val hasConflictingSeason = targetSeason != null && targetSeason != currentSeason
                        val isForCurrentAnime = (targetAnimeId == 0 || targetAnimeId == d.id) && !hasConflictingSeason &&
                            (targetFranchiseItem == null || targetFranchiseItem.id == d.id) &&
                            (targetTitle.isBlank() || targetTitle.equals(d.title, ignoreCase = true) || (!hasConflictingSeason && (targetTitle.contains(d.title, ignoreCase = true) || d.title.contains(targetTitle, ignoreCase = true))))

                        val effectiveId = if (isForCurrentAnime) d.id else (targetFranchiseItem?.id ?: targetAnimeId.takeIf { it > 0 } ?: d.id)
                        val effectiveTitle = if (isForCurrentAnime) d.title else (targetFranchiseItem?.title ?: targetTitle.ifBlank { d.title })
                        val effectiveShowCover = if (isForCurrentAnime) d.coverUrl else (targetFranchiseItem?.coverUrl ?: d.coverUrl)
                        val effectiveBackdrop = if (isForCurrentAnime) resolvedBackdropUrl else (targetFranchiseItem?.bannerUrl ?: "")
                        val effectiveTotalEps = if (isForCurrentAnime) d.episodesCount else (targetFranchiseItem?.episodes ?: 0)
                        val epMeta = if (isForCurrentAnime) episodeMetaMap[epNum] else null

                        isStreamLoading = true
                        coroutineScope.launch {
                            try {
                                val effectiveCover = if (isForCurrentAnime) {
                                    getResolvedEpisodeCover(d.id, epNum, d.coverUrl, epMeta, d.title)
                                } else {
                                    val extMetaMap = try {
                                        AniZipClient.getEpisodeMetadata(effectiveId)
                                    } catch (_: Exception) { emptyMap() }
                                    val extMeta = extMetaMap[epNum]
                                    val fallbackCover = targetFranchiseItem?.bannerUrl?.takeIf { it.isNotBlank() }
                                        ?: targetFranchiseItem?.coverUrl
                                        ?: d.coverUrl
                                    getResolvedEpisodeCover(effectiveId, epNum, fallbackCover, extMeta, effectiveTitle)
                                }
                                fetchAndPlayStream(
                                    anilistId = effectiveId,
                                    episodeNum = epNum,
                                    animeTitle = effectiveTitle,
                                    episodeCoverUrl = effectiveCover,
                                    type = getPreferredStreamType(),
                                    showCoverUrl = effectiveShowCover,
                                    totalEpisodes = effectiveTotalEps,
                                    detailObj = if (isForCurrentAnime) d else null,
                                    backdropUrl = effectiveBackdrop,
                                    logoUrl = if (isForCurrentAnime) animeLogoUrl else "",
                                    onAddedToLibrary = { isInLibrary = true }
                                )
                            } catch (_: Exception) {
                            } finally {
                                isStreamLoading = false
                            }
                        }
                    }
                )
            }

            if (showAnimeReviewsDialog && detail != null) {
                AnimeReviewsDialog(
                    animeId = detail!!.id,
                    animeTitle = detail!!.title,
                    onDismiss = { showAnimeReviewsDialog = false }
                )
            }

            if (showAddToListDialog && detail != null) {
                val d = detail!!
                val isShin = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(d.id)
                val effectiveCover = if (isShin) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_POSTER_URL else d.coverUrl
                val animeBrief = AnimeBrief(
                    id = d.id,
                    title = d.title,
                    coverUrl = effectiveCover,
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
                    val allDownloaded = remember(episodeToDownload) { downloadManager.getAllDownloadedEpisodes() }
                    fun isTypeDownloaded(type: String): Boolean {
                        return allDownloaded.any {
                            it.anilistId == d.id && it.episodeNumber == epNum &&
                            (it.streamType.equals(type, ignoreCase = true) || (type == "sub" && it.streamType == "hardsub") || (type == "hardsub" && it.streamType == "sub")) &&
                            it.status == com.lagradost.cloudstream3.ui.animebox.download.DownloadStatus.COMPLETED
                        }
                    }
                    val preferredLang = getPreferredStreamType()
                    val allAudioOptions = if (isAdultAnime) {
                        listOf(Triple("sub", "Japanese (Original)", "Original Japanese Audio • English Subtitles"))
                    } else {
                        listOf(
                            Triple("sub", "Japanese (Original)", "Original Japanese Audio • English Subtitles"),
                            Triple("dub", "English Dubbed", "English Voice Audio"),
                            Triple("hindi", "Hindi Dubbed", "Hindi Voice Audio (If Available)"),
                            Triple("french", "French (VF)", "French Voice Audio (If Available)"),
                            Triple("italian", "Italian (ITA)", "Italian Voice Audio (If Available)"),
                            Triple("spanish_latino", "Spanish (Latino)", "Spanish Latino Audio (If Available)"),
                            Triple("spanish_castellano", "Spanish (Castellano)", "Castellano Audio (If Available)"),
                            Triple("german", "German (Deutsch)", "German Audio (If Available)")
                        )
                    }
                    val audioOptions = if (isAdultAnime) {
                        allAudioOptions
                    } else {
                        allAudioOptions.filter {
                            com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isLanguageEnabled(it.first)
                        }
                    }
                    val availableTypes = audioOptions.map { it.first }
                    val initialLang = if (isTypeDownloaded(preferredLang) || !availableTypes.contains(preferredLang)) {
                        availableTypes.firstOrNull { !isTypeDownloaded(it) } ?: availableTypes.firstOrNull() ?: "sub"
                    } else preferredLang
                    var selectedAudioLang by remember { mutableStateOf(initialLang) }
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

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    audioOptions.forEach { (type, label, desc) ->
                                        val isDownloaded = isTypeDownloaded(type)
                                        val isSelected = selectedAudioLang == type && !isDownloaded
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    when {
                                                        isSelected -> Color.White
                                                        isDownloaded -> Color(0xFF14151B)
                                                        else -> Color(0xFF1D1E26)
                                                    }
                                                )
                                                .clickable(enabled = !isDownloaded) { selectedAudioLang = type }
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isDownloaded) "$label • Downloaded" else label,
                                                    color = when {
                                                        isSelected -> Color.Black
                                                        isDownloaded -> Color(0xFF6B6B7A)
                                                        else -> Color.White
                                                    },
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
                                                } else if (isDownloaded) {
                                                    Icon(
                                                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                                        contentDescription = "Downloaded",
                                                        tint = Color(0xFF6B6B7A),
                                                        modifier = Modifier.size(18.dp)
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
                                                if (isAdultAnime) {
                                                    val altTitles = listOfNotNull(d.title, initialTitle)
                                                    val hmResult = try {
                                                        HentaiMamaExtractor.getStreamForAnime(d.title, epNum, altTitles)
                                                    } catch (_: Exception) { null }
                                                    val hhResult = if (hmResult == null) {
                                                        try {
                                                            HentaiHavenExtractor.getStreamForAnime(d.title, epNum, altTitles)
                                                        } catch (_: Exception) { null }
                                                    } else null
                                                    val resultStream = hmResult?.streamUrl ?: hhResult?.streamUrl ?: ""
                                                    val resultRef = hmResult?.referer ?: hhResult?.referer ?: "https://hentaimama.io/"
                                                    if (resultStream.isNotEmpty()) {
                                                        mapOf(
                                                            "hls" to resultStream,
                                                            "referer" to resultRef,
                                                            "subtitle" to "",
                                                            "subtitlesJson" to "[]"
                                                        )
                                                    } else null
                                                } else {
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
                                                    com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(this@AnimeBoxDetailActivity, d.id, epNum, targetLang, d.title, serverOverride = com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getDownloadServer(this@AnimeBoxDetailActivity))
                                                }
                                                }
                                            }
                                            var directHls = (streamInfo?.get("hls") as? String) ?: ""
                                            var actualStreamType = targetLang
                                            if (directHls.isEmpty() && targetLang == "hardsub") {
                                                streamInfo = withContext(Dispatchers.IO) {
                                                    com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(this@AnimeBoxDetailActivity, d.id, epNum, "sub", d.title, serverOverride = com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getDownloadServer(this@AnimeBoxDetailActivity))
                                                }
                                                directHls = (streamInfo?.get("hls") as? String) ?: ""
                                                actualStreamType = "sub"
                                            }

                                            var directRef = (streamInfo?.get("referer") as? String) ?: ""
                                            var directSub = (streamInfo?.get("subtitle") as? String) ?: ""
                                            var subsJson = (streamInfo?.get("subtitlesJson") as? String) ?: "[]"
                                            val iStart = (streamInfo?.get("introStart") as? Long) ?: 0L
                                            val iEnd = (streamInfo?.get("introEnd") as? Long) ?: 0L
                                            val oStart = (streamInfo?.get("outroStart") as? Long) ?: 0L
                                            val oEnd = (streamInfo?.get("outroEnd") as? Long) ?: 0L

                                            isStartingDownload = false

                                            if (directHls.isNotEmpty()) {
                                                val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(d.id)
                                                val epMeta = episodeMetaMap[epNum]
                                                val epCover = if (isShinChan) {
                                                    com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager.getAllEpisodeCovers()[epNum] ?: d.coverUrl
                                                } else epMeta?.imageUrl?.ifEmpty { d.coverUrl } ?: d.coverUrl
                                                val showPoster = if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_POSTER_URL else d.coverUrl.ifEmpty { initialCover }
                                                val epBackdrop = if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_BACKDROP_URL else resolvedBackdropUrl.ifEmpty { d.bannerUrl }
                                                val epLogo = if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_LOGO_URL else animeLogoUrl
                                                val langTag = when (actualStreamType) {
                                                    "hindi" -> "Hindi Dub"
                                                    "dub" -> "English Dub"
                                                    "french", "vf" -> "French (VF)"
                                                    "italian", "ita" -> "Italian (ITA)"
                                                    "spanish_latino", "latino" -> "Spanish (Latino)"
                                                    "spanish_castellano", "castellano" -> "Spanish (Castellano)"
                                                    "german", "deutsch" -> "German (Deutsch)"
                                                    "hardsub" -> "HSub"
                                                    else -> "Sub"
                                                }

                                                val prefQ = AnimeBoxSettings.getDownloadQuality(this@AnimeBoxDetailActivity)
                                                val availableQualities = withContext(Dispatchers.IO) {
                                                    AnimeDownloadManager.resolveStreamQualities(directHls, directRef)
                                                }
                                                val subtitleLanguages = subtitleLanguageOptions(subsJson).ifEmpty {
                                                    if (directSub.isNotBlank()) listOf("English") else emptyList()
                                                }
                                                val preferredSubtitleLanguage = AnimeBoxSettings.getDownloadSubtitleLanguage(this@AnimeBoxDetailActivity)
                                                val needsSubtitleChoice = subtitleLanguages.isNotEmpty() &&
                                                    (preferredSubtitleLanguage == "ask" || preferredSubtitleLanguage !in subtitleLanguages)

                                                fun doEnqueue(chosenQuality: String, chosenSubtitleLanguage: String = preferredSubtitleLanguage) {
                                                    downloadManager.enqueueDownload(
                                                        anilistId = d.id,
                                                        animeTitle = d.title,
                                                        episodeNum = epNum,
                                                        episodeTitle = epTitle,
                                                        coverUrl = epCover,
                                                        showCoverUrl = showPoster,
                                                        quality = "$chosenQuality $langTag",
                                                        resolvedUrl = directHls,
                                                        referer = directRef,
                                                        subtitleUrl = directSub,
                                                        subtitlesJson = subtitleJsonForLanguage(subsJson, chosenSubtitleLanguage),
                                                        introStart = iStart,
                                                        introEnd = iEnd,
                                                        outroStart = oStart,
                                                        outroEnd = oEnd,
                                                        streamType = actualStreamType,
                                                        providedBackdrop = epBackdrop,
                                                        providedLogo = epLogo
                                                    )
                                                    Toast.makeText(this@AnimeBoxDetailActivity, "Episode $epNum queued ($chosenQuality)", Toast.LENGTH_SHORT).show()
                                                    episodeToDownload = null
                                                    downloadQualityPrompt = null
                                                }

                                                if (prefQ != "ask" && prefQ in availableQualities && !needsSubtitleChoice) {
                                                    doEnqueue(prefQ, preferredSubtitleLanguage)
                                                } else {
                                                    // Close the episode details sheet before showing the quality selector.
                                                    episodeToDownload = null
                                                    val serverCategory = if (isAdultAnime) "hentai-jp" else targetLang
                                                    val availServers = com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getServersForCategory(serverCategory)
                                                    val currentPrefServer = com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getDownloadServer(this@AnimeBoxDetailActivity)
                                                        ?: com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getPreferredServer(this@AnimeBoxDetailActivity, serverCategory)
                                                    downloadQualityPrompt = DownloadQualityPromptState(
                                                        episodeNum = epNum,
                                                        episodeTitle = epTitle,
                                                        animeTitle = d.title,
                                                        availableQualities = availableQualities,
                                                        preferredQuality = prefQ,
                                                        isPreferredMissing = (prefQ != "ask" && prefQ !in availableQualities),
                                                        subtitleLanguages = subtitleLanguages,
                                                        preferredSubtitleLanguage = preferredSubtitleLanguage,
                                                        isSeason = false,
                                                        currentServer = currentPrefServer,
                                                        availableServers = availServers,
                                                        onServerSelected = { newServer ->
                                                            val newInfo = withContext(Dispatchers.IO) {
                                                                if (isAdultAnime) {
                                                                    val searchTitles = listOfNotNull(d.title, d.title.replace("migi", "migui", ignoreCase = true), d.title.replace("migui", "migi", ignoreCase = true)).distinct()
                                                                    when (newServer.lowercase()) {
                                                                        "hahomoe" -> {
                                                                            val h = runCatching { com.lagradost.cloudstream3.ui.animebox.extractors.HahoMoeExtractor.getStream(d.title, epNum, targetLang.contains("dub", ignoreCase = true), searchTitles) }.getOrNull()
                                                                            if (h != null && h.streamUrl.isNotEmpty()) {
                                                                                mapOf("hls" to h.streamUrl, "referer" to h.referer, "subtitle" to "", "subtitlesJson" to "[]")
                                                                            } else null
                                                                        }
                                                                        "reanime" -> {
                                                                            val r = runCatching { com.lagradost.cloudstream3.ui.animebox.extractors.ReAnimeExtractor.extract(d.id, epNum, "sub") }.getOrNull()
                                                                            if (r != null && r.hlsUrl.isNotEmpty()) {
                                                                                val sJson = org.json.JSONArray().apply { r.subtitles.forEach { put(org.json.JSONObject(it)) } }.toString()
                                                                                mapOf("hls" to r.hlsUrl, "referer" to r.referer, "subtitle" to r.subtitleUrl, "subtitlesJson" to sJson)
                                                                            } else null
                                                                        }
                                                                        "hentaihaven" -> {
                                                                            val hh = runCatching { com.lagradost.cloudstream3.ui.animebox.extractors.HentaiHavenExtractor.getStreamForAnime(d.title, epNum, searchTitles) }.getOrNull()
                                                                            if (hh != null && hh.streamUrl.isNotEmpty()) {
                                                                                mapOf("hls" to hh.streamUrl, "referer" to hh.referer, "subtitle" to hh.subtitleUrl, "subtitlesJson" to hh.subtitlesJson)
                                                                            } else null
                                                                        }
                                                                        else -> {
                                                                            val hm = runCatching { com.lagradost.cloudstream3.ui.animebox.extractors.HentaiMamaExtractor.getStreamForAnime(d.title, epNum, searchTitles) }.getOrNull()
                                                                            if (hm != null && hm.streamUrl.isNotEmpty()) {
                                                                                mapOf("hls" to hm.streamUrl, "referer" to hm.referer, "subtitle" to "", "subtitlesJson" to "[]")
                                                                            } else null
                                                                        }
                                                                    }
                                                                } else {
                                                                    com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(this@AnimeBoxDetailActivity, d.id, epNum, targetLang, d.title, serverOverride = newServer)
                                                                }
                                                            }
                                                            directHls = (newInfo?.get("hls") as? String).orEmpty()
                                                            directRef = (newInfo?.get("referer") as? String).orEmpty()
                                                            directSub = (newInfo?.get("subtitle") as? String).orEmpty()
                                                            subsJson = (newInfo?.get("subtitlesJson") as? String) ?: "[]"
                                                            val newQ = withContext(Dispatchers.IO) {
                                                                if (directHls.isNotEmpty()) AnimeDownloadManager.resolveStreamQualities(directHls, directRef) else emptyList()
                                                            }
                                                            val newS = subtitleLanguageOptions(subsJson).ifEmpty {
                                                                if (directSub.isNotBlank()) listOf("English") else emptyList()
                                                            }
                                                            Pair(newQ, newS)
                                                        },
                                                        onQualitySelected = { chosen, language ->
                                                            doEnqueue(chosen, language)
                                                        }
                                                    )
                                                }
                                            } else {
                                                Toast.makeText(this@AnimeBoxDetailActivity, "Audio track unavailable for Episode $epNum", Toast.LENGTH_SHORT).show()
                                                episodeToDownload = null
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isStartingDownload && !isTypeDownloaded(selectedAudioLang)
                                ) {
                                    if (isStartingDownload) {
                                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(if (isTypeDownloaded(selectedAudioLang)) "Already Downloaded" else "Download", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                    val allSeasonDownloaded = remember(showDownloadSeasonDialog) { downloadManager.getAllDownloadedEpisodes() }
                    fun isSeasonTypeDownloaded(type: String): Boolean {
                        val downloadedCount = allSeasonDownloaded.count {
                            it.anilistId == d.id &&
                            (it.streamType.equals(type, ignoreCase = true) || (type == "sub" && it.streamType == "hardsub") || (type == "hardsub" && it.streamType == "sub")) &&
                            it.status == com.lagradost.cloudstream3.ui.animebox.download.DownloadStatus.COMPLETED
                        }
                        return totalSeasonEps > 0 && downloadedCount >= totalSeasonEps
                    }
                    val preferredLang = getPreferredStreamType()
                    val allAudioOptions = if (isAdultAnime) {
                        listOf(Triple("sub", "Japanese (Original)", "Original Japanese Audio • English Subtitles"))
                    } else {
                        listOf(
                            Triple("sub", "Japanese (Original)", "Original Japanese Audio • English Subtitles"),
                            Triple("dub", "English Dubbed", "English Voice Audio"),
                            Triple("hindi", "Hindi Dubbed", "Hindi Voice Audio (If Available)"),
                            Triple("french", "French (VF)", "French Voice Audio (If Available)"),
                            Triple("italian", "Italian (ITA)", "Italian Voice Audio (If Available)"),
                            Triple("spanish_latino", "Spanish (Latino)", "Spanish Latino Audio (If Available)"),
                            Triple("spanish_castellano", "Spanish (Castellano)", "Castellano Audio (If Available)"),
                            Triple("german", "German (Deutsch)", "German Audio (If Available)")
                        )
                    }
                    val audioOptions = if (isAdultAnime) {
                        allAudioOptions
                    } else {
                        allAudioOptions.filter {
                            com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isLanguageEnabled(it.first)
                        }
                    }
                    val availableTypes = audioOptions.map { it.first }
                    val initialSeasonLang = if (isSeasonTypeDownloaded(preferredLang) || !availableTypes.contains(preferredLang)) {
                        availableTypes.firstOrNull { !isSeasonTypeDownloaded(it) } ?: availableTypes.firstOrNull() ?: "sub"
                    } else preferredLang
                    var selectedAudioLang by remember { mutableStateOf(initialSeasonLang) }
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

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    audioOptions.forEach { (type, label, desc) ->
                                        val isDownloaded = isSeasonTypeDownloaded(type)
                                        val isSelected = selectedAudioLang == type && !isDownloaded
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    when {
                                                        isSelected -> Color.White
                                                        isDownloaded -> Color(0xFF14151B)
                                                        else -> Color(0xFF1D1E26)
                                                    }
                                                )
                                                .clickable(enabled = !isDownloaded) { selectedAudioLang = type }
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isDownloaded) "$label • Downloaded" else label,
                                                    color = when {
                                                        isSelected -> Color.Black
                                                        isDownloaded -> Color(0xFF6B6B7A)
                                                        else -> Color.White
                                                    },
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
                                                } else if (isDownloaded) {
                                                    Icon(
                                                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                                        contentDescription = "Downloaded",
                                                        tint = Color(0xFF6B6B7A),
                                                        modifier = Modifier.size(18.dp)
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
                                            Toast.makeText(this@AnimeBoxDetailActivity, "Checking stream qualities...", Toast.LENGTH_SHORT).show()
                                            val sampleStream = withContext(Dispatchers.IO) {
                                                com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(
                                                    this@AnimeBoxDetailActivity, d.id, 1, targetLang, d.title
                                                )
                                            }
                                            val sampleHls = (sampleStream?.get("hls") as? String) ?: ""
                                            val sampleRef = (sampleStream?.get("referer") as? String) ?: ""
                                            val subtitleLanguages = subtitleLanguageOptions((sampleStream?.get("subtitlesJson") as? String) ?: "[]")
                                            val preferredSubtitleLanguage = AnimeBoxSettings.getDownloadSubtitleLanguage(this@AnimeBoxDetailActivity)
                                            val needsSubtitleChoice = subtitleLanguages.isNotEmpty() &&
                                                (preferredSubtitleLanguage == "ask" || preferredSubtitleLanguage !in subtitleLanguages)
                                            val prefQ = AnimeBoxSettings.getDownloadQuality(this@AnimeBoxDetailActivity)
                                            val availableQualities = withContext(Dispatchers.IO) {
                                                if (sampleHls.isNotEmpty()) AnimeDownloadManager.resolveStreamQualities(sampleHls, sampleRef)
                                                else listOf("Source")
                                            }

                                            fun doEnqueueAll(chosenQuality: String, chosenSubtitleLanguage: String = preferredSubtitleLanguage) {
                                                coroutineScope.launch {
                                                    isStartingSeasonDownload = true
                                                    Toast.makeText(this@AnimeBoxDetailActivity, "Starting download for all $totalSeasonEps episodes ($chosenQuality)...", Toast.LENGTH_LONG).show()
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
                                                                com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(this@AnimeBoxDetailActivity, d.id, epNum, targetLang, d.title, serverOverride = com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getDownloadServer(this@AnimeBoxDetailActivity))
                                                            }
                                                        }
                                                        var directHls = (streamInfo?.get("hls") as? String) ?: ""
                                                        var actualStreamType = targetLang
                                                        if (directHls.isEmpty() && targetLang == "hardsub") {
                                                            streamInfo = withContext(Dispatchers.IO) {
                                                                com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(this@AnimeBoxDetailActivity, d.id, epNum, "sub", d.title, serverOverride = com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getDownloadServer(this@AnimeBoxDetailActivity))
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
                                                            val langTag = when (actualStreamType) {
                                                                "hindi" -> "Hindi Dub"
                                                                "dub" -> "English Dub"
                                                                "french", "vf" -> "French (VF)"
                                                                "italian", "ita" -> "Italian (ITA)"
                                                                "spanish_latino", "latino" -> "Spanish (Latino)"
                                                                "spanish_castellano", "castellano" -> "Spanish (Castellano)"
                                                                "german", "deutsch" -> "German (Deutsch)"
                                                                "hardsub" -> "HSub"
                                                                else -> "Sub"
                                                            }
                                                            downloadManager.enqueueDownload(
                                                                anilistId = d.id,
                                                                animeTitle = d.title,
                                                                episodeNum = epNum,
                                                                episodeTitle = epTitle,
                                                                coverUrl = epCover,
                                                                showCoverUrl = showPoster,
                                                                quality = "$chosenQuality $langTag",
                                                                resolvedUrl = directHls,
                                                                referer = directRef,
                                                                subtitleUrl = directSub,
                                                                subtitlesJson = subtitleJsonForLanguage(subsJson, chosenSubtitleLanguage),
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
                                                    isStartingSeasonDownload = false
                                                    downloadQualityPrompt = null
                                                }
                                            }

                                            isStartingSeasonDownload = false
                                            if (prefQ != "ask" && prefQ in availableQualities && !needsSubtitleChoice) {
                                                doEnqueueAll(prefQ, preferredSubtitleLanguage)
                                            } else {
                                                // Keep only one modal visible at a time.
                                                showDownloadSeasonDialog = false
                                                val serverCategory = if (isAdultAnime) "hentai-jp" else targetLang
                                                val availServers = com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getServersForCategory(serverCategory)
                                                val currentPrefServer = com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getDownloadServer(this@AnimeBoxDetailActivity)
                                                    ?: com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getPreferredServer(this@AnimeBoxDetailActivity, serverCategory)
                                                downloadQualityPrompt = DownloadQualityPromptState(
                                                    episodeNum = -1,
                                                    episodeTitle = "Season Download",
                                                    animeTitle = d.title,
                                                    availableQualities = availableQualities,
                                                    preferredQuality = prefQ,
                                                    isPreferredMissing = (prefQ != "ask" && prefQ !in availableQualities),
                                                    subtitleLanguages = subtitleLanguages,
                                                    preferredSubtitleLanguage = preferredSubtitleLanguage,
                                                    isSeason = true,
                                                    currentServer = currentPrefServer,
                                                    availableServers = availServers,
                                                    onServerSelected = { newServer ->
                                                        val sampleEp = 1
                                                        val newInfo = withContext(Dispatchers.IO) {
                                                            if (isAdultAnime) {
                                                                val searchTitles = listOfNotNull(d.title, d.title.replace("migi", "migui", ignoreCase = true), d.title.replace("migui", "migi", ignoreCase = true)).distinct()
                                                                com.lagradost.cloudstream3.ui.animebox.extractors.HentaiMamaExtractor.getStreamForAnime(d.title, sampleEp, searchTitles)
                                                            } else {
                                                                com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(this@AnimeBoxDetailActivity, d.id, sampleEp, targetLang, d.title, serverOverride = newServer)
                                                            }
                                                        }
                                                        val sampleHls = (newInfo as? Map<*, *>)?.get("hls") as? String ?: ""
                                                        val sampleRef = (newInfo as? Map<*, *>)?.get("referer") as? String ?: ""
                                                        val sampleSub = (newInfo as? Map<*, *>)?.get("subtitle") as? String ?: ""
                                                        val sampleSubsJson = (newInfo as? Map<*, *>)?.get("subtitlesJson") as? String ?: "[]"
                                                        val newQ = withContext(Dispatchers.IO) {
                                                            if (sampleHls.isNotEmpty()) AnimeDownloadManager.resolveStreamQualities(sampleHls, sampleRef) else emptyList()
                                                        }
                                                        val newS = subtitleLanguageOptions(sampleSubsJson).ifEmpty {
                                                            if (sampleSub.isNotBlank()) listOf("English") else emptyList()
                                                        }
                                                        Pair(newQ, newS)
                                                    },
                                                    onQualitySelected = { chosen, language ->
                                                        doEnqueueAll(chosen, language)
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isStartingSeasonDownload && !isSeasonTypeDownloaded(selectedAudioLang)
                                ) {
                                    if (isStartingSeasonDownload) {
                                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(if (isSeasonTypeDownloaded(selectedAudioLang)) "Season Already Downloaded" else "Download Season ($totalSeasonEps Episodes)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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

            downloadQualityPrompt?.let { promptState ->
                DownloadQualityPromptDialog(
                    state = promptState,
                    onDismiss = { downloadQualityPrompt = null }
                )
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

            if (showReportDialog) {
                IssueReportDialog(
                    animeTitle = reportAnimeTitleExtra,
                    episodeNum = reportEpisodeExtra,
                    onDismiss = { showReportDialog = false },
                    onSubmit = { userName, email, issueType, description ->
                        showReportDialog = false
                        Toast.makeText(this@AnimeBoxDetailActivity, "Submitting report...", Toast.LENGTH_SHORT).show()
                        this@AnimeBoxDetailActivity.lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val actProfile = com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager.getActiveProfile(this@AnimeBoxDetailActivity)
                                val syncUid = com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager.resolveSyncUserId(this@AnimeBoxDetailActivity, actProfile)
                                
                                val videoError = intent.getStringExtra("reportVideoError") ?: ""
                                val videoErrorTrace = intent.getStringExtra("reportVideoErrorTrace") ?: ""
                                val reportServer = intent.getStringExtra("reportServer") ?: ""
                                val reportStreamUrl = intent.getStringExtra("reportStreamUrl") ?: ""
                                val reportRes = intent.getStringExtra("reportResolution") ?: ""
                                val reportAudio = intent.getStringExtra("reportAudio") ?: ""
                                val reportSubtitle = intent.getStringExtra("reportSubtitle") ?: intent.getStringExtra("reportSubMode") ?: ""
                                val reportPosition = intent.getLongExtra("reportPositionMs", 0L)

                                val cachedDiag = com.lagradost.cloudstream3.ui.animebox.notifications.DeviceDiagnosticsHelper.getCurrentVideoDiagnostics()
                                val finalServer = reportServer.ifBlank { cachedDiag?.serverName ?: "" }
                                val finalStreamUrl = reportStreamUrl.ifBlank { cachedDiag?.streamUrl ?: "" }
                                val finalRes = reportRes.ifBlank { cachedDiag?.resolution ?: "" }
                                val finalAudio = reportAudio.ifBlank { cachedDiag?.audioTrack ?: "" }
                                val finalSub = reportSubtitle.ifBlank { cachedDiag?.subtitleTrack ?: "" }
                                val finalPosition = if (reportPosition > 0L) reportPosition else (cachedDiag?.playbackPositionMs ?: 0L)
                                val finalVideoError = videoError.ifBlank { cachedDiag?.errorMessage ?: "" }
                                val finalVideoErrorTrace = videoErrorTrace.ifBlank { cachedDiag?.errorStackTrace ?: "" }

                                val diag = try {
                                    kotlinx.coroutines.withTimeoutOrNull(4500L) {
                                        com.lagradost.cloudstream3.ui.animebox.notifications.DeviceDiagnosticsHelper.collectDiagnostics(
                                            this@AnimeBoxDetailActivity,
                                            com.lagradost.cloudstream3.ui.animebox.notifications.DeviceDiagnosticsHelper.VideoPlaybackDiagnostics(
                                                serverName = finalServer,
                                                streamUrl = finalStreamUrl,
                                                resolution = finalRes,
                                                audioTrack = finalAudio,
                                                subtitleTrack = finalSub,
                                                playbackPositionMs = finalPosition,
                                                errorMessage = finalVideoError,
                                                errorStackTrace = finalVideoErrorTrace
                                            )
                                        )
                                    }
                                } catch (_: Throwable) { null }

                                val finalTitle = reportAnimeTitleExtra.ifBlank {
                                    detail?.title?.ifBlank { null } ?: initialTitle.ifBlank { "Anime" }
                                }

                                val success = com.lagradost.cloudstream3.ui.animebox.notifications.SupabaseReportManager.submitReport(
                                    userName = userName,
                                    email = email,
                                    animeTitle = finalTitle,
                                    anilistId = anilistId,
                                    episodeNumber = reportEpisodeExtra,
                                    issueType = issueType,
                                    description = description,
                                    userId = syncUid,
                                    diagnostics = diag
                                )
                                withContext(Dispatchers.Main) {
                                    if (success) {
                                        Toast.makeText(this@AnimeBoxDetailActivity, "Report received for Ep $reportEpisodeExtra! Our team is on it.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(this@AnimeBoxDetailActivity, "Report failed to send. Please check your network connection.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("AnimeBoxDetail", "Error submitting report: ${e.message}", e)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@AnimeBoxDetailActivity, "Error sending report: ${e.message}", Toast.LENGTH_LONG).show()
                                }
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
        epCount: Int = 0,
        isHentai: Boolean = false,
        onShowKitsuRestriction: (() -> Unit)? = null,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val libraryManager = remember { LibraryManager(this@AnimeBoxDetailActivity) }
        var inLibrary by remember(id) { mutableStateOf(libraryManager.isInLibrary(id)) }
        val context = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick?.invoke() }
                    )
                }
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
                            .clip(RoundedCornerShape(bottomEnd = 10.dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.2.sp
                        )
                    }
                }

                // Top-right library add badge docked flush to corner with bottomStart rounding
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(bottomStart = 8.dp))
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
                        }
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (inLibrary) androidx.compose.material.icons.Icons.Default.Check else androidx.compose.material.icons.Icons.Default.Add,
                        contentDescription = "My List Toggle",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Semi-transparent bottom audio/subtitle bar matching Screenshot 3
                val isHentaiItem = isHentai || (id >= 90000000) || com.lagradost.cloudstream3.ui.animebox.api.AniListClient.isAdultAnimeCached(id)
                val resolvedSubCount: String
                val resolvedDubCount: String

                if (isHentaiItem) {
                    resolvedSubCount = if (epCount > 0 && epCount != 12) "$epCount" else "?"
                    resolvedDubCount = "?"
                } else {
                    val isMovieCard = badgeText.equals("Movie", ignoreCase = true) || epCount <= 1 || id == 1689 || com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.isStaticMovie(id)
                    val effectiveEpCount = if (isMovieCard) 1 else if (epCount > 0) epCount else 0
                    val hasDubConfirmed = remember(id) {
                        com.lagradost.cloudstream3.ui.animebox.api.AnililiDubManager.isDubConfirmed(id)
                    }
                    resolvedSubCount = "$effectiveEpCount"
                    resolvedDubCount = if (hasDubConfirmed) "✓" else "0"
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(26.dp)
                        .background(Color.Black.copy(alpha = 0.85f)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // CC badge (subtitle indicator) like before
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CC",
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = resolvedSubCount,
                        color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(7.dp))
                    Text("|", color = Color(0xFF686C78), fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(7.dp))

                    CustomMicIcon(
                        color = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = resolvedDubCount,
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
    fun CustomSubtitlesIcon(color: Color, modifier: Modifier = Modifier) {
        Icon(
            imageVector = CustomSubVector,
            contentDescription = "Subtitles",
            tint = color,
            modifier = modifier
        )
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

        val navBgColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.getNavbarBgColor(context)
        val navOutlineColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.getNavbarOutlineColor(context)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Background bar with sleek rounded top (no thin border outline)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(navBgColor)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isHomeSelected = selectedTab == 0
                val homeColor = if (isHomeSelected) Color.White else Color(0xFF9E9EA5)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(0) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isHomeSelected) CustomHomeSmileAngleFilledIcon else CustomHomeSmileAngleOutlineIcon,
                        contentDescription = "Home",
                        tint = homeColor,
                        modifier = Modifier.size(24.5.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Home",
                        color = homeColor,
                        fontSize = 11.sp,
                        fontWeight = if (isHomeSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }

                val isSearchSelected = selectedTab == 1
                val searchColor = if (isSearchSelected) Color.White else Color(0xFF9E9EA5)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(1) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = CustomSearchIcon,
                        contentDescription = "Search",
                        tint = searchColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Search",
                        color = searchColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSearchSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }

                // Profile sits in middle - larger and naturally fills navbar without empty bottom space
                val isSpaceSelected = selectedTab == 4
                val decUrl = activeProfileObj?.decorationUrl ?: ""
                val hasAnimatedAvatar = (decUrl.isNotBlank() && !decUrl.equals("none", ignoreCase = true)) ||
                    avatarUrl.endsWith(".gif", ignoreCase = true) ||
                    avatarUrl.endsWith(".webp", ignoreCase = true) ||
                    avatarUrl.contains("animated", ignoreCase = true)

                Box(
                    modifier = Modifier
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val profileBoxModifier = if (hasAnimatedAvatar) {
                        Modifier
                            .offset(y = (-5).dp)
                            .size(54.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(4) }
                    } else {
                        Modifier
                            .offset(y = (-5).dp)
                            .size(52.dp)
                            .shadow(10.dp, CircleShape)
                            .clip(CircleShape)
                            .background(navBgColor)
                            .border(2.5.dp, navOutlineColor, CircleShape)
                            .padding(3.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(4) }
                    }

                    Box(
                        modifier = profileBoxModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        com.lagradost.cloudstream3.ui.animebox.profiles.DecoratedProfilePicture(
                            avatarUrl = avatarUrl,
                            decorationUrl = decUrl,
                            size = if (hasAnimatedAvatar) 54.dp else 44.dp,
                            fallbackInitial = activeName,
                            forceCircle = true
                        )
                    }
                }

                val isDownloadsSelected = selectedTab == 2
                val downloadsColor = if (isDownloadsSelected) Color.White else Color(0xFF9E9EA5)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(2) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = CustomDownloadsNavbarIcon,
                        contentDescription = "Downloads",
                        tint = downloadsColor,
                        modifier = Modifier.size(25.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Downloads",
                        color = downloadsColor,
                        fontSize = 11.sp,
                        fontWeight = if (isDownloadsSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }

                val isListSelected = selectedTab == 3
                val listColor = if (isListSelected) Color.White else Color(0xFF9E9EA5)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(3) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = CustomMyListIcon,
                        contentDescription = "My Lists",
                        tint = listColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "My Lists",
                        color = listColor,
                        fontSize = 11.sp,
                        fontWeight = if (isListSelected) FontWeight.Bold else FontWeight.Medium
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
        movieDuration: Int = 0,
        movieTitle: String = "",
        movieDescription: String = "",
        fillerEpisodes: Set<Int> = emptySet(),
        movieBackdropUrl: String = "",
        animeTitle: String = "",
        screenshotRevision: Long = 0L,
        onClick: () -> Unit,
        onDownloadClick: () -> Unit
    ) {
        val effectiveAnimeTitle = if (animeTitle.isNotEmpty()) animeTitle else movieTitle
        var imageUrl by remember(episodeNum, defaultImageUrl, movieBackdropUrl, isMovie, screenshotRevision) {
            val initial = if (isMovie && movieBackdropUrl.isNotEmpty()) {
                movieBackdropUrl
            } else if (isMovie) {
                defaultImageUrl
            } else {
                meta?.imageUrl.orEmpty()
            }
            mutableStateOf(initial)
        }

        val tmdbId = AniZipClient.getLongRunningTmdbId(anilistId)
        LaunchedEffect(episodeNum, defaultImageUrl, meta?.imageUrl, screenshotRevision) {
            val localCover = withContext(Dispatchers.IO) { com.lagradost.cloudstream3.ui.animebox.api.EpisodeCoverUploader.getLocalOrUploadedEpisodeCover(
                this@AnimeBoxDetailActivity,
                anilistId,
                episodeNum,
                effectiveAnimeTitle
            )
            }; if (!localCover.isNullOrEmpty()) {
                imageUrl = localCover
                return@LaunchedEffect
            }
            val remoteCover = withContext(Dispatchers.IO) { com.lagradost.cloudstream3.ui.animebox.api.EpisodeCoverUploader.getCustomEpisodeCover(
                this@AnimeBoxDetailActivity,
                anilistId,
                episodeNum,
                effectiveAnimeTitle
            )
            }; if (!remoteCover.isNullOrEmpty()) {
                imageUrl = remoteCover
                return@LaunchedEffect
            }

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
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF1C1C24))
            ) {
                val isSpoilerBlur = remember(isMovie) { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isBlurEpisodeSpoilersEnabled(this@AnimeBoxDetailActivity) && !isMovie }
                val shouldBlur = isMovieUsingBlurredPoster || isSpoilerBlur

                // Cover load path: episode still -> (only after a genuine load failure) series cover ->
                // clean neutral placeholder. No logo / "null" image is ever substituted.
                var failedCoverUrl by remember(episodeNum) { mutableStateOf<String?>(null) }
                val shownCoverUrl = if (isMovie) {
                    when {
                        imageUrl.isBlank() -> defaultImageUrl
                        failedCoverUrl == imageUrl && defaultImageUrl.isNotBlank() -> defaultImageUrl
                        else -> imageUrl
                    }
                } else {
                    if (failedCoverUrl == imageUrl) "" else imageUrl
                }
                val neutralCoverPainter = remember { androidx.compose.ui.graphics.painter.ColorPainter(Color(0xFF1C1C24)) }
                Image(
                    painter = rememberAsyncImagePainter(
                        model = shownCoverUrl.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
                        placeholder = neutralCoverPainter,
                        error = neutralCoverPainter,
                        fallback = neutralCoverPainter,
                        onError = { if (shownCoverUrl == imageUrl) failedCoverUrl = imageUrl }
                    ),
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

                val isEpisodeFiller = fillerEpisodes.contains(episodeNum) ||
                        com.lagradost.cloudstream3.ui.animebox.api.AnimeFillerManager.isFillerSync(anilistId, episodeNum) ||
                        meta?.isFiller == true ||
                        title.contains("filler", ignoreCase = true) ||
                        (meta?.overview?.contains("filler", ignoreCase = true) == true)

                if (isEpisodeFiller) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(bottomEnd = 8.dp))
                            .background(Color(0xFFFFCC00))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Filler",
                            color = Color.Black,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
                
                val runtimeMin = if (isMovie) {
                    if (movieDuration > 0) movieDuration
                    else if (meta?.runtime != null && meta.runtime > 0) meta.runtime
                    else 90
                } else {
                    if (meta?.runtime != null && meta.runtime > 0) meta.runtime
                    else 24
                }
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
                val epTitle = if (isMovie) {
                    if (movieTitle.isNotBlank()) movieTitle else if (!title.isBlank() && !title.startsWith("Episode ", ignoreCase = true)) title else "Movie"
                } else {
                    if (!title.isBlank() && !title.equals("null", ignoreCase = true) && !title.startsWith("Episode ", ignoreCase = true)) title else (meta?.title ?: "Episode $episodeNum")
                }
                
                Text(
                    text = if (isMovie) epTitle else "$episodeNum. $epTitle",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(3.dp))

                val overviewText = if (isMovie) {
                    val rawMovieDesc = if (movieDescription.isNotBlank()) movieDescription else (meta?.overview ?: "")
                    val cleanMovieDesc = rawMovieDesc.replace(Regex("<[^>]*>"), "").trim()
                    if (cleanMovieDesc.isNotBlank() && !cleanMovieDesc.equals("null", ignoreCase = true)) cleanMovieDesc else "Movie"
                } else {
                    if (!meta?.overview.isNullOrEmpty() && !meta!!.overview.equals("null", ignoreCase = true)) meta!!.overview else "Episode $episodeNum"
                }
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
                            imageVector = EpisodeDownloadIcon,
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
        explicitUrl: String? = null,
        detailObj: AnimeDetail? = null,
        backdropUrl: String = "",
        logoUrl: String = "",
        onAddedToLibrary: (() -> Unit)? = null
    ) {
        val isAdultPlayback = (detailObj?.isAdult == true) || intent.getBooleanExtra("isAdult", false) ||
            com.lagradost.cloudstream3.ui.animebox.api.AniListClient.isAdultAnime(anilistId, animeTitle)
        if (isAdultPlayback && !adultPlaybackWarningShown) {
            val accepted = withContext(Dispatchers.Main) {
                kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                    var answered = false
                    fun finish(allow: Boolean) {
                        if (!answered) {
                            answered = true
                            if (continuation.isActive) continuation.resumeWith(Result.success(allow))
                        }
                    }
                    val dialog = android.app.AlertDialog.Builder(this@AnimeBoxDetailActivity)
                        .setTitle("18+ Adult Content")
                        .setMessage("This anime is intended for adults and may contain explicit sexual content. Confirm that you are of legal age and want to continue.")
                        .setNegativeButton("Cancel") { _, _ -> finish(false) }
                        .setPositiveButton("I understand · Continue") { _, _ -> finish(true) }
                        .setOnCancelListener { finish(false) }
                        .create()
                    dialog.setOnDismissListener { finish(false) }
                    continuation.invokeOnCancellation { dialog.dismiss() }
                    dialog.show()
                }
            }
            if (!accepted) return
            adultPlaybackWarningShown = true
        }

        if (AnimeBoxPlayerActivity.isCurrentlyInPip || AnimeBoxYouTubePlayerActivity.isCurrentlyInPip) {
            Toast.makeText(this, "Please close Picture-in-Picture mode first to play another episode", Toast.LENGTH_SHORT).show()
            return
        }

        val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId)
        val isHentaiSeries = isAdultPlayback || com.lagradost.cloudstream3.ui.animebox.api.AniListClient.isAdultAnime(anilistId, animeTitle)
        val effectiveShowCover = when {
            isShinChan -> com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_POSTER_URL
            isHentaiSeries && anilistId == 21870 -> "https://image.tmdb.org/t/p/w500/6Csk5iIk4q9hfPPrQLl1dox9EVr.jpg"
            isHentaiSeries && anilistId == 1403 -> "https://s4.anilist.co/file/anilistcdn/media/anime/cover/medium/1403.jpg"
            isHentaiSeries && anilistId == 196750 -> "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx196750-qwkwqdNMXIwD.png"
            isHentaiSeries && !detailObj?.coverUrl.isNullOrBlank() -> detailObj!!.coverUrl
            showCoverUrl.isNotBlank() && !showCoverUrl.contains("episode") && !showCoverUrl.contains("okcdn.ru") && !showCoverUrl.contains("videoPreview") && !showCoverUrl.contains("backdrop", ignoreCase = true) && !showCoverUrl.contains("banner", ignoreCase = true) -> showCoverUrl
            else -> detailObj?.coverUrl ?: showCoverUrl
        }

        // Automatically add to "Currently Watching" in My List when clicking on an episode
        try {
            val libManager = com.lagradost.cloudstream3.ui.animebox.library.LibraryManager(this@AnimeBoxDetailActivity)
            val inLib = libManager.isInLibrary(anilistId)
            val currentCategory = if (inLib) libManager.getAnimeCategory(anilistId) else ""
            if (!inLib || currentCategory == "Currently Watching" || currentCategory.isBlank()) {
                val cleanTitle = if (isShinChan) "Crayon Shin-chan" else (detailObj?.title ?: animeTitle).substringBefore(" - Special").substringBefore(" - Episode")
                val brief = AnimeBrief(
                    id = anilistId,
                    title = cleanTitle,
                    coverUrl = effectiveShowCover,
                    bannerUrl = detailObj?.bannerUrl ?: backdropUrl,
                    description = detailObj?.description ?: "",
                    genres = detailObj?.genres ?: emptyList(),
                    averageScore = detailObj?.score ?: 0,
                    logoUrl = logoUrl,
                    episodes = if (totalEpisodes > 0) totalEpisodes else (detailObj?.episodesCount ?: 0),
                    customListCategory = "Currently Watching"
                )
                libManager.addOrUpdateLibraryItem(brief, "Currently Watching")
                withContext(Dispatchers.Main) {
                    onAddedToLibrary?.invoke()
                }
            }
        } catch (_: Exception) {}

        val effectiveBackdrop = if (isShinChan) {
            com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_BACKDROP_URL
        } else {
            val dBanner = detailObj?.bannerUrl ?: ""
            if (dBanner.isNotBlank() && dBanner.startsWith("http")) dBanner else backdropUrl
        }
        val effectiveLogo = if (isShinChan) {
            com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_LOGO_URL
        } else logoUrl

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
                val intent = Intent(this, com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getPlayerActivityClass(this)).apply {
                    putExtra("hlsUrl", shinChanRes.hlsUrl)
                    putExtra("referer", shinChanRes.referer)
                    putExtra("subtitleUrl", "")
                    putExtra("anilistId", anilistId)
                    putExtra("episode", episodeNum)
                    putExtra("animeTitle", animeTitle)
                    putExtra("coverUrl", episodeCoverUrl)
                    putExtra("showCoverUrl", effectiveShowCover)
                    putExtra("backdropUrl", effectiveBackdrop)
                    putExtra("logoUrl", effectiveLogo)
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

        com.lagradost.cloudstream3.ui.animebox.api.AnimeViewCountManager.incrementView(anilistId)

        // 1. Direct offline playback if episode is already downloaded
        val downloadedEp = com.lagradost.cloudstream3.ui.animebox.download.AnimeDownloadManager.getInstance(this).getDownloadedEpisode(anilistId, episodeNum)
        if (downloadedEp != null && downloadedEp.localFilePath.isNotEmpty() && java.io.File(downloadedEp.localFilePath).exists()) {
            val intent = Intent(this, com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getPlayerActivityClass(this)).apply {
                putExtra("localFilePath", downloadedEp.localFilePath)
                putExtra("isOffline", true)
                putExtra("animeTitle", animeTitle)
                putExtra("anilistId", anilistId)
                putExtra("episode", episodeNum)
                putExtra("showCoverUrl", effectiveShowCover)
                putExtra("coverUrl", episodeCoverUrl)
                putExtra("backdropUrl", effectiveBackdrop)
                putExtra("logoUrl", effectiveLogo)
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

        val isAdultContent = (detailObj?.isAdult == true) || intent.getBooleanExtra("isAdult", false) || com.lagradost.cloudstream3.ui.animebox.api.AniListClient.isAdultAnime(anilistId, animeTitle)
        if (isAdultContent) {
            val prefServer = com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getPreferredServer(this, "hentai-jp")
            var streamUrl = ""
            var referer = "https://hentaimama.io/"
            var subUrl = ""
            var subsJson = "[]"
            var providerName = "Hentai Mama"

            val searchAltTitles = (
                listOfNotNull(
                    detailObj?.romajiTitle,
                    animeTitle,
                    detailObj?.title,
                    detailObj?.nativeTitle,
                    animeTitle.replace("migi", "migui", ignoreCase = true),
                    animeTitle.replace("migui", "migi", ignoreCase = true)
                ) + (detailObj?.synonyms ?: emptyList())
            ).map { it.trim() }.filter { it.isNotBlank() }.distinct()

            var isHahoDubLoaded = false

            suspend fun tryHentaiMama(): Boolean {
                val hmResult = HentaiMamaExtractor.getStreamForAnime(animeTitle, episodeNum, searchAltTitles, releaseYear = detailObj?.releaseYear ?: intent.getIntExtra("initialReleaseYear", 0), totalEpisodes = detailObj?.episodesCount ?: intent.getIntExtra("initialEpisodesCount", 0), anilistId = anilistId)
                if (hmResult != null && hmResult.streamUrl.isNotEmpty()) {
                    streamUrl = hmResult.streamUrl
                    referer = hmResult.referer
                    providerName = hmResult.provider
                    return true
                }
                return false
            }

            suspend fun tryReAnime(): Boolean {
                val reAnime = try {
                    com.lagradost.cloudstream3.ui.animebox.extractors.ReAnimeExtractor.extract(anilistId, episodeNum, "sub")
                } catch (_: Exception) { null }
                if (reAnime != null && reAnime.hlsUrl.isNotBlank()) {
                    streamUrl = reAnime.hlsUrl
                    referer = reAnime.referer
                    subUrl = reAnime.subtitleUrl
                    subsJson = org.json.JSONArray().apply {
                        reAnime.subtitles.forEach { put(org.json.JSONObject(it)) }
                    }.toString()
                    providerName = reAnime.provider
                    return true
                }
                return false
            }

            suspend fun tryHahoMoe(): Boolean {
                val primaryHahoTitle = detailObj?.romajiTitle?.takeIf { it.isNotBlank() } ?: animeTitle
                val haho = try {
                    com.lagradost.cloudstream3.ui.animebox.extractors.HahoMoeExtractor.getStream(
                        title = primaryHahoTitle,
                        episodeNum = episodeNum,
                        preferDub = type.contains("dub", ignoreCase = true),
                        searchTitles = searchAltTitles
                    )
                } catch (_: Exception) { null }
                if (haho != null && haho.streamUrl.isNotBlank()) {
                    streamUrl = haho.streamUrl
                    referer = haho.referer
                    providerName = "Haho Moe"
                    isHahoDubLoaded = haho.isDub
                    return true
                }
                return false
            }

            suspend fun tryHentaiHaven(): Boolean {
                val hhResult = HentaiHavenExtractor.getStreamForAnime(animeTitle, episodeNum, searchAltTitles)
                if (hhResult != null && hhResult.streamUrl.isNotEmpty()) {
                    streamUrl = hhResult.streamUrl
                    referer = hhResult.referer
                    subUrl = hhResult.subtitleUrl
                    subsJson = hhResult.subtitlesJson
                    providerName = hhResult.provider
                    return true
                }
                return false
            }

            val isHiiroNiKoku = anilistId == 4731 || animeTitle.contains("Hiiro no Koku", ignoreCase = true) || animeTitle.contains("Hiiro ni Koku", ignoreCase = true) || animeTitle.contains("The Time Colored Scarlet", ignoreCase = true)
            val runners = when {
                isHiiroNiKoku || prefServer.equals("hahomoe", ignoreCase = true) || type.contains("dub", ignoreCase = true) ->
                    listOf(::tryHahoMoe, ::tryHentaiMama, ::tryReAnime, ::tryHentaiHaven)
                prefServer.equals("reanime", ignoreCase = true) ->
                    listOf(::tryReAnime, ::tryHentaiMama, ::tryHahoMoe, ::tryHentaiHaven)
                prefServer.equals("hentaihaven", ignoreCase = true) ->
                    listOf(::tryHentaiHaven, ::tryHentaiMama, ::tryHahoMoe, ::tryReAnime)
                else ->
                    listOf(::tryHentaiMama, ::tryHahoMoe, ::tryReAnime, ::tryHentaiHaven)
            }

            for (runner in runners) {
                if (runner()) break
            }

            if (streamUrl.isNotEmpty()) {
                val actualAdultStreamType = if (type.contains("dub", ignoreCase = true) && isHahoDubLoaded) "dub" else "sub"
                val actualAdultAudioLabel = if (actualAdultStreamType == "dub") "English" else "Japanese (Original)"
                val playIntent = Intent(this, com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getPlayerActivityClass(this)).apply {
                    putExtra("hlsUrl", streamUrl)
                    putExtra("referer", referer)
                    putExtra("subtitleUrl", subUrl)
                    putExtra("subtitlesJson", subsJson)
                    putExtra("provider", providerName)
                    putExtra("anilistId", anilistId)
                    putExtra("episode", episodeNum)
                    putExtra("animeTitle", animeTitle)
                    putExtra("coverUrl", episodeCoverUrl)
                    putExtra("showCoverUrl", effectiveShowCover)
                    putExtra("backdropUrl", effectiveBackdrop)
                    putExtra("logoUrl", effectiveLogo)
                    putExtra("totalEpisodes", totalEpisodes)
                    putExtra("streamType", actualAdultStreamType)
                    putExtra("activeAudio", actualAdultAudioLabel)
                    putStringArrayListExtra("altTitles", ArrayList(searchAltTitles))
                    putExtra("isAdult", true)
                    putExtra("fromContinueWatching", savedProgress > 0L)
                }
                startActivity(playIntent)
                return
            } else {
                Toast.makeText(this, "No source available for this hentai episode", Toast.LENGTH_SHORT).show()
                return
            }
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
        if (streamInfo == null && actualType != "raw") {
            val rawInfo = com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(this, anilistId, episodeNum, "raw", animeTitle)
            if (rawInfo != null) {
                streamInfo = rawInfo
                actualType = "raw"
                showFallbackDialogInPlayer = false
            }
        }

        if (streamInfo != null && (streamInfo["hls"] as? String)?.isNotEmpty() == true) {
            if (AnimeBoxPlayerActivity.isCurrentlyInPip || AnimeBoxYouTubePlayerActivity.isCurrentlyInPip) {
                Toast.makeText(this, "Please close Picture-in-Picture mode first to play another episode", Toast.LENGTH_SHORT).show()
                return
            }
            // Check for saved progress to show resume dialog in player
            val histManager = WatchHistoryManager(this)
            val savedProgress = histManager.getSavedProgress(anilistId, episodeNum)
            val intent = Intent(this, com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getPlayerActivityClass(this)).apply {
                putExtra("hlsUrl", (streamInfo["hls"] as? String) ?: "")
                putExtra("referer", (streamInfo["referer"] as? String) ?: "https://megaplay.buzz/")
                putExtra("subtitleUrl", (streamInfo["subtitle"] as? String) ?: "")
                putExtra("subtitlesJson", (streamInfo["subtitlesJson"] as? String) ?: "[]")
                putExtra("introStart", (streamInfo["introStart"] as? Number)?.toLong() ?: 0L)
                putExtra("introEnd", (streamInfo["introEnd"] as? Number)?.toLong() ?: 0L)
                putExtra("outroStart", (streamInfo["outroStart"] as? Number)?.toLong() ?: 0L)
                putExtra("outroEnd", (streamInfo["outroEnd"] as? Number)?.toLong() ?: 0L)
                putExtra("anilistId", anilistId)
                putExtra("episode", episodeNum)
                putExtra("animeTitle", animeTitle)
                putExtra("coverUrl", episodeCoverUrl)
                putExtra("showCoverUrl", effectiveShowCover)
                putExtra("backdropUrl", effectiveBackdrop)
                putExtra("logoUrl", effectiveLogo)
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

            var episodes = if (obj.has("episodes") && !obj.isNull("episodes")) {
                obj.getInt("episodes")
            } else {
                0
            }

            val score = if (obj.has("averageScore") && !obj.isNull("averageScore")) {
                obj.getInt("averageScore")
            } else 0

            val popularity = obj.optInt("popularity", 0)
            val favourites = obj.optInt("favourites", 0)

            val genresList = mutableListOf<String>()
            if (obj.has("genres") && !obj.isNull("genres")) {
                val genresArray = obj.getJSONArray("genres")
                for (i in 0 until genresArray.length()) {
                    genresList.add(genresArray.getString(i))
                }
            }

            val tagsList = mutableListOf<String>()
            if (obj.has("tags") && !obj.isNull("tags")) {
                val tagsArray = obj.getJSONArray("tags")
                for (i in 0 until tagsArray.length()) {
                    val tagObj = tagsArray.optJSONObject(i)
                    val tagName = tagObj?.optString("name", "") ?: ""
                    if (tagName.isNotBlank() && !tagsList.contains(tagName)) {
                        tagsList.add(tagName)
                    }
                }
            }

            val synonymsList = mutableListOf<String>()
            if (obj.has("synonyms") && !obj.isNull("synonyms")) {
                val synArray = obj.getJSONArray("synonyms")
                for (i in 0 until synArray.length()) {
                    val s = synArray.optString(i, "").trim()
                    if (s.isNotBlank() && !synonymsList.contains(s)) {
                        synonymsList.add(s)
                    }
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

            // AniList already returns character and voice actor data in the detail response.
            // Keep parsing local so the first render never waits on a second character service.
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
                            val relStatus = node.optString("status", "")
                            val relEpisodes = if (node.has("episodes") && !node.isNull("episodes")) node.getInt("episodes") else null
                            relationsList.add(AnimeRelation(relId, relTitle, relCover, relType, format, relStatus, relEpisodes))
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
                            val recStatus = media.optString("status", "")
                            val recEpisodes = if (media.has("episodes") && !media.isNull("episodes")) media.getInt("episodes") else null
                            recommendationsList.add(AnimeRecommendation(recId, recTitle, recCover, format, recStatus, recEpisodes))
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
            // AniList's episode total is often the planned season length while a show is airing.
            // The next scheduled episode is the reliable upper bound for episodes available now.
            if (status.equals("RELEASING", ignoreCase = true) && nextAiring != null && nextAiring.airingAt > System.currentTimeMillis() / 1000L) {
                val alreadyAired = (nextAiring.episode - 1).coerceAtLeast(0)
                episodes = if (episodes > 0) minOf(episodes, alreadyAired) else alreadyAired
            }
            val format = if (obj.has("format") && !obj.isNull("format")) obj.getString("format") else ""
            val rawDuration = if (obj.has("duration") && !obj.isNull("duration")) obj.getInt("duration") else 0
            val duration = if (rawDuration > 0) rawDuration else if (format.equals("MOVIE", ignoreCase = true) || episodes == 1 || com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.isStaticMovie(id)) 90 else 0
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
                nextAiring = nextAiring,
                popularity = popularity,
                favourites = favourites,
                duration = duration,
                tags = tagsList,
                nativeTitle = nativeTitle,
                romajiTitle = rom,
                synonyms = synonymsList
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
                val builder = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                val client = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper.applyDns(builder, this@AnimeBoxDetailActivity).build()
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
                val builder = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                val client = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper.applyDns(builder, this@AnimeBoxDetailActivity).build()
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
        name = "SolarSortIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M16 18L16 6M12 10.125L16 6L20 10.125M8 6L8 18M4 13.875L8 18L12 13.875").toNodes(),
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ).build()


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

val StarHalfIcon: ImageVector
    get() = ImageVector.Builder(
        name = "StarHalf",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White)
    ) {
        moveTo(12f, 2f)
        lineTo(9.19f, 8.63f)
        lineTo(2f, 9.24f)
        lineTo(7.46f, 13.97f)
        lineTo(5.82f, 21f)
        lineTo(12f, 17.27f)
        lineTo(12f, 2f)
        close()
    }.build()

val ViewsEyeIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ViewsEye",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(1f, 12f)
        curveTo(3.5f, 6f, 8f, 4f, 12f, 4f)
        curveTo(16f, 4f, 20.5f, 6f, 23f, 12f)
        curveTo(20.5f, 18f, 16f, 20f, 12f, 20f)
        curveTo(8f, 20f, 3.5f, 18f, 1f, 12f)
        close()
        moveTo(12f, 9f)
        curveTo(10.34f, 9f, 9f, 10.34f, 9f, 12f)
        curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
        curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
        curveTo(15f, 10.34f, 13.66f, 9f, 12f, 9f)
        close()
    }.build()

val EpisodeDownloadIcon: ImageVector
    get() = ImageVector.Builder(
        name = "EpisodeDownload",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2.0f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(12f, 3f)
        lineTo(12f, 15f)
        moveTo(12f, 15f)
        lineTo(8f, 11f)
        moveTo(12f, 15f)
        lineTo(16f, 11f)
        moveTo(4f, 17f)
        verticalLineTo(19f)
        curveTo(4f, 20.1f, 4.9f, 21f, 6f, 21f)
        horizontalLineTo(18f)
        curveTo(19.1f, 21f, 20f, 20.1f, 20f, 19f)
        verticalLineTo(17f)
    }.build()

// Solar download-minimalistic-bold for Download sheets and Empty states
val CloudDownloadIcon: ImageVector
    get() = CustomDownloadsNavbarBoldIcon

// Grid Box View Icon
val GridViewIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SolarGridView",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M2.5 6.5C2.5 4.29086 4.29086 2.5 6.5 2.5C8.70914 2.5 10.5 4.29086 10.5 6.5C10.5 8.70914 8.70914 10.5 6.5 10.5C4.29086 10.5 2.5 8.70914 2.5 6.5ZM13.5 17.5C13.5 15.2909 15.2909 13.5 17.5 13.5C19.7091 13.5 21.5 15.2909 21.5 17.5C21.5 19.7091 19.7091 21.5 17.5 21.5C15.2909 21.5 13.5 19.7091 13.5 17.5ZM21.5 6.5C21.5 4.61438 21.5 3.67157 20.9142 3.08579C20.3284 2.5 19.3856 2.5 17.5 2.5C15.6144 2.5 14.6716 2.5 14.0858 3.08579C13.5 3.67157 13.5 4.61438 13.5 6.5C13.5 8.38562 13.5 9.32843 14.0858 9.91421C14.6716 10.5 15.6144 10.5 17.5 10.5C19.3856 10.5 20.3284 10.5 20.9142 9.91421C21.5 9.32843 21.5 8.38562 21.5 6.5ZM10.5 17.5C10.5 15.6144 10.5 14.6716 9.91421 14.0858C9.32843 13.5 8.38562 13.5 6.5 13.5C4.61438 13.5 3.67157 13.5 3.08579 14.0858C2.5 14.6716 2.5 15.6144 2.5 17.5C2.5 19.3856 2.5 20.3284 3.08579 20.9142C3.67157 21.5 4.61438 21.5 6.5 21.5C8.38562 21.5 9.32843 21.5 9.91421 20.9142C10.5 20.3284 10.5 19.3856 10.5 17.5Z").toNodes(),
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ).build()

// List / Image View Icon (Solar List Linear)
val ListViewIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SolarListView",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M20 7L4 7M15 12L4 12M9 17H4").toNodes(),
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ).build()

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

// ─── Watch Order Modal & AI Assistant ─────────────────────────────

private data class AnimeReviewItem(
    val id: Int,
    val user: String,
    val avatarUrl: String,
    val score: Int,
    val summary: String,
    val body: String,
    val isSpoiler: Boolean
)

@Composable
private fun AnimeReviewsDialog(animeId: Int, animeTitle: String, onDismiss: () -> Unit) {
    com.lagradost.cloudstream3.ui.animebox.comments.DantotsuCommentsDialog(
        animeId = animeId,
        animeTitle = animeTitle,
        onDismiss = onDismiss
    )
}

@Composable
private fun AnimeReviewCommentsPanel(animeId: Int, animeTitle: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val identity = remember(context) { com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewCommentsManager.identity(context) }
    var comments by remember(animeId) { mutableStateOf<List<com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewComment>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var replyingTo by remember { mutableStateOf<com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewComment?>(null) }
    var selectedSharedCollection by remember { mutableStateOf<com.lagradost.cloudstream3.ui.animebox.library.AnimeCustomCollection?>(null) }
    var loading by remember(animeId) { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedImages = uris.take(4)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val loadComments: suspend () -> Unit = {
        val fetched = com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewCommentsManager.fetch(animeId)
        if (fetched != comments) {
            comments = fetched
        }
        error = com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewCommentsManager.lastError
        loading = false
    }
    LaunchedEffect(animeId) {
        loadComments()
        while (true) {
            kotlinx.coroutines.delay(20_000)
            loadComments()
        }
    }
    val commentById = remember(comments) { comments.associateBy { it.id } }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (error != null) {
            Text(
                text = if (error!!.contains("anime_review_comments", true) || error!!.contains("404"))
                    "Comments need the Supabase setup script to be run. See AnimeBox-Shareable/database/anime_review_comments.sql."
                else error!!,
                color = Color(0xFFFFB4AB), fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF302020)).padding(11.dp)
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${comments.size} comments · ${if (identity.type == "anilist") "@${identity.name}" else identity.name}", color = Color(0xFF9D9DA6), fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("Refresh", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF24252B)).clickable { scope.launch { loading = true; loadComments() } }.padding(horizontal = 10.dp, vertical = 7.dp))
        }
        var expandedThreads by remember { mutableStateOf(setOf<String>()) }
        val repliesByParent = remember(comments) { comments.filter { it.parentId != null }.groupBy { it.parentId!! } }
        val rootComments = remember(comments) {
            val roots = comments.filter { it.parentId == null }
            val orphans = comments.filter { it.parentId != null && it.parentId !in commentById }
            roots + orphans
        }

        when {
            loading && comments.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                com.lagradost.cloudstream3.ui.animebox.components.ThemeDotCircularProgressIndicator(color = Color.White, size = 36.dp)
            }
            comments.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Start the conversation about this anime.", color = Color(0xFF8F8F98), fontSize = 13.sp)
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(rootComments, key = { it.id }) { rootComment ->
                    AnimeReviewCommentThreadNode(
                        comment = rootComment,
                        repliesByParent = repliesByParent,
                        commentById = commentById,
                        viewerIdentity = identity,
                        expandedThreads = expandedThreads,
                        onToggleExpand = { cId ->
                            expandedThreads = if (expandedThreads.contains(cId)) expandedThreads - cId else expandedThreads + cId
                        },
                        onReply = { replyingTo = it }
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1C21)).padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            replyingTo?.let { target ->
                val targetIsAdmin = target.authorType == "admin" || target.authorName.equals("Firefly", ignoreCase = true) || target.authorName.equals("AnimeBox Admin", ignoreCase = true) || target.authorName.equals("AnimeBox Team", ignoreCase = true)
                val targetName = if (targetIsAdmin) "Firefly" else target.authorName
                val snippet = target.body.replace('\n', ' ').trim().let { if (it.length > 35) "${it.take(35)}…" else it }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF262830))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Replying to $targetName${if (snippet.isNotBlank()) ": \"$snippet\"" else ""}",
                        color = Color(0xFFD0D0D5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "✕",
                        color = Color(0xFF9D9DA6),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { replyingTo = null }
                            .padding(4.dp)
                    )
                }
            }
            androidx.compose.foundation.text.BasicTextField(
                value = draft,
                onValueChange = { draft = it.take(4000) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp, max = 120.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp, lineHeight = 19.sp),
                decorationBox = { inner ->
                    Box {
                        if (draft.isBlank()) Text("Write a comment…", color = Color(0xFF73757E), fontSize = 13.sp)
                        inner()
                    }
                }
            )
            if (selectedImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    selectedImages.forEachIndexed { idx, uri ->
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2B2D35))
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(uri),
                                contentDescription = "Attachment preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Remove image '✕' icon
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .clickable {
                                        selectedImages = selectedImages.filterIndexed { i, _ -> i != idx }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✕", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = "${selectedImages.size}/4 attached",
                        color = Color(0xFF9D9DA6),
                        fontSize = 10.sp
                    )
                }
            }
            val availableCollections = remember(context, com.lagradost.cloudstream3.ui.animebox.library.LibraryManager.libraryRevisionFlow.collectAsState().value) {
                com.lagradost.cloudstream3.ui.animebox.library.LibraryManager(context).getCollections()
            }
            if (availableCollections.isNotEmpty()) {
                Text("Share one of your collections", color = Color(0xFF9D9DA6), fontSize = 10.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableCollections.forEach { collection ->
                        val selected = selectedSharedCollection?.id == collection.id
                        Text(collection.name, color = if (selected) Color.White else Color(0xFFB0B2B8), fontSize = 10.sp,
                            modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (selected) Color(0xFF54575E) else Color(0xFF292B30))
                                .clickable { selectedSharedCollection = if (selected) null else collection }
                                .padding(horizontal = 9.dp, vertical = 6.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("+ Image / GIF", color = Color(0xFFBBBBc4), fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { imagePicker.launch("image/*") }.padding(horizontal = 9.dp, vertical = 7.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (sending) "Posting…" else (if (replyingTo == null) "Comment" else "Reply"),
                    color = if (sending || (draft.isBlank() && selectedImages.isEmpty())) Color(0xFF777982) else Color.White,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFF33353D))
                        .clickable(enabled = !sending && (draft.isNotBlank() || selectedImages.isNotEmpty())) {
                            scope.launch {
                                sending = true
                                error = null
                                try {
                                    val imageUrls = selectedImages.mapNotNull {
                                        com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewCommentsManager.uploadImage(context, it)
                                    }
                                    if (selectedImages.isNotEmpty() && imageUrls.size != selectedImages.size) {
                                        error = "Image upload failed. Try another image or GIF."
                                    } else {
                                        val posted = com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewCommentsManager.post(
                                            mediaId = animeId,
                                            animeTitle = animeTitle,
                                            identity = identity,
                                            body = draft,
                                            imageUrls = imageUrls,
                                            parentId = replyingTo?.id,
                                            sharedCollection = selectedSharedCollection?.let {
                                                com.lagradost.cloudstream3.ui.animebox.library.LibraryManager(context).collectionSharePayload(it)
                                            }
                                        )
                                        if (posted != null) {
                                            comments = comments + posted
                                            draft = ""
                                            selectedImages = emptyList()
                                            replyingTo = null
                                            selectedSharedCollection = null
                                        } else {
                                            val postError = com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewCommentsManager.lastError ?: "Could not post comment."
                                            if (replyingTo != null && postError.contains("target does not exist", ignoreCase = true)) {
                                                comments = com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewCommentsManager.fetch(animeId)
                                                replyingTo = null
                                                error = "That comment is no longer available. The conversation has been refreshed."
                                            } else error = postError
                                        }
                                    }
                                } catch (e: Exception) {
                                    error = e.localizedMessage ?: "Could not post comment."
                                } finally {
                                    sending = false
                                }
                            }
                        }.padding(horizontal = 13.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AnimeReviewCommentThreadNode(
    comment: com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewComment,
    repliesByParent: Map<String, List<com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewComment>>,
    commentById: Map<String, com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewComment>,
    viewerIdentity: com.lagradost.cloudstream3.ui.animebox.reviews.ReviewCommentIdentity,
    expandedThreads: Set<String>,
    onToggleExpand: (String) -> Unit,
    onReply: (com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewComment) -> Unit
) {
    val childReplies = repliesByParent[comment.id].orEmpty()
    val isExpanded = expandedThreads.contains(comment.id)
    val parent = comment.parentId?.let(commentById::get)

    fun totalReplies(cId: String): Int {
        val children = repliesByParent[cId].orEmpty()
        return children.size + children.sumOf { totalReplies(it.id) }
    }
    val totalCount = remember(childReplies, repliesByParent) { totalReplies(comment.id) }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimeReviewCommentCard(
            comment = comment,
            parent = parent,
            viewerIdentity = viewerIdentity,
            onReply = { onReply(comment) }
        )

        // Show/Hide Replies Toggle (Reddit / modern social media style with ⊖ and ⊕)
        if (childReplies.isNotEmpty()) {
            val replyColor = if (isExpanded) Color(0xFFA5A5BA) else Color(0xFFA78BFA)
            Row(
                modifier = Modifier
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleExpand(comment.id) }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(width = 11.dp, height = 9.dp)) {
                    val path = androidx.compose.ui.graphics.Path()
                    if (isExpanded) {
                        path.moveTo(0f, size.height)
                        path.lineTo(size.width, size.height)
                        path.lineTo(size.width / 2f, 0f)
                    } else {
                        path.moveTo(0f, 0f)
                        path.lineTo(size.width, 0f)
                        path.lineTo(size.width / 2f, size.height)
                    }
                    path.close()
                    drawPath(path, color = replyColor)
                }
                Text(
                    text = if (isExpanded) "Hide ${totalCount} ${if (totalCount == 1) "reply" else "replies"}"
                           else "View ${totalCount} ${if (totalCount == 1) "reply" else "replies"}",
                    color = replyColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Replies with Reddit-style String System (Curved connector elbow branches)
        if (isExpanded && childReplies.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp)
            ) {
                childReplies.forEachIndexed { index, child ->
                    val isLastChild = index == childReplies.lastIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                val strokeW = 1.8.dp.toPx()
                                val lineColor = Color(0x668B5CF6) // sleek subtle thread line
                                val startX = 8.dp.toPx()
                                val curveR = 8.dp.toPx()
                                val branchY = 22.dp.toPx()

                                if (!isLastChild) {
                                    drawLine(
                                        color = lineColor,
                                        start = androidx.compose.ui.geometry.Offset(startX, 0f),
                                        end = androidx.compose.ui.geometry.Offset(startX, size.height),
                                        strokeWidth = strokeW
                                    )
                                } else {
                                    drawLine(
                                        color = lineColor,
                                        start = androidx.compose.ui.geometry.Offset(startX, 0f),
                                        end = androidx.compose.ui.geometry.Offset(startX, (branchY - curveR).coerceAtLeast(0f)),
                                        strokeWidth = strokeW
                                    )
                                }

                                val branchPath = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(startX, (branchY - curveR).coerceAtLeast(0f))
                                    quadraticBezierTo(startX, branchY, startX + curveR, branchY)
                                    lineTo(22.dp.toPx(), branchY)
                                }
                                drawPath(
                                    path = branchPath,
                                    color = lineColor,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
                                )
                            }
                            .padding(start = 22.dp, bottom = 6.dp)
                    ) {
                        AnimeReviewCommentThreadNode(
                            comment = child,
                            repliesByParent = repliesByParent,
                            commentById = commentById,
                            viewerIdentity = viewerIdentity,
                            expandedThreads = expandedThreads,
                            onToggleExpand = onToggleExpand,
                            onReply = onReply
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeReviewCommentCard(
    comment: com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewComment,
    parent: com.lagradost.cloudstream3.ui.animebox.reviews.AnimeReviewComment?,
    viewerIdentity: com.lagradost.cloudstream3.ui.animebox.reviews.ReviewCommentIdentity,
    onReply: () -> Unit
) {
    val context = LocalContext.current
    val isAdmin = comment.authorType == "admin" || comment.authorName.equals("Firefly", ignoreCase = true) || comment.authorName.equals("AnimeBox Admin", ignoreCase = true) || comment.authorName.equals("AnimeBox Team", ignoreCase = true)
    val displayName = if (isAdmin) "Firefly" else comment.authorName

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .background(if (isAdmin) Color(0xFF22252B) else Color(0xFF1A1B20)).padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isAdmin) {
                Image(
                    painter = painterResource(id = R.drawable.adminlogoticketchat),
                    contentDescription = "Firefly Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(30.dp).clip(CircleShape)
                )
            } else {
                val ownAvatar = comment.authorId == viewerIdentity.id
                val avatar = if (ownAvatar) viewerIdentity.avatarUrl else comment.avatarUrl
                com.lagradost.cloudstream3.ui.animebox.profiles.DecoratedProfilePicture(
                    avatarUrl = avatar,
                    decorationUrl = "",
                    size = 30.dp,
                    forceCircle = true,
                    fallbackInitial = displayName
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isAdmin) {
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3B82F6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_check_24),
                                contentDescription = "Verified Admin",
                                tint = Color.White,
                                modifier = Modifier.size(8.5.dp)
                            )
                        }
                    }
                }
                val displayId = when {
                    isAdmin -> "Verified Admin"
                    comment.authorType == "anilist" -> "AniList ID ${comment.authorId.removePrefix("anilist_")}"
                    else -> "Guest ID ${comment.authorId.takeLast(7)}"
                }
                val formattedTime = remember(comment.createdAt) { comment.formattedDateTime() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(displayId, color = if (isAdmin) Color(0xFF60A5FA) else Color(0xFF8D8F98), fontSize = 9.5.sp)
                    if (formattedTime.isNotBlank()) {
                        Text("•", color = Color(0xFF55555F), fontSize = 9.sp)
                        Text(formattedTime, color = Color(0xFF8D8F98), fontSize = 9.5.sp)
                    }
                }
            }
            Text("Reply", color = Color(0xFFBBBBc4), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xFF2B2C32)).clickable(onClick = onReply).padding(horizontal = 8.dp, vertical = 5.dp))
        }
        if (parent != null) {
            val parentIsAdmin = parent.authorType == "admin" || parent.authorName.equals("Firefly", ignoreCase = true) || parent.authorName.equals("AnimeBox Admin", ignoreCase = true) || parent.authorName.equals("AnimeBox Team", ignoreCase = true)
            val parentName = if (parentIsAdmin) "Firefly" else parent.authorName
            val snippet = parent.body.replace('\n', ' ').trim().let { if (it.length > 40) "${it.take(40)}…" else it }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x33282A33))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("↳", color = Color(0xFF9E9FA6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Replying to $parentName${if (snippet.isNotBlank()) ": \"$snippet\"" else ""}",
                    color = Color(0xFF9E9FA6),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (comment.body.isNotBlank()) Text(buildAnnotatedMarkdownString(reviewTextForDisplay(comment.body)), color = Color(0xFFD0D0D5), fontSize = 12.sp, lineHeight = 17.sp)
        comment.sharedCollectionJson?.let { rawCollection ->
            val collection = remember(rawCollection) { runCatching { org.json.JSONObject(rawCollection) }.getOrNull() }
            if (collection != null) {
                val titles = remember(rawCollection) {
                    collection.optJSONArray("anime")?.let { rows ->
                        (0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index) }
                    }.orEmpty()
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFF24262B)).padding(9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Collection · ${collection.optString("name", "Anime picks")}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (titles.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        titles.take(20).forEach { title ->
                            Column(Modifier.width(64.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val cover = title.optString("cover")
                                if (cover.startsWith("https://") || cover.startsWith("http://")) {
                                    Image(rememberAsyncImagePainter(cover), contentDescription = title.optString("title"), contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(7.dp)))
                                } else Box(Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF363940)), contentAlignment = Alignment.Center) {
                                    Text("${title.optInt("id")}", color = Color(0xFFB9BBC1), fontSize = 10.sp)
                                }
                                Text(
                                    text = title.optString("title").trim(),
                                    color = Color(0xFFD1D2D7),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else Text("This collection has no shared titles yet.", color = Color(0xFF9D9FA6), fontSize = 10.sp)
                }
            }
        }
        comment.imageUrls.forEach { url ->
            Image(rememberAsyncImagePainter(url), contentDescription = "Comment attachment", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 260.dp).clip(RoundedCornerShape(10.dp)).clickable { openReviewUrl(context, url) })
        }
        val commentYtId = remember(comment.body) {
            val words = comment.body.split(Regex("\\s+"))
            words.firstNotNullOfOrNull { word -> reviewYoutubeId(word.trim('(', ')', '[', ']', '<', '>', '"', '\'')) }
        }
        if (commentYtId != null) {
            val cachedInfo = remember(commentYtId) {
                com.lagradost.cloudstream3.ui.animebox.api.YouTubeMetadataManager.getCached(commentYtId)
            }
            AiYouTubeVideoCard(
                video = com.lagradost.cloudstream3.ui.animebox.api.AiVideoResult(
                    videoId = commentYtId,
                    title = cachedInfo?.title ?: "YouTube Video Attachment",
                    channel = cachedInfo?.authorName ?: "YouTube",
                    videoUrl = "https://www.youtube.com/watch?v=$commentYtId",
                    thumbnailUrl = cachedInfo?.thumbnailUrl ?: "https://i.ytimg.com/vi/$commentYtId/hqdefault.jpg",
                    channelAvatarUrl = cachedInfo?.authorAvatarUrl.orEmpty()
                )
            )
        }
    }
}

private fun cleanAniListReviewMarkdown(raw: String): String {
    if (raw.isBlank()) return ""
    var text = raw.replace("\r\n", "\n").replace("\r", "\n")

    // Strip AniList spoiler syntax wrappers ~!... !~
    text = text.replace(Regex("~!(.*?)!~", RegexOption.DOT_MATCHES_ALL), "$1")

    // Convert AniList custom image tags img220(url), img420(url), img(url), img100%(url), img(width, url) to markdown images
    // Handles any preceding or trailing tildes like ~~~~~img420(...)~~~~~ or ~~~ img420(...) ~~~
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

    // Clean horizontal rule and decorative divider lines (e.g. `## ----`, `---`, `_____`, `===`, `••••`, `~~~~`)
    text = text.replace(Regex("""(?m)^\s*#{0,6}\s*[-=_*~•]{2,}\s*$"""), "")
    text = text.replace(Regex("""(?m)^\s*[-=_*~•]{2,}\s*$"""), "")
    text = text.replace(Regex("""(?m)^\s*_{2,}\s*$"""), "")

    // Clean noise dividers at the start
    text = text.replace(Regex("""^(?:\s*(?:#{1,6}\s*)?[-=_*~•]{2,}\s*[\n\r]*)+"""), "")
    text = text.replace(Regex("""^(?:\s*#{1,6}\s*(?:Review|Overview|Introduction|Summary|Thoughts)\s*[\n\r]*)+""", RegexOption.IGNORE_CASE), "")
    text = text.replace(Regex("""^(?:\s*(?:#{1,6}\s*)?[-=_*~•]{2,}\s*[\n\r]*)+"""), "")

    // Clean inline trailing dash/underscore/tilde artifacts like `My Review of "..."--` or `text ----` or `header ___`
    text = text.replace(Regex("""(?<=\w)["'”’)]?\s*[-=_*~•]{2,}"""), "")

    // Strip trailing divider noise or redundant score/conclusion headers at the very end
    text = text.replace(Regex("""(?:\s*(?:#{1,6}\s*)?[-=_*~•]{2,}\s*[\n\r]*)+$"""), "")

    // Collapse excessive blank lines (more than 1 empty line between text)
    text = text.replace(Regex("""\n[ \t]*\n[ \t]*\n+"""), "\n\n")

    return text.trim()
}

private fun reviewBodyToMarkdown(raw: String): String {
    val cleanedRaw = cleanAniListReviewMarkdown(raw)
    val htmlPattern = Regex("(?is)</?(?:p|div|br|hr|a|img|span|strong|b|em|i|ul|ol|li|blockquote|h[1-6]|code|pre)\\b[^>]*>")
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
        .replace(Regex("(?m)^\\s*#{1,6}\\s*[-=_*~•]{2,}\\s*$"), "")
        .replace(Regex("(?m)^\\s*(?:-{2,}|_{2,}|\\*{2,}|={2,}|~{2,}|•{2,})\\s*$"), "")
        .replace(Regex("(?m)^\\s*#{1,6}\\s+(.+?)\\s*#*\\s*$")) { "**${it.groupValues[1].trim()}**" }
        .replace(Regex("(?m)^\\s*(\\d+[.)])\\s+"), "◦ $1 ")
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "◦ ")
        .replace(Regex("(?m)^\\s*>\\s?"), "│ ")
        .replace(Regex("(?m)^\\s*```[^\\n]*\\n|^\\s*```\\s*$"), "")
        .replace(Regex("__([^_]+)__"), "**$1**")
        .replace(Regex("_([^_\\n]+)_"), "*$1*")
        .replace(Regex("~~(.*?)~~"), "$1")
        .replace(Regex("~{2,}"), " ")
        .replace(Regex("""\n[ \t]*\n[ \t]*\n+"""), "\n\n")
        .trim()
}

private fun isSafeReviewUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

private sealed interface ReviewBodyPart {
    data class Text(val value: String) : ReviewBodyPart
    data class Image(val url: String, val label: String) : ReviewBodyPart
    data class YouTube(val url: String, val videoId: String, val label: String) : ReviewBodyPart
    data class Link(val url: String, val label: String, val host: String) : ReviewBodyPart
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
                                color = Color(0xFFD6D6DF), fontSize = 12.5.sp, lineHeight = 19.sp,
                                maxLines = if (expanded) Int.MAX_VALUE else 5,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                is ReviewBodyPart.Image -> if (expanded || !previewEmbedRendered) {
                    previewEmbedRendered = true
                    Image(
                        painter = rememberAsyncImagePainter(part.url),
                        contentDescription = part.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(12.dp))
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

@Composable
private fun YouTubeReviewEmbed(part: ReviewBodyPart.YouTube, onOpenUrl: (String) -> Unit) {
    val initialTitle = if (part.label.isNotBlank() && part.label != part.url) part.label else "Watch video on YouTube"
    val cachedInfo = remember(part.videoId) {
        com.lagradost.cloudstream3.ui.animebox.api.YouTubeMetadataManager.getCached(part.videoId)
    }
    AiYouTubeVideoCard(
        video = com.lagradost.cloudstream3.ui.animebox.api.AiVideoResult(
            videoId = part.videoId,
            title = cachedInfo?.title ?: initialTitle,
            channel = cachedInfo?.authorName ?: "YouTube",
            videoUrl = part.url,
            thumbnailUrl = cachedInfo?.thumbnailUrl ?: "https://i.ytimg.com/vi/${part.videoId}/hqdefault.jpg",
            channelAvatarUrl = cachedInfo?.authorAvatarUrl.orEmpty()
        )
    )
}

@Composable
private fun YouTubeMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRoundRect(Color(0xFFE62117), cornerRadius = CornerRadius(size.height * .27f))
        val play = Path().apply {
            moveTo(size.width * .43f, size.height * .25f)
            lineTo(size.width * .43f, size.height * .75f)
            lineTo(size.width * .72f, size.height * .5f)
            close()
        }
        drawPath(play, Color.White)
    }
}

@Composable
private fun ReviewLinkPreview(part: ReviewBodyPart.Link, onOpenUrl: (String) -> Unit) {
    val faviconUrl = remember(part.host) { "https://www.google.com/s2/favicons?domain=${Uri.encode(part.host)}&sz=64" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E1E22))
            .clickable { onOpenUrl(part.url) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painter = rememberAsyncImagePainter(faviconUrl), contentDescription = "${part.host} icon", contentScale = ContentScale.Fit,
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(part.label, color = Color(0xFFE5E5EA), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(part.host, color = Color(0xFFA5A5AD), fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun openReviewUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

enum class WatchOrderDialogView {
    TIMELINE,
    AI_CHAT
}

// ─── Solar Broken Library Icon (https://solar-icons.dev/icons?style=broken&view=grouped&icon=library) ───
val SolarBrokenLibraryVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "SolarBrokenLibrary",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString("M19.562 7c.228-1.305-.776-2.5-2.1-2.5H6.538C5.213 4.5 4.21 5.695 4.438 7M17.5 4.5c.028-.259.043-.389.043-.496a2.01 2.01 0 0 0-1.787-1.993C15.65 2 15.52 2 15.259 2H8.741c-.26 0-.39 0-.497.011A2.01 2.01 0 0 0 6.457 4.004c0 .107.014.237.043.496M15 18H9M21.1935 16.793C20.8437 19.2739 20.6689 20.5143 19.7717 21.2572C18.8745 22 17.5512 22 14.9046 22H9.09536C6.44881 22 5.12553 22 4.22834 21.2572C3.33115 20.5143 3.15626 19.2739 2.80648 16.793L2.38351 13.793C1.93748 10.6294 1.71447 9.04765 2.66232 8.02383C3.61017 7 5.29758 7 8.67239 7H15.3276C18.7024 7 20.3898 7 21.3377 8.02383C22.0865 8.83268 22.1045 9.98979 21.8592 12").toNodes(),
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ).build()
}

@Composable
fun SolarBrokenLibraryIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Icon(
        imageVector = SolarBrokenLibraryVector,
        contentDescription = "Library",
        modifier = modifier,
        tint = tint
    )
}

// ─── Watch Order Stack Icon (Tiered Cards / Stacked Timeline Box) ───
@Composable
fun WatchOrderStackIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFE5E7EB)
) {
    SolarBrokenLibraryIcon(modifier = modifier, tint = tint)
}

// ─── 3D Stacked Series / Season Badge (No circle background) ───────
@Composable
fun SeriesSeasonBadge3D(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(32.dp, 30.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back stack layer (Deep charcoal base)
        Box(
            modifier = Modifier
                .size(19.dp, 22.dp)
                .offset(x = 4.dp, y = (-2).dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF222228))
        )
        // Middle stack layer (Graphite with subtle silver border)
        Box(
            modifier = Modifier
                .size(19.dp, 22.dp)
                .offset(x = 2.dp, y = 0.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF33333C))
                .border(0.5.dp, Color(0xFF555562).copy(alpha = 0.6f), RoundedCornerShape(3.dp))
        )
        // Front focal layer (Sleek monochrome gradient with crisp silver highlight)
        Box(
            modifier = Modifier
                .size(19.dp, 22.dp)
                .offset(x = 0.dp, y = 2.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF3E3E4C),
                            Color(0xFF1E1E24)
                        )
                    )
                )
                .border(0.8.dp, Color(0xFFCCCCCC).copy(alpha = 0.85f), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(8.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.25f, size.height * 0.15f)
                    lineTo(size.width * 0.85f, size.height * 0.5f)
                    lineTo(size.width * 0.25f, size.height * 0.85f)
                    close()
                }
                drawPath(path, Color.White)
            }
        }
    }
}

@Composable
fun WatchOrderDialog(
    currentAnime: AnimeDetail,
    episodeMetaMap: Map<Int, EpisodeMeta> = emptyMap(),
    onDismiss: () -> Unit,
    onSelectAnime: (Int, WatchOrderItem?) -> Unit = { _, _ -> },
    onPlayEpisode: (Int, Int, String, WatchOrderItem?) -> Unit = { _, _, _, _ -> }
) {
    var isLoading by remember(currentAnime.id) { mutableStateOf(true) }
    var entriesFoundCount by remember(currentAnime.id) { mutableIntStateOf(0) }
    var orderList by remember(currentAnime.id) { mutableStateOf<List<WatchOrderItem>>(emptyList()) }
    var currentDialogView by remember { mutableStateOf(WatchOrderDialogView.AI_CHAT) }
    var isChatFullscreen by remember { mutableStateOf(true) }
    val appThemeColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.getPrimaryColor(androidx.compose.ui.platform.LocalContext.current)

    LaunchedEffect(currentAnime.id) {
        isLoading = true
        entriesFoundCount = 0
        val result = try {
            WatchOrderManager.getWatchOrder(
                currentAnimeId = currentAnime.id,
                currentAnimeTitle = currentAnime.title,
                currentAnimeCover = currentAnime.coverUrl,
                currentDesc = currentAnime.description,
                currentFormat = currentAnime.format,
                currentEpisodes = currentAnime.episodesCount,
                currentYear = if (currentAnime.releaseYear > 0) currentAnime.releaseYear else null,
                onProgress = { count ->
                    entriesFoundCount = count
                }
            )
        } catch (_: Exception) {
            emptyList()
        }
        orderList = result
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val isFullscreenActive = isChatFullscreen && currentDialogView == WatchOrderDialogView.AI_CHAT

        val view = androidx.compose.ui.platform.LocalView.current
        DisposableEffect(view, isFullscreenActive) {
            var p: Any? = view
            var dialogWin: android.view.Window? = null
            while (p != null) {
                if (p is androidx.compose.ui.window.DialogWindowProvider) {
                    dialogWin = p.window
                    break
                }
                try {
                    val method = p.javaClass.getMethod("getWindow")
                    val w = method.invoke(p) as? android.view.Window
                    if (w != null) {
                        dialogWin = w
                        break
                    }
                } catch (_: Exception) {}
                p = (p as? android.view.View)?.parent ?: (p as? android.view.ViewParent)
            }

            dialogWin?.let { window ->
                window.setWindowAnimations(0)
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            }
            onDispose {}
        }

        var isVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { isVisible = true }

        val targetScrimAlpha = if (!isVisible) 0f else if (isFullscreenActive) 1f else 0.40f
        val scrimAlpha by animateFloatAsState(
            targetValue = targetScrimAlpha,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "dialogScrimAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isFullscreenActive) Color(0xFF141418) else Color.Black.copy(alpha = scrimAlpha))
                .then(
                    if (!isFullscreenActive) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onDismiss() }
                    } else Modifier
                ),
            contentAlignment = if (isFullscreenActive) Alignment.TopCenter else Alignment.Center
        ) {
            Surface(
                modifier = if (isFullscreenActive) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.82f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                        .clip(RoundedCornerShape(20.dp))
                },
                color = if (isFullscreenActive) Color(0xFF141418) else Color(0xF216161A),
                shape = if (isFullscreenActive) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(20.dp),
                tonalElevation = 8.dp
            ) {
                if (currentDialogView == WatchOrderDialogView.AI_CHAT) {
                    // ─── AI Chatbot View (Takes same dialog modal space or expands fullscreen) ───
                    WatchOrderAiChatSection(
                        currentAnime = currentAnime,
                        franchiseItems = orderList,
                        isFranchiseLoading = isLoading,
                        episodeMetaMap = episodeMetaMap,
                        isFullscreen = isChatFullscreen,
                        onToggleFullscreen = { isChatFullscreen = !isChatFullscreen },
                        onBack = onDismiss,
                        onSelectAnime = onSelectAnime,
                        onPlayEpisode = onPlayEpisode
                    )
                } else {
                    // ─── Timeline Watch Order Guide View ───
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // ─── Header ───
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1A1A22))
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Stacked timeline icon with no circle background
                                WatchOrderStackIcon(
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )

                                Column {
                                    Text(
                                        text = "Watch Order Guide",
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isLoading) {
                                            if (entriesFoundCount > 0) "Analyzing timeline ($entriesFoundCount entries found)..."
                                            else "Analyzing franchise timeline..."
                                        } else {
                                            "${orderList.size} ${if (orderList.size == 1) "entry" else "entries"} • Chronological Order"
                                        },
                                        color = Color(0xFF9E9EA8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF2E2E38))
                        )

                        // ─── Content (Weight 1f so bottom button remains stationary) ───
                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    com.lagradost.cloudstream3.ui.animebox.components.ThemeDotCircularProgressIndicator(
                                        color = Color(0xFFD4D6DC),
                                        size = 40.dp
                                    )
                                    CurvedHillWaveText(
                                        text = "Loading your watch order...",
                                        color = appThemeColor,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else if (orderList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    WatchOrderStackIcon(
                                        tint = Color(0xFF555562),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = "No related watch order entries found.",
                                        color = Color(0xFFB3B3BF),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            // High-Performance Virtualized Timeline (Lag-free & smooth scrolling)
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp),
                                contentPadding = PaddingValues(top = 16.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(
                                    items = orderList,
                                    key = { _, item -> item.id }
                                ) { index, item ->
                                    val isLast = index == orderList.size - 1
                                    WatchOrderTimelineEntry(
                                        stepNumber = index + 1,
                                        item = item,
                                        isLast = isLast,
                                        onClick = { onSelectAnime(item.id, item) }
                                    )
                                }
                            }
                        }

                        // ─── Stationary Docked Bottom "Ask AI Watch Order" Button ───
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF16161D))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF22242B))
                                    .clickable {
                                        currentDialogView = WatchOrderDialogView.AI_CHAT
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GrokAtlasAvatar(
                                        expression = AtlasExpression.HAPPY,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Atlas • Your Watch Order Guide",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.25.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Ask questions, custom routes & skip guides",
                                            color = Color(0xFFB3B3BF),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
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

// ─── AI Watch Order Chat Section ──────────────────────────────────────────

/** Removes emoji / pictographs from Atlas question text (presentation only). */
private fun stripAtlasEmoji(text: String): String =
    text.replace(
        Regex("[\\u2190-\\u21FF\\u2300-\\u23FF\\u2460-\\u24FF\\u25A0-\\u27BF\\u2900-\\u297F\\u2B00-\\u2BFF\\uFE0F\\u200D\\u20E3]|[\\uD83C-\\uD83E][\\uDC00-\\uDFFF]"),
        ""
    ).replace(Regex("\\s{2,}"), " ").trim()

private val atlasPixelFontFamily by lazy {
    androidx.compose.ui.text.font.FontFamily(
        androidx.compose.ui.text.font.Font(R.font.press_start_2p)
    )
}

enum class AtlasExpression {
    NEUTRAL,
    HAPPY,
    THINKING,
    SCARED,
    ANGRY
}

/**
 * Animated Grok-style face avatar for Atlas.
 * Features a pure white circular head, smoothly animated capsule/oval eyes,
 * natural blinking, pupil saccades, and mood transitions (Happy, Scared, Angry, Thinking, Neutral).
 */
@Composable
fun GrokAtlasAvatar(
    expression: AtlasExpression = AtlasExpression.NEUTRAL,
    isThinking: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "grokAtlasFace")

    // 1. Natural Blinking Animation (quick drop in eye height every 3.5 seconds)
    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3600
                1f at 0
                1f at 3100
                0.08f at 3220
                1f at 3340
                1f at 3600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "eyeBlink"
    )

    // 2. Idle eye saccades (subtle look left, center, right, up, down)
    val lookOffsetX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5200
                0f at 0
                0f at 1600
                2.2f at 1900
                2.2f at 3000
                (-2.2f) at 3400
                (-2.2f) at 4400
                0f at 4800
                0f at 5200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "eyeLookX"
    )

    val lookOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4400
                0f at 0
                0f at 1800
                (-1.8f) at 2100
                (-1.8f) at 3100
                1.4f at 3500
                1.4f at 4000
                0f at 4400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "eyeLookY"
    )

    // 3. Thinking float / eye roll animation when AI is generating
    val thinkingShiftY by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinkingShiftY"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = minOf(w, h) / 2f
        val center = Offset(w / 2f, h / 2f)

        // Draw subtle dark outer border
        drawCircle(
            color = Color(0xFF1E293B),
            radius = radius,
            center = center
        )

        // Draw vibrant celestial blue slime body (matching User Image 2)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF60A5FA), Color(0xFF3897F0), Color(0xFF1D4ED8)),
                center = Offset(center.x - radius * 0.15f, center.y - radius * 0.2f),
                radius = radius * 1.1f
            ),
            radius = radius * 0.94f,
            center = center
        )

        // Eye geometry
        val eyeWidth = radius * 0.22f
        val eyeBaseHeight = radius * 0.42f
        val eyeSpacing = radius * 0.30f

        val effectiveBlink = if (isThinking) 1f else blinkProgress
        val eyeHeight = (eyeBaseHeight * effectiveBlink).coerceAtLeast(radius * 0.04f)

        val activeOffsetX = if (isThinking) 0f else lookOffsetX * (radius / 16f)
        val activeOffsetY = if (isThinking) thinkingShiftY * (radius / 16f) else lookOffsetY * (radius / 16f)

        val leftEyeCenter = Offset(center.x - eyeSpacing + activeOffsetX, center.y + activeOffsetY)
        val rightEyeCenter = Offset(center.x + eyeSpacing + activeOffsetX, center.y + activeOffsetY)

        when (expression) {
            AtlasExpression.HAPPY -> {
                // Happy curved eyes (^ ^)
                val strokeWidth = radius * 0.16f
                val happyRadius = radius * 0.18f
                drawArc(
                    color = Color.White,
                    startAngle = 190f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(leftEyeCenter.x - happyRadius, leftEyeCenter.y - happyRadius),
                    size = Size(happyRadius * 2, happyRadius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color.White,
                    startAngle = 190f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(rightEyeCenter.x - happyRadius, rightEyeCenter.y - happyRadius),
                    size = Size(happyRadius * 2, happyRadius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            AtlasExpression.ANGRY -> {
                // Slanted / angled oval eyes (\ /)
                val slant = radius * 0.08f
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(leftEyeCenter.x - eyeWidth / 2f, leftEyeCenter.y - eyeHeight / 2f + slant),
                    size = Size(eyeWidth, eyeHeight),
                    cornerRadius = CornerRadius(eyeWidth / 2f, eyeWidth / 2f)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(rightEyeCenter.x - eyeWidth / 2f, rightEyeCenter.y - eyeHeight / 2f - slant),
                    size = Size(eyeWidth, eyeHeight),
                    cornerRadius = CornerRadius(eyeWidth / 2f, eyeWidth / 2f)
                )
            }
            AtlasExpression.SCARED -> {
                // Small trembling wide round eyes (O O)
                val scaredEyeRadius = radius * 0.13f
                drawCircle(
                    color = Color.White,
                    radius = scaredEyeRadius,
                    center = leftEyeCenter
                )
                drawCircle(
                    color = Color.White,
                    radius = scaredEyeRadius,
                    center = rightEyeCenter
                )
            }
            else -> {
                // Default vertical capsule / oval white eyes looking slightly up and right (matching User Image 2)
                val lookUpRightX = radius * 0.12f
                val lookUpRightY = -radius * 0.10f
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(leftEyeCenter.x - eyeWidth / 2f + lookUpRightX, leftEyeCenter.y - eyeHeight / 2f + lookUpRightY),
                    size = Size(eyeWidth, eyeHeight),
                    cornerRadius = CornerRadius(eyeWidth / 2f, eyeWidth / 2f)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(rightEyeCenter.x - eyeWidth / 2f + lookUpRightX, rightEyeCenter.y - eyeHeight / 2f + lookUpRightY),
                    size = Size(eyeWidth, eyeHeight),
                    cornerRadius = CornerRadius(eyeWidth / 2f, eyeWidth / 2f)
                )
            }
        }

        if (expression == AtlasExpression.HAPPY) {
            drawArc(
                color = Color(0xFF0B2857),
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.28f, center.y + radius * 0.18f),
                size = Size(radius * 0.56f, radius * 0.42f),
                style = Stroke(width = radius * 0.075f, cap = StrokeCap.Round)
            )
        }
    }
}

private val AiChatCopyIconVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "AiChatCopy",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(16f, 1f)
            lineTo(4f, 1f)
            curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f)
            lineTo(2f, 17f)
            lineTo(4f, 17f)
            lineTo(4f, 3f)
            lineTo(16f, 3f)
            lineTo(16f, 1f)
            close()
            moveTo(19f, 5f)
            lineTo(8f, 5f)
            curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f)
            lineTo(6f, 21f)
            curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f)
            lineTo(19f, 23f)
            curveTo(20.1f, 23f, 21f, 22.1f, 21f, 21f)
            lineTo(21f, 7f)
            curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
            close()
            moveTo(19f, 21f)
            lineTo(8f, 21f)
            lineTo(8f, 7f)
            lineTo(19f, 7f)
            lineTo(19f, 21f)
            close()
        }
    }.build()
}

private val AiChatKeyIconVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "AiChatKey",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12.65f, 10f)
            curveTo(11.83f, 7.67f, 9.61f, 6f, 7f, 6f)
            curveTo(3.69f, 6f, 1f, 8.69f, 1f, 12f)
            curveTo(1f, 15.31f, 3.69f, 18f, 7f, 18f)
            curveTo(9.61f, 18f, 11.83f, 16.33f, 12.65f, 14f)
            lineTo(17f, 14f)
            lineTo(17f, 18f)
            lineTo(21f, 18f)
            lineTo(21f, 14f)
            lineTo(23f, 14f)
            lineTo(23f, 10f)
            lineTo(12.65f, 10f)
            close()
            moveTo(7f, 14f)
            curveTo(5.9f, 14f, 5f, 13.1f, 5f, 12f)
            curveTo(5f, 10.9f, 5.9f, 10f, 7f, 10f)
            curveTo(8.1f, 10f, 9f, 10.9f, 9f, 12f)
            curveTo(9f, 13.1f, 8.1f, 14f, 7f, 14f)
            close()
        }
    }.build()
}



private val YouTubeIconVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "YouTube",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Official YouTube red badge
        path(fill = SolidColor(Color(0xFFFF0000))) {
            moveTo(23.498f, 6.186f)
            curveTo(23.167f, 4.954f, 22.203f, 4.02f, 20.976f, 3.69f)
            curveTo(19.104f, 3.185f, 12.0f, 3.185f, 12.0f, 3.185f)
            curveTo(12.0f, 3.185f, 4.896f, 3.185f, 3.024f, 3.69f)
            curveTo(1.797f, 4.02f, 0.833f, 4.954f, 0.502f, 6.186f)
            curveTo(0.0f, 8.07f, 0.0f, 12.0f, 0.0f, 12.0f)
            curveTo(0.0f, 12.0f, 0.0f, 15.93f, 0.502f, 17.814f)
            curveTo(0.833f, 19.046f, 1.797f, 19.98f, 3.024f, 20.31f)
            curveTo(4.896f, 20.815f, 12.0f, 20.815f, 12.0f, 20.815f)
            curveTo(12.0f, 20.815f, 19.104f, 20.815f, 20.976f, 20.31f)
            curveTo(22.203f, 19.98f, 23.167f, 19.046f, 23.498f, 17.814f)
            curveTo(24.0f, 15.93f, 24.0f, 12.0f, 24.0f, 12.0f)
            curveTo(24.0f, 12.0f, 24.0f, 8.07f, 23.498f, 6.186f)
            close()
        }
        // Center white play triangle
        path(fill = SolidColor(Color.White)) {
            moveTo(9.545f, 15.568f)
            lineTo(9.545f, 8.432f)
            lineTo(15.818f, 12.0f)
            close()
        }
    }.build()
}


fun buildAnnotatedMarkdownString(rawInput: String): androidx.compose.ui.text.AnnotatedString {
    val trimmedRaw = rawInput.trim()
    if (trimmedRaw.all { ch -> ch == '"' || ch == '\'' || ch == '”' || ch == '“' || ch == '’' || ch == '‘' || ch == '•' || ch == '.' || ch == '-' || ch == ' ' }) {
        return androidx.compose.ui.text.AnnotatedString("")
    }

    // Sanitize stray `* *` artifacts, double bullets, isolated quotes, and unneeded markdown symbols
    var input = rawInput
        .replace(Regex("""(?<=\s|^)\*\s+\*(?=\s|$)"""), "")
        .replace(Regex("""(?<=\s|^)["'“”‘’]{1,2}(?=\s|$)"""), "")
        .replace(Regex("""^["'“”‘’]{1,2}|["'“”‘’]{1,2}$"""), "")
        .replace(Regex("""(?<=\s|^)\.(?=\s|$)"""), "")
        .replace(Regex("""^\s*[\*\-•]\s*"""), "")

    // Match code `...`, bold-italic ***...***, bold **...**, italic *...*
    val tokenRegex = Regex("""(`[^`]+`|\*\*\*[^*]+\*\*\*|\*\*[^*]+\*\*|\*[^*]+\*)""")

    return buildAnnotatedString {
        var lastIdx = 0
        for (match in tokenRegex.findAll(input)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > lastIdx) {
                // Strip any stray orphan asterisks from the plain text segment so `*` or `* *` never show
                val plainSegment = input.substring(lastIdx, start).replace("*", "")
                append(plainSegment)
            }
            val token = match.value
            when {
                token.startsWith("`") && token.endsWith("`") -> {
                    val codeContent = token.removeSurrounding("`")
                    withStyle(
                        SpanStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Color(0xFFD4D4E8),
                            background = Color(0xFF262632)
                        )
                    ) {
                        append(" $codeContent ")
                    }
                }
                token.startsWith("***") && token.endsWith("***") -> {
                    val content = token.removeSurrounding("***")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = Color.White)) {
                        append(content)
                    }
                }
                token.startsWith("**") && token.endsWith("**") -> {
                    val content = token.removeSurrounding("**")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                        append(content)
                    }
                }
                token.startsWith("*") && token.endsWith("*") -> {
                    val content = token.removeSurrounding("*")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color(0xFFE2E2EC))) {
                        append(content)
                    }
                }
                else -> append(token)
            }
            lastIdx = end
        }
        if (lastIdx < input.length) {
            val trailing = input.substring(lastIdx).replace("*", "")
            append(trailing)
        }
    }
}

data class ParsedMarkdownTable(
    val headers: List<String>,
    val rows: List<List<String>>
)

sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Header(val level: Int, val title: String) : MarkdownBlock()
    data class Bullet(val content: String) : MarkdownBlock()
    data class Numbered(val number: String, val content: String) : MarkdownBlock()
    object Divider : MarkdownBlock()
    data class Table(val table: ParsedMarkdownTable) : MarkdownBlock()
    data class Image(val alt: String, val url: String) : MarkdownBlock()
}

fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    val lines = rawText.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    fun isMarkdownTableRow(line: String): Boolean {
        val t = line.trim()
        if (!t.contains("|")) return false
        if (t.startsWith("|") && t.length > 1) return true
        if (t.endsWith("|") && t.length > 1) return true
        if (t.count { it == '|' } >= 2) return true
        val parts = t.split("|")
        if (parts.size >= 2 && parts.all { p -> p.trim().isEmpty() || p.trim().all { c -> c == '-' || c == ':' || c == ' ' } }) return true
        return false
    }

    while (i < lines.size) {
        val trimmed = lines[i].trim()
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Skip lines that are only stray quotation marks, bullets or dots
        if (trimmed == "..." || trimmed.all { ch -> ch == '"' || ch == '\'' || ch == '”' || ch == '“' || ch == '’' || ch == '‘' || ch == '*' || ch == '•' || ch == '-' || ch == '.' || ch == ' ' }) {
            i++
            continue
        }

        // Skip code fences for quiz, game, or watch_order blocks if they ever leak into raw markdown
        if (trimmed.startsWith("```quiz", ignoreCase = true) || trimmed.startsWith("```game", ignoreCase = true) || trimmed.startsWith("```watch_order", ignoreCase = true) || trimmed.startsWith("```play_episode", ignoreCase = true)) {
            while (i < lines.size) {
                val fenceLine = lines[i].trim()
                i++
                if (fenceLine == "```" && i > 1) break
            }
            continue
        }
        if (trimmed.startsWith("{") && (trimmed.contains("\"question\"") || trimmed.contains("\"gameType\"") || trimmed.contains("\"options\""))) {
            i++
            continue
        }

        // Standalone Markdown Image: ![alt](url)
        val standaloneImg = Regex("""^!\[([^\]]*)\]\((https?://[^\)]+)\)$""").find(trimmed)
        if (standaloneImg != null) {
            val alt = standaloneImg.groupValues[1]
            val url = standaloneImg.groupValues[2]
            blocks.add(MarkdownBlock.Image(alt, url))
            i++
            continue
        }

        // Check if line looks like markdown table row
        if (isMarkdownTableRow(trimmed)) {
            val tableLines = mutableListOf<String>()
            while (i < lines.size) {
                val tLine = lines[i].trim()
                if (isMarkdownTableRow(tLine)) {
                    tableLines.add(tLine)
                    i++
                } else if (tLine.isEmpty() && i + 1 < lines.size && isMarkdownTableRow(lines[i + 1].trim())) {
                    // Empty line between table rows from LLM, skip empty line and continue table
                    i++
                } else {
                    break
                }
            }

            fun parseRow(rowLine: String): List<String> {
                val clean = rowLine.trim().removePrefix("|").removeSuffix("|")
                val cells = clean.split("|")
                return cells.map { cell ->
                    val c = cell.trim()
                    val isQuoteOrDotOnly = c.all { ch -> ch == '"' || ch == '\'' || ch == '”' || ch == '“' || ch == '’' || ch == '‘' || ch == '.' || ch == '•' || ch == ' ' }
                    if (isQuoteOrDotOnly) "" else c
                }
            }

            if (tableLines.size >= 2) {
                val headerCells = parseRow(tableLines[0])
                var dataStartIndex = 1
                if (tableLines.size > 1) {
                    val secondLine = tableLines[1]
                    val isDivider = secondLine.trim().removePrefix("|").removeSuffix("|").split("|")
                        .all { cell -> cell.trim().all { it == '-' || it == ':' || it == ' ' } && cell.trim().isNotEmpty() }
                    if (isDivider) {
                        dataStartIndex = 2
                    }
                }

                val dataRows = mutableListOf<List<String>>()
                for (rowIdx in dataStartIndex until tableLines.size) {
                    val rowCells = parseRow(tableLines[rowIdx])
                    dataRows.add(rowCells)
                }

                blocks.add(MarkdownBlock.Table(ParsedMarkdownTable(headers = headerCells, rows = dataRows)))
                continue
            } else if (tableLines.size == 1) {
                val prevTable = blocks.lastOrNull() as? MarkdownBlock.Table
                if (prevTable != null) {
                    val singleRow = parseRow(tableLines[0])
                    blocks[blocks.size - 1] = MarkdownBlock.Table(
                        ParsedMarkdownTable(
                            headers = prevTable.table.headers,
                            rows = prevTable.table.rows + listOf(singleRow)
                        )
                    )
                    continue
                } else {
                    blocks.add(MarkdownBlock.Paragraph(tableLines[0]))
                    continue
                }
            }
        }

        // Horizontal dividers: ---, ***, ___, * * *, etc.
        if (trimmed == "---" || trimmed == "***" || trimmed == "___" || trimmed.matches(Regex("""^([\*\-_]\s*){3,}$"""))) {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // Markdown Headers: #, ##, ###, ####, #####, ###### (eliminates #### entirely)
        val headerMatch = Regex("""^(#{1,6})\s+(.*)""").find(trimmed)
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val rawTitle = headerMatch.groupValues[2]
            val cleanHeader = rawTitle.replace(Regex("""^[\*\s#]+|[\*\s#:]+$"""), "").trim()
            blocks.add(MarkdownBlock.Header(level, cleanHeader))
            i++
            continue
        }

        // Numbered list items: 1. , 2) , etc.
        val numberedMatch = Regex("""^(\d+[\.\)])\s+(.*)""").find(trimmed)
        if (numberedMatch != null) {
            val num = numberedMatch.groupValues[1]
            val content = numberedMatch.groupValues[2]
            blocks.add(MarkdownBlock.Numbered(num, content))
            i++
            continue
        }

        // Bullet points: * , - , • , + , or nested bullets like * *
        val bulletMatch = Regex("""^([\*\-•\+]+(?:\s+[\*\-•\+]+)?)\s+(.*)""").find(trimmed)
        if (bulletMatch != null) {
            val bulletContent = bulletMatch.groupValues[2]
            blocks.add(MarkdownBlock.Bullet(bulletContent))
            i++
            continue
        }

        // Regular paragraph text
        blocks.add(MarkdownBlock.Paragraph(trimmed))
        i++
    }
    return blocks
}

fun resolveStoreDomain(text: String): String? {
    val urlMatch = Regex("""https?://([a-zA-Z0-9\-\.]+)""").find(text)
    if (urlMatch != null) {
        val host = urlMatch.groupValues[1].lowercase()
        if (host.contains("githubusercontent") || host.contains("pokemonshowdown") || host.contains("pokeapi") ||
            text.contains(".png", ignoreCase = true) || text.contains(".jpg", ignoreCase = true) ||
            text.contains(".webp", ignoreCase = true) || text.contains(".gif", ignoreCase = true)) {
            return null
        }
        return host
    }
    val lower = text.trim().lowercase()
    if (lower.contains("githubusercontent") || lower.contains("pokemonshowdown") || lower.contains("pokeapi") ||
        lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".webp") || lower.endsWith(".gif")) {
        return null
    }
    return when {
        lower.contains("steam") -> "store.steampowered.com"
        lower.contains("epic") -> "epicgames.com"
        lower.contains("gog") -> "gog.com"
        lower.contains("playstation store") || lower.contains("ps store") || lower == "playstation" -> "store.playstation.com"
        lower.contains("nintendo eshop") || lower.contains("eshop") || lower == "nintendo" -> "nintendo.com"
        lower.contains("xbox store") || lower.contains("microsoft store") || lower == "xbox" -> "xbox.com"
        lower.contains("amazon") -> "amazon.com"
        lower.contains("crunchyroll") -> "store.crunchyroll.com"
        lower.contains("cdjapan") -> "cdjapan.co.jp"
        lower.contains("amiami") -> "amiami.com"
        lower.contains("bookwalker") -> "global.bookwalker.jp"
        lower.contains("rightstuf") -> "store.crunchyroll.com"
        lower.contains("iidb") -> "iidb.iisu.network"
        lower.contains("wikipedia") -> "en.wikipedia.org"
        else -> null
    }
}

fun getFaviconUrl(domain: String): String {
    return "https://www.google.com/s2/favicons?domain=$domain&sz=64"
}

@Composable
fun MarkdownTableCellView(
    cellText: String,
    isFirstColumn: Boolean = false,
    modifier: Modifier = Modifier
) {
    val trimmedCell = cellText.trim()
    val isArtifactOnly = trimmedCell.all { ch ->
        ch == '"' || ch == '\'' || ch == '”' || ch == '“' || ch == '’' || ch == '‘' || ch == '•' || ch == '.' || ch == '-' || ch == ' '
    }
    if (isArtifactOnly && !trimmedCell.contains("http")) {
        return
    }

    val context = LocalContext.current

    // Check for inline markdown image ![alt](url) (e.g. Pokémon sprite inside table cell)
    val imgRegex = Regex("""!\[([^\]]*)\]\((https?://[^\)]+)\)""")
    val imgMatch = imgRegex.find(cellText)
    if (imgMatch != null) {
        val alt = imgMatch.groupValues[1]
        val imgUrl = imgMatch.groupValues[2]
        val cleanLabel = cellText.replace(imgMatch.value, "").trim()
        val isLabelQuoteOnly = cleanLabel.all { ch -> ch == '"' || ch == '\'' || ch == '”' || ch == '“' || ch == '’' || ch == '‘' || ch == '*' || ch == '-' || ch == '.' || ch == ' ' }
        val sanitizedLabel = if (isLabelQuoteOnly) "" else cleanLabel.trim('"', '\'', '”', '“', '’', '‘', '.', ' ')
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Transparent,
                modifier = Modifier.size(30.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(imgUrl),
                    contentDescription = alt,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (sanitizedLabel.isNotBlank()) {
                Text(
                    text = buildAnnotatedMarkdownString(sanitizedLabel),
                    color = Color(0xFFDCDCE8),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
        return
    }

    // Check for markdown link [Label](url) or bare url
    val linkRegex = Regex("""\[([^\]]+)\]\((https?://[^\)]+)\)""")
    val linkMatch = linkRegex.find(cellText)
    val rawUrlMatch = if (linkMatch == null) Regex("""(https?://[^\s]+)""").find(cellText) else null

    val url = linkMatch?.groupValues?.get(2) ?: rawUrlMatch?.groupValues?.get(1)
    val rawDisplayText = when {
        linkMatch != null -> linkMatch.groupValues[1]
        rawUrlMatch != null -> cellText.replace(rawUrlMatch.value, "").trim().ifBlank { "Visit Store" }
        else -> cellText
    }
    val displayText = if (rawDisplayText.trim().all { ch -> ch == '"' || ch == '\'' || ch == '”' || ch == '“' || ch == '’' || ch == '‘' || ch == ' ' }) {
        ""
    } else {
        rawDisplayText.trim('"', '\'', '”', '“', '’', '‘', ' ')
    }

    val storeDomain = resolveStoreDomain(cellText)
    val faviconUrl = if (url != null || isFirstColumn) storeDomain?.let { getFaviconUrl(it) } else null

    if (url != null) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF22222E))
                .clickable {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(horizontal = 8.dp, vertical = 4.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (faviconUrl != null) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(15.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(faviconUrl),
                        contentDescription = storeDomain,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(1.dp)
                    )
                }
            }
            Text(
                text = displayText,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(12.dp)
            )
        }
    } else if (faviconUrl != null && !cellText.all { it.isDigit() || it == '$' || it == '¥' || it == '€' || it == '.' || it == ' ' || it == '-' || it == '~' }) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(15.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(faviconUrl),
                    contentDescription = storeDomain,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(1.dp)
                )
            }
            Text(
                text = buildAnnotatedMarkdownString(cellText),
                color = Color(0xFFDCDCE8),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    } else {
        val clean = cellText
            .replace(Regex("""(?<=\s|^)["'“”‘’]{1,2}(?=\s|$)"""), "")
            .replace(Regex("""^["'“”‘’]{1,2}|["'“”‘’]{1,2}$"""), "")
            .trim()
        val isOnlyQuoteOrDot = clean.all { ch -> ch == '"' || ch == '\'' || ch == '”' || ch == '“' || ch == '’' || ch == '‘' || ch == '•' || ch == '.' || ch == '-' || ch == ' ' }
        if (clean.isNotBlank() && !isOnlyQuoteOrDot) {
            InlineMarkdownTextWithImages(
                text = clean,
                color = Color(0xFFDCDCE8),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = modifier
            )
        }
    }
}

@Composable
fun MarkdownTableView(table: ParsedMarkdownTable) {
    if (table.headers.isEmpty() && table.rows.isEmpty()) return

    val horizontalScrollState = rememberScrollState()
    val colCount = maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (colCount <= 0) return

    val columnWidths = remember(table) {
        (0 until colCount).map { colIdx ->
            val headerLen = table.headers.getOrNull(colIdx)?.length ?: 0
            val maxCellLen = table.rows.maxOfOrNull { it.getOrNull(colIdx)?.length ?: 0 } ?: 0
            val maxLen = maxOf(headerLen, maxCellLen)
            when {
                maxLen <= 6 -> 85.dp
                maxLen <= 12 -> 110.dp
                maxLen <= 22 -> 145.dp
                maxLen <= 36 -> 185.dp
                else -> 230.dp
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF161620)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
        ) {
            // Header row
            if (table.headers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFF22222E))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    table.headers.indices.forEach { colIdx ->
                        val header = table.headers.getOrNull(colIdx) ?: ""
                        val width = columnWidths.getOrElse(colIdx) { 120.dp }
                        Box(
                            modifier = Modifier
                                .width(width)
                                .padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = buildAnnotatedMarkdownString(header),
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(Color(0xFF2C2C3A))
                )
            }

            // Data rows
            table.rows.forEachIndexed { rowIdx, row ->
                val rowBg = if (rowIdx % 2 == 1) Color(0xFF1A1A24) else Color(0xFF161620)
                Row(
                    modifier = Modifier
                        .background(rowBg)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (0 until colCount).forEach { colIdx ->
                        val cellText = row.getOrNull(colIdx) ?: ""
                        val width = columnWidths.getOrElse(colIdx) { 120.dp }
                        Box(
                            modifier = Modifier
                                .width(width)
                                .padding(horizontal = 6.dp)
                        ) {
                            MarkdownTableCellView(cellText = cellText, isFirstColumn = (colIdx == 0))
                        }
                    }
                }
                if (rowIdx < table.rows.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color(0xFF20202C))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineMarkdownTextWithImages(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFE2E2EA),
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 18.sp
) {
    val cleanText = remember(text) {
        val s = text.trim()
        if (s.all { ch -> ch == '"' || ch == '\'' || ch == '”' || ch == '“' || ch == '’' || ch == '‘' || ch == '•' || ch == '.' || ch == '-' || ch == ' ' }) {
            ""
        } else {
            s.replace(Regex("""(?<=\s|^)["'“”‘’]{1,2}(?=\s|$)"""), "")
                .replace(Regex("""^["'“”‘’]{1,2}|["'“”‘’]{1,2}$"""), "")
                .trim()
        }
    }

    if (cleanText.isBlank()) return

    val imgRegex = Regex("""!\[([^\]]*)\]\((https?://[^\)]+)\)""")
    val matches = remember(cleanText) { imgRegex.findAll(cleanText).toList() }

    if (matches.isEmpty()) {
        Text(
            text = buildAnnotatedMarkdownString(cleanText),
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            modifier = modifier
        )
        return
    }

    FlowRow(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        var lastIndex = 0
        matches.forEach { match ->
            val matchRange = match.range
            if (matchRange.first > lastIndex) {
                val prefix = cleanText.substring(lastIndex, matchRange.first)
                if (prefix.isNotBlank()) {
                    Text(
                        text = buildAnnotatedMarkdownString(prefix),
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight
                    )
                }
            }
            val alt = match.groupValues[1]
            val imgUrl = match.groupValues[2]
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF1C1C26),
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .size(44.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(imgUrl),
                    contentDescription = alt,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(2.dp)
                )
            }
            lastIndex = matchRange.last + 1
        }
        if (lastIndex < cleanText.length) {
            val suffix = cleanText.substring(lastIndex)
            if (suffix.isNotBlank()) {
                Text(
                    text = buildAnnotatedMarkdownString(suffix),
                    color = color,
                    fontSize = fontSize,
                    lineHeight = lineHeight
                )
            }
        }
    }
}

@Composable
fun FormattedAiChatMessage(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    if (isError) {
        Text(
            text = text,
            color = Color(0xFFFF6B6B),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Divider -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .height(0.6.dp)
                            .background(Color(0xFF2E2E38))
                    )
                }
                is MarkdownBlock.Header -> {
                    Spacer(modifier = Modifier.height(if (block.level <= 2) 6.dp else 4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (block.title.contains("Google", ignoreCase = true) || block.title.contains("Episode", ignoreCase = true) || block.title.contains("✦")) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                        Text(
                            text = buildAnnotatedMarkdownString(block.title),
                            color = Color.White,
                            fontSize = if (block.level <= 2) 14.5.sp else 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 19.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
                is MarkdownBlock.Image -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E1E28),
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .wrapContentSize()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(block.url),
                                contentDescription = block.alt,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(52.dp)
                            )
                            if (block.alt.isNotBlank()) {
                                Text(
                                    text = block.alt,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.Numbered -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = block.number,
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp
                        )
                        InlineMarkdownTextWithImages(
                            text = block.content,
                            color = Color(0xFFE2E2EA),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.Bullet -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            color = Color(0xFFCCCCCC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        InlineMarkdownTextWithImages(
                            text = block.content,
                            color = Color(0xFFE2E2EA),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.Table -> {
                    MarkdownTableView(table = block.table)
                }
                is MarkdownBlock.Paragraph -> {
                    val linkMatches = remember(block.text) {
                        Regex("""\[([^\]]+)\]\((https?://[^\)]+)\)""").findAll(block.text).toList()
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        InlineMarkdownTextWithImages(
                            text = block.text,
                            color = Color(0xFFE2E2EA),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        if (linkMatches.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(5.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                linkMatches.forEach { match ->
                                    val label = match.groupValues[1]
                                    val linkUrl = match.groupValues[2]
                                    val domain = resolveStoreDomain(linkUrl) ?: resolveStoreDomain(label)
                                    val favUrl = domain?.let { getFaviconUrl(it) }
                                    if (domain != null) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF22222E),
                                            modifier = Modifier.clickable {
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl)))
                                                } catch (_: Exception) {}
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                if (favUrl != null) {
                                                    Image(
                                                        painter = rememberAsyncImagePainter(favUrl),
                                                        contentDescription = domain,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                                Text(
                                                    text = label,
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
                }
            }
        }
    }
}

@Composable
fun AiYouTubeVideoCard(
    video: AiVideoResult,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var activeVideo by remember(video.videoId) {
        mutableStateOf(
            com.lagradost.cloudstream3.ui.animebox.api.YouTubeMetadataManager.getCached(video.videoId)?.let { cached ->
                video.copy(
                    title = cached.title,
                    channel = cached.authorName,
                    channelAvatarUrl = cached.authorAvatarUrl,
                    thumbnailUrl = cached.thumbnailUrl.ifBlank { video.thumbnailUrl }
                )
            } ?: video
        )
    }

    LaunchedEffect(video.videoId) {
        val isGenericTitle = activeVideo.title.isBlank() ||
            activeVideo.title.equals("YouTube Video Attachment", ignoreCase = true) ||
            activeVideo.title.equals("Watch video on YouTube", ignoreCase = true) ||
            activeVideo.title.startsWith("http://", ignoreCase = true) ||
            activeVideo.title.startsWith("https://", ignoreCase = true)
        if (activeVideo.channelAvatarUrl.isBlank() || isGenericTitle || activeVideo.channel.equals("YouTube", ignoreCase = true)) {
            val fetched = com.lagradost.cloudstream3.ui.animebox.api.YouTubeMetadataManager.fetchVideoInfo(
                video.videoId,
                if (isGenericTitle) null else activeVideo.title
            )
            activeVideo = activeVideo.copy(
                title = fetched.title,
                channel = fetched.authorName,
                channelAvatarUrl = fetched.authorAvatarUrl,
                thumbnailUrl = fetched.thumbnailUrl.ifBlank { activeVideo.thumbnailUrl }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14141E))
    ) {
        // 16:9 Widescreen Video Card Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0F16))
                .clickable {
                    try {
                        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:${activeVideo.videoId}"))
                        context.startActivity(appIntent)
                    } catch (_: Exception) {
                        try {
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${activeVideo.videoId}"))
                            context.startActivity(webIntent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Cannot open video link", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        ) {
            val thumbUrl = activeVideo.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${activeVideo.videoId}/hqdefault.jpg" }
            Image(
                painter = rememberAsyncImagePainter(model = thumbUrl),
                contentDescription = activeVideo.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Top gradient overlay with Video Title, Channel Avatar & Channel Name
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.88f),
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Actual Creator Logo Icon (or official YouTube logo if avatar unavailable)
                    if (activeVideo.channelAvatarUrl.isNotBlank()) {
                        Image(
                            painter = rememberAsyncImagePainter(model = activeVideo.channelAvatarUrl),
                            contentDescription = activeVideo.channel,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = YouTubeIconVector,
                            contentDescription = "YouTube",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(width = 24.dp, height = 18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeVideo.title,
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                        if (activeVideo.channel.isNotBlank()) {
                            Text(
                                text = activeVideo.channel,
                                color = Color(0xFFBAC0D4),
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }



            // Bottom bar scrim with duration and "Watch on YouTube" pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.82f)
                            )
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (video.duration.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = video.duration,
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // "Watch on YouTube" Pill Button with authentic logo and NO outline border
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.76f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Watch on",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Icon(
                            imageVector = YouTubeIconVector,
                            contentDescription = "YouTube",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(width = 20.dp, height = 14.dp)
                        )
                        Text(
                            text = "YouTube",
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

@Composable
fun HentaiSpeechBubbleWarning(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        // Bunny girl avatar on bottom left
        Image(
            painter = painterResource(id = R.drawable.ic_hentai_bunny_raster),
            contentDescription = "Bunny Girl Warning",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(44.dp)
                .offset(y = (-2).dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Speech bubble box with tail pointing down-left to bunny girl
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
        ) {
            // Draw comic action curves (outside top-left and bottom-right as in reference image)
            androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                val accentColor = Color.White.copy(alpha = 0.45f)
                // Top-left comic curve
                drawArc(
                    color = accentColor,
                    startAngle = 180f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(-6.dp.toPx(), -5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(18.dp.toPx(), 18.dp.toPx()),
                    style = stroke
                )
                // Bottom-right comic curve
                drawArc(
                    color = accentColor,
                    startAngle = 0f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(18.dp.toPx(), 18.dp.toPx()),
                    style = stroke
                )
            }

            // Bubble body
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                    .background(Color(0xF2121319))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE11D48))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "18+",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = "ADULT CONTENT WARNING",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Viewer discretion advised. This title contains explicit adult content.",
                    color = Color(0xFFD1D5DB),
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun EpisodePlayButtonCard(
    action: AiPlayEpisodeAction,
    coverUrl: String? = null,
    onPlay: (Int, Int, String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1A1A1E),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                onPlay(action.animeId, action.episodeNum, action.animeTitle)
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Widescreen Episode Poster / Backdrop Thumbnail (Clean, no weird overlay)
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF111114))
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(coverUrl),
                        contentDescription = "Episode Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Episode Metadata Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Episode ${action.episodeNum}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2A2A30))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "HD",
                            color = Color(0xFFCCCCD0),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = action.animeTitle.ifBlank { "Tap to Watch Episode" },
                    color = Color(0xFFAAAAAF),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Sleek subtle circular action pill with solid Spotlight Play Vector
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2C2C34)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = SolarPlayVector,
                    contentDescription = "Play Episode",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
fun AiModelSelectorDropdown(
    isFreeMode: Boolean,
    onModeSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF2B2C33),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isFreeMode) "Free Model" else "Pro",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select AI Model",
                    tint = Color(0xFF9E9EAF),
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = Color(0xFF1E1E28),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = null,
            offset = androidx.compose.ui.unit.DpOffset(x = 82.dp, y = (-42).dp),
            modifier = Modifier
                .widthIn(min = 115.dp, max = 135.dp)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Pro",
                        color = if (!isFreeMode) Color.White else Color(0xFFB0B0C0),
                        fontSize = 12.sp,
                        fontWeight = if (!isFreeMode) FontWeight.Bold else FontWeight.Medium
                    )
                },
                trailingIcon = {
                    if (!isFreeMode) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFFA5B4FC),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                modifier = Modifier.height(36.dp),
                onClick = {
                    expanded = false
                    onModeSelected(false)
                }
            )

            // Sleek thin divider line separating Pro and Free
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(0.75.dp)
                    .background(Color(0xFF2E2E3E))
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Free",
                        color = if (isFreeMode) Color.White else Color(0xFFB0B0C0),
                        fontSize = 12.sp,
                        fontWeight = if (isFreeMode) FontWeight.Bold else FontWeight.Medium
                    )
                },
                trailingIcon = {
                    if (isFreeMode) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                modifier = Modifier.height(36.dp),
                onClick = {
                    expanded = false
                    onModeSelected(true)
                }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WatchOrderAiChatSection(
    currentAnime: AnimeDetail,
    franchiseItems: List<WatchOrderItem>,
    isFranchiseLoading: Boolean = false,
    episodeMetaMap: Map<Int, EpisodeMeta> = emptyMap(),
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onBack: () -> Unit,
    onSelectAnime: (Int, WatchOrderItem?) -> Unit = { _, _ -> },
    onPlayEpisode: (Int, Int, String, WatchOrderItem?) -> Unit = { _, _, _, _ -> }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    var apiKey by remember {
        mutableStateOf(AnimeBoxSettings.getOpenRouterApiKey(context))
    }
    var isFreeMode by remember {
        mutableStateOf(AnimeBoxSettings.isOpenRouterFreeMode(context))
    }
    val model = remember {
        mutableStateOf(if (isFreeMode) AnimeBoxSettings.DEFAULT_FREE_MODEL else AnimeBoxSettings.getProModel(context))
    }

    val messages = remember { mutableStateListOf<AiChatMessage>() }
    val answeredQuizIds = remember { mutableStateMapOf<String, String>() }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var showKeySetup by remember { mutableStateOf(apiKey.isBlank()) }
    var tempKeyInput by remember { mutableStateOf(apiKey) }
    var selectedPreviewImage by remember { mutableStateOf<AiSearchImage?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    var attachedBase64Image by remember { mutableStateOf<String?>(null) }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            attachedImageUri = uri
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    attachedBase64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    Toast.makeText(context, "Image attached! Ask Atlus anything about it.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim()
            if (!spoken.isNullOrBlank()) {
                inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
            }
        }
    }

    // Smart prompt suggestion list
    val isFateFranchise = remember(currentAnime.title) {
        currentAnime.title.contains("Fate", ignoreCase = true)
    }

    val suggestionChips = remember(isFateFranchise, currentAnime.title) {
        if (isFateFranchise) {
            listOf(
                "Watch Official Trailer",
                "Chronological Watch Order",
                "Fate Canon vs Filler Guide",
                "Best Fate Starting Route",
                "Epic Fight Scenes",
                "Play Episode 1",
                "Find a specific episode",
                "Play Guess the Character"
            )
        } else {
            listOf(
                "Watch Official Trailer",
                "Recommended Watch Order",
                "Chronological vs Release Order",
                "Canon vs Filler Episode Guide",
                "Epic Fight Scenes",
                "Play Episode 1",
                "Find a specific episode",
                "Anime & Game Trivia Quiz"
            )
        }
    }

    fun sendMessage(promptText: String) {
        val prompt = promptText.trim()
        if (prompt.isEmpty()) return
        if (isGenerating) return

        if (apiKey.isBlank()) {
            showKeySetup = true
            messages.add(
                AiChatMessage(
                    role = "assistant",
                    text = "Both Free (zero-credit models) and Pro modes require an OpenRouter API key from openrouter.ai/keys. Please enter your API key in setup to get started.",
                    isError = true
                )
            )
            return
        }

        val effectiveImg = attachedBase64Image
        val userMessage = AiChatMessage(
            role = "user",
            text = prompt,
            base64Image = effectiveImg
        )
        messages.add(userMessage)
        inputText = ""
        attachedBase64Image = null
        attachedImageUri = null
        isGenerating = true

        coroutineScope.launch {
            kotlinx.coroutines.delay(80)
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }

            val activeModel = if (isFreeMode) AnimeBoxSettings.DEFAULT_FREE_MODEL else AnimeBoxSettings.getProModel(context)
            val result = OpenRouterChatClient.sendMessage(
                context = context,
                apiKey = apiKey,
                model = activeModel,
                messages = messages.toList(),
                currentAnimeTitle = currentAnime.title,
                franchiseItems = franchiseItems,
                base64Image = effectiveImg
            )

            val aiMsg = result.getOrNull()
            if (aiMsg != null) {
                val latestUserPrompt = messages.lastOrNull { it.role == "user" }?.text.orEmpty()
                val normalizedQuestion = aiMsg.quizData?.question.orEmpty()
                    .lowercase()
                    .replace(Regex("[^a-z0-9]+"), "")
                val previouslyAskedQuestions = messages.asSequence()
                    .filter { it.role == "assistant" }
                    .map { it.quizData?.question.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), "") }
                    .filter { it.isNotBlank() }
                    .toSet()
                val isQuizAnswer = latestUserPrompt.startsWith("My answer to the current quiz", ignoreCase = true)
                val repeatedQuizQuestion = isQuizAnswer && normalizedQuestion.isNotBlank() &&
                    normalizedQuestion in previouslyAskedQuestions

                val finalMessage = if (repeatedQuizQuestion) {
                    isGenerating = true
                    val correction = AiChatMessage(
                        role = "user",
                        text = "The question you just generated repeats a question already asked in this quiz. The repeated question was: '${aiMsg.quizData?.question.orEmpty()}'. Briefly evaluate the user's last answer, then create a different question about a different fact with four new A-D choices. Do not reuse or paraphrase that question."
                    )
                    val retry = OpenRouterChatClient.sendMessage(
                        context = context,
                        apiKey = apiKey,
                        model = activeModel,
                        messages = messages.toList() + correction,
                        currentAnimeTitle = currentAnime.title,
                        franchiseItems = franchiseItems,
                        base64Image = null
                    ).getOrNull()
                    isGenerating = false
                    val retryQuestion = retry?.quizData?.question.orEmpty()
                        .lowercase().replace(Regex("[^a-z0-9]+"), "")
                    if (retry != null && retryQuestion.isNotBlank() && retryQuestion !in previouslyAskedQuestions) {
                        retry
                    } else {
                        AiChatMessage(
                            role = "assistant",
                            text = "I caught a repeated quiz question and couldn't get a fresh one this time. Send ‘another quiz question’ to try again."
                        )
                    }
                } else {
                    aiMsg
                }
                isGenerating = false
                messages.add(finalMessage)
                coroutineScope.launch {
                    kotlinx.coroutines.delay(100)
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            } else {
                val err = result.exceptionOrNull()
                isGenerating = false
                messages.add(
                    AiChatMessage(
                        role = "assistant",
                        text = "Failed to get response: ${err?.message ?: "Please check your OpenRouter API key or network connection."}",
                        isError = true
                    )
                )
            }
        }
    }

    val isImeOpen = WindowInsets.isImeVisible
    val appThemeColor = remember { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper.getPrimaryColor(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D11))
    ) {
        // ─── Header: Grok Avatar + Anime Title on Left, Actions & ">" Return on Right ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414).copy(alpha = 0.92f))
                .then(
                    if (isFullscreen) Modifier.statusBarsPadding()
                    else Modifier
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Animated Grok Face Avatar + Anime Title
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GrokAtlasAvatar(
                        expression = if (isGenerating) AtlasExpression.THINKING else AtlasExpression.NEUTRAL,
                        isThinking = isGenerating,
                        modifier = Modifier.size(28.dp)
                    )

                    Column {
                        Text(
                            text = currentAnime.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Atlas • Watch Order Guide",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Right: Actions (Refresh Tag and clean pure ">" Return Button - NO circle backdrop)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (messages.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2A2A2A))
                                .clickable { messages.clear() }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Refresh",
                                color = Color(0xFFD0D0D0),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }

                    // Return button on the right with pure > arrow (no circle background, no border)
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // ─── Top Segment: Watch Order Guide Franchise Carousel with Atlus Guided Auto-Scroll ───
        if (isFranchiseLoading || franchiseItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF101116))
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CurvedHillWaveText(
                    text = "Loading your watch order...",
                    color = appThemeColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            val timelineListState = androidx.compose.foundation.lazy.rememberLazyListState()
            var isUserInteractingWithTimeline by remember { mutableStateOf(false) }
            var guidedIndex by remember { mutableIntStateOf(0) }
            var lastTimelineInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

            // Atlus Guided Auto-Scroll when user is inactive / idle for > 3.5 seconds
            LaunchedEffect(franchiseItems.size, isUserInteractingWithTimeline) {
                if (franchiseItems.isEmpty()) return@LaunchedEffect
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    val idleTime = System.currentTimeMillis() - lastTimelineInteractionTime
                    if (!isUserInteractingWithTimeline && idleTime >= 3500) {
                        guidedIndex = (guidedIndex + 1) % franchiseItems.size
                        timelineListState.animateScrollToItem(guidedIndex)
                        kotlinx.coroutines.delay(3500)
                    }
                }
            }

            val activeGuidedItem = franchiseItems.getOrNull(guidedIndex) ?: franchiseItems.firstOrNull()
            val guidedSentence = remember(activeGuidedItem) {
                activeGuidedItem?.let { item ->
                    val format = item.format.ifBlank { "entry" }
                    val rel = item.relationType.replace("_", " ").lowercase()
                    val epText = if (item.episodes != null) " with ${item.episodes} eps" else ""
                    val yearText = if (item.year != null) " (${item.year})" else ""
                    "Next: Watch \"${item.title}\"$yearText — $rel $format$epText!"
                } ?: ""
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E0F14))
                    .padding(vertical = 10.dp)
            ) {
                // Header with title and entry count (No scroll arrow text)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Franchise Timeline • ${franchiseItems.size} Entries",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }

                // 1. Poster carousel ON TOP
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    state = timelineListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    isUserInteractingWithTimeline = event.changes.any { it.pressed }
                                    lastTimelineInteractionTime = System.currentTimeMillis()
                                }
                            }
                        },
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(franchiseItems, key = { _, it -> it.id }) { idx, item ->
                        val isCurrent = item.isCurrent
                        val isGuided = idx == guidedIndex
                        val hoverScale by animateFloatAsState(
                            targetValue = if (isGuided) 1.12f else 1.0f,
                            animationSpec = tween(durationMillis = 260),
                            label = "guidedHover"
                        )

                        Surface(
                            modifier = Modifier
                                .width(136.dp)
                                .height(204.dp)
                                .graphicsLayer {
                                    scaleX = hoverScale
                                    scaleY = hoverScale
                                }
                                .clip(androidx.compose.ui.graphics.RectangleShape)
                                .clickable {
                                    guidedIndex = idx
                                    lastTimelineInteractionTime = System.currentTimeMillis()
                                    if (item.id != 0 && item.id != currentAnime.id) {
                                        onSelectAnime(item.id, item)
                                    }
                                },
                            shape = androidx.compose.ui.graphics.RectangleShape,
                            color = Color.Black
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val cover = item.coverUrl.ifBlank { item.bannerUrl }
                                if (cover.isNotBlank()) {
                                    Image(
                                        painter = rememberAsyncImagePainter(cover),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Black.copy(alpha = 0.15f),
                                                        Color.Transparent,
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.65f),
                                                        Color.Black.copy(alpha = 0.90f)
                                                    )
                                                )
                                            )
                                    )
                                    // Compact subtle bottom gradient scrim so artwork remains prominent while keeping text legible
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                                )
                                            )
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black)
                                    )
                                }

                                // Top Tags Row: Format Pill Left, Current Tag Right
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val fmt = item.format.ifBlank { "ANIME" }.uppercase()
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = fmt,
                                            color = Color.White,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(appThemeColor)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "CURRENT",
                                                color = Color.White,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }

                                // Bottom Details: Title, Year/Episodes, Relation micro-pill
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    val relText = item.relationType.replace("_", " ").uppercase()
                                    val meta = listOfNotNull(
                                        item.year?.toString(),
                                        item.episodes?.let { "$it eps" }
                                    ).joinToString(" • ")
                                    if (meta.isNotBlank()) {
                                        Text(
                                            text = meta,
                                            color = Color(0xFFC0C2D0),
                                            fontSize = 9.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (relText.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.White.copy(alpha = 0.16f))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = relText,
                                                color = Color(0xFFE0E0EC),
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Atlus Guiding Dialogue BELOW Carousel with Animated Transition
                if (guidedSentence.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AnimatedContent(
                        targetState = activeGuidedItem,
                        transitionSpec = {
                            (fadeIn(tween(260)) + expandVertically(tween(260))).togetherWith(
                                fadeOut(tween(200)) + shrinkVertically(tween(200))
                            )
                        },
                        label = "AtlusTimelineDialogue"
                    ) { currentItem ->
                        val sentence = currentItem?.let { item ->
                            val format = item.format.ifBlank { "entry" }
                            val rel = item.relationType.replace("_", " ").lowercase()
                            val epText = if (item.episodes != null) " with ${item.episodes} eps" else ""
                            val yearText = if (item.year != null) " (${item.year})" else ""
                            "Next: Watch \"${item.title}\"$yearText — $rel $format$epText!"
                        } ?: ""

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GrokAtlasAvatar(
                                expression = AtlasExpression.HAPPY,
                                modifier = Modifier.size(36.dp)
                            )
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF1B1C23),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                    Text(
                                        text = "Atlus Guide",
                                        color = appThemeColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.4.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = sentence,
                                        color = Color(0xFFE2E4EC),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─── Chat Messages Area ───
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Inline API Key Setup Banner (Directly within chat stream, non-modal)
            if (showKeySetup || apiKey.isBlank()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        color = Color(0xFF24262C)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            GrokAtlasAvatar(
                                expression = AtlasExpression.HAPPY,
                                modifier = Modifier.size(34.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Welcome to Atlas Guide",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Ask anything about \"${currentAnime.title}\". Both Free (zero-credit models) and Pro modes require an OpenRouter API key from openrouter.ai/keys. Enter your key below to get started.",
                                color = Color(0xFFC5C7CE),
                                fontSize = 11.5.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 15.5.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF292B31))
                                    .padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = tempKeyInput,
                                    onValueChange = { tempKeyInput = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ),
                                    cursorBrush = SolidColor(Color.White),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        if (tempKeyInput.isEmpty()) {
                                            Text(
                                                text = "sk-or-v1-...",
                                                color = Color(0xFF989BA4),
                                                fontSize = 12.sp,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/keys")))
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Get API key", color = Color(0xFFD4D6DC), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val clean = tempKeyInput.trim()
                                        if (clean.isNotEmpty()) {
                                            AnimeBoxSettings.setOpenRouterApiKey(context, clean)
                                            AnimeBoxSettings.setOpenRouterFreeMode(context, true)
                                            apiKey = clean
                                            isFreeMode = true
                                            model.value = AnimeBoxSettings.DEFAULT_FREE_MODEL
                                            showKeySetup = false
                                            Toast.makeText(context, "Saved Key (Free $0 Model)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Both Free and Pro modes require an OpenRouter API key. Get one at openrouter.ai/keys", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF383A42),
                                        contentColor = Color(0xFFD4D6DC)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Text("Save Key (Free)", color = Color(0xFFD4D6DC), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        val clean = tempKeyInput.trim()
                                        if (clean.isNotEmpty()) {
                                            AnimeBoxSettings.setOpenRouterApiKey(context, clean)
                                            AnimeBoxSettings.setOpenRouterFreeMode(context, false)
                                            apiKey = clean
                                            isFreeMode = false
                                            model.value = AnimeBoxSettings.getProModel(context)
                                            showKeySetup = false
                                            Toast.makeText(context, "Saved Key (Pro Mode)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Both Free and Pro modes require an OpenRouter API key. Get one at openrouter.ai/keys", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF383A42)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Text("Save Key (Pro)", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

                // Welcome card if conversation just started
                if (messages.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            color = Color(0xFF24262C)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GrokAtlasAvatar(
                                        expression = AtlasExpression.HAPPY,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Atlas • Your Watch Order Guide",
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "Ask me anything about \"${currentAnime.title}\" and its franchise! I can recommend watch orders, generate comparison tables, start anime and Pokémon trivia games with interactive choices, and identify character screenshots.",
                                    color = Color(0xFFC5C7CE),
                                    fontSize = 12.sp,
                                    lineHeight = 16.5.sp
                                )
                            }
                        }
                    }
                }

                // Render Chat Messages
                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.role == "user"

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier
                                .align(if (isUser) Alignment.TopEnd else Alignment.TopStart)
                                .fillMaxWidth(if (isUser) 0.84f else 1f)
                                .padding(start = if (isUser) 0.dp else 34.dp)
                                .clip(
                                    RoundedCornerShape(
                                            topStart = 12.dp,
                                        topEnd = 12.dp,
                                            bottomStart = 12.dp,
                                            bottomEnd = 12.dp
                                    )
                                ),
                            color = if (isUser) Color(0xFF303136) else Color(0xFF202126).copy(alpha = 0.96f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                if (!isUser) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Atlas",
                                                color = Color(0xFFD8D9DE),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(msg.text))
                                                Toast.makeText(context, "Copied response to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = AiChatCopyIconVector,
                                                contentDescription = "Copy",
                                                tint = Color(0xFF888896),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }

                                // Uploaded user image thumbnail (if present)
                                if (isUser && msg.base64Image != null) {
                                    val decodedBitmap = remember(msg.base64Image) {
                                        try {
                                            val bytes = Base64.decode(msg.base64Image, Base64.DEFAULT)
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                    if (decodedBitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = decodedBitmap,
                                            contentDescription = "Uploaded image",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .padding(bottom = 8.dp)
                                                .size(120.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                }

                                // Google Lens Image Search Results Strip (Prominently displayed at top of message)
                                if (!msg.searchImages.isNullOrEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF13141B))
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                                            ) {
                                                Icon(
                                                    imageVector = CustomSearchIcon,
                                                    contentDescription = "Search Images",
                                                    tint = Color(0xFF8AB4F8),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Image Gallery",
                                                    color = Color(0xFFE2E8F8),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = "${msg.searchImages.size} results",
                                                color = Color(0xFF7E849A),
                                                fontSize = 10.5.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            itemsIndexed(
                                                items = msg.searchImages,
                                                key = { idx, item -> "${item.imageUrl}_$idx" }
                                            ) { _, imgItem ->
                                                val thumbUrl = imgItem.thumbnailUrl.ifBlank { imgItem.imageUrl }
                                                Surface(
                                                    modifier = Modifier
                                                        .width(135.dp)
                                                        .height(110.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { selectedPreviewImage = imgItem },
                                                    color = Color(0xFF20202C),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Box(modifier = Modifier.fillMaxSize()) {
                                                        Image(
                                                            painter = rememberAsyncImagePainter(model = thumbUrl),
                                                            contentDescription = imgItem.title,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                        // Subtle bottom scrim with title
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .align(Alignment.BottomCenter)
                                                                .background(
                                                                    Brush.verticalGradient(
                                                                        colors = listOf(
                                                                            Color.Transparent,
                                                                            Color.Black.copy(alpha = 0.85f)
                                                                        )
                                                                    )
                                                                )
                                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(
                                                                text = imgItem.title,
                                                                color = Color(0xFFE5E5EF),
                                                                fontSize = 9.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                if (isUser) {
                                    FormattedAiChatMessage(
                                        text = msg.text,
                                        isError = msg.isError
                                    )
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(msg.text))
                                                Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(AiChatCopyIconVector, contentDescription = "Copy your message", tint = Color(0xFF888896), modifier = Modifier.size(14.dp))
                                        }
                                    }
                                } else {
                                    FormattedAiChatMessage(
                                        text = msg.text,
                                        isError = msg.isError
                                    )
                                }

                                val priorUserPromptForVideos = messages.takeWhile { it.id != msg.id }
                                    .lastOrNull { it.role == "user" }?.text.orEmpty()
                                val isPlayReq = Regex("(?i)\\b(?:play|start|begin|put on|card|episode card|stream)\\b").containsMatchIn(priorUserPromptForVideos)
                                val hasPlayIntent = isPlayReq || msg.playAction != null

                                // YouTube Video Results (Trailers, Scenes, Fight Moments)
                                // Suppressed on episode playback requests or when episode play button is shown
                                if (msg.role != "user" && !hasPlayIntent && !msg.searchVideos.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        for (video in msg.searchVideos) {
                                            AiYouTubeVideoCard(video = video)
                                        }
                                    }
                                }

                                // Rate limit / API key / 402 balance error recovery card
                                val isRateLimitOrKeyError = msg.isError && (
                                    msg.text.contains("429", ignoreCase = true) ||
                                    msg.text.contains("402", ignoreCase = true) ||
                                    msg.text.contains("balance", ignoreCase = true) ||
                                    msg.text.contains("quota", ignoreCase = true) ||
                                    msg.text.contains("credits", ignoreCase = true) ||
                                    msg.text.contains("rate limit", ignoreCase = true) ||
                                    msg.text.contains("api key", ignoreCase = true) ||
                                    msg.text.contains("unauthorized", ignoreCase = true)
                                )
                                if (isRateLimitOrKeyError) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF292B31).copy(alpha = 0.98f)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                GrokAtlasAvatar(
                                                    expression = AtlasExpression.SCARED,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = if (msg.text.contains("402") || msg.text.contains("balance")) "OpenRouter Balance Empty (HTTP 402)" else "API Key / Rate Limit Notice",
                                                    color = Color(0xFFE3E4E8),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "Both Free and Pro modes require an OpenRouter API key. If your Pro balance is empty, you can switch to a Free ($0 credit) model or update your key.",
                                                color = Color(0xFFC5C7CE),
                                                fontSize = 10.5.sp
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = {
                                                        if (apiKey.isBlank()) {
                                                            showKeySetup = true
                                                            Toast.makeText(context, "An OpenRouter API key is required. Please set your key in setup.", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            isFreeMode = true
                                                            AnimeBoxSettings.setOpenRouterFreeMode(context, true)
                                                            model.value = AnimeBoxSettings.DEFAULT_FREE_MODEL
                                                            Toast.makeText(context, "Switched to Free ($0 credit)", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF3A3B42),
                                                        contentColor = Color(0xFFE5E6EA)
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.weight(1f).height(34.dp)
                                                ) {
                                                    Text("Switch to Free", color = Color(0xFFE5E6EA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Button(
                                                    onClick = { showKeySetup = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = appThemeColor),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.weight(1f).height(34.dp)
                                                ) {
                                                    Text("Update Key", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Provide a playable action when Atlas answers a play/episode request in prose
                                // instead of returning its structured play_episode payload.
                                val priorUserMessages = messages.takeWhile { it.id != msg.id }.filter { it.role == "user" }
                                val priorUserPrompt = priorUserMessages.lastOrNull()?.text.orEmpty()
                                val combinedRecentUserPrompts = priorUserMessages.takeLast(2).joinToString(" ") { it.text }
                                val isWatchRecommendationRequest = Regex(
                                    "(?i)\\b(?:what|which|recommend|suggest|pick)\\b.{0,60}\\b(?:watch|play)\\b"
                                ).containsMatchIn(priorUserPrompt)
                                val explicitPlaybackRequest = !isWatchRecommendationRequest && Regex(
                                    "(?i)\\b(?:play|start|begin|put on|card|episode card|stream|watch)\\b"
                                ).containsMatchIn(combinedRecentUserPrompts)
                                val isSeasonSelection = !isWatchRecommendationRequest && (
                                    priorUserPrompt.contains("season", ignoreCase = true) ||
                                    priorUserMessages.takeLast(3).any {
                                        Regex("(?i)\\b(?:play|start|begin|put on|card|episode card|stream|episode|ep\\.?|finale|last episode|final episode|first episode|premiere)\\b").containsMatchIn(it.text)
                                    }
                                )
                                val isUserMessage = msg.role == "user"
                                val naturalPlayTarget = if (!isUserMessage) {
                                    OpenRouterChatClient.matchFranchiseSeason(
                                        priorUserPrompt,
                                        "$combinedRecentUserPrompts\n${msg.text}",
                                        franchiseItems
                                    ) ?: franchiseItems.find { it.id == currentAnime.id }
                                } else null
                                val detectedEpNum = if (!isUserMessage) {
                                    OpenRouterChatClient.extractEpisodeNumber(
                                        combinedRecentUserPrompts,
                                        msg.text,
                                        naturalPlayTarget?.episodes
                                    ) ?: if (explicitPlaybackRequest) 1 else null
                                } else null
                                val isCardRequest = (explicitPlaybackRequest || isSeasonSelection || detectedEpNum != null) && msg.quizData == null
                                val playAction = if (isUserMessage) null else (msg.playAction ?: if (isCardRequest && detectedEpNum != null) {
                                    com.lagradost.cloudstream3.ui.animebox.api.AiPlayEpisodeAction(
                                        animeId = naturalPlayTarget?.id ?: currentAnime.id,
                                        animeTitle = naturalPlayTarget?.title ?: currentAnime.title,
                                        episodeNum = detectedEpNum,
                                        maxEpisodes = naturalPlayTarget?.episodes
                                    )
                                } else null)

                                // Fast Episode Play Button with Widescreen Thumbnail & Instant Launch
                                if (playAction != null && !playAction.isOutOfRange && msg.quizData == null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val action = playAction
                                    val targetFranchiseItem = franchiseItems.find { it.id == action.animeId && it.id != 0 }
                                        ?: OpenRouterChatClient.matchFranchiseSeason(action.animeTitle, msg.text, franchiseItems)
                                        ?: franchiseItems.find { action.animeTitle.isNotBlank() && it.title.equals(action.animeTitle, ignoreCase = true) }
                                    val effectiveTargetId = targetFranchiseItem?.id ?: action.animeId.takeIf { it != 0 } ?: currentAnime.id
                                    val isCurrentAnimeTarget = (effectiveTargetId == currentAnime.id)

                                    val defaultTargetCover = targetFranchiseItem?.bannerUrl?.takeIf { it.isNotBlank() }
                                        ?: targetFranchiseItem?.coverUrl?.takeIf { it.isNotBlank() }
                                        ?: currentAnime.bannerUrl?.takeIf { it.isNotBlank() }
                                        ?: currentAnime.coverUrl

                                    val resolvedEpCover by produceState<String>(
                                        initialValue = if (isCurrentAnimeTarget) {
                                            episodeMetaMap[action.episodeNum]?.imageUrl?.takeIf { it.isNotBlank() } ?: defaultTargetCover
                                        } else {
                                            defaultTargetCover
                                        },
                                        key1 = effectiveTargetId,
                                        key2 = action.episodeNum
                                    ) {
                                        if (isCurrentAnimeTarget) {
                                            val epImg = episodeMetaMap[action.episodeNum]?.imageUrl?.takeIf { it.isNotBlank() }
                                            if (!epImg.isNullOrBlank()) {
                                                value = epImg
                                            }
                                        } else {
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    val custom = com.lagradost.cloudstream3.ui.animebox.api.EpisodeCoverUploader.getLocalOrUploadedEpisodeCover(
                                                        context, effectiveTargetId, action.episodeNum, action.animeTitle
                                                    ) ?: com.lagradost.cloudstream3.ui.animebox.api.EpisodeCoverUploader.getCustomEpisodeCover(
                                                        context, effectiveTargetId, action.episodeNum, action.animeTitle
                                                    )
                                                    if (!custom.isNullOrBlank()) {
                                                        value = custom
                                                        return@withContext
                                                    }
                                                    val targetMetaMap = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getEpisodeMetadata(effectiveTargetId)
                                                    val meta = targetMetaMap[action.episodeNum]
                                                    val tmdbId = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getLongRunningTmdbId(effectiveTargetId)
                                                    if (tmdbId != null && meta != null) {
                                                        val tmdbImg = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getTmdbEpisodeImage(tmdbId, meta.seasonNumber, meta.episodeNumber)
                                                        if (tmdbImg.isNotBlank()) {
                                                            value = tmdbImg
                                                            return@withContext
                                                        }
                                                    }
                                                    val metaImg = meta?.imageUrl
                                                    if (!metaImg.isNullOrBlank()) {
                                                        value = metaImg
                                                        return@withContext
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    }

                                    EpisodePlayButtonCard(
                                        action = if (targetFranchiseItem != null && (action.animeId != targetFranchiseItem.id || action.animeTitle != targetFranchiseItem.title)) {
                                            action.copy(animeId = targetFranchiseItem.id, animeTitle = targetFranchiseItem.title)
                                        } else action,
                                        coverUrl = resolvedEpCover,
                                        onPlay = { aId, ep, title ->
                                            val resolvedId = if (aId != 0) aId else effectiveTargetId
                                            val resolvedTitle = if (title.isNotBlank()) title else (targetFranchiseItem?.title ?: currentAnime.title)
                                            onPlayEpisode(resolvedId, ep, resolvedTitle, targetFranchiseItem)
                                        }
                                    )
                                }

                                // Interactive 4-Choice MCQ Options (For Character/Anime/Pokémon Quizzes & Games)
                                if (msg.quizData != null && msg.quizData.options.isNotEmpty()) {
                                    val rawQuestion = msg.quizData.question
                                    val cleanQuestion = stripAtlasEmoji(rawQuestion)
                                    val isSeasonPrompt = cleanQuestion.contains("season", ignoreCase = true) ||
                                        msg.quizData.options.any { it.text.contains("season", ignoreCase = true) }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    if (cleanQuestion.isNotBlank()) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp)),
                                            color = Color(0xFF1E202B)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = cleanQuestion,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    lineHeight = 18.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    } else {
                                        Text(
                                            text = if (isSeasonPrompt) "Select a Season" else "Interactive Choices — Tap to Select",
                                            color = Color(0xFFE5E5EF),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        msg.quizData.options.forEach { option ->
                                            val selectedAnswer = answeredQuizIds[msg.id]
                                            val cleanOptionText = stripAtlasEmoji(option.text)
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable(enabled = !isGenerating && selectedAnswer == null) {
                                                        answeredQuizIds[msg.id] = option.label
                                                        if (isSeasonPrompt) {
                                                            val origRequest = messages.takeWhile { it.id != msg.id }
                                                                .lastOrNull { it.role == "user" }?.text.orEmpty()
                                                            if (origRequest.isNotBlank() && !origRequest.equals(cleanOptionText, ignoreCase = true)) {
                                                                sendMessage("$cleanOptionText (for my request: $origRequest)")
                                                            } else {
                                                                sendMessage(cleanOptionText)
                                                            }
                                                        } else {
                                                            val previousQuestion = cleanQuestion.trim().replace('"', '\'')
                                                            sendMessage("My answer to the current quiz is ${option.label}: ${cleanOptionText}. The previous question was: '$previousQuestion'. Briefly evaluate my answer, then continue with a genuinely different question about another detail and four fresh A-D choices. Do not repeat or paraphrase the previous question.")
                                                        }
                                                    },
                                                color = if (selectedAnswer == option.label) Color(0xFF44464D) else Color(0xFF292A30)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    if (!isSeasonPrompt) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .clip(CircleShape)
                                                                .background(appThemeColor.copy(alpha = 0.25f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = option.label,
                                                                color = appThemeColor,
                                                                fontSize = 11.5.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    if (option.imageUrl.isNotBlank()) {
                                                        Surface(
                                                            modifier = Modifier
                                                                .size(42.dp)
                                                                .clip(RoundedCornerShape(6.dp)),
                                                            color = Color(0xFF16161E)
                                                        ) {
                                                            Image(
                                                                painter = rememberAsyncImagePainter(option.imageUrl),
                                                                contentDescription = option.text,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        }
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = cleanOptionText,
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Normal,
                                                            lineHeight = 18.sp,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (option.note.isNotBlank()) {
                                                            Text(
                                                                text = option.note,
                                                                color = Color(0xFF9E9EAE),
                                                                fontSize = 10.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                        contentDescription = null,
                                                        tint = Color(0xFF7E7E8E),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Interactive Visual Watch Order Roadmap Flow / iiDB Anime Games Cards (If detected)
                                val validWatchOrderItems = msg.watchOrderMap.orEmpty().filter { item ->
                                    item.title.isNotBlank() && !item.title.equals("null", ignoreCase = true)
                                }
                                if (validWatchOrderItems.isNotEmpty() && msg.searchImages.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    val dynamicTitle = msg.sectionTitle?.ifBlank { null } ?: "Recommended Watch Flow (Tap card to view)"
                                    Text(
                                        text = dynamicTitle,
                                        color = Color(0xFFE5E5EF),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        itemsIndexed(validWatchOrderItems) { mapIdx, item ->
                                            val isLast = mapIdx == validWatchOrderItems.size - 1

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val effectiveImage = item.bannerUrl.ifBlank { item.coverUrl }

                                                Surface(
                                                    modifier = Modifier
                                                        .width(160.dp)
                                                        .height(140.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable {
                                                            if (item.iidbUrl.isNotBlank()) {
                                                                try {
                                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.iidbUrl))
                                                                    context.startActivity(intent)
                                                                } catch (_: Exception) {
                                                                    if (item.id > 0) onSelectAnime(item.id, null)
                                                                }
                                                            } else if (item.id > 0) {
                                                                onSelectAnime(item.id, null)
                                                            }
                                                        },
                                                    color = Color(0xFF22222C)
                                                ) {
                                                    Column(modifier = Modifier.fillMaxSize()) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(82.dp)
                                                                .background(Color(0xFF141418))
                                                        ) {
                                                            if (effectiveImage.isNotBlank()) {
                                                                Image(
                                                                    painter = rememberAsyncImagePainter(effectiveImage),
                                                                    contentDescription = item.title,
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
                                                                                    Color(0x551A1A22),
                                                                                    Color(0xFF22222C)
                                                                                )
                                                                            )
                                                                        )
                                                                )
                                                            }
                                                            if (item.badge.isNotEmpty()) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .align(Alignment.TopStart)
                                                                        .clip(RoundedCornerShape(bottomEnd = 6.dp))
                                                                        .background(Color.Black.copy(alpha = 0.80f))
                                                                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                                                                ) {
                                                                    Text(
                                                                        text = item.badge,
                                                                        color = Color.White,
                                                                        fontSize = 8.5.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        maxLines = 1
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .weight(1f)
                                                                .padding(horizontal = 8.dp, vertical = 5.dp),
                                                            verticalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = item.title,
                                                                color = Color.White,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis,
                                                                lineHeight = 14.5.sp
                                                            )
                                                            val metaNote = item.note.ifBlank {
                                                                listOfNotNull(item.format.takeIf { it.isNotBlank() }, item.year?.toString()).joinToString(" • ")
                                                            }
                                                            if (metaNote.isNotEmpty()) {
                                                                Text(
                                                                    text = metaNote,
                                                                    color = Color(0xFFB0B0BE),
                                                                    fontSize = 9.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                if (!isLast) {
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(horizontal = 4.dp)
                                                            .size(22.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF23232E)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                            contentDescription = null,
                                                            tint = Color(0xFFD0D0DE),
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (!isUser) {
                            GrokAtlasAvatar(
                                expression = if (msg.isError) AtlasExpression.SCARED else AtlasExpression.NEUTRAL,
                                modifier = Modifier.align(Alignment.TopStart).size(28.dp)
                            )
                        }
                    }
                }

                // AI Generating Indicator
                if (isGenerating) {
                    val latestPrompt = messages.lastOrNull { it.role == "user" }?.text.orEmpty().lowercase()
                    val loadingLabel = when {
                        latestPrompt.contains("canon") || latestPrompt.contains("filler") -> "Finding canon and filler episodes..."
                        latestPrompt.contains("quiz") || latestPrompt.contains("trivia") -> "Preparing your quiz..."
                        latestPrompt.contains("watch order") || latestPrompt.contains("chronological") || latestPrompt.contains("timeline") -> "Building your watch order..."
                        latestPrompt.contains("trailer") || latestPrompt.contains("fight scene") || latestPrompt.contains("youtube") -> "Finding video results..."
                        else -> "Atlas is thinking..."
                    }

                    item {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp)),
                            color = Color(0xFF1B1B22)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                com.lagradost.cloudstream3.ui.animebox.components.ThemeDotCircularProgressIndicator(
                                    color = appThemeColor,
                                    size = 18.dp
                                )
                                if (loadingLabel != "Atlas is thinking...") {
                                    CurvedHillWaveText(
                                        text = loadingLabel,
                                        color = appThemeColor,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        text = loadingLabel,
                                        color = Color(0xFFB0B0BC),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
        }

            // ─── Quick Suggestion Chips (Styled matching User Image 3 pills with Solar linear icons) ───
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestionChips.size) { idx ->
                    val chipText = suggestionChips[idx]
                    val chipIcon = when {
                        chipText.contains("Trailer", ignoreCase = true) -> SolarClapperboardLinearIcon
                        chipText.contains("Order", ignoreCase = true) || chipText.contains("Route", ignoreCase = true) -> SolarCompassLinearIcon
                        chipText.contains("Canon", ignoreCase = true) || chipText.contains("Guide", ignoreCase = true) -> SolarChecklistLinearIcon
                        chipText.contains("Episode", ignoreCase = true) || chipText.contains("Play", ignoreCase = true) -> SolarPlayLinearIcon
                        chipText.contains("Quiz", ignoreCase = true) || chipText.contains("Trivia", ignoreCase = true) -> SolarGamepadLinearIcon
                        else -> SolarCompassLinearIcon
                    }
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(enabled = !isGenerating) {
                                sendMessage(chipText)
                            },
                        color = Color(0xFF1B1C22)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = chipIcon,
                                contentDescription = null,
                                tint = Color(0xFFC5C6D0),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = chipText,
                                color = Color(0xFFEDEDF2),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ─── Bottom Chat Input Bar (Claude-Style Rounded Capsule, User Image 1) ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0D11))
                    .then(
                        if (isImeOpen) Modifier.imePadding()
                        else if (isFullscreen) Modifier.navigationBarsPadding()
                        else Modifier
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp)),
                    color = Color(0xFF1E1F24)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Upper text input
                        if (attachedBase64Image != null || attachedImageUri != null) {
                            Row(
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2B2C35))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (attachedImageUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(attachedImageUri),
                                        contentDescription = "Attached preview",
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                                Text("Image attached", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f))
                                        .clickable {
                                            attachedBase64Image = null
                                            attachedImageUri = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(11.dp))
                                }
                            }
                        }

                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.5.sp
                            ),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            decorationBox = { innerTextField ->
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = "Reply to Atlus...",
                                        color = Color(0xFF888894),
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        // Lower action row: [+] button, Model pill, Spacer, [Mic] button, [Send/Waveform] circle button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // [+] circle button (Upload image for multimodal AI)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2B2C33))
                                        .clickable {
                                            imagePickerLauncher.launch("image/*")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Attach Image",
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                // Model Selector Capsule Pill (Pro / Free Model)
                                AiModelSelectorDropdown(
                                    isFreeMode = isFreeMode,
                                    onModeSelected = { newFreeMode ->
                                        isFreeMode = newFreeMode
                                        AnimeBoxSettings.setOpenRouterFreeMode(context, newFreeMode)
                                        model.value = if (newFreeMode) AnimeBoxSettings.DEFAULT_FREE_MODEL else AnimeBoxSettings.getProModel(context)
                                        Toast.makeText(
                                            context,
                                            if (newFreeMode) "Switched to Free Model" else "Switched to Pro",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Solar microphone-3 button (Transcribes spoken words directly to keyboard input)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2B2C33))
                                        .clickable {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Atlus...")
                                            }
                                            try {
                                                speechLauncher.launch(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = SolarMicrophone3Icon,
                                        contentDescription = "Voice",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Send circular button (active when input present)
                                val canSend = inputText.trim().isNotEmpty() && !isGenerating
                                if (canSend) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(appThemeColor)
                                            .clickable {
                                                sendMessage(inputText)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        // ─── High-Resolution Fullscreen Image Viewer Dialog ───
        selectedPreviewImage?.let { activeImg ->
            Dialog(
                onDismissRequest = { selectedPreviewImage = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.94f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { selectedPreviewImage = null }
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .widthIn(max = 680.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF161620))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { /* prevent backdrop click-through */ }
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top header: Google Lens icon + Title + Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = CustomSearchIcon,
                                    contentDescription = "Image Preview",
                                    tint = Color(0xFF8AB4F8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = activeImg.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { selectedPreviewImage = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFFB8B8C8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Full image preview: fits naturally on both phones and tablets
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 460.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F0F16)),
                            contentAlignment = Alignment.Center
                        ) {
                            val fullImg = activeImg.imageUrl.ifBlank { activeImg.thumbnailUrl }
                            Image(
                                painter = rememberAsyncImagePainter(model = fullImg),
                                contentDescription = activeImg.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action buttons: Open Source & Close
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val openTarget = activeImg.sourceUrl.ifBlank { activeImg.imageUrl }
                            if (openTarget.isNotBlank() && openTarget.startsWith("http")) {
                                TextButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(openTarget))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Cannot open image source link", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "Open Source",
                                        color = Color(0xFF8AB4F8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            Button(
                                onClick = { selectedPreviewImage = null },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B38)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Close",
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
}

// ─── Watch Order Timeline Entry (Borderless Cards with Solid White Outline on Current) ───

@Composable
fun WatchOrderTimelineEntry(
    stepNumber: Int,
    item: WatchOrderItem,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val isCurrent = item.isCurrent

    val hasMultipleEpisodes = (item.episodes ?: 0) > 1
    val isTvFormat = item.format.equals("TV", ignoreCase = true) || item.format.equals("TV_SHORT", ignoreCase = true)

    val isMovie = item.id == 1689 || (!hasMultipleEpisodes && !isTvFormat && (
        item.format.equals("MOVIE", ignoreCase = true) ||
        ((item.episodes == 1 || item.format.equals("SPECIAL", ignoreCase = true) || item.format.equals("OVA", ignoreCase = true)) && (
            item.relationType.contains("MOVIE", ignoreCase = true) ||
            com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.isStaticMovie(item.id)
        ))
    ))

    val displayTitle = if (item.title.isBlank() || item.title.equals("null", ignoreCase = true)) {
        "Anime #${item.id}"
    } else {
        item.title
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(bottom = if (isLast) 0.dp else 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline Column (Circle + Dynamic Connecting Line)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
                .padding(top = 10.dp)
        ) {
            // Node Dot
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22232D)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$stepNumber",
                        color = Color(0xFFC5C5D2),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Connecting vertical line to next node (fills card height dynamically)
            if (!isLast) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (isCurrent) Color.White.copy(alpha = 0.65f) else Color(0xFF383848),
                                    Color(0xFF20212C)
                                )
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Right Info Card:
        // Preserves solid white outline ONLY on currently viewing anime.
        // All other boxes have NO thin border outlines!
        val cardBorder = if (isCurrent) {
            Modifier.border(1.5.dp, Color.White, RoundedCornerShape(12.dp))
        } else {
            Modifier
        }

        val cardBg = if (isCurrent) Color(0xFF20202A) else Color(0xFF16161D)

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .then(cardBorder)
                .background(cardBg)
                .clickable { onClick() }
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Poster with Format Badge
                Box(
                    modifier = Modifier
                        .width(68.dp)
                        .height(98.dp)
                        .clip(androidx.compose.ui.graphics.RectangleShape)
                        .background(Color(0xFF101014))
                ) {
                    if (item.coverUrl.isNotEmpty()) {
                        Image(
                            painter = rememberAsyncImagePainter(item.coverUrl),
                            contentDescription = displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    val formatLabel = if (isMovie) "MOVIE" else item.format.ifEmpty { "ANIME" }.uppercase()
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatLabel,
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Info Column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Top Badges Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "CURRENTLY VIEWING",
                                    color = Color(0xFF111115),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }

                        val relLabel = when {
                            isMovie -> "Movie"
                            item.relationType.equals("PREQUEL", ignoreCase = true) -> "Prequel"
                            item.relationType.equals("SEQUEL", ignoreCase = true) -> "Sequel"
                            item.relationType.equals("SIDE_STORY", ignoreCase = true) -> if (isMovie) "Movie" else "Side Story"
                            item.relationType.equals("SPIN_OFF", ignoreCase = true) -> "Spin-off"
                            item.relationType.equals("ALTERNATIVE", ignoreCase = true) -> "Alternative"
                            item.relationType.equals("PARENT", ignoreCase = true) -> "Main Story"
                            item.relationType.isNotEmpty() && !item.relationType.equals("Movie", ignoreCase = true) -> item.relationType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                            else -> if (isTvFormat) "Main Story" else item.format.ifEmpty { "Series" }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF262632))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = relLabel,
                                color = Color(0xFFD4D4E0),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Anime Title
                    Text(
                        text = displayTitle,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Metadata Row: Year • Episodes • Status
                    val metaParts = mutableListOf<String>()
                    if (item.year != null && item.year > 0) metaParts.add("${item.year}")
                    if (item.episodes != null && item.episodes > 0) {
                        metaParts.add(if (item.episodes == 1) "1 ep" else "${item.episodes} eps")
                    }
                    if (item.status.isNotEmpty()) {
                        metaParts.add(item.status.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
                    }
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString(" • "),
                            color = Color(0xFF9E9EA8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (item.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = item.description,
                            color = Color(0xFF7E7E8C),
                            fontSize = 10.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}
