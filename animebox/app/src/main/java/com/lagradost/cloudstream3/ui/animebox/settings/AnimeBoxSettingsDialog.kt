package com.lagradost.cloudstream3.ui.animebox.settings

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnimeBoxSettingsDialog(
    onDismiss: () -> Unit,
    onSettingsChanged: () -> Unit = {}
) {
    val context = LocalContext.current

    // Preference States
    var trailerEnabled by remember { mutableStateOf(AnimeBoxSettings.isTrailerEnabled(context)) }
    var lowPerfMode by remember { mutableStateOf(AnimeBoxSettings.isLowPerformanceMode(context)) }
    var appTheme by remember { mutableStateOf(AnimeBoxSettings.getAppTheme(context)) }
    var timelineTheme by remember { mutableStateOf(AnimeBoxSettings.getPlayerTimelineTheme(context)) }
    var skipIntroTheme by remember { mutableStateOf(AnimeBoxSettings.getSkipIntroTheme(context)) }
    var skipIntroEnabled by remember { mutableStateOf(AnimeBoxSettings.isSkipIntroEnabled(context)) }
    var brightnessMode by remember { mutableStateOf(AnimeBoxSettings.getBrightnessMode(context)) }
    var volumeMode by remember { mutableStateOf(AnimeBoxSettings.getVolumeMode(context)) }
    var defaultEpViewMode by remember { mutableStateOf(AnimeBoxSettings.getDefaultEpisodeViewMode(context)) }

    // Alert dialog state for import/export messages
    var alertTitle by remember { mutableStateOf("") }
    var alertMessage by remember { mutableStateOf("") }
    var showAlert by remember { mutableStateOf(false) }

    // Export Data SAF Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonStr = AnimeBoxSettings.exportDataToJson(context)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    OutputStreamWriter(os).use { writer ->
                        writer.write(jsonStr)
                    }
                }
                alertTitle = "Export Successful"
                alertMessage = "Your profile details, watchlist, watch history, timestamps, and settings have been exported successfully."
                showAlert = true
            } catch (e: Exception) {
                alertTitle = "Export Failed"
                alertMessage = "Could not export app data: ${e.localizedMessage}"
                showAlert = true
            }
        }
    }

    // Import Data SAF Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val stringBuilder = StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            stringBuilder.append(line)
                            line = reader.readLine()
                        }
                    }
                }
                val jsonContent = stringBuilder.toString()
                val result = AnimeBoxSettings.importDataFromJson(context, jsonContent)
                if (result.isSuccess) {
                    // Refresh state
                    trailerEnabled = AnimeBoxSettings.isTrailerEnabled(context)
                    lowPerfMode = AnimeBoxSettings.isLowPerformanceMode(context)
                    appTheme = AnimeBoxSettings.getAppTheme(context)
                    timelineTheme = AnimeBoxSettings.getPlayerTimelineTheme(context)
                    skipIntroTheme = AnimeBoxSettings.getSkipIntroTheme(context)
                    skipIntroEnabled = AnimeBoxSettings.isSkipIntroEnabled(context)
                    brightnessMode = AnimeBoxSettings.getBrightnessMode(context)
                    volumeMode = AnimeBoxSettings.getVolumeMode(context)
                    defaultEpViewMode = AnimeBoxSettings.getDefaultEpisodeViewMode(context)
                    onSettingsChanged()

                    alertTitle = "Import Successful"
                    alertMessage = "All app data, profiles, My List, history timestamps, and settings have been imported successfully!"
                    showAlert = true
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unrecognized backup format."
                    alertTitle = "Import Failed"
                    alertMessage = "Failed to import data: $errorMsg\n\nPlease ensure you selected a valid AnimeBox backup file."
                    showAlert = true
                }
            } catch (e: Exception) {
                alertTitle = "Import Failed"
                alertMessage = "Error reading backup file: ${e.localizedMessage}"
                showAlert = true
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Settings",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Divider(color = Color(0xFF222222), thickness = 1.dp)

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // -------------------------------------------------------------
                    // SECTION: Performance & Optimization
                    // -------------------------------------------------------------
                    SettingsSectionHeader(title = "Performance & Optimization")

                    // Detail Trailers Toggle
                    SettingsSwitchRow(
                        title = "Anime Detail Trailers",
                        subtitle = "Enable video trailers on anime detail pages. Turn OFF to use static backdrops and optimize performance on low-end devices.",
                        checked = trailerEnabled,
                        onCheckedChange = { checked ->
                            trailerEnabled = checked
                            AnimeBoxSettings.setTrailerEnabled(context, checked)
                            onSettingsChanged()
                        }
                    )

                    // Low Performance Mode Toggle
                    SettingsSwitchRow(
                        title = "Low Performance Mode",
                        subtitle = "Disables heavy blur effects and complex background animations for smoother operation on low devices.",
                        checked = lowPerfMode,
                        onCheckedChange = { checked ->
                            lowPerfMode = checked
                            AnimeBoxSettings.setLowPerformanceMode(context, checked)
                            onSettingsChanged()
                        }
                    )

                    // -------------------------------------------------------------
                    // SECTION: Appearance & UI Themes
                    // -------------------------------------------------------------
                    SettingsSectionHeader(title = "Appearance & UI Themes")

                    // Detail Page Default Episode View Mode
                    SettingsSelectorRow(
                        title = "Anime Detail Episode View",
                        subtitle = "Default display layout for episode lists on the Anime detail page",
                        currentValue = if (defaultEpViewMode == "number") "Numbering View" else "Image View (Default)",
                        options = listOf(
                            "Image View (Default)" to "image",
                            "Numbering View" to "number"
                        ),
                        onOptionSelected = { selectedKey ->
                            defaultEpViewMode = selectedKey
                            AnimeBoxSettings.setDefaultEpisodeViewMode(context, selectedKey)
                            onSettingsChanged()
                        }
                    )

                    // -------------------------------------------------------------
                    // SECTION: Video Player Customization
                    // -------------------------------------------------------------
                    SettingsSectionHeader(title = "Video Player Controls & Aesthetics")

                    // Player Timeline Accent Color
                    var showCustomColorPicker by remember { mutableStateOf(false) }
                    val customHex = AnimeBoxSettings.getCustomTimelineColor(context)

                    SettingsSelectorRow(
                        title = "Video Player Timeline Color",
                        subtitle = "Theme accent color for player progress seekbar and scrub head",
                        currentValue = when (timelineTheme) {
                            "red" -> "Netflix Red"
                            "cyan" -> "Neon Cyan"
                            "gold" -> "Sunset Gold"
                            "green" -> "Emerald Green"
                            "white" -> "Pure White"
                            "custom" -> "Custom Color ($customHex)"
                            else -> "Lavender Purple (Default)"
                        },
                        options = listOf(
                            "Lavender Purple (Default)" to "lavender",
                            "Netflix Red" to "red",
                            "Neon Cyan" to "cyan",
                            "Sunset Gold" to "gold",
                            "Emerald Green" to "green",
                            "Pure White" to "white",
                            "Custom Color..." to "custom"
                        ),
                        onOptionSelected = { selectedKey ->
                            if (selectedKey == "custom") {
                                showCustomColorPicker = true
                            } else {
                                timelineTheme = selectedKey
                                AnimeBoxSettings.setPlayerTimelineTheme(context, selectedKey)
                                onSettingsChanged()
                            }
                        }
                    )

                    if (showCustomColorPicker) {
                        CustomColorPickerDialog(
                            initialColorHex = customHex,
                            onDismiss = { showCustomColorPicker = false },
                            onColorSelected = { selectedHex ->
                                AnimeBoxSettings.setCustomTimelineColor(context, selectedHex)
                                AnimeBoxSettings.setPlayerTimelineTheme(context, "custom")
                                timelineTheme = "custom"
                                showCustomColorPicker = false
                                onSettingsChanged()
                            }
                        )
                    }

                    // Skip Intro Enabled Toggle
                    SettingsSwitchRow(
                        title = "Skip Intro & Outro Option",
                        subtitle = "Display quick skip buttons when intro/outro timestamps are detected",
                        checked = skipIntroEnabled,
                        onCheckedChange = { checked ->
                            skipIntroEnabled = checked
                            AnimeBoxSettings.setSkipIntroEnabled(context, checked)
                            onSettingsChanged()
                        }
                    )

                    // Brightness Slider Setting
                    SettingsSelectorRow(
                        title = "Brightness Bar Option",
                        subtitle = "Vertical gesture control on left side of video player",
                        currentValue = if (brightnessMode == "gesture") "Gesture Slider (Left)" else "Hidden (Default)",
                        options = listOf(
                            "Hidden (Default)" to "hidden",
                            "Gesture Slider (Left)" to "gesture"
                        ),
                        onOptionSelected = { selectedKey ->
                            brightnessMode = selectedKey
                            AnimeBoxSettings.setBrightnessMode(context, selectedKey)
                            onSettingsChanged()
                        }
                    )

                    // Volume Slider Setting
                    SettingsSelectorRow(
                        title = "Volume Bar Option",
                        subtitle = "Vertical gesture control on right side of video player",
                        currentValue = if (volumeMode == "gesture") "Gesture Slider (Right)" else "Hidden (Default)",
                        options = listOf(
                            "Hidden (Default)" to "hidden",
                            "Gesture Slider (Right)" to "gesture"
                        ),
                        onOptionSelected = { selectedKey ->
                            volumeMode = selectedKey
                            AnimeBoxSettings.setVolumeMode(context, selectedKey)
                            onSettingsChanged()
                        }
                    )

                    // -------------------------------------------------------------
                    // SECTION: Data Sync & Backup
                    // -------------------------------------------------------------
                    SettingsSectionHeader(title = "Data Management (Import & Export)")

                    // Export Option
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161616), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
                            .clickable {
                                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                exportLauncher.launch("animebox_backup_$timeStamp.json")
                            }
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFD0BCFF).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Export",
                                    tint = Color(0xFFD0BCFF)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Export App Data",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Backup profiles, watchlist, watch history, timestamps, and settings to a JSON file.",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Import Option
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161616), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
                            .clickable {
                                importLauncher.launch("application/json")
                            }
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Import",
                                    tint = Color(0xFF4CAF50)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Import App Data",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Restore data from a JSON file. Format is validated automatically.",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Alert Dialog for Import/Export outcomes
            if (showAlert) {
                AlertDialog(
                    onDismissRequest = { showAlert = false },
                    title = {
                        Text(
                            text = alertTitle,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = alertMessage,
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showAlert = false }) {
                            Text("OK", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = Color(0xFF1F1F1F),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161616), RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFD0BCFF),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF2A2A2A)
            )
        )
    }
}

@Composable
private fun SettingsSelectorRow(
    title: String,
    subtitle: String? = null,
    currentValue: String,
    options: List<Pair<String, String>>, // Label to Key
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161616), RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentValue,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = Color.Gray
                )
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF262626))
            Spacer(modifier = Modifier.height(8.dp))

            options.forEach { (label, key) ->
                val isSelected = currentValue.startsWith(label.substringBefore(" (")) || currentValue == label
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable {
                            onOptionSelected(key)
                            expanded = false
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color(0xFFAAAAAA),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomColorPickerDialog(
    initialColorHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    var hexText by remember { mutableStateOf(initialColorHex) }
    val presetColors = listOf(
        "#FF1744", "#D500F9", "#651FFF", "#3D5AFE",
        "#00E5FF", "#1DE9B6", "#00E676", "#76FF03",
        "#FFEA00", "#FF9100", "#FF3D00", "#FFFFFF"
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color(0xFF181818), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Select Custom Timeline Color",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Swatches Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    presetColors.take(6).forEach { hex ->
                        val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.White }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(if (hexText.equals(hex, ignoreCase = true)) 2.dp else 0.dp, Color.White, CircleShape)
                                .clickable { hexText = hex }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                // Swatches Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    presetColors.drop(6).forEach { hex ->
                        val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.White }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(if (hexText.equals(hex, ignoreCase = true)) 2.dp else 0.dp, Color.White, CircleShape)
                                .clickable { hexText = hex }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Preview & Hex Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val previewColor = try { Color(android.graphics.Color.parseColor(if (hexText.startsWith("#")) hexText else "#$hexText")) } catch (_: Exception) { Color.White }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { hexText = it },
                        label = { Text("HEX Code", color = Color.Gray, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.width(150.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val validHex = if (!hexText.startsWith("#")) "#$hexText" else hexText
                            onColorSelected(validHex)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
