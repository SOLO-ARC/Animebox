package com.lagradost.cloudstream3.ui.animebox.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class WatchStatusDefinition(
    val categoryName: String,
    val icon: ImageVector,
    val color: Color = Color.White
)

private fun createSolarIcon(name: String, pathData: String): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.White)
    ).build()
}

// Solar eye-circle-bold (Eye enclosed inside a circular ring, clean independent multi-path)
val StatusWatchingIcon: ImageVector
    get() = ImageVector.Builder(
        name = "StatusWatching",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(
            "M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2zm0 18c-4.411 0-8-3.589-8-8s3.589-8 8-8 8 3.589 8 8-3.589 8-8 8z"
        ).toNodes(),
        fill = SolidColor(Color.White)
    ).addPath(
        pathData = PathParser().parsePathString(
            "M12 8c-3 0-5.5 2-6.5 4 1 2 3.5 4 6.5 4s5.5-2 6.5-4c-1-2-3.5-4-6.5-4zm0 6.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5zm0-4c-.83 0-1.5.67-1.5 1.5s.67 1.5 1.5 1.5 1.5-.67 1.5-1.5-.67-1.5-1.5-1.5z"
        ).toNodes(),
        fill = SolidColor(Color.White)
    ).build()

// Solar check-circle-bold
val StatusCompletedIcon: ImageVector
    get() = createSolarIcon(
        "StatusCompleted",
        "M12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2ZM16.0303 9.53033L10.5303 15.0303C10.2374 15.3232 9.76256 15.3232 9.46967 15.0303L7.46967 13.0303C7.17678 12.7374 7.17678 12.2626 7.46967 11.9697C7.76256 11.6768 8.23744 11.6768 8.53033 11.9697L10 13.4393L14.9697 8.46967C15.2626 8.17678 15.7374 8.17678 16.0303 8.46967C16.3232 8.76256 16.3232 9.23744 16.0303 9.53033Z"
    )

// Solar clock/calendar inside circle (Visible inner clock hands and ring, never empty)
val StatusPlanToWatchIcon: ImageVector
    get() = ImageVector.Builder(
        name = "StatusPlanToWatch",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(
            "M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2zm0 18c-4.411 0-8-3.589-8-8s3.589-8 8-8 8 3.589 8 8-3.589 8-8 8z"
        ).toNodes(),
        fill = SolidColor(Color.White)
    ).addPath(
        pathData = PathParser().parsePathString(
            "M12 6.5c-.55 0-1 .45-1 1V12c0 .28.11.53.29.71l3 3c.39.39 1.02.39 1.41 0 .39-.39.39-1.02 0-1.41L13 11.58V7.5c0-.55-.45-1-1-1z"
        ).toNodes(),
        fill = SolidColor(Color.White)
    ).build()

// Solar calendar icon matching Image 9
val StatusCalendarPlanToWatchIcon: ImageVector
    get() = createSolarIcon(
        "StatusCalendarPlanToWatch",
        "M19 4h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zm0-12H5V6h14v2zm-7 5h5v5h-5v-5z"
    )

// Atlus Mascot Slime Icon (Celestial blue round slime with bright white vertical oval eyes)
val AtlusSlimeIcon: ImageVector
    get() = ImageVector.Builder(
        name = "AtlusSlime",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"
        ).toNodes(),
        fill = SolidColor(Color(0xFF3897F0))
    ).addPath(
        pathData = PathParser().parsePathString(
            "M9.2 9.5c-.66 0-1.2.9-1.2 2s.54 2 1.2 2 1.2-.9 1.2-2-.54-2-1.2-2zm5.6 0c-.66 0-1.2.9-1.2 2s.54 2 1.2 2 1.2-.9 1.2-2-.54-2-1.2-2z"
        ).toNodes(),
        fill = SolidColor(Color.White)
    ).build()

// Solar pause-circle-bold
val StatusOnHoldIcon: ImageVector
    get() = createSolarIcon(
        "StatusOnHold",
        "M12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2ZM9 8C9.55228 8 10 8.44772 10 9V15C10 15.5523 9.55228 16 9 16C8.44772 16 8 15.5523 8 15V9C8 8.44772 8.44772 8 9 8ZM15 8C15.5523 8 16 8.44772 16 9V15C16 15.5523 15.5523 16 15 16C14.4477 16 14 15.5523 14 15V9C14 8.44772 14.4477 8 15 8Z"
    )

// Solar close-circle-bold
val StatusDroppedIcon: ImageVector
    get() = createSolarIcon(
        "StatusDropped",
        "M12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2ZM9.53033 8.46967L12 10.9393L14.4697 8.46967C14.7626 8.17678 15.2374 8.17678 15.5303 8.46967C15.8232 8.76256 15.8232 9.23744 15.5303 9.53033L13.0607 12L15.5303 14.4697C15.8232 14.7626 15.8232 15.2374 15.5303 15.5303C15.2374 15.8232 14.7626 15.8232 14.4697 15.5303L12 13.0607L9.53033 15.5303C9.23744 15.8232 8.76256 15.8232 8.46967 15.5303C8.17678 15.2374 8.17678 14.7626 8.46967 14.4697L10.9393 12L8.46967 9.53033C8.17678 9.23744 8.17678 8.76256 8.46967 8.46967C8.76256 8.17678 9.23744 8.17678 9.53033 8.46967Z"
    )

// Solar restart-circle-bold / refresh-circle-bold
val StatusRewatchingIcon: ImageVector
    get() = createSolarIcon(
        "StatusRewatching",
        "M12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2ZM15.8115 15.8115C14.8352 16.7878 13.4878 17.3871 12 17.3871C9.02484 17.3871 6.6129 14.9752 6.6129 12C6.6129 9.02484 9.02484 6.6129 12 6.6129C13.5683 6.6129 14.9814 7.28292 15.9624 8.35161V7.25C15.9624 6.83579 16.2982 6.5 16.7124 6.5C17.1266 6.5 17.4624 6.83579 17.4624 7.25V10.25C17.4624 10.6642 17.1266 11 16.7124 11H13.7124C13.2982 11 12.9624 10.6642 12.9624 10.25C12.9624 9.83579 13.2982 9.5 13.7124 9.5H15.2285C14.4443 8.64731 13.2952 8.1129 12 8.1129C9.8532 8.1129 8.1129 9.8532 8.1129 12C8.1129 14.1468 9.8532 15.8871 12 15.8871C13.0734 15.8871 14.0474 15.4529 14.7509 14.7509C15.0438 14.458 15.5186 14.458 15.8115 14.7509C16.1044 15.0438 16.1044 15.5186 15.8115 15.8115Z"
    )

// Solar heart-bold
val StatusFavoritesIcon: ImageVector
    get() = createSolarIcon(
        "StatusFavorites",
        "M12 21.35L10.55 20.03C5.4 15.36 2 12.28 2 8.5C2 5.42 4.42 3 7.5 3C9.24 3 10.91 3.81 12 5.09C13.09 3.81 14.76 3 16.5 3C19.58 3 22 5.42 22 8.5C22 12.28 18.6 15.36 13.45 20.04L12 21.35Z"
    )

val ALL_WATCH_STATUSES = listOf(
    WatchStatusDefinition("Watching", StatusWatchingIcon, Color(0xFF4CAF50)),
    WatchStatusDefinition("Completed", StatusCompletedIcon, Color(0xFF00E676)),
    WatchStatusDefinition("Plan to Watch", StatusCalendarPlanToWatchIcon, Color(0xFF2196F3)),
    WatchStatusDefinition("On Hold", StatusOnHoldIcon, Color(0xFFFFB300)),
    WatchStatusDefinition("Dropped", StatusDroppedIcon, Color(0xFFFF5252)),
    WatchStatusDefinition("Rewatching", StatusRewatchingIcon, Color(0xFFAB47BC)),
    WatchStatusDefinition("Favorites", StatusFavoritesIcon, Color(0xFFFF4081))
)

@Composable
fun WatchStatusDialog(
    currentStatus: String?,
    isInLibrary: Boolean,
    onSelectStatus: (String) -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onDismiss: () -> Unit
) {
    val normalizedCurrent = currentStatus?.trim() ?: ""

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onDismiss() },
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
                // Header (Matching Screenshot 2)
                Text(
                    text = "Select Watch Status",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Track this anime in your personal library",
                    color = Color(0xFF8E8E9B),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Status options list
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ALL_WATCH_STATUSES.forEach { statusDef ->
                        val isSelected = isInLibrary && normalizedCurrent.equals(statusDef.categoryName, ignoreCase = true)
                        val statusColor = statusDef.color
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF25262D))
                                .clickable {
                                    onSelectStatus(statusDef.categoryName)
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Status Icon with colorful circular background
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF303138)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = statusDef.icon,
                                        contentDescription = statusDef.categoryName,
                                        tint = statusColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Status Title Text
                                Text(
                                    text = statusDef.categoryName,
                                    color = if (isSelected) Color.White else Color(0xFFE4E4EB),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                // Right Checkmark
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFFB8BAC4),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // If in library, option to remove
                if (isInLibrary) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = onRemoveFromLibrary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "Remove from My List",
                            color = Color(0xFFC4C4C4),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SelectWatchStatusDialog(
    currentCategory: String?,
    isInLibrary: Boolean,
    onSelectStatus: (String) -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    WatchStatusDialog(
        currentStatus = currentCategory,
        isInLibrary = isInLibrary,
        onSelectStatus = onSelectStatus,
        onRemoveFromLibrary = onRemoveFromLibrary,
        onDismiss = onDismiss
    )
}
