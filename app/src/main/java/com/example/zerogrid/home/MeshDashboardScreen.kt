package com.example.zerogrid.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerogrid.mesh.engine.MeshChannelMode
import com.example.zerogrid.mesh.engine.MeshEngine
import com.example.zerogrid.mesh.engine.MeshNode
import com.example.zerogrid.navigation.Screen
import com.example.zerogrid.navigation.ZeroGridBottomBar
import com.example.zerogrid.ui.components.ZeroGridTopBar
import com.example.zerogrid.ui.theme.BadgeGreen
import com.example.zerogrid.ui.theme.ZeroGridTheme

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MeshDashboardScreen(
    onNavigate: (Screen) -> Unit = {},
    onOpenPeerChat: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val meshEngine = MeshEngine.getInstance(context)
    val peers by meshEngine.connectedPeers.collectAsState()
    val isMeshActive by meshEngine.isMeshActive.collectAsState()
    val activeChannelMode by meshEngine.activeChannelMode.collectAsState()
    val sosAlerts by meshEngine.sosAlerts.collectAsState()
    val colors = ZeroGridTheme.colors

    // Read real battery percentage dynamically
    val batteryPercent = remember { getBatteryPercentage(context) }

    Scaffold(
        containerColor = Color.Transparent, // Overdraw elimination: let root Scaffold own background
        topBar = {
            ZeroGridTopBar(
                peerCount = peers.size,
                isMeshActive = isMeshActive,
                onProfileClick = { onNavigate(Screen.PROFILE) }
            )
        },
//        bottomBar = { ZeroGridBottomBar(currentScreen = Screen.HOME, onNavigate = onNavigate) }
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
                // 1. Hero Mesh Status Card
                item(key = "hero_mesh_status", contentType = "HeroCard") {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                    ) {
                        MeshActiveHeroCard(
                            peersCount = peers.size,
                            relayedPeersCount = peers.count { it.hopDistance > 1 },
                            activeChannelMode = activeChannelMode,
                            batteryPercent = batteryPercent,
                            onScanClick = { onNavigate(Screen.MESH) }
                        )
                    }
                }

                item(key = "spacer_hero") {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // 2. Communication Channel Switcher Card
                item(key = "channel_switcher", contentType = "ChannelSwitcher") {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                    ) {
                        ChannelSwitcherCard(
                            activeChannelMode = activeChannelMode,
                            onSelectChannel = { meshEngine.setMeshChannelMode(it) }
                        )
                    }
                }

                item(key = "spacer_switcher") {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 3. Quick Actions Header
                item(key = "quick_actions_header") {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "QUICK ACTIONS",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }

                item(key = "spacer_qa") {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 4. Quick Actions Responsive Grid
                item(key = "quick_actions_grid", contentType = "QuickActions") {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                    ) {
                        QuickActionsGrid(
                            isTablet = isTablet,
                            sosAlertsCount = sosAlerts.size,
                            onNavigate = onNavigate
                        )
                    }
                }

                item(key = "spacer_qa_grid") {
                    Spacer(modifier = Modifier.height(28.dp))
                }

                // 5. Nearby People & Devices Section Header
                item(key = "nearby_header") {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NEARBY PEOPLE & DEVICES",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            TextButton(
                                onClick = { onNavigate(Screen.MESH) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "View All (${peers.size})",
                                    color = colors.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                item(key = "spacer_nearby") {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Dynamic Nearby Peers List
                if (peers.isEmpty()) {
                    item(key = "zero_peers") {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 840.dp)
                                .fillMaxWidth()
                        ) {
                            ZeroPeersCard(onScanClick = { onNavigate(Screen.MESH) })
                        }
                    }
                } else {
                    items(peers.take(6), key = { it.nodeId }, contentType = { "NearbyPeer" }) { peer ->
                        Box(
                            modifier = Modifier
                                .widthIn(max = 840.dp)
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            NearbyPeerCard(
                                peer = peer,
                                onOpenChat = { onOpenPeerChat(peer.nodeId) },
                                onCardClick = { onNavigate(Screen.MESH) }
                            )
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun MeshActiveHeroCard(
    peersCount: Int,
    relayedPeersCount: Int,
    activeChannelMode: MeshChannelMode,
    batteryPercent: Int,
    onScanClick: () -> Unit
) {
    val colors = ZeroGridTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(colors.divider))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Top Row: Icon + Title + Battery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Hub,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Mesh Active & Connected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Off-grid direct communication",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                // Battery Pill
                Surface(
                    color = BadgeGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "🔋 $batteryPercent%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = BadgeGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3 Stat Pills Row (Peers, Reach, Mode)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroStatPill(
                    modifier = Modifier.weight(1f),
                    label = "Peers",
                    value = "$peersCount Active",
                    valueColor = colors.textPrimary
                )
                HeroStatPill(
                    modifier = Modifier.weight(1f),
                    label = "Reach",
                    value = if (relayedPeersCount > 0) "Direct & Relay" else if (peersCount > 0) "Direct Only" else "Scanning",
                    valueColor = colors.textPrimary
                )
                HeroStatPill(
                    modifier = Modifier.weight(1f),
                    label = "Mode",
                    value = activeChannelMode.label,
                    valueColor = colors.primary
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Scan for Devices Action Button
            Button(
                onClick = onScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = if (colors.isDark) Color.Black else Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scan for Devices",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HeroStatPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color
) {
    val colors = ZeroGridTheme.colors

    Surface(
        modifier = modifier,
        color = colors.surfaceNested,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChannelSwitcherCard(
    activeChannelMode: MeshChannelMode,
    onSelectChannel: (MeshChannelMode) -> Unit
) {
    val colors = ZeroGridTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(colors.divider))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Radio Channel: ${activeChannelMode.label}",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (activeChannelMode == MeshChannelMode.BLE) "Bluetooth Low Energy • Low battery" else "Wi-Fi Direct P2P • High throughput",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }

            // Radio toggle button
            OutlinedButton(
                onClick = {
                    val next = if (activeChannelMode == MeshChannelMode.BLE) MeshChannelMode.WIFI_DIRECT else MeshChannelMode.BLE
                    onSelectChannel(next)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (activeChannelMode == MeshChannelMode.BLE) "Switch to Wi-Fi" else "Switch to BLE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun QuickActionsGrid(
    isTablet: Boolean,
    sosAlertsCount: Int,
    onNavigate: (Screen) -> Unit
) {
    val colors = ZeroGridTheme.colors

    if (isTablet) {
        // 4 Columns on tablet
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionTile(
                modifier = Modifier.weight(1f),
                title = "Messages",
                subtitle = "Offline chat",
                icon = Icons.Outlined.ChatBubbleOutline,
                iconTint = colors.primary,
                iconBg = colors.primary.copy(alpha = 0.1f),
                onClick = { onNavigate(Screen.MESSAGES) }
            )
            QuickActionTile(
                modifier = Modifier.weight(1f),
                title = "Nearby",
                subtitle = "Peer discovery",
                icon = Icons.Outlined.Devices,
                iconTint = Color(0xFF3B82F6),
                iconBg = Color(0xFF3B82F6).copy(alpha = 0.1f),
                onClick = { onNavigate(Screen.MESH) }
            )
            QuickActionTile(
                modifier = Modifier.weight(1f),
                title = "Channels",
                subtitle = "Public groups",
                icon = Icons.Outlined.Tag,
                iconTint = Color(0xFF8B5CF6),
                iconBg = Color(0xFF8B5CF6).copy(alpha = 0.1f),
                onClick = { onNavigate(Screen.CHANNELS) }
            )
            QuickActionTile(
                modifier = Modifier.weight(1f),
                title = "SOS Beacon",
                subtitle = if (sosAlertsCount > 0) "$sosAlertsCount alert active" else "Emergency ping",
                icon = Icons.Outlined.Campaign,
                iconTint = colors.accentRed,
                iconBg = colors.accentRed.copy(alpha = 0.1f),
                titleColor = colors.accentRed,
                isAlert = true,
                onClick = { onNavigate(Screen.SOS_CENTER) }
            )
        }
    } else {
        // 2x2 Grid on mobile
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionTile(
                    modifier = Modifier.weight(1f),
                    title = "Messages",
                    subtitle = "Offline chat",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    iconTint = colors.primary,
                    iconBg = colors.primary.copy(alpha = 0.1f),
                    onClick = { onNavigate(Screen.MESSAGES) }
                )
                QuickActionTile(
                    modifier = Modifier.weight(1f),
                    title = "Nearby",
                    subtitle = "Peer discovery",
                    icon = Icons.Outlined.Devices,
                    iconTint = Color(0xFF3B82F6),
                    iconBg = Color(0xFF3B82F6).copy(alpha = 0.1f),
                    onClick = { onNavigate(Screen.MESH) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionTile(
                    modifier = Modifier.weight(1f),
                    title = "Channels",
                    subtitle = "Public groups",
                    icon = Icons.Outlined.Tag,
                    iconTint = Color(0xFF8B5CF6),
                    iconBg = Color(0xFF8B5CF6).copy(alpha = 0.1f),
                    onClick = { onNavigate(Screen.CHANNELS) }
                )
                QuickActionTile(
                    modifier = Modifier.weight(1f),
                    title = "SOS Beacon",
                    subtitle = if (sosAlertsCount > 0) "$sosAlertsCount alert active" else "Emergency ping",
                    icon = Icons.Outlined.Campaign,
                    iconTint = colors.accentRed,
                    iconBg = colors.accentRed.copy(alpha = 0.1f),
                    titleColor = colors.accentRed,
                    isAlert = true,
                    onClick = { onNavigate(Screen.SOS_CENTER) }
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    titleColor: Color? = null,
    isAlert: Boolean = false,
    onClick: () -> Unit
) {
    val colors = ZeroGridTheme.colors

    Card(
        onClick = onClick,
        modifier = modifier.height(88.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = if (isAlert) {
            CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(colors.accentRed.copy(alpha = 0.35f)))
        } else {
            CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(colors.divider))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(iconBg, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = titleColor ?: colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun NearbyPeerCard(
    peer: MeshNode,
    onOpenChat: () -> Unit,
    onCardClick: () -> Unit
) {
    val colors = ZeroGridTheme.colors
    val initials = if (peer.alias.length >= 2) peer.alias.take(2).uppercase() else "ZG"

    Card(
        onClick = onCardClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(colors.divider)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Initials avatar circle with online badge
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(colors.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            color = colors.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(BadgeGreen, CircleShape)
                            .border(1.5.dp, colors.cardBackground, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = peer.alias,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(BadgeGreen, CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (peer.hopDistance <= 1) "Direct • Strong Signal" else "Relayed via ${peer.hopDistance} hops",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            // Chat Action Button
            IconButton(
                onClick = onOpenChat,
                modifier = Modifier
                    .size(38.dp)
                    .background(colors.surfaceNested, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Chat with peer",
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ZeroPeersCard(onScanClick: () -> Unit) {
    val colors = ZeroGridTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(colors.divider))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(colors.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Radar,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Scanning Mesh Radios...",
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Devices within Bluetooth or Wi-Fi Direct range will automatically appear here.",
                color = colors.textSecondary,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onScanClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
            ) {
                Text(text = "Nearby Devices Radar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

/**
 * Reads the device's battery level dynamically via the Android BatteryManager.
 */
private fun getBatteryPercentage(context: Context): Int {
    return try {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            88
        }
    } catch (e: Exception) {
        88
    }
}