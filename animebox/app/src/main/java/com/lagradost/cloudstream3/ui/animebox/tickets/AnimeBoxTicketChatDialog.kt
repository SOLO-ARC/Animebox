@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AnimeBoxTicketChatDialog(
    ticket: SupportTicket,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var messages by remember { mutableStateOf<List<TicketMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var textInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingImage by remember { mutableStateOf(false) }
    var isSendingMessage by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
    }

    // Polling effect for live real-time chat updates
    LaunchedEffect(ticket.id) {
        isLoading = true
        while (true) {
            try {
                val list = SupabaseTicketManager.fetchTicketMessages(ticket.id)
                if (list.size != messages.size || list != messages) {
                    messages = list
                    if (list.isNotEmpty()) {
                        listState.animateScrollToItem(list.size - 1)
                    }
                }
            } catch (_: Exception) {}
            isLoading = false
            delay(4000L) // Poll every 4 seconds for live WhatsApp-like conversation
        }
    }

    val onSendMessage = {
        val currentText = textInput.trim()
        val currentUri = selectedImageUri

        if (currentText.isNotEmpty() || currentUri != null) {
            coroutineScope.launch {
                isSendingMessage = true
                var uploadedUrl: String? = null

                if (currentUri != null) {
                    isUploadingImage = true
                    uploadedUrl = SupabaseTicketManager.uploadImageToImgBB(context, currentUri)
                    isUploadingImage = false
                }

                val success = SupabaseTicketManager.sendMessage(
                    ticketId = ticket.id,
                    senderType = "user",
                    senderName = ticket.anilistUsername,
                    senderAvatarUrl = ticket.anilistAvatarUrl,
                    senderAnilistId = ticket.anilistId,
                    message = currentText,
                    imageUrl = uploadedUrl
                )

                if (success) {
                    textInput = ""
                    selectedImageUri = null
                    val fresh = SupabaseTicketManager.fetchTicketMessages(ticket.id)
                    messages = fresh
                    if (fresh.isNotEmpty()) {
                        listState.animateScrollToItem(fresh.size - 1)
                    }
                } else {
                    Toast.makeText(context, "Failed to send message. Please retry.", Toast.LENGTH_SHORT).show()
                }
                isSendingMessage = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        DisposableEffect(view) {
            var parent = view.parent
            while (parent != null) {
                if (parent is android.app.Dialog) {
                    parent.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                    parent.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    parent.window?.setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    break
                }
                parent = parent.parent
            }
            onDispose {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF09090D))
        ) {
            val isImeOpen = WindowInsets.isImeVisible
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .then(
                        if (isImeOpen) Modifier.imePadding()
                        else Modifier.navigationBarsPadding()
                    )
            ) {
                // ── Top Header Bar (Monochrome Flat - Zero Outlines) ────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF101015))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
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

                    Spacer(modifier = Modifier.width(4.dp))

                    // Firefly Support Avatar with status dot
                    Box(modifier = Modifier.size(38.dp)) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = SupabaseTicketManager.ADMIN_AVATAR_URL
                            ),
                            contentDescription = "Admin Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                                .border(1.5.dp, Color(0xFF101015), CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FireFly Support",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${ticket.title} • #TK-${ticket.ticketNumber}",
                            color = Color(0xFF888894),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = ticket.status.replace("_", " ").uppercase(),
                            color = statusText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ── WhatsApp-Style Rich Luxury Canvas Wallpaper ─────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF09090D))
                ) {
                    // WhatsApp-inspired custom doodle pattern
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawWhatsAppDoodleBackground()
                    }

                    if (isLoading && messages.isEmpty()) {
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
                    } else if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF2E2E3C),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Ticket created successfully",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Our team has received your inquiry. Type your messages or attach screenshots below.",
                                color = Color(0xFF777786),
                                fontSize = 11.5.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Date Separator Pill (Flat Minimalist)
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFF14141C))
                                            .padding(horizontal = 10.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "LIVE TICKET CONVERSATION",
                                            color = Color(0xFF7E7E8E),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            items(messages, key = { it.id }) { msg ->
                                val isUser = msg.senderType == "user"
                                ChatMessageBubble(
                                    message = msg,
                                    isUser = isUser,
                                    onImageClick = { url -> fullScreenImageUrl = url }
                                )
                            }
                        }
                    }
                }

                // ── Selected Image Preview Attachment Bar ───────────────────
                AnimatedVisibility(
                    visible = selectedImageUri != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121218))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1C1C24))
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(model = selectedImageUri),
                                contentDescription = "Attached Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Screenshot attached",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isUploadingImage) "Uploading to ImgBB..." else "Ready to send",
                                color = if (isUploadingImage) Color(0xFF38BDF8) else Color(0xFF7E7E8E),
                                fontSize = 10.sp
                            )
                        }

                        IconButton(
                            onClick = { selectedImageUri = null },
                            enabled = !isUploadingImage
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Image",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // ── Bottom Input Row (Monochrome Clean Minimalist) ──────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF101015))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment button
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A22))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach Image",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Text Input Box (Flat - Zero Outlines)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF181820))
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        if (textInput.isEmpty()) {
                            Text(
                                text = "Type your message...",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 13.sp
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send Button (Monochrome Pure White Button)
                    val canSend = textInput.trim().isNotEmpty() || selectedImageUri != null
                    IconButton(
                        onClick = onSendMessage,
                        enabled = !isSendingMessage && canSend,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (canSend) Color.White else Color(0xFF1A1A22))
                    ) {
                        if (isSendingMessage || isUploadingImage) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (canSend) Color.Black else Color(0xFF555562),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ── Fullscreen Image Preview Dialog ─────────────────────────────
            if (fullScreenImageUrl != null) {
                Dialog(
                    onDismissRequest = { fullScreenImageUrl = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.96f))
                            .clickable { fullScreenImageUrl = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = fullScreenImageUrl),
                            contentDescription = "Full Image Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                        IconButton(
                            onClick = { fullScreenImageUrl = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(24.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Preview",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── WhatsApp-Inspired Doodle Canvas Renderer ────────────────────────────────
private fun DrawScope.drawWhatsAppDoodleBackground() {
    val motifColor = Color(0xFFFFFFFF).copy(alpha = 0.032f)
    val spacingX = 48.dp.toPx()
    val spacingY = 48.dp.toPx()

    var row = 0
    var y = spacingY / 2
    while (y < size.height) {
        var col = 0
        var x = spacingX / 2
        while (x < size.width) {
            when ((row + col) % 4) {
                0 -> {
                    // Mini chat bubble icon
                    drawRoundRect(
                        color = motifColor,
                        topLeft = Offset(x - 5.dp.toPx(), y - 3.5.dp.toPx()),
                        size = Size(10.dp.toPx(), 7.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                }
                1 -> {
                    // Mini dot
                    drawCircle(
                        color = motifColor,
                        radius = 1.6.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
                2 -> {
                    // Mini star / cross motif
                    drawLine(
                        color = motifColor,
                        start = Offset(x - 3.dp.toPx(), y),
                        end = Offset(x + 3.dp.toPx(), y),
                        strokeWidth = 1.2.dp.toPx()
                    )
                    drawLine(
                        color = motifColor,
                        start = Offset(x, y - 3.dp.toPx()),
                        end = Offset(x, y + 3.dp.toPx()),
                        strokeWidth = 1.2.dp.toPx()
                    )
                }
                3 -> {
                    // Diamond motif
                    drawCircle(
                        color = motifColor,
                        radius = 1.2.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
            col++
            x += spacingX
        }
        row++
        y += spacingY
    }
}

@Composable
private fun ChatMessageBubble(
    message: TicketMessage,
    isUser: Boolean,
    onImageClick: (String) -> Unit
) {
    val bubbleShape = RoundedCornerShape(4.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = message.senderAvatarUrl ?: SupabaseTicketManager.ADMIN_AVATAR_URL
                ),
                contentDescription = message.senderName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Sender name for Admin
            if (!isUser) {
                Text(
                    text = "FireFly Support",
                    color = Color(0xFFAAAAAA),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 3.dp, bottom = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(
                        if (isUser) {
                            Color(0xFF262632) // Flat crisp dark graphite for user
                        } else {
                            Color(0xFF16161E) // Flat deep charcoal for admin
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Column {
                    // Inline image attachment if present
                    if (!message.imageUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .clickable { onImageClick(message.imageUrl) }
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(model = message.imageUrl),
                                contentDescription = "Attached Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (message.message.isNotBlank()) {
                            Spacer(modifier = Modifier.height(5.dp))
                        }
                    }

                    if (message.message.isNotBlank()) {
                        Text(
                            text = message.message,
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 17.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message.formattedTime(),
                            color = Color(0xFF777784),
                            fontSize = 9.sp
                        )
                        if (isUser) {
                            Spacer(modifier = Modifier.width(3.dp))
                            // Double check marks (WhatsApp style)
                            Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(10.5.dp)
                                )
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Delivered",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(10.5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
