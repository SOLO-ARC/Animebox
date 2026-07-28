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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import com.lagradost.cloudstream3.ui.animebox.api.AniListClient
import com.lagradost.cloudstream3.ui.animebox.api.AniZipClient
import com.lagradost.cloudstream3.ui.animebox.history.WatchHistoryItem
import com.lagradost.cloudstream3.ui.animebox.history.WatchHistoryManager
import com.lagradost.cloudstream3.ui.animebox.library.LibraryManager
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import com.lagradost.cloudstream3.ui.animebox.profiles.UserProfile
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.json.JSONObject

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
        diffMinutes < 60 -> "${diffMinutes}m ago"
        diffHours < 24 -> "${diffHours}h ago"
        diffDays < 7 -> "${diffDays}d ago"
        diffWeeks < 4 -> "${diffWeeks}w ago"
        diffMonths < 12 -> "${diffMonths}mo ago"
        else -> "${diffYears}y ago"
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
    val releaseDay: Int = 0
)

class AnimeBoxMainActivity : ComponentActivity() {

    companion object {
        // Fixed spotlight anime IDs (appear first in spotlight rotation)
        private val FIXED_SPOTLIGHT_IDS = listOf(
            99423,  // Darling in the FranXX
            129201, // Summer Time Rendering
            127230, // Chainsaw Man
            150672, // Oshi no Ko
            101922, // Demon Slayer
            137822, // Blue Lock
            21234,  // Erased
            21355,  // Re:ZERO
            113813, // Rent-a-Girlfriend
            155963, // Hokkaido Gals Are Super Adorable!
            226,    // Elfen Lied
            20605   // Tokyo Ghoul
        )
        private const val SUMMER_TIME_RENDERING_ID = 129201
        private const val SUMMER_TIME_RENDERING_BACKDROP = "https://image.tmdb.org/t/p/original/1czz0r7urqCPP0CZTAEkCk4TZY1.jpg"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialTab = intent.getIntExtra("selectTab", 0)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD0BCFF), // Premium Light Purple
                    background = Color(0xFF000000), // Pitch Black
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                HomeScreen(initialTab)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val initialTab = intent.getIntExtra("selectTab", 0)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD0BCFF), // Premium Light Purple
                    background = Color(0xFF000000), // Pitch Black
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                HomeScreen(initialTab)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HomeScreen(initialTab: Int = 0) {
        val coroutineScope = rememberCoroutineScope()
        var currentProfileId by remember { mutableStateOf(ProfileManager.getActiveProfile(this@AnimeBoxMainActivity)) }
        var showProfileSelector by remember { mutableStateOf(false) }
        val historyManager = remember { WatchHistoryManager(this@AnimeBoxMainActivity) }
        val libraryManager = remember { LibraryManager(this@AnimeBoxMainActivity) }

        var trendingList by remember { mutableStateOf<List<AnimeBrief>>(emptyList()) }
        var continueWatchingList by remember { mutableStateOf<List<WatchHistoryItem>>(emptyList()) }
        var libraryItemsList by remember { mutableStateOf<List<AnimeBrief>>(emptyList()) }
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
        var selectedTab by remember { mutableStateOf(initialTab) } // 0: Home, 1: Search, 2: Library
        var showNotificationsDialog by remember { mutableStateOf(false) }
        var lastSeenNotificationId by remember { mutableStateOf(0) }

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

        val focusManager = LocalFocusManager.current
        val context = LocalContext.current

        // Helper standard genres
        val standardGenres = listOf(
            "Action", "Adventure", "Comedy", "Drama", "Fantasy", 
            "Mystery", "Psychological", "Romance", "Sci-Fi", 
            "Slice of Life", "Sports", "Supernatural", "Thriller",
            "Ecchi", "Horror", "Mahou Shoujo", "Mecha", "Music"
        )

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
                    kotlinx.coroutines.delay(400L)
                    val response = AniListClient.searchAnime(queryText)
                    if (response != null) {
                        searchResults = parseTrending(response) // Reuse parseTrending since schema matches
                    } else {
                        searchResults = emptyList()
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
                val response = AniListClient.getAnimeByGenre(genreName, 1)
                if (response != null) {
                    searchResults = parseTrending(response)
                    genreHasMore = parseHasNextPage(response)
                } else {
                    searchResults = emptyList()
                }
                isSearchLoading = false
            }
        }

        // Refresh lists when active profile changes
        LaunchedEffect(currentProfileId) {
            continueWatchingList = historyManager.getWatchHistory()
            libraryItemsList = libraryManager.getLibraryItems()
        }

        // Initial Data Fetching — fixed spotlight IDs first, then trending, extra sections in background
        LaunchedEffect(Unit) {
            isLoading = true
            coroutineScope.launch {
                // PHASE 1: Parallel fetch of fixed spotlight data + trending + popular
                val fixedDeferred = coroutineScope.async { AniListClient.getAnimesByIds(FIXED_SPOTLIGHT_IDS) }
                val trendingDeferred = coroutineScope.async { AniListClient.getTrendingAnime() }
                val popularDeferred = coroutineScope.async { AniListClient.getPopularAnime() }

                val fixedResponse = fixedDeferred.await()
                val trendingResponse = trendingDeferred.await()
                val popularResponse = popularDeferred.await()

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

                // Top 3 trending that aren't already in the fixed list
                val top3Trending = rawTrending.filter { t -> FIXED_SPOTLIGHT_IDS.none { it == t.id } }.take(3)

                // Show spotlight immediately with AniList banners while logos/TMDB load
                // PHASE 2: Resolve logos + TMDB backdrops in parallel for all spotlight items
                val allSpotlightRaw = sortedFixed + top3Trending
                val resolvedDefs = allSpotlightRaw.map { anime ->
                    coroutineScope.async {
                        val logo = AniZipClient.getAnimeLogoUrl(anime.id)
                        val tmdbBackdrop = when (anime.id) {
                            SUMMER_TIME_RENDERING_ID -> SUMMER_TIME_RENDERING_BACKDROP
                            else -> AniZipClient.getTmdbBackdropUrl(anime.id)
                                .ifEmpty { anime.bannerUrl.ifEmpty { anime.coverUrl } }
                        }
                        anime.copy(logoUrl = logo, bannerUrl = tmdbBackdrop)
                    }
                }
                spotlightAnimes = resolvedDefs.awaitAll()
                isLoading = false

                // PHASE 3: Load extra home sections in background (don't block UI)
                coroutineScope.launch {
                    val resp = AniListClient.getRecentlyAdded()
                    if (resp != null) {
                        val parsed = parseTrending(resp)
                        val curYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                        recentlyAddedList = parsed.filter { it.releaseYear == curYear }
                    }
                }
                coroutineScope.launch {
                    val resp = AniListClient.getPopularThisSeason()
                    if (resp != null) thisSeasonList = parseTrending(resp)
                }
                coroutineScope.launch {
                    val resp = AniListClient.getPopularMovies()
                    if (resp != null) moviesListData = parseTrending(resp)
                }
                coroutineScope.launch {
                    val resp = AniListClient.getComingSoon()
                    if (resp != null) {
                        val list = parseTrending(resp)
                        comingSoonList = list
                        comingSoonIds = list.map { it.id }.toSet()
                    }
                }
                coroutineScope.launch {
                    // Random page between 1 and 80 to get completely random 2000+ anime (not just popular)
                    val resp = AniListClient.getRandomAnimeAfter2000((1..80).random())
                    if (resp != null) {
                        val filteredList = parseTrending(resp)
                        if (filteredList.isNotEmpty()) {
                            // Resolve high-quality landscape backdrops in parallel and filter out low quality ones
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
                }
            }
        }

        // Refresh continueWatching and library when Main Activity Resumes
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    continueWatchingList = WatchHistoryManager(this@AnimeBoxMainActivity).getWatchHistory()
                    libraryItemsList = LibraryManager(this@AnimeBoxMainActivity).getLibraryItems()
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

        Scaffold(
            bottomBar = {
                NetflixBottomNav(
                    selectedTab = selectedTab,
                    onTabSelected = { tabIndex ->
                        selectedTab = tabIndex
                        if (tabIndex != 1) {
                            // Reset search filters when leaving search tab
                            searchQuery = ""
                            activeSearchGenre = ""
                            searchResults = emptyList()
                        }
                    }
                )
            },
            containerColor = Color.Black
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.Black)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFD0BCFF))
                    }
                } else {
                    when (selectedTab) {
                        0 -> {
                            // HOME TAB
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // 1. Netflix Spotlight Hero Section (top 3 from trending)
                                if (spotlightAnimes.isNotEmpty()) {
                                    val spotlight = spotlightAnimes[spotlightIndex]
                                    SpotlightSection(
                                        spotlight = spotlight,
                                        inLibrary = libraryItemsList.any { it.id == spotlight.id },
                                        onToggleLibrary = {
                                            val added = LibraryManager(this@AnimeBoxMainActivity).toggleLibraryItem(spotlight)
                                            libraryItemsList = LibraryManager(this@AnimeBoxMainActivity).getLibraryItems()
                                            Toast.makeText(context, if (added) "Added to My List" else "Removed from My List", Toast.LENGTH_SHORT).show()
                                        },
                                        onPlayClick = { openDetailsPage(spotlight.id) }
                                    )
                                }

                                // 2. Horizontal Genre Chips Bar
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                                ) {
                                    items(standardGenres) { genre ->
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                                .clickable {
                                                    selectedTab = 1 // Go to Search Tab
                                                    filterByGenre(genre)
                                                }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = genre,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 3. Continue Watching (isolated by profile)
                                if (continueWatchingList.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Continue Watching",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Continue Watching",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.height(160.dp)
                                    ) {
                                        items(continueWatchingList) { item ->
                                            ContinueWatchingCard(
                                                item = item,
                                                onDeleteClick = {
                                                    val histManager = WatchHistoryManager(this@AnimeBoxMainActivity)
                                                    val list = histManager.getWatchHistory().toMutableList()
                                                    list.removeAll { it.anilistId == item.anilistId }
                                                    val prefs = getSharedPreferences("AnimeBoxHistory_${ProfileManager.getActiveProfile(this@AnimeBoxMainActivity)}", Context.MODE_PRIVATE)
                                                    prefs.edit().putString("history_list", list.toJson()).apply()
                                                    continueWatchingList = list
                                                },
                                                onClick = { resolvedUrl ->
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
                                            )
                                        }
                                    }
                                }

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
                                                // Huge overlapping background number (Netflix style)
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

                                                // Overlapping Premium Poster Card on Right
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
                                                            val added = LibraryManager(this@AnimeBoxMainActivity).toggleLibraryItem(anime)
                                                            libraryItemsList = LibraryManager(this@AnimeBoxMainActivity).getLibraryItems()
                                                            Toast.makeText(context, if (added) "Added to My List" else "Removed from My List", Toast.LENGTH_SHORT).show()
                                                        },
                                                        onClick = { openDetailsPage(anime.id) }
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
                                                    val added = LibraryManager(this@AnimeBoxMainActivity).toggleLibraryItem(anime)
                                                    libraryItemsList = LibraryManager(this@AnimeBoxMainActivity).getLibraryItems()
                                                    Toast.makeText(context, if (added) "Added to My List" else "Removed from My List", Toast.LENGTH_SHORT).show()
                                                },
                                                onClick = { openDetailsPage(anime.id) }
                                            )
                                        }
                                    }
                                }

                                // 5b. Suggested / Random Show Spotlight (similar to 3rd screenshot)
                                if (suggestionList.isNotEmpty()) {
                                    val suggestion = suggestionList[suggestionIndex]
                                    SuggestionCard(
                                        anime = suggestion,
                                        backdropUrl = suggestion.bannerUrl,
                                        isAniZipBackdrop = true,
                                        inLibrary = libraryItemsList.any { it.id == suggestion.id },
                                        onToggleLibrary = {
                                            val added = LibraryManager(this@AnimeBoxMainActivity).toggleLibraryItem(suggestion)
                                            libraryItemsList = LibraryManager(this@AnimeBoxMainActivity).getLibraryItems()
                                            Toast.makeText(context, if (added) "Added to My List" else "Removed from My List", Toast.LENGTH_SHORT).show()
                                        },
                                        onClick = { openDetailsPage(suggestion.id) }
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
                                                    val added = LibraryManager(this@AnimeBoxMainActivity).toggleLibraryItem(anime)
                                                    libraryItemsList = LibraryManager(this@AnimeBoxMainActivity).getLibraryItems()
                                                    Toast.makeText(context, if (added) "Added to My List" else "Removed from My List", Toast.LENGTH_SHORT).show()
                                                },
                                                onClick = { openDetailsPage(anime.id) }
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
                                                    val added = LibraryManager(this@AnimeBoxMainActivity).toggleLibraryItem(anime)
                                                    libraryItemsList = LibraryManager(this@AnimeBoxMainActivity).getLibraryItems()
                                                    Toast.makeText(context, if (added) "Added to My List" else "Removed from My List", Toast.LENGTH_SHORT).show()
                                                },
                                                onClick = { openDetailsPage(anime.id) }
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

                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        1 -> {
                            // SEARCH TAB (Genre row, Search bar, and Results or Recommendations)
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                            ) {
                                // Search Input Field
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { 
                                        searchQuery = it
                                        if (it.isNotBlank()) {
                                            performSearch(it, false)
                                        } else {
                                            searchResults = emptyList()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    placeholder = { Text("Type anime title...", color = Color.Gray) },
                                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "SearchIcon", tint = Color.Gray) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { performSearch(searchQuery, true) }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFFD0BCFF),
                                        unfocusedBorderColor = Color.DarkGray
                                    )
                                )

                                // Genre Tags Row below search bar
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    items(standardGenres) { genre ->
                                        val isActive = activeSearchGenre == genre
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = if (isActive) Color(0xFFD0BCFF) else Color(0xFF1E1E1E),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    if (isActive) {
                                                        activeSearchGenre = ""
                                                        searchResults = emptyList()
                                                    } else {
                                                        filterByGenre(genre)
                                                    }
                                                }
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = genre,
                                                color = if (isActive) Color.Black else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (isSearchLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFFD0BCFF))
                                    }
                                } else {
                                    val isShowingRecommendations = searchQuery.isEmpty() && activeSearchGenre.isEmpty()
                                    val activeList = if (isShowingRecommendations) recommendedAnimes else searchResults

                                    if (activeList.isEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = if (searchQuery.isNotEmpty()) "No results found for \"$searchQuery\"" else "No anime found",
                                                color = Color.Gray,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = {
                                                    searchQuery = ""
                                                    activeSearchGenre = ""
                                                    searchResults = emptyList()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF))
                                            ) {
                                                Text("Clear Search", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }

                                            if (recommendedAnimes.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(20.dp))
                                                Text(
                                                    text = "Popular Recommendations",
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp)
                                                )
                                                LazyVerticalGrid(
                                                    columns = GridCells.Fixed(3),
                                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(1f)
                                                ) {
                                                    items(recommendedAnimes) { anime ->
                                                        PremiumAnimePosterCard(
                                                            anime = anime,
                                                            inLibrary = libraryItemsList.any { it.id == anime.id },
                                                            onToggleLibrary = {
                                                                val added = LibraryManager(this@AnimeBoxMainActivity).toggleLibraryItem(anime)
                                                                libraryItemsList = LibraryManager(this@AnimeBoxMainActivity).getLibraryItems()
                                                                Toast.makeText(context, if (added) "Added to My List" else "Removed from My List", Toast.LENGTH_SHORT).show()
                                                            },
                                                            onClick = { openDetailsPage(anime.id) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Column(modifier = Modifier.weight(1f)) {
                                            LazyVerticalGrid(
                                                columns = GridCells.Fixed(3),
                                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f)
                                                    .padding(bottom = 8.dp)
                                            ) {
                                                items(activeList) { anime ->
                                                    PremiumAnimePosterCard(
                                                        anime = anime,
                                                        inLibrary = libraryItemsList.any { it.id == anime.id },
                                                        onToggleLibrary = {
                                                            val added = LibraryManager(this@AnimeBoxMainActivity).toggleLibraryItem(anime)
                                                            libraryItemsList = LibraryManager(this@AnimeBoxMainActivity).getLibraryItems()
                                                            Toast.makeText(context, if (added) "Added to My List" else "Removed from My List", Toast.LENGTH_SHORT).show()
                                                        },
                                                        onClick = { openDetailsPage(anime.id) }
                                                    )
                                                }
                                                // Load More button (for genre searches with more pages)
                                                if (!isShowingRecommendations && activeSearchGenre.isNotEmpty() && genreHasMore) {
                                                    item(span = { GridItemSpan(3) }) {
                                                        Box(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isLoadingMore) {
                                                                CircularProgressIndicator(color = Color(0xFFD0BCFF), modifier = Modifier.size(28.dp))
                                                            } else {
                                                                OutlinedButton(
                                                                    onClick = {
                                                                        isLoadingMore = true
                                                                        coroutineScope.launch {
                                                                            val nextPage = genrePage + 1
                                                                            val resp = AniListClient.getAnimeByGenre(activeSearchGenre, nextPage)
                                                                            if (resp != null) {
                                                                                searchResults = searchResults + parseTrending(resp)
                                                                                genrePage = nextPage
                                                                                genreHasMore = parseHasNextPage(resp)
                                                                            }
                                                                            isLoadingMore = false
                                                                        }
                                                                    },
                                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0BCFF)),
                                                                    shape = RoundedCornerShape(8.dp)
                                                                ) {
                                                                    Text("Load More", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold)
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

                        2 -> {
                            // LIBRARY TAB ("My List")
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = "My List",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )

                                if (libraryItemsList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Your list is empty. Add shows to get started.",
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
                                            .padding(bottom = 8.dp)
                                    ) {
                                        items(libraryItemsList) { anime ->
                                            PremiumAnimePosterCard(
                                                anime = anime,
                                                inLibrary = true,
                                                onToggleLibrary = {
                                                    LibraryManager(this@AnimeBoxMainActivity).toggleLibraryItem(anime)
                                                    libraryItemsList = LibraryManager(this@AnimeBoxMainActivity).getLibraryItems()
                                                    Toast.makeText(context, "Removed from My List", Toast.LENGTH_SHORT).show()
                                                },
                                                onClick = { openDetailsPage(anime.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                /* ─── Continue Watching loading overlay ─── */
                if (continueLaunchLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFFD0BCFF))
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp) // Pushed slightly higher
                            .align(Alignment.TopCenter),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Search icon (navigates to Search tab)
                        IconButton(
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Right side: Notification bell + Profile avatar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val notificationsList = remember(recentlyAddedList) { recentlyAddedList }
                            val calendar = java.util.Calendar.getInstance()
                            val nowMs = calendar.timeInMillis
                            val under24Count = remember(notificationsList, nowMs) {
                                notificationsList.count { anime ->
                                    val releaseCal = java.util.Calendar.getInstance().apply {
                                        set(java.util.Calendar.YEAR, anime.releaseYear)
                                        set(java.util.Calendar.MONTH, if (anime.releaseMonth > 0) anime.releaseMonth - 1 else 0)
                                        set(java.util.Calendar.DAY_OF_MONTH, if (anime.releaseDay > 0) anime.releaseDay else 1)
                                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                                        set(java.util.Calendar.MINUTE, 0)
                                        set(java.util.Calendar.SECOND, 0)
                                        set(java.util.Calendar.MILLISECOND, 0)
                                    }
                                    val releaseMs = releaseCal.timeInMillis
                                    val diffMs = nowMs - releaseMs
                                    val diffHours = if (diffMs > 0) diffMs / (1000 * 60 * 60) else 999
                                    diffHours < 24 && anime.releaseYear > 0
                                }
                            }
                            val hasNewNotification = notificationsList.isNotEmpty() && lastSeenNotificationId != notificationsList[0].id
                            val notificationCount = if (hasNewNotification) under24Count else 0

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { 
                                        showNotificationsDialog = true 
                                        if (notificationsList.isNotEmpty()) {
                                            lastSeenNotificationId = notificationsList[0].id
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    if (notificationCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 1.dp, y = (-1).dp)
                                                .size(14.dp)
                                                .background(Color(0xFFD0BCFF), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$notificationCount",
                                                color = Color.Black,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
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
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFD0BCFF))
                                    .clickable { showProfileSelector = true },
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
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showProfileSelector) {
            ProfileSelectorDialog(
                onDismiss = { showProfileSelector = false },
                onProfileSelected = { profileId ->
                    ProfileManager.setActiveProfile(this@AnimeBoxMainActivity, profileId)
                    currentProfileId = profileId
                    showProfileSelector = false
                }
            )
        }

        if (showNotificationsDialog) {
            val notificationsList = remember(recentlyAddedList) { recentlyAddedList }
            NotificationsDialog(
                notifications = notificationsList,
                onDismiss = { showNotificationsDialog = false },
                onNotificationClick = { openDetailsPage(it) }
            )
        }

        // Coming Soon Release Alert Dialog
        if (comingSoonAlertAnime != null) {
            AlertDialog(
                onDismissRequest = { comingSoonAlertAnime = null },
                confirmButton = {
                    TextButton(onClick = { comingSoonAlertAnime = null }) {
                        Text("OK", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold)
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
    }

    @Composable
    fun NetflixBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
        NavigationBar(
            containerColor = Color.Black,
            tonalElevation = 0.dp,
            modifier = Modifier.border(0.5.dp, Color(0xFF1E1E1E), RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
        ) {
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Home") },
                label = { Text("Home", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFD0BCFF),
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = Color(0xFFD0BCFF),
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                icon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                label = { Text("Search", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFD0BCFF),
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = Color(0xFFD0BCFF),
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
            NavigationBarItem(
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                icon = { Icon(imageVector = Icons.Default.List, contentDescription = "My List") },
                label = { Text("My List", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFD0BCFF),
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = Color(0xFFD0BCFF),
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
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
        val context = LocalContext.current
        val resId = remember(context) {
            context.resources.getIdentifier("ic_mic_custom", "drawable", context.packageName)
        }
        if (resId != 0) {
            Icon(
                painter = rememberAsyncImagePainter(model = resId),
                contentDescription = "Mic Icon",
                tint = color,
                modifier = modifier.size(18.dp)
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = modifier.size(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp, 7.dp)
                        .background(color, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(1.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp, 1.dp)
                        .background(color)
                )
            }
        }
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
                        .height(26.dp)
                        .background(Color.Black.copy(alpha = 0.7f)),
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
                        Text("CC", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (anime.episodes > 0) "${anime.episodes}" else "?",
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                    Text("|", color = Color.Gray.copy(alpha = 0.5f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(8.dp))

                    CustomMicIcon(color = Color.White)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (anime.episodes > 0) "${anime.episodes}" else "?",
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
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
        notifications: List<AnimeBrief>,
        onDismiss: () -> Unit,
        onNotificationClick: (Int) -> Unit
    ) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Notifications",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (notifications.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No new notifications.", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(notifications.size) { index ->
                                val anime = notifications[index]
                                val calendar = java.util.Calendar.getInstance()
                                val nowMs = calendar.timeInMillis
                                val releaseCal = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.YEAR, anime.releaseYear)
                                    set(java.util.Calendar.MONTH, if (anime.releaseMonth > 0) anime.releaseMonth - 1 else 0)
                                    set(java.util.Calendar.DAY_OF_MONTH, if (anime.releaseDay > 0) anime.releaseDay else 1)
                                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    set(java.util.Calendar.MINUTE, 0)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }
                                val releaseMs = releaseCal.timeInMillis
                                val diffMs = nowMs - releaseMs
                                val diffHours = if (diffMs > 0) diffMs / (1000 * 60 * 60) else 999
                                val isUnder24Hours = diffHours < 24 && anime.releaseYear > 0
                                
                                val isTvShow = !anime.status.equals("FINISHED", ignoreCase = true) && anime.episodes > 1
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isUnder24Hours) Color(0xFFD0BCFF).copy(alpha = 0.08f) else Color.Transparent)
                                        .clickable {
                                            onNotificationClick(anime.id)
                                            onDismiss()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Poster
                                    Image(
                                        painter = rememberAsyncImagePainter(model = anime.coverUrl),
                                        contentDescription = anime.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(64.dp)
                                            .height(96.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF1E1E1E))
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    // Details
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isTvShow) "New Episode Released" else "New Release Available",
                                                color = Color(0xFFD0BCFF), // Light purple color
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isUnder24Hours) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFFFB300), RoundedCornerShape(3.dp))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text("NEW", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (isTvShow) {
                                                "Episode ${anime.episodes} of ${anime.title} has just been released."
                                            } else {
                                                "${anime.title} has just been released."
                                            },
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = getReleaseTimeAgo(anime.releaseYear, anime.releaseMonth, anime.releaseDay),
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                if (index < notifications.size - 1) {
                                    Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1E1E1E)))
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
                "What is AnimeBox?" to "AnimeBox is a premium streaming service that allows you to watch a wide variety of anime shows, movies, and more on thousands of internet-connected devices.",
                "How much does AnimeBox Cost?" to "AnimeBox is completely free! There are no hidden fees, subscriptions, or contracts.",
                "Where can I watch?" to "Watch anywhere, anytime. Sign in with your account to watch instantly on the web or through the app on your phone, tablet, or TV.",
                "How do I cancel?" to "Since AnimeBox is free, there are no subscriptions to cancel! You can simply close or uninstall the app at any time.",
                "What can I watch on AnimeBox?" to "AnimeBox has an extensive library of anime feature films, documentaries, TV shows, and award-winning AnimeBox originals. Watch as much as you want, anytime you want.",
                "Is AnimeBox good for kids?" to "Yes! You can create dedicated kids profiles to filter out adult content and restrict viewing to age-appropriate anime content."
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
                            text = "${if (anime.episodes > 0) "${anime.episodes} Episodes" else "Ongoing"}  •  Score ${anime.averageScore}%",
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
                .height(440.dp)
                .clickable { onPlayClick() }
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = spotlight.bannerUrl.ifEmpty { spotlight.coverUrl }),
                contentDescription = spotlight.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Top shadow gradient for header visibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (spotlight.logoUrl.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(model = spotlight.logoUrl),
                        contentDescription = spotlight.title,
                        modifier = Modifier
                            .height(120.dp)
                            .fillMaxWidth(0.85f)
                            .padding(bottom = 8.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = spotlight.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (spotlight.genres.isNotEmpty()) {
                    Text(
                        text = spotlight.genres.take(3).joinToString("  •  "),
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onToggleLibrary() }
                            .padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (inLibrary) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = "My List",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("MY LIST", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onPlayClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play", fontWeight = FontWeight.Black, color = Color.White, fontSize = 15.sp)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onPlayClick() }
                            .padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("INFO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
    fun ContinueWatchingCard(item: WatchHistoryItem, onDeleteClick: () -> Unit, onClick: (String) -> Unit) {
        var resolvedCoverUrl by remember(item.coverImageUrl) { mutableStateOf(item.coverImageUrl) }

        if (resolvedCoverUrl.isEmpty() || resolvedCoverUrl.contains("anilist.co") || resolvedCoverUrl.contains("img.anili.st")) {
            LaunchedEffect(item.anilistId, item.episodeNumber) {
                var backdrop = ""
                if (item.anilistId == 21 || item.anilistId == 235) {
                    val tmdbId = if (item.anilistId == 21) 37854 else 30983
                    val allEps = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getTmdbAllEpisodes(tmdbId)
                    val epMeta = allEps[item.episodeNumber]
                    if (epMeta != null && epMeta.imageUrl.isNotEmpty()) {
                        backdrop = epMeta.imageUrl
                    }
                }
                if (backdrop.isEmpty()) {
                    backdrop = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getAniZipBackdropUrl(item.anilistId)
                }
                if (backdrop.isEmpty()) {
                    backdrop = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getTmdbBackdropUrl(item.anilistId)
                }
                if (backdrop.isEmpty()) {
                    val details = com.lagradost.cloudstream3.ui.animebox.api.AniListClient.getAnimeDetails(item.anilistId)
                    if (details != null) {
                        try {
                            val obj = org.json.JSONObject(details).getJSONObject("data").getJSONObject("Media")
                            val banner = obj.optString("bannerImage", "")
                            backdrop = if (banner.isNotEmpty()) banner else obj.getJSONObject("coverImage").getString("large")
                        } catch (e: Exception) {}
                    }
                }
                if (backdrop.isNotEmpty()) {
                    resolvedCoverUrl = backdrop
                }
            }
        }

        Column(
            modifier = Modifier
                .width(180.dp)
                .clickable { onClick(resolvedCoverUrl) }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF161616))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = resolvedCoverUrl),
                    contentDescription = item.animeTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 12.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("EP ${item.episodeNumber}", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 12.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${formatTime(item.progressPositionMs)}/${formatTime(item.totalDurationMs)}", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }

                val percentage = if (item.totalDurationMs > 0) item.progressPositionMs.toFloat() / item.totalDurationMs else 0f
                LinearProgressIndicator(
                    progress = { percentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomStart),
                    color = Color(0xFFD0BCFF),
                    trackColor = Color.DarkGray.copy(alpha = 0.5f)
                )
            }
            Text(
                text = item.animeTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }

    @Composable
    fun ProfileSelectorDialog(onDismiss: () -> Unit, onProfileSelected: (String) -> Unit) {
        val context = LocalContext.current
        var profilesList by remember { mutableStateOf(ProfileManager.getProfiles(context)) }
        
        var showAddProfile by remember { mutableStateOf(false) }
        var newProfileName by remember { mutableStateOf("") }
        var profileLimitWarning by remember { mutableStateOf(false) }
        var showSettingsDialog by remember { mutableStateOf(false) }

        // Editing Profile specific states
        var editingProfile by remember { mutableStateOf<UserProfile?>(null) }
        var editProfileName by remember { mutableStateOf("") }
        var editProfileAvatarUrl by remember { mutableStateOf("") }
        var showAvatarPicker by remember { mutableStateOf(false) }

        // Gallery image picker launcher defined in correct scope
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

        val presetAvatars = listOf(
            "https://wallpapers.com/images/hd/luffy-profile-picture-2pwhf6e9t6p660cf.jpg",
            "https://avatarfiles.alphacoders.com/264/264259.jpg",
            "https://avatarfiles.alphacoders.com/279/279482.jpg",
            "https://avatarfiles.alphacoders.com/318/318029.jpg",
            "https://avatarfiles.alphacoders.com/335/335010.jpg"
        )

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false) // Use full screen properties to avoid stretching issues
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Who's watching?",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 32.dp)
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
                                    // Clicking the main profile avatar navigates inside
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
                                                    .background(Color(0xFFD0BCFF)),
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
                                            tint = Color(0xFFD0BCFF),
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

                    Spacer(modifier = Modifier.height(32.dp))

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
                                focusedBorderColor = Color(0xFFD0BCFF),
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF)),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Add Profile", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Netflix-style profiles addition button (capped at 3)
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
                            enabled = true // Clickable to trigger warning alert message
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
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    }
                }
            }
        }

        // Settings Dialog Overlay
        if (showSettingsDialog) {
            com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettingsDialog(
                onDismiss = { showSettingsDialog = false },
                onSettingsChanged = {
                    profilesList = ProfileManager.getProfiles(context)
                }
            )
        }

        // Edit Profile Dialog Overlay
        if (editingProfile != null) {
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

            Dialog(
                onDismissRequest = { editingProfile = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "Edit Profile",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        // Avatar Image box
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1E1E))
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
                                        .background(Color(0xFFD0BCFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = editProfileName.take(1).uppercase(),
                                        color = Color.White,
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // TextField to edit name
                        OutlinedTextField(
                            value = editProfileName,
                            onValueChange = { editProfileName = it },
                            label = { Text("Profile Name", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(0.9f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD0BCFF),
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // TextField to edit custom image url
                        OutlinedTextField(
                            value = editProfileAvatarUrl,
                            onValueChange = { editProfileAvatarUrl = it },
                            label = { Text("Avatar URL / File Path", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(0.9f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD0BCFF),
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        // App Settings Button below Avatar URL section
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(50.dp),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "App Settings",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "App Settings",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Edit Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(0.9f),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { editingProfile = null }) {
                                Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    if (editProfileName.isNotBlank()) {
                                        val profiles = ProfileManager.getProfiles(context).toMutableList()
                                        val targetIdx = profiles.indexOfFirst { it.id == p.id }
                                        if (targetIdx != -1) {
                                            profiles[targetIdx] = UserProfile(p.id, editProfileName, editProfileAvatarUrl)
                                            ProfileManager.saveProfiles(context, profiles)
                                            profilesList = ProfileManager.getProfiles(context)
                                        }
                                        editingProfile = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF)),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Save Changes", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openDetailsPage(anilistId: Int) {
        val intent = Intent(this, AnimeBoxDetailActivity::class.java).apply {
            putExtra("anilistId", anilistId)
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
        val prefs = getSharedPreferences("AnimeBoxPlayer", android.content.Context.MODE_PRIVATE)
        val selectedAudio = prefs.getString("selectedAudio", "Japanese (Original)")
        val type = if (selectedAudio == "Hindi") "hindi" else if (selectedAudio == "English") "dub" else "sub"

        var streamInfo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (type == "hindi") {
                // 1. Try anidrive API first
                try {
                    val aniDriveUrl = "${com.lagradost.cloudstream3.BuildConfig.LOVABLE_ANIDRIVE_API}/api/public/stream?anilist=$anilistId&ep=$episodeNum"
                    val req = okhttp3.Request.Builder().url(aniDriveUrl).build()
                    okhttp3.OkHttpClient().newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val jsonStr = resp.body?.string() ?: ""
                            if (jsonStr.isNotEmpty()) {
                                val json = org.json.JSONObject(jsonStr)
                                if (json.optBoolean("success", false) && json.has("data")) {
                                    val data = json.getJSONObject("data")
                                    val mainUrl = data.optString("url", "")
                                    if (mainUrl.isNotEmpty()) {
                                        var subtitleUrl = ""
                                        var introStart = 0L; var introEnd = 0L
                                        var outroStart = 0L; var outroEnd = 0L
                                        try {
                                            val subUrl = "${com.lagradost.cloudstream3.BuildConfig.VERCEL_MULTIMOVIE_API}/api/anime?anilistId=$anilistId&episode=$episodeNum&type=sub"
                                            val subReq = okhttp3.Request.Builder().url(subUrl).build()
                                            okhttp3.OkHttpClient().newCall(subReq).execute().use { subRes ->
                                                if (subRes.isSuccessful) {
                                                    val subJson = org.json.JSONObject(subRes.body?.string() ?: "")
                                                    if (subJson.has("primary")) {
                                                        val subPrim = subJson.getJSONObject("primary")
                                                        if (subPrim.has("tracks") && !subPrim.isNull("tracks")) {
                                                            val subTracks = subPrim.getJSONArray("tracks")
                                                            for (k in 0 until subTracks.length()) {
                                                                val t = subTracks.getJSONObject(k)
                                                                val lang = t.optString("lang", "").lowercase()
                                                                val urlTrack = t.optString("url", "")
                                                                if (lang.contains("english") || lang.contains("en") || subtitleUrl.isEmpty()) {
                                                                    subtitleUrl = urlTrack
                                                                    if (lang.contains("english")) break
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {}

                                        return@withContext mapOf(
                                            "hls" to mainUrl,
                                            "referer" to "",
                                            "subtitle" to subtitleUrl,
                                            "introStart" to introStart,
                                            "introEnd" to introEnd,
                                            "outroStart" to outroStart,
                                            "outroEnd" to outroEnd
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }

                // 2. Fallback to multimovieapi Hindi endpoint if anidrive fails
                val hindiUrl = "${com.lagradost.cloudstream3.BuildConfig.VERCEL_MULTIMOVIE_API}/api/hindi?anilistId=$anilistId&episode=$episodeNum"
                val hindiReq = okhttp3.Request.Builder().url(hindiUrl).build()
                val hindiList = mutableListOf<Map<String, String>>()
                try {
                    okhttp3.OkHttpClient().newCall(hindiReq).execute().use { response ->
                        if (response.isSuccessful) {
                            val json = org.json.JSONObject(response.body?.string() ?: "")
                            if (json.has("streams")) {
                                val streams = json.getJSONArray("streams")
                                for (i in 0 until streams.length()) {
                                    val s = streams.getJSONObject(i)
                                    if (s.has("hls") && !s.isNull("hls")) {
                                        val hls = s.getString("hls")
                                        val headers = s.optJSONObject("headers")
                                        val referer = headers?.optString("Referer", "") ?: ""
                                        hindiList.add(mapOf("hls" to hls, "referer" to referer))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }

                var subtitleUrl = ""
                var introStart = 0L; var introEnd = 0L
                var outroStart = 0L; var outroEnd = 0L
                val subUrl = "${com.lagradost.cloudstream3.BuildConfig.VERCEL_MULTIMOVIE_API}/api/anime?anilistId=$anilistId&episode=$episodeNum&type=sub"
                val subReq = okhttp3.Request.Builder().url(subUrl).build()
                try {
                    okhttp3.OkHttpClient().newCall(subReq).execute().use { subRes ->
                        if (subRes.isSuccessful) {
                            val subJson = org.json.JSONObject(subRes.body?.string() ?: "")
                            if (subJson.has("primary")) {
                                val subPrim = subJson.getJSONObject("primary")
                                if (subPrim.has("tracks") && !subPrim.isNull("tracks")) {
                                    val subTracks = subPrim.getJSONArray("tracks")
                                    for (k in 0 until subTracks.length()) {
                                        val t = subTracks.getJSONObject(k)
                                        val lang = t.optString("lang", "").lowercase()
                                        val urlTrack = t.optString("url", "")
                                        if (lang.contains("english") || lang.contains("en") || subtitleUrl.isEmpty()) {
                                            subtitleUrl = urlTrack
                                            if (lang.contains("english")) break
                                        }
                                    }
                                }
                                if (subPrim.has("chapters") && !subPrim.isNull("chapters")) {
                                    val chapters = subPrim.getJSONArray("chapters")
                                    for (i in 0 until chapters.length()) {
                                        val ch = chapters.getJSONObject(i)
                                        when (ch.optString("title", "").lowercase()) {
                                            "intro" -> { introStart = ch.optLong("start", 0L); introEnd = ch.optLong("end", 0L) }
                                            "outro" -> { outroStart = ch.optLong("start", 0L); outroEnd = ch.optLong("end", 0L) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }

                if (hindiList.isNotEmpty()) {
                    val first = hindiList.first()
                    val hindiListJson = org.json.JSONArray().apply {
                        hindiList.forEach { put(org.json.JSONObject(it)) }
                    }.toString()
                    
                    mapOf(
                        "hls" to first["hls"],
                        "referer" to first["referer"],
                        "subtitle" to subtitleUrl,
                        "introStart" to introStart,
                        "introEnd" to introEnd,
                        "outroStart" to outroStart,
                        "outroEnd" to outroEnd,
                        "hindiStreamsJson" to hindiListJson
                    )
                } else null
            } else {
                val url = "${com.lagradost.cloudstream3.BuildConfig.VERCEL_MULTIMOVIE_API}/api/anime?anilistId=$anilistId&episode=$episodeNum&type=$type"
                val request = okhttp3.Request.Builder().url(url).build()
                try {
                okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = org.json.JSONObject(response.body?.string() ?: "")
                        if (json.has("primary")) {
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

                            // Parse chapters array for intro/outro timestamps
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

                            if (hls.isNotEmpty()) {
                                mapOf(
                                    "hls" to hls,
                                    "referer" to referer,
                                    "subtitle" to subtitleUrl,
                                    "introStart" to introStart,
                                    "introEnd" to introEnd,
                                    "outroStart" to outroStart,
                                    "outroEnd" to outroEnd,
                                    "backupHls" to backupHls,
                                    "backupProvider" to backupProvider
                                )
                            } else null
                        } else null
                    } else null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

        if (streamInfo == null || (streamInfo["hls"] as? String).isNullOrEmpty()) {
            streamInfo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val sType = if (type == "dub") "dub" else "sub"
                    val url = "${com.lagradost.cloudstream3.BuildConfig.VERCEL_SCRAPER_API}/default/$anilistId/$sType/$episodeNum"
                    val req = okhttp3.Request.Builder().url(url).build()
                    okhttp3.OkHttpClient().newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val jsonStr = resp.body?.string() ?: ""
                            if (jsonStr.isNotEmpty()) {
                                val json = org.json.JSONObject(jsonStr)
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
                                                    val uri = android.net.Uri.parse(u)
                                                    "${uri.scheme}://${uri.host}/"
                                                } catch (e: Exception) { "" }
                                                val server = s.optString("server", s.optString("name", s.optString("provider", ""))).lowercase()
                                                val isDefault = s.optBoolean("default", false)

                                                fun extractSub(link: String): String {
                                                    if (link.contains("sub=")) {
                                                        val idx = link.indexOf("sub=")
                                                        val raw = link.substring(idx + 4).let { val e = it.indexOf("&"); if (e != -1) it.substring(0, e) else it }
                                                        return try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (ex: Exception) { raw }
                                                    }
                                                    return ""
                                                }

                                                val subCandidate = extractSub(embed).ifEmpty { extractSub(rawRef) }
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
                                    mapOf(
                                        "hls" to hlsUrl,
                                        "referer" to referer,
                                        "subtitle" to subtitleUrl,
                                        "introStart" to introStart,
                                        "introEnd" to introEnd,
                                        "outroStart" to outroStart,
                                        "outroEnd" to outroEnd
                                    )
                                } else null
                            } else null
                        } else null
                    }
                } catch (e: Exception) { null }
            }
        }

        if (streamInfo != null && (streamInfo["hls"] as? String)?.isNotEmpty() == true) {
            // Fetch total episodes count from AniList if not passed
            val epCount = if (totalEpisodes > 0) totalEpisodes else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val anilistClient = com.lagradost.cloudstream3.ui.animebox.api.AniListClient
                    val resp = anilistClient.getAnimeDetails(anilistId)
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
                putExtra("hlsUrl", streamInfo["hls"] as String)
                putExtra("referer", streamInfo["referer"] as String)
                putExtra("subtitleUrl", (streamInfo["subtitle"] as? String) ?: "")
                putExtra("introStart", streamInfo["introStart"] as Long)
                putExtra("introEnd", streamInfo["introEnd"] as Long)
                putExtra("outroStart", streamInfo["outroStart"] as Long)
                putExtra("outroEnd", streamInfo["outroEnd"] as Long)
                putExtra("backupHls", streamInfo["backupHls"] as? String ?: "")
                putExtra("backupProvider", streamInfo["backupProvider"] as? String ?: "")
                putExtra("hindiStreamsJson", streamInfo["hindiStreamsJson"] as? String ?: "")
                putExtra("streamType", type)
                putExtra("fromContinueWatching", true)
                putExtra("anilistId", anilistId)
                putExtra("episode", episodeNum)
                putExtra("animeTitle", animeTitle)
                putExtra("coverUrl", coverUrl)
                putExtra("showCoverUrl", showCoverUrl)
                putExtra("totalEpisodes", epCount)
                putExtra("streamType", "sub")
                // Always show resume dialog when launched from Continue Watching
                putExtra("fromContinueWatching", true)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Failed to load stream. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseTrending(jsonString: String): List<AnimeBrief> {
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
                // Filter out future releases for anything that isn't coming soon (NOT_YET_RELEASED)
                if (isFuture && mediaStatus != "NOT_YET_RELEASED") {
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

