package com.lagradost.cloudstream3.ui.animebox.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.animebox.SolarFourKLinearIcon
import com.lagradost.cloudstream3.ui.animebox.SolarSmartSpeakerBoldIcon

/**
 * Tab enumeration for Player 2 unified slide-up bottom sheet panel.
 */
enum class Player2PanelTab(val title: String, val iconRes: Int) {
    EPISODES("Episodes", R.drawable.ic_p2_library_series),
    QUALITY("Quality", R.drawable.ic_p2_resolution),
    SPEED("Speed", R.drawable.ic_p2_playback_speed),
    AUDIO_SUBS("Audio & Subs", R.drawable.ic_p2_subtitles),
    PUBLIC_SUBS("Public Subtitles", R.drawable.ic_baseline_language_24),
    TIMER("Sleep Timer", R.drawable.ic_p2_timer),
    SETTINGS("Settings", R.drawable.ic_p2_more_options)
}

/**
 * Formats milliseconds to HH:mm:ss or mm:ss display
 */
fun formatPlayerTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * Player 2 Full Controls Overlay.
 * Designed exactly matching Image 1:
 * - Top Bar: Back arrow, Anime title, top action icons (speed, camera, timer, subtitles, audio/mic, cast, more options)
 * - Timeline: Scrubber with Vivid Light Green bar (#00D26A), white circular thumb, time stamps (e.g. 01:24:37 / 02:48:35)
 * - Bottom Bar: Lock, Mute, Rewind 10, Previous, Play/Pause rounded pill button, Next, Forward 10, Episodes, Download, Resize
 */
@Composable
fun AnimeBoxPlayer2ControlsOverlay(
    modifier: Modifier = Modifier,
    animeTitle: String,
    currentEpisodeNum: Int,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    isPlaying: Boolean,
    isLocked: Boolean,
    isMuted: Boolean,
    timelineAccentColor: Color = Color(0xFF00D26A),
    hasPrevEpisode: Boolean = true,
    hasNextEpisode: Boolean = true,
    onBackPressed: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onLockClicked: () -> Unit,
    onToggleMute: () -> Unit,
    onScreenshotClicked: () -> Unit,
    onCastClicked: () -> Unit,
    onServerClicked: () -> Unit = {},
    onDownloadClicked: () -> Unit,
    onResizeClicked: () -> Unit,
    onOpenPanel: (Player2PanelTab) -> Unit
) {
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableStateOf(0L) }

    val displayPositionMs = if (isDraggingSlider) dragPositionMs else currentPositionMs
    val safeDurationMs = if (durationMs > 0L) durationMs else 1L
    val progressFraction = (displayPositionMs.toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPositionMs.toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.85f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.90f)
                    )
                )
            )
    ) {
        // ─── 1. TOP UTILITY BAR (Matching Image 1) ─────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Group: Back Button & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 12.dp)
            ) {
                IconButton(
                    onClick = onBackPressed,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_001),
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = animeTitle.ifEmpty { "Demon Slayer" },
                    color = Color.White,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right Group: Top Utility Action Icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Playback Speed Icon
                IconButton(
                    onClick = { onOpenPanel(Player2PanelTab.SPEED) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_p2_playback_speed),
                        contentDescription = "Playback Speed",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Screenshot Camera Icon
                IconButton(
                    onClick = onScreenshotClicked,
                    modifier = Modifier.size(38.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_p2_camera),
                        contentDescription = "Screenshot",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Sleep Timer Icon
                IconButton(
                    onClick = { onOpenPanel(Player2PanelTab.TIMER) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_p2_timer),
                        contentDescription = "Sleep Timer",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Subtitles Icon
                IconButton(
                    onClick = { onOpenPanel(Player2PanelTab.AUDIO_SUBS) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_p2_subtitles),
                        contentDescription = "Subtitles",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Audio / Voice Track Icon
                IconButton(
                    onClick = { onOpenPanel(Player2PanelTab.AUDIO_SUBS) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_p2_audio),
                        contentDescription = "Audio Track",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Server Switcher Icon (replaces Cast button as requested)
                IconButton(
                    onClick = onServerClicked,
                    modifier = Modifier.size(38.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_p2_server),
                        contentDescription = "Switch Server",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // More Options / Settings (Three Vertical Dots)
                IconButton(
                    onClick = { onOpenPanel(Player2PanelTab.SETTINGS) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_p2_more_options),
                        contentDescription = "More Options",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ─── 2. BOTTOM CONTROLS & TIMELINE (Matching Image 1) ──────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            // Timeline Row with Timestamps and Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Elapsed Time Label (e.g. 01:24:37)
                Text(
                    text = formatPlayerTime(displayPositionMs),
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 12.dp)
                )

                // Interactive Scrubber Bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        // Unplayed Background Track
                        drawRect(
                            color = Color(0xFF4A4A54),
                            size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight)
                        )

                        // Buffered Track
                        if (bufferedFraction > 0f) {
                            drawRect(
                                color = timelineAccentColor.copy(alpha = 0.35f),
                                size = androidx.compose.ui.geometry.Size(canvasWidth * bufferedFraction, canvasHeight)
                            )
                        }

                        // Played Active Track (Default: Vivid Light Green #00D26A)
                        drawRect(
                            color = timelineAccentColor,
                            size = androidx.compose.ui.geometry.Size(canvasWidth * progressFraction, canvasHeight)
                        )
                    }

                    // Scrubber Thumb Circle (Pure white circle matching screenshot)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction.coerceIn(0.01f, 0.99f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(13.dp)
                                    .shadow(4.dp, CircleShape)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }

                    // Touch detector for seeking
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(safeDurationMs) {
                                detectTapGestures { offset ->
                                    val trackWidth = this.size.width
                                    val frac = if (trackWidth > 0) (offset.x / trackWidth.toFloat()).coerceIn(0f, 1f) else 0f
                                    onSeekTo((frac * safeDurationMs).toLong())
                                }
                            }
                    )
                }

                // Total Duration Label (e.g. 02:48:35)
                Text(
                    text = formatPlayerTime(safeDurationMs),
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Actions Row (Below Progress Bar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Group: Lock & Mute
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Lock Screen Icon Button
                    IconButton(
                        onClick = onLockClicked,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_p2_lock_screen),
                            contentDescription = "Lock Screen",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Volume / Mute Icon Button
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Image(
                            painter = painterResource(id = if (isMuted) R.drawable.ic_p2_mute else R.drawable.ic_p2_volume),
                            contentDescription = "Mute / Unmute",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Center Group: Rewind 10, Previous, Play/Pause Capsule, Next, Forward 10
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Rewind 10 Seconds
                    IconButton(
                        onClick = { onSeekRelative(-10000L) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_p2_rewind_10),
                            contentDescription = "Rewind 10s",
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Previous Episode Button
                    IconButton(
                        onClick = onPrevEpisode,
                        enabled = hasPrevEpisode,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_p2_previous),
                            contentDescription = "Previous Episode",
                            modifier = Modifier.size(24.dp),
                            alpha = if (hasPrevEpisode) 1f else 0.35f
                        )
                    }

                    // Distinctive White Pill / Capsule Play/Pause Button (Matching Image 1)
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .width(58.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(Color(0xFFEEF2F6))
                            .clickable(onClick = onTogglePlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = if (isPlaying) R.drawable.ic_p2_pause else R.drawable.ic_p2_play),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Next Episode Button
                    IconButton(
                        onClick = onNextEpisode,
                        enabled = hasNextEpisode,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_p2_next),
                            contentDescription = "Next Episode",
                            modifier = Modifier.size(24.dp),
                            alpha = if (hasNextEpisode) 1f else 0.35f
                        )
                    }

                    // Forward 10 Seconds
                    IconButton(
                        onClick = { onSeekRelative(10000L) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_p2_forward_10),
                            contentDescription = "Forward 10s",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Right Group: Resolution/Quality, Episodes Library, Download, Resize
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Quality / Resolution Button
                    IconButton(
                        onClick = { onOpenPanel(Player2PanelTab.QUALITY) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_p2_resolution),
                            contentDescription = "Video Quality",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Episodes Drawer / Library Button
                    IconButton(
                        onClick = { onOpenPanel(Player2PanelTab.EPISODES) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_p2_library_series),
                            contentDescription = "Episodes Library",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Download Button
                    IconButton(
                        onClick = onDownloadClicked,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_p2_download),
                            contentDescription = "Download Episode",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Resize / Aspect Ratio Button
                    IconButton(
                        onClick = onResizeClicked,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_p2_resize),
                            contentDescription = "Aspect Ratio",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating Unlock Button shown when player is locked
 */
@Composable
fun AnimeBoxPlayer2FloatingUnlock(
    visible: Boolean,
    onUnlock: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + scaleIn(tween(200)),
        exit = fadeOut(tween(200)) + scaleOut(tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1E1E24).copy(alpha = 0.92f))
                    .border(1.dp, Color(0xFF383842), RoundedCornerShape(24.dp))
                    .clickable(onClick = onUnlock)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_p2_unlock_screen),
                    contentDescription = "Unlock",
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Screen Locked · Tap to Unlock",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Modern Slide-Up Bottom Sheet Panel (Matching Image 2).
 * Features:
 * - Top drag handle pill
 * - Clean header title: "Quality for current video · 1080p", "Episodes · Demon Slayer", etc.
 * - Horizontal panel tabs switcher (Episodes, Quality, Speed, Audio/Subs, Timer, Settings)
 * - QUALITY panel matching Image 2 with 1080p Premium (Enhanced bitrate), 1080p, 720p, etc.
 * - Seamless switching between all panels!
 */
@Composable
fun AnimeBoxPlayer2ModernBottomSheetPanel(
    show: Boolean,
    activeTab: Player2PanelTab,
    animeTitle: String,
    currentEpisodeNum: Int,
    totalEpisodes: Int,
    episodeImages: Map<Int, String> = emptyMap(),
    episodeTitles: Map<Int, String> = emptyMap(),
    animePosterUrl: String = "",
    currentQuality: String = "1080p",
    currentSpeed: Float = 1.0f,
    selectedAudio: String = "Japanese",
    selectedSub: String = "English",
    audioTracks: List<String> = listOf("Japanese (Original)", "English Dub", "Hindi Dub"),
    subtitleTracks: List<String> = listOf("English", "Hindi", "Arabic", "Spanish", "Off"),
    timerMinutes: Int = 0,
    timelineAccentColor: Color = Color(0xFF00D26A),
    activePlayerStyle: String = "player2",
    isGraphicsUpscalerEnabled: Boolean = false,
    isAudioEnhancerEnabled: Boolean = false,
    publicSubtitlesList: List<com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitleItem> = emptyList(),
    onTabChange: (Player2PanelTab) -> Unit,
    onClose: () -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    onQualitySelected: (String) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onAudioSelected: (String) -> Unit,
    onSubSelected: (String) -> Unit,
    onTimerSelected: (Int) -> Unit,
    onSwitchPlayerStyle: (String) -> Unit,
    onOpenGraphicsEnhancer: () -> Unit = {},
    onOpenAudioEnhancement: () -> Unit = {},
    onOpenSubtitleAppearance: () -> Unit = {},
    onOpenSubtitleSync: () -> Unit = {},
    onRequestTranslate: () -> Unit = {},
    onRequestGroqAI: () -> Unit = {},
    onOpenOnlineSubSearch: () -> Unit = {},
    onPickCustomSubtitle: () -> Unit = {},
    onPublicSubSelected: (com.lagradost.cloudstream3.ui.animebox.api.PublicSubtitleItem) -> Unit = {},
    onOpenServerSwitcher: () -> Unit = {}
) {
    if (!show) return

    val listState = rememberLazyListState()

    // Smooth scroll to current playing episode when opening episodes tab
    LaunchedEffect(show, activeTab, currentEpisodeNum) {
        if (activeTab == Player2PanelTab.EPISODES && currentEpisodeNum > 0) {
            val targetIdx = (currentEpisodeNum - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetIdx)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161619)),
            border = BorderStroke(1.dp, Color(0xFF2C2C34))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 10.dp)
            ) {
                // Top Drag Handle Pill Bar
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4E4E58))
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Header Bar matching Image 2 Title Format
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val headerTitle = when (activeTab) {
                        Player2PanelTab.QUALITY -> "Quality for current video  ·  $currentQuality"
                        Player2PanelTab.EPISODES -> "Episodes  ·  ${animeTitle.ifEmpty { "Demon Slayer" }}"
                        Player2PanelTab.SPEED -> "Playback Speed  ·  ${currentSpeed}x"
                        Player2PanelTab.AUDIO_SUBS -> "Audio & Subtitles  ·  $selectedAudio"
                        Player2PanelTab.PUBLIC_SUBS -> "Public Subtitles Space"
                        Player2PanelTab.TIMER -> "Sleep Timer  ·  ${if (timerMinutes > 0) "${timerMinutes}m" else "Off"}"
                        Player2PanelTab.SETTINGS -> "Player Options & Enhancers"
                    }

                    Text(
                        text = headerTitle,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFB0B0BC),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Horizontal Tab Selector Pills (Switch panels seamlessly)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(Player2PanelTab.values()) { tab ->
                        val isSelected = activeTab == tab
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) Color(0xFF282832) else Color(0xFF1B1B20))
                                .border(
                                    1.dp,
                                    if (isSelected) timelineAccentColor.copy(alpha = 0.8f) else Color(0xFF34343E),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { onTabChange(tab) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = tab.title,
                                color = if (isSelected) Color.White else Color(0xFF9E9EA8),
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF24242C), thickness = 1.dp)

                // ── Panels Content Area ────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (activeTab) {
                        // ── 1. EPISODES PANEL (Reduced padding & compact spacing) ────────
                        Player2PanelTab.EPISODES -> {
                            val epCount = if (totalEpisodes > 0) totalEpisodes else 26
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(count = epCount) { idx ->
                                    val epNum = idx + 1
                                    val isCurrent = epNum == currentEpisodeNum
                                    val coverImg = episodeImages[epNum] ?: animePosterUrl
                                    val epTitle = episodeTitles[epNum] ?: "Episode $epNum"

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isCurrent) Color(0xFF22222B) else Color(0xFF19191E))
                                            .border(
                                                1.dp,
                                                if (isCurrent) timelineAccentColor.copy(alpha = 0.85f) else Color(0xFF2C2C36),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { onEpisodeSelected(epNum) }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Episode Cover Image Thumbnail
                                        Box(
                                            modifier = Modifier
                                                .width(112.dp)
                                                .height(64.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF101014))
                                        ) {
                                            if (coverImg.isNotBlank()) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(model = coverImg),
                                                    contentDescription = "Cover EP $epNum",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.linearGradient(
                                                                colors = listOf(Color(0xFF2C2D35), Color(0xFF1E1F24))
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("EP $epNum", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                                }
                                            }

                                            // Episode Number Pill Badge
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(4.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.Black.copy(alpha = 0.8f))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "EP $epNum",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            // Playing Indicator Badge
                                            if (isCurrent) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopStart)
                                                        .padding(4.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(timelineAccentColor)
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "PLAYING",
                                                        color = Color.Black,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Episode Metadata Column
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = epTitle,
                                                color = if (isCurrent) timelineAccentColor else Color.White,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = if (isCurrent) "Now Playing" else "Episode $epNum  ·  Japanese & English",
                                                color = if (isCurrent) Color.White.copy(alpha = 0.85f) else Color(0xFF8A8A96),
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }

                                        // Trailing Play Icon
                                        IconButton(
                                            onClick = { onEpisodeSelected(epNum) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Image(
                                                painter = painterResource(id = if (isCurrent) R.drawable.ic_p2_play else R.drawable.ic_p2_next),
                                                contentDescription = "Play",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── 2. QUALITY PANEL (Matching Image 2 Exactly!) ──────────
                        Player2PanelTab.QUALITY -> {
                            val qualityOptions = listOf(
                                "1080p Premium" to "Enhanced bitrate",
                                "1080p" to "",
                                "720p" to "",
                                "480p" to "",
                                "360p" to "",
                                "240p" to "",
                                "Auto" to "Best quality for your connection"
                            )

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(qualityOptions) { (qual, subtitle) ->
                                    val isSelected = currentQuality.equals(qual, ignoreCase = true) ||
                                            (qual == "1080p Premium" && currentQuality == "1080p")

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onQualitySelected(qual) }
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = qual,
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (qual == "1080p Premium") {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(timelineAccentColor.copy(alpha = 0.2f))
                                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "PRO",
                                                            color = timelineAccentColor,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }

                                            if (subtitle.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = subtitle,
                                                    color = Color(0xFF8A8A96),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = timelineAccentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── 3. PLAYBACK SPEED PANEL ──────────────────────────────
                        Player2PanelTab.SPEED -> {
                            val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(speeds) { spd ->
                                    val isSelected = Math.abs(currentSpeed - spd) < 0.05f
                                    val label = if (spd == 1.0f) "Normal (1.0x)" else "${spd}x"

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
                                            color = if (isSelected) Color.White else Color(0xFFC0C0C8),
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = timelineAccentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── 4. AUDIO & SUBTITLES PANEL ───────────────────────────
                        Player2PanelTab.AUDIO_SUBS -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    Text(
                                        text = "Audio Language",
                                        color = Color(0xFFA0A0AA),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        audioTracks.forEach { track ->
                                            val isSelected = selectedAudio.contains(track, ignoreCase = true) || track.contains(selectedAudio, ignoreCase = true)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onAudioSelected(track) }
                                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(track, color = Color.White, fontSize = 15.sp)
                                                if (isSelected) {
                                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = timelineAccentColor, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Text(
                                        text = "Subtitles",
                                        color = Color(0xFFA0A0AA),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        subtitleTracks.forEach { sub ->
                                            val isSelected = selectedSub.contains(sub, ignoreCase = true) || sub.contains(selectedSub, ignoreCase = true)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onSubSelected(sub) }
                                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(sub, color = Color.White, fontSize = 15.sp)
                                                if (isSelected) {
                                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = timelineAccentColor, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                // Subtitle Tools & Public Subtitles Space Section
                                item {
                                    Text(
                                        text = "Subtitle Tools & Styling",
                                        color = Color(0xFFA0A0AA),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Subtitle Appearance Tool
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF1E1F26))
                                                .border(1.dp, Color(0xFF2E2F3A), RoundedCornerShape(10.dp))
                                                .clickable { onOpenSubtitleAppearance() }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_p2_appearencesubtitle),
                                                contentDescription = "Subtitle Appearance",
                                                modifier = Modifier.size(26.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Subtitle Appearance", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                                                Text("Font, colors, stroke border & shadow style", color = Color(0xFF8A8A96), fontSize = 11.5.sp)
                                            }
                                            Text("OPEN", color = timelineAccentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Subtitle Synchronization Tool
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF1E1F26))
                                                .border(1.dp, Color(0xFF2E2F3A), RoundedCornerShape(10.dp))
                                                .clickable { onOpenSubtitleSync() }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_p2_playback_speed),
                                                contentDescription = "Subtitle Sync",
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Subtitle Synchronization", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                                                Text("Adjust offset delay (+/- ms) or auto-sync", color = Color(0xFF8A8A96), fontSize = 11.5.sp)
                                            }
                                            Text("SYNC", color = timelineAccentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Public Subtitles Space Link
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF1E1F26))
                                                .border(1.dp, Color(0xFF2E2F3A), RoundedCornerShape(10.dp))
                                                .clickable { onTabChange(Player2PanelTab.PUBLIC_SUBS) }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_baseline_language_24),
                                                contentDescription = "Public Subtitles Space",
                                                tint = timelineAccentColor,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Public Subtitles Space", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                                                Text("Groq AI Whisper, ML Kit translate & cloud subs", color = Color(0xFF8A8A96), fontSize = 11.5.sp)
                                            }
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_p2_next),
                                                contentDescription = "Go",
                                                tint = Color(0xFF8A8A96),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── 5. PUBLIC SUBTITLES SPACE PANEL ───────────────────────
                        Player2PanelTab.PUBLIC_SUBS -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Text(
                                        text = "AI Speech-to-Text & Neural Translations",
                                        color = Color(0xFFA0A0AA),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "High-precision AI Whisper transcription and on-device neural translation.",
                                        color = Color(0xFF787884),
                                        fontSize = 11.5.sp
                                    )
                                }

                                // Groq Cloud Whisper AI - Click-based!
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1B1C22))
                                            .border(1.dp, Color(0xFF2C2D38), RoundedCornerShape(12.dp))
                                            .clickable { onRequestGroqAI() }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF262732)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_p2_audio),
                                                contentDescription = "Groq AI",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Groq Cloud Whisper AI", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF3B82F6).copy(alpha = 0.2f))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text("AI", color = Color(0xFF60A5FA), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                "Click to transcribe audio track using Groq Whisper Cloud API",
                                                color = Color(0xFF8E8E9B),
                                                fontSize = 12.sp
                                            )
                                        }

                                        Text("START", color = timelineAccentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Google ML Kit On-Device Translation - Click-based language picker!
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1B1C22))
                                            .border(1.dp, Color(0xFF2C2D38), RoundedCornerShape(12.dp))
                                            .clickable { onRequestTranslate() }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF262732)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_baseline_language_24),
                                                contentDescription = "ML Kit Translation",
                                                tint = timelineAccentColor,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Google ML Kit Translation", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(timelineAccentColor.copy(alpha = 0.2f))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text("LOCAL", color = timelineAccentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                "Click to select language and translate subtitles on-device",
                                                color = Color(0xFF8E8E9B),
                                                fontSize = 12.sp
                                            )
                                        }

                                        Text("SELECT", color = timelineAccentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Search Subtitles Online
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1B1C22))
                                            .border(1.dp, Color(0xFF2C2D38), RoundedCornerShape(12.dp))
                                            .clickable { onOpenOnlineSubSearch() }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF262732)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_p2_subtitles),
                                                contentDescription = "Search Online",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Search Subtitles Online", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text("Fetch subtitles from OpenSubtitles, Subscene & SubDL", color = Color(0xFF8E8E9B), fontSize = 12.sp)
                                        }

                                        Text("SEARCH", color = Color(0xFF90CAF9), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Load Custom Subtitle File
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1B1C22))
                                            .border(1.dp, Color(0xFF2C2D38), RoundedCornerShape(12.dp))
                                            .clickable { onPickCustomSubtitle() }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF262732)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_p2_download),
                                                contentDescription = "Import Subtitles",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Import Custom Subtitle File", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text("Load local .srt, .vtt, or .ass files from device storage", color = Color(0xFF8E8E9B), fontSize = 12.sp)
                                        }

                                        Text("IMPORT", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Community Public Subtitles List (if any available)
                                if (publicSubtitlesList.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Community Public Subtitles (${publicSubtitlesList.size})",
                                            color = Color(0xFFA0A0AA),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    items(publicSubtitlesList) { pubItem ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF16171D))
                                                .border(1.dp, Color(0xFF24252E), RoundedCornerShape(8.dp))
                                                .clickable { onPublicSubSelected(pubItem) }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(pubItem.langName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                Text("Uploaded by: ${pubItem.uploaderName}", color = Color(0xFF80808C), fontSize = 11.sp)
                                            }
                                            Text("LOAD", color = timelineAccentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // ── 6. SLEEP TIMER PANEL ─────────────────────────────────
                        Player2PanelTab.TIMER -> {
                            val timerOptions = listOf(0 to "Off", 15 to "15 Minutes", 30 to "30 Minutes", 45 to "45 Minutes", 60 to "60 Minutes", -1 to "End of Episode")
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(timerOptions) { (mins, label) ->
                                    val isSelected = timerMinutes == mins
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onTimerSelected(mins) }
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = label, color = Color.White, fontSize = 15.5.sp)
                                        if (isSelected) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = timelineAccentColor, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // ── 7. SETTINGS & ENHANCERS PANEL (Full Scrollable on Mobile) ──
                        Player2PanelTab.SETTINGS -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Video & Audio Enhancers",
                                    color = Color(0xFFA0A0AA),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                // Graphics Enhancer Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1B1C22))
                                        .border(1.dp, Color(0xFF2C2D38), RoundedCornerShape(12.dp))
                                        .clickable { onOpenGraphicsEnhancer() }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF262732)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = SolarFourKLinearIcon,
                                            contentDescription = "Graphics Enhancer",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Graphics Enhancer", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("Ultra clarity upscaling, sharpness & line contrast", color = Color(0xFF8E8E9B), fontSize = 12.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isGraphicsUpscalerEnabled) timelineAccentColor.copy(alpha = 0.2f) else Color(0xFF2A2B35))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (isGraphicsUpscalerEnabled) "ACTIVE" else "OFF",
                                            color = if (isGraphicsUpscalerEnabled) timelineAccentColor else Color(0xFF8E8E9B),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Audio Enhancement Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1B1C22))
                                        .border(1.dp, Color(0xFF2C2D38), RoundedCornerShape(12.dp))
                                        .clickable { onOpenAudioEnhancement() }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF262732)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = SolarSmartSpeakerBoldIcon,
                                            contentDescription = "Audio Enhancement",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Audio Enhancement", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("3D virtualizer, bass boost & vocal clarity equalizer", color = Color(0xFF8E8E9B), fontSize = 12.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isAudioEnhancerEnabled) timelineAccentColor.copy(alpha = 0.2f) else Color(0xFF2A2B35))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (isAudioEnhancerEnabled) "ACTIVE" else "OFF",
                                            color = if (isAudioEnhancerEnabled) timelineAccentColor else Color(0xFF8E8E9B),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Subtitle Appearance Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1B1C22))
                                        .border(1.dp, Color(0xFF2C2D38), RoundedCornerShape(12.dp))
                                        .clickable { onOpenSubtitleAppearance() }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF262732)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_p2_appearencesubtitle),
                                            contentDescription = "Subtitle Appearance",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Subtitle Appearance", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("Font family, color, background opacity & scale", color = Color(0xFF8E8E9B), fontSize = 12.sp)
                                    }
                                    Text("OPEN", color = timelineAccentColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }

                                // Direct Public Subtitles Space Link with Language Icon
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1B1C22))
                                        .border(1.dp, Color(0xFF2C2D38), RoundedCornerShape(12.dp))
                                        .clickable { onTabChange(Player2PanelTab.PUBLIC_SUBS) }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF262732)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_baseline_language_24),
                                            contentDescription = "Public Subtitles Space",
                                            tint = timelineAccentColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Public Subtitles Space", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("AI Whisper, ML Kit translations & cloud subtitles", color = Color(0xFF8E8E9B), fontSize = 12.sp)
                                    }
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_p2_next),
                                        contentDescription = "Go",
                                        tint = Color(0xFF8E8E9B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Server Switcher Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1B1C22))
                                        .border(1.dp, Color(0xFF2C2D38), RoundedCornerShape(12.dp))
                                        .clickable { onOpenServerSwitcher() }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF262732)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_p2_server),
                                            contentDescription = "Streaming Server",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Streaming Server", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("Switch streaming source or mirror server", color = Color(0xFF8E8E9B), fontSize = 12.sp)
                                    }
                                    Text("SWITCH", color = timelineAccentColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

