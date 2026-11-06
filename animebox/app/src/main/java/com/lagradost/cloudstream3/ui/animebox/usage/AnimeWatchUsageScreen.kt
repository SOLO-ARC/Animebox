package com.lagradost.cloudstream3.ui.animebox.usage

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.rememberAsyncImagePainter
import com.lagradost.cloudstream3.R
import java.util.Calendar

private val BreakdownRingColors = listOf(
    Color(0xFF4285F4), // Vibrant Wellbeing Blue
    Color(0xFFEA4335), // Coral Red
    Color(0xFFFBBC04), // Amber Gold
    Color(0xFF34A853), // Fresh Emerald Green
    Color(0xFFA142F4), // Purple
    Color(0xFF24C1E0), // Teal Cyan
    Color(0xFFFF7043), // Sunset Orange
    Color(0xFFEC407A)  // Vivid Pink
)

fun getShortAnimeTitle(fullName: String): String {
    if (fullName.isBlank()) return "Other"
    val primary = fullName
        .split(":", " - ", " – ", " Season", " Part", " 2nd", " 3rd", " The Final", " Cour")
        .firstOrNull()?.trim() ?: fullName
    return if (primary.length > 11) {
        val words = primary.split(" ")
        if (words.isNotEmpty() && words[0].length in 3..11) words[0]
        else primary.take(10) + "…"
    } else {
        primary
    }
}

enum class UsageViewMode {
    SCREEN_TIME_BAR,
    DONUT_BREAKDOWN
}

@Composable
fun AnimeWatchUsageScreen(
    activeProfileId: String? = null,
    onClose: () -> Unit,
    onOpenAnimeDetails: (Int) -> Unit
) {
    val context = LocalContext.current
    var viewMode by remember { mutableStateOf(UsageViewMode.SCREEN_TIME_BAR) }
    var showDropdownMenu by remember { mutableStateOf(false) }

    // Navigation reference date (default: now)
    var referenceTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var weeklyData by remember(referenceTimeMs) {
        mutableStateOf(AnimeWatchUsageManager.getWeeklyBreakdown(context, activeProfileId, referenceTimeMs))
    }

    // Selected day index (0 = Sunday .. 6 = Saturday)
    var selectedDayIndex by remember(weeklyData) {
        val todayCal = Calendar.getInstance()
        val todayKey = AnimeWatchUsageManager.getDateKey(todayCal.timeInMillis)
        val matchingIdx = weeklyData.indexOfFirst { it.dateKey == todayKey }
        mutableIntStateOf(if (matchingIdx >= 0) matchingIdx else 6)
    }

    val selectedDay = weeklyData.getOrNull(selectedDayIndex) ?: weeklyData.lastOrNull()
    val isTodaySelected = selectedDay?.dateKey == AnimeWatchUsageManager.getDateKey()
    val dayRecords = selectedDay?.items ?: emptyList()
    val totalTimeMs = selectedDay?.totalWatchTimeMs ?: 0L

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── TOP BAR ──────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "App activity details",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // ─── VIEW SELECTOR PILL (Screen Time ▼ / Donut Breakdown ▼) ──────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1E283A))
                                .clickable { showDropdownMenu = !showDropdownMenu }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (viewMode == UsageViewMode.SCREEN_TIME_BAR) "Screen time" else "Breakdown ring",
                                color = Color(0xFF90B8F8),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select View",
                                tint = Color(0xFF90B8F8),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Dropdown modal
                        if (showDropdownMenu) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1C1E26))
                                    .padding(vertical = 4.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(0.5f)
                                            .clickable {
                                                viewMode = UsageViewMode.SCREEN_TIME_BAR
                                                showDropdownMenu = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Screen time (Bar chart)",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (viewMode == UsageViewMode.SCREEN_TIME_BAR) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(0.5f)
                                            .clickable {
                                                viewMode = UsageViewMode.DONUT_BREAKDOWN
                                                showDropdownMenu = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Breakdown ring",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (viewMode == UsageViewMode.DONUT_BREAKDOWN) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
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

                // ─── HERO DURATION STAT (Shown only in Bar Chart mode, Donut has it in center) ───
                if (viewMode == UsageViewMode.SCREEN_TIME_BAR) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = AnimeWatchUsageManager.formatDuration(totalTimeMs),
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isTodaySelected) "Today" else selectedDay?.displayDate ?: "",
                                color = Color(0xFF8E8E98),
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // ─── CHART SECTION (BAR CHART or DONUT RING) ──────────────────────
                item {
                    if (viewMode == UsageViewMode.SCREEN_TIME_BAR) {
                        WeeklyBarChart(
                            weeklyData = weeklyData,
                            selectedDayIndex = selectedDayIndex,
                            onSelectDay = { selectedDayIndex = it }
                        )
                    } else {
                        DonutBreakdownChart(
                            records = dayRecords,
                            totalTimeMs = totalTimeMs,
                            isToday = isTodaySelected,
                            displayDateLabel = selectedDay?.dayName ?: ""
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ─── DATE NAVIGATOR ROW (< Sat, 19 Sept >) ────────────────────────
                item {
                    val isPastLimit = run {
                        val oldestAllowed = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, -6)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val currentCal = selectedDay?.calendar
                        currentCal != null && !currentCal.after(oldestAllowed)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = {
                                if (!isPastLimit) {
                                    if (selectedDayIndex > 0) {
                                        selectedDayIndex--
                                    } else {
                                        val c = Calendar.getInstance().apply {
                                            timeInMillis = referenceTimeMs
                                            add(Calendar.WEEK_OF_YEAR, -1)
                                        }
                                        referenceTimeMs = c.timeInMillis
                                        selectedDayIndex = 6
                                    }
                                }
                            },
                            enabled = !isPastLimit
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Day",
                                tint = if (!isPastLimit) Color(0xFFA0A0AB) else Color(0xFF3C3C46)
                            )
                        }

                        Text(
                            text = selectedDay?.displayDate ?: "",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        val isFuture = run {
                            val todayKey = AnimeWatchUsageManager.getDateKey()
                            val nextCal = selectedDay?.calendar?.clone() as? Calendar
                            if (nextCal != null) {
                                nextCal.add(Calendar.DAY_OF_YEAR, 1)
                                AnimeWatchUsageManager.getDateKey(nextCal.timeInMillis) > todayKey
                            } else true
                        }

                        IconButton(
                            onClick = {
                                if (!isFuture) {
                                    if (selectedDayIndex < 6) {
                                        selectedDayIndex++
                                    } else {
                                        val c = Calendar.getInstance().apply {
                                            timeInMillis = referenceTimeMs
                                            add(Calendar.WEEK_OF_YEAR, 1)
                                        }
                                        referenceTimeMs = c.timeInMillis
                                        selectedDayIndex = 0
                                    }
                                }
                            },
                            enabled = !isFuture
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Day",
                                tint = if (!isFuture) Color(0xFFA0A0AB) else Color(0xFF3C3C46)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ─── DAILY INSIGHTS / ACTIVITY SUMMARY CARD (NO BORDER OUTLINE) ───
                item {
                    val epsCount = dayRecords.sumOf { it.episodesWatched.size }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF222632))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2D3242)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFBAC5DF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Anime Watch Insights",
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (dayRecords.isNotEmpty()) {
                                        "Watched ${dayRecords.size} anime${if (dayRecords.size > 1) "s" else ""}${if (epsCount > 0) " across $epsCount episodes" else ""} on this day."
                                    } else {
                                        "No watch activity recorded for this date."
                                    },
                                    color = Color(0xFFA6AEC2),
                                    fontSize = 12.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ─── ANIME USAGE LIST HEADER ──────────────────────────────────────
                if (dayRecords.isNotEmpty()) {
                    item {
                        Text(
                            text = "Watched Anime",
                            color = Color(0xFF8E8E98),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }

                    // ─── ANIME USAGE ITEMS ────────────────────────────────────────
                    items(dayRecords, key = { it.anilistId }) { item ->
                        val percent = if (totalTimeMs > 0L) {
                            ((item.watchTimeMs.toFloat() / totalTimeMs.toFloat()) * 100f).toInt().coerceIn(1, 100)
                        } else 0

                        AnimeUsageRow(
                            record = item,
                            percent = percent,
                            onClick = { onOpenAnimeDetails(item.anilistId) }
                        )
                    }
                } else {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No anime watched on this day",
                                color = Color(0xFF8E8E98),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 7-Day Bar Chart matching Screenshot 1 (Digital Wellbeing style)
 */
@Composable
fun WeeklyBarChart(
    weeklyData: List<DayUsageData>,
    selectedDayIndex: Int,
    onSelectDay: (Int) -> Unit
) {
    val maxMs = weeklyData.maxOfOrNull { it.totalWatchTimeMs }?.coerceAtLeast(3600000L * 4) ?: (3600000L * 4)
    // Round upper limit to whole hours (4h, 8h, 12h, 16h)
    val maxHours = ((maxMs / 3600000L) + 1).let {
        when {
            it <= 4 -> 4L
            it <= 8 -> 8L
            it <= 12 -> 12L
            it <= 16 -> 16L
            else -> ((it / 4) + 1) * 4
        }
    }
    val chartHeightDp = 130.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeightDp)
        ) {
            // Horizontal Guideline grid lines
            val levels = listOf(1f, 0.75f, 0.5f, 0.25f, 0f)
            val labels = listOf(
                "${maxHours}h",
                "${(maxHours * 0.75).toInt()}h",
                "${(maxHours * 0.5).toInt()}h",
                "${(maxHours * 0.25).toInt()}h",
                "0h"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width - 32.dp.toPx()
                val h = size.height
                levels.forEach { level ->
                    val y = h * (1f - level)
                    drawLine(
                        color = Color(0xFF2C2C34),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            // Right-side hour labels
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEach { lbl ->
                    Text(
                        text = lbl,
                        color = Color(0xFF6B6B78),
                        fontSize = 10.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(28.dp)
                    )
                }
            }

            // 7 Days of the week Bar Columns
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(end = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyData.forEachIndexed { index, day ->
                    val isSelected = index == selectedDayIndex
                    val fraction = (day.totalWatchTimeMs.toFloat() / (maxHours * 3600000f)).coerceIn(0f, 1f)
                    val barHeight = animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(300),
                        label = "barHeight_$index"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectDay(index) },
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Bar Column
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.68f)
                                .height(chartHeightDp * barHeight.value.coerceAtLeast(0.02f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    when {
                                        isSelected -> Color.White
                                        day.totalWatchTimeMs > 0L -> Color.White.copy(alpha = 0.35f)
                                        else -> Color.White.copy(alpha = 0.08f)
                                    }
                                )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Day of week labels (Sun, Mon, Tue, Wed, Thu, Fri, Sat)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weeklyData.forEachIndexed { index, day ->
                val isSelected = index == selectedDayIndex
                Text(
                    text = day.dayName,
                    color = if (isSelected) Color.White else Color(0xFF8E8E98),
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectDay(index) }
                )
            }
        }
    }
}

data class BreakdownSegment(
    val animeTitle: String,
    val shortLabel: String,
    val durationMs: Long,
    val sweepAngle: Float,
    val color: Color
)

/**
 * Donut Ring breakdown chart with vibrant anime segments, outside labels, and center stats matching Google Wellbeing
 */
@Composable
fun DonutBreakdownChart(
    records: List<AnimeUsageRecord>,
    totalTimeMs: Long,
    isToday: Boolean,
    displayDateLabel: String = ""
) {
    val density = LocalDensity.current.density
    val textPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#E4E4EE")
            textSize = 12f * density
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        }
    }

    // Build multi-anime segments with distinct colors
    val segments = remember(records, totalTimeMs) {
        if (records.isEmpty() || totalTimeMs <= 0L) {
            emptyList<BreakdownSegment>()
        } else {
            val sorted = records.sortedByDescending { it.watchTimeMs }
            val list = mutableListOf<BreakdownSegment>()
            if (sorted.size <= 3) {
                sorted.forEachIndexed { idx, rec ->
                    val sweep = (rec.watchTimeMs.toFloat() / totalTimeMs.toFloat()) * 360f
                    list.add(
                        BreakdownSegment(
                            animeTitle = rec.animeTitle,
                            shortLabel = getShortAnimeTitle(rec.animeTitle),
                            durationMs = rec.watchTimeMs,
                            sweepAngle = sweep,
                            color = BreakdownRingColors.getOrElse(idx % BreakdownRingColors.size) { Color.Gray }
                        )
                    )
                }
            } else {
                // Top 2 prominent anime get explicit named slices, remaining grouped into "Other"
                val top2 = sorted.take(2)
                val remaining = sorted.drop(2)
                top2.forEachIndexed { idx, rec ->
                    val sweep = (rec.watchTimeMs.toFloat() / totalTimeMs.toFloat()) * 360f
                    list.add(
                        BreakdownSegment(
                            animeTitle = rec.animeTitle,
                            shortLabel = getShortAnimeTitle(rec.animeTitle),
                            durationMs = rec.watchTimeMs,
                            sweepAngle = sweep,
                            color = BreakdownRingColors.getOrElse(idx) { Color.Gray }
                        )
                    )
                }
                val otherMs = remaining.sumOf { it.watchTimeMs }
                if (otherMs > 0L) {
                    val sweep = (otherMs.toFloat() / totalTimeMs.toFloat()) * 360f
                    list.add(
                        BreakdownSegment(
                            animeTitle = "Other",
                            shortLabel = "Other",
                            durationMs = otherMs,
                            sweepAngle = sweep,
                            color = Color(0xFFFBBC04) // Amber Yellow for Other (matches reference screenshot)
                        )
                    )
                }
            }
            list
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val ringRadius = 76.dp.toPx()
                val strokeWidth = 15.dp.toPx()
                val topLeft = Offset(cx - ringRadius, cy - ringRadius)
                val arcSize = Size(ringRadius * 2f, ringRadius * 2f)

                if (segments.isEmpty()) {
                    drawArc(
                        color = Color(0xFF2C2C34),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                } else {
                    var currentAngle = -90f // Start at 12 o'clock
                    val labelRadius = ringRadius + (strokeWidth / 2f) + 16.dp.toPx()

                    segments.forEach { seg ->
                        val gap = if (segments.size > 1) 3.5f else 0f
                        val actualSweep = (seg.sweepAngle - gap).coerceAtLeast(1.5f)

                        // Draw colorful ring arc
                        drawArc(
                            color = seg.color,
                            startAngle = currentAngle + (gap / 2f),
                            sweepAngle = actualSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Draw short anime name outside the ring
                        if (seg.sweepAngle >= 14f) {
                            val midAngle = currentAngle + (seg.sweepAngle / 2f)
                            val rad = Math.toRadians(midAngle.toDouble())
                            val lx = cx + (labelRadius * Math.cos(rad)).toFloat()
                            val ly = cy + (labelRadius * Math.sin(rad)).toFloat()

                            val cosVal = Math.cos(rad)
                            val sinVal = Math.sin(rad)

                            textPaint.textAlign = when {
                                cosVal > 0.3 -> android.graphics.Paint.Align.LEFT
                                cosVal < -0.3 -> android.graphics.Paint.Align.RIGHT
                                else -> android.graphics.Paint.Align.CENTER
                            }

                            val vOffset = when {
                                sinVal > 0.65 -> (textPaint.descent() - textPaint.ascent()) * 0.7f
                                sinVal < -0.65 -> -(textPaint.descent() - textPaint.ascent()) * 0.3f
                                else -> -(textPaint.descent() + textPaint.ascent()) / 2f
                            }

                            drawContext.canvas.nativeCanvas.drawText(
                                seg.shortLabel,
                                lx,
                                ly + vOffset,
                                textPaint
                            )
                        }

                        currentAngle += seg.sweepAngle
                    }
                }
            }

            // Center Hero Text inside ring
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isToday) "TODAY" else displayDateLabel.ifEmpty { "WATCH TIME" }.uppercase(),
                    color = Color(0xFF8E8E98),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = AnimeWatchUsageManager.formatDuration(totalTimeMs),
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Legend row below ring showing color badges + short anime titles
        if (segments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                segments.forEach { seg ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(seg.color)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = seg.shortLabel,
                            color = Color(0xFFBAC0D0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single anime usage row with natural AniList portrait poster (NOT square!) and NO outline borders
 */
@Composable
fun AnimeUsageRow(
    record: AnimeUsageRecord,
    percent: Int,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Natural 2:3 Aspect Ratio AniList Poster (46.dp x 66.dp, NOT square!)
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(66.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E24)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = if (record.posterUrl.isNotBlank()) record.posterUrl else R.drawable.default_cover
                ),
                contentDescription = record.animeTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title and formatted duration
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.animeTitle,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            val epText = if (record.episodesWatched.isNotEmpty()) {
                val count = record.episodesWatched.size
                if (count == 1) " • Ep ${record.episodesWatched.first()}"
                else " • $count episodes"
            } else ""

            Text(
                text = "${AnimeWatchUsageManager.formatDuration(record.watchTimeMs)}$epText",
                color = Color(0xFFA0A0AB),
                fontSize = 12.5.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Clean percentage indicator
        if (percent > 0) {
            Text(
                text = "$percent%",
                color = Color(0xFF8E8E98),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
