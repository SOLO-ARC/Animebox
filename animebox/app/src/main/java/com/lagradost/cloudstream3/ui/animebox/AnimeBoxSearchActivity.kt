package com.lagradost.cloudstream3.ui.animebox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
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
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD0BCFF),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                SearchScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SearchScreen() {
        var query by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<SearchAnimeBrief>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        val performSearch = {
            searchJob?.cancel()
            focusManager.clearFocus()
            if (query.isNotBlank()) {
                isLoading = true
                searchJob = coroutineScope.launch {
                    kotlinx.coroutines.delay(400L)
                    val response = AniListClient.searchAnime(query)
                    searchResults = if (response != null) parseSearchResults(response) else emptyList()
                    isLoading = false
                }
            } else {
                searchResults = emptyList()
                isLoading = false
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Search Anime", fontWeight = FontWeight.Bold, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
                )
            },
            containerColor = Color(0xFF121212)
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    placeholder = { Text("Type anime title...", color = Color.Gray) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "SearchIcon", tint = Color.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color.DarkGray
                    )
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFD0BCFF))
                    }
                } else {
                    if (searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (query.isEmpty()) "Find your favorite anime shows" else "No anime results found",
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
                                .padding(bottom = 16.dp)
                        ) {
                            items(searchResults) { anime ->
                                SearchPosterCard(anime = anime, onClick = {
                                    val intent = Intent(this@AnimeBoxSearchActivity, AnimeBoxDetailActivity::class.java).apply {
                                        putExtra("anilistId", anime.id)
                                    }
                                    startActivity(intent)
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SearchPosterCard(anime: SearchAnimeBrief, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = anime.coverUrl),
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (anime.rating.isNotEmpty() && anime.rating != "null") {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xE68E24AA))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${anime.rating}%",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = anime.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
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
}
