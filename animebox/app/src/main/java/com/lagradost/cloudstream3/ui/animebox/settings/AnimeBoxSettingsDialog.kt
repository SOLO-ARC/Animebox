package com.lagradost.cloudstream3.ui.animebox.settings

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.lagradost.cloudstream3.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lagradost.cloudstream3.ui.animebox.CustomSearchIcon
import com.lagradost.cloudstream3.ui.animebox.download.AnimeDownloadManager
import com.lagradost.cloudstream3.ui.animebox.download.StorageStats
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ----------------------------------------------------------------------------
// Official Google Material 24x24 Vector Path Data
// ----------------------------------------------------------------------------

enum class SettingsIconType(val pathData: String) {
    CHEVRON_LEFT("M14.71 6.71a.996.996 0 0 0-1.41 0L8.71 11.3a.996.996 0 0 0 0 1.41l4.59 4.59c.39.39 1.02.39 1.41 0 .39-.39.39-1.02 0-1.41L10.83 12l3.88-3.88c.39-.39.38-1.03 0-1.41z"),
    CHEVRON_RIGHT("M9.29 6.71c-.39.39-.39 1.02 0 1.41L13.17 12l-3.88 3.88c-.39.39-.39 1.02 0 1.41.39.39 1.02.39 1.41 0l4.59-4.59c.39-.39.39-1.02 0-1.41L10.7 6.7c-.38-.38-1.02-.38-1.41.01z"),
    WIFI("M12 3c-4.21 0-8.03 1.71-10.78 4.46a.996.996 0 0 0 0 1.41c.39.39 1.02.39 1.41 0C5.1 6.4 8.39 5 12 5s6.9 1.4 9.37 3.87c.39.39 1.02.39 1.41 0a.996.996 0 0 0 0-1.41C20.03 4.71 16.21 3 12 3zm0 4.5c-3.04 0-5.8 1.23-7.78 3.22a.996.996 0 0 0 0 1.41c.39.39 1.02.39 1.41 0 1.63-1.63 3.89-2.63 6.37-2.63s4.74 1 6.37 2.63c.39.39 1.02.39 1.41 0a.996.996 0 0 0 0-1.41C17.8 8.73 15.04 7.5 12 7.5zm0 4.5c-1.8 0-3.43.73-4.6 1.9a.996.996 0 0 0 0 1.41c.39.39 1.02.39 1.41 0 .8-.8 1.9-1.31 3.19-1.31s2.39.51 3.19 1.31c.39.39 1.02.39 1.41 0a.996.996 0 0 0 0-1.41C15.43 12.73 13.8 12 12 12zm0 5c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"),
    SMART_DOWNLOADS("M12 4V2.21c0-.45-.54-.67-.85-.35l-2.8 2.79c-.2.2-.2.51 0 .71l2.79 2.79c.32.31.86.09.86-.36V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46A7.93 7.93 0 0 0 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74A7.93 7.93 0 0 0 4 12c0 4.42 3.58 8 8 8v1.79c0 .45.54.67.85.35l2.79-2.79c.2-.2.2-.51 0-.71l-2.79-2.79c-.31-.31-.85-.09-.85.36V18z"),
    DOWNLOAD_QUALITY("M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"),
    STORAGE_LOCATION("M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z"),
    DELETE_DOWNLOADS("M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"),
    APP_THEME("M12 2C6.49 2 2 6.49 2 12s4.49 10 10 10c1.38 0 2.5-1.12 2.5-2.5 0-.61-.23-1.2-.64-1.67-.08-.1-.13-.21-.13-.33 0-.28.22-.5.5-.5H16c3.31 0 6-2.69 6-6 0-4.96-4.49-9-10-9zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 8 6.5 8 8 8.67 8 9.5 7.33 11 6.5 11zm3-4C8.67 7 8 6.33 8 5.5S8.67 4 9.5 4s1.5.67 1.5 1.5S10.33 7 9.5 7zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 4 14.5 4s1.5.67 1.5 1.5S15.33 7 14.5 7zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 8 17.5 8s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z"),
    EPISODE_VIEW("M4 10.5c-.83 0-1.5.67-1.5 1.5s.67 1.5 1.5 1.5h16c.83 0 1.5-.67 1.5-1.5s-.67-1.5-1.5-1.5H4zm0-6c-.83 0-1.5.67-1.5 1.5s.67 1.5 1.5 1.5h16c.83 0 1.5-.67 1.5-1.5s-.67-1.5-1.5-1.5H4zm0 12c-.83 0-1.5.67-1.5 1.5s.67 1.5 1.5 1.5h16c.83 0 1.5-.67 1.5-1.5s-.67-1.5-1.5-1.5H4z"),
    TRAILERS_TV("M21 3H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h5v1c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-1h5c1.1 0 1.99-.9 1.99-2L23 5c0-1.1-.9-2-2-2zm-1 14H4c-.55 0-1-.45-1-1V6c0-.55.45-1 1-1h16c.55 0 1 .45 1 1v10c0 .55-.45 1-1 1z"),
    LOW_PERF("M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.28-10.43zM10.59 15.41a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z"),
    TIMELINE_COLOR("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm.5-13c0-.55-.45-1-1-1s-1 .45-1 1v5c0 .34.17.65.45.83l4 2.4c.47.28 1.08.13 1.37-.34.28-.47.13-1.08-.34-1.37L12.5 12.2V7z"),
    SKIP_INTRO("M5.58 16.89l5.77-4.07c.56-.4.56-1.24 0-1.63L5.58 7.11C4.91 6.65 4 7.12 4 7.93v8.14c0 .81.91 1.28 1.58.82zM16 7c-.55 0-1 .45-1 1v8c0 .55.45 1 1 1s1-.45 1-1V8c0-.55-.45-1-1-1z"),
    BRIGHTNESS("M20 8.69V4h-4.69L12 .69 8.69 4H4v4.69L.69 12 4 15.31V20h4.69L12 23.31 15.31 20H20v-4.69L23.31 12 20 8.69zM12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6 6 2.69 6 6-2.69 6-6 6z"),
    VOLUME("M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"),
    DOUBLE_TAP("M12 5V2.21c0-.45-.54-.67-.85-.35l-2.8 2.79c-.2.2-.2.51 0 .71l2.79 2.79c.32.31.86.09.86-.36V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6c0-.55-.45-1-1-1s-1 .45-1 1c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z"),
    REMEMBER_PREFS("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1.29 14.29a.996.996 0 0 1-1.41 0L6.7 13.7a.996.996 0 1 1 1.41-1.41L10 14.17l5.88-5.88a.996.996 0 1 1 1.41 1.41l-6.58 6.59z"),
    EXPORT("M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM13.65 14.35l-1.3-1.29V17c0 .55-.45 1-1 1s-1-.45-1-1v-3.94l-1.29 1.29a.996.996 0 1 1-1.41-1.41l3.05-3.05c.39-.39 1.02-.39 1.41 0l3.05 3.05a.996.996 0 0 1-1.41 1.41z"),
    IMPORT("M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM12 17l-5-5h3V8h4v4h3l-5 5z"),
    DNS_SERVER("M19 13H5c-1.1 0-2 .9-2 2v4c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2v-4c0-1.1-.9-2-2-2zM7 19c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zM19 3H5c-1.1 0-2 .9-2 2v4c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM7 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z"),
    ABOUT_DEVICE("M16 1H8C6.34 1 5 2.34 5 4v16c0 1.66 1.34 3 3 3h8c1.66 0 3-1.34 3-3V4c0-1.66-1.34-3-3-3zm-2 20h-4c-.55 0-1-.45-1-1s.45-1 1-1h4c.55 0 1 .45 1 1s-.45 1-1 1zm3-3H7c-.55 0-1-.45-1-1V5c0-.55.45-1 1-1h10c.55 0 1 .45 1 1v12c0 .55-.45 1-1 1z"),
    SUPPORT_TICKET("M20,2H4C2.9,2 2,2.9 2,4v18l4,-4h14c1.1,0 2,-0.9 2,-2V4C22,2.9 21.1,2 20,2zM17,13H7v-1.5h10V13zM17,10H7V8.5h10V10zM17,7H7V5.5h10V7z")
}

@Composable
fun PixelSettingsIcon(
    type: SettingsIconType,
    tint: Color = Color(0xFFE2E2E6),
    modifier: Modifier = Modifier,
    sizeDp: Int = 24
) {
    val path = remember(type) {
        PathParser().parsePathString(type.pathData).toPath()
    }

    Canvas(modifier = modifier.size(sizeDp.dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            drawPath(path, color = tint)
        }
    }
}

// ----------------------------------------------------------------------------
// Minimalist Monochrome Skeleton Shimmer
// ----------------------------------------------------------------------------

@Composable
fun Modifier.monochromeShimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return this.background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF0F0F0F),
                Color(0xFF222222),
                Color(0xFF0F0F0F)
            ),
            start = Offset(translateAnim - 400f, translateAnim - 400f),
            end = Offset(translateAnim, translateAnim)
        )
    )
}

// ----------------------------------------------------------------------------
// Main Black & White App Settings Dialog
// ----------------------------------------------------------------------------

@Composable
fun AnimeBoxSettingsDialog(
    onDismiss: () -> Unit,
    onSettingsChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Skeleton Loading State
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(280)
        isLoading = false
    }

    // Search query
    var searchQuery by remember { mutableStateOf("") }

    // Preference States
    var trailerEnabled by remember { mutableStateOf(try { AnimeBoxSettings.isTrailerEnabled(context) } catch (_: Exception) { false }) }
    var lowPerfMode by remember { mutableStateOf(try { AnimeBoxSettings.isLowPerformanceMode(context) } catch (_: Exception) { false }) }
    var appTheme by remember { mutableStateOf(try { AnimeBoxSettings.getAppTheme(context) } catch (_: Exception) { "lavender" }) }
    var timelineTheme by remember { mutableStateOf(try { AnimeBoxSettings.getPlayerTimelineTheme(context) } catch (_: Exception) { "lavender" }) }
    var skipIntroTheme by remember { mutableStateOf(try { AnimeBoxSettings.getSkipIntroTheme(context) } catch (_: Exception) { "lavender" }) }
    var skipIntroEnabled by remember { mutableStateOf(try { AnimeBoxSettings.isSkipIntroEnabled(context) } catch (_: Exception) { true }) }
    var brightnessMode by remember { mutableStateOf(try { AnimeBoxSettings.getBrightnessMode(context) } catch (_: Exception) { "gesture" }) }
    var volumeMode by remember { mutableStateOf(try { AnimeBoxSettings.getVolumeMode(context) } catch (_: Exception) { "gesture" }) }
    var defaultEpViewMode by remember { mutableStateOf(try { AnimeBoxSettings.getDefaultEpisodeViewMode(context) } catch (_: Exception) { "image" }) }
    var seekDuration by remember { mutableIntStateOf(try { AnimeBoxSettings.getSeekDuration(context) } catch (_: Exception) { 10 }) }
    var rememberPlaybackPrefs by remember { mutableStateOf(try { AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context) } catch (_: Exception) { true }) }

    // Storage & Download Manager states
    val downloadManager = remember { AnimeDownloadManager.getInstance(context) }
    var storageStats by remember { mutableStateOf(StorageStats(128.0, 45.0, 1.2, 81.8)) }
    val globalPrefs = remember { context.getSharedPreferences("AnimeBoxPrefs", android.content.Context.MODE_PRIVATE) }
    var wifiOnlyDownloads by remember { mutableStateOf(globalPrefs.getBoolean("wifi_only_downloads", false)) }
    var smartDownloads by remember { mutableStateOf(globalPrefs.getBoolean("smart_downloads", true)) }
    var downloadQuality by remember { mutableStateOf(globalPrefs.getString("download_quality", "1080p (Standard)") ?: "1080p (Standard)") }
    var downloadLocation by remember { mutableStateOf(globalPrefs.getString("download_location", "Internal Storage") ?: "Internal Storage") }
    var maturityRating by remember { mutableStateOf(AnimeBoxSettings.getMaturityRating(context)) }
    var autoplayNextEpisode by remember { mutableStateOf(AnimeBoxSettings.isAutoplayNextEpisodeEnabled(context)) }
    var autoplayPreviews by remember { mutableStateOf(AnimeBoxSettings.isAutoplayPreviewsEnabled(context)) }
    var displayLanguage by remember { mutableStateOf(AnimeBoxSettings.getDisplayLanguage(context)) }
    var blurEpisodeSpoilers by remember { mutableStateOf(AnimeBoxSettings.isBlurEpisodeSpoilersEnabled(context)) }

    // Load storage stats asynchronously
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val stats = downloadManager.getStorageStats()
                withContext(Dispatchers.Main) {
                    storageStats = stats
                }
            } catch (_: Exception) {}
        }
    }

    // Dialog Modal states
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showCustomColorPicker by remember { mutableStateOf(false) }
    val customHex = AnimeBoxSettings.getCustomTimelineColor(context)
    var showAppThemePicker by remember { mutableStateOf(false) }
    var showSyncModal by remember { mutableStateOf(false) }
    var targetSyncProvider by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.sync.SyncProvider.ALL) }
    var alertTitle by remember { mutableStateOf("") }
    var alertMessage by remember { mutableStateOf("") }
    var showAlert by remember { mutableStateOf(false) }
    var showDnsModal by remember { mutableStateOf(false) }
    var showTicketsModal by remember { mutableStateOf(false) }
    var showAniListPromptForTickets by remember { mutableStateOf(false) }

    // Export Data SAF Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonStr = AnimeBoxSettings.exportDataToJson(context)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    OutputStreamWriter(os).use { writer ->
                        writer.write(jsonStr)
                    }
                }
                alertTitle = "Export Successful"
                alertMessage = "Your profile details, watchlist, watch history, timestamps, and settings have been exported successfully."
                showAlert = true
            } catch (e: Exception) {
                alertTitle = "Export Failed"
                alertMessage = "Could not export app data: ${e.localizedMessage}"
                showAlert = true
            }
        }
    }

    // Import Data SAF Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val stringBuilder = StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            stringBuilder.append(line)
                            line = reader.readLine()
                        }
                    }
                }
                val jsonContent = stringBuilder.toString()
                val result = AnimeBoxSettings.importDataFromJson(context, jsonContent)
                if (result.isSuccess) {
                    trailerEnabled = AnimeBoxSettings.isTrailerEnabled(context)
                    lowPerfMode = AnimeBoxSettings.isLowPerformanceMode(context)
                    appTheme = AnimeBoxSettings.getAppTheme(context)
                    timelineTheme = AnimeBoxSettings.getPlayerTimelineTheme(context)
                    skipIntroTheme = AnimeBoxSettings.getSkipIntroTheme(context)
                    skipIntroEnabled = AnimeBoxSettings.isSkipIntroEnabled(context)
                    brightnessMode = AnimeBoxSettings.getBrightnessMode(context)
                    volumeMode = AnimeBoxSettings.getVolumeMode(context)
                    defaultEpViewMode = AnimeBoxSettings.getDefaultEpisodeViewMode(context)
                    onSettingsChanged()

                    alertTitle = "Import Successful"
                    alertMessage = "All app data, profiles, watchlist, history timestamps, and settings have been imported successfully!"
                    showAlert = true
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unrecognized backup format."
                    alertTitle = "Import Failed"
                    alertMessage = "Failed to import data: $errorMsg\n\nPlease ensure you selected a valid FireFly backup file."
                    showAlert = true
                }
            } catch (e: Exception) {
                alertTitle = "Import Failed"
                alertMessage = "Error reading backup file: ${e.localizedMessage}"
                showAlert = true
            }
        }
    }

    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // -------------------------------------------------------------
            // Google Pixel Search Header with `<` Chevron & Navbar Search Icon
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF1E1F22))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // `<` Chevron Back Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(38.dp)
                    ) {
                        PixelSettingsIcon(
                            type = SettingsIconType.CHEVRON_LEFT,
                            tint = Color(0xFFE2E2E6),
                            sizeDp = 22
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Navbar Search Icon
                    Icon(
                        imageVector = CustomSearchIcon,
                        contentDescription = "Search",
                        tint = Color(0xFFC4C7C5),
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Text Field / Hint (Centered Vertically with decorationBox)
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        cursorBrush = SolidColor(Color.White),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search settings",
                                        color = Color(0xFF8E918F),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Normal,
                                        style = TextStyle(
                                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFFC4C7C5),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // Main Settings List or Skeleton Loading
            // -------------------------------------------------------------
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .monochromeShimmer()
                    )
                    repeat(6) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .monochromeShimmer()
                            )
                            Spacer(modifier = Modifier.width(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .monochromeShimmer()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .height(11.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .monochromeShimmer()
                                )
                            }
                        }
                    }
                }
            } else {
                val query = searchQuery.trim().lowercase()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ---------------------------------------------------------
                    // SECTION 1: Network & Downloads
                    // ---------------------------------------------------------
                    if (query.isEmpty() || "download".contains(query) || "wifi".contains(query) || "quality".contains(query) || "storage".contains(query)) {
                        PixelSectionHeader(title = "Network & Downloads")

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.WIFI,
                            title = "Wi-Fi Only",
                            subtitle = "Download episodes exclusively over Wi-Fi networks\nto conserve cellular mobile data",
                            checked = wifiOnlyDownloads,
                            onCheckedChange = {
                                wifiOnlyDownloads = it
                                globalPrefs.edit().putBoolean("wifi_only_downloads", it).apply()
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.SMART_DOWNLOADS,
                            title = "Smart Downloads",
                            subtitle = "Automatically deletes completed episodes after watching\nand downloads the next episode in queue",
                            checked = smartDownloads,
                            onCheckedChange = {
                                smartDownloads = it
                                globalPrefs.edit().putBoolean("smart_downloads", it).apply()
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.DOWNLOAD_QUALITY,
                            title = "Video Quality",
                            subtitle = "Choose preferred stream resolution and encoding\nquality for saved offline anime episodes",
                            currentValue = downloadQuality,
                            options = listOf(
                                "1080p (Standard)" to "1080p (Standard)",
                                "720p (High)" to "720p (High)",
                                "480p (Data Saver)" to "480p (Data Saver)"
                            ),
                            onOptionSelected = {
                                downloadQuality = it
                                globalPrefs.edit().putString("download_quality", it).apply()
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.STORAGE_LOCATION,
                            title = "Download Location",
                            subtitle = "Specify the internal storage or SD card directory\nfor saving offline anime videos and covers",
                            currentValue = downloadLocation,
                            options = listOf(
                                "Internal Storage" to "Internal Storage",
                                "SD Card" to "SD Card"
                            ),
                            onOptionSelected = {
                                downloadLocation = it
                                globalPrefs.edit().putString("download_location", it).apply()
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsActionRow(
                            iconType = SettingsIconType.DELETE_DOWNLOADS,
                            title = "Delete All Downloads",
                            subtitle = "Permanently remove all offline video files\nand cached thumbnails to free up device space",
                            onClick = { showDeleteAllConfirm = true }
                        )

                        // Minimalist Monochrome Storage Visualizer
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF141414))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Device Storage",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Internal Storage",
                                    color = Color(0xFF8E918F),
                                    fontSize = 12.5.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val total = if (storageStats.totalGb.isNaN() || storageStats.totalGb <= 0.0) 128.0 else storageStats.totalGb
                            val used = if (storageStats.usedGb.isNaN() || storageStats.usedGb < 0.0) 45.0 else storageStats.usedGb
                            val app = if (storageStats.appGb.isNaN() || storageStats.appGb < 0.0) 1.2 else storageStats.appGb
                            val rawUsedFrac = (used / total).toFloat()
                            val rawAppFrac = (app / total).toFloat()
                            val usedFrac = if (rawUsedFrac.isNaN() || rawUsedFrac <= 0f) 0.35f else rawUsedFrac.coerceIn(0.05f, 0.75f)
                            val appFrac = if (rawAppFrac.isNaN() || rawAppFrac <= 0f) 0.05f else rawAppFrac.coerceIn(0.02f, 0.20f)
                            val rawFreeFrac = 1f - usedFrac - appFrac
                            val freeFrac = if (rawFreeFrac.isNaN() || rawFreeFrac <= 0f) 0.60f else rawFreeFrac.coerceIn(0.05f, 0.90f)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF262626))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(appFrac)
                                        .background(Color.White)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(usedFrac)
                                        .background(Color(0xFF6B6B6B))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(freeFrac)
                                        .background(Color(0xFF262626))
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(Color.White, CircleShape))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    val appMb = app * 1024.0
                                    val appStr = if (appMb >= 1024.0) String.format(Locale.US, "%.1f GB", app) else String.format(Locale.US, "%.0f MB", appMb)
                                    Text("FireFly • $appStr", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF6B6B6B), CircleShape))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Used • ${String.format(Locale.US, "%.0f GB", used)}", color = Color(0xFF8E918F), fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF333333), CircleShape))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    val freeGb = (total - used - app).coerceAtLeast(0.0)
                                    Text("Free • ${String.format(Locale.US, "%.0f GB", freeGb)}", color = Color(0xFF8E918F), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // SECTION 2: Appearance & Display
                    // ---------------------------------------------------------
                    if (query.isEmpty() || "theme".contains(query) || "color".contains(query) || "appearance".contains(query) || "view".contains(query)) {
                        PixelSectionHeader(title = "Appearance & Display")

                        val currentThemeName = when (appTheme) {
                            "light_red", "red" -> "Light Red"
                            "cyan" -> "Cyan Blue"
                            "emerald" -> "Emerald Green"
                            "amber" -> "Golden Amber"
                            else -> "Lavender"
                        }
                        val currentThemeColor = when (appTheme) {
                            "light_red", "red" -> Color(0xFFFF5252)
                            "cyan" -> Color(0xFF00E5FF)
                            "emerald" -> Color(0xFF00E676)
                            "amber" -> Color(0xFFFFAB00)
                            else -> Color(0xFFD0BCFF)
                        }

                        // App Theme Row with Matching Square Selector Box
                        PixelSettingsCustomActionRow(
                            iconType = SettingsIconType.APP_THEME,
                            title = "App Theme",
                            subtitle = "Customize primary navigation accents, selection\nhighlights, and interactive UI component tints",
                            badgeTrailing = {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1F22))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(currentThemeColor)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = currentThemeName,
                                            color = Color(0xFFE2E2E6),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = Color(0xFF8E918F),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            },
                            onClick = { showAppThemePicker = true }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.EPISODE_VIEW,
                            title = "Episode View Mode",
                            subtitle = "Switch between rich poster thumbnail cards\nor compact numbered grid tiles on detail pages",
                            currentValue = if (defaultEpViewMode == "number") "Numbering View" else "Image View (Default)",
                            options = listOf(
                                "Image View (Default)" to "image",
                                "Numbering View" to "number"
                            ),
                            onOptionSelected = { selectedKey ->
                                defaultEpViewMode = selectedKey
                                AnimeBoxSettings.setDefaultEpisodeViewMode(context, selectedKey)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.EPISODE_VIEW,
                            title = "Blur Episode Spoilers",
                            subtitle = "Blurs episode preview thumbnails in details and player to prevent spoiler scenes",
                            checked = blurEpisodeSpoilers,
                            onCheckedChange = { checked ->
                                blurEpisodeSpoilers = checked
                                AnimeBoxSettings.setBlurEpisodeSpoilersEnabled(context, checked)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.LOW_PERF,
                            title = "Maturity Rating",
                            subtitle = "Filter 18+ adult anime across all sections of the app",
                            currentValue = maturityRating,
                            options = listOf(
                                "With restrictions (Default)" to "With restrictions",
                                "No restrictions (Show 18+)" to "No restrictions"
                            ),
                            onOptionSelected = { selectedRating ->
                                maturityRating = selectedRating
                                AnimeBoxSettings.setMaturityRating(context, selectedRating)
                                com.lagradost.cloudstream3.ui.animebox.api.AniListClient.refreshClient()
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.ABOUT_DEVICE,
                            title = "Display Language",
                            subtitle = "Preferred language for menus and UI navigation tags",
                            currentValue = displayLanguage,
                            options = listOf(
                                "English" to "English",
                                "Japanese" to "Japanese",
                                "Hindi" to "Hindi",
                                "Spanish" to "Spanish",
                                "French" to "French"
                            ),
                            onOptionSelected = { selectedLang ->
                                displayLanguage = selectedLang
                                AnimeBoxSettings.setDisplayLanguage(context, selectedLang)
                                onSettingsChanged()
                            }
                        )
                    }

                    // ---------------------------------------------------------
                    // SECTION 3: Performance & Battery
                    // ---------------------------------------------------------
                    if (query.isEmpty() || "trailer".contains(query) || "perf".contains(query) || "battery".contains(query) || "optimize".contains(query)) {
                        PixelSectionHeader(title = "Performance & Battery")

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.TRAILERS_TV,
                            title = "Trailers on Detail Page",
                            subtitle = "Stream official YouTube anime trailers directly\non detail screens when previewing titles",
                            checked = trailerEnabled,
                            onCheckedChange = { checked ->
                                trailerEnabled = checked
                                AnimeBoxSettings.setTrailerEnabled(context, checked)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.EPISODE_VIEW,
                            title = "Autoplay Next Episode",
                            subtitle = "Automatically load next episode when current video ends",
                            checked = autoplayNextEpisode,
                            onCheckedChange = { checked ->
                                autoplayNextEpisode = checked
                                AnimeBoxSettings.setAutoplayNextEpisodeEnabled(context, checked)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.TRAILERS_TV,
                            title = "Autoplay Previews",
                            subtitle = "Automatically play video previews on focus in spotlight",
                            checked = autoplayPreviews,
                            onCheckedChange = { checked ->
                                autoplayPreviews = checked
                                AnimeBoxSettings.setAutoplayPreviewsEnabled(context, checked)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.LOW_PERF,
                            title = "Low Performance Mode",
                            subtitle = "Disables background blur filters and heavy UI\nanimations to maximize device battery and fps",
                            checked = lowPerfMode,
                            onCheckedChange = { checked ->
                                lowPerfMode = checked
                                AnimeBoxSettings.setLowPerformanceMode(context, checked)
                                onSettingsChanged()
                            }
                        )
                    }

                    // ---------------------------------------------------------
                    // SECTION 4: Video Player Controls
                    // ---------------------------------------------------------
                    if (query.isEmpty() || "player".contains(query) || "timeline".contains(query) || "intro".contains(query) || "brightness".contains(query) || "volume".contains(query) || "skip".contains(query)) {
                        PixelSectionHeader(title = "Video Player Controls")

                        val currentTimelineName = when (timelineTheme) {
                            "red" -> "Netflix Red"
                            "cyan" -> "Neon Cyan"
                            "gold" -> "Sunset Gold"
                            "green" -> "Emerald Green"
                            "white" -> "Pure White"
                            "custom" -> "Custom ($customHex)"
                            else -> "Lavender"
                        }
                        val currentTimelineColor = try {
                            when (timelineTheme) {
                                "red" -> Color(0xFFE50914)
                                "cyan" -> Color(0xFF00E5FF)
                                "gold" -> Color(0xFFFFD700)
                                "green" -> Color(0xFF00E676)
                                "white" -> Color.White
                                "custom" -> Color(android.graphics.Color.parseColor(if (customHex.startsWith("#")) customHex else "#$customHex"))
                                else -> Color(0xFFD0BCFF)
                            }
                        } catch (_: Exception) { Color(0xFFD0BCFF) }

                        // Timeline Color Row with Matching Square Selector Box
                        PixelSettingsCustomActionRow(
                            iconType = SettingsIconType.TIMELINE_COLOR,
                            title = "Timeline Color",
                            subtitle = "Customize progress seekbar colors and scrub\nindicator highlights in the video player",
                            badgeTrailing = {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1F22))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(currentTimelineColor)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = currentTimelineName.substringBefore(" ("),
                                            color = Color(0xFFE2E2E6),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = Color(0xFF8E918F),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            },
                            onClick = { showCustomColorPicker = true }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.SKIP_INTRO,
                            title = "Skip Intro & Outro",
                            subtitle = "Shows dedicated one-tap buttons to effortlessly\nskip opening and ending anime theme timestamps",
                            checked = skipIntroEnabled,
                            onCheckedChange = { checked ->
                                skipIntroEnabled = checked
                                AnimeBoxSettings.setSkipIntroEnabled(context, checked)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.BRIGHTNESS,
                            title = "Brightness Control",
                            subtitle = "Swipe vertically on the left side of the player screen\nto rapidly raise or lower display brightness",
                            currentValue = if (brightnessMode == "gesture") "Gesture Slider (Default)" else "Hidden",
                            options = listOf(
                                "Gesture Slider (Default)" to "gesture",
                                "Hidden" to "hidden"
                            ),
                            onOptionSelected = { selectedKey ->
                                brightnessMode = selectedKey
                                AnimeBoxSettings.setBrightnessMode(context, selectedKey)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.VOLUME,
                            title = "Volume Control",
                            subtitle = "Swipe vertically on the right side of the player screen\nto adjust audio volume without hardware keys",
                            currentValue = if (volumeMode == "gesture") "Gesture Slider (Default)" else "Hidden",
                            options = listOf(
                                "Gesture Slider (Default)" to "gesture",
                                "Hidden" to "hidden"
                            ),
                            onOptionSelected = { selectedKey ->
                                volumeMode = selectedKey
                                AnimeBoxSettings.setVolumeMode(context, selectedKey)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.DOUBLE_TAP,
                            title = "Double Tap Skip",
                            subtitle = "Set the exact amount of seconds jumped forward\nor backward when double-tapping player sides",
                            currentValue = "${seekDuration}s",
                            options = listOf(
                                "5 seconds" to "5",
                                "10 seconds (Default)" to "10",
                                "15 seconds" to "15",
                                "30 seconds" to "30",
                                "60 seconds" to "60"
                            ),
                            onOptionSelected = { selectedKey ->
                                val secs = selectedKey.toIntOrNull() ?: 10
                                seekDuration = secs
                                AnimeBoxSettings.setSeekDuration(context, secs)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.REMEMBER_PREFS,
                            title = "Remember Preferences",
                            subtitle = "Automatically remembers your chosen audio language,\nsubtitle tracks, and custom playback speeds",
                            checked = rememberPlaybackPrefs,
                            onCheckedChange = { checked ->
                                rememberPlaybackPrefs = checked
                                AnimeBoxSettings.setRememberPlaybackPrefsEnabled(context, checked)
                                onSettingsChanged()
                            }
                        )
                    }

                    // ---------------------------------------------------------
                    // SECTION 5: Data & Connectivity
                    // ---------------------------------------------------------
                    if (query.isEmpty() || "export".contains(query) || "import".contains(query) || "backup".contains(query) || "dns".contains(query) || "doh".contains(query) || "sync".contains(query) || "anilist".contains(query) || "mal".contains(query)) {
                        PixelSectionHeader(title = "Account & Cloud Sync")

                        val actProfId = com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager.getActiveProfile(context)
                        val anilistUser = com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager.getAniListUser(context, actProfId)

                        PixelSettingsActionRow(
                            iconRes = R.drawable.ic_anilist_official,
                            title = "AniList Sync",
                            subtitle = if (anilistUser != null) "Connected as @${anilistUser.username} • Watchlist synced" else "Connect AniList account for bidirectional watchlist & progress sync",
                            onClick = {
                                targetSyncProvider = com.lagradost.cloudstream3.ui.animebox.sync.SyncProvider.ANILIST
                                showSyncModal = true
                            }
                        )
                        PixelDivider()

                        PixelSettingsActionRow(
                            iconType = SettingsIconType.SUPPORT_TICKET,
                            title = "Support & Feedback Tickets",
                            subtitle = if (anilistUser != null) "Create tickets, submit bugs/suggestions & live chat" else "Log in with AniList to create tickets & chat with support",
                            onClick = {
                                if (anilistUser != null) {
                                    showTicketsModal = true
                                } else {
                                    showAniListPromptForTickets = true
                                }
                            }
                        )
                        PixelDivider()

                        PixelSettingsActionRow(
                            iconType = SettingsIconType.EXPORT,
                            title = "Export App Data",
                            subtitle = "Backup data, history & settings to JSON",
                            onClick = {
                                try {
                                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                    exportLauncher.launch("firefly_backup_$timeStamp.json")
                                } catch (_: Exception) {}
                            }
                        )
                        PixelDivider()

                        PixelSettingsActionRow(
                            iconType = SettingsIconType.IMPORT,
                            title = "Import App Data",
                            subtitle = "Restore data & settings from backup file",
                            onClick = {
                                try {
                                    importLauncher.launch("application/json")
                                } catch (_: Exception) {}
                            }
                        )
                        PixelDivider()

                        PixelSettingsActionRow(
                            iconType = SettingsIconType.DNS_SERVER,
                            title = "DNS over HTTPS (DoH)",
                            subtitle = "Active: ${AnimeBoxSettings.getDnsMode(context).replaceFirstChar { it.uppercase() }}",
                            onClick = { showDnsModal = true }
                        )
                    }

                    // ---------------------------------------------------------
                    // SECTION 6: About / Device Info
                    // ---------------------------------------------------------
                    if (query.isEmpty() || "about".contains(query) || "device".contains(query) || "version".contains(query)) {
                        PixelSectionHeader(title = "About")

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PixelSettingsIcon(type = SettingsIconType.ABOUT_DEVICE)
                            Spacer(modifier = Modifier.width(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "FireFly Anime",
                                    color = Color(0xFFE2E2E6),
                                    fontSize = 17.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Version ${com.lagradost.cloudstream3.BuildConfig.VERSION_NAME} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                                    color = Color(0xFF8E918F),
                                    fontSize = 13.5.sp,
                                    lineHeight = 18.sp
                                )
                                Text(
                                    text = "Device: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                                    color = Color(0xFF8E918F),
                                    fontSize = 13.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        PixelDivider()

                        // Check for App Updates Row (In-App Direct Updater)
                        val activity = context as? Activity
                        val currentVersionName = com.lagradost.cloudstream3.BuildConfig.VERSION_NAME

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF222228))
                                .border(1.dp, Color(0xFF383842), RoundedCornerShape(12.dp))
                                .clickable {
                                    if (activity != null) {
                                        Toast.makeText(context, "Checking for app updates...", Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch {
                                            try {
                                                val hasUpdate = with(com.lagradost.cloudstream3.utils.InAppUpdater) {
                                                    activity.runAutoUpdate(checkAutoUpdate = false)
                                                }
                                                if (!hasUpdate) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "You're on the latest version (v$currentVersionName)", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Update check: ${e.message ?: "Up to date"}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Cannot check for updates in current context", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2E2E36)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.firefly_logo),
                                    contentDescription = "FireFly",
                                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "FireFly Updater",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF35353E))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "v$currentVersionName",
                                            color = Color(0xFFE0E0E0),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Check GitHub releases for latest updates",
                                    color = Color(0xFFA0A0AB),
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "CHECK",
                                    color = Color.Black,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // -------------------------------------------------------------
        // Dialog Modals
        // -------------------------------------------------------------

        if (showSyncModal) {
            val actProfId = com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager.getActiveProfile(context)
            com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxSyncDialog(
                profileId = actProfId,
                initialProvider = targetSyncProvider,
                onDismiss = { showSyncModal = false }
            )
        }

        // App Theme Picker Modal
        if (showAppThemePicker) {
            AppThemePickerDialog(
                currentTheme = appTheme,
                onDismiss = { showAppThemePicker = false },
                onThemeSelected = { selectedKey ->
                    appTheme = selectedKey
                    AnimeBoxSettings.setAppTheme(context, selectedKey)
                    showAppThemePicker = false
                    onSettingsChanged()
                }
            )
        }

        // Custom Timeline Color Picker Modal
        if (showCustomColorPicker) {
            CustomColorPickerDialog(
                initialColorHex = customHex,
                currentTheme = timelineTheme,
                onDismiss = { showCustomColorPicker = false },
                onPresetSelected = { presetKey ->
                    timelineTheme = presetKey
                    AnimeBoxSettings.setPlayerTimelineTheme(context, presetKey)
                    showCustomColorPicker = false
                    onSettingsChanged()
                },
                onCustomHexApplied = { selectedHex ->
                    AnimeBoxSettings.setCustomTimelineColor(context, selectedHex)
                    AnimeBoxSettings.setPlayerTimelineTheme(context, "custom")
                    timelineTheme = "custom"
                    showCustomColorPicker = false
                    onSettingsChanged()
                }
            )
        }

        // Delete All Downloads Confirmation Dialog
        if (showDeleteAllConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteAllConfirm = false },
                title = {
                    Text(
                        text = "Delete All Downloads",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete all downloaded anime episodes? This will permanently remove all offline files from your device storage.",
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    downloadManager.deleteAllDownloads()
                                    storageStats = downloadManager.getStorageStats()
                                } catch (_: Exception) {}
                                showDeleteAllConfirm = false
                                Toast.makeText(context, "All downloads deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Delete All", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllConfirm = false }) {
                        Text("Cancel", color = Color(0xFF8E918F), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF1E1F22),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFCCCCCC),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // DNS Dialog Modal
        if (showDnsModal) {
            AnimeBoxDnsDialog(
                onDismiss = { showDnsModal = false },
                onDnsChanged = { onSettingsChanged() }
            )
        }

        // Alert Dialog for Import/Export outcomes
        if (showAlert) {
            AlertDialog(
                onDismissRequest = { showAlert = false },
                title = {
                    Text(
                        text = alertTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = alertMessage,
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showAlert = false }) {
                        Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF1E1F22),
                shape = RoundedCornerShape(14.dp)
            )
        }

        val actProf = remember { com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager.getActiveProfile(context) }
        val currentAniUser = remember(actProf) { com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager.getAniListUser(context, actProf) }

        if (showTicketsModal && currentAniUser != null) {
            com.lagradost.cloudstream3.ui.animebox.tickets.AnimeBoxTicketListDialog(
                anilistUser = currentAniUser,
                profileId = actProf,
                onDismiss = { showTicketsModal = false }
            )
        }

        if (showAniListPromptForTickets) {
            AlertDialog(
                onDismissRequest = { showAniListPromptForTickets = false },
                title = {
                    Text(text = "AniList Account Required", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = "Support tickets and live chat threads are linked with your AniList identity. Please connect your AniList account first to submit tickets and chat with our team.",
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.5.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAniListPromptForTickets = false
                            targetSyncProvider = com.lagradost.cloudstream3.ui.animebox.sync.SyncProvider.ANILIST
                            showSyncModal = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF02A9FF), contentColor = Color.White)
                    ) {
                        Text("Connect AniList", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAniListPromptForTickets = false }) {
                        Text("Cancel", color = Color(0xFF8E918F))
                    }
                },
                containerColor = Color(0xFF1E1F22),
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Pixel Settings Flat Rows (Exact Google Settings Hierarchy & Typography)
// ----------------------------------------------------------------------------

@Composable
fun PixelSectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF8E918F),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
fun PixelDivider() {
    HorizontalDivider(
        color = Color(0xFF1C1D20),
        thickness = 1.dp,
        modifier = Modifier.padding(start = 44.dp)
    )
}

@Composable
fun PixelSettingsSwitchRow(
    iconType: SettingsIconType,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PixelSettingsIcon(type = iconType)
        Spacer(modifier = Modifier.width(20.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = Color(0xFFA0A2A8),
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        val context = LocalContext.current
        val themeRevision by AnimeBoxThemeHelper.themeRevisionFlow.collectAsState()
        val primaryColor = remember(themeRevision) { AnimeBoxSettings.getAppThemeColor(context) }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = primaryColor,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color(0xFF938F99),
                uncheckedTrackColor = Color(0xFF2B2930),
                uncheckedBorderColor = Color(0xFF79747E)
            )
        )
    }
}

@Composable
fun PixelSettingsSelectorRow(
    iconType: SettingsIconType,
    title: String,
    subtitle: String? = null,
    currentValue: String,
    options: List<Pair<String, String>>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PixelSettingsIcon(type = iconType)
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = Color(0xFFA0A2A8),
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1F22))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentValue.substringBefore(" ("),
                        color = Color(0xFFE2E2E6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = Color(0xFF8E918F),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 44.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { (label, key) ->
                    val isSelected = currentValue.startsWith(label.substringBefore(" (")) || currentValue == label

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF24262A) else Color.Transparent)
                            .clickable {
                                onOptionSelected(key)
                                expanded = false
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFF8E918F),
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
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

@Composable
fun PixelSettingsActionRow(
    iconType: SettingsIconType? = null,
    iconRes: Int? = null,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(5.dp))
            )
        } else if (iconType != null) {
            PixelSettingsIcon(type = iconType)
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = Color(0xFFA0A2A8),
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PixelSettingsCustomActionRow(
    iconType: SettingsIconType,
    title: String,
    subtitle: String,
    badgeTrailing: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PixelSettingsIcon(type = iconType)
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = Color(0xFFA0A2A8),
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        badgeTrailing()
    }
}

// ----------------------------------------------------------------------------
// App Theme Picker Dialog Modal
// ----------------------------------------------------------------------------

@Composable
fun AppThemePickerDialog(
    currentTheme: String,
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit
) {
    val themePresets = listOf(
        Triple("Lavender Purple (Default)", "lavender", Color(0xFFD0BCFF)),
        Triple("Light Red", "light_red", Color(0xFFFF5252)),
        Triple("Cyan Blue", "cyan", Color(0xFF00E5FF)),
        Triple("Emerald Green", "emerald", Color(0xFF00E676)),
        Triple("Golden Amber", "amber", Color(0xFFFFAB00))
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1F22))
                .border(1.dp, Color(0xFF2A2B30), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Choose App Theme",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select the primary accent color for highlights and navigation.",
                    color = Color(0xFF8E918F),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                themePresets.forEach { (name, key, color) ->
                    val isSelected = (currentTheme == key) || (key == "lavender" && (currentTheme.isBlank() || currentTheme == "default"))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF2A2B30) else Color.Transparent)
                            .clickable { onThemeSelected(key) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = name,
                            color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                            fontSize = 14.5.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF8E918F), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Custom Timeline Color Picker Dialog Modal
// ----------------------------------------------------------------------------

@Composable
fun CustomColorPickerDialog(
    initialColorHex: String,
    currentTheme: String,
    onDismiss: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onCustomHexApplied: (String) -> Unit
) {
    var hexText by remember { mutableStateOf(initialColorHex) }
    val presetThemes = listOf(
        Triple("Lavender (Default)", "lavender", Color(0xFFD0BCFF)),
        Triple("Netflix Red", "red", Color(0xFFE50914)),
        Triple("Neon Cyan", "cyan", Color(0xFF00E5FF)),
        Triple("Sunset Gold", "gold", Color(0xFFFFD700)),
        Triple("Emerald Green", "green", Color(0xFF00E676)),
        Triple("Pure White", "white", Color.White)
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1F22))
                .border(1.dp, Color(0xFF2A2B30), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Player Timeline Color",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select a preset theme or enter your own custom HEX color.",
                    color = Color(0xFF8E918F),
                    fontSize = 12.5.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Presets Grid
                presetThemes.chunked(2).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPresets.forEach { (name, key, color) ->
                            val isSelected = currentTheme == key
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF2A2B30) else Color(0xFF141414))
                                    .clickable { onPresetSelected(key) }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = name.substringBefore(" ("),
                                    color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF2A2B30))
                Spacer(modifier = Modifier.height(14.dp))

                // Custom HEX input
                Text(
                    text = "Custom HEX Color",
                    color = Color(0xFFCCCCCC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val previewColor = try {
                        Color(android.graphics.Color.parseColor(if (hexText.startsWith("#")) hexText else "#$hexText"))
                    } catch (_: Exception) { Color.White }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(1.dp, Color(0xFF444444), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { hexText = it },
                        label = { Text("HEX Code", color = Color(0xFF8E918F), fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color(0xFF333333)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF8E918F))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val validHex = if (!hexText.startsWith("#")) "#$hexText" else hexText
                            onCustomHexApplied(validHex)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
