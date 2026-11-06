package com.lagradost.cloudstream3.ui.animebox.news

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.lagradost.cloudstream3.ui.animebox.CustomSearchIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import com.lagradost.cloudstream3.utils.AppContextUtils.openBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AnimeBoxNewsFeedView(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var newsList by remember { mutableStateOf<List<AnimeCornerNewsItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    fun loadNews() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val items = withContext(Dispatchers.IO) {
                    AnimeCornerNewsClient.getNews("All")
                }
                // Filter out any articles mentioning "interview" in their title
                newsList = items.filter { !it.title.contains("interview", ignoreCase = true) }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to load Anime Corner news"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadNews()
    }

    val filteredNews = remember(searchQuery, newsList) {
        if (searchQuery.isBlank()) {
            newsList
        } else {
            newsList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.excerpt.contains(searchQuery, ignoreCase = true) ||
                it.author.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Search Bar (No arrow button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
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
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            cursorBrush = SolidColor(Color.White),
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
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
                                            text = "Search anime news, articles...",
                                            color = Color(0xFF8E8E93),
                                            fontSize = 14.5.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF8E8E93),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            com.lagradost.cloudstream3.ui.animebox.components.ThemeDotCircularProgressIndicator(
                                modifier = Modifier.padding(bottom = 4.dp),
                                size = 38.dp
                            )
                            Text(
                                text = "Fetching latest stories...",
                                color = Color(0xFF9E9EA8),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (errorMessage != null && newsList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "Could not load news",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = errorMessage ?: "Unknown error",
                                color = Color(0xFF8E8E98),
                                fontSize = 13.sp
                            )
                            Button(
                                onClick = { loadNews() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (filteredNews.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No news found",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Try searching for another topic",
                                color = Color(0xFF8E8E98),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    // Two news items at a row (2-column grid)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredNews, key = { it.id + it.link }) { article ->
                            NewsFeedGridCard(
                                article = article,
                                onClick = {
                                    if (article.link.isNotBlank()) {
                                        context.openBrowser(article.link)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsFeedGridCard(
    article: AnimeCornerNewsItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF18181E))
            .clickable { onClick() }
    ) {
        // Thumbnail Image
        if (article.imageUrl.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(Color(0xFF101014))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(article.imageUrl),
                    contentDescription = article.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            // Title
            Text(
                text = article.title,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 16.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Author / Date info (No arrow button)
            Text(
                text = if (article.date.isNotBlank()) article.date else article.author,
                color = Color(0xFF8E8E98),
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
