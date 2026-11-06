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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import android.view.WindowManager
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_anilist_official),
                    contentDescription = "AniList",
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "AniList Sync",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    if (anilistUser != null) {
                        Text(
                            text = "Connected as @${anilistUser?.username}",
                            color = Color(0xFF02A9FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        text = {
            if (anilistUser != null) {
                val user = anilistUser!!
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF1E1E22))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (user.avatarUrl.isNotEmpty()) {
                            Image(
                                painter = rememberAsyncImagePainter(model = user.avatarUrl),
                                contentDescription = user.username,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color(0xFF484852), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "@${user.username}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "My Lists Sync Active",
                                color = Color(0xFFA5A5B0),
                                fontSize = 11.5.sp
                            )
                        }

                        TextButton(
                            onClick = {
                                AnimeBoxAccountSyncManager.logoutAniList(context, profileId)
                                anilistUser = null
                                com.lagradost.cloudstream3.ui.animebox.library.LibraryManager.notifyLibraryChanged()
                                Toast.makeText(context, "AniList disconnected", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                        ) {
                            Text("Disconnect", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Text(
                    text = "Connect your AniList account to merge your anime library with My Lists, keeping your watchlist and history synchronized across devices.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            }
        },
        confirmButton = {
            if (anilistUser != null) {
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
                                    com.lagradost.cloudstream3.ui.animebox.library.LibraryManager.notifyLibraryChanged()
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    if (isAniListSyncing) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Sync",
                            tint = Color.Black,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(rotation)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Syncing...", color = Color.Black, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Sync Now", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = { showAniListLoginWeb = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Text("Connect AniList", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8E918F))
            }
        },
        containerColor = Color(0xFF141416),
        shape = RoundedCornerShape(2.dp)
    )

    // ------------------------------------------------------------------------
    // AniList OAuth In-App Web Modal (Automatic token capture on authorize)
    // ------------------------------------------------------------------------
    if (showAniListLoginWeb) {
        Dialog(
            onDismissRequest = { showAniListLoginWeb = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            val view = LocalView.current
            SideEffect {
                (view.parent as? DialogWindowProvider)?.window?.let { window ->
                    window.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT
                    )
                    window.statusBarColor = android.graphics.Color.parseColor("#121319")
                    window.navigationBarColor = android.graphics.Color.parseColor("#121319")
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                }
            }

            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121319))) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Modern browser header with full edge-to-edge status bar coverage
                    Surface(
                        color = Color(0xFF121319),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // AniList Official Logo (bold & clean, no lock badge)
                            Image(
                                painter = painterResource(id = R.drawable.ic_anilist_official),
                                contentDescription = "AniList",
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            // Bold, browser URL box with very less rounded edges
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF1E202B))
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "anilist.co",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "/",
                                    color = Color(0xFF63677A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "oauth/authorize",
                                    color = Color(0xFFA5A9BD),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Tactile square close button with very less rounded edges
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF1E202B))
                                    .clickable { showAniListLoginWeb = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                val cookieManager = android.webkit.CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                @SuppressLint("SetJavaScriptEnabled")
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

                                fun handleAniListUrl(url: String): Boolean {
                                    if (url.contains("error=")) {
                                        Toast.makeText(context, "AniList login cancelled or rejected.", Toast.LENGTH_SHORT).show()
                                        showAniListLoginWeb = false
                                        return true
                                    }
                                    if (url.contains("access_token=") || url.contains("anilistlogin")) {
                                        val token = extractTokenFromUrl(url)
                                        if (!token.isNullOrBlank()) {
                                            coroutineScope.launch {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Verifying AniList account...", Toast.LENGTH_SHORT).show()
                                                }
                                                val user = AnimeBoxAccountSyncManager.fetchAniListUserProfile(token)
                                                withContext(Dispatchers.Main) {
                                                    if (user != null) {
                                                        AnimeBoxAccountSyncManager.saveAniListAuth(context, profileId, token, user)
                                                        anilistUser = user

                                                        // Auto-change profile name from Guest to AniList username
                                                        try {
                                                            val profiles = com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager.getProfiles(context).toMutableList()
                                                            val profIdx = profiles.indexOfFirst { it.id == profileId }
                                                            if (profIdx != -1) {
                                                                val currProf = profiles[profIdx]
                                                                if (currProf.name.equals("guest", ignoreCase = true) || currProf.name.isBlank()) {
                                                                    profiles[profIdx] = currProf.copy(name = user.username)
                                                                    com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager.saveProfiles(context, profiles)
                                                                }
                                                            }
                                                        } catch (_: Exception) {}

                                                        showAniListLoginWeb = false
                                                        Toast.makeText(context, "Connected to AniList as @${user.username}!", Toast.LENGTH_LONG).show()
                                                        coroutineScope.launch {
                                                            val stats = AnimeBoxAccountSyncManager.syncAniListWatchlist(context, profileId)
                                                            withContext(Dispatchers.Main) {
                                                                com.lagradost.cloudstream3.ui.animebox.library.LibraryManager.notifyLibraryChanged()
                                                                if (stats.success) {
                                                                    Toast.makeText(context, "My Lists synced! ${stats.importedCount} from AniList.", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        Toast.makeText(context, "Unable to load AniList profile. Please check your connection.", Toast.LENGTH_LONG).show()
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
                        modifier = Modifier.fillMaxSize().navigationBarsPadding()
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
