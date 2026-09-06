package com.lagradost.cloudstream3.ui.animebox.tickets

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.lagradost.cloudstream3.ui.animebox.sync.SyncAccountUser
import kotlinx.coroutines.launch

@Composable
fun AnimeBoxTicketListDialog(
    anilistUser: SyncAccountUser,
    profileId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var tickets by remember { mutableStateOf<List<SupportTicket>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("all") }
    var showCreateModal by remember { mutableStateOf(false) }
    var selectedTicketForChat by remember { mutableStateOf<SupportTicket?>(null) }

    val loadTickets = {
        coroutineScope.launch {
            isLoading = true
            val list = SupabaseTicketManager.fetchUserTickets(anilistUser.id)
            tickets = list
            isLoading = false
        }
    }

    LaunchedEffect(anilistUser.id) {
        loadTickets()
    }

    val filteredTickets = remember(tickets, selectedFilter) {
        if (selectedFilter == "all") {
            tickets
        } else {
            tickets.filter { it.status.equals(selectedFilter, ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF08080A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // ── Top Header Bar (Monochrome Clean Minimalist) ────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F0F12))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Column {
                            Text(
                                text = "Support & Feedback",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "@${anilistUser.username} • 1-on-1 Live Support",
                                color = Color(0xFF7E7E88),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    // + New Ticket Button (Solid Clean Monochrome Button)
                    Button(
                        onClick = { showCreateModal = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New Ticket",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ── AniList User Banner (Flat Monochrome - No Outlines) ────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF141418))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = anilistUser.avatarUrl),
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = anilistUser.username,
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AniList ID: ${anilistUser.id} • Official Support Channel",
                            color = Color(0xFF777782),
                            fontSize = 10.5.sp
                        )
                    }
                    IconButton(
                        onClick = { loadTickets() },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFF888894),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // ── Monochrome Filter Tabs (Zero Borders, Reduced Roundness) ─
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filters = listOf(
                        "all" to "All (${tickets.size})",
                        "open" to "Open (${tickets.count { it.status.equals("open", ignoreCase = true) }})",
                        "in_progress" to "In Progress (${tickets.count { it.status.equals("in_progress", ignoreCase = true) }})",
                        "resolved" to "Resolved (${tickets.count { it.status.equals("resolved", ignoreCase = true) }})",
                        "closed" to "Closed (${tickets.count { it.status.equals("closed", ignoreCase = true) }})"
                    )
                    filters.forEach { (key, label) ->
                        val isSelected = selectedFilter == key
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) Color.White else Color(0xFF16161B))
                                .clickable { selectedFilter = key }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else Color(0xFF888894),
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Tickets List Stream (Flat Dark Cards - Zero Outlines) ───
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else if (filteredTickets.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = Color(0xFF33333E),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (selectedFilter == "all") "No Support Tickets Found" else "No $selectedFilter tickets",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = "Have an issue, bug, or suggestion? Tap '+ New Ticket' above to start a live support conversation with our team.",
                                color = Color(0xFF6E6E78),
                                fontSize = 11.5.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredTickets, key = { it.id }) { t ->
                                TicketItemCard(
                                    ticket = t,
                                    onClick = { selectedTicketForChat = t }
                                )
                            }
                        }
                    }
                }
            }

            // ── Create Ticket Modal (Monochrome Clean Flat) ─────────────────
            if (showCreateModal) {
                CreateTicketDialog(
                    anilistUser = anilistUser,
                    profileId = profileId,
                    onDismiss = { showCreateModal = false },
                    onTicketCreated = { created ->
                        showCreateModal = false
                        loadTickets()
                        selectedTicketForChat = created
                    }
                )
            }

            // ── Ticket Live Chat Dialog ─────────────────────────────────────
            if (selectedTicketForChat != null) {
                AnimeBoxTicketChatDialog(
                    ticket = selectedTicketForChat!!,
                    onDismiss = {
                        selectedTicketForChat = null
                        loadTickets()
                    }
                )
            }
        }
    }
}

@Composable
private fun TicketItemCard(
    ticket: SupportTicket,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF131317))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#TK-${ticket.ticketNumber}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF1E1E24))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = ticket.category,
                            color = Color(0xFFAAAAAA),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Flat Status Pill (No outlines)
                val statusBg = when (ticket.status.lowercase()) {
                    "resolved" -> Color(0xFF142E1B)
                    "in_progress" -> Color(0xFF122438)
                    "closed" -> Color(0xFF222228)
                    else -> Color(0xFF332410)
                }
                val statusText = when (ticket.status.lowercase()) {
                    "resolved" -> Color(0xFF4ADE80)
                    "in_progress" -> Color(0xFF38BDF8)
                    "closed" -> Color(0xFF888894)
                    else -> Color(0xFFFBBF24)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(statusBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = ticket.status.replace("_", " ").uppercase(),
                        color = statusText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = ticket.title,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = ticket.lastMessagePreview?.ifBlank { ticket.description } ?: ticket.description,
                color = Color(0xFF7E7E88),
                fontSize = 11.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = ticket.formattedLastMessageTime(),
                    color = Color(0xFF55555F),
                    fontSize = 10.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Open Chat",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateTicketDialog(
    anilistUser: SyncAccountUser,
    profileId: String,
    onDismiss: () -> Unit,
    onTicketCreated: (SupportTicket) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf("Issue / Bug") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val categories = listOf(
        "Issue / Bug",
        "Feature Suggestion",
        "Content / Anime Request",
        "Account / Sync",
        "Other"
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 320.dp, max = 440.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF15151A))
                    .clickable(enabled = false) {}
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Create Support Ticket",
                            color = Color.White,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.Gray,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category Selector Chips (Monochrome Flat)
                    Text(
                        text = "Category",
                        color = Color(0xFF888894),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSel = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSel) Color.White else Color(0xFF1E1E26))
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSel) Color.Black else Color(0xFFAAAAAA),
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Title input
                    Text(
                        text = "Subject / Title",
                        color = Color(0xFF888894),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1E1E26))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        if (title.isEmpty()) {
                            Text(
                                text = "e.g. Episode 3 audio is desynced",
                                color = Color(0xFF555562),
                                fontSize = 12.5.sp
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = title,
                            onValueChange = { title = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 12.5.sp
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description input
                    Text(
                        text = "Detailed Explanation",
                        color = Color(0xFF888894),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1E1E26))
                            .padding(10.dp)
                    ) {
                        if (description.isEmpty()) {
                            Text(
                                text = "Describe your issue, suggestion, or question in detail...",
                                color = Color(0xFF555562),
                                fontSize = 12.5.sp
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = description,
                            onValueChange = { description = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 12.5.sp
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Optional Image Attachment (Flat Clean)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1E1E26))
                            .clickable { imagePickerLauncher.launch("image/*") }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectedImageUri != null) "Screenshot Attached" else "Attach Screenshot (Optional)",
                                color = if (selectedImageUri != null) Color.White else Color(0xFFAAAAAA),
                                fontSize = 11.5.sp
                            )
                        }
                        if (selectedImageUri != null) {
                            IconButton(
                                onClick = { selectedImageUri = null },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Submit Button (Pure Solid White button)
                    Button(
                        onClick = {
                            if (title.trim().isEmpty() || description.trim().isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Please fill in both Subject and Explanation",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            coroutineScope.launch {
                                isSubmitting = true
                                var imageUrl: String? = null
                                if (selectedImageUri != null) {
                                    imageUrl = SupabaseTicketManager.uploadImageToImgBB(
                                        context,
                                        selectedImageUri!!
                                    )
                                }
                                val created = SupabaseTicketManager.createTicket(
                                    anilistId = anilistUser.id,
                                    anilistUsername = anilistUser.username,
                                    anilistAvatarUrl = anilistUser.avatarUrl,
                                    profileId = profileId,
                                    title = title.trim(),
                                    category = selectedCategory,
                                    description = description.trim(),
                                    initialImageUrl = imageUrl
                                )
                                isSubmitting = false
                                if (created != null) {
                                    Toast.makeText(
                                        context,
                                        "Ticket created! Our team will respond shortly.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onTicketCreated(created)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Failed to create ticket. Please retry.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                text = "Submit Ticket",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
