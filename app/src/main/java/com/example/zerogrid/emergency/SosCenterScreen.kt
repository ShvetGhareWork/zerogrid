package com.example.zerogrid.emergency

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.zerogrid.mesh.engine.MeshEngine
import com.example.zerogrid.mesh.engine.MeshPacket
import com.example.zerogrid.navigation.Screen
import com.example.zerogrid.navigation.ZeroGridBottomBar
import com.example.zerogrid.ui.theme.*

@Composable
fun SosCenterScreen(onNavigate: (Screen) -> Unit = {}) {
    val meshEngine = MeshEngine.getInstance(LocalContext.current)
    val alerts by meshEngine.sosAlerts.collectAsState()
    val peers by meshEngine.connectedPeers.collectAsState()
    val acknowledgedIds by meshEngine.acknowledgedAlertIds.collectAsState()
    val localNodeId = meshEngine.localNodeId
    val colors = ZeroGridTheme.colors

    Scaffold(
        containerColor = colors.background,
        topBar = { EmergencyTopBar(onBackClick = { onNavigate(Screen.HOME) }) },
//        bottomBar = { ZeroGridBottomBar(currentScreen = Screen.SOS_CENTER, onNavigate = onNavigate) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            MeshStatusBanner(peers.size)
            Spacer(modifier = Modifier.height(16.dp))

            // Emergency SOS Action Card
            EmergencySosCard(onSendSosClick = { onNavigate(Screen.SEND_SOS) })
            Spacer(modifier = Modifier.height(14.dp))

            // Emergency Contacts Quick Action
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Screen.EMERGENCY_CONTACTS) },
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.divider)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContactPhone,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Emergency Contacts", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Manage trusted contacts for SOS dispatch", color = colors.textSecondary, fontSize = 12.sp)
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Active Emergency Alerts Section
            Text(
                text = "ACTIVE EMERGENCY ALERTS",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            ActiveAlertsSection(
                alerts = alerts,
                localNodeId = localNodeId,
                acknowledgedIds = acknowledgedIds,
                onAcknowledge = { packetId -> meshEngine.acknowledgeSosAlert(packetId) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Network Reach Section
            Text(
                text = "NETWORK REACH",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            val maxHops = if (peers.isEmpty()) 0 else peers.maxOf { it.hopDistance }
            val lastAlert = alerts.maxByOrNull { it.timestamp }
            val lastBroadcastTime = if (lastAlert != null) {
                java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(lastAlert.timestamp))
            } else {
                "None"
            }
            val deliveryStatus = if (peers.isNotEmpty()) "100% (Mesh)" else if (alerts.isNotEmpty()) "Relayed" else "Standby"

            NetworkReachSection(
                reachableCount = peers.size,
                maxHops = maxHops,
                lastBroadcastTime = lastBroadcastTime,
                deliveryStatus = deliveryStatus
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Recent Activity Section
            Text(
                text = "RECENT ACTIVITY",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            RecentActivitySection(
                alerts = alerts,
                acknowledgedIds = acknowledgedIds
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Action Buttons Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Campaign,
                    title = "Broadcast\nSOS",
                    onClick = { onNavigate(Screen.SEND_SOS) }
                )
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.RssFeed,
                    title = "Emergency\nContacts",
                    onClick = { onNavigate(Screen.EMERGENCY_CONTACTS) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Emergency Contacts Full Width Button
            Button(
                onClick = { onNavigate(Screen.EMERGENCY_CONTACTS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.cardBackground),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.divider)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Outlined.ContactEmergency, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Manage Emergency Contacts",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // How SOS Works Footer Card
            HowSosWorksCard()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun EmergencyTopBar(onBackClick: () -> Unit = {}) {
    val colors = ZeroGridTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Emergency Center",
                    color = colors.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = "History",
                tint = colors.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
    }
}

@Composable
private fun MeshStatusBanner(reachableCount: Int) {
    val colors = ZeroGridTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(modifier = Modifier.size(6.dp).background(colors.primary, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "ZeroGrid Mesh Active",
            color = colors.primary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(16.dp))
        Icon(imageVector = Icons.Outlined.Hub, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$reachableCount devices reachable",
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun EmergencySosCard(onSendSosClick: () -> Unit = {}) {
    val colors = ZeroGridTheme.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.accentRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(colors.accentRed.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Outlined.Warning, contentDescription = null, tint = colors.accentRed, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Emergency SOS",
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Broadcast an emergency alert to nearby ZeroGrid devices.",
                color = colors.textSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onSendSosClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Outlined.Campaign, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SEND SOS",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Your SOS will be relayed across the local mesh. Use only for genuine emergencies.",
                color = colors.textSecondary,
                fontSize = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ActiveAlertsSection(alerts: List<MeshPacket>, localNodeId: String, acknowledgedIds: Set<String>, onAcknowledge: (String) -> Unit) {
    val colors = ZeroGridTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (alerts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "No active emergency alerts", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "All clear on the mesh network.", color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        } else {
            alerts.forEach { alert ->
                val isMine = alert.senderId == localNodeId
                val isAcknowledged = alert.packetId in acknowledgedIds

                val borderColor = when {
                    isAcknowledged -> colors.divider
                    isMine -> colors.primary
                    else -> colors.accentRed
                }
                val tagText = when {
                    isAcknowledged -> "ACKNOWLEDGED"
                    isMine -> "SENT BY ME"
                    else -> "INCOMING"
                }
                val tagColor = when {
                    isAcknowledged -> colors.textSecondary
                    isMine -> colors.primary
                    else -> colors.accentRed
                }
                val tagBg = when {
                    isAcknowledged -> colors.surfaceNested
                    isMine -> colors.primary.copy(alpha = 0.15f)
                    else -> colors.accentRed.copy(alpha = 0.15f)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor.copy(alpha = if (isAcknowledged) 0.3f else 0.8f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(tagColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isMine) "My Emergency Beacon" else "Remote Emergency Alert",
                                    color = if (isAcknowledged) colors.textSecondary else colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = tagText,
                                color = tagColor,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(tagBg, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = colors.divider.copy(alpha = 0.5f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Sender row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Outlined.DeviceHub, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isMine) "FROM: ${alert.senderId} (This device)" else "FROM: ${alert.senderId}",
                                color = if (isMine) colors.primary else colors.textPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Hop + time row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Outlined.Hub, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${alert.hopCount} hops  •  TTL: ${alert.ttl}  •  ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(alert.timestamp)}",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Payload
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.surfaceNested),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = alert.payload,
                                color = if (isAcknowledged) colors.textSecondary else colors.textPrimary,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        if (!isMine && !isAcknowledged) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onAcknowledge(alert.packetId) },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ACKNOWLEDGE ALERT",
                                    color = colors.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else if (isMine && !isAcknowledged) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Broadcasting on mesh — other nodes will be alerted",
                                color = colors.primary.copy(alpha = 0.7f),
                                fontSize = 11.sp,
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
private fun NetworkReachSection(
    reachableCount: Int,
    maxHops: Int,
    lastBroadcastTime: String,
    deliveryStatus: String
) {
    val colors = ZeroGridTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Devices reached", color = colors.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Outlined.Devices, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = reachableCount.toString(),
                            color = colors.primary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Relay hops", color = colors.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = maxHops.toString(),
                        color = colors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.divider, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Delivery status", color = colors.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = deliveryStatus,
                        color = colors.primary,
                        fontSize = if (deliveryStatus.length > 8) 18.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Last broadcast", color = colors.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lastBroadcastTime,
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentActivitySection(
    alerts: List<MeshPacket>,
    acknowledgedIds: Set<String>
) {
    val colors = ZeroGridTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (alerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No emergency alerts recorded",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mesh network operating in normal state",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                alerts.take(5).forEach { alert ->
                    val isAck = alert.packetId in acknowledgedIds
                    val formattedTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                        .format(java.util.Date(alert.timestamp))
                    val category = alert.payload.substringAfter("Category: ").substringBefore(" |").ifEmpty { "Emergency" }
                    RecentActivityItem(
                        icon = if (isAck) Icons.Outlined.Shield else Icons.Outlined.Emergency,
                        iconTint = if (isAck) colors.primary else colors.accentRed,
                        title = if (isAck) "SOS acknowledged: $category" else "SOS Broadcast: $category (Node-${alert.senderId.takeLast(4)})",
                        time = formattedTime
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentActivityItem(icon: ImageVector, iconTint: Color, title: String, time: String) {
    val colors = ZeroGridTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(colors.surfaceNested, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.textPrimary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = time, color = colors.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit = {}
) {
    val colors = ZeroGridTheme.colors
    Card(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.divider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun HowSosWorksCard() {
    val colors = ZeroGridTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.divider)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(imageVector = Icons.Outlined.Info, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "How SOS works",
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your emergency message is encrypted and propagated through reachable ZeroGrid devices. Relayed hops ensure maximum reachability.",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun ZeroGridEmergencyCenterScreen() = SosCenterScreen()

@Preview(showBackground = true)
@Composable
fun ZeroGridEmergencyCenterPreview() {
    ZeroGridTheme {
        ZeroGridEmergencyCenterScreen()
    }
}