package com.lagradost.cloudstream3.ui.animebox.games

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import com.lagradost.cloudstream3.utils.AppContextUtils.openBrowser

@Composable
fun AnimeGamesScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredGames = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            AnimeGamesCatalog.GAMES
        } else {
            AnimeGamesCatalog.GAMES.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.franchise.contains(searchQuery, ignoreCase = true)
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
            // 1. Search Bar Header on Top (No arrow button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Input Box (#232328 rounded pill/box)
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
                                            text = "Search anime games, franchises...",
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

            // 2. Games Grid (2 games logos per row)
            if (filteredGames.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No games found",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Try searching for another anime title",
                            color = Color(0xFF8E8E98),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredGames, key = { it.packageName }) { game ->
                        AnimeGameGridCard(
                            game = game,
                            onClick = {
                                openPlayStore(context, game.packageName, game.playStoreUrl)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeGameGridCard(
    game: AnimeMobileGame,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        // Square Logo (Clean borderless)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E26)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = rememberAsyncImagePainter(game.logoUrl),
                contentDescription = game.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        // Game Title
        Text(
            text = game.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Franchise / Tag
        Text(
            text = game.franchise,
            color = Color(0xFF8E8E98),
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun openPlayStore(context: Context, packageName: String, playStoreUrl: String) {
    try {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(marketIntent)
    } catch (_: Exception) {
        try {
            context.openBrowser(playStoreUrl)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
