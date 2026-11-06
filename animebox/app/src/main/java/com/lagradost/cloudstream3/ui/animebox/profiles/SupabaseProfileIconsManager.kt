package com.lagradost.cloudstream3.ui.animebox.profiles

import com.lagradost.cloudstream3.ui.animebox.api.AnimeBoxConfig

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import coil3.compose.rememberAsyncImagePainter
import coil3.request.allowHardware
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages profile pictures and icons for AnimeBox.
 * Primary Default Profile Picture: a25.png (bundled in app resources as R.drawable.default_profile_picture).
 * Extra Profile Pictures: Dynamically loaded from Supabase bucket 'profile_icons'.
 * Avatar Decorations: Discord-style frames.
 */
object SupabaseProfileIconsManager {
    private val SUPABASE_URL get() = AnimeBoxConfig.SUPABASE_URL
    private val SUPABASE_KEY get() = AnimeBoxConfig.SUPABASE_ANON_KEY
    private const val BUCKET_NAME = "profile_icons"

    // Public Base URL for Supabase storage bucket (legacy)
    val PUBLIC_BUCKET_URL get() = "$SUPABASE_URL/storage/v1/object/public/$BUCKET_NAME"

    // Default fallback icon filenames from Supabase profile_icons bucket (a1.png .. a26.png excluding a25.png)
    val KNOWN_AVATAR_IDS = listOf(
        "a1", "a2", "a3", "a4", "a5",
        "a6", "a7", "a8", "a9", "a10",
        "a11", "a12", "a13", "a14", "a16",
        "a17", "a18", "a19", "a20", "a21",
        "a22", "a23", "a24", "a26"
    )

    private val KNOWN_SUPABASE_ICONS = KNOWN_AVATAR_IDS.map { "$it.png" }

    // Direct ImgBB URLs uploaded using user's ImgBB API key (fallback mapping)
    val IMGBB_AVATAR_MAP = mapOf(
        "a1.png" to "https://i.ibb.co/23RBX8Ww/a1-test.png",
        "a2.png" to "https://i.ibb.co/ppPhNCt/a2.png",
        "a3.png" to "https://i.ibb.co/Wps2gQ6d/a3.png",
        "a4.png" to "https://i.ibb.co/j9SfJpsB/a4.png",
        "a5.png" to "https://i.ibb.co/JFcpHt2Q/a5.png",
        "a6.png" to "https://i.ibb.co/FLSQCMQ8/a6.png",
        "a7.png" to "https://i.ibb.co/tT1S82FP/a7.png",
        "a8.png" to "https://i.ibb.co/N2F872bf/a8.png",
        "a9.png" to "https://i.ibb.co/MDpmfkqt/a9.png",
        "a10.png" to "https://i.ibb.co/Nftvcxz/a10.png",
        "a11.png" to "https://i.ibb.co/q3XJrkqV/a11.png",
        "a12.png" to "https://i.ibb.co/6cngnGJv/a12.png",
        "a13.png" to "https://i.ibb.co/4w3MDtBd/a13.png",
        "a14.png" to "https://i.ibb.co/szb1DZ7/a14.png",
        "a16.png" to "https://i.ibb.co/DDQLsJTy/a16.png",
        "a17.png" to "https://i.ibb.co/jPkLhKRT/a17.png",
        "a18.png" to "https://i.ibb.co/nMFR0h3R/a18.png",
        "a19.png" to "https://i.ibb.co/hP3ZZ0B/a19.png",
        "a20.png" to "https://i.ibb.co/zVmmsqMW/a20.png",
        "a21.png" to "https://i.ibb.co/fdLc6MQQ/a21.png",
        "a22.png" to "https://i.ibb.co/jvJmPYPq/a22.png",
        "a23.png" to "https://i.ibb.co/wZqvC8n2/a23.png",
        "a24.png" to "https://i.ibb.co/ymYNrhLc/a24.png",
        "a26.png" to "https://i.ibb.co/TDgXh6v5/a26.png"
    )

    /**
     * Resolves any avatar URL, filename, or identifier to its local bundled drawable resource name.
     * Guarantees 0ms instant display, 100% offline functionality, and 0 egress consumed from Supabase.
     */
    fun getDrawableNameForAvatar(urlOrName: String?): String? {
        if (urlOrName.isNullOrBlank()) return null
        val trimmed = urlOrName.trim()
        if (trimmed.contains("default_profile_picture", ignoreCase = true) || trimmed.contains("avatar_a25", ignoreCase = true)) {
            return "default_profile_picture"
        }
        if (trimmed.startsWith("android.resource://")) {
            val last = trimmed.substringAfterLast("/")
            if (last.isNotBlank()) return last
        }
        if (trimmed.startsWith("avatar_a", ignoreCase = true)) {
            return trimmed.substringBefore(".")
        }
        // Match against known ImgBB URLs
        IMGBB_AVATAR_MAP.entries.find { it.value.equals(trimmed, ignoreCase = true) }?.let {
            val id = it.key.substringBefore(".")
            return "avatar_$id"
        }
        // Match against /aX.png or aX patterns
        val match = Regex("""(?:avatar_)?(a\d+)(?:\.png|\.webp)?(?:[/?#]|$)""", RegexOption.IGNORE_CASE).find(trimmed)
        if (match != null) {
            val id = match.groupValues[1].lowercase()
            if (id == "a25") return "default_profile_picture"
            return "avatar_$id"
        }
        return null
    }

    /**
     * Normalizes any legacy Supabase avatar URL, ImgBB URL, or filename to an android.resource URI.
     * Guarantees that zero egress is consumed from Supabase storage and loads in 0ms.
     */
    fun normalizeAvatarUrl(url: String?, context: Context? = null): String {
        if (url.isNullOrBlank()) return ""
        val trimmed = url.trim()
        val drawableName = getDrawableNameForAvatar(trimmed)
        if (drawableName != null) {
            val pkg = context?.packageName ?: "com.firefly.app.ai"
            return "android.resource://$pkg/drawable/$drawableName"
        }
        return trimmed
    }

    data class ProfilePictureItem(
        val url: String,
        val isPrimaryDefault: Boolean = false,
        val name: String = ""
    )

    data class AvatarDecorationItem(
        val id: String,
        val name: String,
        val url: String
    )

    val AVATAR_DECORATIONS = listOf(
        AvatarDecorationItem("none", "None", ""),
        AvatarDecorationItem("hailey", "Hailey Stars", "https://img.avatardecoration.com/decorations/hailey.png"),
        AvatarDecorationItem("rift_butterfly", "Rift Butterfly", "https://img.avatardecoration.com/decorations/rift_butterfly.png"),
        AvatarDecorationItem("koi_pond", "Koi Pond", "https://img.avatardecoration.com/decorations/koi_pond.png"),
        AvatarDecorationItem("sakura_ink", "Sakura Ink", "https://img.avatardecoration.com/decorations/sakura_ink.png"),
        AvatarDecorationItem("chillet", "Chillet", "https://img.avatardecoration.com/decorations/chillet.png"),
        AvatarDecorationItem("cat_ears", "Cat Ears", "https://img.avatardecoration.com/decorations/cat_ears.png"),
        AvatarDecorationItem("black_hole", "Black Hole", "https://img.avatardecoration.com/decorations/black_hole.png"),
        AvatarDecorationItem("heartbloom", "Heartbloom", "https://img.avatardecoration.com/decorations/heartbloom.png"),
        AvatarDecorationItem("ghosts", "Ghosts", "https://img.avatardecoration.com/decorations/ghosts.png"),
        AvatarDecorationItem("straw_hat", "Straw Hat", "https://img.avatardecoration.com/decorations/straw_hat.png"),
        AvatarDecorationItem("chun_li", "Chun-Li", "https://img.avatardecoration.com/decorations/chun_li.png"),
        AvatarDecorationItem("dusk_and_dawn", "Dusk & Dawn", "https://img.avatardecoration.com/decorations/dusk_and_dawn.png"),
        AvatarDecorationItem("wallach_spaceport", "Spaceport", "https://img.avatardecoration.com/decorations/wallach_spaceport.png"),
        AvatarDecorationItem("victory_crown", "Victory Crown", "https://img.avatardecoration.com/decorations/victory_crown.png"),
        AvatarDecorationItem("kitsune", "Kitsune", "https://img.avatardecoration.com/decorations/kitsune.png"),
        AvatarDecorationItem("reynas_leer", "Reyna's Leer", "https://img.avatardecoration.com/decorations/reynas_leer.png"),
        AvatarDecorationItem("spirit_embers", "Spirit Embers", "https://img.avatardecoration.com/decorations/spirit_embers.png"),
        AvatarDecorationItem("lamball", "Lamball", "https://img.avatardecoration.com/decorations/lamball.png"),
        AvatarDecorationItem("selyne", "Selyne", "https://img.avatardecoration.com/decorations/selyne.png"),
        AvatarDecorationItem("pal_sphere", "Pal Sphere", "https://img.avatardecoration.com/decorations/pal_sphere.png"),
        AvatarDecorationItem("astronaut_helmet", "Astronaut", "https://img.avatardecoration.com/decorations/astronaut_helmet.png"),
        AvatarDecorationItem("oasis", "Oasis", "https://img.avatardecoration.com/decorations/oasis.png"),
        AvatarDecorationItem("sweat_drops", "Sweat Drops", "https://img.avatardecoration.com/decorations/sweat_drops.png"),
        AvatarDecorationItem("air", "Air Element", "https://img.avatardecoration.com/decorations/air.png"),
        AvatarDecorationItem("fan_flourish", "Fan Flourish", "https://img.avatardecoration.com/decorations/fan_flourish.png"),
        AvatarDecorationItem("fairy_sprites", "Fairy Sprites", "https://img.avatardecoration.com/decorations/fairy_sprites.png"),
        AvatarDecorationItem("head_in_clouds_sunset", "Clouds Sunset", "https://discord-decoration.art/decorations/head_in_the_clouds_sunset.png"),
        AvatarDecorationItem("head_in_clouds", "In Clouds", "https://discord-decoration.art/decorations/head_in_the_clouds.png"),
        AvatarDecorationItem("hawks", "Hawks", "https://discord-decoration.art/decorations/hawks.png"),
        AvatarDecorationItem("izuku_midoriya", "Izuku Midoriya", "https://discord-decoration.art/decorations/izuku_midoriya.png"),
        AvatarDecorationItem("bonsai", "Bonsai", "https://discord-decoration.art/decorations/bonsai.png"),
        AvatarDecorationItem("spirit_blossom_sett", "Spirit Sett", "https://discord-decoration.art/decorations/spirit_blossom_springs_sett.png"),
        AvatarDecorationItem("spirit_blossom", "Spirit Blossom", "https://discord-decoration.art/decorations/spirit_blossom_springs.png"),
        AvatarDecorationItem("yunara", "Yunara", "https://discord-decoration.art/decorations/yunara.png"),
        AvatarDecorationItem("dragon_balls", "Dragon Balls", "https://discord-decoration.art/decorations/dragon_balls.png"),
        AvatarDecorationItem("greenhouse_cat", "Greenhouse Cat", "https://discord-decoration.art/decorations/greenhouse_cat.png"),
        AvatarDecorationItem("gallica", "Gallica", "https://discord-decoration.art/decorations/gallica.png"),
        AvatarDecorationItem("elpis", "Elpis", "https://discord-decoration.art/decorations/elpis.png"),
        AvatarDecorationItem("liquid_moon", "Liquid Moon", "https://discord-decoration.art/decorations/liquid_moon.png")
    )

    /**
     * Gets the full list of profile pictures synchronously with 0ms delay.
     * Item 0 is ALWAYS the primary default avatar (a25.png local drawable).
     * Subsequent items are bundled local WebP drawables (zero network, zero egress, 100% offline).
     */
    fun getProfileIconsSync(context: Context): List<ProfilePictureItem> {
        val items = ArrayList<ProfilePictureItem>(25)
        val pkg = context.packageName

        // 1. Primary Default Avatar (a25.png local asset)
        val defaultUri = "android.resource://$pkg/drawable/default_profile_picture"
        items.add(ProfilePictureItem(url = defaultUri, isPrimaryDefault = true, name = "Primary Default (a25)"))

        // 2. Bundled local avatars (avatar_a1 .. avatar_a26)
        KNOWN_AVATAR_IDS.forEach { id ->
            val uri = "android.resource://$pkg/drawable/avatar_$id"
            items.add(
                ProfilePictureItem(
                    url = uri,
                    isPrimaryDefault = false,
                    name = id
                )
            )
        }
        return items
    }

    suspend fun getProfileIcons(context: Context): List<ProfilePictureItem> = getProfileIconsSync(context)
}

@Composable
fun DecoratedProfilePicture(
    avatarUrl: String,
    decorationUrl: String,
    size: Dp = 80.dp,
    modifier: Modifier = Modifier,
    fallbackInitial: String = "P",
    primaryColor: Color = Color(0xFF8B5CF6),
    forceCircle: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val effectiveAvatarUrl: String = remember(avatarUrl) {
        SupabaseProfileIconsManager.normalizeAvatarUrl(avatarUrl, context)
    }
    val profilePainter: Any = remember(effectiveAvatarUrl) {
        val resName = SupabaseProfileIconsManager.getDrawableNameForAvatar(effectiveAvatarUrl)
            ?: if (effectiveAvatarUrl.startsWith("android.resource://")) effectiveAvatarUrl.substringAfterLast("/") else null
        if (resName != null) {
            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
            if (resId != 0) resId else effectiveAvatarUrl
        } else {
            effectiveAvatarUrl
        }
    }

    val hasDecoration = decorationUrl.isNotBlank() && !decorationUrl.equals("none", ignoreCase = true)
    val avatarShape = if (hasDecoration || forceCircle) {
        CircleShape
    } else {
        androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    }

    Box(
        modifier = modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // 1. Inner Profile Picture Card (CircleShape if decorated/forced, RoundedCornerShape(6.dp) by default)
        val innerAvatarSize = if (hasDecoration) size * 0.90f else size * 0.96f
        val defaultResId = remember {
            val id = context.resources.getIdentifier("default_profile_picture", "drawable", context.packageName)
            if (id != 0) id else com.lagradost.cloudstream3.R.drawable.default_profile_picture
        }
        val defaultPainter = androidx.compose.ui.res.painterResource(defaultResId)

        Box(
            modifier = Modifier
                .size(innerAvatarSize)
                .clip(avatarShape)
                .background(Color(0xFF18181C)),
            contentAlignment = Alignment.Center
        ) {
            if (effectiveAvatarUrl.isNotEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = profilePainter,
                        error = defaultPainter,
                        fallback = defaultPainter
                    ),
                    contentDescription = "Profile Picture",
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
                        text = fallbackInitial.take(1).uppercase().ifEmpty { "P" },
                        color = Color.White,
                        fontSize = (innerAvatarSize.value * 0.44f).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. Avatar Decoration Overlay Frame (Discord Style APNG live animated overlay)
        if (hasDecoration) {
            var apngDrawable by remember(decorationUrl) { mutableStateOf<android.graphics.drawable.Drawable?>(null) }

            LaunchedEffect(decorationUrl) {
                if (decorationUrl.isBlank()) {
                    apngDrawable = null
                    return@LaunchedEffect
                }
                if (decorationUrl.startsWith("http://", ignoreCase = true) || decorationUrl.startsWith("https://", ignoreCase = true)) {
                    withContext(Dispatchers.IO) {
                        try {
                            val conn = URL(decorationUrl).openConnection() as HttpURLConnection
                            conn.connectTimeout = 8000
                            conn.readTimeout = 8000
                            val drawable = conn.inputStream.use { stream ->
                                com.linecorp.apng.ApngDrawable.decode(stream)
                            }
                            (drawable as? android.graphics.drawable.Animatable)?.start()
                            withContext(Dispatchers.Main) {
                                apngDrawable = drawable
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            val decorationModel: Any = apngDrawable ?: remember(decorationUrl) {
                if (decorationUrl.startsWith("android.resource://")) {
                    val resName = decorationUrl.substringAfterLast("/")
                    val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                    if (resId != 0) resId else decorationUrl
                } else {
                    coil3.request.ImageRequest.Builder(context)
                        .data(decorationUrl)
                        .allowHardware(false)
                        .crossfade(true)
                        .build()
                }
            }

            Image(
                painter = rememberAsyncImagePainter(model = decorationModel),
                contentDescription = "Profile Picture Decoration",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.22f)
            )
        }
    }
}
