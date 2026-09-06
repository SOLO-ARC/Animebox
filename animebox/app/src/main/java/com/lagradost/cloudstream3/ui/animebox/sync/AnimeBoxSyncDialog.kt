package com.lagradost.cloudstream3.ui.animebox.sync

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.rememberAsyncImagePainter
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SyncProvider {
    ALL,
    ANILIST
}

@Composable
fun AnimeBoxSyncDialog(
    profileId: String,
    initialProvider: SyncProvider = SyncProvider.ANILIST,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val activeProfile = remember(profileId) {
        ProfileManager.getProfiles(context).find { it.id == profileId }
            ?: ProfileManager.getProfiles(context).firstOrNull()
    }

    var anilistUser by remember { mutableStateOf(AnimeBoxAccountSyncManager.getAniListUser(context, profileId)) }
    var isAniListSyncing by remember { mutableStateOf(false) }
    var showAniListLoginWeb by remember { mutableStateOf(false) }

    val anilistClientId = remember {
        val key = BuildConfig.ANILIST_KEY
        if (key.isNullOrBlank() || key == "null") "49557" else key
    }

    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E1E1E))
                    .clickable(enabled = false) {}
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Header: Official AniList Icon, Title, Status, Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2B2B32)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_anilist_official),
                                    contentDescription = "AniList",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "AniList Sync",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = if (anilistUser != null) "Connected as @${anilistUser?.username}" else "Profile: ${activeProfile?.name ?: "Guest"}",
                                    color = if (anilistUser != null) Color(0xFFE0E0E0) else Color(0xFF9E9EA8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2B2B32))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (anilistUser != null) {
                        val user = anilistUser!!

                        // Connected User Card (Monochrome dark)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF282828))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (user.avatarUrl.isNotEmpty()) {
                                Image(
                                    painter = rememberAsyncImagePainter(model = user.avatarUrl),
                                    contentDescription = user.username,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, Color(0xFF484852), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "@${user.username}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "AniList ID: ${user.id}",
                                    color = Color(0xFFA5A5B0),
                                    fontSize = 11.5.sp
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = "My Lists Sync Active",
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            TextButton(
                                onClick = {
                                    AnimeBoxAccountSyncManager.logoutAniList(context, profileId)
                                    anilistUser = null
                                    Toast.makeText(context, "AniList disconnected", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                            ) {
                                Text("Disconnect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Sync Now Button (Monochrome White on Black)
                        Button(
                            onClick = {
                                val token = AnimeBoxAccountSyncManager.getAniListToken(context, profileId)
                                if (token != null) {
                                    isAniListSyncing = true
                                    coroutineScope.launch {
                                        val refreshedUser = AnimeBoxAccountSyncManager.fetchAniListUserProfile(token)
                                        if (refreshedUser != null) {
                                            AnimeBoxAccountSyncManager.saveAniListAuth(context, profileId, token, refreshedUser)
                                            anilistUser = refreshedUser
                                        }
                                        val stats = AnimeBoxAccountSyncManager.syncAniListWatchlist(context, profileId)
                                        withContext(Dispatchers.Main) {
                                            isAniListSyncing = false
                                            if (stats.success) {
                                                Toast.makeText(
                                                    context,
                                                    "My Lists synced! ${stats.importedCount} from AniList, ${stats.uploadedCount} uploaded.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                Toast.makeText(context, "AniList synced successfully!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isAniListSyncing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                                disabledContainerColor = Color(0xFF505050),
                                disabledContentColor = Color(0xFF909090)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Sync",
                                tint = Color.Black,
                                modifier = Modifier
                                    .size(17.dp)
                                    .then(if (isAniListSyncing) Modifier.rotate(rotation) else Modifier)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAniListSyncing) "Syncing My Lists..." else "Sync My Lists with AniList",
                                color = Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Not connected state
                        Text(
                            text = "Connect your AniList account to merge your anime library with My Lists, keeping your watchlist status synchronized both ways.",
                            color = Color(0xFF9E9EA8),
                            fontSize = 13.sp,
                            lineHeight = 18.5.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Connect Button (Monochrome White Button)
                        Button(
                            onClick = { showAniListLoginWeb = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Connect with AniList",
                                color = Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // AniList OAuth In-App Web Modal (Automatic token capture on authorize)
    // ------------------------------------------------------------------------
    if (showAniListLoginWeb) {
        Dialog(
            onDismissRequest = { showAniListLoginWeb = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF12131A))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_anilist_official),
                                contentDescription = "AniList",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Log In with AniList", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        IconButton(onClick = { showAniListLoginWeb = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                @SuppressLint("SetJavaScriptEnabled")
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"

                                fun handleAniListUrl(url: String): Boolean {
                                    if (url.contains("access_token=") || url.contains("anilistlogin")) {
                                        val token = extractTokenFromUrl(url)
                                        if (!token.isNullOrBlank()) {
                                            coroutineScope.launch {
                                                val user = AnimeBoxAccountSyncManager.fetchAniListUserProfile(token)
                                                withContext(Dispatchers.Main) {
                                                    if (user != null) {
                                                        AnimeBoxAccountSyncManager.saveAniListAuth(context, profileId, token, user)
                                                        anilistUser = user
                                                        showAniListLoginWeb = false
                                                        Toast.makeText(context, "Connected to AniList as @${user.username}!", Toast.LENGTH_LONG).show()
                                                        coroutineScope.launch {
                                                            AnimeBoxAccountSyncManager.syncAniListWatchlist(context, profileId)
                                                        }
                                                    }
                                                }
                                            }
                                            return true
                                        }
                                    }
                                    return false
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        return handleAniListUrl(url)
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        url?.let { handleAniListUrl(it) }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        url?.let { handleAniListUrl(it) }
                                    }
                                }

                                val authUrl = "https://anilist.co/api/v2/oauth/authorize?client_id=$anilistClientId&response_type=token"
                                loadUrl(authUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// OAuth Helper
// ----------------------------------------------------------------------------

private fun extractTokenFromUrl(url: String): String? {
    return try {
        if (url.contains("access_token=")) {
            url.substringAfter("access_token=").substringBefore("&")
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}
