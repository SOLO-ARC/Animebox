package com.lagradost.cloudstream3.ui.animebox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.lagradost.cloudstream3.ui.animebox.download.*
import com.lagradost.cloudstream3.ui.animebox.notifications.*
import com.lagradost.cloudstream3.ui.animebox.settings.PixelSettingsIcon
import com.lagradost.cloudstream3.ui.animebox.settings.SettingsIconType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.cloudstream3.R
import androidx.compose.ui.res.painterResource
import com.lagradost.cloudstream3.ui.animebox.api.AniListClient
import com.lagradost.cloudstream3.ui.animebox.api.AniZipClient
import com.lagradost.cloudstream3.ui.animebox.history.WatchHistoryItem
import com.lagradost.cloudstream3.ui.animebox.history.WatchHistoryManager
import com.lagradost.cloudstream3.ui.animebox.library.LibraryManager
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import com.lagradost.cloudstream3.ui.animebox.profiles.UserProfile
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsDialog
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import java.io.File
import org.json.JSONObject
import org.json.JSONArray

fun resolveImageModel(urlOrPath: String): Any {
    if (urlOrPath.isBlank()) return ""
    if (urlOrPath.startsWith("/") && !urlOrPath.startsWith("file://")) {
        val f = File(urlOrPath)
        if (f.exists()) return f
    }
    return urlOrPath
}

fun getReleaseTimeAgo(year: Int, month: Int, day: Int): String {
    if (year <= 0) return "Recently"
    
    val calendar = java.util.Calendar.getInstance()
    val nowMs = calendar.timeInMillis
    
    val releaseCal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.YEAR, year)
        set(java.util.Calendar.MONTH, if (month > 0) month - 1 else 0)
        set(java.util.Calendar.DAY_OF_MONTH, if (day > 0) day else 1)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val releaseMs = releaseCal.timeInMillis
    
    val diffMs = nowMs - releaseMs
    if (diffMs <= 0) {
        return "Just now"
    }
    
    val diffMinutes = diffMs / (1000 * 60)
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24
    val diffWeeks = diffDays / 7
    val diffMonths = diffDays / 30
    val diffYears = diffDays / 365

    return when {
        diffHours < 1 -> "Just now"
        diffHours < 24 -> "${diffHours}h ago"
        diffDays == 1L -> "1 day ago"
        diffDays < 7 -> "${diffDays} days ago"
        diffDays < 30 -> "${diffDays / 7} weeks ago"
        else -> "${diffDays / 30} months ago"
    }
}

// Persistent Tag Order Helpers
fun getSavedCategoryOrder(context: Context): List<String> {
    val defaultList = listOf("All", "Currently Watching", "Completed", "Plan to Watch", "On Hold", "Dropped", "Re-watching", "Favorites")
    val prefs = context.getSharedPreferences("AnimeBox_Tag_Orders", Context.MODE_PRIVATE)
    val savedJson = prefs.getString("my_list_category_order", null) ?: return defaultList
    return try {
        val arr = JSONArray(savedJson)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.getString(i)
            if (!list.contains(item)) list.add(item)
        }
        defaultList.forEach { if (!list.contains(it)) list.add(it) }
        list
    } catch (e: Exception) {
        defaultList
    }
}

fun saveCategoryOrder(context: Context, order: List<String>) {
    val prefs = context.getSharedPreferences("AnimeBox_Tag_Orders", Context.MODE_PRIVATE)
    val arr = JSONArray(order)
    prefs.edit().putString("my_list_category_order", arr.toString()).apply()
}

fun getSavedGenreOrder(context: Context): List<String> {
    val defaultList = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Fantasy", 
        "Mystery", "Psychological", "Romance", "Sci-Fi", 
        "Slice of Life", "Sports", "Supernatural", "Thriller",
        "Ecchi", "Horror", "Mahou Shoujo", "Mecha", "Music"
    )
    val prefs = context.getSharedPreferences("AnimeBox_Tag_Orders", Context.MODE_PRIVATE)
    val savedJson = prefs.getString("search_genre_order", null) ?: return defaultList
    return try {
        val arr = JSONArray(savedJson)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.getString(i)
            if (!list.contains(item)) list.add(item)
        }
        defaultList.forEach { if (!list.contains(it)) list.add(it) }
        list
    } catch (e: Exception) {
        defaultList
    }
}

fun saveGenreOrder(context: Context, order: List<String>) {
    val prefs = context.getSharedPreferences("AnimeBox_Tag_Orders", Context.MODE_PRIVATE)
    val arr = JSONArray(order)
    prefs.edit().putString("search_genre_order", arr.toString()).apply()
}

@Composable
fun ReorderableDragTagRow(
    tags: List<String>,
    selectedTag: String,
    onTagSelected: (String) -> Unit,
    onOrderChanged: (List<String>) -> Unit,
    isCategoryStyle: Boolean = false,
    isCategorySelected: ((String) -> Boolean)? = null,
    modifier: Modifier = Modifier
) {
    var tagList by remember(tags) { mutableStateOf(tags) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulatedX by remember { mutableStateOf(0f) }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        itemsIndexed(tagList, key = { _, tag -> tag }) { index, tag ->
            val isDragging = draggingIndex == index
            val isSelected = isCategorySelected?.invoke(tag) ?: (selectedTag == tag)

            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 10f else 1f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationX = dragAccumulatedX
                            scaleX = 1.08f
                            scaleY = 1.08f
                            shadowElevation = 12f
                        }
                    }
                    .clip(if (isCategoryStyle) RoundedCornerShape(10.dp) else RoundedCornerShape(4.dp))
                    .background(
                        if (isSelected) AnimeBoxThemeHelper.getPrimaryColor(LocalContext.current)
                        else if (isCategoryStyle) Color(0xFF1E1F27)
                        else Color(0xFF1E1E1E)
                    )
                    .pointerInput(tagList) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragAccumulatedX = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulatedX += dragAmount.x
                                val swapThreshold = 180f
                                val targetOffset = (dragAccumulatedX / swapThreshold).toInt()
                                if (targetOffset != 0) {
                                    val currentIdx = draggingIndex ?: index
                                    val newIdx = (currentIdx + targetOffset).coerceIn(0, tagList.size - 1)
                                    if (currentIdx != newIdx) {
                                        val mutable = tagList.toMutableList()
                                        val movedItem = mutable.removeAt(currentIdx)
                                        mutable.add(newIdx, movedItem)
                                        tagList = mutable
                                        draggingIndex = newIdx
                                        dragAccumulatedX = 0f
                                        onOrderChanged(mutable)
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingIndex = null
                                dragAccumulatedX = 0f
                                onOrderChanged(tagList)
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragAccumulatedX = 0f
                            }
                        )
                    }
                    .clickable {
                        if (draggingIndex == null) {
                            onTagSelected(tag)
                        }
                    }
                    .padding(
                        horizontal = 14.dp,
                        vertical = if (isCategoryStyle) 8.dp else 6.dp
                    )
            ) {
                Text(
                    text = tag,
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = if (isCategoryStyle) 12.5.sp else 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class AnimeBrief(
    val id: Int,
    val title: String,
    val coverUrl: String,
    val bannerUrl: String,
    val description: String,
    val genres: List<String> = emptyList(),
    val averageScore: Int = 0,
    val logoUrl: String = "",
    val episodes: Int = 0,
    val status: String = "", // "NOT_YET_RELEASED" for Coming Soon
    val releaseYear: Int = 0,
    val releaseMonth: Int = 0,
    val releaseDay: Int = 0,
    val customListCategory: String = "Planning",
    val hasRecentEpisode: Boolean = false
)

class AnimeBoxMainActivity : ComponentActivity() {

    companion object {
        // Fixed spotlight anime IDs (appear first in spotlight rotation)
        private val FIXED_SPOTLIGHT_IDS = listOf(
            99423,  // Darling in the FranXX
            129201, // Summer Time Rendering
            127230, // Chainsaw Man
            150672, // Oshi no Ko
            151807, // Solo Leveling
            98659,  // Classroom of the Elite
            137822, // Blue Lock
            21234,  // Erased
            113813, // Rent-a-Girlfriend
            155963, // Hokkaido Gals Are Super Adorable!
            226,    // Elfen Lied
            20605   // Tokyo Ghoul
        )
        private const val SUMMER_TIME_RENDERING_ID = 129201
        private const val SUMMER_TIME_RENDERING_BACKDROP = "https://image.tmdb.org/t/p/original/1czz0r7urqCPP0CZTAEkCk4TZY1.jpg"

        val STATIC_FIXED_SPOTLIGHT_ANIMES = listOf(
            AnimeBrief(
                id = 99423,
                title = "Darling in the FranXX",
                coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/nx99423-8KyKcaR8KGb0.jpg",
                bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/99423-4Q12qYjDkJ78.jpg",
                description = "In the distant future, humanity has been driven to the brink of extinction by giant beasts known as Klaxosaurs...",
                genres = listOf("Action", "Drama", "Mecha", "Romance", "Sci-Fi"),
                averageScore = 72,
                episodes = 24
            ),
            AnimeBrief(
                id = 129201,
                title = "Summer Time Rendering",
                coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx129201-NqL99Y2qG3g5.jpg",
                bannerUrl = "",
                description = "Upon hearing of Ushio's death, Shinpei returns to his hometown of Wakayama city on Hitogashima...",
                genres = listOf("Action", "Drama", "Mystery", "Supernatural", "Suspense"),
                averageScore = 84,
                episodes = 25
            ),
            AnimeBrief(
                id = 127230,
                title = "Chainsaw Man",
                coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx127230-FloCoAS2zjh4.png",
                bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/127230-f4qTzpwbQpZs.jpg",
                description = "Denji is a young man living a life of poverty, working as a Devil Hunter alongside Pochita...",
                genres = listOf("Action", "Drama", "Horror", "Supernatural"),
                averageScore = 84,
                episodes = 12
            ),
            AnimeBrief(
                id = 150672,
                title = "Oshi no Ko",
                coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx150672-6GFbJqA9wz8L.png",
                bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/150672-3yD5V7vK9L7J.jpg",
                description = "Sixteen-year-old Ai Hoshino is a talented and beautiful idol adored by her fans...",
                genres = listOf("Drama", "Mystery", "Supernatural"),
                averageScore = 85,
                episodes = 11
            ),
            AnimeBrief(
                id = 151807,
                title = "Solo Leveling",
                coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx151807-31faFprP1Akg.png",
                bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/151807-3Z7s5W20aM7k.jpg",
                description = "They say whatever doesn't kill you makes you stronger, but that's not the case for the world's weakest hunter Sung Jinwoo...",
                genres = listOf("Action", "Adventure", "Fantasy"),
                averageScore = 83,
                episodes = 12
            ),
            AnimeBrief(
                id = 98659,
                title = "Classroom of the Elite",
                coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/nx98659-15Gk5i2wG8P7.jpg",
                bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/98659-0Z1s8xL9p5W3.jpg",
                description = "Koudo Ikusei Senior High School is a leading prestigious school with state-of-the-art facilities...",
                genres = listOf("Drama", "Mystery", "Psychological"),
                averageScore = 78,
                episodes = 12
            ),
            AnimeBrief(
                id = 137822,
                title = "Blue Lock",
                coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx137822-4D3G9p8k9L8M.jpg",
                bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/137822-7qL8w9P5x3Z1.jpg",
                description = "After a disastrous defeat at the 2018 World Cup, Japan's team struggles to regroup...",
                genres = listOf("Action", "Drama", "Sports"),
                averageScore = 80,
                episodes = 24
            ),
            AnimeBrief(
                id = 21234,
                title = "Erased",
                coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21234-7s7Rk38K69P0.jpg",
                bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/21234-9L3p5W8x2Z1M.jpg",
                description = "Satoru Fujinuma is a 29-year-old manga artist who possesses an involuntary ability called Revival...",
                genres = listOf("Drama", "Mystery", "Psychological", "Supernatural", "Suspense"),
                averageScore = 82,
                episodes = 12
            ),
            AnimeBrief(
                id = 113813,
                title = "Rent-a-Girlfriend",
                coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx113813-jL9p8K5x3Z1M.jpg",
                bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/113813-7qL8w9P5x3Z1.jpg",
                description = "Dumped by his girlfriend, emotionally shattered college student Kazuya Kinoshita attempts to appease the void in his heart...",
                genres = listOf("Comedy", "Drama", "Romance"),
                averageScore = 67,
                episodes = 12
            ),
            AnimeBrief(
                id = 155963,
                title = "Hokkaido Gals Are Super Adorable!",
                coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx155963-7qL8w9P5x3Z1.jpg",
                bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/155963-3Z7s5W20aM7k.jpg",
                description = "High school boy Tsubasa moves to Kitami City in Hokkaido, where he meets a 'gal' at a bus stop...",
                genres = listOf("Comedy", "Romance", "Slice of Life"),
                averageScore = 74,
                episodes = 12
            ),
            AnimeBrief(
                id = 226,
                title = "Elfen Lied",
                coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx226-7qL8w9P5x3Z1.jpg",
                bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/226-3Z7s5W20aM7k.jpg",
                description = "Lucy is a special breed of human referred to as a Diclonius, born with a short pair of horns and invisible telekinetic hands...",
                genres = listOf("Action", "Drama", "Horror", "Psychological", "Sci-Fi", "Supernatural"),
                averageScore = 74,
                episodes = 13
            ),
            AnimeBrief(
                id = 20605,
                title = "Tokyo Ghoul",
                coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20605-jL9p8K5x3Z1M.jpg",
                bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/20605-7qL8w9P5x3Z1.jpg",
                description = "Tokyo has become a cruel and merciless city—a place where vicious creatures called ghouls exist alongside humans...",
                genres = listOf("Action", "Drama", "Horror", "Mystery", "Psychological", "Supernatural"),
                averageScore = 75,
                episodes = 12
            )
        )
    }

    private val activeNavTab = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialTab = intent?.getIntExtra("selectTab", 0) ?: 0
        activeNavTab.value = initialTab
        setContent {
            HomeScreen(activeNavTab)
        }
    }

    private var onResumeRefresh: (() -> Unit)? = null

    override fun onResume() {
        super.onResume()
        onResumeRefresh?.invoke()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val reqTab = intent.getIntExtra("selectTab", -1)
        if (reqTab >= 0) {
            activeNavTab.value = reqTab
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HomeScreen(navTabState: MutableState<Int> = mutableStateOf(0)) {
        val coroutineScope = rememberCoroutineScope()
        var themeTrigger by remember { mutableStateOf(0) }
        val primaryColor = remember(themeTrigger) { AnimeBoxThemeHelper.getPrimaryColor(this@AnimeBoxMainActivity) }
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = primaryColor,
                background = Color(0xFF000000), // Pitch Black
                surface = Color(0xFF1E1E1E)
            )
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            var currentProfileId by remember { mutableStateOf(ProfileManager.getActiveProfile(this@AnimeBoxMainActivity)) }
            var showProfileSelector by remember { mutableStateOf(false) }
            var showSettingsDialog by remember { mutableStateOf(false) }
            val historyManager = remember(currentProfileId) { WatchHistoryManager(this@AnimeBoxMainActivity) }
        val libraryManager = remember(currentProfileId) { LibraryManager(this@AnimeBoxMainActivity) }

        var trendingList by remember { mutableStateOf<List<AnimeBrief>>(emptyList()) }
        var continueWatchingList by remember(currentProfileId) { mutableStateOf<List<WatchHistoryItem>>(emptyList()) }
        var libraryItemsList by remember(currentProfileId) { mutableStateOf<List<AnimeBrief>>(emptyList()) }

        DisposableEffect(Unit) {
            onResumeRefresh = {
                val actProf = ProfileManager.getActiveProfile(this@AnimeBoxMainActivity)
                currentProfileId = actProf
                val hMgr = WatchHistoryManager(this@AnimeBoxMainActivity)
                val lMgr = LibraryManager(this@AnimeBoxMainActivity)
                continueWatchingList = hMgr.getWatchHistory()
                libraryItemsList = lMgr.getLibraryItems()
            }
            onDispose {
                onResumeRefresh = null
            }
        }
        var spotlightAnimes by remember { mutableStateOf<List<AnimeBrief>>(emptyList()) }
        var spotlightIndex by remember { mutableStateOf(0) }
        var isLoading by remember { mutableStateOf(true) }
        var continueLaunchLoading by remember { mutableStateOf(false) }

        // Extra Home sections
        var recentlyAddedList by remember { mutableStateOf<List<AnimeBrief>>(emptyList()) }
        var thisSeasonList by remember { mutableStateOf<List<AnimeBrief>>(emptyList()) }
        var moviesListData by remember { mutableStateOf<List<AnimeBrief>>(emptyList()) }
        var comingSoonList by remember { mutableStateOf<List<AnimeBrief>>(emptyList()) }
        var comingSoonIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
        var comingSoonAlertAnime by remember { mutableStateOf<AnimeBrief?>(null) }

        // Suggestion Section states
        var suggestionList by remember { mutableStateOf<List<AnimeBrief>>(emptyList()) }
        var suggestionIndex by remember { mutableStateOf(0) }
        var suggestionBackdropUrl by remember { mutableStateOf("") }
        var isAniZipBackdrop by remember { mutableStateOf(false) }

        // Navigation state
        var selectedTab by navTabState // 0: Home, 1: Search, 2: Library
        var initialProfileForEditing by remember { mutableStateOf<UserProfile?>(null) }
        var showNotificationsDialog by remember { mutableStateOf(false) }
        var supabaseNotifications by remember { mutableStateOf<List<SupabaseNotification>>(emptyList()) }
        var unreadNotifCount by remember { mutableIntStateOf(0) }

        // Fetch Supabase notifications on launch & when opened
        LaunchedEffect(Unit) {
            supabaseNotifications = SupabaseNotificationManager.getCachedNotifications(this@AnimeBoxMainActivity)
            unreadNotifCount = SupabaseNotificationManager.getUnreadCount(this@AnimeBoxMainActivity)
            try {
                val fetched = SupabaseNotificationManager.fetchNotifications(this@AnimeBoxMainActivity)
                supabaseNotifications = fetched
                unreadNotifCount = fetched.count { !it.isRead }
            } catch (_: Exception) {}
        }

        LaunchedEffect(showNotificationsDialog) {
            if (showNotificationsDialog) {
                try {
                    val fresh = SupabaseNotificationManager.fetchNotifications(this@AnimeBoxMainActivity)
                    supabaseNotifications = fresh
                    unreadNotifCount = fresh.count { !it.isRead }
                } catch (_: Exception) {}
            }
        }

        var showDnsDialog by remember { mutableStateOf(false) }
        var anilistErrorMessage by remember { mutableStateOf<String?>(null) }
        var showOutageBanner by remember { mutableStateOf(false) }
        var isOffline by remember { mutableStateOf(false) }
        var showServiceUnavailableDialog by remember { mutableStateOf(false) }

        // Kitsu Fallback states
        var isKitsuMode by remember { mutableStateOf(false) }
        var showKitsuFallbackPromptDialog by remember { mutableStateOf(false) }
        var showKitsuMyListRestrictionDialog by remember { mutableStateOf(false) }
        var retryCount by remember { mutableStateOf(0) }

        // Search specific states
        var searchQuery by remember { mutableStateOf("") }
        var activeSearchGenre by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<AnimeBrief>>(emptyList()) }
        var isSearchLoading by remember { mutableStateOf(false) }
        var recommendedAnimes by remember { mutableStateOf<List<AnimeBrief>>(emptyList()) }

        // Genre pagination state (declared before filterByGenre lambda to be in scope)
        var genrePage by remember { mutableStateOf(1) }
        var genreHasMore by remember { mutableStateOf(false) }
        var isLoadingMore by remember { mutableStateOf(false) }

        // Persistent tag orders for My List and Search
        var orderedCategories by remember { mutableStateOf(getSavedCategoryOrder(this@AnimeBoxMainActivity)) }
        var orderedGenres by remember { mutableStateOf(getSavedGenreOrder(this@AnimeBoxMainActivity)) }

        // My List category filter state & Add to List Dialog state
        var selectedMyListCategory by remember { mutableStateOf("All") }
        var showAddToListDialogForAnime by remember { mutableStateOf<AnimeBrief?>(null) }

        // Downloads Management states
        val downloadManager = remember { AnimeDownloadManager.getInstance(this@AnimeBoxMainActivity) }
        var downloadedAnimeGroups by remember { mutableStateOf(downloadManager.getDownloadedAnimeGroups()) }
        var storageStats by remember { mutableStateOf(downloadManager.getStorageStats()) }
        var selectedDrilldownAnime by remember { mutableStateOf<DownloadedAnimeGroup?>(null) }

        val focusManager = LocalFocusManager.current
        val context = LocalContext.current

        // Helper standard genres
        val standardGenres = orderedGenres

        // Isolate & reload continue watching & library upon profile switch
        LaunchedEffect(currentProfileId) {
            val hMgr = WatchHistoryManager(this@AnimeBoxMainActivity)
            val lMgr = LibraryManager(this@AnimeBoxMainActivity)
            continueWatchingList = hMgr.getWatchHistory()
            libraryItemsList = lMgr.getLibraryItems()
        }

        var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        // Perform search logic inside activity
        val performSearch: (String, Boolean) -> Unit = { queryText, clearFocus ->
            searchJob?.cancel()
            if (clearFocus) {
                focusManager.clearFocus()
            }
            activeSearchGenre = "" // clear active genre
            if (queryText.isNotBlank()) {
                isSearchLoading = true
                searchJob = coroutineScope.launch {
                    if (!clearFocus) {
                        kotlinx.coroutines.delay(400L)
                    }
                    if (isKitsuMode) {
                        searchResults = com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.searchAnime(queryText)
                    } else {
                        val response = AniListClient.searchAnime(queryText)
                        if (response != null) {
                            searchResults = parseTrending(response) // Reuse parseTrending since schema matches
                        } else {
                            searchResults = emptyList()
                        }
                    }
                    isSearchLoading = false
                }
            } else {
                searchResults = emptyList()
                isSearchLoading = false
            }
        }

        // Filter search by genre (resets to page 1)
        val filterByGenre = { genreName: String ->
            isSearchLoading = true
            focusManager.clearFocus()
            searchQuery = "" // clear text query
            activeSearchGenre = genreName
            genrePage = 1
            genreHasMore = false
            coroutineScope.launch {
                if (isKitsuMode) {
                    val kitsuList = com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.getAnimeByGenre(genreName, 1)
                    searchResults = kitsuList
                    genreHasMore = kitsuList.size >= 20
                } else {
                    val response = AniListClient.getAnimeByGenre(genreName, 1)
                    if (response != null) {
                        searchResults = parseTrending(response)
                        genreHasMore = parseHasNextPage(response)
                    } else {
                        searchResults = emptyList()
                    }
                }
                isSearchLoading = false
            }
        }

        // Function to load Kitsu fallback home data
        val loadKitsuHomeData: () -> Unit = {
            isLoading = true
            showOutageBanner = false
            anilistErrorMessage = null
            com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.isKitsuModeActive = true
            coroutineScope.launch {
                try {
                    val trendingDeferred = async { com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.getTrendingAnime() }
                    val popularDeferred = async { com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.getPopularAnime() }
                    val recentDeferred = async { com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.getRecentlyAddedAnime() }
                    val thisSeasonDeferred = async { com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.getPopularThisSeasonAnime() }
                    val moviesDeferred = async { com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.getPopularMovies() }
                    val comingSoonDeferred = async { com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.getComingSoonAnime() }

                    val kitsuTrending = trendingDeferred.await()
                    val kitsuPopular = popularDeferred.await()

                    if (kitsuTrending.isNotEmpty()) {
                        trendingList = kitsuTrending.take(9)
                    }
                    if (kitsuPopular.isNotEmpty()) {
                        recommendedAnimes = kitsuPopular
                        if (trendingList.isEmpty()) {
                            trendingList = kitsuPopular.take(9)
                        }
                    }

                    // Spotlight in Kitsu mode: resolve TMDB backdrops and logos for fixed spotlight
                    val resolvedFixed = STATIC_FIXED_SPOTLIGHT_ANIMES.map { anime ->
                        async {
                            val logo = AniZipClient.getAnimeLogoUrl(anime.id).ifEmpty { anime.logoUrl }
                            val tmdbBackdrop = when (anime.id) {
                                SUMMER_TIME_RENDERING_ID -> SUMMER_TIME_RENDERING_BACKDROP
                                else -> AniZipClient.getTmdbBackdropUrl(anime.id).ifEmpty { anime.bannerUrl }
                            }
                            anime.copy(logoUrl = logo, bannerUrl = tmdbBackdrop)
                        }
                    }.awaitAll()
                    spotlightAnimes = resolvedFixed.ifEmpty { STATIC_FIXED_SPOTLIGHT_ANIMES }

                    // Populate all remaining home sections in background
                    recentlyAddedList = recentDeferred.await()
                    thisSeasonList = thisSeasonDeferred.await()
                    moviesListData = moviesDeferred.await()
                    comingSoonList = comingSoonDeferred.await()
                    suggestionList = (kitsuTrending + kitsuPopular).distinctBy { it.id }.filter { it.bannerUrl.isNotEmpty() }
                } catch (e: Exception) {
                    spotlightAnimes = STATIC_FIXED_SPOTLIGHT_ANIMES
                } finally {
                    isLoading = false
                }
            }
        }

        val loadHomeData: () -> Unit = {
            isLoading = true
            showOutageBanner = false
            anilistErrorMessage = null
            coroutineScope.launch {
                try {
                    val hasNetwork = isNetworkAvailable(this@AnimeBoxMainActivity)
                    isOffline = !hasNetwork

                    if (!hasNetwork) {
                        anilistErrorMessage = "You are currently offline. Connect to the internet to stream online."
                        showOutageBanner = true
                        spotlightAnimes = STATIC_FIXED_SPOTLIGHT_ANIMES.map { it.copy(bannerUrl = "") }
                        trendingList = emptyList()
                        recentlyAddedList = emptyList()
                        thisSeasonList = emptyList()
                        moviesListData = emptyList()
                        comingSoonList = emptyList()
                        suggestionList = emptyList()
                        return@launch
                    }

                    // PHASE 1: Parallel fetch of fixed spotlight data + trending + popular
                    val fixedDeferred = coroutineScope.async { AniListClient.getAnimesByIds(FIXED_SPOTLIGHT_IDS) }
                    val trendingDeferred = coroutineScope.async { AniListClient.getTrendingAnime() }
                    val popularDeferred = coroutineScope.async { AniListClient.getPopularAnime() }

                    val fixedResponse = fixedDeferred.await()
                    val trendingResponse = trendingDeferred.await()
                    val popularResponse = popularDeferred.await()

                    if (trendingResponse == null && fixedResponse == null) {
                        anilistErrorMessage = AniListClient.lastErrorMessage ?: "The AniList API has been temporarily disabled due to severe stability issues (HTTP 403)."
                        showOutageBanner = true
                        spotlightAnimes = STATIC_FIXED_SPOTLIGHT_ANIMES
                        trendingList = emptyList()
                        recentlyAddedList = emptyList()
                        thisSeasonList = emptyList()
                        moviesListData = emptyList()
                        comingSoonList = emptyList()
                        suggestionList = emptyList()

                        if (retryCount > 0) {
                            showKitsuFallbackPromptDialog = true
                        }

                        // Asynchronously resolve TMDB backdrops and logos from AniZip for the fixed spotlight anime
                        coroutineScope.launch {
                            try {
                                val resolvedFixed = STATIC_FIXED_SPOTLIGHT_ANIMES.map { anime ->
                                    coroutineScope.async {
                                        val logo = AniZipClient.getAnimeLogoUrl(anime.id).ifEmpty { anime.logoUrl }
                                        val tmdbBackdrop = when (anime.id) {
                                            SUMMER_TIME_RENDERING_ID -> SUMMER_TIME_RENDERING_BACKDROP
                                            else -> AniZipClient.getTmdbBackdropUrl(anime.id).ifEmpty { anime.bannerUrl }
                                        }
                                        anime.copy(logoUrl = logo, bannerUrl = tmdbBackdrop)
                                    }
                                }.awaitAll()
                                if (resolvedFixed.isNotEmpty()) {
                                    spotlightAnimes = resolvedFixed
                                }
                            } catch (_: Exception) {}
                        }
                        return@launch
                    } else {
                        showOutageBanner = false
                        isKitsuMode = false
                        com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.isKitsuModeActive = false
                        retryCount = 0
                    }

                    // Parse trending and show carousel immediately
                    val rawTrending = if (trendingResponse != null) parseTrending(trendingResponse) else emptyList()
                    trendingList = rawTrending.take(9)

                    // Popular for search recommendations
                    if (popularResponse != null) {
                        recommendedAnimes = parseTrending(popularResponse)
                    }

                    // Parse fixed anime, preserve the requested order
                    val fixedParsed = if (fixedResponse != null) parseTrending(fixedResponse) else emptyList()
                    val sortedFixed = FIXED_SPOTLIGHT_IDS.mapNotNull { id -> fixedParsed.find { it.id == id } }
                        .ifEmpty { STATIC_FIXED_SPOTLIGHT_ANIMES }

                    // Find candidates from top 10 trending that aren't already in the fixed list (and not Re:Zero)
                    val dynamicCandidates = mutableListOf<AnimeBrief>()
                    for (t in rawTrending) {
                        if (dynamicCandidates.size >= 3) break
                        if (FIXED_SPOTLIGHT_IDS.none { it == t.id } && 
                            !t.title.contains("Re:Zero", ignoreCase = true) && 
                            !t.title.contains("Re: ZERO", ignoreCase = true)
                        ) {
                            val tmdbBd = AniZipClient.getTmdbBackdropUrl(t.id)
                            val candidateBd = if (tmdbBd.isNotEmpty()) tmdbBd else t.bannerUrl
                            if (candidateBd.isNotEmpty()) {
                                dynamicCandidates.add(t.copy(bannerUrl = candidateBd))
                            }
                        }
                    }

                    // PHASE 2: Resolve logos + TMDB backdrops in parallel for all spotlight items
                    val allSpotlightRaw = sortedFixed + dynamicCandidates
                    val resolvedDefs = allSpotlightRaw.map { anime ->
                        coroutineScope.async {
                            val logo = AniZipClient.getAnimeLogoUrl(anime.id).ifEmpty { anime.logoUrl }
                            val tmdbBackdrop = when (anime.id) {
                                SUMMER_TIME_RENDERING_ID -> SUMMER_TIME_RENDERING_BACKDROP
                                else -> AniZipClient.getTmdbBackdropUrl(anime.id)
                                    .ifEmpty { anime.bannerUrl.ifEmpty { anime.coverUrl } }
                            }
                            anime.copy(logoUrl = logo, bannerUrl = tmdbBackdrop)
                        }
                    }
                    spotlightAnimes = resolvedDefs.awaitAll().ifEmpty { STATIC_FIXED_SPOTLIGHT_ANIMES }
                } catch (e: Exception) {
                    anilistErrorMessage = "Failed to load feed: ${e.localizedMessage ?: "Unknown error"}"
                    showOutageBanner = true
                    spotlightAnimes = STATIC_FIXED_SPOTLIGHT_ANIMES
                } finally {
                    isLoading = false
                }

                // PHASE 3: Load extra home sections in background (don't block UI)
                coroutineScope.launch {
                    try {
                        val resp = AniListClient.getRecentlyAdded()
                        if (resp != null) {
                            val parsed = parseTrending(resp)
                            val curYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                            recentlyAddedList = parsed.filter { it.releaseYear == curYear }
                        }
                    } catch (_: Exception) {}
                }
                coroutineScope.launch {
                    try {
                        val resp = AniListClient.getPopularThisSeason()
                        if (resp != null) thisSeasonList = parseTrending(resp)
                    } catch (_: Exception) {}
                }
                coroutineScope.launch {
                    try {
                        val resp = AniListClient.getPopularMovies()
                        if (resp != null) moviesListData = parseTrending(resp)
                    } catch (_: Exception) {}
                }
                coroutineScope.launch {
                    try {
                        val resp = AniListClient.getComingSoon()
                        if (resp != null) {
                            val list = parseTrending(resp, allowUpcoming = true)
                            comingSoonList = list
                            comingSoonIds = list.map { it.id }.toSet()
                        }
                    } catch (_: Exception) {}
                }
                coroutineScope.launch {
                    try {
                        val resp = AniListClient.getRandomAnimeAfter2000((1..80).random())
                        if (resp != null) {
                            val filteredList = parseTrending(resp)
                            if (filteredList.isNotEmpty()) {
                                val resolvedList = filteredList.map { anime ->
                                    coroutineScope.async {
                                        val backdrop = AniZipClient.getAniZipBackdropUrl(anime.id)
                                        val finalBackdrop = backdrop.ifEmpty { anime.bannerUrl }
                                        if (anime.averageScore > 72 && finalBackdrop.isNotEmpty() && !finalBackdrop.endsWith("large") && !finalBackdrop.contains("coverImage")) {
                                            anime.copy(bannerUrl = finalBackdrop)
                                        } else {
                                            null
                                        }
                                    }
                                }.awaitAll().filterNotNull()
                                
                                if (resolvedList.isNotEmpty()) {
                                    suggestionList = resolvedList.shuffled()
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // Initial Data Fetching
        LaunchedEffect(Unit) {
            loadHomeData()
        }

        // Refresh continueWatching and library when Main Activity Resumes
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    continueWatchingList = WatchHistoryManager(this@AnimeBoxMainActivity).getWatchHistory()
                    libraryItemsList = LibraryManager(this@AnimeBoxMainActivity).getLibraryItems()
                    downloadedAnimeGroups = downloadManager.getDownloadedAnimeGroups()
                    storageStats = downloadManager.getStorageStats()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        // Spotlight auto-cycling (changes every 6 seconds)
        if (spotlightAnimes.isNotEmpty()) {
            LaunchedEffect(spotlightAnimes) {
                while (true) {
                    kotlinx.coroutines.delay(6000)
                    spotlightIndex = (spotlightIndex + 1) % spotlightAnimes.size
                }
            }
        }

        // Suggestion auto-cycling (changes every 12 seconds)
        if (suggestionList.isNotEmpty()) {
            LaunchedEffect(suggestionList) {
                while (true) {
                    kotlinx.coroutines.delay(12000)
                    suggestionIndex = (suggestionIndex + 1) % suggestionList.size
                }
            }
        }

        val homeScrollState = rememberScrollState()

        Scaffold(
            bottomBar = {
                NetflixBottomNav(
                    selectedTab = selectedTab,
                    onTabSelected = { tabIndex ->
                        selectedTab = tabIndex
                    },
                    onMySpaceClick = {
                        val actProfId = ProfileManager.getActiveProfile(this@AnimeBoxMainActivity)
                        val actObj = ProfileManager.getProfiles(this@AnimeBoxMainActivity).find { it.id == actProfId }
                            ?: ProfileManager.getProfiles(this@AnimeBoxMainActivity).firstOrNull()
                        initialProfileForEditing = actObj
                        showProfileSelector = true
                    }
                )
            },
            containerColor = Color.Black
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingValues.calculateBottomPadding())
                    .background(Color.Black)
            ) {
                if (isLoading) {
                    AnimeBoxHomeSkeletonLoading()
                } else {
                    when (selectedTab) {
                        0 -> {
                            // HOME TAB
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(homeScrollState)
                            ) {
                                // 1. Netflix Spotlight Hero Section (top 3 from trending / fixed fallback)
                                if (spotlightAnimes.isNotEmpty()) {
                                    val safeIndex = if (spotlightIndex in spotlightAnimes.indices) spotlightIndex else 0
                                    val spotlight = spotlightAnimes[safeIndex]
                                    SpotlightSection(
                                        spotlight = spotlight,
                                        inLibrary = libraryItemsList.any { it.id == spotlight.id },
                                        onToggleLibrary = {
                                            if (isKitsuMode) {
                                                showKitsuMyListRestrictionDialog = true
                                            } else if (showOutageBanner || isOffline) {
                                                showServiceUnavailableDialog = true
                                            } else {
                                                val added = LibraryManager(this@AnimeBoxMainActivity).toggleLibraryItem(spotlight)
                                                libraryItemsList = LibraryManager(this@AnimeBoxMainActivity).getLibraryItems()
                                                Toast.makeText(context, if (added) "Added to My List" else "Removed from My List", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onPlayClick = {
                                            if (showOutageBanner || isOffline) {
                                                showServiceUnavailableDialog = true
                                            } else {
                                                openDetailsPage(spotlight.id, spotlight)
                                            }
                                        }
                                    )
                                }

                                // 2. Horizontal Rich 3-Poster Genre Cards Bar (Visible always, offline & online)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                                ) {
                                    items(STATIC_HOME_GENRES) { genreItem ->
                                        HomeGenreCard(
                                            genre = genreItem,
                                            onClick = {
                                                selectedTab = 1
                                                filterByGenre(genreItem.searchQuery)
                                            }
                                        )
                                    }
                                }

                                // 3. Continue Watching (isolated by profile) - Netflix Style
                                if (continueWatchingList.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Continue Watching",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(continueWatchingList) { item ->
                                            ContinueWatchingCard(
                                                item = item,
                                                onDeleteClick = {
                                                    val histManager = WatchHistoryManager(this@AnimeBoxMainActivity)
                                                    val list = histManager.getWatchHistory().toMutableList()
                                                    list.removeAll { it.anilistId == item.anilistId && it.episodeNumber == item.episodeNumber }
                                                    val prefs = getSharedPreferences("AnimeBoxHistory_${ProfileManager.getActiveProfile(this@AnimeBoxMainActivity)}", Context.MODE_PRIVATE)
                                                    prefs.edit().putString("history_list", list.toJson()).apply()
                                                    continueWatchingList = list
                                                },
                                                onInfoClick = {
                                                    openDetailsPage(item.anilistId, null)
                                                },
                                                onClick = { resolvedUrl ->
                                                    if (showOutageBanner || isOffline) {
                                                        showServiceUnavailableDialog = true
                                                    } else {
                                                        // Launch player directly, show resume dialog inside player
                                                        continueLaunchLoading = true
                                                        coroutineScope.launch {
                                                            fetchAndLaunchPlayerFromHistory(
                                                                anilistId = item.anilistId,
                                                                episodeNum = item.episodeNumber,
                                                                animeTitle = item.animeTitle,
                                                                coverUrl = resolvedUrl,
                                                                showCoverUrl = resolvedUrl,
                                                                totalEpisodes = 0 // will be filled from detail if needed
                                                            )
                                                            continueLaunchLoading = false
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                // AniList Outage / Offline In-App Alert Card (Located below Continue Watching / Spotlight)
                                if (showOutageBanner) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF282828))
                                            .padding(18.dp)
                                    ) {
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF383838)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isOffline) CustomWifiOffIcon else CustomCloudOffIcon,
                                                        contentDescription = null,
                                                        tint = primaryColor,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (isOffline) "You're Offline" else "AniList Temporarily Unavailable",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = if (isOffline) "No internet connection detected" else "Upstream servers unreachable",
                                                        color = Color(0xFF9E9E9E),
                                                        fontSize = 11.5.sp
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = if (isOffline) {
                                                    "You are currently offline. You can watch your downloaded anime episodes or retry connecting."
                                                } else {
                                                    anilistErrorMessage ?: "The AniList API has been temporarily disabled due to severe stability issues (HTTP 403)."
                                                },
                                                color = Color(0xFFC4C4C4),
                                                fontSize = 12.5.sp,
                                                lineHeight = 18.sp
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        retryCount++
                                                        loadHomeData()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(vertical = 10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = null,
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        "Retry",
                                                        color = Color.Black,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                                Button(
                                                    onClick = { selectedTab = 2 },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF383838)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(vertical = 10.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.netflix_download),
                                                        contentDescription = "Downloads",
                                                        tint = primaryColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        "Downloads",
                                                        color = primaryColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!showOutageBanner) {
                                    Spacer(modifier = Modifier.height(0.dp))

                                    // 4. Trending Now - Netflix Outline Numbers Section
                                    if (trendingList.isNotEmpty()) {
                                        SectionHeader(title = "Top 10 Trending Shows")
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(195.dp)
                                                .padding(bottom = 4.dp)
                                        ) {
                                            itemsIndexed(trendingList) { index, anime ->
                                                Box(
                                                    modifier = Modifier
                                                        .width(160.dp)
                                                        .fillMaxHeight()
                                                ) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        style = androidx.compose.ui.text.TextStyle(
                                                            fontSize = 120.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = Color(0xFF161616),
                                                            drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                                                                width = 8f,
                                                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                            )
                                                        ),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomStart)
                                                            .offset(x = (-4).dp, y = 20.dp)
                                                    )
                                                    Text(
                                                        text = "${index + 1}",
                                                        style = androidx.compose.ui.text.TextStyle(
                                                            fontSize = 120.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = Color.Black
                                                        ),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomStart)
                                                            .offset(x = (-4).dp, y = 20.dp)
                                                    )
                                                    Text(
                                                        text = "${index + 1}",
                                                        style = androidx.compose.ui.text.TextStyle(
                                                            fontSize = 120.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = Color.White.copy(alpha = 0.85f),
                                                            drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                                                                width = 2.5f,
                                                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                            )
                                                        ),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomStart)
                                                            .offset(x = (-4).dp, y = 20.dp)
                                                    )

                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .width(118.dp)
                                                            .height(174.dp)
                                                    ) {
                                                        PremiumAnimePosterCard(
                                                            anime = anime,
                                                            inLibrary = libraryItemsList.any { it.id == anime.id },
                                                            onToggleLibrary = {
                                                                if (isKitsuMode) {
                                                                    showKitsuMyListRestrictionDialog = true
                                                                } else {
                                                                    showAddToListDialogForAnime = anime
                                                                }
                                                            },
                                                            onClick = { openDetailsPage(anime.id, anime) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 5. Recently Added
                                    if (recentlyAddedList.isNotEmpty()) {
                                        SectionHeader(title = "Recently Added")
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        ) {
                                            items(recentlyAddedList) { anime ->
                                                PremiumAnimePosterCard(
                                                    anime = anime,
                                                    inLibrary = libraryItemsList.any { it.id == anime.id },
                                                    onToggleLibrary = {
                                                        if (isKitsuMode) {
                                                            showKitsuMyListRestrictionDialog = true
                                                        } else {
                                                            showAddToListDialogForAnime = anime
                                                        }
                                                    },
                                                    onClick = { openDetailsPage(anime.id, anime) }
                                                )
                                            }
                                        }
                                    }

                                    // 5b. Suggested / Random Show Spotlight
                                    if (suggestionList.isNotEmpty()) {
                                        val curSug = suggestionList[suggestionIndex]
                                        SuggestionCard(
                                            anime = curSug,
                                            backdropUrl = curSug.bannerUrl,
                                            isAniZipBackdrop = true,
                                            inLibrary = libraryItemsList.any { it.id == curSug.id },
                                            onToggleLibrary = {
                                                if (isKitsuMode) {
                                                    showKitsuMyListRestrictionDialog = true
                                                } else {
                                                    val added = LibraryManager(this@AnimeBoxMainActivity).toggleLibraryItem(curSug)
                                                    libraryItemsList = LibraryManager(this@AnimeBoxMainActivity).getLibraryItems()
                                                    Toast.makeText(context, if (added) "Added to My List" else "Removed from My List", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onClick = { openDetailsPage(curSug.id, curSug) }
                                        )
                                    }

                                    // 6. Popular This Season
                                    if (thisSeasonList.isNotEmpty()) {
                                        SectionHeader(title = "Popular This Season")
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        ) {
                                            items(thisSeasonList) { anime ->
                                                PremiumAnimePosterCard(
                                                    anime = anime,
                                                    inLibrary = libraryItemsList.any { it.id == anime.id },
                                                    onToggleLibrary = {
                                                        if (isKitsuMode) {
                                                            showKitsuMyListRestrictionDialog = true
                                                        } else {
                                                            showAddToListDialogForAnime = anime
                                                        }
                                                    },
                                                    onClick = { openDetailsPage(anime.id, anime) }
                                                )
                                            }
                                        }
                                    }

                                    // 7. Popular Movies
                                    if (moviesListData.isNotEmpty()) {
                                        SectionHeader(title = "Popular Movies")
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        ) {
                                            items(moviesListData) { anime ->
                                                PremiumAnimePosterCard(
                                                    anime = anime,
                                                    inLibrary = libraryItemsList.any { it.id == anime.id },
                                                    onToggleLibrary = {
                                                        if (isKitsuMode) {
                                                            showKitsuMyListRestrictionDialog = true
                                                        } else {
                                                            showAddToListDialogForAnime = anime
                                                        }
                                                    },
                                                    onClick = { openDetailsPage(anime.id, anime) }
                                                )
                                            }
                                        }
                                    }

                                    // 8. Coming Soon
                                    if (comingSoonList.isNotEmpty()) {
                                        SectionHeader(title = "Coming Soon")
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        ) {
                                            items(comingSoonList) { anime ->
                                                PremiumAnimePosterCard(
                                                    anime = anime,
                                                    inLibrary = libraryItemsList.any { it.id == anime.id },
                                                    onToggleLibrary = { /* Can't add coming soon to list */ },
                                                    onClick = { comingSoonAlertAnime = anime }
                                                )
                                            }
                                        }
                                    }

                                    FaqSection()
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        1 -> {
                            // SEARCH TAB (Redesigned with Erased poster subtle translucent backdrop, pill search, recent searches, live suggestions, and 3-column full vertical poster cards)
                            val searchHistKey = remember { "user_search_history_${ProfileManager.getActiveProfile(context)}" }
                            var recentSearchesList by remember {
                                mutableStateOf(
                                    context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE)
                                        .getStringSet(searchHistKey, emptySet())?.toList() ?: emptyList()
                                )
                            }

                            val saveRecentQuery: (String) -> Unit = { q ->
                                if (q.isNotBlank()) {
                                    val updated = (listOf(q.trim()) + recentSearchesList.filterNot { it.equals(q.trim(), ignoreCase = true) }).take(3)
                                    recentSearchesList = updated
                                    context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE)
                                        .edit().putStringSet(searchHistKey, updated.toSet()).apply()
                                }
                            }

                            val removeRecentQuery: (String) -> Unit = { q ->
                                val updated = recentSearchesList.filterNot { it.equals(q, ignoreCase = true) }
                                recentSearchesList = updated
                                context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE)
                                    .edit().putStringSet(searchHistKey, updated.toSet()).apply()
                            }

                            val clearAllRecentQueries: () -> Unit = {
                                recentSearchesList = emptyList()
                                context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE)
                                    .edit().remove(searchHistKey).apply()
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    // Search Bar (Netflix Style #222222 with Search Icon and Clear Button)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 10.dp)
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF222222))
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = CustomSearchIcon,
                                                contentDescription = "Search",
                                                tint = Color(0xFF9E9EA4),
                                                modifier = Modifier.size(20.dp)
                                            )

                                            Spacer(modifier = Modifier.width(10.dp))

                                            BasicTextField(
                                                value = searchQuery,
                                                onValueChange = { 
                                                    searchQuery = it
                                                    if (it.isNotBlank()) {
                                                        performSearch(it, false)
                                                    } else {
                                                        searchResults = emptyList()
                                                    }
                                                },
                                                singleLine = true,
                                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                                ),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                                keyboardActions = KeyboardActions(onSearch = { 
                                                    saveRecentQuery(searchQuery)
                                                    performSearch(searchQuery, true)
                                                }),
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
                                                                text = "Search shows, movies, genres...",
                                                                color = Color(0xFF8E8E93),
                                                                fontSize = 15.sp,
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
                                                    onClick = { 
                                                        searchQuery = ""
                                                        searchResults = emptyList()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Clear",
                                                        tint = Color(0xFF8E8E93),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Genre Tags Horizontal Row (Chips directly below search bar)
                                    ReorderableDragTagRow(
                                        tags = if (isKitsuMode) com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.KITSU_GENRES else orderedGenres,
                                        selectedTag = activeSearchGenre,
                                        onTagSelected = { genre ->
                                            if (activeSearchGenre == genre) {
                                                activeSearchGenre = ""
                                                searchResults = emptyList()
                                            } else {
                                                filterByGenre(genre)
                                            }
                                        },
                                        onOrderChanged = { newOrder ->
                                            if (!isKitsuMode) {
                                                orderedGenres = newOrder
                                                saveGenreOrder(this@AnimeBoxMainActivity, newOrder)
                                            }
                                        },
                                        isCategoryStyle = false,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    if (isSearchLoading) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = primaryColor)
                                        }
                                    } else {
                                        val isShowingRecs = searchQuery.isEmpty() && activeSearchGenre.isEmpty()
                                        val blockedInitialTopResultIds = setOf(16498, 113415, 101922) // Attack on Titan, Jujutsu Kaisen, Demon Slayer Season 1
                                        val displayList = if (isShowingRecs) {
                                            val nonBlocked = recommendedAnimes.filter { it.id !in blockedInitialTopResultIds }
                                            val blocked = recommendedAnimes.filter { it.id in blockedInitialTopResultIds }
                                            nonBlocked + blocked
                                        } else {
                                            searchResults
                                        }

                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .padding(bottom = 6.dp)
                                        ) {
                                            // 1. RECENT SEARCHES (Shown when query is empty and activeSearchGenre is empty)
                                            if (searchQuery.isEmpty() && activeSearchGenre.isEmpty() && recentSearchesList.isNotEmpty()) {
                                                item(span = { GridItemSpan(3) }) {
                                                    Column {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(top = 4.dp, bottom = 6.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "Recent Searches",
                                                                color = Color.White,
                                                                fontSize = 16.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = "Clear All",
                                                                color = primaryColor,
                                                                fontSize = 13.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .clickable { clearAllRecentQueries() }
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }

                                                        recentSearchesList.take(3).forEach { recentText ->
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable {
                                                                        searchQuery = recentText
                                                                        saveRecentQuery(recentText)
                                                                        performSearch(recentText, true)
                                                                    }
                                                                    .padding(vertical = 8.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = recentText,
                                                                    color = Color(0xFFDDDDDD),
                                                                    fontSize = 15.sp,
                                                                    fontWeight = FontWeight.Medium
                                                                )
                                                                Icon(
                                                                    imageVector = Icons.Default.Close,
                                                                    contentDescription = "Remove",
                                                                    tint = Color(0xFF8E8E93),
                                                                    modifier = Modifier
                                                                        .size(20.dp)
                                                                        .clickable { removeRecentQuery(recentText) }
                                                                )
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(10.dp))
                                                    }
                                                }
                                            }

                                            // 2. LIVE QUERY SUGGESTIONS TYPEAHEAD (When user is typing - max 4 suggestions)
                                            if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                                                val suggestions = (listOf(searchQuery) + searchResults.map { it.title }).distinct().take(4)
                                                item(span = { GridItemSpan(3) }) {
                                                    Column {
                                                        suggestions.forEach { suggestionText ->
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable {
                                                                        searchQuery = suggestionText
                                                                        saveRecentQuery(suggestionText)
                                                                        performSearch(suggestionText, true)
                                                                    }
                                                                    .padding(vertical = 8.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    imageVector = CustomSearchIcon,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFF8E8E93),
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(12.dp))
                                                                Text(
                                                                    text = suggestionText,
                                                                    color = Color.White,
                                                                    fontSize = 15.sp,
                                                                    fontWeight = FontWeight.Medium,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                    }
                                                }
                                            }

                                            // 3. SEARCH RESULTS & TOP RESULT BACKDROP BANNER (Netflix Style Matching Screenshots)
                                            if (displayList.isNotEmpty()) {
                                                val topResult = displayList.first()
                                                val moreLikeThis = displayList.drop(1).take(6)
                                                val moreResults = displayList.drop(7)

                                                // Top Result 16:9 Backdrop Banner Card (AniList Backdrop Only)
                                                item(span = { GridItemSpan(3) }) {
                                                    val bannerImg = topResult.bannerUrl.ifEmpty { topResult.coverUrl }

                                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                                                        Text(
                                                            text = "TOP RESULT",
                                                            color = Color.White,
                                                            fontSize = 13.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            letterSpacing = 0.5.sp,
                                                            modifier = Modifier.padding(bottom = 10.dp)
                                                        )

                                                        // 16:9 Backdrop Image
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(185.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(Color(0xFF18181C))
                                                                .clickable { openDetailsPage(topResult.id, topResult) }
                                                        ) {
                                                            Image(
                                                                painter = rememberAsyncImagePainter(model = bannerImg),
                                                                contentDescription = topResult.title,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        }

                                                        Spacer(modifier = Modifier.height(12.dp))

                                                        // Title
                                                        Text(
                                                            text = topResult.title,
                                                            color = Color.White,
                                                            fontSize = 20.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )

                                                        Spacer(modifier = Modifier.height(10.dp))

                                                        // Action Buttons Row: [ ▶ Watch Now ] and [ + ]
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Button(
                                                                onClick = { openDetailsPage(topResult.id, topResult) },
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                                                shape = RoundedCornerShape(4.dp),
                                                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                                                modifier = Modifier.height(38.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.PlayArrow,
                                                                    contentDescription = null,
                                                                    tint = Color.Black,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text(
                                                                    text = "Watch Now",
                                                                    color = Color.Black,
                                                                    fontSize = 13.5.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }

                                                            Spacer(modifier = Modifier.width(10.dp))

                                                            val inLib = libraryItemsList.any { it.id == topResult.id }
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(38.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Color(0xFF262626))
                                                                    .clickable {
                                                                        if (showOutageBanner || isOffline) {
                                                                            showServiceUnavailableDialog = true
                                                                        } else {
                                                                            showAddToListDialogForAnime = topResult
                                                                        }
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (inLib) Icons.Default.Check else Icons.Default.Add,
                                                                    contentDescription = "My List",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(10.dp))

                                                        // Metadata line: e.g. "1999 • U/A 16+ • 1 Season • Anime"
                                                        val metaParts = mutableListOf<String>()
                                                        if (topResult.releaseYear != null && topResult.releaseYear > 0) {
                                                            metaParts.add("${topResult.releaseYear}")
                                                        }
                                                        metaParts.add("U/A 16+")
                                                        if (topResult.episodes > 0) {
                                                            metaParts.add("${topResult.episodes} Episodes")
                                                        } else {
                                                            metaParts.add("1 Season")
                                                        }
                                                        if (topResult.genres.isNotEmpty()) {
                                                            metaParts.add(topResult.genres.first())
                                                        } else {
                                                            metaParts.add("Anime")
                                                        }

                                                        Text(
                                                            text = metaParts.joinToString(" • "),
                                                            color = Color(0xFF9E9EA4),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )

                                                        // Description summary
                                                        if (topResult.description.isNotEmpty()) {
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            val cleanDesc = topResult.description
                                                                .replace(Regex("<[^>]*>"), "")
                                                                .replace("\n", " ")
                                                                .trim()
                                                            Text(
                                                                text = cleanDesc,
                                                                color = Color(0xFFCCCCCC),
                                                                fontSize = 12.sp,
                                                                lineHeight = 16.5.sp,
                                                                maxLines = 3,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }

                                                // 4. MORE LIKE THIS (Next items)
                                                if (moreLikeThis.isNotEmpty()) {
                                                    item(span = { GridItemSpan(3) }) {
                                                        Text(
                                                            text = "MORE LIKE THIS",
                                                            color = Color.White,
                                                            fontSize = 13.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            letterSpacing = 0.5.sp,
                                                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                                        )
                                                    }

                                                    items(moreLikeThis) { anime ->
                                                        PremiumAnimePosterCard(
                                                            anime = anime,
                                                            inLibrary = libraryItemsList.any { it.id == anime.id },
                                                            onToggleLibrary = {
                                                                if (showOutageBanner || isOffline) {
                                                                    showServiceUnavailableDialog = true
                                                                } else {
                                                                    showAddToListDialogForAnime = anime
                                                                }
                                                            },
                                                            onClick = {
                                                                openDetailsPage(anime.id, anime)
                                                            }
                                                        )
                                                    }
                                                }

                                                // 5. MORE RESULTS (Remaining items)
                                                if (moreResults.isNotEmpty()) {
                                                    item(span = { GridItemSpan(3) }) {
                                                        Text(
                                                            text = "MORE RESULTS",
                                                            color = Color.White,
                                                            fontSize = 13.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            letterSpacing = 0.5.sp,
                                                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                                        )
                                                    }

                                                    items(moreResults) { anime ->
                                                        PremiumAnimePosterCard(
                                                            anime = anime,
                                                            inLibrary = libraryItemsList.any { it.id == anime.id },
                                                            onToggleLibrary = {
                                                                if (showOutageBanner || isOffline) {
                                                                    showServiceUnavailableDialog = true
                                                                } else {
                                                                    showAddToListDialogForAnime = anime
                                                                }
                                                            },
                                                            onClick = {
                                                                openDetailsPage(anime.id, anime)
                                                            }
                                                        )
                                                    }
                                                }

                                                // Load More button (for genre searches with more pages)
                                                if (!isShowingRecs && activeSearchGenre.isNotEmpty() && genreHasMore) {
                                                    item(span = { GridItemSpan(3) }) {
                                                        Box(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isLoadingMore) {
                                                                CircularProgressIndicator(color = primaryColor, modifier = Modifier.size(28.dp))
                                                            } else {
                                                                OutlinedButton(
                                                                    onClick = {
                                                                        isLoadingMore = true
                                                                        coroutineScope.launch {
                                                                            val nextPage = genrePage + 1
                                                                            if (isKitsuMode) {
                                                                                val kitsuList = com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.getAnimeByGenre(activeSearchGenre, nextPage)
                                                                                if (kitsuList.isNotEmpty()) {
                                                                                    searchResults = searchResults + kitsuList
                                                                                    genrePage = nextPage
                                                                                    genreHasMore = kitsuList.size >= 20
                                                                                } else {
                                                                                    genreHasMore = false
                                                                                }
                                                                            } else {
                                                                                val resp = AniListClient.getAnimeByGenre(activeSearchGenre, nextPage)
                                                                                if (resp != null) {
                                                                                    searchResults = searchResults + parseTrending(resp)
                                                                                    genrePage = nextPage
                                                                                    genreHasMore = parseHasNextPage(resp)
                                                                                }
                                                                            }
                                                                            isLoadingMore = false
                                                                        }
                                                                    },
                                                                    border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor),
                                                                    shape = RoundedCornerShape(8.dp)
                                                                ) {
                                                                    Text("Load More", color = primaryColor, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else if (!isShowingRecs && !isSearchLoading) {
                                                item(span = { GridItemSpan(3) }) {
                                                    Box(
                                                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "No results found for \"$searchQuery\"",
                                                            color = Color.Gray,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            // DOWNLOADS TAB (Netflix-Style Series Cards & Offline Playback Manager)
                            val allEpisodes by AnimeDownloadManager.episodesState.collectAsState()
                            var downloadSearchQuery by remember { mutableStateOf("") }
                            val dQuery = downloadSearchQuery.trim().lowercase()

                            val activeDownloads = remember(allEpisodes, dQuery) {
                                allEpisodes.filter { (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING) &&
                                    (dQuery.isEmpty() || it.animeTitle.lowercase().contains(dQuery) || it.episodeTitle.lowercase().contains(dQuery)) }
                            }
                            val completedVideos = remember(allEpisodes, dQuery) {
                                allEpisodes.filter { it.status == DownloadStatus.COMPLETED &&
                                    (dQuery.isEmpty() || it.animeTitle.lowercase().contains(dQuery) || it.episodeTitle.lowercase().contains(dQuery)) }
                                    .sortedByDescending { it.downloadTimestamp }
                            }

                            val downloadedGroups = remember(completedVideos) {
                                completedVideos.groupBy { it.anilistId }.map { (id, epList) ->
                                    val first = epList.first()
                                    val imagesDir = File(downloadManager.baseDir, "images")
                                    val coversDir = File(downloadManager.baseDir, "covers")
                                    val localBg = File(imagesDir, "backdrop_${id}.jpg")
                                    val localLogo = File(imagesDir, "logo_${id}.png")
                                    val localShowCover = File(coversDir, "show_cover_${id}.jpg")

                                    val bgPath = when {
                                        localBg.exists() && localBg.length() > 0 -> localBg.absolutePath
                                        first.localBackdropPath.isNotEmpty() -> first.localBackdropPath
                                        else -> ""
                                    }
                                    val logoPath = when {
                                        localLogo.exists() && localLogo.length() > 0 -> localLogo.absolutePath
                                        first.localLogoPath.isNotEmpty() -> first.localLogoPath
                                        else -> ""
                                    }
                                    val coverPath = when {
                                        localShowCover.exists() && localShowCover.length() > 0 -> localShowCover.absolutePath
                                        first.showCoverUrl.isNotEmpty() -> first.showCoverUrl
                                        else -> first.coverUrl
                                    }
                                    DownloadedAnimeGroup(
                                        anilistId = id,
                                        animeTitle = first.animeTitle,
                                        coverUrl = coverPath,
                                        backdropUrl = bgPath,
                                        logoUrl = logoPath,
                                        episodes = epList.sortedBy { it.episodeNumber },
                                        totalSizeBytes = epList.sumOf { it.fileSizeBytes }
                                    )
                                }
                            }

                            var selectedSeriesDrilldown by remember { mutableStateOf<DownloadedAnimeGroup?>(null) }
                            var selectedDownloadFilter by remember { mutableStateOf("All") }
                            var showDeleteAllDownloadsDialog by remember { mutableStateOf(false) }
                            var groupToDelete by remember { mutableStateOf<DownloadedAnimeGroup?>(null) }
                            var episodeToDelete by remember { mutableStateOf<DownloadedEpisode?>(null) }

                            fun formatDownloadedBytes(bytes: Long): String {
                                val mb = bytes / (1024.0 * 1024.0)
                                return if (mb >= 1024.0) {
                                    String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0)
                                } else {
                                    String.format(java.util.Locale.US, "%.0f MB", mb)
                                }
                            }

                            val playEpisode: (DownloadedEpisode) -> Unit = { ep ->
                                if (ep.localFilePath.isNotEmpty()) {
                                    val playIntent = Intent(this@AnimeBoxMainActivity, AnimeBoxPlayerActivity::class.java).apply {
                                        putExtra("localFilePath", ep.localFilePath)
                                        putExtra("isOffline", true)
                                        putExtra("animeTitle", ep.animeTitle)
                                        putExtra("anilistId", ep.anilistId)
                                        putExtra("episode", ep.episodeNumber)
                                        putExtra("showCoverUrl", ep.coverUrl)
                                        putExtra("coverUrl", ep.coverUrl)
                                        putExtra("subtitleUrl", ep.localSubtitlePath)
                                        putExtra("subtitlesJson", ep.subtitlesJson)
                                        putExtra("introStart", ep.introStart)
                                        putExtra("introEnd", ep.introEnd)
                                        putExtra("outroStart", ep.outroStart)
                                        putExtra("outroEnd", ep.outroEnd)
                                        putExtra("streamType", ep.streamType)
                                    }
                                    startActivity(playIntent)
                                }
                            }

                            // Keep active drilldown synced with downloads state
                            val currentDrilldown = selectedSeriesDrilldown?.let { drill ->
                                downloadedGroups.find { it.anilistId == drill.anilistId }
                            }

                            // Keep storage stats updated
                            LaunchedEffect(allEpisodes.size, selectedTab) {
                                storageStats = downloadManager.getStorageStats()
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                                    .padding(horizontal = 16.dp)
                            ) {
                                if (currentDrilldown != null) {
                                    // ─── SERIES DRILLDOWN VIEW (Netflix Style) ───────────────────────────
                                    val group = currentDrilldown
                                    var drillBackdropUrl by remember(group.anilistId) { mutableStateOf(group.backdropUrl) }
                                    var drillLogoUrl by remember(group.anilistId) { mutableStateOf(group.logoUrl) }

                                    LaunchedEffect(group.anilistId) {
                                        if (drillBackdropUrl.isEmpty()) {
                                            val bg = AniZipClient.getAnimeBackdropUrl(group.anilistId)
                                            if (bg.isNotEmpty()) drillBackdropUrl = bg
                                        }
                                        if (drillLogoUrl.isEmpty()) {
                                            val logo = AniZipClient.getAnimeLogoUrl(group.anilistId)
                                            if (logo.isNotEmpty()) drillLogoUrl = logo
                                        }
                                    }

                                    val heroImage = drillBackdropUrl.ifEmpty { group.backdropUrl.ifEmpty { group.coverUrl } }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .statusBarsPadding()
                                            .padding(top = 8.dp, bottom = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            IconButton(
                                                onClick = { selectedSeriesDrilldown = null },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                                    contentDescription = "Back",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = group.animeTitle,
                                                    color = Color.White,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${group.episodes.size} Episodes • ${formatDownloadedBytes(group.totalSizeBytes)}",
                                                    color = Color(0xFF8E8E93),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { groupToDelete = group },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Series",
                                                tint = Color(0xFFFF5252),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Series Header 16:9 Hero Banner
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF161616))
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(model = resolveImageModel(heroImage)),
                                            contentDescription = group.animeTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.Black.copy(alpha = 0.25f),
                                                            Color.Black.copy(alpha = 0.85f)
                                                        )
                                                    )
                                                )
                                        )

                                        // FireFly App Logo top left
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(start = 8.dp, top = 8.dp)
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.firefly_logo),
                                                contentDescription = "FireFly",
                                                modifier = Modifier
                                                    .height(22.dp)
                                                    .wrapContentWidth(),
                                                contentScale = ContentScale.Fit
                                            )
                                        }

                                        // Bottom content with Logo or Title & Play Button
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                if (drillLogoUrl.isNotEmpty()) {
                                                    Image(
                                                        painter = rememberAsyncImagePainter(model = resolveImageModel(drillLogoUrl)),
                                                        contentDescription = "Logo",
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier
                                                            .height(34.dp)
                                                            .fillMaxWidth(0.70f),
                                                        alignment = Alignment.CenterStart
                                                    )
                                                } else {
                                                    Text(
                                                        text = group.animeTitle,
                                                        color = Color.White,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${group.episodes.size} Episodes downloaded • Ready offline",
                                                    color = Color(0xFFB0B0B8),
                                                    fontSize = 11.5.sp
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    group.episodes.firstOrNull()?.let { playEpisode(it) }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Play",
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Episode List (Screenshot 2 Style)
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        items(group.episodes) { ep ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { playEpisode(ep) }
                                                    .padding(vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // 16:9 Thumbnail with Center Circular Play Button Overlay
                                                Box(
                                                    modifier = Modifier
                                                        .width(128.dp)
                                                        .height(72.dp)
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(Color(0xFF1E1E1E)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Image(
                                                        painter = rememberAsyncImagePainter(model = resolveImageModel(ep.coverUrl)),
                                                        contentDescription = ep.animeTitle,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.35f))
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                                            .border(1.2.dp, Color.White.copy(alpha = 0.90f), CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = "Play",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(19.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(14.dp))

                                                // Episode Number. Title & Duration/Size
                                                Column(modifier = Modifier.weight(1f)) {
                                                    val epNumTitle = if (ep.episodeTitle.isNotEmpty() && !ep.episodeTitle.equals("Episode ${ep.episodeNumber}", ignoreCase = true)) {
                                                        "${ep.episodeNumber}. ${ep.episodeTitle}"
                                                    } else {
                                                        "${ep.episodeNumber}. Episode ${ep.episodeNumber}"
                                                    }
                                                    Text(
                                                        text = epNumTitle,
                                                        color = Color.White,
                                                        fontSize = 14.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        lineHeight = 18.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Text(
                                                        text = "${ep.quality.ifEmpty { "HD" }} | ${formatDownloadedBytes(ep.fileSizeBytes)}",
                                                        color = Color(0xFF8E8E93),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Normal
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                // Right: Phone with Checkmark Icon in WHITE
                                                NetflixDownloadedPhoneIcon(
                                                    modifier = Modifier.size(24.dp),
                                                    tint = Color.White
                                                )

                                                Spacer(modifier = Modifier.width(4.dp))

                                                IconButton(
                                                    onClick = { episodeToDelete = ep },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete",
                                                        tint = Color(0xFF7E7E88),
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // ─── MAIN DOWNLOADS SCREEN (Monochrome Netflix Style) ─────────
                                    // 1. Header Title: "Downloads" + Shows count badge
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .statusBarsPadding()
                                            .padding(top = 10.dp, bottom = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Downloads",
                                            color = Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (downloadedGroups.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF1E1E1E))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "${downloadedGroups.size} ${if (downloadedGroups.size == 1) "Show" else "Shows"}",
                                                    color = Color(0xFFE2E8F0),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    // 2. DEVICE STORAGE SECTION (PLACED ON TOP OF SEARCH!)
                                    val total = if (storageStats.totalGb.isNaN() || storageStats.totalGb <= 0.0) 128.0 else storageStats.totalGb
                                    val used = if (storageStats.usedGb.isNaN() || storageStats.usedGb < 0.0) 45.0 else storageStats.usedGb
                                    val app = if (storageStats.appGb.isNaN() || storageStats.appGb < 0.0) 1.2 else storageStats.appGb
                                    val rawUsedFrac = (used / total).toFloat()
                                    val rawAppFrac = (app / total).toFloat()
                                    val usedFrac = if (rawUsedFrac.isNaN() || rawUsedFrac <= 0f) 0.35f else rawUsedFrac.coerceIn(0.05f, 0.75f)
                                    val appFrac = if (rawAppFrac.isNaN() || rawAppFrac <= 0f) 0.05f else rawAppFrac.coerceIn(0.02f, 0.20f)
                                    val rawFreeFrac = 1f - usedFrac - appFrac
                                    val freeFrac = if (rawFreeFrac.isNaN() || rawFreeFrac <= 0f) 0.60f else rawFreeFrac.coerceIn(0.05f, 0.90f)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF181818))
                                            .padding(14.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Device Storage",
                                                    color = Color.White,
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (allEpisodes.isNotEmpty()) {
                                                    Text(
                                                        text = "Delete All",
                                                        color = Color(0xFFFF5252),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .clickable { showDeleteAllDownloadsDialog = true }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            // 3-Segment Storage Bar: Thicker (12dp) with smooth rounded corners
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(12.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF2A2A2A))
                                            ) {
                                                Box(modifier = Modifier.fillMaxHeight().weight(usedFrac).background(Color.White))
                                                Box(modifier = Modifier.fillMaxHeight().weight(appFrac).background(Color(0xFF9E9EA4)))
                                                Box(modifier = Modifier.fillMaxHeight().weight(freeFrac).background(Color(0xFF33333A)))
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            // Storage Legend
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(modifier = Modifier.size(7.dp).background(Color.White, RoundedCornerShape(2.dp)))
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text("Used • ${String.format(java.util.Locale.US, "%.0f GB", used)}", color = Color(0xFFE2E8F0), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(modifier = Modifier.size(7.dp).background(Color(0xFF9E9EA4), RoundedCornerShape(2.dp)))
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    val appMb = app * 1024.0
                                                    val appStr = if (appMb >= 1024.0) String.format(java.util.Locale.US, "%.1f GB", app) else String.format(java.util.Locale.US, "%.0f MB", appMb)
                                                    Text("FireFly • $appStr", color = Color(0xFFE2E8F0), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(modifier = Modifier.size(7.dp).background(Color(0xFF6B7280), RoundedCornerShape(2.dp)))
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    val freeGb = (total - used - app).coerceAtLeast(0.0)
                                                    Text("Free • ${String.format(java.util.Locale.US, "%.0f GB", freeGb)}", color = Color(0xFFE2E8F0), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        }
                                    }

                                    // 3. SEARCH BAR (PLACED BELOW DEVICE STORAGE)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1E1E1E))
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = CustomSearchIcon,
                                                contentDescription = "Search",
                                                tint = Color(0xFF8E8E93),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            BasicTextField(
                                                value = downloadSearchQuery,
                                                onValueChange = { downloadSearchQuery = it },
                                                singleLine = true,
                                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 14.sp,
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
                                                        if (downloadSearchQuery.isEmpty()) {
                                                            Text(
                                                                text = "Search downloaded anime...",
                                                                color = Color(0xFF7E7E88),
                                                                fontSize = 14.sp
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                }
                                            )
                                            if (downloadSearchQuery.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { downloadSearchQuery = "" },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Clear",
                                                        tint = Color(0xFF8E8E93),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Category / Filter Tags (Monochrome)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("All", "Series (${downloadedGroups.size})").forEach { tag ->
                                            val isSelected = selectedDownloadFilter == tag.substringBefore(" ")
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSelected) Color.White else Color(0xFF1E1E24))
                                                    .clickable { selectedDownloadFilter = tag.substringBefore(" ") }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = tag,
                                                    color = if (isSelected) Color.Black else Color(0xFFB0B0BC),
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        }
                                        if (activeDownloads.isNotEmpty()) {
                                            val isSelected = selectedDownloadFilter == "Downloading"
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSelected) Color.White else Color(0xFF1E1E24))
                                                    .clickable { selectedDownloadFilter = "Downloading" }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "Downloading (${activeDownloads.size})",
                                                    color = if (isSelected) Color.Black else Color.White,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Downloads Content List / Empty State
                                    if (activeDownloads.isEmpty() && completedVideos.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(24.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(72.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF181818)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = CloudDownloadIcon,
                                                        contentDescription = "Downloads",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(34.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = if (downloadSearchQuery.isNotEmpty()) "No Downloads Found" else "Never be without anime",
                                                    fontSize = 19.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = if (downloadSearchQuery.isNotEmpty()) "Try searching with a different title." else "Download full shows and movies to watch offline on the go, anytime, anywhere.",
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF9E9EA4),
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    lineHeight = 18.sp,
                                                    modifier = Modifier.fillMaxWidth(0.85f)
                                                )
                                                if (downloadSearchQuery.isEmpty()) {
                                                    Spacer(modifier = Modifier.height(20.dp))
                                                    Button(
                                                        onClick = { selectedTab = 0 },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                                        shape = RoundedCornerShape(6.dp),
                                                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
                                                    ) {
                                                        Text(
                                                            text = "Explore What to Download",
                                                            fontSize = 13.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(bottom = 24.dp)
                                        ) {
                                            // Active in-progress downloads
                                            if (activeDownloads.isNotEmpty() && selectedDownloadFilter != "Series") {
                                                item {
                                                    Text(
                                                        text = "Active Downloads (${activeDownloads.size})",
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                                    )
                                                }
                                                items(activeDownloads) { downloadingEp ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFF141414))
                                                            .padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .width(128.dp)
                                                                .height(72.dp)
                                                                .clip(RoundedCornerShape(5.dp))
                                                                .background(Color(0xFF1E1E1E)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (downloadingEp.coverUrl.isNotEmpty()) {
                                                                Image(
                                                                    painter = rememberAsyncImagePainter(model = resolveImageModel(downloadingEp.coverUrl)),
                                                                    contentDescription = downloadingEp.animeTitle,
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .background(Color.Black.copy(alpha = 0.55f))
                                                                )
                                                            }
                                                            CircularProgressIndicator(
                                                                progress = (downloadingEp.downloadProgress / 100f).coerceIn(0f, 1f),
                                                                color = Color.White,
                                                                strokeWidth = 2.5.dp,
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = downloadingEp.animeTitle,
                                                                color = Color.White,
                                                                fontSize = 15.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = "Episode ${downloadingEp.episodeNumber} • ${downloadingEp.downloadProgress}% • ${downloadingEp.downloadSpeedText}",
                                                                color = Color(0xFF9E9EA4),
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                coroutineScope.launch {
                                                                    downloadManager.cancelDownload(downloadingEp.anilistId, downloadingEp.episodeNumber)
                                                                }
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Cancel",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // DOWNLOADED ANIME / MOVIE CARDS (16:9 Backdrop + Show Logo + White Phone Icon)
                                            if (downloadedGroups.isNotEmpty() && selectedDownloadFilter != "Downloading") {
                                                items(downloadedGroups) { group ->
                                                    val isSingleVideo = group.episodes.size == 1
                                                    var backdropUrl by remember(group.anilistId) { mutableStateOf(group.backdropUrl) }
                                                    var logoUrl by remember(group.anilistId) { mutableStateOf(group.logoUrl) }

                                                    LaunchedEffect(group.anilistId) {
                                                        if (backdropUrl.isEmpty()) {
                                                            val bg = AniZipClient.getAnimeBackdropUrl(group.anilistId)
                                                            if (bg.isNotEmpty()) backdropUrl = bg
                                                        }
                                                        if (logoUrl.isEmpty()) {
                                                            val logo = AniZipClient.getAnimeLogoUrl(group.anilistId)
                                                            if (logo.isNotEmpty()) logoUrl = logo
                                                        }
                                                    }

                                                    val displayImage = group.backdropUrl.ifEmpty { backdropUrl.ifEmpty { group.coverUrl } }
                                                    val displayLogo = group.logoUrl.ifEmpty { logoUrl }

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .clickable {
                                                                if (isSingleVideo) {
                                                                    group.episodes.firstOrNull()?.let { playEpisode(it) }
                                                                } else {
                                                                    selectedSeriesDrilldown = group
                                                                }
                                                            }
                                                            .padding(vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        // 16:9 Backdrop Thumbnail
                                                        Box(
                                                            modifier = Modifier
                                                                .width(132.dp)
                                                                .height(74.dp)
                                                                .clip(RoundedCornerShape(5.dp))
                                                                .background(Color(0xFF1E1E1E)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Image(
                                                                painter = rememberAsyncImagePainter(model = resolveImageModel(displayImage)),
                                                                contentDescription = group.animeTitle,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )

                                                            // Dark gradient overlay
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .background(
                                                                        Brush.verticalGradient(
                                                                            colors = listOf(
                                                                                Color.Black.copy(alpha = 0.15f),
                                                                                Color.Black.copy(alpha = 0.65f)
                                                                            )
                                                                        )
                                                                    )
                                                            )

                                                            // FireFly App Logo in top left (a little big)
                                                            Box(
                                                                modifier = Modifier
                                                                    .align(Alignment.TopStart)
                                                                    .padding(start = 5.dp, top = 4.dp)
                                                            ) {
                                                                Image(
                                                                    painter = painterResource(id = R.drawable.firefly_logo),
                                                                    contentDescription = "FireFly",
                                                                    modifier = Modifier
                                                                        .height(17.dp)
                                                                        .wrapContentWidth(),
                                                                    contentScale = ContentScale.Fit
                                                                )
                                                            }

                                                            // Show Logo Overlay (a little big)
                                                            if (displayLogo.isNotEmpty()) {
                                                                Image(
                                                                    painter = rememberAsyncImagePainter(model = resolveImageModel(displayLogo)),
                                                                    contentDescription = "Logo",
                                                                    contentScale = ContentScale.Fit,
                                                                    modifier = Modifier
                                                                        .align(Alignment.BottomStart)
                                                                        .padding(start = 5.dp, bottom = 4.dp, end = 5.dp)
                                                                        .height(28.dp)
                                                                        .fillMaxWidth(0.85f)
                                                                )
                                                            }

                                                            // If single video, center play button
                                                            if (isSingleVideo) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(28.dp)
                                                                        .align(Alignment.Center)
                                                                        .background(Color.Black.copy(alpha = 0.60f), CircleShape)
                                                                        .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.PlayArrow,
                                                                        contentDescription = "Play",
                                                                        tint = Color.White,
                                                                        modifier = Modifier.size(17.dp)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.width(14.dp))

                                                        // Middle: Title & Metadata Line
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = group.animeTitle,
                                                                color = Color.White,
                                                                fontSize = 15.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis,
                                                                lineHeight = 18.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(3.dp))
                                                            val metaText = if (isSingleVideo) {
                                                                "${group.episodes.firstOrNull()?.quality ?: "HD"} | ${formatDownloadedBytes(group.totalSizeBytes)}"
                                                            } else {
                                                                "${group.episodes.size} ${if (group.episodes.size == 1) "Episode" else "Episodes"} | ${formatDownloadedBytes(group.totalSizeBytes)}"
                                                            }
                                                            Text(
                                                                text = metaText,
                                                                color = Color(0xFF8E8E93),
                                                                fontSize = 12.5.sp,
                                                                fontWeight = FontWeight.Normal
                                                            )
                                                        }

                                                        Spacer(modifier = Modifier.width(8.dp))

                                                        // Right: Chevron for series, or Clean White Phone Checkmark for single download
                                                        if (isSingleVideo) {
                                                            NetflixDownloadedPhoneIcon(
                                                                modifier = Modifier.size(24.dp),
                                                                tint = Color.White
                                                            )
                                                        } else {
                                                            Icon(
                                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                                contentDescription = "Open Series",
                                                                tint = Color(0xFFCCCCCC),
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Delete Single Episode Dialog
                                if (episodeToDelete != null) {
                                    val target = episodeToDelete!!
                                    AlertDialog(
                                        onDismissRequest = { episodeToDelete = null },
                                        title = {
                                            Text(
                                                text = "Delete Episode",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp
                                            )
                                        },
                                        text = {
                                            Text(
                                                text = "Delete Episode ${target.episodeNumber} (${formatDownloadedBytes(target.fileSizeBytes)}) from your storage?",
                                                color = Color(0xFFCCCCCC),
                                                fontSize = 13.5.sp
                                            )
                                        },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        downloadManager.deleteEpisode(target.anilistId, target.episodeNumber)
                                                        storageStats = downloadManager.getStorageStats()
                                                        episodeToDelete = null
                                                        Toast.makeText(context, "Deleted Episode ${target.episodeNumber}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            ) {
                                                Text("Delete", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { episodeToDelete = null }) {
                                                Text("Cancel", color = Color(0xFF8E918F), fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        containerColor = Color(0xFF141414),
                                        titleContentColor = Color.White,
                                        textContentColor = Color(0xFFCCCCCC),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                // Delete Entire Series Dialog
                                if (groupToDelete != null) {
                                    val targetGroup = groupToDelete!!
                                    AlertDialog(
                                        onDismissRequest = { groupToDelete = null },
                                        title = {
                                            Text(
                                                text = "Delete Entire Series",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp
                                            )
                                        },
                                        text = {
                                            Text(
                                                text = "Delete all ${targetGroup.episodes.size} downloaded episodes (${formatDownloadedBytes(targetGroup.totalSizeBytes)}) of ${targetGroup.animeTitle}?",
                                                color = Color(0xFFCCCCCC),
                                                fontSize = 13.5.sp
                                            )
                                        },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        downloadManager.deleteAnimeGroup(targetGroup.anilistId)
                                                        storageStats = downloadManager.getStorageStats()
                                                        groupToDelete = null
                                                        selectedSeriesDrilldown = null
                                                        Toast.makeText(context, "Deleted ${targetGroup.animeTitle}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            ) {
                                                Text("Delete All Episodes", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { groupToDelete = null }) {
                                                Text("Cancel", color = Color(0xFF8E918F), fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        containerColor = Color(0xFF141414),
                                        titleContentColor = Color.White,
                                        textContentColor = Color(0xFFCCCCCC),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                // Delete All Downloads Dialog
                                if (showDeleteAllDownloadsDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showDeleteAllDownloadsDialog = false },
                                        title = {
                                            Text(
                                                text = "Delete All Downloads",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp
                                            )
                                        },
                                        text = {
                                            Text(
                                                text = "Are you sure you want to delete all downloaded anime episodes? This will permanently remove all offline files from your device storage.",
                                                color = Color(0xFFCCCCCC),
                                                fontSize = 13.5.sp,
                                                lineHeight = 19.sp
                                            )
                                        },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        downloadManager.deleteAllDownloads()
                                                        storageStats = downloadManager.getStorageStats()
                                                        showDeleteAllDownloadsDialog = false
                                                        selectedSeriesDrilldown = null
                                                        Toast.makeText(context, "All downloads deleted", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            ) {
                                                Text("Delete All", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDeleteAllDownloadsDialog = false }) {
                                                Text("Cancel", color = Color(0xFF8E918F), fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        containerColor = Color(0xFF141414),
                                        titleContentColor = Color.White,
                                        textContentColor = Color(0xFFCCCCCC),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }

                        3 -> {
                            // LIBRARY TAB ("My List")
                            val allCategories = listOf("All") + com.lagradost.cloudstream3.ui.animebox.library.LibraryManager.ANILIST_CATEGORIES
                            val normalizedSelected = com.lagradost.cloudstream3.ui.animebox.library.LibraryManager.normalizeCategory(selectedMyListCategory)
                            val filteredLibraryItems = if (selectedMyListCategory.equals("All", ignoreCase = true)) {
                                libraryItemsList
                            } else {
                                libraryItemsList.filter { 
                                    val cat = com.lagradost.cloudstream3.ui.animebox.library.LibraryManager.normalizeCategory(it.customListCategory)
                                    cat.equals(normalizedSelected, ignoreCase = true)
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp)
                            ) {
                                // Top Header - Placed comfortably below status bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 26.dp, bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "My List",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (libraryItemsList.isNotEmpty()) {
                                        Text(
                                            text = "${filteredLibraryItems.size} Titles",
                                            fontSize = 12.sp,
                                            color = primaryColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Middle Anime Grid
                                if (filteredLibraryItems.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (selectedMyListCategory == "All") "Your list is empty. Add shows to get started." else "No anime in \"$selectedMyListCategory\"",
                                            color = Color.Gray,
                                            fontSize = 14.sp
                                        )
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(3),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .padding(bottom = 6.dp)
                                    ) {
                                        items(filteredLibraryItems) { anime ->
                                            PremiumAnimePosterCard(
                                                anime = anime,
                                                inLibrary = true,
                                                onToggleLibrary = {
                                                    if (showOutageBanner || isOffline) {
                                                        showServiceUnavailableDialog = true
                                                    } else {
                                                        showAddToListDialogForAnime = anime
                                                    }
                                                },
                                                onClick = {
                                                    if (showOutageBanner || isOffline) {
                                                        showServiceUnavailableDialog = true
                                                    } else {
                                                        openDetailsPage(anime.id, anime)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                // Bottom Side Category Filter Chips (Above Navbar with sleek modern look, Drag-and-Drop Reorderable)
                                ReorderableDragTagRow(
                                    tags = orderedCategories,
                                    selectedTag = selectedMyListCategory,
                                    onTagSelected = { cat ->
                                        selectedMyListCategory = cat
                                    },
                                    onOrderChanged = { newOrder ->
                                        orderedCategories = newOrder
                                        saveCategoryOrder(this@AnimeBoxMainActivity, newOrder)
                                    },
                                    isCategoryStyle = true,
                                    isCategorySelected = { cat ->
                                        if (cat.equals("All", ignoreCase = true)) {
                                            selectedMyListCategory.equals("All", ignoreCase = true)
                                        } else {
                                            !selectedMyListCategory.equals("All", ignoreCase = true) && 
                                                com.lagradost.cloudstream3.ui.animebox.library.LibraryManager.normalizeCategory(cat) == normalizedSelected
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 12.dp)
                                )
                            }
                        }
                    }
                }

                // Continue Watching loading overlay
                if (continueLaunchLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = primaryColor)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Loading...", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Floating Top Header Bar (Search icon left | Notification + Profile right) — Home tab only
                if (selectedTab == 0) {
                    val activeProfileObj = ProfileManager.getProfiles(this@AnimeBoxMainActivity)
                        .find { it.id == currentProfileId }
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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    ) {
                        // Minimal subtle top gradient for header visibility
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.30f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left side: FireFly App Logo
                            Image(
                                painter = painterResource(id = R.drawable.firefly_logo),
                                contentDescription = "FireFly",
                                modifier = Modifier
                                    .height(48.dp)
                                    .wrapContentWidth(),
                                contentScale = ContentScale.Fit
                            )

                            // Right side: Search + Notification bell + Profile avatar
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Search Icon Button
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null
                                        ) { selectedTab = 1 },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = CustomSearchIcon,
                                        contentDescription = "Search",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Notification Icon Button
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null
                                        ) { 
                                            showNotificationsDialog = true 
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier.size(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = CustomNotificationIcon,
                                            contentDescription = "Notifications",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        if (unreadNotifCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 3.dp, y = (-2).dp)
                                                    .size(15.dp)
                                                    .background(Color(0xFFE50914), CircleShape)
                                                    .border(1.dp, Color.Black, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (unreadNotifCount > 9) "9+" else "$unreadNotifCount",
                                                    color = Color.White,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    textAlign = TextAlign.Center,
                                                    style = androidx.compose.ui.text.TextStyle(
                                                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                                            includeFontPadding = false
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable { showProfileSelector = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF222228)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (avatarUrl.isNotEmpty()) {
                                            Image(
                                                painter = rememberAsyncImagePainter(model = profilePainter),
                                                contentDescription = activeName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(
                                                text = activeName.take(1).uppercase(),
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
                }
            }
        }

        if (showProfileSelector) {
            ProfileSelectorDialog(
                initialProfileForEditing = initialProfileForEditing,
                onDismiss = { 
                    showProfileSelector = false
                    initialProfileForEditing = null
                },
                onProfileSelected = { profileId ->
                    ProfileManager.setActiveProfile(this@AnimeBoxMainActivity, profileId)
                    currentProfileId = profileId
                    val hMgr = WatchHistoryManager(this@AnimeBoxMainActivity, profileId)
                    val lMgr = LibraryManager(this@AnimeBoxMainActivity, profileId)
                    continueWatchingList = hMgr.getWatchHistory()
                    libraryItemsList = lMgr.getLibraryItems()
                    showProfileSelector = false
                    initialProfileForEditing = null
                }
            )
        }

        if (showNotificationsDialog) {
            NotificationsDialog(
                notifications = supabaseNotifications,
                onDismiss = { showNotificationsDialog = false },
                onMarkAllAsRead = {
                    coroutineScope.launch {
                        SupabaseNotificationManager.markAllAsRead(this@AnimeBoxMainActivity)
                        supabaseNotifications = SupabaseNotificationManager.getCachedNotifications(this@AnimeBoxMainActivity)
                        unreadNotifCount = 0
                    }
                },
                onNotificationClick = { notif ->
                    coroutineScope.launch {
                        SupabaseNotificationManager.markAsRead(this@AnimeBoxMainActivity, notif.id)
                        supabaseNotifications = SupabaseNotificationManager.getCachedNotifications(this@AnimeBoxMainActivity)
                        unreadNotifCount = supabaseNotifications.count { !it.isRead }
                    }
                    if (!notif.link.isNullOrEmpty()) {
                        val link = notif.link
                        val intId = link.toIntOrNull()
                        if (intId != null) {
                            openDetailsPage(intId)
                            showNotificationsDialog = false
                        } else if (link.startsWith("http://") || link.startsWith("https://")) {
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                startActivity(browserIntent)
                            } catch (_: Exception) {}
                        }
                    }
                },
                onClearAll = {
                    SupabaseNotificationManager.clearAllNotifications(this@AnimeBoxMainActivity)
                    supabaseNotifications = emptyList()
                    unreadNotifCount = 0
                }
            )
        }

        if (showDnsDialog) {
            AnimeBoxDnsDialog(
                onDismiss = { showDnsDialog = false },
                onDnsChanged = { loadHomeData() }
            )
        }

        // Coming Soon Release Alert Dialog
        if (comingSoonAlertAnime != null) {
            AlertDialog(
                onDismissRequest = { comingSoonAlertAnime = null },
                confirmButton = {
                    TextButton(onClick = { comingSoonAlertAnime = null }) {
                        Text("OK", color = primaryColor, fontWeight = FontWeight.Bold)
                    }
                },
                title = {
                    Text(
                        text = "Coming Soon",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "The following anime has not been released yet.",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                },
                containerColor = Color(0xFF1E1E1E),
                titleContentColor = Color.White,
                textContentColor = Color.LightGray
            )
        }

        // Kitsu Mode My List Restriction Alert Dialog
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

        // Kitsu Fallback Offer Prompt Dialog (Triggered on Retry Failure)
        if (showKitsuFallbackPromptDialog) {
            Dialog(
                onDismissRequest = { showKitsuFallbackPromptDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable { showKitsuFallbackPromptDialog = false },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(min = 300.dp, max = 390.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF282828))
                            .clickable(enabled = false) {}
                            .padding(22.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF383838)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = CustomCloudOffIcon,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Switch to Kitsu Fallback?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "AniList servers are still unreachable. Would you like to temporarily switch to Kitsu metadata to browse and stream anime?",
                                color = Color(0xFFC4C4C4),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { showKitsuFallbackPromptDialog = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF383838)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Text("Stay Offline", color = Color(0xFFE0E0E0), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }

                                Button(
                                    onClick = {
                                        showKitsuFallbackPromptDialog = false
                                        isKitsuMode = true
                                        loadKitsuHomeData()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Text("Switch to Kitsu", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Service Unavailable / Offline Alert Dialog
        if (showServiceUnavailableDialog) {
            Dialog(
                onDismissRequest = { showServiceUnavailableDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable { showServiceUnavailableDialog = false },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(min = 300.dp, max = 380.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF282828))
                            .clickable(enabled = false) {}
                            .padding(22.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF383838)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isOffline) CustomWifiOffIcon else CustomCloudOffIcon,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = if (isOffline) "You're Offline" else "Service Unavailable",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (isOffline) {
                                    "You are currently offline. Online streaming and anime details cannot be loaded without an internet connection.\n\nYou can watch your downloaded anime episodes in the Downloads section."
                                } else {
                                    "AniList servers are currently unreachable. Online streaming and anime details cannot be accessed right now.\n\nYou can watch your downloaded anime episodes in the Downloads section."
                                },
                                color = Color(0xFFC4C4C4),
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { showServiceUnavailableDialog = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF383838)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Text("Dismiss", color = Color(0xFFE0E0E0), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }

                                Button(
                                    onClick = {
                                        showServiceUnavailableDialog = false
                                        selectedTab = 2 // Switch to Downloads tab!
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.netflix_download),
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Downloads", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add to My List / Watch Status Category Selector Dialog (Matching Screenshot 2)
        if (showAddToListDialogForAnime != null) {
            if (isKitsuMode || com.lagradost.cloudstream3.ui.animebox.api.KitsuClient.isKitsuModeActive || AniListClient.lastErrorMessage != null) {
                showKitsuMyListRestrictionDialog = true
                showAddToListDialogForAnime = null
            } else {
                val targetAnime = showAddToListDialogForAnime!!
                val isInLib = libraryManager.isInLibrary(targetAnime.id)
                val currentCat = if (isInLib) libraryManager.getAnimeCategory(targetAnime.id) else "Plan to Watch"

                com.lagradost.cloudstream3.ui.animebox.library.SelectWatchStatusDialog(
                    isInLibrary = isInLib,
                    currentCategory = currentCat,
                    onSelectStatus = { selectedStatus ->
                        libraryManager.addOrUpdateLibraryItem(targetAnime, selectedStatus)
                        libraryItemsList = libraryManager.getLibraryItems()
                        showAddToListDialogForAnime = null
                        Toast.makeText(context, "Added to \"$selectedStatus\"", Toast.LENGTH_SHORT).show()
                    },
                    onRemoveFromLibrary = {
                        libraryManager.removeLibraryItem(targetAnime.id)
                        libraryItemsList = libraryManager.getLibraryItems()
                        showAddToListDialogForAnime = null
                        Toast.makeText(context, "Removed from Library", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = {
                        showAddToListDialogForAnime = null
                    }
                )
            }
        }
    }
}
}

    @Composable
    fun NetflixBottomNav(
        selectedTab: Int,
        onTabSelected: (Int) -> Unit,
        onMySpaceClick: () -> Unit
    ) {
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
                // Tab 0: Home
                val isHomeSelected = selectedTab == 0
                val homeColor = if (isHomeSelected) Color.White else Color(0xFF8E8E93)
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
                        imageVector = CustomHomeIcon,
                        contentDescription = "Home",
                        tint = homeColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Home",
                        color = homeColor,
                        fontSize = 11.sp,
                        fontWeight = if (isHomeSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                // Tab 1: Search
                val isSearchSelected = selectedTab == 1
                val searchColor = if (isSearchSelected) Color.White else Color(0xFF8E8E93)
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
                        tint = searchColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Search",
                        color = searchColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSearchSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                // Tab 2: Downloads
                val isDownloadsSelected = selectedTab == 2
                val downloadsColor = if (isDownloadsSelected) Color.White else Color(0xFF8E8E93)
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
                        tint = downloadsColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Downloads",
                        color = downloadsColor,
                        fontSize = 11.sp,
                        fontWeight = if (isDownloadsSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                // Tab 3: My Lists (Shopping Bag icon matching reference screenshot)
                val isListSelected = selectedTab == 3
                val listColor = if (isListSelected) Color.White else Color(0xFF8E8E93)
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
                        contentDescription = "My Lists",
                        tint = listColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "My Lists",
                        color = listColor,
                        fontSize = 11.sp,
                        fontWeight = if (isListSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                // Tab 4: My Space (Profile Avatar)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onMySpaceClick() }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF222228)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUrl.isNotEmpty()) {
                            Image(
                                painter = rememberAsyncImagePainter(model = profilePainter),
                                contentDescription = activeName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = activeName.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "My Space",
                        color = Color(0xFF8E8E93),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }

    @Composable
    fun CustomSubtitlesIcon(color: Color, modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .size(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.2.dp, color, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(3.dp, 1.2.dp).background(color))
                    Box(modifier = Modifier.size(3.dp, 1.2.dp).background(color))
                }
            }
        }
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
    fun PremiumAnimePosterCard(
        anime: AnimeBrief,
        inLibrary: Boolean,
        onToggleLibrary: () -> Unit,
        onClick: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .width(118.dp)
                .clickable(onClick = onClick)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(174.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161616))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = anime.coverUrl),
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Firefly logo badge on top-left corner
                Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.lagradost.cloudstream3.R.drawable.firefly_logo),
                    contentDescription = "Firefly",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(16.dp)
                )

                // Top-right circular add button (gold '+', or gold checkmark '✓' if in library)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { onToggleLibrary() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (inLibrary) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = "My List Toggle",
                        tint = Color(0xFFFFB300), // Netflix style gold/yellow
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Semi-transparent bottom audio/subtitle bar
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(Color.Black.copy(alpha = 0.75f)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // CC badge (subtitle indicator)
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CC",
                            color = Color.White,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 8.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (anime.episodes > 0) "${anime.episodes}" else "?",
                        color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(7.dp))
                    Text("|", color = Color.Gray.copy(alpha = 0.5f), fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(7.dp))

                    CustomMicIcon(
                        color = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (anime.episodes > 0) "${anime.episodes}" else "?",
                        color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = anime.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    @Composable
    fun SectionHeader(title: String) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )
    }

    @Composable
    fun NotificationsDialog(
        notifications: List<SupabaseNotification>,
        onDismiss: () -> Unit,
        onMarkAllAsRead: () -> Unit,
        onNotificationClick: (SupabaseNotification) -> Unit,
        onClearAll: () -> Unit
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() },
                contentAlignment = Alignment.TopCenter
            ) {
                // Floating Card anchored near top matching user reference design
                Box(
                    modifier = Modifier
                        .padding(top = 52.dp, start = 16.dp, end = 16.dp)
                        .widthIn(min = 320.dp, max = 390.dp)
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141416))
                        .border(1.dp, Color(0xFF27272A), RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume clicks inside card */ }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Top Header Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF18181B))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Notifications",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (notifications.isNotEmpty()) {
                                    Text(
                                        text = "Mark all read",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { onMarkAllAsRead() }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )

                                    Text(
                                        text = "Clear all",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { onClearAll() }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF27272A)))

                        // Notifications list
                        if (notifications.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp, horizontal = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = CustomNotificationIcon,
                                    contentDescription = null,
                                    tint = Color(0xFF52525B),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No notifications yet",
                                    color = Color(0xFFA1A1AA),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "New episodes and announcements will appear here",
                                    color = Color(0xFF71717A),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 480.dp)
                            ) {
                                items(notifications.size) { index ->
                                    val notif = notifications[index]
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (!notif.isRead) Color(0xFF1C1C20) else Color.Transparent)
                                            .clickable { onNotificationClick(notif) }
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                    ) {
                                        // Title & Unread indicator
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = notif.title,
                                                color = Color.White,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 19.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (!notif.isRead) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.5.dp)
                                                        .background(Color.White, CircleShape)
                                                )
                                            }
                                        }

                                        // Message Body
                                        if (notif.message.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = notif.message,
                                                color = Color(0xFFA1A1AA),
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            )
                                        }

                                        // Image / GIF attachment preview
                                        if (!notif.imageUrl.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 160.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF09090B))
                                                    .border(1.dp, Color(0xFF27272A), RoundedCornerShape(8.dp))
                                            ) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(model = notif.imageUrl),
                                                    contentDescription = "Notification Image",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Timestamp & Optional View action
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = notif.formattedDateTime(),
                                                color = Color(0xFF71717A),
                                                fontSize = 11.5.sp
                                            )

                                            if (!notif.link.isNullOrEmpty()) {
                                                Text(
                                                    text = "Open →",
                                                    color = primaryColor,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    if (index < notifications.size - 1) {
                                        Spacer(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(Color(0xFF202024))
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

    @Composable
    fun FaqSection() {
        val faqs = remember {
            listOf(
                "What is FireFly?" to "FireFly is a premium streaming service that allows you to watch a wide variety of anime shows, movies, and more on thousands of internet-connected devices.",
                "How much does FireFly Cost?" to "FireFly is completely free! There are no hidden fees, subscriptions, or contracts.",
                "Where can I watch?" to "Watch anywhere, anytime. Sign in with your account to watch instantly on your phone or tablet.",
                "How do I cancel?" to "Since FireFly is free, there are no subscriptions to cancel! You can simply close or uninstall the app at any time.",
                "What can I watch on FireFly?" to "FireFly has an extensive library of anime feature films, documentaries, TV shows, and award-winning FireFly originals. Watch as much as you want, anytime you want.",
                "Is FireFly good for kids?" to "Yes! You can create dedicated kids profiles to filter out adult content and restrict viewing to age-appropriate anime content."
            )
        }

        var expandedIndex by remember { mutableStateOf(-1) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Text(
                text = "Frequently Asked Questions",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            faqs.forEachIndexed { index, faq ->
                val isExpanded = expandedIndex == index
                FaqItem(
                    question = faq.first,
                    answer = faq.second,
                    isExpanded = isExpanded,
                    onToggle = {
                        expandedIndex = if (isExpanded) -1 else index
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    @Composable
    fun FaqItem(question: String, answer: String, isExpanded: Boolean, onToggle: () -> Unit) {
        val rotationAngle by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isExpanded) 45f else 0f,
            label = "plus_icon_rotation"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2D2D2D))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = question,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Expand",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotationAngle)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = answer,
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }

    @Composable
    fun HomeGenreCard(genre: HomeGenreCategory, onClick: () -> Unit) {
        var backdropUrl by remember(genre.id, genre.backdropUrl) { mutableStateOf(genre.backdropUrl.ifEmpty { genre.posterUrl }) }
        LaunchedEffect(genre.anilistId, genre.backdropUrl) {
            if (genre.backdropUrl.isEmpty()) {
                try {
                    val tmdb = AniZipClient.getAnimeBackdropUrl(genre.anilistId)
                    if (tmdb.isNotEmpty()) {
                        backdropUrl = tmdb
                    }
                } catch (_: Exception) {}
            }
        }

        Box(
            modifier = Modifier
                .width(160.dp)
                .height(76.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(genre.startColor)
                .clickable { onClick() }
        ) {
            // Right-side Backdrop Image with center portion visible on right
            if (backdropUrl.isNotEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(model = backdropUrl),
                    contentDescription = genre.displayName,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(96.dp)
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                )
            }

            // Organic curved wave mask & horizontal smooth blend
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w * 0.44f, 0f)
                    cubicTo(
                        w * 0.56f, h * 0.35f,
                        w * 0.38f, h * 0.65f,
                        w * 0.48f, h
                    )
                    lineTo(0f, h)
                    close()
                }
                drawPath(path = path, color = genre.startColor)

                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to genre.startColor,
                            0.42f to genre.startColor,
                            0.58f to genre.startColor.copy(alpha = 0.85f),
                            0.78f to genre.startColor.copy(alpha = 0.20f),
                            1.0f to Color.Transparent
                        )
                    )
                )
            }

            // Genre Title (Bold clean white text)
            Text(
                text = genre.displayName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.90f),
                        blurRadius = 8f,
                        offset = androidx.compose.ui.geometry.Offset(0f, 2f)
                    )
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp, end = 68.dp)
            )
        }
    }

    @Composable
    fun SearchRecommendedItemRow(
        anime: AnimeBrief,
        onClick: () -> Unit,
        onPlayClick: () -> Unit
    ) {
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: 16:9 Dual / Banner thumbnail
            Box(
                modifier = Modifier
                    .width(118.dp)
                    .height(66.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E1E22))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context)
                            .data(anime.coverUrl)
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Subtle gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color(0x40000000), Color(0x99000000))
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Middle: Title and Subtitle
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = anime.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Series  16+  Dub | Sub",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right: Orange Circular Play Button (Matching Reference Screenshot 2 & 3)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF6B00))
                    .clickable { onPlayClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    @Composable
    fun SuggestionCard(
        anime: AnimeBrief,
        backdropUrl: String,
        isAniZipBackdrop: Boolean,
        inLibrary: Boolean,
        onToggleLibrary: () -> Unit,
        onClick: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(155.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E1E))
                .clickable(onClick = onClick)
        ) {
            // Backdrop Image
            Image(
                painter = rememberAsyncImagePainter(model = backdropUrl),
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dark Gradient Overlay (stronger on left/bottom for readability)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Side: Mini Poster + Title/Meta Info
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.weight(1f)
                ) {
                    // Mini Poster overlapping/inset
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(84.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = anime.coverUrl),
                            contentDescription = anime.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title & Meta details
                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = anime.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${if (anime.episodes > 0) "${anime.episodes} Episodes" else "Ongoing"}  •  Score ${if (anime.averageScore > 0) "${anime.averageScore}%" else "0%"}",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Right Side: Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    // Play Button
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // My List / Add Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable { onToggleLibrary() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (inLibrary) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = "Toggle List",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun SpotlightSection(
        spotlight: AnimeBrief,
        inLibrary: Boolean,
        onToggleLibrary: () -> Unit,
        onPlayClick: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .clickable { onPlayClick() }
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = spotlight.bannerUrl.ifEmpty { spotlight.coverUrl }),
                contentDescription = spotlight.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Top faint shadow gradient on spotlight to ensure header icons are clearly visible
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // Bottom fade gradient where title, genres and action buttons sit
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            0.82f to Color.Black.copy(alpha = 0.65f),
                            1f to Color.Black
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = spotlight.title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )

                if (spotlight.genres.isNotEmpty()) {
                    Text(
                        text = spotlight.genres.take(4).joinToString(" • "),
                        fontSize = 12.sp,
                        color = Color(0xFFE2E2E6),
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(top = 3.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: None / My List
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleLibrary() }
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (inLibrary) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = "My List",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (inLibrary) "In List" else "None",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    // Center: Play Button (Sleek translucent frosted glass button)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.28f))
                            .clickable { onPlayClick() }
                            .padding(horizontal = 26.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    // Right: Info Button (Circular outline Info icon matching screenshot)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPlayClick() }
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = CustomOutlinedInfoIcon,
                            contentDescription = "Info",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Info",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60)
        return String.format("%02d:%02d", minutes, seconds)
    }

    @Composable
    fun ContinueWatchingCard(
        item: WatchHistoryItem,
        onDeleteClick: () -> Unit,
        onInfoClick: () -> Unit,
        onClick: (String) -> Unit
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val cachedEpThumb = continueWatchingPosterCache[item.anilistId * 1000 + item.episodeNumber] ?: ""
        val cachedLogo = continueWatchingLogoCache[item.anilistId] ?: ""
        var episodeThumbUrl by remember(item.anilistId, item.episodeNumber) { 
            mutableStateOf(if (item.coverImageUrl.isNotEmpty() && !item.coverImageUrl.contains("cover", ignoreCase = true)) item.coverImageUrl else cachedEpThumb) 
        }
        var animeLogoUrl by remember(item.anilistId) { mutableStateOf(cachedLogo) }

        LaunchedEffect(item.anilistId, item.episodeNumber) {
            if (cachedLogo.isEmpty()) {
                val logo = AniZipClient.getAnimeLogoUrl(item.anilistId)
                if (logo.isNotEmpty()) {
                    continueWatchingLogoCache[item.anilistId] = logo
                    animeLogoUrl = logo
                }
            }
            if (episodeThumbUrl.isEmpty() || episodeThumbUrl.contains("cover", ignoreCase = true)) {
                try {
                    val epMetaMap = AniZipClient.getEpisodeMetadata(item.anilistId)
                    val targetEp = epMetaMap[item.episodeNumber]
                    val epImg = targetEp?.imageUrl ?: ""
                    if (epImg.isNotEmpty()) {
                        continueWatchingPosterCache[item.anilistId * 1000 + item.episodeNumber] = epImg
                        episodeThumbUrl = epImg
                    } else {
                        val details = AniListClient.getAnimeDetails(item.anilistId)
                        if (details != null) {
                            val media = org.json.JSONObject(details).getJSONObject("data").getJSONObject("Media")
                            val banner = media.optString("bannerImage").ifEmpty {
                                val coverObj = media.getJSONObject("coverImage")
                                coverObj.optString("extraLarge").ifEmpty { coverObj.optString("large") }
                            }
                            if (banner.isNotEmpty()) {
                                continueWatchingPosterCache[item.anilistId * 1000 + item.episodeNumber] = banner
                                episodeThumbUrl = banner
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val displayThumb = episodeThumbUrl.ifEmpty { item.coverImageUrl }
        val percentage = if (item.totalDurationMs > 0) (item.progressPositionMs.toFloat() / item.totalDurationMs).coerceIn(0f, 1f) else 0f

        Column(
            modifier = Modifier
                .width(204.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141418))
        ) {
            // 16:9 Thumbnail Block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .clickable { onClick(displayThumb) }
            ) {
                if (displayThumb.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF222228))
                    )
                } else {
                    Image(
                        painter = rememberAsyncImagePainter(model = displayThumb),
                        contentDescription = item.animeTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Dark vignette overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.30f))
                )

                // Anime PNG Logo hovering if available
                if (animeLogoUrl.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(34.dp)
                            .align(Alignment.BottomStart)
                            .padding(bottom = 6.dp, start = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = animeLogoUrl),
                            contentDescription = item.animeTitle,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Netflix Firefly badge top-left
                Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.lagradost.cloudstream3.R.drawable.firefly_logo),
                    contentDescription = "Firefly",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(16.dp)
                )

                // Netflix-style center ring play button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Delete / Remove 'x' button on top-right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 5.dp, end = 5.dp)
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                        .clickable { onDeleteClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Episode badge in bottom-right corner: half-circle quadrant touching corner with bold text
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(topStart = 10.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = "Ep ${item.episodeNumber}",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.2.sp
                    )
                }

                // Lavender Progress Bar at bottom of thumbnail (Dynamic app theme accent)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomStart)
                        .background(Color(0xFF33333E))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = percentage)
                            .fillMaxHeight()
                            .background(primaryColor)
                    )
                }
            }

            // Bottom title and episode info in card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(displayThumb) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = item.animeTitle,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Episode ${item.episodeNumber}",
                    color = Color(0xFF9E9EA4),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }

    enum class CardTrailingType {
        CHEVRON, ARROW, SWITCH
    }

    @Composable
    fun EditProfileOptionCard(
        icon: Any,
        title: String,
        subtitle: String,
        trailingType: CardTrailingType,
        switchChecked: Boolean = false,
        onSwitchChanged: ((Boolean) -> Unit)? = null,
        onClick: (() -> Unit)? = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1B1B20))
                .then(
                    if (onClick != null && trailingType != CardTrailingType.SWITCH) Modifier.clickable { onClick() }
                    else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                when (icon) {
                    is SettingsIconType -> PixelSettingsIcon(type = icon, tint = Color.LightGray, sizeDp = 22)
                    is ImageVector -> Icon(imageVector = icon, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(22.dp))
                    is androidx.compose.ui.graphics.painter.Painter -> Icon(painter = icon, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFFA0A0AB),
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            when (trailingType) {
                CardTrailingType.CHEVRON, CardTrailingType.ARROW -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(22.dp)
                    )
                }
                CardTrailingType.SWITCH -> {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val switchColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getAppThemeColor(ctx)
                    Switch(
                        checked = switchChecked,
                        onCheckedChange = { onSwitchChanged?.invoke(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = switchColor,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color(0xFF33333C)
                        )
                    )
                }
            }
        }
    }

    @Composable
    fun ProfileSelectorDialog(
        initialProfileForEditing: UserProfile? = null,
        onDismiss: () -> Unit,
        onProfileSelected: (String) -> Unit
    ) {
        val context = LocalContext.current
        val primaryColor = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getAppThemeColor(context)
        var profilesList by remember { mutableStateOf(ProfileManager.getProfiles(context)) }

        var showAddProfile by remember { mutableStateOf(false) }
        var newProfileName by remember { mutableStateOf("") }
        var profileLimitWarning by remember { mutableStateOf(false) }

        // Editing Profile specific states
        var editingProfile by remember { mutableStateOf<UserProfile?>(initialProfileForEditing) }
        var editProfileName by remember(editingProfile) { mutableStateOf(editingProfile?.name ?: "") }
        var editProfileAvatarUrl by remember(editingProfile) { mutableStateOf(editingProfile?.avatarUrl ?: "") }

        // Settings States
        var autoplayNextEpisode by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isAutoplayNextEpisodeEnabled(context)) }
        var autoplayPreviews by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isAutoplayPreviewsEnabled(context)) }
        var autoSkipIntro by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isSkipIntroEnabled(context)) }
        var lowPerfMode by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isLowPerformanceMode(context)) }
        var maturityRating by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getMaturityRating(context)) }
        var displayLanguage by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getDisplayLanguage(context)) }
        var currentTheme by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getAppTheme(context)) }
        var currentAudio by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getLastUsedAudio(context)) }
        var currentSubMode by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getLastUsedSubMode(context)) }
        var seekDuration by remember { mutableIntStateOf(com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getSeekDuration(context)) }
        var brightnessMode by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getBrightnessMode(context)) }
        var volumeMode by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getVolumeMode(context)) }
        var timelineTheme by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getPlayerTimelineTheme(context)) }
        var dnsMode by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getDnsMode(context)) }

        // Subtitle Appearance States
        var subFontSize by remember { mutableFloatStateOf(context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE).getFloat("sub_font_size", 18f)) }
        var subColor by remember { mutableStateOf(context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE).getString("sub_color", "White") ?: "White") }
        var subBgOpacity by remember { mutableIntStateOf(context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE).getInt("sub_bg_opacity", 50)) }
        var subEdgeStyle by remember { mutableStateOf(context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE).getString("sub_edge_style", "Drop Shadow") ?: "Drop Shadow") }

        // Storage / Downloads States
        var wifiOnlyDownloads by remember { mutableStateOf(context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE).getBoolean("wifi_only_downloads", false)) }
        var smartDownloads by remember { mutableStateOf(context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE).getBoolean("smart_downloads", true)) }

        // Subdialog modals
        var showMaturityDialog by remember { mutableStateOf(false) }
        var showLanguageDialog by remember { mutableStateOf(false) }
        var showAudioSubDialog by remember { mutableStateOf(false) }
        var showSubtitleAppearanceDialog by remember { mutableStateOf(false) }
        var showThemeDialog by remember { mutableStateOf(false) }
        var showPlayerControlsDialog by remember { mutableStateOf(false) }
        var showTimelineColorDialog by remember { mutableStateOf(false) }
        var showDnsDialog by remember { mutableStateOf(false) }
        var showDownloadsStorageDialog by remember { mutableStateOf(false) }
        var showBackupRestoreDialog by remember { mutableStateOf(false) }
        var showDeleteConfirmDialog by remember { mutableStateOf(false) }

        fun loc(key: String): String {
            return when (displayLanguage.lowercase()) {
                "japanese", "ja" -> when (key) {
                    "edit_profile" -> "プロフィール編集"
                    "profile_name" -> "プロフィール名"
                    "maturity_rating" -> "年齢制限設定"
                    "display_language" -> "表示言語"
                    "display_language_sub" -> "アプリ全体の表示言語を変更します"
                    "audio_sub_lang" -> "音声と字幕の言語"
                    "audio_sub_sub" -> "デフォルトの音声トラックと字幕設定"
                    "subtitle_appearance" -> "字幕の表示設定"
                    "subtitle_appearance_sub" -> "フォントサイズ、色、背景、スタイルをカスタマイズ"
                    "theme_accent" -> "テーマアクセントカラー"
                    "player_controls" -> "プレイヤーとジェスチャー設定"
                    "player_controls_sub" -> "シーク時間、明るさ・音量スワイプ操作"
                    "network_dns" -> "ネットワークとDNS設定"
                    "downloads_storage" -> "ダウンロードとストレージ"
                    "downloads_storage_sub" -> "Wi-Fiのみダウンロード、ストレージとキャッシュ管理"
                    "backup_restore" -> "バックアップとデータ管理"
                    "backup_restore_sub" -> "マイリスト、履歴、設定のエクスポート/インポート"
                    "autoplay_next" -> "次のエピソードを自動再生"
                    "autoplay_next_sub" -> "次のエピソードを自動的に読み込んで再生します"
                    "autoplay_previews" -> "プレビューと予告編の自動再生"
                    "autoplay_previews_sub" -> "ホーム画面で予告動画を自動再生"
                    "auto_skip_intro" -> "OP/EDの自動スキップ"
                    "auto_skip_intro_sub" -> "オープニングとエンディングを自動的にスキップ"
                    "low_perf" -> "低パフォーマンスモード"
                    "low_perf_sub" -> "低スペック端末向けにアニメーションを軽減"
                    "delete_profile" -> "プロフィールを削除"
                    "save_changes" -> "変更を保存"
                    else -> key
                }
                "hindi", "hi" -> when (key) {
                    "edit_profile" -> "प्रोफ़ाइल संपादित करें"
                    "profile_name" -> "प्रोफ़ाइल नाम"
                    "maturity_rating" -> "आयु रेटिंग"
                    "display_language" -> "प्रदर्शन भाषा"
                    "display_language_sub" -> "ऐप में प्रदर्शित भाषा बदलें"
                    "audio_sub_lang" -> "ऑडियो और उपशीर्षक भाषाएँ"
                    "audio_sub_sub" -> "पसंदीदा ऑडियो और उपशीर्षक प्राथमिकताएं"
                    "subtitle_appearance" -> "उपशीर्षक दिखावट"
                    "subtitle_appearance_sub" -> "फ़ॉन्ट आकार, रंग, पृष्ठभूमि और शैली अनुकूलित करें"
                    "theme_accent" -> "थीम एक्सेंट रंग"
                    "player_controls" -> "प्लेयर और जेस्चर नियंत्रण"
                    "player_controls_sub" -> "सीक अवधि, चमक और वॉल्यूम जेस्चर"
                    "network_dns" -> "नेटवर्क और डीएनएस रूटिंग"
                    "downloads_storage" -> "डाउनलोड और स्टोरेज"
                    "downloads_storage_sub" -> "वाई-फ़ाई डाउनलोड, कैश साफ़ करें"
                    "backup_restore" -> "बैकअप और डेटा प्रबंधन"
                    "backup_restore_sub" -> "वॉचलिस्ट, इतिहास और सेटिंग्स JSON बैकअप"
                    "autoplay_next" -> "अगला एपिसोड ऑटोप्ले करें"
                    "autoplay_next_sub" -> "स्वचालित रूप से अगला एपिसोड चलाएं"
                    "autoplay_previews" -> "ट्रेलर ऑटोप्ले करें"
                    "autoplay_previews_sub" -> "होम कार्ड पर वीडियो पूर्वावलोकन चलाएं"
                    "auto_skip_intro" -> "इंट्रो / आउट्रो ऑटो-स्किप करें"
                    "auto_skip_intro_sub" -> "ओपनिंग और एंडिंग अपने आप छोड़ें"
                    "low_perf" -> "लो परफॉर्मेंस मोड"
                    "low_perf_sub" -> "धीमे फोन के लिए भारी एनिमेशन बंद करें"
                    "delete_profile" -> "प्रोफ़ाइल हटाएं"
                    "save_changes" -> "बदलाव सहेजें"
                    else -> key
                }
                "spanish", "es" -> when (key) {
                    "edit_profile" -> "Editar perfil"
                    "profile_name" -> "Nombre del perfil"
                    "maturity_rating" -> "Clasificación por edad"
                    "display_language" -> "Idioma de visualización"
                    "display_language_sub" -> "Cambiar el idioma del texto en la app"
                    "audio_sub_lang" -> "Idiomas de audio y subtítulos"
                    "audio_sub_sub" -> "Audio y subtítulos predeterminados"
                    "subtitle_appearance" -> "Apariencia de subtítulos"
                    "subtitle_appearance_sub" -> "Personalizar tamaño, color, fondo y estilo"
                    "theme_accent" -> "Color de acento del tema"
                    "player_controls" -> "Controles del reproductor"
                    "player_controls_sub" -> "Duración de búsqueda, gestos de brillo y volumen"
                    "network_dns" -> "Red y enrutamiento DNS"
                    "downloads_storage" -> "Descargas y almacenamiento"
                    "downloads_storage_sub" -> "Descargas por Wi-Fi, borrar caché"
                    "backup_restore" -> "Copia de seguridad y datos"
                    "backup_restore_sub" -> "Exportar e importar lista, historial y ajustes"
                    "autoplay_next" -> "Reproducir siguiente episodio"
                    "autoplay_next_sub" -> "Cargar y reproducir automáticamente"
                    "autoplay_previews" -> "Avances automáticos"
                    "autoplay_previews_sub" -> "Reproducir avances de video en inicio"
                    "auto_skip_intro" -> "Saltar intro / outro automáticamente"
                    "auto_skip_intro_sub" -> "Omitir aperturas y cierres"
                    "low_perf" -> "Modo de bajo rendimiento"
                    "low_perf_sub" -> "Desactivar animaciones pesadas"
                    "delete_profile" -> "Eliminar perfil"
                    "save_changes" -> "Guardar cambios"
                    else -> key
                }
                "french", "fr" -> when (key) {
                    "edit_profile" -> "Modifier le profil"
                    "profile_name" -> "Nom du profil"
                    "maturity_rating" -> "Classification d'âge"
                    "display_language" -> "Langue d'affichage"
                    "display_language_sub" -> "Changer la langue du texte dans l'application"
                    "audio_sub_lang" -> "Langues audio et sous-titres"
                    "audio_sub_sub" -> "Piste audio et sous-titres préférés"
                    "subtitle_appearance" -> "Apparence des sous-titres"
                    "subtitle_appearance_sub" -> "Personnaliser la taille, la couleur, le fond"
                    "theme_accent" -> "Couleur d'accentuation"
                    "player_controls" -> "Commandes du lecteur"
                    "player_controls_sub" -> "Durée du saut, gestes de luminosité et volume"
                    "network_dns" -> "Réseau et routage DNS"
                    "downloads_storage" -> "Téléchargements et stockage"
                    "downloads_storage_sub" -> "Téléchargement Wi-Fi, vider le cache"
                    "backup_restore" -> "Sauvegarde et données"
                    "backup_restore_sub" -> "Exporter et importer la liste et l'historique"
                    "autoplay_next" -> "Lecture auto épisode suivant"
                    "autoplay_next_sub" -> "Charger et lire automatiquement le suivant"
                    "autoplay_previews" -> "Aperçus automatiques"
                    "autoplay_previews_sub" -> "Lire les bandes-annonces sur l'accueil"
                    "auto_skip_intro" -> "Passer l'intro / outro auto"
                    "auto_skip_intro_sub" -> "Ignorer automatiquement génériques"
                    "low_perf" -> "Mode basse performance"
                    "low_perf_sub" -> "Désactiver les animations lourdes"
                    "delete_profile" -> "Supprimer le profil"
                    "save_changes" -> "Enregistrer les modifications"
                    else -> key
                }
                "german", "de" -> when (key) {
                    "edit_profile" -> "Profil bearbeiten"
                    "profile_name" -> "Profilname"
                    "maturity_rating" -> "Altersfreigabe"
                    "display_language" -> "Anzeigesprache"
                    "display_language_sub" -> "Ändern Sie die Textsprache in der App"
                    "audio_sub_lang" -> "Audio- und Untertitelsprachen"
                    "audio_sub_sub" -> "Bevorzugte Audio- und Untertitelspuren"
                    "subtitle_appearance" -> "Untertitel-Erscheinungsbild"
                    "subtitle_appearance_sub" -> "Schriftgröße, Farbe, Hintergrund anpassen"
                    "theme_accent" -> "Design-Akzentfarbe"
                    "player_controls" -> "Player- und Gestensteuerung"
                    "player_controls_sub" -> "Suchdauer, Wischgesten für Helligkeit und Lautstärke"
                    "network_dns" -> "Netzwerk- und DNS-Routing"
                    "downloads_storage" -> "Downloads und Speicher"
                    "downloads_storage_sub" -> "Nur WLAN-Downloads, Cache leeren"
                    "backup_restore" -> "Sicherung und Daten"
                    "backup_restore_sub" -> "Watchlist, Verlauf und Einstellungen exportieren"
                    "autoplay_next" -> "Nächste Folge automatisch"
                    "autoplay_next_sub" -> "Nächste Folge automatisch abspielen"
                    "autoplay_previews" -> "Vorschau automatisch abspielen"
                    "autoplay_previews_sub" -> "Videovorschauen auf der Startseite"
                    "auto_skip_intro" -> "Intro / Outro automatisch überspringen"
                    "auto_skip_intro_sub" -> "Openings und Endings überspringen"
                    "low_perf" -> "Energiesparmodus"
                    "low_perf_sub" -> "Schwere Animationen deaktivieren"
                    "delete_profile" -> "Profil löschen"
                    "save_changes" -> "Änderungen speichern"
                    else -> key
                }
                "portuguese", "pt" -> when (key) {
                    "edit_profile" -> "Editar perfil"
                    "profile_name" -> "Nome do perfil"
                    "maturity_rating" -> "Classificação etária"
                    "display_language" -> "Idioma de exibição"
                    "display_language_sub" -> "Alterar o idioma do texto no aplicativo"
                    "audio_sub_lang" -> "Idiomas de áudio e legendas"
                    "audio_sub_sub" -> "Áudio e legendas padrão"
                    "subtitle_appearance" -> "Aparência da legenda"
                    "subtitle_appearance_sub" -> "Personalizar tamanho, cor, fundo e estilo"
                    "theme_accent" -> "Cor de destaque do tema"
                    "player_controls" -> "Controles do reprodutor"
                    "player_controls_sub" -> "Tempo de busca, gestos de brilho e volume"
                    "network_dns" -> "Rede e roteamento DNS"
                    "downloads_storage" -> "Downloads e armazenamento"
                    "downloads_storage_sub" -> "Downloads apenas Wi-Fi, limpar cache"
                    "backup_restore" -> "Backup e gerenciamento de dados"
                    "backup_restore_sub" -> "Exportar e importar dados em JSON"
                    "autoplay_next" -> "Próximo episódio automático"
                    "autoplay_next_sub" -> "Carregar e reproduzir automaticamente"
                    "autoplay_previews" -> "Reprodução automática de prévias"
                    "autoplay_previews_sub" -> "Vídeos de prévia na página inicial"
                    "auto_skip_intro" -> "Pular abertura / encerramento auto"
                    "auto_skip_intro_sub" -> "Pular introduções automaticamente"
                    "low_perf" -> "Modo de baixo desempenho"
                    "low_perf_sub" -> "Desativar animações pesadas"
                    "delete_profile" -> "Excluir perfil"
                    "save_changes" -> "Salvar alterações"
                    else -> key
                }
                "indonesian", "id" -> when (key) {
                    "edit_profile" -> "Edit Profil"
                    "profile_name" -> "Nama Profil"
                    "maturity_rating" -> "Rating Usia"
                    "display_language" -> "Bahasa Tampilan"
                    "display_language_sub" -> "Ubah bahasa teks di seluruh aplikasi"
                    "audio_sub_lang" -> "Bahasa Audio & Subtitel"
                    "audio_sub_sub" -> "Preferensi trek audio dan subtitel"
                    "subtitle_appearance" -> "Tampilan Subtitel"
                    "subtitle_appearance_sub" -> "Sesuaikan ukuran font, warna, latar belakang"
                    "theme_accent" -> "Warna Aksen Tema"
                    "player_controls" -> "Kontrol Pemutar & Gestur"
                    "player_controls_sub" -> "Durasi seek, kontrol gestur kecerahan & volume"
                    "network_dns" -> "Jaringan & Perutean DNS"
                    "downloads_storage" -> "Unduhan & Penyimpanan"
                    "downloads_storage_sub" -> "Unduh hanya Wi-Fi, hapus cache"
                    "backup_restore" -> "Cadangan & Data"
                    "backup_restore_sub" -> "Ekspor/impor daftar tonton dan riwayat"
                    "autoplay_next" -> "Putar Otomatis Episode Berikutnya"
                    "autoplay_next_sub" -> "Muat dan putar episode berikutnya otomatis"
                    "autoplay_previews" -> "Putar Otomatis Pratinjau"
                    "autoplay_previews_sub" -> "Putar video pratinjau di beranda"
                    "auto_skip_intro" -> "Lewati Intro / Outro Otomatis"
                    "auto_skip_intro_sub" -> "Lewati lagu pembuka dan penutup otomatis"
                    "low_perf" -> "Mode Performa Rendah"
                    "low_perf_sub" -> "Nonaktifkan animasi berat untuk HP hemat daya"
                    "delete_profile" -> "Hapus Profil"
                    "save_changes" -> "Simpan Perubahan"
                    else -> key
                }
                "arabic", "ar" -> when (key) {
                    "edit_profile" -> "تعديل الملف الشخصي"
                    "profile_name" -> "اسم الملف الشخصي"
                    "maturity_rating" -> "تصنيف النضج"
                    "display_language" -> "لغة العرض"
                    "display_language_sub" -> "تغيير لغة النصوص في التطبيق"
                    "audio_sub_lang" -> "لغات الصوت والترجمة"
                    "audio_sub_sub" -> "المسار الصوتي والترجمات المفضلة"
                    "subtitle_appearance" -> "مظهر الترجمة"
                    "subtitle_appearance_sub" -> "تخصيص حجم الخط واللون والخلفية"
                    "theme_accent" -> "لون السمة المميز"
                    "player_controls" -> "عناصر تحكم المشغل"
                    "player_controls_sub" -> "مدة التخطي وإيماءات السطوع والصوت"
                    "network_dns" -> "الشبكة وتوجيه DNS"
                    "downloads_storage" -> "التنزيلات وسعة التخزين"
                    "downloads_storage_sub" -> "تنزيل عبر Wi-Fi فقط ومسح الذاكرة المؤقتة"
                    "backup_restore" -> "النسخ الاحتياطي والبيانات"
                    "backup_restore_sub" -> "تصدير واستيراد قائمة المشاهدة والإعدادات"
                    "autoplay_next" -> "التشغيل التلقائي للحلقة التالية"
                    "autoplay_next_sub" -> "تشغيل الحلقة التالية تلقائيًا"
                    "autoplay_previews" -> "التشغيل التلقائي للمقاطع الدعائية"
                    "autoplay_previews_sub" -> "تشغيل مقاطع الفيديو في الصفحة الرئيسية"
                    "auto_skip_intro" -> "تخطي المقدمة / الخاتمة تلقائيًا"
                    "auto_skip_intro_sub" -> "تخطي شارات البداية والنهاية"
                    "low_perf" -> "وضع الأداء المنخفض"
                    "low_perf_sub" -> "تعطيل التأثيرات البصرية للأجهزة الضعيفة"
                    "delete_profile" -> "حذف الملف الشخصي"
                    "save_changes" -> "حفظ التغييرات"
                    else -> key
                }
                "russian", "ru" -> when (key) {
                    "edit_profile" -> "Редактировать профиль"
                    "profile_name" -> "Имя профиля"
                    "maturity_rating" -> "Возрастной рейтинг"
                    "display_language" -> "Язык интерфейса"
                    "display_language_sub" -> "Изменить язык текста во всем приложении"
                    "audio_sub_lang" -> "Языки аудио и субтитров"
                    "audio_sub_sub" -> "Предпочитаемая озвучка и субтитры"
                    "subtitle_appearance" -> "Внешний вид субтитров"
                    "subtitle_appearance_sub" -> "Настройка размера, цвета, фона и стиля"
                    "theme_accent" -> "Акцентный цвет темы"
                    "player_controls" -> "Управление плеером"
                    "player_controls_sub" -> "Длительность перемотки и жесты"
                    "network_dns" -> "Сеть и DNS-маршрутизация"
                    "downloads_storage" -> "Загрузки и память"
                    "downloads_storage_sub" -> "Загрузка только по Wi-Fi, очистка кэша"
                    "backup_restore" -> "Резервное копирование"
                    "backup_restore_sub" -> "Экспорт и импорт закладок и истории"
                    "autoplay_next" -> "Автовоспроизведение серии"
                    "autoplay_next_sub" -> "Автоматически воспроизводить следующую серию"
                    "autoplay_previews" -> "Автовоспроизведение трейлеров"
                    "autoplay_previews_sub" -> "Воспроизводить видео на главном экране"
                    "auto_skip_intro" -> "Автопропуск опенинга / эндинга"
                    "auto_skip_intro_sub" -> "Автоматически пропускать заставки"
                    "low_perf" -> "Режим экономии ресурсов"
                    "low_perf_sub" -> "Отключение тяжелых эффектов и анимаций"
                    "delete_profile" -> "Удалить профиль"
                    "save_changes" -> "Сохранить изменения"
                    else -> key
                }
                else -> when (key) {
                    "edit_profile" -> "Edit Profile"
                    "profile_name" -> "Profile Name"
                    "maturity_rating" -> "Maturity Rating"
                    "display_language" -> "Display Language"
                    "display_language_sub" -> "Change the language of the text across the app ($displayLanguage)"
                    "audio_sub_lang" -> "Audio & Subtitle Languages"
                    "audio_sub_sub" -> "Preferred audio track and subtitles"
                    "subtitle_appearance" -> "Subtitle Appearance"
                    "subtitle_appearance_sub" -> "Customize font size, color, background & styling"
                    "theme_accent" -> "Theme Accent Color"
                    "player_controls" -> "Player & Gesture Controls"
                    "player_controls_sub" -> "Seek duration, brightness & volume swipe gestures"
                    "network_dns" -> "Network & DNS Routing"
                    "downloads_storage" -> "Downloads & Cache Storage"
                    "downloads_storage_sub" -> "Internal storage, Wi-Fi downloads, clear cache"
                    "backup_restore" -> "Backup & Data Management"
                    "backup_restore_sub" -> "Export watchlist, history & settings to JSON or restore backup"
                    "autoplay_next" -> "Autoplay Next Episode"
                    "autoplay_next_sub" -> "Automatically load and play next episode"
                    "autoplay_previews" -> "Autoplay Previews & Trailers"
                    "autoplay_previews_sub" -> "Play video previews on home banner cards"
                    "auto_skip_intro" -> "Auto-Skip Intro / Outro"
                    "auto_skip_intro_sub" -> "Automatically skip opening and ending sequences"
                    "low_perf" -> "Low Performance Mode"
                    "low_perf_sub" -> "Disable blur shaders and heavy animations for budget devices"
                    "delete_profile" -> "Delete Profile"
                    "save_changes" -> "Save Changes"
                    else -> key
                }
            }
        }

        val galleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                editProfileAvatarUrl = uri.toString()
            }
        }

        // Backup SAF Launchers
        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                try {
                    val jsonData = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.exportDataToJson(context)
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonData.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Backup exported successfully!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                try {
                    val stringBuilder = java.lang.StringBuilder()
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        java.io.BufferedReader(java.io.InputStreamReader(inputStream)).use { reader ->
                            var line: String? = reader.readLine()
                            while (line != null) {
                                stringBuilder.append(line)
                                line = reader.readLine()
                            }
                        }
                    }
                    val jsonContent = stringBuilder.toString()
                    val result = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.importDataFromJson(context, jsonContent)
                    if (result.isSuccess) {
                        profilesList = ProfileManager.getProfiles(context)
                        currentTheme = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getAppTheme(context)
                        Toast.makeText(context, "All app data and profiles restored successfully!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Import failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading backup file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Handle back press
        androidx.activity.compose.BackHandler(enabled = true) {
            if (editingProfile != null) {
                if (initialProfileForEditing != null) {
                    onDismiss()
                } else {
                    editingProfile = null
                }
            } else {
                onDismiss()
            }
        }

        Dialog(
            onDismissRequest = {
                if (editingProfile != null && initialProfileForEditing == null) {
                    editingProfile = null
                } else {
                    onDismiss()
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val view = androidx.compose.ui.platform.LocalView.current
            DisposableEffect(view) {
                var parent = view.parent
                while (parent != null) {
                    if (parent is android.app.Dialog) {
                        parent.window?.let { win ->
                            win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                            win.statusBarColor = android.graphics.Color.BLACK
                            win.navigationBarColor = android.graphics.Color.BLACK
                            win.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                            androidx.core.view.WindowCompat.getInsetsController(win, win.decorView).isAppearanceLightStatusBars = false
                        }
                        break
                    }
                    parent = parent.parent
                }
                try {
                    val providerClass = Class.forName("androidx.compose.ui.window.DialogWindowProvider")
                    if (providerClass.isInstance(view.parent)) {
                        val getWindowMethod = providerClass.getMethod("getWindow")
                        val win = getWindowMethod.invoke(view.parent) as? android.view.Window
                        win?.let { w ->
                            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                            w.statusBarColor = android.graphics.Color.BLACK
                            w.navigationBarColor = android.graphics.Color.BLACK
                            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                            androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightStatusBars = false
                        }
                    }
                } catch (_: Exception) {}

                val act = context as? android.app.Activity
                act?.window?.let { win ->
                    win.statusBarColor = android.graphics.Color.BLACK
                    win.navigationBarColor = android.graphics.Color.BLACK
                    androidx.core.view.WindowCompat.getInsetsController(win, win.decorView).isAppearanceLightStatusBars = false
                }
                onDispose {}
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (editingProfile != null) {
                    // EDIT PROFILE VIEW (Netflix Style Match - Opens from Top like Home & Detail)
                    val p = editingProfile!!
                    val editProfilePainter = remember(editProfileAvatarUrl) {
                        if (editProfileAvatarUrl.startsWith("android.resource://")) {
                            val resName = editProfileAvatarUrl.substringAfterLast("/")
                            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                            if (resId != 0) resId else editProfileAvatarUrl
                        } else {
                            editProfileAvatarUrl
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 10.dp, start = 20.dp, end = 20.dp, bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        // Top App Bar: Back arrow and Title "Edit Profile"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (initialProfileForEditing != null) onDismiss() else editingProfile = null
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = loc("edit_profile"),
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Avatar with Edit Badge on bottom-right
                        Box(
                            modifier = Modifier
                                .size(105.dp)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E1E22))
                                    .clickable { galleryLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (editProfileAvatarUrl.isNotEmpty()) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = editProfilePainter),
                                        contentDescription = "Avatar Preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(primaryColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = editProfileName.take(1).uppercase().ifEmpty { "P" },
                                            color = Color.White,
                                            fontSize = 36.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // White pencil badge at bottom-right
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 2.dp, y = 2.dp)
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .clickable { galleryLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Change Avatar",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Profile Name input box (Dark pill/card with rounded corners)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF26262B))
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = editProfileName,
                                onValueChange = { editProfileName = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    if (editProfileName.isEmpty()) {
                                        Text(
                                            text = loc("profile_name"),
                                            color = Color(0xFF8E8E93),
                                            fontSize = 16.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // COMPLETE SETTINGS CARDS
                        // 1. Display Language Card
                        EditProfileOptionCard(
                            icon = painterResource(id = R.drawable.ic_baseline_translate_24),
                            title = loc("display_language"),
                            subtitle = "${loc("display_language_sub")} ($displayLanguage).",
                            trailingType = CardTrailingType.CHEVRON,
                            onClick = { showLanguageDialog = true }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. Audio & Subtitle Languages Card
                        EditProfileOptionCard(
                            icon = SettingsIconType.REMEMBER_PREFS,
                            title = loc("audio_sub_lang"),
                            subtitle = "${loc("audio_sub_sub")} ($currentAudio, $currentSubMode).",
                            trailingType = CardTrailingType.CHEVRON,
                            onClick = { showAudioSubDialog = true }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 4. Subtitle Appearance Card (Dedicated In-App Styling Panel)
                        EditProfileOptionCard(
                            icon = SettingsIconType.ABOUT_DEVICE,
                            title = loc("subtitle_appearance"),
                            subtitle = "${loc("subtitle_appearance_sub")} (${subFontSize.toInt()}sp, $subColor).",
                            trailingType = CardTrailingType.CHEVRON,
                            onClick = { showSubtitleAppearanceDialog = true }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 5. Theme Accent Color Card
                        val themeTitle = when (currentTheme) {
                            "light_red", "red" -> "Crimson Red"
                            "cyan" -> "Ocean Cyan"
                            "emerald" -> "Emerald Green"
                            "amber" -> "Golden Amber"
                            else -> "Lavender Purple (Default)"
                        }
                        EditProfileOptionCard(
                            icon = SettingsIconType.APP_THEME,
                            title = loc("theme_accent"),
                            subtitle = themeTitle,
                            trailingType = CardTrailingType.CHEVRON,
                            onClick = { showThemeDialog = true }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 6. Streaming & Player Gesture Controls
                        val gestureSub = if (brightnessMode == "hidden" && volumeMode == "hidden") {
                            "Gestures: Hidden"
                        } else if (brightnessMode == "hidden") {
                            "Brightness: Hidden, Volume: Active"
                        } else if (volumeMode == "hidden") {
                            "Brightness: Active, Volume: Hidden"
                        } else {
                            "Swipe Gestures: Active (${seekDuration}s seek)"
                        }
                        EditProfileOptionCard(
                            icon = SettingsIconType.DOUBLE_TAP,
                            title = loc("player_controls"),
                            subtitle = gestureSub,
                            trailingType = CardTrailingType.CHEVRON,
                            onClick = { showPlayerControlsDialog = true }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 6b. Video Player Timeline Accent Color
                        val timelineTitle = when (timelineTheme) {
                            "red" -> "Netflix Crimson Red"
                            "cyan" -> "Ocean Cyan"
                            "emerald" -> "Emerald Green"
                            "amber" -> "Golden Amber"
                            "white" -> "Pure White"
                            else -> "Lavender Purple (Default)"
                        }
                        EditProfileOptionCard(
                            icon = SettingsIconType.TIMELINE_COLOR,
                            title = "Player Timeline Color",
                            subtitle = timelineTitle,
                            trailingType = CardTrailingType.CHEVRON,
                            onClick = { showTimelineColorDialog = true }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 7. Network & DNS Routing (Official Material WiFi fan icon)
                        val dnsTitle = when (dnsMode) {
                            "cloudflare" -> "Cloudflare DoH (1.1.1.1)"
                            "google" -> "Google Public DNS (8.8.8.8)"
                            "adguard" -> "AdGuard DNS (Ad-block)"
                            "quad9" -> "Quad9 DNS (9.9.9.9)"
                            else -> "System Default / Auto"
                        }
                        EditProfileOptionCard(
                            icon = SettingsIconType.WIFI,
                            title = loc("network_dns"),
                            subtitle = dnsTitle,
                            trailingType = CardTrailingType.CHEVRON,
                            onClick = { showDnsDialog = true }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 8. Downloads & Storage Manager
                        EditProfileOptionCard(
                            icon = SettingsIconType.SMART_DOWNLOADS,
                            title = loc("downloads_storage"),
                            subtitle = "${loc("downloads_storage_sub")} (${if (wifiOnlyDownloads) "Wi-Fi Only" else "Any Network"}).",
                            trailingType = CardTrailingType.CHEVRON,
                            onClick = { showDownloadsStorageDialog = true }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 9. Backup & Restore
                        EditProfileOptionCard(
                            icon = SettingsIconType.EXPORT,
                            title = loc("backup_restore"),
                            subtitle = loc("backup_restore_sub"),
                            trailingType = CardTrailingType.CHEVRON,
                            onClick = { showBackupRestoreDialog = true }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 10. Autoplay Next Episode Card
                        EditProfileOptionCard(
                            icon = SettingsIconType.EPISODE_VIEW,
                            title = loc("autoplay_next"),
                            subtitle = loc("autoplay_next_sub"),
                            trailingType = CardTrailingType.SWITCH,
                            switchChecked = autoplayNextEpisode,
                            onSwitchChanged = {
                                autoplayNextEpisode = it
                                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setAutoplayNextEpisodeEnabled(context, it)
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 11. Autoplay Previews & Trailers Card
                        EditProfileOptionCard(
                            icon = SettingsIconType.TRAILERS_TV,
                            title = loc("autoplay_previews"),
                            subtitle = loc("autoplay_previews_sub"),
                            trailingType = CardTrailingType.SWITCH,
                            switchChecked = autoplayPreviews,
                            onSwitchChanged = {
                                autoplayPreviews = it
                                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setAutoplayPreviewsEnabled(context, it)
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 12. Auto-Skip Intro / Outro Card
                        EditProfileOptionCard(
                            icon = SettingsIconType.SKIP_INTRO,
                            title = loc("auto_skip_intro"),
                            subtitle = loc("auto_skip_intro_sub"),
                            trailingType = CardTrailingType.SWITCH,
                            switchChecked = autoSkipIntro,
                            onSwitchChanged = {
                                autoSkipIntro = it
                                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setSkipIntroEnabled(context, it)
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 13. Low Performance Mode Card
                        EditProfileOptionCard(
                            icon = SettingsIconType.LOW_PERF,
                            title = loc("low_perf"),
                            subtitle = loc("low_perf_sub"),
                            trailingType = CardTrailingType.SWITCH,
                            switchChecked = lowPerfMode,
                            onSwitchChanged = {
                                lowPerfMode = it
                                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setLowPerformanceMode(context, it)
                            }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Save Changes Action Button
                        Button(
                            onClick = {
                                if (editProfileName.isNotBlank()) {
                                    val profiles = ProfileManager.getProfiles(context).toMutableList()
                                    val targetIdx = profiles.indexOfFirst { it.id == p.id }
                                    if (targetIdx != -1) {
                                        profiles[targetIdx] = UserProfile(p.id, editProfileName.trim(), editProfileAvatarUrl)
                                        ProfileManager.saveProfiles(context, profiles)
                                        profilesList = ProfileManager.getProfiles(context)
                                    }
                                    Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                                }
                                if (initialProfileForEditing != null) {
                                    onDismiss()
                                } else {
                                    editingProfile = null
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Text(loc("save_changes"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Delete Profile Button (Clean text button without trash icon)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (profilesList.size <= 1) {
                                        Toast.makeText(context, "Cannot delete the only profile.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        showDeleteConfirmDialog = true
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = loc("delete_profile"),
                                color = Color(0xFFE2E2E6),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // -------------------------------------------------------------
                    // MODALS FOR EDIT PROFILE SETTINGS
                    // -------------------------------------------------------------

                    // 1. Subtitle Appearance Modal with Live Preview
                    if (showSubtitleAppearanceDialog) {
                        AlertDialog(
                            onDismissRequest = { showSubtitleAppearanceDialog = false },
                            title = { Text("Subtitle Appearance", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Live Preview Card
                                    Text("Preview:", color = Color.Gray, fontSize = 12.5.sp)
                                    val previewTextColor = when (subColor) {
                                        "Yellow" -> Color(0xFFFFEB3B)
                                        "Cyan" -> Color(0xFF00E5FF)
                                        "Green", "Light Green" -> Color(0xFF76FF03)
                                        else -> Color.White
                                    }
                                    val previewBgAlpha = subBgOpacity / 100f

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF141418)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.Black.copy(alpha = previewBgAlpha))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Ore wa Kaizoku Ou ni naru Otoko da!",
                                                color = previewTextColor,
                                                fontSize = (subFontSize.coerceIn(12f, 24f)).sp,
                                                fontWeight = FontWeight.Bold,
                                                style = if (subEdgeStyle == "Outline") TextStyle(
                                                    shadow = androidx.compose.ui.graphics.Shadow(
                                                        color = Color.Black,
                                                        offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                                        blurRadius = 3f
                                                    )
                                                ) else TextStyle()
                                            )
                                        }
                                    }

                                    // Font Size
                                    Text("Font Size:", color = Color.Gray, fontSize = 12.5.sp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(14f to "Small", 18f to "Medium", 22f to "Large", 26f to "Huge").forEach { (sz, label) ->
                                            val isSel = (subFontSize == sz)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSel) Color(0xFFD0BCFF) else Color(0xFF26262B))
                                                    .clickable { subFontSize = sz }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(label, color = if (isSel) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // Font Color (Properly sized chips so Green fits without wrapping)
                                    Text("Text Color:", color = Color.Gray, fontSize = 12.5.sp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("White", "Yellow", "Cyan", "Green").forEach { col ->
                                            val isSel = (subColor == col || (col == "Green" && subColor == "Light Green"))
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSel) Color(0xFFD0BCFF) else Color(0xFF26262B))
                                                    .clickable { subColor = col }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(col, color = if (isSel) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                            }
                                        }
                                    }

                                    // Background Opacity
                                    Text("Background Opacity:", color = Color.Gray, fontSize = 12.5.sp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(0 to "0%", 25 to "25%", 50 to "50%", 75 to "75%", 100 to "100%").forEach { (op, label) ->
                                            val isSel = (subBgOpacity == op)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSel) Color(0xFFD0BCFF) else Color(0xFF26262B))
                                                    .clickable { subBgOpacity = op }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(label, color = if (isSel) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // Text Edge Style
                                    Text("Text Edge Style:", color = Color.Gray, fontSize = 12.5.sp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("None", "Drop Shadow", "Outline").forEach { edge ->
                                            val isSel = (subEdgeStyle == edge)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSel) Color(0xFFD0BCFF) else Color(0xFF26262B))
                                                    .clickable { subEdgeStyle = edge }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(edge, color = if (isSel) Color.Black else Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE)
                                            .edit()
                                            .putFloat("sub_font_size", subFontSize)
                                            .putString("sub_color", subColor)
                                            .putInt("sub_bg_opacity", subBgOpacity)
                                            .putString("sub_edge_style", subEdgeStyle)
                                            .apply()
                                        showSubtitleAppearanceDialog = false
                                        Toast.makeText(context, "Subtitle appearance saved", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                                ) {
                                    Text("Save & Apply", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSubtitleAppearanceDialog = false }) {
                                    Text("Cancel", color = Color.Gray)
                                }
                            },
                            containerColor = Color(0xFF1E1E24)
                        )
                    }

                    // 2. Downloads & Storage Manager Modal
                    if (showDownloadsStorageDialog) {
                        AlertDialog(
                            onDismissRequest = { showDownloadsStorageDialog = false },
                            title = { Text("Downloads & Storage", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Download Over Wi-Fi Only", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                            Text("Conserve mobile cellular data", color = Color.Gray, fontSize = 12.sp)
                                        }
                                        Switch(
                                            checked = wifiOnlyDownloads,
                                            onCheckedChange = {
                                                wifiOnlyDownloads = it
                                                context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE)
                                                    .edit().putBoolean("wifi_only_downloads", it).apply()
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.Black,
                                                checkedTrackColor = primaryColor,
                                                uncheckedThumbColor = Color.LightGray,
                                                uncheckedTrackColor = Color(0xFF33333C)
                                            )
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Smart Downloads", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                            Text("Automatically manage completed episodes", color = Color.Gray, fontSize = 12.sp)
                                        }
                                        Switch(
                                            checked = smartDownloads,
                                            onCheckedChange = {
                                                smartDownloads = it
                                                context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE)
                                                    .edit().putBoolean("smart_downloads", it).apply()
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.Black,
                                                checkedTrackColor = primaryColor,
                                                uncheckedThumbColor = Color.LightGray,
                                                uncheckedTrackColor = Color(0xFF33333C)
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Button(
                                        onClick = {
                                            try {
                                                context.cacheDir.deleteRecursively()
                                                context.externalCacheDir?.deleteRecursively()
                                                Toast.makeText(context, "App cache & temporary images cleared successfully!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A32)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Clear Cache & Temporary Files", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showDownloadsStorageDialog = false }) { Text("Close", color = primaryColor) } },
                            containerColor = Color(0xFF1E1E24)
                        )
                    }

                    // 3. Backup & Restore Modal
                    if (showBackupRestoreDialog) {
                        AlertDialog(
                            onDismissRequest = { showBackupRestoreDialog = false },
                            title = { Text("Backup & Data Management", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text("Export your profile details, library watchlist, and playback history to a backup file, or restore from a previous backup.", color = Color.LightGray, fontSize = 13.5.sp)
                                    Button(
                                        onClick = {
                                            showBackupRestoreDialog = false
                                            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                                            exportLauncher.launch("firefly_backup_$timeStamp.json")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Export Data to JSON", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            showBackupRestoreDialog = false
                                            importLauncher.launch("application/json")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A32)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Import & Restore Data", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showBackupRestoreDialog = false }) { Text("Close", color = primaryColor) } },
                            containerColor = Color(0xFF1E1E24)
                        )
                    }

                    // 4. Theme Modal
                    if (showThemeDialog) {
                        AlertDialog(
                            onDismissRequest = { showThemeDialog = false },
                            title = { Text("Choose Theme Color", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        "lavender" to "Lavender Purple (Default)",
                                        "light_red" to "Crimson Red",
                                        "cyan" to "Ocean Cyan",
                                        "emerald" to "Emerald Green",
                                        "amber" to "Golden Amber"
                                    ).forEach { (thm, label) ->
                                        val isSel = currentTheme == thm
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) Color(0xFF2E2E36) else Color.Transparent)
                                                .clickable {
                                                    currentTheme = thm
                                                    com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setAppTheme(context, thm)
                                                    showThemeDialog = false
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(label, color = if (isSel) primaryColor else Color.White, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Close", color = primaryColor) } },
                            containerColor = Color(0xFF1E1E24)
                        )
                    }

                    // 5. Language Modal
                    if (showLanguageDialog) {
                        AlertDialog(
                            onDismissRequest = { showLanguageDialog = false },
                            title = { Text("Display Language", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("English", "Japanese", "Hindi", "Spanish", "French", "German", "Portuguese", "Indonesian", "Arabic", "Russian").forEach { lang ->
                                        val isSel = displayLanguage == lang
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) Color(0xFF2E2E36) else Color.Transparent)
                                                .clickable {
                                                    displayLanguage = lang
                                                    com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setDisplayLanguage(context, lang)
                                                    showLanguageDialog = false
                                                    Toast.makeText(context, "Display Language: $lang", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(lang, color = if (isSel) primaryColor else Color.White, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("Close", color = primaryColor) } },
                            containerColor = Color(0xFF1E1E24)
                        )
                    }

                    // 7. Audio & Subtitle Language Modal
                    if (showAudioSubDialog) {
                        AlertDialog(
                            onDismissRequest = { showAudioSubDialog = false },
                            title = { Text("Default Audio & Subtitles", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Preferred Audio Track:", color = Color.Gray, fontSize = 13.sp)
                                    listOf("Japanese (Original)", "English", "Hindi").forEach { aud ->
                                        val isSel = currentAudio.contains(aud)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) Color(0xFF2E2E36) else Color.Transparent)
                                                .clickable {
                                                    currentAudio = aud
                                                    com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setLastUsedAudio(context, aud)
                                                }
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(aud, color = if (isSel) primaryColor else Color.White, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Preferred Subtitles:", color = Color.Gray, fontSize = 13.sp)
                                    listOf("English (VTT)", "Hard Sub", "Off").forEach { sub ->
                                        val isSel = currentSubMode == sub
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) Color(0xFF2E2E36) else Color.Transparent)
                                                .clickable {
                                                    currentSubMode = sub
                                                    com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setLastUsedSubMode(context, sub)
                                                }
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(sub, color = if (isSel) primaryColor else Color.White, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showAudioSubDialog = false }) { Text("Done", color = primaryColor) } },
                            containerColor = Color(0xFF1E1E24)
                        )
                    }

                    // 8. Player Controls Modal (Seek interval + Brightness/Volume swipe toggles)
                    if (showPlayerControlsDialog) {
                        AlertDialog(
                            onDismissRequest = { showPlayerControlsDialog = false },
                            title = { Text("Player & Gesture Controls", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text("Double-tap & Button Seek Interval:", color = Color.Gray, fontSize = 13.sp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(5, 10, 15, 30).forEach { sec ->
                                            val isSel = seekDuration == sec
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSel) primaryColor else Color(0xFF26262E))
                                                    .clickable {
                                                        seekDuration = sec
                                                        com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setSeekDuration(context, sec)
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${sec}s",
                                                    color = if (isSel) Color.Black else Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Swipe Gestures on Video Player:", color = Color.Gray, fontSize = 13.sp)

                                    // Brightness gesture toggle
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF26262E))
                                            .clickable {
                                                val newMode = if (brightnessMode == "gesture") "hidden" else "gesture"
                                                brightnessMode = newMode
                                                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setBrightnessMode(context, newMode)
                                            }
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Brightness Gesture Bar", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text(if (brightnessMode == "gesture") "Shown on left swipe" else "Hidden", color = Color(0xFF9E9EA4), fontSize = 12.sp)
                                        }
                                        Switch(
                                            checked = brightnessMode == "gesture",
                                            onCheckedChange = { isChecked ->
                                                val newMode = if (isChecked) "gesture" else "hidden"
                                                brightnessMode = newMode
                                                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setBrightnessMode(context, newMode)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.Black,
                                                checkedTrackColor = primaryColor,
                                                uncheckedThumbColor = Color.LightGray,
                                                uncheckedTrackColor = Color(0xFF33333C)
                                            )
                                        )
                                    }

                                    // Volume gesture toggle
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF26262E))
                                            .clickable {
                                                val newMode = if (volumeMode == "gesture") "hidden" else "gesture"
                                                volumeMode = newMode
                                                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setVolumeMode(context, newMode)
                                            }
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Volume Gesture Bar", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text(if (volumeMode == "gesture") "Shown on right swipe" else "Hidden", color = Color(0xFF9E9EA4), fontSize = 12.sp)
                                        }
                                        Switch(
                                            checked = volumeMode == "gesture",
                                            onCheckedChange = { isChecked ->
                                                val newMode = if (isChecked) "gesture" else "hidden"
                                                volumeMode = newMode
                                                com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setVolumeMode(context, newMode)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.Black,
                                                checkedTrackColor = primaryColor,
                                                uncheckedThumbColor = Color.LightGray,
                                                uncheckedTrackColor = Color(0xFF33333C)
                                            )
                                        )
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showPlayerControlsDialog = false }) { Text("Done", color = primaryColor) } },
                            containerColor = Color(0xFF1E1E24)
                        )
                    }

                    // 8b. Video Player Timeline Color Picker Modal
                    if (showTimelineColorDialog) {
                        AlertDialog(
                            onDismissRequest = { showTimelineColorDialog = false },
                            title = { Text("Player Timeline Accent Color", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    listOf(
                                        "lavender" to ("Lavender Purple (Default)" to Color(0xFFD0BCFF)),
                                        "red" to ("Netflix Crimson Red" to Color(0xFFE50914)),
                                        "cyan" to ("Ocean Cyan" to Color(0xFF06B6D4)),
                                        "emerald" to ("Emerald Green" to Color(0xFF10B981)),
                                        "amber" to ("Golden Amber" to Color(0xFFF59E0B)),
                                        "white" to ("Pure White" to Color(0xFFFFFFFF))
                                    ).forEach { (themeKey, pair) ->
                                        val (label, swatchColor) = pair
                                        val isSel = timelineTheme == themeKey
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) Color(0xFF2E2E38) else Color.Transparent)
                                                .clickable {
                                                    timelineTheme = themeKey
                                                    com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setPlayerTimelineTheme(context, themeKey)
                                                    showTimelineColorDialog = false
                                                    Toast.makeText(context, "Timeline Color: $label", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(swatchColor)
                                                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Text(
                                                text = label,
                                                color = if (isSel) Color.White else Color(0xFFCCCCCC),
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showTimelineColorDialog = false }) { Text("Cancel", color = primaryColor) } },
                            containerColor = Color(0xFF1E1E24)
                        )
                    }

                    // 9. DNS Modal
                    if (showDnsDialog) {
                        AlertDialog(
                            onDismissRequest = { showDnsDialog = false },
                            title = { Text("DNS & Network Routing", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        "system" to "System Default (Auto)",
                                        "cloudflare" to "Cloudflare DoH (1.1.1.1)",
                                        "google" to "Google Public DNS (8.8.8.8)",
                                        "adguard" to "AdGuard DNS (Ad-block)",
                                        "quad9" to "Quad9 DNS (9.9.9.9)"
                                    ).forEach { (mode, label) ->
                                        val isSel = dnsMode == mode
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) Color(0xFF2E2E36) else Color.Transparent)
                                                .clickable {
                                                    dnsMode = mode
                                                    com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.setDnsMode(context, mode)
                                                    showDnsDialog = false
                                                    Toast.makeText(context, "DNS Routing: $label", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(label, color = if (isSel) primaryColor else Color.White, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showDnsDialog = false }) { Text("Close", color = primaryColor) } },
                            containerColor = Color(0xFF1E1E24)
                        )
                    }

                    // 10. Delete Profile Confirm Modal
                    if (showDeleteConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirmDialog = false },
                            title = { Text("Delete Profile", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = { Text("Are you sure you want to delete profile \"${p.name}\"? This action cannot be undone.", color = Color.LightGray) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        ProfileManager.deleteProfile(context, p.id)
                                        profilesList = ProfileManager.getProfiles(context)
                                        showDeleteConfirmDialog = false
                                        editingProfile = null
                                        if (initialProfileForEditing != null) onDismiss()
                                    }
                                ) {
                                    Text("Delete", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                                    Text("Cancel", color = Color.Gray)
                                }
                            },
                            containerColor = Color(0xFF1E1E24)
                        )
                    }
                } else {
                    // MAIN PROFILES SELECTION VIEW ("Who's watching?") - Centered layout
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 20.dp)
                        ) {
                            Text(
                                text = "Who's watching?",
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 28.dp)
                            )

                            if (profileLimitWarning) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .background(Color(0xFFE53935).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFE53935), RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = "Limit", tint = Color(0xFFE53935))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Maximum profile limit reached (Max 3 profiles allowed).",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Profiles Grid
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .heightIn(max = 160.dp)
                            ) {
                                items(profilesList.size) { index ->
                                    val profile = profilesList[index]
                                    val dialogProfilePainter = remember(profile.avatarUrl) {
                                        if (profile.avatarUrl.startsWith("android.resource://")) {
                                            val resName = profile.avatarUrl.substringAfterLast("/")
                                            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                                            if (resId != 0) resId else profile.avatarUrl
                                        } else {
                                            profile.avatarUrl
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(75.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF1F1F1F)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Clicking the main profile avatar selects and navigates
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable { onProfileSelected(profile.id) }
                                            ) {
                                                if (profile.avatarUrl.isNotEmpty()) {
                                                    Image(
                                                        painter = rememberAsyncImagePainter(model = dialogProfilePainter),
                                                        contentDescription = profile.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(primaryColor),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = profile.name.take(1).uppercase(),
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 32.sp
                                                        )
                                                    }
                                                }
                                            }

                                            // Pencil/Edit button overlay on the top right
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.7f))
                                                    .clickable {
                                                        editingProfile = profile
                                                        editProfileName = profile.name
                                                        editProfileAvatarUrl = profile.avatarUrl
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Profile",
                                                    tint = primaryColor,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = profile.name,
                                            color = Color.LightGray,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            if (showAddProfile) {
                                OutlinedTextField(
                                    value = newProfileName,
                                    onValueChange = { newProfileName = it },
                                    label = { Text("Profile Name", color = Color.Gray) },
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .padding(bottom = 12.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = primaryColor,
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(0.8f),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(onClick = { showAddProfile = false }) {
                                        Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            if (newProfileName.isNotBlank()) {
                                                ProfileManager.addProfile(context, newProfileName, "")
                                                profilesList = ProfileManager.getProfiles(context)
                                                newProfileName = ""
                                                showAddProfile = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("Add Profile", fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                val isAtMaxLimit = profilesList.size >= 3
                                OutlinedButton(
                                    onClick = {
                                        if (isAtMaxLimit) {
                                            profileLimitWarning = true
                                        } else {
                                            showAddProfile = true
                                            profileLimitWarning = false
                                        }
                                    },
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, 
                                        if (isAtMaxLimit) Color.DarkGray else Color.Gray
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth(0.7f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isAtMaxLimit) Color.Gray else Color.LightGray
                                    ),
                                    enabled = true
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add, 
                                        contentDescription = "Add Profile",
                                        tint = (if (isAtMaxLimit) Color.Gray else Color.LightGray).copy(alpha = if (isAtMaxLimit) 0.4f else 1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Add Profile", 
                                        fontWeight = FontWeight.Bold,
                                        color = (if (isAtMaxLimit) Color.Gray else Color.LightGray).copy(alpha = if (isAtMaxLimit) 0.4f else 1f)
                                    )
                                }
                            }

                            // Button to close selection screen
                            if (!showAddProfile) {
                                Spacer(modifier = Modifier.height(18.dp))
                                TextButton(onClick = onDismiss) {
                                    Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openDetailsPage(anilistId: Int, brief: AnimeBrief? = null) {
        val intent = Intent(this, AnimeBoxDetailActivity::class.java).apply {
            putExtra("anilistId", anilistId)
            if (brief != null) {
                putExtra("initialTitle", brief.title)
                putExtra("initialCoverUrl", brief.coverUrl)
                putExtra("initialBannerUrl", brief.bannerUrl)
                putExtra("initialDescription", brief.description)
                putExtra("initialGenres", ArrayList(brief.genres))
                putExtra("initialScore", brief.averageScore)
                putExtra("initialEpisodesCount", brief.episodes)
            }
        }
        startActivity(intent)
    }

    private suspend fun fetchAndLaunchPlayerFromHistory(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String,
        coverUrl: String,
        showCoverUrl: String,
        totalEpisodes: Int
    ) {
        if (AnimeBoxPlayerActivity.isCurrentlyInPip) {
            Toast.makeText(this, "Please close Picture-in-Picture mode first to play another episode", Toast.LENGTH_SHORT).show()
            return
        }

        // Direct offline playback if episode is already downloaded
        val downloadedEp = com.lagradost.cloudstream3.ui.animebox.download.AnimeDownloadManager.getInstance(this@AnimeBoxMainActivity).getDownloadedEpisode(anilistId, episodeNum)
        if (downloadedEp != null && downloadedEp.localFilePath.isNotEmpty() && java.io.File(downloadedEp.localFilePath).exists()) {
            val playIntent = Intent(this@AnimeBoxMainActivity, AnimeBoxPlayerActivity::class.java).apply {
                putExtra("localFilePath", downloadedEp.localFilePath)
                putExtra("isOffline", true)
                putExtra("animeTitle", animeTitle)
                putExtra("anilistId", anilistId)
                putExtra("episode", episodeNum)
                putExtra("showCoverUrl", showCoverUrl)
                putExtra("coverUrl", coverUrl)
                putExtra("subtitleUrl", downloadedEp.localSubtitlePath)
                putExtra("subtitlesJson", downloadedEp.subtitlesJson)
                putExtra("introStart", downloadedEp.introStart)
                putExtra("introEnd", downloadedEp.introEnd)
                putExtra("outroStart", downloadedEp.outroStart)
                putExtra("outroEnd", downloadedEp.outroEnd)
                putExtra("streamType", downloadedEp.streamType)
                putExtra("fromContinueWatching", true)
            }
            startActivity(playIntent)
            return
        }

        val type = if (com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isRememberPlaybackPrefsEnabled(this@AnimeBoxMainActivity)) {
            val lastAud = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getLastUsedAudio(this@AnimeBoxMainActivity)
            val lastSub = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getLastUsedSubMode(this@AnimeBoxMainActivity)
            if (lastSub == "Hard Sub") "hardsub"
            else if (lastAud == "English") "dub"
            else if (lastAud == "Hindi") "hindi"
            else "sub"
        } else {
            "sub"
        }

        var streamInfo = com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(
            this@AnimeBoxMainActivity, anilistId, episodeNum, type
        )
        var isDubFallback = false
        if (streamInfo == null && type != "sub") {
            streamInfo = com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(
                this@AnimeBoxMainActivity, anilistId, episodeNum, "sub"
            )
            if (streamInfo != null) {
                isDubFallback = true
            }
        }

        if (streamInfo != null && (streamInfo["hls"] as? String)?.isNotEmpty() == true) {
            val epCount = if (totalEpisodes > 0) totalEpisodes else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val resp = com.lagradost.cloudstream3.ui.animebox.api.AniListClient.getAnimeDetails(anilistId)
                    if (resp != null) {
                        val json = org.json.JSONObject(resp)
                        json.getJSONObject("data").getJSONObject("Media").optInt("episodes", 0)
                    } else 0
                } catch (e: Exception) { 0 }
            }

            if (AnimeBoxPlayerActivity.isCurrentlyInPip) {
                Toast.makeText(this, "Please close Picture-in-Picture mode first to play another episode", Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent(this, AnimeBoxPlayerActivity::class.java).apply {
                putExtra("hlsUrl", (streamInfo["hls"] as? String) ?: "")
                putExtra("referer", (streamInfo["referer"] as? String) ?: "https://megaplay.buzz/")
                putExtra("subtitleUrl", (streamInfo["subtitle"] as? String) ?: "")
                putExtra("introStart", (streamInfo["introStart"] as? Number)?.toLong() ?: 0L)
                putExtra("introEnd", (streamInfo["introEnd"] as? Number)?.toLong() ?: 0L)
                putExtra("outroStart", (streamInfo["outroStart"] as? Number)?.toLong() ?: 0L)
                putExtra("outroEnd", (streamInfo["outroEnd"] as? Number)?.toLong() ?: 0L)
                putExtra("backupHls", (streamInfo["backupHls"] as? String) ?: "")
                putExtra("backupProvider", (streamInfo["backupProvider"] as? String) ?: "")
                putExtra("hindiStreamsJson", (streamInfo["hindiStreamsJson"] as? String) ?: "")
                putExtra("streamType", if (isDubFallback) "sub" else type)
                if (isDubFallback) {
                    putExtra("showAudioFallbackWarning", true)
                    putExtra("requestedAudioType", type)
                }
                putExtra("fromContinueWatching", true)
                putExtra("anilistId", anilistId)
                putExtra("episode", episodeNum)
                putExtra("animeTitle", animeTitle)
                putExtra("coverUrl", coverUrl)
                putExtra("showCoverUrl", showCoverUrl)
                putExtra("totalEpisodes", epCount)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Failed to load stream. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseTrending(jsonString: String, allowUpcoming: Boolean = false): List<AnimeBrief> {
        val list = mutableListOf<AnimeBrief>()
        try {
            val obj = JSONObject(jsonString)
            val mediaArray = obj.getJSONObject("data").getJSONObject("Page").getJSONArray("media")
            for (i in 0 until mediaArray.length()) {
                val media = mediaArray.getJSONObject(i)
                if (com.lagradost.cloudstream3.ui.animebox.api.AniListClient.isBlockedMedia(media)) continue
                val id = media.getInt("id")
                val titleObj = media.getJSONObject("title")
                val title = if (titleObj.has("english") && !titleObj.isNull("english")) {
                    titleObj.getString("english")
                } else {
                    titleObj.getString("romaji")
                }
                val coverUrl = media.getJSONObject("coverImage").getString("large")
                val bannerUrl = if (media.has("bannerImage") && !media.isNull("bannerImage")) {
                    media.getString("bannerImage")
                } else ""
                
                val rawDesc = if (media.has("description") && !media.isNull("description")) {
                    media.getString("description")
                } else ""
                val cleanDesc = rawDesc.replace(Regex("<[^>]*>"), "")

                val genresList = mutableListOf<String>()
                if (media.has("genres") && !media.isNull("genres")) {
                    val genresArray = media.getJSONArray("genres")
                    for (j in 0 until genresArray.length()) {
                        genresList.add(genresArray.getString(j))
                    }
                }

                val averageScore = if (media.has("averageScore") && !media.isNull("averageScore")) {
                    media.getInt("averageScore")
                } else 0

                val episodes = if (media.has("episodes") && !media.isNull("episodes")) {
                    media.getInt("episodes")
                } else 0

                // Skip entries with no title or cover image (prevents empty grid cells)
                if (title.isBlank() || coverUrl.isBlank()) continue

                val isAdult = if (media.has("isAdult") && !media.isNull("isAdult")) {
                    media.getBoolean("isAdult")
                } else false
                if (isAdult) continue

                var rYear = 0
                var rMonth = 0
                var rDay = 0
                if (media.has("startDate") && !media.isNull("startDate")) {
                    val startObj = media.getJSONObject("startDate")
                    rYear = if (startObj.has("year") && !startObj.isNull("year")) startObj.getInt("year") else 0
                    rMonth = if (startObj.has("month") && !startObj.isNull("month")) startObj.getInt("month") else 0
                    rDay = if (startObj.has("day") && !startObj.isNull("day")) startObj.getInt("day") else 0
                }

                val cal = java.util.Calendar.getInstance()
                val curYear = cal.get(java.util.Calendar.YEAR)
                val curMonth = cal.get(java.util.Calendar.MONTH) + 1
                val curDay = cal.get(java.util.Calendar.DAY_OF_MONTH)

                val isFuture = if (rYear > curYear) {
                    true
                } else if (rYear == curYear) {
                    if (rMonth > curMonth) {
                        true
                    } else if (rMonth == curMonth) {
                        rDay > curDay
                    } else {
                        false
                    }
                } else {
                    false
                }

                val mediaStatus = if (media.has("status") && !media.isNull("status")) media.getString("status") else ""
                
                // Strict upcoming check: filter out future releases & NOT_YET_RELEASED unless allowUpcoming is explicitly true
                if (!allowUpcoming && (isFuture || mediaStatus == "NOT_YET_RELEASED" || mediaStatus.equals("UPCOMING", ignoreCase = true) || mediaStatus.equals("NOT_YET_AIRED", ignoreCase = true))) {
                    continue
                }

                list.add(AnimeBrief(id, title, coverUrl, bannerUrl, cleanDesc, genresList, averageScore, episodes = episodes, status = mediaStatus, releaseYear = rYear, releaseMonth = rMonth, releaseDay = rDay))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun parseHasNextPage(jsonString: String): Boolean {
        return try {
            val obj = JSONObject(jsonString)
            obj.getJSONObject("data").getJSONObject("Page").getJSONObject("pageInfo").getBoolean("hasNextPage")
        } catch (e: Exception) {
            false
        }
    }
}

// Custom Home Icon - Outlined house with wider roof span and spacious base
val CustomHomeIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "CustomHome",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
        strokeLineWidth = 2.4f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
    ) {
        // Pointed wider roof triangle with extended overhang eaves
        moveTo(1.5f, 11.5f)
        lineTo(12f, 2.8f)
        lineTo(22.5f, 11.5f)
        
        // Wider house walls and spacious base
        moveTo(4.2f, 9.5f)
        lineTo(4.8f, 21.0f)
        curveTo(4.8f, 21.3f, 5.1f, 21.6f, 5.5f, 21.6f)
        horizontalLineTo(18.5f)
        curveTo(18.9f, 21.6f, 19.2f, 21.3f, 19.2f, 21.0f)
        lineTo(19.8f, 9.5f)
    }.build()

// Custom My List (Shopping Bag) Icon as shown in screenshot #2
val CustomMyListIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "CustomMyList",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
        strokeLineWidth = 2.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
    ) {
        // Outer bag body with rounded corners
        moveTo(6f, 9f)
        curveTo(4.9f, 9f, 4f, 9.9f, 4f, 11f)
        verticalLineTo(18.5f)
        curveTo(4f, 20.43f, 5.57f, 22f, 7.5f, 22f)
        horizontalLineTo(16.5f)
        curveTo(18.43f, 22f, 20f, 20.43f, 20f, 18.5f)
        verticalLineTo(11f)
        curveTo(20f, 9.9f, 19.1f, 9f, 18f, 9f)
        close()
        // Arch handle
        moveTo(8.5f, 12f)
        verticalLineTo(6.5f)
        curveTo(8.5f, 4.57f, 10.07f, 3f, 12f, 3f)
        curveTo(13.93f, 3f, 15.5f, 4.57f, 15.5f, 6.5f)
        verticalLineTo(12f)
    }.build()

val CustomDownloadsNavbarIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "CustomDownloadsNavbar",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
        strokeLineWidth = 2.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
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

val BottomNavShoppingBagIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = CustomMyListIcon

// Custom Mic icon built programmatically with crisp vector paths
val CustomMicVector: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "CustomMic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.White),
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
    ) {
        // Inner capsule: width 6 (9 to 15), height 11 (3 to 14)
        moveTo(12f, 14f)
        curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
        verticalLineTo(5f)
        curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
        curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
        verticalLineTo(11f)
        curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
        close()
        // Cradle arc & stand
        moveTo(17.5f, 11f)
        curveTo(17.5f, 13.85f, 15.1f, 16.15f, 12.8f, 16.45f)
        verticalLineTo(19.5f)
        horizontalLineTo(15f)
        verticalLineTo(21f)
        horizontalLineTo(9f)
        verticalLineTo(19.5f)
        horizontalLineTo(11.2f)
        verticalLineTo(16.45f)
        curveTo(8.9f, 16.15f, 6.5f, 13.85f, 6.5f, 11f)
        horizontalLineTo(8f)
        curveTo(8f, 13.2f, 9.8f, 15f, 12f, 15f)
        curveTo(14.2f, 15f, 16f, 13.2f, 16f, 11f)
        horizontalLineTo(17.5f)
        close()
    }.build()

// Custom Search Icon with prominent large lens circle and very short handle stem
val CustomSearchIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "CustomSearch",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
        strokeLineWidth = 2.4f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
    ) {
        // Large prominent lens circle centered at (10.5, 10.5) with radius 7.5
        moveTo(18f, 10.5f)
        curveTo(18f, 14.64f, 14.64f, 18f, 10.5f, 18f)
        curveTo(6.36f, 18f, 3f, 14.64f, 3f, 10.5f)
        curveTo(3f, 6.36f, 6.36f, 3f, 10.5f, 3f)
        curveTo(14.64f, 3f, 18f, 6.36f, 18f, 10.5f)
        close()
        // Very short diagonal handle stem
        moveTo(16.2f, 16.2f)
        lineTo(19.8f, 19.8f)
    }.build()

// Custom Modern Notification Bell Icon with sleek curves, widened flared rim, and clapper
val CustomNotificationIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "CustomNotification",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
        strokeLineWidth = 2.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
    ) {
        // Bell Dome & Flared Rim (clean smooth arch)
        moveTo(19f, 16.5f)
        curveTo(18.2f, 15.2f, 17.5f, 13.8f, 17.5f, 10.2f)
        curveTo(17.5f, 7.0f, 15.1f, 4.4f, 12f, 4.4f)
        curveTo(8.9f, 4.4f, 6.5f, 7.0f, 6.5f, 10.2f)
        curveTo(6.5f, 13.8f, 5.8f, 15.2f, 5f, 16.5f)
        lineTo(3.8f, 17.5f)
        lineTo(20.2f, 17.5f)
        close()
        // Bottom Clapper Ball
        moveTo(9.5f, 18.5f)
        curveTo(9.9f, 20.0f, 10.9f, 21.0f, 12f, 21.0f)
        curveTo(13.1f, 21.0f, 14.1f, 20.0f, 14.5f, 18.5f)
    }.build()

// Custom Segmented Play Icon (matching user reference image for trailer play)
val CustomSegmentedPlayIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "CustomSegmentedPlay",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
        strokeLineWidth = 2.3f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
    ) {
        // Upper Segment: left mid -> top corner -> right apex -> bottom mid
        moveTo(6.5f, 9.8f)
        lineTo(6.5f, 6.8f)
        curveTo(6.5f, 5.2f, 8.2f, 4.2f, 9.6f, 5.0f)
        lineTo(19.2f, 10.8f)
        curveTo(20.5f, 11.6f, 20.5f, 13.4f, 19.2f, 14.2f)
        lineTo(13.5f, 17.6f)

        // Lower Segment: bottom right-mid -> bottom-left corner -> left lower-mid
        moveTo(11.2f, 19.0f)
        lineTo(9.6f, 20.0f)
        curveTo(8.2f, 20.8f, 6.5f, 19.8f, 6.5f, 18.2f)
        lineTo(6.5f, 14.2f)
    }.build()

@Composable
fun AnimeBoxHomeSkeletonLoading(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "home_skeleton")
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
        // 1. Netflix Spotlight Hero Banner Skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
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
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerColor)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.width(60.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).background(shimmerColor))
                    Box(modifier = Modifier.width(70.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).background(shimmerColor))
                    Box(modifier = Modifier.width(65.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).background(shimmerColor))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Box(modifier = Modifier.width(120.dp).height(40.dp).clip(RoundedCornerShape(6.dp)).background(shimmerColor))
                    Box(modifier = Modifier.width(120.dp).height(40.dp).clip(RoundedCornerShape(6.dp)).background(shimmerColor))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Continue Watching Section Skeleton
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.width(140.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).background(shimmerColor))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(215.dp)
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(shimmerColor)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Trending Now Poster Cards Skeleton
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.width(120.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).background(shimmerColor))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .width(115.dp)
                            .height(170.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(shimmerColor)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

val CustomWifiOffIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "CustomWifiOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
        strokeLineWidth = 2.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
    ) {
        moveTo(1f, 1f)
        lineTo(23f, 23f)
        moveTo(16.72f, 11.06f)
        curveTo(18.47f, 11.77f, 20.06f, 12.87f, 21.4f, 14.24f)
        moveTo(5f, 12.55f)
        curveTo(3.73f, 13.56f, 2.67f, 14.8f, 1.89f, 16.2f)
        moveTo(12f, 20f)
        horizontalLineTo(12.01f)
    }.build()

val CustomCloudOffIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "CustomCloudOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
        strokeLineWidth = 2.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
    ) {
        moveTo(1f, 1f)
        lineTo(23f, 23f)
        moveTo(19.35f, 10.04f)
        curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
        curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
        moveTo(3.71f, 9.42f)
        curveTo(2.05f, 10.74f, 1f, 12.74f, 1f, 15f)
        curveTo(1f, 18.87f, 4.13f, 22f, 8f, 22f)
        horizontalLineTo(18.17f)
    }.build()

val CustomOutlinedInfoIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "CustomOutlinedInfo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
        strokeLineWidth = 2.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
    ) {
        // Circle outline
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
        close()
        // Top dot
        moveTo(12f, 8f)
        horizontalLineTo(12.01f)
        // Bottom line
        moveTo(12f, 12f)
        lineTo(12f, 16f)
    }.build()

val continueWatchingPosterCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
val continueWatchingLogoCache = java.util.concurrent.ConcurrentHashMap<Int, String>()

data class HomeGenreCategory(
    val id: String,
    val displayName: String,
    val searchQuery: String,
    val startColor: Color,
    val anilistId: Int,
    val posterUrl: String,
    val backdropUrl: String = ""
)

val STATIC_HOME_GENRES: List<HomeGenreCategory> = listOf(
    HomeGenreCategory(
        id = "action",
        displayName = "Action",
        searchQuery = "Action",
        startColor = Color(0xFF78350F),
        anilistId = 16498, // Attack on Titan
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx16498-73IhOXpJZImD.png",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "adventure",
        displayName = "Adventure",
        searchQuery = "Adventure",
        startColor = Color(0xFF047857),
        anilistId = 11061, // Hunter x Hunter
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx11061-sIpBprNRALMQ.png",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "comedy",
        displayName = "Comedy",
        searchQuery = "Comedy",
        startColor = Color(0xFFD97706),
        anilistId = 140960, // Spy x Family
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx140960-YrkSDTKiWezp.jpg",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "romance",
        displayName = "Romance",
        searchQuery = "Romance",
        startColor = Color(0xFF9D174D),
        anilistId = 101921, // Kaguya-sama
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101921-VvdGQyv8655I.jpg",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "drama",
        displayName = "Drama",
        searchQuery = "Drama",
        startColor = Color(0xFF0F766E),
        anilistId = 103047, // Violet Evergarden: The Movie
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx103047-k5KjTq39a9cE.jpg",
        backdropUrl = "https://media.themoviedb.org/t/p/w1066_and_h600_face/aLqtWLA6NQHBwQHvHDq5z4EKUm0.jpg"
    ),
    HomeGenreCategory(
        id = "fantasy",
        displayName = "Fantasy",
        searchQuery = "Fantasy",
        startColor = Color(0xFF6D28D9),
        anilistId = 101280, // That Time I Got Reincarnated as a Slime
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101280-I1g8z6zQ26gC.jpg",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "horror",
        displayName = "Horror",
        searchQuery = "Horror",
        startColor = Color(0xFF991B1B),
        anilistId = 11111, // Another
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx11111-eRjG2c5H4h0e.png",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "mystery",
        displayName = "Mystery",
        searchQuery = "Mystery",
        startColor = Color(0xFF4C1D95),
        anilistId = 1535, // Death Note
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx1535-43TjQeT2s17W.png",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "sports",
        displayName = "Sports",
        searchQuery = "Sports",
        startColor = Color(0xFFC2410C),
        anilistId = 137822, // Blue Lock
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx137822-0lA81z16o4bU.png",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "supernatural",
        displayName = "Super-\nnatural",
        searchQuery = "Supernatural",
        startColor = Color(0xFF3730A3),
        anilistId = 113415, // Jujutsu Kaisen
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx113415-bbBWj4pMWjvF.jpg",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "thriller",
        displayName = "Thriller",
        searchQuery = "Thriller",
        startColor = Color(0xFF881337),
        anilistId = 21459, // The Promised Neverland
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21459-7zD79549Y3eM.jpg",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "mecha",
        displayName = "Mecha",
        searchQuery = "Mecha",
        startColor = Color(0xFF1E40AF),
        anilistId = 116589, // 86 Eighty-Six
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx116589-7iT6P0bCq3bK.jpg",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "music",
        displayName = "Music",
        searchQuery = "Music",
        startColor = Color(0xFF6D28D9),
        anilistId = 130003, // Bocchi the Rock!
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx130003-b0X2M0N4s0wU.png",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "psychological",
        displayName = "Psycho-\nlogical",
        searchQuery = "Psychological",
        startColor = Color(0xFF312E81),
        anilistId = 98659, // Classroom of the Elite
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx98659-YrkSDTKiWezp.jpg",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "ecchi",
        displayName = "Ecchi",
        searchQuery = "Ecchi",
        startColor = Color(0xFFDB2777),
        anilistId = 11617, // High School DxD
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx11617-H29G0qB4S0rF.png",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "sci-fi",
        displayName = "Sci-Fi",
        searchQuery = "Sci-Fi",
        startColor = Color(0xFF0284C7),
        anilistId = 9253, // Steins;Gate
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx9253-7pdcVzQSkpKq.png",
        backdropUrl = ""
    ),
    HomeGenreCategory(
        id = "slice-of-life",
        displayName = "Slice of Life",
        searchQuery = "Slice of Life",
        startColor = Color(0xFF10B981),
        anilistId = 5680, // K-ON!
        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx5680-eWj6e6w1Hj91.png",
        backdropUrl = ""
    )
)

@Composable
fun NetflixDownloadedPhoneIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = 1.8.dp.toPx()

        // Draw smartphone / device outline
        val phoneW = w * 0.70f
        val phoneH = h * 0.94f
        val left = (w - phoneW) / 2f
        val top = (h - phoneH) / 2f
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())

        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(phoneW, phoneH),
            cornerRadius = cornerRadius,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
        )

        // Top speaker / notch line
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.42f, top + 3.dp.toPx()),
            end = androidx.compose.ui.geometry.Offset(w * 0.58f, top + 3.dp.toPx()),
            strokeWidth = 1.2.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Checkmark inside screen
        val checkPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(left + phoneW * 0.28f, top + phoneH * 0.52f)
            lineTo(left + phoneW * 0.45f, top + phoneH * 0.68f)
            lineTo(left + phoneW * 0.75f, top + phoneH * 0.36f)
        }
        drawPath(
            path = checkPath,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeW * 1.15f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}



