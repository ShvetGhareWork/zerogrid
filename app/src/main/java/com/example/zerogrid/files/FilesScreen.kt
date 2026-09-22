package com.example.zerogrid.files

import android.content.Intent
import android.os.StatFs
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
import com.example.zerogrid.mesh.engine.MeshEngine
import com.example.zerogrid.navigation.Screen
import com.example.zerogrid.navigation.ZeroGridBottomBar
import com.example.zerogrid.ui.components.ZeroGridTopBar
import com.example.zerogrid.ui.theme.BadgeGreen
import com.example.zerogrid.ui.theme.ZeroGridTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OfflineFileItem(
    val id: String,
    val name: String,
    val sizeString: String,
    val senderOrAuthor: String,
    val timeAgo: String,
    val icon: ImageVector,
    val iconColor: Color,
    val iconBg: Color
)

@Composable
fun FilesScreen(onNavigate: (Screen) -> Unit = {}) {
    val context = LocalContext.current
    val meshEngine = MeshEngine.getInstance(context)
    val peers by meshEngine.connectedPeers.collectAsState()
    val isMeshActive by meshEngine.isMeshActive.collectAsState()
    val activeChannelMode by meshEngine.activeChannelMode.collectAsState()
    val colors = ZeroGridTheme.colors

    // Real device storage query
    val storageSummary = remember {
        try {
            val stat = StatFs(context.filesDir.absolutePath)
            val available = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
            val total = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
            "${available} MB free of ${total} GB"
        } catch (e: Exception) {
            "Storage Available"
        }
    }

    // Dynamic offline files in vault
    val offlineFiles = remember {
        val list = mutableListOf<OfflineFileItem>()
        val dir = context.filesDir
        dir.listFiles()?.take(5)?.forEach { file ->
            if (file.isFile) {
                val sizeKb = file.length() / 1024
                val sizeStr = if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB"
                list.add(
                    OfflineFileItem(
                        id = file.name,
                        name = file.name,
                        sizeString = sizeStr,
                        senderOrAuthor = "Local Vault",
                        timeAgo = "Stored",
                        icon = Icons.Outlined.Description,
                        iconColor = colors.primary,
                        iconBg = colors.primary.copy(alpha = 0.12f)
                    )
                )
            }
        }
        if (list.isEmpty()) {
            list.addAll(
                listOf(
                    OfflineFileItem(
                        id = "field_guide",
                        name = "Offline-Field-Guide.pdf",
                        sizeString = "4.5 MB",
                        senderOrAuthor = "ZeroGrid System",
                        timeAgo = "Built-in",
                        icon = Icons.Outlined.PictureAsPdf,
                        iconColor = Color(0xFFEF4444),
                        iconBg = Color(0xFFEF4444).copy(alpha = 0.12f)
                    ),
                    OfflineFileItem(
                        id = "camp_coords",
                        name = "Camp-Coordinates.gpx",
                        sizeString = "120 KB",
                        senderOrAuthor = "Medical Post",
                        timeAgo = "Cached",
                        icon = Icons.Outlined.LocationOn,
                        iconColor = Color(0xFF10B981),
                        iconBg = Color(0xFF10B981).copy(alpha = 0.12f)
                    ),
                    OfflineFileItem(
                        id = "sector_map",
                        name = "Sector-4-Photo.jpg",
                        sizeString = "3.1 MB",
                        senderOrAuthor = "Rescue Team Alpha",
                        timeAgo = "Cached",
                        icon = Icons.Outlined.Image,
                        iconColor = Color(0xFF3B82F6),
                        iconBg = Color(0xFF3B82F6).copy(alpha = 0.12f)
                    )
                )
            )
        }
        list
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            ZeroGridTopBar(
                peerCount = peers.size,
                isMeshActive = isMeshActive,
                onProfileClick = { onNavigate(Screen.PROFILE) }
            )
        },
//        bottomBar = { ZeroGridBottomBar(currentScreen = Screen.FILES, onNavigate = onNavigate) }
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
                            // Screen Title & Subtitle
                            Text(
                                text = "Files & Transfers",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Peer-to-peer offline sharing",
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Transfer Protocol & Standby/Active Card (Image 3 Hero)
                            TransferChannelHeroCard(
                                peersCount = peers.size,
                                activeChannelLabel = activeChannelMode.label,
                                storageSummary = storageSummary,
                                onOpenTransfer = { onNavigate(Screen.FILE_TRANSFER) }
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Main CTA Button: Send File to Nearby Peer
                            Button(
                                onClick = { onNavigate(Screen.SEND_FILE) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = if (colors.isDark) Color.Black else Color.White
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Send File to Nearby Peer",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Subtitle under CTA
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "((•)) No Internet Required • Wi-Fi Direct & BLE",
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Offline Files Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "OFFLINE FILES",
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "See All (${offlineFiles.size})",
                                    color = colors.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }

                // Offline Files List items
                items(offlineFiles, key = { it.id }) { file ->
                    Box(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    ) {
                        OfflineFileRowCard(
                            file = file,
                            onOpen = {
                                // Trigger open/share intent
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "File: ${file.name} (${file.sizeString}) from ZeroGrid mesh vault.")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share file reference"))
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun TransferChannelHeroCard(
    peersCount: Int,
    activeChannelLabel: String,
    storageSummary: String,
    onOpenTransfer: () -> Unit
) {
    val colors = ZeroGridTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(colors.divider))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: File Icon + Info + Status Pill
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
                            .size(46.dp)
                            .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderZip,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Mesh File Transport",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = storageSummary,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                Surface(
                    color = BadgeGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Standby",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = BadgeGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Linear Progress Bar (Idle / Ready)
            LinearProgressIndicator(
                progress = { 1f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = colors.primary,
                trackColor = colors.surfaceNested
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Encrypted 64 KB Chunks",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "P2P Mesh Driver Active",
                    color = colors.primary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3 Stat Boxes Row (Speed, Peers, Protocol)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FileStatBox(
                    modifier = Modifier.weight(1f),
                    label = "SPEED",
                    value = if (activeChannelLabel == "BLE") "~2 KB/s" else "~10 MB/s"
                )
                FileStatBox(
                    modifier = Modifier.weight(1f),
                    label = "PEERS",
                    value = "$peersCount In Range"
                )
                FileStatBox(
                    modifier = Modifier.weight(1f),
                    label = "PROTOCOL",
                    value = activeChannelLabel
                )
            }
        }
    }
}

@Composable
private fun FileStatBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    val colors = ZeroGridTheme.colors

    Surface(
        modifier = modifier,
        color = colors.surfaceNested,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OfflineFileRowCard(
    file: OfflineFileItem,
    onOpen: () -> Unit
) {
    val colors = ZeroGridTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(colors.divider))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(file.iconBg, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = file.icon,
                        contentDescription = null,
                        tint = file.iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = file.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${file.sizeString} • ${file.senderOrAuthor} • ${file.timeAgo}",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }

            // Open/Share Button
            OutlinedButton(
                onClick = onOpen,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Open",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}