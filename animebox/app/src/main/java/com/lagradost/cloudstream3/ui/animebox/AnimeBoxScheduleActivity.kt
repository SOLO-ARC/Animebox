package com.lagradost.cloudstream3.ui.animebox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import com.lagradost.cloudstream3.ui.animebox.notifications.ScheduleAlert
import com.lagradost.cloudstream3.ui.animebox.notifications.ScheduleAlertManager
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class ScheduleMedia(
    val id: Int,
    val title: String,
    val coverUrl: String,
    val bannerUrl: String,
    val format: String,
    val year: Int,
    val status: String
)

data class AiringScheduleItem(
    val id: Int,
    val airingAt: Long,
    val timeUntilAiring: Long,
    val episode: Int,
    val media: ScheduleMedia
)

data class ScheduleDay(
    val offset: Int,
    val title: String,
    val subtitle: String,
    val fullDateString: String,
    val startTimestamp: Long,
    val endTimestamp: Long
)

class AnimeBoxScheduleActivity : ComponentActivity() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Rich genre-matching colors for each day pill
    private val dayGenreColors = listOf(
        Color(0xFF6B21A8), // Deep Violet (Day -2)
        Color(0xFF831843), // Crimson Berry (Yesterday / Day -1)
        Color(0xFF1E3A8A), // Deep Royal Navy (Today / Day 0)
        Color(0xFF065F46), // Dark Emerald (Tomorrow / Day 1)
        Color(0xFF78350F), // Warm Mahogany (Day 2)
        Color(0xFF0F766E), // Deep Teal (Day 3)
        Color(0xFF9D174D)  // Rose Crimson (Day 4)
    )

    // Palette for timeline node dots
    private val nodeDotColors = listOf(
        Color(0xFFE5484D), // Crimson Coral
        Color(0xFF8B5CF6), // Royal Purple
        Color(0xFF3B82F6), // Ocean Blue
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Golden Amber
        Color(0xFFEC4899), // Hot Pink
        Color(0xFF06B6D4), // Cyan
        Color(0xFF84CC16), // Lime Green
        Color(0xFFA855F7), // Violet
        Color(0xFFF97316)  // Tangerine Orange
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val daysList = remember { generateScheduleDays() }
            var selectedDayIndex by remember { mutableIntStateOf(2) } // index 2 = Today
            val currentDay = daysList[selectedDayIndex]

            var schedules by remember(currentDay) { mutableStateOf<List<AiringScheduleItem>>(emptyList()) }
            var isLoading by remember(currentDay) { mutableStateOf(true) }
            var errorMsg by remember(currentDay) { mutableStateOf<String?>(null) }
            var notifiedItems by remember { mutableStateOf(ScheduleAlertManager.getAlertIds(this@AnimeBoxScheduleActivity)) }

            LaunchedEffect(currentDay) {
                isLoading = true
                errorMsg = null
                try {
                    val result = fetchSchedules(currentDay.startTimestamp, currentDay.endTimestamp)
                    schedules = result
                } catch (e: Exception) {
                    errorMsg = "Unable to load schedule. Tap to retry."
                } finally {
                    isLoading = false
                }
            }

            Scaffold(
                bottomBar = {
                    ScheduleBottomNav(
                        onTabSelected = { tabIndex ->
                            val intent = Intent(this@AnimeBoxScheduleActivity, AnimeBoxMainActivity::class.java).apply {
                                putExtra("selectTab", tabIndex)
                                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(intent)
                            finish()
                        }
                    )
                },
                containerColor = Color(0xFF0C0D12)
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = paddingValues.calculateBottomPadding())
                        .statusBarsPadding()
                ) {
                    // Header Row: Clean Title without Back Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Schedule",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    // Horizontal Scrollable Day Selector Pills (Less rounded: 6.dp, distinct genre colors)
                    val scrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        daysList.forEachIndexed { index, day ->
                            val isSelected = selectedDayIndex == index
                            val pillColor = dayGenreColors[index % dayGenreColors.size]

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) {
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    pillColor,
                                                    pillColor.copy(alpha = 0.82f)
                                                )
                                            )
                                        } else {
                                            SolidColor(pillColor.copy(alpha = 0.22f))
                                        }
                                    )
                                    .then(
                                        if (isSelected) Modifier.border(1.2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                                    .clickable { selectedDayIndex = index }
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = day.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color(0xFFD4D6E2)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = day.subtitle,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color(0xFF8E91A3)
                                    )
                                }
                            }
                        }
                    }

                    // Full Date Subtitle
                    Text(
                        text = currentDay.fullDateString,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8E91A3),
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 10.dp)
                    )

                    // Content Area
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    } else if (errorMsg != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    isLoading = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMsg ?: "Error loading",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    } else if (schedules.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No releases scheduled for this day.",
                                color = Color(0xFF7A7D8F),
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = TimeZone.getDefault() } }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            items(schedules, key = { it.id }) { item ->
                                val timeStr = timeFormat.format(Date(item.airingAt * 1000L)).lowercase()
                                val isPast = (item.airingAt * 1000L) < System.currentTimeMillis()
                                val isNotified = notifiedItems.contains(item.id)
                                val dotColor = nodeDotColors[Math.abs(item.id.hashCode()) % nodeDotColors.size]

                                // Row containing continuous connected line on left & time/card on right
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                ) {
                                    // Left Timeline Column (Connected thin line + colorful ring node)
                                    Box(
                                        modifier = Modifier
                                            .width(26.dp)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        // Continuous vertical connecting line
                                        Box(
                                            modifier = Modifier
                                                .width(1.5.dp)
                                                .fillMaxHeight()
                                                .background(Color(0xFF2E303E))
                                        )

                                        // Hollow Ring Node (matching reference image)
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .size(11.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF0C0D12))
                                                .border(2.dp, dotColor, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(3.5.dp)
                                                    .clip(CircleShape)
                                                    .background(dotColor)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Right Content Column: Airing Time & Anime Card
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(bottom = 16.dp)
                                    ) {
                                        Text(
                                            text = timeStr,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )

                                        // Anime Card (No thin borders, sleek less rounded boxes: 6.dp)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF14151C))
                                                .clickable {
                                                    val intent = Intent(this@AnimeBoxScheduleActivity, AnimeBoxDetailActivity::class.java).apply {
                                                        putExtra("anilistId", item.media.id)
                                                        putExtra("initialTitle", item.media.title)
                                                        putExtra("initialCoverUrl", item.media.coverUrl)
                                                        putExtra("initialBannerUrl", item.media.bannerUrl)
                                                        putExtra("initialYear", item.media.year)
                                                        putExtra("initialFormat", item.media.format)
                                                    }
                                                    startActivity(intent)
                                                }
                                                .padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Poster (Less rounded: 6.dp)
                                                Box(
                                                    modifier = Modifier
                                                        .width(62.dp)
                                                        .height(86.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF1E1F28))
                                                ) {
                                                    Image(
                                                        painter = rememberAsyncImagePainter(model = item.media.coverUrl),
                                                        contentDescription = item.media.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                // Center info: Title, Episode, Tags
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = item.media.title,
                                                        fontSize = 14.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Text(
                                                        text = "Episode ${item.episode}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFF8B8E9E)
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val formatLabel = when (item.media.format.uppercase()) {
                                                            "TV_SHORT" -> "TV SHORT"
                                                            "MOVIE" -> "MOVIE"
                                                            "SPECIAL" -> "SPECIAL"
                                                            "ONA" -> "ONA"
                                                            "OVA" -> "OVA"
                                                            else -> "TV"
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0xFF222430))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = formatLabel,
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFFB0B3C4)
                                                            )
                                                        }

                                                        if (item.media.year > 0) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Color(0xFF222430))
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "${item.media.year}",
                                                                    fontSize = 9.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFFB0B3C4)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                // Right info: Time, Status, Bell icon (matching Home)
                                                Column(
                                                    horizontalAlignment = Alignment.End,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = timeStr,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(if (isPast) Color(0xFF1B1C24) else Color(0xFF262834))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = if (isPast) "AIRED" else "UPCOMING",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = if (isPast) Color(0xFF7A7D8F) else Color(0xFFE2E4EE)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    IconButton(
                                                        onClick = {
                                                            if (isNotified) {
                                                                ScheduleAlertManager.removeAlert(this@AnimeBoxScheduleActivity, item.id)
                                                                notifiedItems = notifiedItems - item.id
                                                                Toast.makeText(this@AnimeBoxScheduleActivity, "Notification cancelled", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                val alert = ScheduleAlert(
                                                                    scheduleId = item.id,
                                                                    anilistId = item.media.id,
                                                                    title = item.media.title,
                                                                    episode = item.episode,
                                                                    airingAt = item.airingAt,
                                                                    coverUrl = item.media.coverUrl
                                                                )
                                                                ScheduleAlertManager.saveAlert(this@AnimeBoxScheduleActivity, alert)
                                                                notifiedItems = notifiedItems + item.id
                                                                Toast.makeText(this@AnimeBoxScheduleActivity, "Alert set for Episode ${item.episode}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = CustomNotificationIcon,
                                                            contentDescription = "Alert",
                                                            tint = if (isNotified) Color.White else Color(0xFF7A7D8F),
                                                            modifier = Modifier.size(21.dp)
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
    }

    @Composable
    private fun ScheduleBottomNav(onTabSelected: (Int) -> Unit) {
        val context = this
        val activeProfId = ProfileManager.getActiveProfile(context)
        val activeProfileObj = ProfileManager.getProfiles(context).find { it.id == activeProfId }
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121212))
                .navigationBarsPadding()
                .padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val unselectedColor = Color(0xFF8E8E93)

                // Tab 0: Home
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(0) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = CustomHomeOutlineIcon,
                        contentDescription = "Home",
                        tint = unselectedColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Home",
                        color = unselectedColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Tab 1: Search
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(1) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = CustomSearchIcon,
                        contentDescription = "Search",
                        tint = unselectedColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Search",
                        color = unselectedColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Tab 2: Downloads
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(2) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = CustomDownloadsNavbarIcon,
                        contentDescription = "Downloads",
                        tint = unselectedColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Downloads",
                        color = unselectedColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Tab 3: My List
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(3) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = CustomMyListIcon,
                        contentDescription = "My List",
                        tint = unselectedColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "My List",
                        color = unselectedColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Tab 4: My Space
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(4) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF222228))
                    ) {
                        if (avatarUrl.isNotEmpty()) {
                            Image(
                                painter = rememberAsyncImagePainter(model = profilePainter),
                                contentDescription = "My Space",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF8B5CF6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeProfileObj?.name?.take(1)?.uppercase() ?: "U",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "My Space",
                        color = unselectedColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    private suspend fun fetchSchedules(startTimestamp: Long, endTimestamp: Long): List<AiringScheduleItem> = withContext(Dispatchers.IO) {
        val query = """
            query (${'$'}start: Int, ${'$'}end: Int) {
              Page(page: 1, perPage: 50) {
                airingSchedules(airingAt_greater: ${'$'}start, airingAt_lesser: ${'$'}end, sort: TIME) {
                  id
                  airingAt
                  timeUntilAiring
                  episode
                  media {
                    id
                    title {
                      romaji
                      english
                      native
                    }
                    coverImage {
                      large
                      medium
                    }
                    format
                    startDate {
                      year
                    }
                    status
                    bannerImage
                  }
                }
              }
            }
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("query", query)
            put("variables", JSONObject().apply {
                put("start", startTimestamp)
                put("end", endTimestamp)
            })
        }

        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "Mozilla/5.0")
            .build()

        val items = mutableListOf<AiringScheduleItem>()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val jsonObj = JSONObject(bodyStr)
                val data = jsonObj.optJSONObject("data") ?: return@withContext emptyList()
                val page = data.optJSONObject("Page") ?: return@withContext emptyList()
                val schedulesArr = page.optJSONArray("airingSchedules") ?: return@withContext emptyList()

                fun isValidTitle(s: String?): Boolean {
                    return !s.isNullOrBlank() && !s.equals("null", ignoreCase = true)
                }

                for (i in 0 until schedulesArr.length()) {
                    val sObj = schedulesArr.getJSONObject(i)
                    val sId = sObj.optInt("id", 0)
                    val airingAt = sObj.optLong("airingAt", 0L)
                    val timeUntilAiring = sObj.optLong("timeUntilAiring", 0L)
                    val episode = sObj.optInt("episode", 1)

                    val mObj = sObj.optJSONObject("media") ?: continue
                    val mId = mObj.optInt("id", 0)
                    val tObj = mObj.optJSONObject("title")

                    val englishTitle = tObj?.optString("english", "")?.trim()
                    val romajiTitle = tObj?.optString("romaji", "")?.trim()
                    val nativeTitle = tObj?.optString("native", "")?.trim()

                    // Ensure we prioritize English, and never show "null"
                    val finalTitle = when {
                        isValidTitle(englishTitle) -> englishTitle!!
                        isValidTitle(romajiTitle) -> romajiTitle!!
                        isValidTitle(nativeTitle) -> nativeTitle!!
                        else -> "Untitled"
                    }

                    val cObj = mObj.optJSONObject("coverImage")
                    val cover = cObj?.optString("large", "")?.ifEmpty { cObj.optString("medium", "") } ?: ""
                    val banner = mObj.optString("bannerImage", "")
                    val format = mObj.optString("format", "TV")
                    val status = mObj.optString("status", "RELEASING")
                    val startObj = mObj.optJSONObject("startDate")
                    val year = startObj?.optInt("year", 0) ?: 0

                    items.add(
                        AiringScheduleItem(
                            id = sId,
                            airingAt = airingAt,
                            timeUntilAiring = timeUntilAiring,
                            episode = episode,
                            media = ScheduleMedia(
                                id = mId,
                                title = finalTitle,
                                coverUrl = cover,
                                bannerUrl = banner,
                                format = format,
                                year = year,
                                status = status
                            )
                        )
                    )
                }
            }
        }
        return@withContext items
    }

    private fun generateScheduleDays(): List<ScheduleDay> {
        val days = mutableListOf<ScheduleDay>()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val titleFormat = SimpleDateFormat("EEE", Locale.US)
        val subtitleFormat = SimpleDateFormat("MMM d", Locale.US)
        val fullFormat = SimpleDateFormat("EEEE, MMM d", Locale.US)

        for (offset in -2..4) {
            val dayCal = cal.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_YEAR, offset)

            val startSec = dayCal.timeInMillis / 1000L
            val endSec = startSec + 86400L

            val title = when (offset) {
                -1 -> "Yesterday"
                0 -> "Today"
                1 -> "Tomorrow"
                else -> titleFormat.format(dayCal.time)
            }
            val subtitle = subtitleFormat.format(dayCal.time)
            val fullStr = fullFormat.format(dayCal.time)

            days.add(
                ScheduleDay(
                    offset = offset,
                    title = title,
                    subtitle = subtitle,
                    fullDateString = fullStr,
                    startTimestamp = startSec,
                    endTimestamp = endSec
                )
            )
        }
        return days
    }
}
