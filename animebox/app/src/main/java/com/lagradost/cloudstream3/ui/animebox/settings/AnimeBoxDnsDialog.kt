package com.lagradost.cloudstream3.ui.animebox.settings

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
    var selectedMode by remember {
        val current = AnimeBoxSettings.getDnsMode(context)
        mutableStateOf(if (current == "system") "default" else current)
    }
    var customUrl by remember { mutableStateOf(AnimeBoxSettings.getCustomDnsUrl(context)) }
    var showCustomInput by remember { mutableStateOf(selectedMode == "custom") }

    val dnsOptions = remember {
        listOf(
            DnsOption("default", "System Default (Auto)", "Standard network provider DNS"),
            DnsOption("cloudflare", "Cloudflare DoH (1.1.1.1)", "Fast & secure DNS with DoH encryption"),
            DnsOption("google", "Google Public DNS (8.8.8.8)", "High availability Google public DoH"),
            DnsOption("adguard", "AdGuard DNS (Ad-block)", "Filters ads, trackers & malicious domains"),
            DnsOption("quad9", "Quad9 DNS (9.9.9.9)", "Privacy-focused malware blocking DNS"),
            DnsOption("controld", "ControlD (High Speed & Secure)", "Fast next-gen encrypted DoH resolver"),
            DnsOption("dnssb", "DNS.SB (Privacy-first)", "Zero-logging encrypted DoH"),
            DnsOption("custom", "Custom DNS-over-HTTPS (DoH)", "Enter your own custom DNS-over-HTTPS URL")
        )
    }

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
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141416))
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
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Canvas(modifier = Modifier.size(22.dp)) {
                            scale(size.width / 24f, size.height / 24f, Offset.Zero) {
                                drawPath(dnsPath, color = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Network & DNS Routing",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Route app and video stream queries over encrypted DNS-over-HTTPS (DoH).",
                        color = Color(0xFF8E8E98),
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )

                    dnsOptions.forEach { opt ->
                        val isSelected = selectedMode == opt.id
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) Color(0xFF222226) else Color(0xFF19191D))
                                .clickable {
                                    selectedMode = opt.id
                                    showCustomInput = (opt.id == "custom")
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = opt.title,
                                        color = if (isSelected) Color.White else Color(0xFFB0B0B8),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = opt.description,
                                        color = if (isSelected) Color(0xFFCCCCCC) else Color(0xFF7E7E88),
                                        fontSize = 11.5.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (showCustomInput) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E1E24))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                "Custom DoH Resolver URL",
                                color = Color(0xFF8E8E98),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            BasicTextField(
                                value = customUrl,
                                onValueChange = { customUrl = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                cursorBrush = SolidColor(Color.White),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (customUrl.isBlank()) {
                                            Text(
                                                "https://resolver.example/dns-query",
                                                color = Color(0xFF63636D),
                                                fontSize = 13.5.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color(0xFF8E8E98), fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (selectedMode == "custom") {
                                    val trimmed = customUrl.trim()
                                    if (!trimmed.startsWith("https://", ignoreCase = true) || !trimmed.contains(".")) {
                                        Toast.makeText(
                                            context,
                                            "Enter a valid HTTPS DNS-over-HTTPS URL",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@Button
                                    }
                                    AnimeBoxSettings.setCustomDnsUrl(context, trimmed)
                                }
                                AnimeBoxSettings.setDnsMode(context, selectedMode)
                                AnimeBoxDnsHelper.refreshAllNetworkClients(context)
                                Toast.makeText(
                                    context,
                                    "DNS updated to ${selectedMode.replaceFirstChar { it.uppercase() }}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDnsChanged?.invoke()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF141416)
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Apply DNS", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }
                    }
                }
            }
        }
    }
}
