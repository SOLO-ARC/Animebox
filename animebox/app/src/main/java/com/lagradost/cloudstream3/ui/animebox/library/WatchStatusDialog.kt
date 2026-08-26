package com.lagradost.cloudstream3.ui.animebox.library

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class WatchStatusDefinition(
    val categoryName: String,
    val icon: ImageVector
)

// SVG Path Icons matching Screenshot 2
val StatusWatchingIcon: ImageVector
    get() = ImageVector.Builder(
        name = "StatusWatching",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White)
    ) {
        moveTo(9f, 16.17f)
        lineTo(4.83f, 12f)
        lineTo(3.41f, 13.41f)
        lineTo(9f, 19f)
        lineTo(21f, 7f)
        lineTo(19.59f, 5.59f)
        close()
    }.build()

val StatusCompletedIcon: ImageVector
    get() = ImageVector.Builder(
        name = "StatusCompleted",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White)
    ) {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
        close()
        moveTo(10f, 17f)
        lineTo(5f, 12f)
        lineTo(6.41f, 10.59f)
        lineTo(10f, 14.17f)
        lineTo(17.59f, 6.58f)
        lineTo(19f, 8f)
        lineTo(10f, 17f)
        close()
    }.build()

val StatusPlanToWatchIcon: ImageVector
    get() = ImageVector.Builder(
        name = "StatusPlanToWatch",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White)
    ) {
        moveTo(11.99f, 2f)
        curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
        curveTo(2f, 17.52f, 6.47f, 22f, 11.99f, 22f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        curveTo(22f, 6.48f, 17.52f, 2f, 11.99f, 2f)
        close()
        moveTo(12f, 20f)
        curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
        curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
        curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
        curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
        close()
        moveTo(12.5f, 7f)
        horizontalLineTo(11f)
        verticalLineTo(13f)
        lineTo(16.25f, 16.15f)
        lineTo(17f, 14.92f)
        lineTo(12.5f, 12.25f)
        close()
    }.build()

val StatusOnHoldIcon: ImageVector
    get() = ImageVector.Builder(
        name = "StatusOnHold",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White)
    ) {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
        close()
        moveTo(12f, 20f)
        curveTo(7.59f, 20f, 4f, 16.41f, 4f, 12f)
        curveTo(4f, 7.59f, 7.59f, 4f, 12f, 4f)
        curveTo(16.41f, 4f, 20f, 7.59f, 20f, 12f)
        curveTo(20f, 16.41f, 16.41f, 20f, 12f, 20f)
        close()
        moveTo(9f, 16f)
        horizontalLineTo(11f)
        verticalLineTo(8f)
        horizontalLineTo(9f)
        verticalLineTo(16f)
        close()
        moveTo(13f, 16f)
        horizontalLineTo(15f)
        verticalLineTo(8f)
        horizontalLineTo(13f)
        verticalLineTo(16f)
        close()
    }.build()

val StatusDroppedIcon: ImageVector
    get() = ImageVector.Builder(
        name = "StatusDropped",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White)
    ) {
        moveTo(12f, 2f)
        curveTo(6.47f, 2f, 2f, 6.47f, 2f, 12f)
        curveTo(2f, 17.53f, 6.47f, 22f, 12f, 22f)
        curveTo(17.53f, 22f, 22f, 17.53f, 22f, 12f)
        curveTo(22f, 6.47f, 17.53f, 2f, 12f, 2f)
        close()
        moveTo(12f, 20f)
        curveTo(7.59f, 20f, 4f, 16.41f, 4f, 12f)
        curveTo(4f, 7.59f, 7.59f, 4f, 12f, 4f)
        curveTo(16.41f, 4f, 20f, 7.59f, 20f, 12f)
        curveTo(20f, 16.41f, 16.41f, 20f, 12f, 20f)
        close()
        moveTo(15.59f, 7f)
        lineTo(12f, 10.59f)
        lineTo(8.41f, 7f)
        lineTo(7f, 8.41f)
        lineTo(10.59f, 12f)
        lineTo(7f, 15.59f)
        lineTo(8.41f, 17f)
        lineTo(12f, 13.41f)
        lineTo(15.59f, 17f)
        lineTo(17f, 15.59f)
        lineTo(13.41f, 12f)
        lineTo(17f, 8.41f)
        close()
    }.build()

val StatusRewatchingIcon: ImageVector
    get() = ImageVector.Builder(
        name = "StatusRewatching",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White)
    ) {
        moveTo(12f, 4f)
        verticalLineTo(1f)
        lineTo(8f, 5f)
        lineTo(12f, 9f)
        verticalLineTo(6f)
        curveTo(15.31f, 6f, 18f, 8.69f, 18f, 12f)
        curveTo(18f, 13.01f, 17.75f, 13.97f, 17.3f, 14.8f)
        lineTo(18.76f, 16.26f)
        curveTo(19.54f, 15.03f, 20f, 13.57f, 20f, 12f)
        curveTo(20f, 7.58f, 16.42f, 4f, 12f, 4f)
        close()
        moveTo(12f, 18f)
        curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
        curveTo(6f, 10.99f, 6.25f, 10.03f, 6.7f, 9.2f)
        lineTo(5.24f, 7.74f)
        curveTo(4.46f, 8.97f, 4f, 10.43f, 4f, 12f)
        curveTo(4f, 16.42f, 7.58f, 20f, 12f, 20f)
        verticalLineTo(23f)
        lineTo(16f, 19f)
        lineTo(12f, 15f)
        verticalLineTo(18f)
        close()
    }.build()

val StatusFavoritesIcon: ImageVector
    get() = ImageVector.Builder(
        name = "StatusFavorites",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White)
    ) {
        moveTo(12f, 21.35f)
        lineTo(10.55f, 20.03f)
        curveTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
        curveTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
        curveTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.09f)
        curveTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
        curveTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
        curveTo(22f, 12.28f, 18.6f, 15.36f, 13.45f, 20.04f)
        lineTo(12f, 21.35f)
        close()
    }.build()

val ALL_WATCH_STATUSES = listOf(
    WatchStatusDefinition("Currently Watching", StatusWatchingIcon),
    WatchStatusDefinition("Completed", StatusCompletedIcon),
    WatchStatusDefinition("Plan to Watch", StatusPlanToWatchIcon),
    WatchStatusDefinition("On Hold", StatusOnHoldIcon),
    WatchStatusDefinition("Dropped", StatusDroppedIcon),
    WatchStatusDefinition("Re-watching", StatusRewatchingIcon),
    WatchStatusDefinition("Favorites", StatusFavoritesIcon)
)

/**
 * Modern Watch Status Dialog matching User Reference Screenshot 2.
 * High-end dark theme modal with rounded status items, checkmark state, and smooth typography.
 */
@Composable
fun SelectWatchStatusDialog(
    isInLibrary: Boolean,
    currentCategory: String,
    onSelectStatus: (String) -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onDismiss: () -> Unit
) {
    val normalizedCurrent = LibraryManager.normalizeCategory(currentCategory)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
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
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color.White else Color(0xFF1D1E26))
                                .clickable {
                                    onSelectStatus(statusDef.categoryName)
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Status Icon
                                Icon(
                                    imageVector = statusDef.icon,
                                    contentDescription = statusDef.categoryName,
                                    tint = if (isSelected) Color.Black else Color(0xFF9E9EB2),
                                    modifier = Modifier.size(20.dp)
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                // Status Title Text
                                Text(
                                    text = statusDef.categoryName,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                // Right Checkmark (only visible on selected item, as in screenshot 2)
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.Black,
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
