package com.example.zerogrid.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerogrid.mesh.engine.MeshEngine
import com.example.zerogrid.navigation.Screen
import com.example.zerogrid.navigation.ZeroGridBottomBar
import com.example.zerogrid.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

import com.example.zerogrid.hardware.HardwareStateManager
import com.example.zerogrid.hardware.WifiRequiredDialog
import com.example.zerogrid.mesh.engine.MeshNode

/**
 * Per-peer direct message chat screen.
 * Shows persistent conversation history (survives reconnections) and live incoming messages.
 */
@Composable
fun PeerDirectChatScreen(
    peerId: String,
    onNavigate: (Screen) -> Unit = {}
) {
    val context = LocalContext.current
    val meshEngine = remember { MeshEngine.getInstance(context) }
    val messageStore = remember(context) { MessageStore.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = ZeroGridTheme.colors

    var messageText by remember { mutableStateOf("") }
    val activeChannelMode by meshEngine.activeChannelMode.collectAsState()

    val conversations by meshEngine.conversations.collectAsState()
    val connectedPeers by meshEngine.connectedPeers.collectAsState()

    // Live conversation — updates from the StateFlow as new messages arrive/are sent
    val messages = conversations[peerId] ?: emptyList()

    // Resolve display name from connected peers or persistent MessageStore alias cache
    val peer = connectedPeers.firstOrNull { it.nodeId == peerId }
    val displayName = peer?.alias?.takeIf { !it.startsWith("Peer ") && it.isNotBlank() }
        ?: messageStore.getPeerDisplayName(peerId)
    val isOnline = peer != null
    val hopInfo = peer?.let {
        "${it.transportType} • ${it.rssi}dBm"
    } ?: "Offline"

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PeerChatTopBar(
                displayName = displayName,
                isOnline = isOnline,
                hopInfo = hopInfo,
                activeMode = activeChannelMode,
                onToggleMode = {
                    val newMode = if (activeChannelMode == com.example.zerogrid.mesh.engine.MeshChannelMode.BLE) {
                        com.example.zerogrid.mesh.engine.MeshChannelMode.WIFI_DIRECT
                    } else {
                        com.example.zerogrid.mesh.engine.MeshChannelMode.BLE
                    }
                    meshEngine.setMeshChannelMode(newMode)
                },
                onClearChat = {
                    meshEngine.deleteConversation(peerId)
                    onNavigate(Screen.MESSAGES)
                },
                onBackClick = { onNavigate(Screen.MESSAGES) }
            )
        },
        bottomBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.background)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (activeChannelMode == com.example.zerogrid.mesh.engine.MeshChannelMode.BLE) Icons.Outlined.Bluetooth else Icons.Outlined.Wifi,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Active Channel: ${activeChannelMode.displayName}",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                PeerChatInputBar(
                    messageText = messageText,
                    onValueChange = { messageText = it },
                    onSend = {
                        val text = messageText.trim()
                        if (text.isNotEmpty()) {
                            meshEngine.sendDirectMessage(peerId, text)
                            messageText = ""
                        }
                    },
                    isOnline = isOnline,
                    enabled = true
                )
                ZeroGridBottomBar(currentScreen = Screen.MESSAGES, onNavigate = onNavigate)
            }
        }
    ) { paddingValues ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(colors.surfaceNested, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName.first().uppercaseChar().toString(),
                            color = colors.primary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = displayName,
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isOnline) "Connected via mesh • $hopInfo" else "Not currently in range",
                        color = if (isOnline) colors.primary else colors.textSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Text(
                            text = "No messages yet.\nSay hello to ${displayName}!",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.isMine) {
                        SentMessageBubble(
                            msg = msg,
                            onRetryClick = {
                                val retried = meshEngine.retryMessage(peerId, msg.id)
                                if (!retried) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Peer is still offline. ZeroGrid will retry automatically every 15s.",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            }
                        )
                    } else {
                        ReceivedMessageBubble(msg, displayName)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerChatTopBar(
    displayName: String,
    isOnline: Boolean,
    hopInfo: String,
    activeMode: com.example.zerogrid.mesh.engine.MeshChannelMode,
    onToggleMode: () -> Unit,
    onClearChat: () -> Unit,
    onBackClick: () -> Unit
) {
    val colors = ZeroGridTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(colors.surfaceNested, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = colors.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                            .align(Alignment.BottomEnd)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (isOnline) "Online • $hopInfo" else "Last seen on mesh",
                    color = if (isOnline) Color(0xFF4CAF50) else colors.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(
                modifier = Modifier
                    .background(colors.surfaceNested, RoundedCornerShape(12.dp))
                    .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { onToggleMode() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (activeMode == com.example.zerogrid.mesh.engine.MeshChannelMode.BLE) Icons.Outlined.Bluetooth else Icons.Outlined.Wifi,
                    contentDescription = "Switch Channel",
                    tint = colors.primary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = activeMode.id,
                    color = colors.primary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onClearChat,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Clear Chat",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
    }
}

@Composable
private fun SentMessageBubble(
    msg: StoredMessage,
    onRetryClick: () -> Unit
) {
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(msg.timestamp)
    val isPaused = msg.status == MessageStatus.PAUSED
    val colors = ZeroGridTheme.colors

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    if (isPaused) Color(0xFFFFB74D).copy(alpha = 0.15f) else colors.primary.copy(alpha = 0.15f),
                    RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
                )
                .then(
                    if (isPaused) Modifier.border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.5f), RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                    else Modifier
                )
                .clickable(enabled = isPaused) { onRetryClick() }
                .padding(12.dp, 10.dp)
        ) {
            Column {
                Text(
                    text = msg.text,
                    color = if (isPaused) colors.textPrimary else colors.primary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = timeStr,
                        color = if (isPaused) Color(0xFFFFB74D).copy(alpha = 0.8f) else colors.primary.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    when (msg.status) {
                        MessageStatus.PAUSED -> {
                            Icon(
                                imageVector = Icons.Outlined.PauseCircleOutline,
                                contentDescription = "Paused",
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        MessageStatus.SENDING -> {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = "Sending",
                                tint = colors.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        MessageStatus.SENT -> {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Sent",
                                tint = colors.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        MessageStatus.DELIVERED -> {
                            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "Delivered",
                                    tint = colors.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                if (isPaused) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFB74D).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Retry",
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Paused • Peer offline • Tap to retry",
                            color = Color(0xFFFFB74D),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceivedMessageBubble(msg: StoredMessage, senderName: String) {
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(msg.timestamp)
    val colors = ZeroGridTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(colors.surfaceNested, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = senderName.first().uppercaseChar().toString(),
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = senderName,
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(colors.cardBackground, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                    .padding(12.dp, 10.dp)
            ) {
                Column {
                    Text(
                        text = msg.text,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = timeStr,
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (msg.hopCount > 0) {
                            Text(
                                text = "• ${msg.hopCount}↗",
                                color = colors.textSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerChatInputBar(
    messageText: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isOnline: Boolean,
    enabled: Boolean
) {
    val colors = ZeroGridTheme.colors
    Column {
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
        if (!isOnline) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceNested)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PauseCircleOutline,
                    contentDescription = null,
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Peer offline — messages will pause & retry every 15s",
                    color = Color(0xFFFFB74D),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = if (isOnline) "Send a message..." else "Message (pauses if offline)...",
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.cardBackground,
                    unfocusedContainerColor = colors.cardBackground,
                    disabledContainerColor = colors.surfaceNested,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                singleLine = true
            )
            Button(
                onClick = onSend,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (enabled && messageText.isNotBlank()) colors.primary else colors.surfaceNested,
                    disabledContainerColor = colors.surfaceNested
                ),
                enabled = enabled && messageText.isNotBlank(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (enabled && messageText.isNotBlank()) (if (colors.isDark) Color.Black else Color.White) else colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = Icons.Outlined.Lock, contentDescription = null, tint = colors.primary.copy(alpha = 0.5f), modifier = Modifier.size(11.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "End-to-end encrypted over mesh",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}