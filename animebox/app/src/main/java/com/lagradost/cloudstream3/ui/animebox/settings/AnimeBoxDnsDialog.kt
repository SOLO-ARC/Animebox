package com.lagradost.cloudstream3.ui.animebox.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lagradost.cloudstream3.ui.animebox.api.AniListClient

data class DnsOption(
    val id: String,
    val title: String,
    val description: String
)

@Composable
fun AnimeBoxDnsDialog(
    onDismiss: () -> Unit,
    onDnsChanged: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(AnimeBoxSettings.getDnsMode(context)) }
    var customUrl by remember { mutableStateOf(AnimeBoxSettings.getCustomDnsUrl(context)) }
    var showCustomInput by remember { mutableStateOf(selectedMode == "custom") }

    val dnsOptions = remember {
        listOf(
            DnsOption("default", "System Default DNS", "Standard network provider DNS (Default)"),
            DnsOption("cloudflare", "Cloudflare DNS (1.1.1.1)", "Fast & secure DNS with DoH encryption"),
            DnsOption("google", "Google DNS (8.8.8.8)", "High availability Google public DoH"),
            DnsOption("adguard", "AdGuard DNS", "Filters ads, trackers & malicious domains"),
            DnsOption("quad9", "Quad9 DNS (9.9.9.9)", "Privacy-focused malware blocking DNS"),
            DnsOption("custom", "Custom DoH DNS", "Enter your own custom DNS-over-HTTPS URL")
        )
    }

    // Exact Settings DNS Icon Path (replacing the Lock icon)
    val dnsPath = remember {
        PathParser().parsePathString(
            "M19 13H5c-1.1 0-2 .9-2 2v4c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2v-4c0-1.1-.9-2-2-2zM7 19c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zM19 3H5c-1.1 0-2 .9-2 2v4c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM7 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z"
        ).toPath()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF181820))
                    .border(1.dp, Color(0xFF2E2E38), RoundedCornerShape(16.dp))
                    .clickable(enabled = false) {}
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Canvas(modifier = Modifier.size(24.dp)) {
                            scale(size.width / 24f, size.height / 24f, Offset.Zero) {
                                drawPath(dnsPath, color = Color(0xFFD0BCFF))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "DNS over HTTPS (DoH)",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Change DNS provider to bypass ISP blocks or fix AniList server connectivity issues.",
                        color = Color(0xFF9E9EA8),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    dnsOptions.forEach { opt ->
                        val isSelected = selectedMode == opt.id
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF2A2A34) else Color(0xFF1E1E26))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFFD0BCFF) else Color(0xFF2E2E38),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedMode = opt.id
                                    showCustomInput = (opt.id == "custom")
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = opt.title,
                                        color = if (isSelected) Color(0xFFD0BCFF) else Color.White,
                                        fontSize = 14.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = opt.description,
                                        color = if (isSelected) Color(0xFFD0BCFF).copy(alpha = 0.8f) else Color(0xFF8E8E9A),
                                        fontSize = 12.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFFD0BCFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (showCustomInput) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customUrl,
                            onValueChange = { customUrl = it },
                            label = { Text("Custom DoH URL (e.g. https://...)", color = Color(0xFF8E8E9A), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD0BCFF),
                                unfocusedBorderColor = Color(0xFF2E2E38),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color(0xFF8E8E9A), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                AnimeBoxSettings.setDnsMode(context, selectedMode)
                                if (selectedMode == "custom") {
                                    AnimeBoxSettings.setCustomDnsUrl(context, customUrl.trim())
                                }
                                AniListClient.refreshClient()
                                android.widget.Toast.makeText(
                                    context,
                                    "DNS updated to ${selectedMode.replaceFirstChar { it.uppercase() }}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onDnsChanged?.invoke()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF1E1E26)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Apply DNS", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
