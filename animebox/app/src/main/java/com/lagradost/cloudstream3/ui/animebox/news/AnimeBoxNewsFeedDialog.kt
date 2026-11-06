package com.lagradost.cloudstream3.ui.animebox.news

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.rememberAsyncImagePainter
import com.lagradost.cloudstream3.utils.AppContextUtils.openBrowser

@Composable
fun AnimeBoxNewsFeedDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("All") }
    var newsList by remember { mutableStateOf<List<AnimeCornerNewsItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val categories = listOf("All", "News", "Editor's Picks")

    LaunchedEffect(selectedCategory) {
        isLoading = true
        newsList = AnimeCornerNewsClient.getNews(selectedCategory)
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101014))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar (Monochrome theme matching My Space)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF16161C))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Anime Corner News",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Latest Anime News & Editor's Picks",
                                color = Color(0xFF8E8E98),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            isLoading = true
                            newsList = emptyList()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White
                        )
                    }
                }

                // Category Filter Tabs Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141418))
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) Color.White else Color(0xFF22222A))
                                .border(
                                    1.dp,
                                    if (isSelected) Color.White else Color(0xFF33333E),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // News Feed Content List
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NewspaperPageFlipAnimation(
                                modifier = Modifier.padding(bottom = 2.dp),
                                bookWidth = 54f,
                                bookHeight = 38f
                            )
                            Text(
                                text = "Loading Anime Corner news...",
                                color = Color(0xFF8E8E98),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (newsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No news articles found. Tap refresh to try again.",
                            color = Color(0xFF8E8E98),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(newsList, key = { it.id + it.link }) { article ->
                            NewsArticleCard(
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
private fun NewsArticleCard(
    article: AnimeCornerNewsItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF282832), RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Featured Article Image
            if (article.imageUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF1C1C22))
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = article.imageUrl),
                        contentDescription = article.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Subtle bottom gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFF16161C))
                                )
                            )
                    )

                    // Category Pill Tag
                    Box(
                        modifier = Modifier
                            .padding(10.dp)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = article.category.uppercase(),
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Article Details Padding Container
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = article.title,
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (article.excerpt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = article.excerpt,
                        color = Color(0xFFA0A0AA),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${article.author} • ${article.date}",
                        color = Color(0xFF72727C),
                        fontSize = 11.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Read Article",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Read",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
