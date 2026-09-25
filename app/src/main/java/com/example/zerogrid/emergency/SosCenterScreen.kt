package com.example.zerogrid.emergency

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
fun SosCenterScreen(
    onNavigate: (Screen) -> Unit = {},
    onTrackSos: ((Double, Double, String, String, Long) -> Unit)? = null
) {
    val meshEngine = MeshEngine.getInstance(LocalContext.current)
    val alerts by meshEngine.sosAlerts.collectAsState()
    val peers by meshEngine.connectedPeers.collectAsState()
    val acknowledgedIds by meshEngine.acknowledgedAlertIds.collectAsState()
    val localNodeId = meshEngine.localNodeId
    val colors = ZeroGridTheme.colors

    // Derive the three alert buckets reactively
    val relativeAlerts = remember(alerts, acknowledgedIds) {
        alerts.filter { it.isCloudSos() && it.packetId !in acknowledgedIds && it.senderId != localNodeId }
    }
    val localMeshAlerts = remember(alerts, acknowledgedIds) {
        alerts.filter { !it.isCloudSos() && it.packetId !in acknowledgedIds && it.senderId != localNodeId }
    }
    val myActiveAlerts = remember(alerts, acknowledgedIds) {
        alerts.filter { it.senderId == localNodeId && it.packetId !in acknowledgedIds }
    }
    val acknowledgedAlerts = remember(alerts, acknowledgedIds) {
        alerts.filter { it.packetId in acknowledgedIds && it.senderId != localNodeId }
    }

    // Safety confirmation dialog state
    var pendingAckAlert by remember { mutableStateOf<MeshPacket?>(null) }

    pendingAckAlert?.let { alert ->
        val isCloud = alert.isCloudSos()
        val senderName = alert.getSosSenderName() ?: "Node-${alert.senderId.takeLast(4)}"
        AlertDialog(
            onDismissRequest = { pendingAckAlert = null },
            containerColor = colors.cardBackground,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = if (isCloud) "🛡️ Confirm Relative Safety" else "📍 Confirm Local Area",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = if (isCloud)
                        "Are you sure $senderName is safe? This will remove the alert from your active list and move it to acknowledged history."
                    else
                        "Are you sure the surrounding area and this local peer are attended to and safe?",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isCloud) {
                            meshEngine.acknowledgeCloudSosAlert(alert.packetId, confirmedSafe = true)
                        } else {
                            meshEngine.acknowledgeSosAlert(alert.packetId)
                        }
                        pendingAckAlert = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCloud) colors.accentRed else Color(0xFF10B981)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isCloud) "Confirm Safe" else "Confirm & Dismiss",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAckAlert = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Sync active cloud SOS events from backend into local state on screen open
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val response = com.example.zerogrid.network.RetrofitInstance.sosApi.getActiveSos()
                if (response.isSuccessful) {
                    response.body()?.events?.forEach { ev ->
                        val coords = ev.location?.coordinates
                        if (coords != null && coords.size >= 2) {
                            meshEngine.recordExternalSosAlert(
                                sosId = ev.id,
                                senderName = ev.triggeredBy?.displayName ?: "Emergency Contact",
                                category = ev.category,
                                message = ev.message ?: "",
                                lat = coords[1],
                                lng = coords[0],
                                accuracy = ev.accuracyMeters
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SosCenterScreen", "Failed to sync active SOS from cloud", e)
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { EmergencyTopBar(onBackClick = { onNavigate(Screen.HOME) }) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "mesh_status_banner") {
                MeshStatusBanner(peers.size)
            }

            // ── SECTION 1: RELATIVE / FAMILY EMERGENCY SOS ──────────────
            item(key = "relative_sos_header") {
                SosSectionHeader(
                    label = "FAMILY & RELATIVE EMERGENCY SOS",
                    count = relativeAlerts.size,
                    dotColor = colors.accentRed,
                    isActive = relativeAlerts.isNotEmpty()
                )
            }
            item(key = "relative_sos_content") {
                if (relativeAlerts.isEmpty()) {
                    SosAllClearCard(
                        message = "No active family alerts",
                        subtitle = "Family & relative emergencies appear here"
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        relativeAlerts.forEach { alert ->
                            SosAlertCard(
                                alert = alert,
                                sectionType = SosType.RELATIVE,
                                onAcknowledgeClick = { pendingAckAlert = alert },
                                onTrackSos = onTrackSos
                            )
                        }
                    }
                }
            }

            // ── SECTION 2: LOCAL AREA MESH SOS ─────────────────────────
            item(key = "local_sos_header") {
                SosSectionHeader(
                    label = "LOCAL AREA MESH SOS",
                    count = localMeshAlerts.size,
                    dotColor = Color(0xFFF59E0B),
                    isActive = localMeshAlerts.isNotEmpty()
                )
            }
            item(key = "local_sos_content") {
                if (localMeshAlerts.isEmpty()) {
                    SosAllClearCard(
                        message = "No active local mesh alerts",
                        subtitle = "Nearby peer SOS beacons appear here"
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        localMeshAlerts.forEach { alert ->
                            SosAlertCard(
                                alert = alert,
                                sectionType = SosType.LOCAL_MESH,
                                onAcknowledgeClick = { pendingAckAlert = alert },
                                onTrackSos = onTrackSos
                            )
                        }
                    }
                }
            }

            // ── MY OWN ACTIVE BROADCASTS ────────────────────────────────
            if (myActiveAlerts.isNotEmpty()) {
                item(key = "my_sos_header") {
                    SosSectionHeader(
                        label = "MY ACTIVE BROADCAST",
                        count = myActiveAlerts.size,
                        dotColor = colors.primary,
                        isActive = true
                    )
                }
                item(key = "my_sos_content") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        myActiveAlerts.forEach { alert ->
                            SosAlertCard(
                                alert = alert,
                                sectionType = SosType.MINE,
                                onAcknowledgeClick = {},
                                onTrackSos = onTrackSos
                            )
                        }
                    }
                }
            }

            // ── EMERGENCY SOS DISPATCH ─────────────────────────────────
            item(key = "emergency_sos_card") {
                EmergencySosCard(onSendSosClick = { onNavigate(Screen.SEND_SOS) })
            }

            // ── EMERGENCY CONTACTS QUICK ACCESS ────────────────────────
            item(key = "emergency_contacts_quick") {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigate(Screen.EMERGENCY_CONTACTS) },
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.divider)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(38.dp).background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Outlined.ContactPhone, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Emergency Contacts", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Manage trusted contacts for SOS dispatch", color = colors.textSecondary, fontSize = 12.sp)
                        }
                        Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ── ACKNOWLEDGED SOS HISTORY ───────────────────────────────
            if (acknowledgedAlerts.isNotEmpty()) {
                item(key = "acked_header") {
                    SosSectionHeader(
                        label = "ACKNOWLEDGED SOS HISTORY",
                        count = acknowledgedAlerts.size,
                        dotColor = colors.primary,
                        isActive = false
                    )
                }
                item(key = "acked_content") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        acknowledgedAlerts.forEach { alert ->
                            AcknowledgedAlertRow(alert = alert, onTrackSos = onTrackSos)
                        }
                    }
                }
            }

            // ── NETWORK REACH ──────────────────────────────────────────
            item(key = "network_reach_section") {
                val maxHops = if (peers.isEmpty()) 0 else peers.maxOf { it.hopDistance }
                val lastAlert = alerts.maxByOrNull { it.timestamp }
                val lastBroadcastTime = if (lastAlert != null) {
                    java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                        .format(java.util.Date(lastAlert.timestamp))
                } else "None"
                val deliveryStatus = if (peers.isNotEmpty()) "100% (Mesh)" else if (alerts.isNotEmpty()) "Relayed" else "Standby"
                Column {
                    Text(text = "NETWORK REACH", color = colors.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    NetworkReachSection(reachableCount = peers.size, maxHops = maxHops, lastBroadcastTime = lastBroadcastTime, deliveryStatus = deliveryStatus)
                }
            }

            // ── QUICK ACTIONS ──────────────────────────────────────────
            item(key = "quick_actions_row") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickActionButton(modifier = Modifier.weight(1f), icon = Icons.Outlined.Campaign, title = "Broadcast\nSOS", onClick = { onNavigate(Screen.SEND_SOS) })
                    QuickActionButton(modifier = Modifier.weight(1f), icon = Icons.Outlined.RssFeed, title = "Emergency\nContacts", onClick = { onNavigate(Screen.EMERGENCY_CONTACTS) })
                }
            }

            item(key = "manage_contacts_button") {
                Button(
                    onClick = { onNavigate(Screen.EMERGENCY_CONTACTS) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.cardBackground),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.divider)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Outlined.ContactEmergency, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Manage Emergency Contacts", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            item(key = "how_sos_works_card") {
                HowSosWorksCard()
            }
        }
    }
}

/** SOS source type — drives dialog text and colour treatment */
private enum class SosType { RELATIVE, LOCAL_MESH, MINE }

@Composable
private fun SosSectionHeader(label: String, count: Int, dotColor: Color, isActive: Boolean) {
    val colors = ZeroGridTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(if (isActive) dotColor else colors.textSecondary, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (count > 0) "$label ($count)" else label,
            color = if (isActive) dotColor else colors.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SosAllClearCard(message: String, subtitle: String) {
    val colors = ZeroGridTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.divider)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = message, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = subtitle, color = colors.textSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun SosAlertCard(
    alert: MeshPacket,
    sectionType: SosType,
    onAcknowledgeClick: () -> Unit,
    onTrackSos: ((Double, Double, String, String, Long) -> Unit)? = null
) {
    val colors = ZeroGridTheme.colors
    val accentColor = when (sectionType) {
        SosType.RELATIVE   -> colors.accentRed
        SosType.LOCAL_MESH -> Color(0xFFF59E0B)
        SosType.MINE       -> colors.primary
    }
    val tagLabel = when (sectionType) {
        SosType.RELATIVE   -> "RELATIVE"
        SosType.LOCAL_MESH -> "LOCAL MESH"
        SosType.MINE       -> "SENT BY ME"
    }

    val senderDisplayName = alert.getSosSenderName()?.takeIf { it.isNotBlank() }
        ?: "Node-${alert.senderId.takeLast(4)}"
    val category = alert.getSosCategory()
    val message = alert.getSosMessage()
    val sosCoords = alert.getSosCoordinates()
    val accuracy = alert.getSosAccuracy()

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(accentColor, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (sectionType) { SosType.LOCAL_MESH -> "Local Peer Alert"; SosType.MINE -> "My Broadcast"; else -> "Relative Emergency" },
                        color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = tagLabel, color = accentColor, fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = colors.divider.copy(alpha = 0.5f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Outlined.DeviceHub, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = "FROM: $senderDisplayName", color = colors.textPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Outlined.AccessTime, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(5.dp))
                val timeLabel = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(alert.timestamp)
                Text(
                    text = if (sectionType == SosType.LOCAL_MESH) "${alert.hopCount} hops  •  $timeLabel" else timeLabel,
                    color = colors.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colors.surfaceNested), shape = RoundedCornerShape(8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "$category EMERGENCY", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        if (sosCoords != null) Text(text = "GPS LOCK ✓", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    if (message.isNotBlank() && message != "Emergency SOS triggered") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "\"$message\"", color = colors.textPrimary, fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                    if (sosCoords != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Location: ${String.format(java.util.Locale.US, "%.5f", sosCoords.first)}, ${String.format(java.util.Locale.US, "%.5f", sosCoords.second)}${if (accuracy != null) " (±${accuracy.toInt()}m)" else ""}",
                            color = colors.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            if (sosCoords != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onTrackSos?.invoke(sosCoords.first, sosCoords.second, senderDisplayName, category, alert.timestamp) },
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "TRACK LOCATION", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
            if (sectionType != SosType.MINE) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onAcknowledgeClick,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (sectionType == SosType.RELATIVE) "CONFIRM PERSON SAFE" else "CONFIRM AREA SAFE",
                        color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun AcknowledgedAlertRow(
    alert: MeshPacket,
    onTrackSos: ((Double, Double, String, String, Long) -> Unit)? = null
) {
    val colors = ZeroGridTheme.colors
    val senderName = alert.getSosSenderName() ?: "Node-${alert.senderId.takeLast(4)}"
    val category = alert.getSosCategory()
    val coords = alert.getSosCoordinates()
    val formattedTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(alert.timestamp))
    val typeLabel = if (alert.isCloudSos()) "Relative" else "Mesh Peer"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceNested),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(32.dp).background(colors.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Outlined.Shield, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "CONFIRMED SAFE ✓  •  $typeLabel", color = colors.primary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "$category Alert — $senderName", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = formattedTime, color = colors.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            if (coords != null) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onTrackSos?.invoke(coords.first, coords.second, senderName, category, alert.timestamp) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF60A5FA)),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.Navigation, contentDescription = null, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("TRACK", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
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
private fun ActiveAlertsSection(
    alerts: List<MeshPacket>,
    localNodeId: String,
    acknowledgedIds: Set<String>,
    onAcknowledge: (String) -> Unit,
    onTrackSos: ((Double, Double, String, String, Long) -> Unit)? = null
) {
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
                        val parsedSenderName = alert.getSosSenderName()
                        val senderDisplayName = when {
                            isMine -> "You"
                            !parsedSenderName.isNullOrBlank() -> parsedSenderName
                            else -> "Node-${alert.senderId.takeLast(4)}"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Outlined.DeviceHub, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isMine) "FROM: $senderDisplayName (${alert.senderId})" else "FROM: $senderDisplayName",
                                color = if (isMine) colors.primary else colors.textPrimary,
                                fontSize = 12.sp,
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

                        Spacer(modifier = Modifier.height(10.dp))

                        // Clean Parsed Payload Card (Never raw JSON)
                        val category = alert.getSosCategory()
                        val message = alert.getSosMessage()
                        val sosCoords = alert.getSosCoordinates()
                        val accuracy = alert.getSosAccuracy()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.surfaceNested),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$category EMERGENCY",
                                        color = colors.accentRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (sosCoords != null) {
                                        Text(
                                            text = "GPS LOCK ✓",
                                            color = Color(0xFF10B981),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                if (message.isNotBlank() && message != "Emergency SOS triggered") {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "\"$message\"",
                                        color = colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                                if (sosCoords != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Location: ${String.format(java.util.Locale.US, "%.5f", sosCoords.first)}, ${String.format(java.util.Locale.US, "%.5f", sosCoords.second)}${if (accuracy != null) " (±${accuracy.toInt()}m)" else ""}",
                                        color = colors.textSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // TRACK LOCATION button — shown whenever valid GPS coords exist
                        if (sosCoords != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    onTrackSos?.invoke(
                                        sosCoords.first,
                                        sosCoords.second,
                                        senderDisplayName,
                                        category,
                                        alert.timestamp
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E3A8A)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Navigation,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isMine) "TEST COMPASS POINTER (THIS DEVICE)" else "TRACK LOCATION",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
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
    acknowledgedIds: Set<String>,
    localNodeId: String = "",
    onTrackSos: ((Double, Double, String, String, Long) -> Unit)? = null
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
                alerts.take(10).forEach { alert ->
                    val isAck = alert.packetId in acknowledgedIds
                    val formattedTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                        .format(java.util.Date(alert.timestamp))
                    val category = alert.getSosCategory()
                    val message = alert.getSosMessage()
                    val isMine = alert.senderId == localNodeId
                    val rawSender = alert.getSosSenderName()
                    val senderName = when {
                        isMine -> "You"
                        !rawSender.isNullOrBlank() -> rawSender
                        else -> "Node-${alert.senderId.takeLast(4)}"
                    }
                    val coords = alert.getSosCoordinates()

                    val title = if (isAck) "SOS Acknowledged: $category ($senderName)" else "$category Alert • $senderName"
                    val subtitle = if (message.isNotBlank() && message != "Emergency SOS triggered") "\"$message\" • $formattedTime" else formattedTime

                    RecentActivityItem(
                        icon = if (isAck) Icons.Outlined.Shield else Icons.Outlined.Emergency,
                        iconTint = if (isAck) colors.primary else colors.accentRed,
                        title = title,
                        subtitle = subtitle,
                        hasCoords = coords != null,
                        onTrackClick = if (coords != null) {
                            { onTrackSos?.invoke(coords.first, coords.second, senderName, category, alert.timestamp) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentActivityItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    hasCoords: Boolean = false,
    onTrackClick: (() -> Unit)? = null
) {
    val colors = ZeroGridTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTrackClick != null) Modifier.clickable { onTrackClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(colors.surfaceNested, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = colors.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        if (hasCoords && onTrackClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onTrackClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF60A5FA)
                ),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text("TRACK", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
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