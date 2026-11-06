package com.lagradost.cloudstream3.ui.animebox.subtitles

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings
import java.io.File
import java.io.FileOutputStream

data class SubtitleFontOption(
    val id: String,
    val name: String,
    val category: String = "Curated",
    val systemFontFamily: String = "",
    val fontResId: Int = 0
)

object SubtitleFontManager {

    const val CUSTOM_FONT_ID = "custom_imported"
    const val DEFAULT_FONT_NAME = "Default (Google Sans)"

    // Curated list of 30 anime and cinematic fonts with Default on top
    val CURATED_FONTS = listOf(
        SubtitleFontOption(
            id = "default_google_sans",
            name = DEFAULT_FONT_NAME,
            category = "AnimeBox Recommended",
            fontResId = R.font.google_sans,
            systemFontFamily = "sans-serif"
        ),
        SubtitleFontOption(
            id = "trebuchet_ms",
            name = "Trebuchet MS (Anime Hardsub)",
            category = "Classic Anime",
            fontResId = R.font.trebuchet_ms_bold,
            systemFontFamily = "sans-serif-bold"
        ),
        SubtitleFontOption(
            id = "product_sans",
            name = "Product Sans Bold",
            category = "Modern Sans",
            fontResId = R.font.productsans_bold,
            systemFontFamily = "sans-serif-bold"
        ),
        SubtitleFontOption(
            id = "roboto",
            name = "Roboto",
            category = "Clean & Neutral",
            systemFontFamily = "sans-serif"
        ),
        SubtitleFontOption(
            id = "roboto_medium",
            name = "Roboto Medium",
            category = "Clean & Neutral",
            systemFontFamily = "sans-serif-medium"
        ),
        SubtitleFontOption(
            id = "roboto_condensed",
            name = "Roboto Condensed",
            category = "Compact",
            systemFontFamily = "sans-serif-condensed"
        ),
        SubtitleFontOption(
            id = "roboto_black",
            name = "Roboto Black (Boldest)",
            category = "Compact",
            systemFontFamily = "sans-serif-black"
        ),
        SubtitleFontOption(
            id = "open_sans",
            name = "Open Sans",
            category = "Modern Sans",
            systemFontFamily = "sans-serif"
        ),
        SubtitleFontOption(
            id = "montserrat",
            name = "Montserrat",
            category = "Modern Sans",
            systemFontFamily = "sans-serif"
        ),
        SubtitleFontOption(
            id = "poppins",
            name = "Poppins",
            category = "Geometric Sans",
            systemFontFamily = "sans-serif-medium"
        ),
        SubtitleFontOption(
            id = "inter",
            name = "Inter",
            category = "Modern Sans",
            systemFontFamily = "sans-serif"
        ),
        SubtitleFontOption(
            id = "oswald",
            name = "Oswald (Condensed Display)",
            category = "Display & Dramatic",
            systemFontFamily = "sans-serif-condensed"
        ),
        SubtitleFontOption(
            id = "raleway",
            name = "Raleway",
            category = "Elegant Sans",
            systemFontFamily = "sans-serif"
        ),
        SubtitleFontOption(
            id = "nunito",
            name = "Nunito (Soft Rounded)",
            category = "Rounded",
            systemFontFamily = "casual"
        ),
        SubtitleFontOption(
            id = "lato",
            name = "Lato",
            category = "Warm & Readable",
            systemFontFamily = "sans-serif"
        ),
        SubtitleFontOption(
            id = "bebas_neue",
            name = "Bebas Neue (Cinematic)",
            category = "Display & Dramatic",
            systemFontFamily = "sans-serif-condensed-bold"
        ),
        SubtitleFontOption(
            id = "ubuntu",
            name = "Ubuntu",
            category = "Distinctive",
            systemFontFamily = "sans-serif"
        ),
        SubtitleFontOption(
            id = "merriweather",
            name = "Merriweather",
            category = "Editorial Serif",
            systemFontFamily = "serif"
        ),
        SubtitleFontOption(
            id = "playfair_display",
            name = "Playfair Display",
            category = "Editorial Serif",
            systemFontFamily = "serif"
        ),
        SubtitleFontOption(
            id = "lora",
            name = "Lora",
            category = "Calligraphic Serif",
            systemFontFamily = "serif"
        ),
        SubtitleFontOption(
            id = "cinzel",
            name = "Cinzel (Fantasy Epic)",
            category = "Cinematic / Historic",
            systemFontFamily = "serif"
        ),
        SubtitleFontOption(
            id = "comfortaa",
            name = "Comfortaa (Ultra Rounded)",
            category = "Rounded",
            systemFontFamily = "casual"
        ),
        SubtitleFontOption(
            id = "righteous",
            name = "Righteous (Sci-Fi Retro)",
            category = "Retro / Cyber",
            systemFontFamily = "sans-serif-bold"
        ),
        SubtitleFontOption(
            id = "caveat",
            name = "Caveat (Handwritten)",
            category = "Script / Artistic",
            systemFontFamily = "cursive"
        ),
        SubtitleFontOption(
            id = "permanent_marker",
            name = "Permanent Marker (Manga SFX)",
            category = "Manga / Expressive",
            systemFontFamily = "sans-serif-black"
        ),
        SubtitleFontOption(
            id = "satisfy",
            name = "Satisfy (Brush Script)",
            category = "Script / Artistic",
            systemFontFamily = "cursive"
        ),
        SubtitleFontOption(
            id = "monospace_clean",
            name = "Monospace Terminal",
            category = "Monospaced",
            systemFontFamily = "monospace"
        ),
        SubtitleFontOption(
            id = "serif_classic",
            name = "Serif Classic",
            category = "Editorial Serif",
            systemFontFamily = "serif"
        ),
        SubtitleFontOption(
            id = "sans_light",
            name = "Sans-Serif Light",
            category = "Clean & Neutral",
            systemFontFamily = "sans-serif-light"
        ),
        SubtitleFontOption(
            id = "sans_bold",
            name = "Sans-Serif Heavy Bold",
            category = "Clean & Neutral",
            systemFontFamily = "sans-serif-bold"
        )
    )

    /**
     * Import custom TTF / OTF font from content URI to internal app storage
     */
    fun importFontFromUri(context: Context, uri: Uri): Result<String> {
        return try {
            val contentResolver = context.contentResolver
            val fontsDir = File(context.filesDir, "custom_fonts")
            if (!fontsDir.exists()) fontsDir.mkdirs()

            val targetFile = File(fontsDir, "user_custom_sub_font.ttf")
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return Result.failure(Exception("Could not open font file stream"))

            // Verify file can be loaded as Typeface
            val testTf = Typeface.createFromFile(targetFile)
            if (testTf == null) {
                targetFile.delete()
                return Result.failure(Exception("File is not a valid TTF/OTF font"))
            }

            AnimeBoxSettings.setCustomSubFontPath(context, targetFile.absolutePath)
            AnimeBoxSettings.setSubFontFamily(context, "Custom Imported Font")
            Result.success("Font imported successfully!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolve Typeface for ExoPlayer SubtitleView
     */
    fun getTypeface(context: Context, targetFontName: String? = null): Typeface {
        val selected = targetFontName ?: AnimeBoxSettings.getSubFontFamily(context)

        // Custom imported font
        if (selected.equals("Custom Imported Font", ignoreCase = true)) {
            val path = AnimeBoxSettings.getCustomSubFontPath(context)
            if (path.isNotBlank()) {
                val f = File(path)
                if (f.exists() && f.length() > 0) {
                    try {
                        val tf = Typeface.createFromFile(f)
                        if (tf != null) return tf
                    } catch (_: Throwable) {}
                }
            }
        }

        val option = CURATED_FONTS.find { it.name.equals(selected, ignoreCase = true) }
            ?: CURATED_FONTS.first()

        if (option.fontResId != 0) {
            try {
                val tf = androidx.core.content.res.ResourcesCompat.getFont(context, option.fontResId)
                if (tf != null) return tf
            } catch (_: Throwable) {}
        }

        if (option.systemFontFamily.isNotBlank()) {
            try {
                return Typeface.create(option.systemFontFamily, Typeface.BOLD)
            } catch (_: Throwable) {}
        }

        return Typeface.create("sans-serif", Typeface.BOLD)
    }

    /**
     * Resolve FontFamily for Jetpack Compose Subtitle Preview & Player Overlay
     */
    fun getFontFamily(context: Context, targetFontName: String? = null): FontFamily {
        val selected = targetFontName ?: AnimeBoxSettings.getSubFontFamily(context)

        if (selected.equals("Custom Imported Font", ignoreCase = true)) {
            val path = AnimeBoxSettings.getCustomSubFontPath(context)
            if (path.isNotBlank()) {
                val f = File(path)
                if (f.exists() && f.length() > 0) {
                    try {
                        val tf = Typeface.createFromFile(f)
                        if (tf != null) return FontFamily(tf)
                    } catch (_: Throwable) {}
                }
            }
        }

        val option = CURATED_FONTS.find { it.name.equals(selected, ignoreCase = true) }
            ?: CURATED_FONTS.first()

        if (option.fontResId != 0) {
            try {
                return FontFamily(androidx.compose.ui.text.font.Font(option.fontResId))
            } catch (_: Throwable) {}
        }

        if (option.systemFontFamily.isNotBlank()) {
            try {
                val tf = Typeface.create(option.systemFontFamily, Typeface.BOLD)
                return FontFamily(tf)
            } catch (_: Throwable) {}
        }

        return FontFamily.Default
    }

    @Composable
    fun SubtitleFontSelectorDialog(
        show: Boolean,
        currentFont: String,
        onSelectFont: (String) -> Unit,
        onImportFontClick: () -> Unit,
        onDismiss: () -> Unit
    ) {
        if (!show) return
        val context = LocalContext.current

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.82f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF16161C))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Subtitle Font",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Choose from 30 styles or import custom font",
                                    color = Color(0xFF8E8E98),
                                    fontSize = 12.sp
                                )
                            }

                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Import Custom Font Button
                        val customFontPath = AnimeBoxSettings.getCustomSubFontPath(context)
                        val hasCustomFont = customFontPath.isNotBlank() && File(customFontPath).exists()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF222430))
                                .clickable { onImportFontClick() }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Import",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Import Custom Font (TTF / OTF)",
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (hasCustomFont) "Custom font loaded • Tap to replace" else "Select any .ttf or .otf file from storage",
                                    color = Color(0xFFA0A0AB),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (hasCustomFont) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val isCustomSel = currentFont.equals("Custom Imported Font", ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCustomSel) Color(0xFF2E3244) else Color(0xFF1B1B22))
                                    .clickable { onSelectFont("Custom Imported Font") }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "★ Custom Imported Font",
                                    color = if (isCustomSel) Color.White else Color(0xFFD4D4D8),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCustomSel) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 30 Curated Fonts List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(CURATED_FONTS.size) { idx ->
                                val fontOption = CURATED_FONTS[idx]
                                val isSel = currentFont.equals(fontOption.name, ignoreCase = true)
                                val resolvedFamily = remember(fontOption.name) {
                                    getFontFamily(context, fontOption.name)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSel) Color(0xFF2E3244)
                                            else Color.Transparent
                                        )
                                        .clickable { onSelectFont(fontOption.name) }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = fontOption.name,
                                            color = if (isSel) Color.White else Color(0xFFE2E2E6),
                                            fontSize = 13.5.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = "Subtitles • 字幕 • 123",
                                            fontFamily = resolvedFamily,
                                            color = if (isSel) Color(0xFF90B8F8) else Color(0xFF8E8E98),
                                            fontSize = 12.sp
                                        )
                                    }

                                    if (isSel) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
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
}

