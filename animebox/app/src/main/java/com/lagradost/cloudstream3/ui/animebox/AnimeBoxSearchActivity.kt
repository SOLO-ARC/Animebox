package com.lagradost.cloudstream3.ui.animebox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.lagradost.cloudstream3.ui.animebox.api.AnimeStudioRepository
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.cloudstream3.ui.animebox.api.AniListClient
import kotlinx.coroutines.launch
import org.json.JSONObject

data class SearchAnimeBrief(
    val id: Int,
    val title: String,
    val coverUrl: String,
    val rating: String
)

class AnimeBoxSearchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialStudio = intent.getStringExtra("initialStudio") ?: ""
        val initialMode = intent.getStringExtra("searchMode") ?: if (initialStudio.isNotEmpty()) "STUDIO" else "GENRE"
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF6B00),
                    background = Color(0xFF141416),
                    surface = Color(0xFF1E1E22)
                )
            ) {
                SearchScreen(initialStudio = initialStudio, initialMode = initialMode)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SearchScreen(initialStudio: String = "", initialMode: String = "GENRE") {
        var query by remember { mutableStateOf("") }
        var searchMode by remember { mutableStateOf(initialMode) }
        var activeStudio by remember { mutableStateOf(initialStudio) }
        var activeGenre by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<SearchAnimeBrief>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        val context = LocalContext.current

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

        var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        val filterByStudio: (String) -> Unit = { studioName ->
            searchJob?.cancel()
            focusManager.clearFocus()
            query = ""
            activeStudio = studioName
            isLoading = true
            searchJob = coroutineScope.launch {
                val queryStudioName = com.lagradost.cloudstream3.ui.animebox.api.AnimeStudioRepository.getQueryForStudio(studioName)
                var response = AniListClient.getAnimeByStudio(queryStudioName, 1)
                if (response == null && queryStudioName != studioName) {
                    response = AniListClient.getAnimeByStudio(studioName, 1)
                }
                if (response == null) {
                    val stripped = studioName.replace(Regex("(?i)\\b(Studio|Films|Animation|Filmworks)\\b"), "").trim()
                    if (stripped.isNotEmpty() && stripped != queryStudioName && stripped != studioName) {
                        response = AniListClient.getAnimeByStudio(stripped, 1)
                    }
                }
                if (response != null) {
                    val list = parseStudioSearchResults(response)
                    searchResults = list
                } else {
                    searchResults = emptyList()
                }
                isLoading = false
            }
        }

        val filterByGenre: (String) -> Unit = { genreName ->
            searchJob?.cancel()
            focusManager.clearFocus()
            query = ""
            activeStudio = ""
            activeGenre = genreName
            isLoading = true
            searchJob = coroutineScope.launch {
                val response = AniListClient.getAnimeByGenre(genreName, 1)
                searchResults = if (response != null) parseSearchResults(response) else emptyList()
                isLoading = false
            }
        }

        val performSearch: (String) -> Unit = { targetQuery ->
            searchJob?.cancel()
            focusManager.clearFocus()
            activeStudio = ""
            activeGenre = ""
            if (targetQuery.isNotBlank()) {
                isLoading = true
                saveRecentQuery(targetQuery)
                searchJob = coroutineScope.launch {
                    kotlinx.coroutines.delay(350L)
                    val response = AniListClient.searchAnime(targetQuery)
                    searchResults = if (response != null) parseSearchResults(response) else emptyList()
                    isLoading = false
                }
            } else {
                searchResults = emptyList()
                isLoading = false
            }
        }

        // Auto filter on initial load if studio is provided
        LaunchedEffect(initialStudio) {
            if (initialStudio.isNotEmpty()) {
                filterByStudio(initialStudio)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141416))
        ) {
            // Background search artwork image
            Image(
                painter = painterResource(id = R.drawable.search_screen_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Black layer with 80% opacity over the background for increased contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.80f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                // Top Search Bar (Back Arrow + Rounded Pill Search Box #232328)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { finish() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = CustomBackChevronIcon,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF232328))
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
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            BasicTextField(
                                value = query,
                                onValueChange = { 
                                    query = it
                                    if (it.isNotBlank()) {
                                        performSearch(it)
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
                                keyboardActions = KeyboardActions(onSearch = { performSearch(query) }),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (query.isEmpty()) {
                                            Text(
                                                text = if (searchMode == "STUDIO") "Search anime studios or titles..." else "Search shows, movies, games...",
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

                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { 
                                        query = ""
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
                }

                // Tags Horizontal Row directly below search bar
                if (searchMode == "STUDIO") {
                    // Studio Tags Horizontal Row (Matching genre tags UI, no borders)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        items(AnimeStudioRepository.ALL_STUDIO_NAMES) { studioName ->
                            val isSelected = activeStudio.equals(studioName, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) Color(0xFFFF6B00) else Color(0xFF1E1E1E))
                                    .clickable {
                                        if (isSelected) {
                                            activeStudio = ""
                                            searchMode = "GENRE"
                                            searchResults = emptyList()
                                        } else {
                                            filterByStudio(studioName)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = studioName,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    // Genre Tags Horizontal Row (Standard AniList Genres, no borders)
                    val staticGenres = listOf(
                        "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Hentai",
                        "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological",
                        "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller"
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        items(staticGenres) { genreName ->
                            val isSelected = activeGenre.equals(genreName, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) Color(0xFFFF6B00) else Color(0xFF1E1E1E))
                                    .clickable {
                                        if (isSelected) {
                                            activeGenre = ""
                                            searchResults = emptyList()
                                        } else {
                                            filterByGenre(genreName)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = genreName,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFF6B00))
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
                        // 1. Recent Searches (When query is empty)
                        if (query.isEmpty() && recentSearchesList.isNotEmpty()) {
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
                                            color = Color(0xFFFF6B00),
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
                                                    query = recentText
                                                    performSearch(recentText)
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

                        // 2. Typeahead suggestions (when query is being typed - max 4 suggestions)
                        if (query.isNotBlank() && searchResults.isNotEmpty()) {
                            val suggestions = (listOf(query) + searchResults.map { it.title }).distinct().take(4)
                            item(span = { GridItemSpan(3) }) {
                                Column {
                                    suggestions.forEach { suggestionText ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    query = suggestionText
                                                    performSearch(suggestionText)
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

                        // 3. Search Results or Empty State
                        if (searchResults.isNotEmpty()) {
                            item(span = { GridItemSpan(3) }) {
                                Text(
                                    text = if (query.isNotEmpty()) "Top Results" else "Recommended Shows & Movies",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                )
                            }

                            items(searchResults) { anime ->
                                SearchPosterCard(
                                    anime = anime,
                                    onClick = {
                                        val intent = Intent(this@AnimeBoxSearchActivity, AnimeBoxDetailActivity::class.java).apply {
                                            putExtra("anilistId", anime.id)
                                        }
                                        startActivity(intent)
                                    }
                                )
                            }
                        } else if ((query.isNotEmpty() || activeStudio.isNotEmpty() || activeGenre.isNotEmpty()) && !isLoading) {
                            item(span = { GridItemSpan(3) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val emptyMsg = when {
                                        activeStudio.isNotEmpty() -> "No anime found for \"$activeStudio\""
                                        activeGenre.isNotEmpty() -> "No anime found in \"$activeGenre\""
                                        else -> "No anime results found for \"$query\""
                                    }
                                    Text(
                                        text = emptyMsg,
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

    @Composable
    fun SearchPosterCard(
        anime: SearchAnimeBrief,
        onClick: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E22))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = anime.coverUrl),
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (anime.rating.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = anime.rating,
                            color = Color(0xFFFFB300),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = anime.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    private fun parseSearchResults(jsonString: String): List<SearchAnimeBrief> {
        val list = mutableListOf<SearchAnimeBrief>()
        try {
            val obj = JSONObject(jsonString)
            val mediaArray = obj.getJSONObject("data").getJSONObject("Page").getJSONArray("media")
            for (i in 0 until mediaArray.length()) {
                val media = mediaArray.getJSONObject(i)
                if (AniListClient.isBlockedMedia(media)) continue
                val isAdult = if (media.has("isAdult") && !media.isNull("isAdult")) {
                    media.getBoolean("isAdult")
                } else false
                if (isAdult) continue

                val id = media.getInt("id")
                val titleObj = media.getJSONObject("title")
                val title = if (titleObj.has("english") && !titleObj.isNull("english")) {
                    titleObj.getString("english")
                } else {
                    titleObj.getString("romaji")
                }
                val coverUrl = media.getJSONObject("coverImage").getString("large")
                val score = if (media.has("averageScore") && !media.isNull("averageScore")) {
                    media.getInt("averageScore").toString()
                } else ""

                list.add(SearchAnimeBrief(id, title, coverUrl, score))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun parseStudioSearchResults(jsonString: String): List<SearchAnimeBrief> {
        val list = mutableListOf<SearchAnimeBrief>()
        try {
            val obj = JSONObject(jsonString)
            val dataObj = obj.getJSONObject("data")
            if (!dataObj.has("Studio") || dataObj.isNull("Studio")) return list
            val studioObj = dataObj.getJSONObject("Studio")
            val mediaObj = studioObj.getJSONObject("media")
            val nodesArray = mediaObj.getJSONArray("nodes")
            val seenIds = mutableSetOf<Int>()
            for (i in 0 until nodesArray.length()) {
                val media = nodesArray.getJSONObject(i)
                if (AniListClient.isBlockedMedia(media)) continue
                val isAdult = if (media.has("isAdult") && !media.isNull("isAdult")) {
                    media.getBoolean("isAdult")
                } else false
                if (isAdult) continue

                val id = media.getInt("id")
                if (!seenIds.add(id)) continue
                val titleObj = media.getJSONObject("title")
                val title = if (titleObj.has("english") && !titleObj.isNull("english")) {
                    titleObj.getString("english")
                } else {
                    titleObj.getString("romaji")
                }
                val coverUrl = media.getJSONObject("coverImage").getString("large")
                val score = if (media.has("averageScore") && !media.isNull("averageScore")) {
                    media.getInt("averageScore").toString()
                } else ""

                list.add(SearchAnimeBrief(id, title, coverUrl, score))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}

val CustomBackChevronIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "CustomBackChevron",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
        strokeLineWidth = 2.4f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(15f, 18f)
        lineTo(9f, 12f)
        lineTo(15f, 6f)
    }.build()
