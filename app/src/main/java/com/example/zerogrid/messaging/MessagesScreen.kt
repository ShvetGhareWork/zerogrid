package com.example.zerogrid.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerogrid.mesh.engine.MeshEngine
import com.example.zerogrid.mesh.engine.MeshNode
import com.example.zerogrid.navigation.Screen
import com.example.zerogrid.navigation.ZeroGridBottomBar
import com.example.zerogrid.ui.components.ZeroGridTopBar
import com.example.zerogrid.ui.theme.BadgeGreen
import com.example.zerogrid.ui.theme.ZeroGridTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
data class ChatSummaryUiModel(
    val peerId: String,
    val alias: String,
    val isOnline: Boolean,
    val hopDistance: Int,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int
)

@Composable
fun MessagesScreen(
    onNavigate: (Screen) -> Unit = {},
    onOpenPeerChat: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val meshEngine = remember { MeshEngine.getInstance(context) }
    val connectedPeers by meshEngine.connectedPeers.collectAsState()
    val sosAlerts by meshEngine.sosAlerts.collectAsState()
    val conversations by meshEngine.conversations.collectAsState()
    val isMeshActive by meshEngine.isMeshActive.collectAsState()
    val activeChannelMode by meshEngine.activeChannelMode.collectAsState()
    val colors = ZeroGridTheme.colors

    val localNodeId = meshEngine.localNodeId
    val localSuffix = remember(localNodeId) { localNodeId.removePrefix("NODE-") }
    val localDisplayName by meshEngine.displayName.collectAsState()

    val filteredPeers = remember(connectedPeers, localNodeId, localDisplayName) {
        connectedPeers.filter { node ->
            !node.nodeId.equals(localNodeId, ignoreCase = true) &&
            !node.nodeId.removePrefix("NODE-").equals(localSuffix, ignoreCase = true) &&
            !node.alias.equals(localDisplayName, ignoreCase = true) &&
            !node.alias.equals(android.os.Build.MODEL, ignoreCase = true)
        }.distinctBy { it.nodeId }
    }

    // Peers that have messages or are currently connected
    val allChatPeerIds = remember(conversations, filteredPeers) {
        val fromConversations = conversations.keys.filter { peerId ->
            !peerId.equals(localNodeId, ignoreCase = true) &&
            !peerId.removePrefix("NODE-").equals(localSuffix, ignoreCase = true)
        }
        val fromConnected = filteredPeers.map { it.nodeId }
        (fromConversations + fromConnected).distinct()
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Direct Chats, 1: Channels
    var searchQuery by remember { mutableStateOf("") }

    // Fast O(1) peer lookup map to eliminate linear searches during list building
    val peerMap = remember(filteredPeers) { filteredPeers.associateBy { it.nodeId } }

    // Precomputed immutable UI model list: eliminates heavy calculations, unread counts, and lookups during item composition
    val displayChats = remember(allChatPeerIds, searchQuery, conversations, peerMap) {
        allChatPeerIds.mapNotNull { peerId ->
            val peer = peerMap[peerId]
            val alias = peer?.alias ?: meshEngine.getPeerDisplayName(peerId)
            val messages = conversations[peerId] ?: emptyList()
            val lastMsg = messages.lastOrNull()
            val lastMsgText = lastMsg?.text ?: "Ready to connect over mesh"

            if (searchQuery.isNotBlank() &&
                !alias.contains(searchQuery, ignoreCase = true) &&
                !lastMsgText.contains(searchQuery, ignoreCase = true)
            ) {
                return@mapNotNull null
            }

            val unreadCount = messages.count { !it.isMine }
            ChatSummaryUiModel(
                peerId = peerId,
                alias = alias,
                isOnline = peer != null,
                hopDistance = peer?.hopDistance ?: -1,
                lastMessage = lastMsgText,
                timestamp = lastMsg?.timestamp ?: System.currentTimeMillis(),
                unreadCount = unreadCount
            )
        }
    }

    Scaffold(
        containerColor = Color.Transparent, // Overdraw elimination: let root Scaffold own background
        topBar = {
            ZeroGridTopBar(
                peerCount = filteredPeers.size,
                isMeshActive = isMeshActive,
                onProfileClick = { onNavigate(Screen.PROFILE) }
            )
        },
//        bottomBar = { ZeroGridBottomBar(currentScreen = Screen.MESSAGES, onNavigate = onNavigate) }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isTablet = maxWidth >= 600.dp
            val horizontalPadding = if (isTablet) 32.dp else 16.dp

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                    ) {
                        Column {
                            // Screen Header: Title + Subtitle + Mesh Active Pill
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Messages",
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Offline & Mesh Chat",
                                        fontSize = 13.sp,
                                        color = colors.textSecondary
                                    )
                                }

                                Surface(
                                    color = if (isMeshActive) BadgeGreen.copy(alpha = 0.12f) else colors.surfaceNested,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(if (isMeshActive) BadgeGreen else colors.textSecondary, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isMeshActive) "${activeChannelMode.label} Active" else "Offline",
                                            color = if (isMeshActive) BadgeGreen else colors.textSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Search Bar
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = colors.cardBackground,
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(colors.divider))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = "Search",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = {
                                            Text(
                                                text = "Search chats or channels...",
                                                color = colors.textSecondary,
                                                fontSize = 14.sp
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedTextColor = colors.textPrimary,
                                            unfocusedTextColor = colors.textPrimary
                                        ),
                                        singleLine = true
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "Clear",
                                                tint = colors.textSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Segmented Tab Selector: Direct Chats vs Channels
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.surfaceNested, RoundedCornerShape(12.dp))
                                    .padding(4.dp)
                            ) {
                                SegmentedTabItem(
                                    modifier = Modifier.weight(1f),
                                    title = "Direct Chats",
                                    badge = allChatPeerIds.size.toString(),
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 }
                                )
                                SegmentedTabItem(
                                    modifier = Modifier.weight(1f),
                                    title = "Channels",
                                    badge = "2",
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Emergency Broadcast Banner Card
                            EmergencyBroadcastBanner(
                                nearbyCount = filteredPeers.size + 1,
                                sosAlertsCount = sosAlerts.size,
                                onJoinChannel = { onNavigate(Screen.CHANNELS) }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Section Title
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedTab == 0) "RECENT CHATS" else "AVAILABLE CHANNELS",
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Sort,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Activity",
                                        color = colors.textSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }

                if (selectedTab == 0) {
                    // Direct Chats List
                    if (displayChats.isEmpty()) {
                        item(key = "empty_messages") {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 840.dp)
                                    .fillMaxWidth()
                            ) {
                                EmptyMessagesCard(
                                    hasConnectedPeers = filteredPeers.isNotEmpty(),
                                    onStartChat = {
                                        filteredPeers.firstOrNull()?.let { onOpenPeerChat?.invoke(it.nodeId) }
                                            ?: onNavigate(Screen.MESH)
                                    }
                                )
                            }
                        }
                    } else {
                        items(displayChats, key = { it.peerId }, contentType = { "ChatSummary" }) { chat ->
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 840.dp)
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {
                                RecentChatCard(
                                    alias = chat.alias,
                                    isOnline = chat.isOnline,
                                    hopDistance = chat.hopDistance,
                                    lastMessage = chat.lastMessage,
                                    timestamp = chat.timestamp,
                                    unreadCount = chat.unreadCount,
                                    onClick = { onOpenPeerChat?.invoke(chat.peerId) }
                                )
                            }
                        }
                    }
                } else {
                    // Channels Section
                    item {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 840.dp)
                                .fillMaxWidth()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ChannelRowCard(
                                    channelName = "#emergency-broadcast",
                                    description = "All-station emergency announcements and SOS broadcast feed.",
                                    memberCount = "${filteredPeers.size + 1} Nearby",
                                    isAlert = sosAlerts.isNotEmpty(),
                                    onClick = { onNavigate(Screen.SOS_CENTER) }
                                )
                                ChannelRowCard(
                                    channelName = "#mesh-general",
                                    description = "Public community mesh chat. All local nodes can broadcast here.",
                                    memberCount = "${filteredPeers.size} Peers",
                                    isAlert = false,
                                    onClick = { onNavigate(Screen.CHANNELS) }
                                )
                            }
                        }
                    }
                }

                // Bottom CTA Button: Start Chat
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                filteredPeers.firstOrNull()?.let { onOpenPeerChat?.invoke(it.nodeId) }
                                    ?: onNavigate(Screen.MESH)
                            },
                            modifier = Modifier
                                .fillMaxWidth(if (isTablet) 0.5f else 1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = if (colors.isDark) Color.Black else Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start Chat",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabItem(
    modifier: Modifier = Modifier,
    title: String,
    badge: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = ZeroGridTheme.colors

    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.primary else Color.Transparent,
        contentColor = if (selected) (if (colors.isDark) Color.Black else Color.White) else colors.textSecondary
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                color = if (selected) {
                    (if (colors.isDark) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.25f))
                } else {
                    colors.cardBackground
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = badge,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmergencyBroadcastBanner(
    nearbyCount: Int,
    sosAlertsCount: Int,
    onJoinChannel: () -> Unit
) {
    val colors = ZeroGridTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (sosAlertsCount > 0) colors.accentRed.copy(alpha = 0.08f) else colors.primary.copy(alpha = 0.08f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (sosAlertsCount > 0) colors.accentRed.copy(alpha = 0.35f) else colors.primary.copy(alpha = 0.25f)
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            .size(38.dp)
                            .background(
                                if (sosAlertsCount > 0) colors.accentRed.copy(alpha = 0.15f) else colors.primary.copy(alpha = 0.15f),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Campaign,
                            contentDescription = null,
                            tint = if (sosAlertsCount > 0) colors.accentRed else colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "#emergency-broadcast",
                        color = if (sosAlertsCount > 0) colors.accentRed else colors.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = BadgeGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CellTower,
                            contentDescription = null,
                            tint = BadgeGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$nearbyCount Nearby",
                            color = BadgeGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (sosAlertsCount > 0) {
                    "ACTIVE SOS BEACON: $sosAlertsCount emergency alert broadcast in progress over mesh."
                } else {
                    "ZeroGrid off-grid community broadcast feed for emergency alerts and coordination."
                },
                color = colors.textPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (sosAlertsCount > 0) "Emergency Active" else "Channel Standby",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                TextButton(
                    onClick = onJoinChannel,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Join Channel",
                            color = if (sosAlertsCount > 0) colors.accentRed else colors.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = if (sosAlertsCount > 0) colors.accentRed else colors.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentChatCard(
    alias: String,
    isOnline: Boolean,
    hopDistance: Int,
    lastMessage: String,
    timestamp: Long,
    unreadCount: Int,
    onClick: () -> Unit
) {
    val colors = ZeroGridTheme.colors
    val initials = if (alias.length >= 2) alias.take(2).uppercase() else "ZG"

    val timeString = remember(timestamp) {
        val diff = System.currentTimeMillis() - timestamp
        when {
            diff < 60_000 -> "now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(colors.divider))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with online/offline badge
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(colors.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = colors.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(if (isOnline) BadgeGreen else colors.textSecondary, CircleShape)
                        .border(2.dp, colors.cardBackground, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Name + Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = alias,
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (alias.contains("Rescue", ignoreCase = true) || alias.contains("Admin", ignoreCase = true)) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.Verified,
                                contentDescription = "Verified",
                                tint = colors.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = timeString,
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Last Message Preview
                Text(
                    text = lastMessage,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Hop badge & unread indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (hopLabel, hopColor) = when {
                        !isOnline -> "Offline Cache" to colors.textSecondary
                        hopDistance <= 1 -> "Direct" to BadgeGreen
                        hopDistance == 2 -> "1 Hop" to colors.primary
                        else -> "$hopDistance Hops" to colors.textSecondary
                    }

                    Surface(
                        color = hopColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (hopDistance <= 1) Icons.Outlined.NearMe else Icons.Outlined.AltRoute,
                                contentDescription = null,
                                tint = hopColor,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = hopLabel,
                                color = hopColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(colors.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = unreadCount.toString(),
                                color = if (colors.isDark) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRowCard(
    channelName: String,
    description: String,
    memberCount: String,
    isAlert: Boolean,
    onClick: () -> Unit
) {
    val colors = ZeroGridTheme.colors

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(colors.divider))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (isAlert) colors.accentRed.copy(alpha = 0.12f) else colors.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAlert) Icons.Outlined.Campaign else Icons.Outlined.Tag,
                    contentDescription = null,
                    tint = if (isAlert) colors.accentRed else colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = channelName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isAlert) colors.accentRed else colors.textPrimary
                    )
                    Text(
                        text = memberCount,
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyMessagesCard(
    hasConnectedPeers: Boolean,
    onStartChat: () -> Unit
) {
    val colors = ZeroGridTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(colors.divider))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(colors.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "No Active Mesh Chats",
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasConnectedPeers) {
                    "Peers are currently within mesh range. Select a peer to start encrypted off-grid communication."
                } else {
                    "No peers detected within radio range yet. Radio drivers are scanning for nearby nodes."
                },
                color = colors.textSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onStartChat,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = if (colors.isDark) Color.Black else Color.White
                )
            ) {
                Text(
                    text = if (hasConnectedPeers) "Start Chat with Nearby Peer" else "Scan Nearby Devices",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}