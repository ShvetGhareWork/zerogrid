package com.example.zerogrid.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.zerogrid.emergency.*
import com.example.zerogrid.files.*
import com.example.zerogrid.hardware.HardwareRequirementBanner
import com.example.zerogrid.home.*
import com.example.zerogrid.mesh.*
import com.example.zerogrid.mesh.engine.MeshEngine
import com.example.zerogrid.messaging.*
import com.example.zerogrid.onboarding.*
import com.example.zerogrid.settings.*
import com.example.zerogrid.debug.*
import com.example.zerogrid.ui.theme.*

private val SosRed = Color(0xFFFF3B30)
private val SosAmber = Color(0xFFFF9500)
private val SosCyan = Color(0xFF00E5FF)

@Composable
fun ZeroGridApp(onLogout: () -> Unit = {}) {
    val context = LocalContext.current
    val meshEngine = remember { MeshEngine.getInstance(context) }

    val sosAlerts by meshEngine.sosAlerts.collectAsState()
    val acknowledgedAlertIds by meshEngine.acknowledgedAlertIds.collectAsState()

    // Only show pop-up for REMOTE alerts (not ones this device sent itself)
    val activeSosAlert = sosAlerts.firstOrNull {
        it.packetId !in acknowledgedAlertIds && it.senderId != meshEngine.localNodeId
    }

    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    val backStack = remember { mutableStateListOf<Screen>() }
    // Peer ID selected for direct chat — passed into PEER_DIRECT_CHAT screen
    var selectedPeerId by remember { mutableStateOf("") }

    fun navigateTo(screen: Screen) {
        if (currentScreen != screen) {
            backStack.add(currentScreen)
            currentScreen = screen
        }
    }

    fun navigateBack() {
        if (backStack.isNotEmpty()) {
            currentScreen = backStack.removeAt(backStack.size - 1)
        } else if (currentScreen != Screen.HOME) {
            currentScreen = Screen.HOME
        }
    }

    fun openPeerChat(peerId: String) {
        selectedPeerId = peerId
        navigateTo(Screen.PEER_DIRECT_CHAT)
    }

    BackHandler(enabled = (currentScreen != Screen.HOME || backStack.isNotEmpty())) {
        navigateBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HardwareRequirementBanner()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentScreen) {
                    Screen.HOME -> MeshDashboardScreen(
                        onNavigate = { navigateTo(it) },
                        onOpenPeerChat = { peerId -> openPeerChat(peerId) }
                    )
                    Screen.MESSAGES -> MessagesScreen(
                        onNavigate = { navigateTo(it) },
                        onOpenPeerChat = { peerId -> openPeerChat(peerId) }
                    )
                    Screen.PEER_DIRECT_CHAT -> PeerDirectChatScreen(
                        peerId = selectedPeerId,
                        onNavigate = { navigateTo(it) }
                    )
                    Screen.MESH -> NearbyDevicesScreen(
                        onNavigate = { navigateTo(it) },
                        onBackClick = { navigateBack() },
                        onOpenPeerDetails = { peerId ->
                            selectedPeerId = peerId
                            navigateTo(Screen.PEER_DETAILS)
                        },
                        onOpenPeerChat = { peerId -> openPeerChat(peerId) }
                    )
                    Screen.FILES -> FilesScreen(onNavigate = { navigateTo(it) })
                    Screen.SETTINGS -> SettingsScreen(onNavigate = { navigateTo(it) }, onLogout = onLogout)
                    Screen.SOS_CENTER -> SosCenterScreen(onNavigate = { navigateTo(it) })
                    Screen.SEND_SOS -> SendSosScreen(onNavigate = { navigateTo(it) })
                    Screen.SEND_FILE -> SendFileScreen(onNavigate = { navigateTo(it) })
                    Screen.FILE_TRANSFER -> FileTransferScreen(onNavigate = { navigateTo(it) })
                    Screen.PEER_DETAILS -> PeerDetailsScreen(
                        peerId = selectedPeerId,
                        onNavigate = { navigateTo(it) },
                        onOpenPeerChat = { peerId -> openPeerChat(peerId) },
                        onBackClick = { navigateBack() }
                    )
                    Screen.CHANNELS -> ChannelsScreen(onNavigate = { navigateTo(it) })
                    Screen.CHAT_DETAIL -> ChatDetailScreen(onNavigate = { navigateTo(it) })
                    Screen.SPLASH -> SplashScreen(onNavigate = { navigateTo(it) })
                    Screen.ONBOARDING -> OnBoardingScreen(onNavigate = { navigateTo(it) })
                    Screen.PERMISSIONS -> PermissionsScreen(onNavigate = { navigateTo(it) })
                    Screen.CREATE_IDENTITY -> CreateIdentityScreen(onNavigate = { navigateTo(it) })
                    Screen.NETWORK_STATUS -> NetworkStatusScreen(onNavigate = { navigateTo(it) })
                    Screen.SECURITY_PRIVACY -> SecurityPrivacyScreen(onNavigate = { navigateTo(it) })
                    Screen.DEBUG_CONSOLE -> DebugConsoleScreen(onNavigate = { navigateTo(it) })
                    Screen.EMERGENCY_CONTACTS -> com.example.zerogrid.contacts.EmergencyContactsScreen(
                        onNavigate = { navigateTo(it) },
                        onBack = { navigateBack() }
                    )
                    Screen.FAMILY_LINKS -> com.example.zerogrid.family.FamilyLinksScreen(
                        onNavigate = { navigateTo(it) },
                        onBack = { navigateBack() }
                    )
                    Screen.PROFILE -> com.example.zerogrid.profile.ProfileScreen(
                        onNavigate = { navigateTo(it) },
                        onBack = { navigateBack() },
                        onLogout = onLogout
                    )
                }
            }
        }


        // Global High-Priority Emergency Pop-Up Alert Dialog
        if (activeSosAlert != null) {
            Dialog(
                onDismissRequest = {
                    // Do not dismiss on accidental outside click for safety
                },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, Brush.linearGradient(listOf(SosRed, SosAmber, SosRed)), RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarker),
                        shape = RoundedCornerShape(20.dp)
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
                                    .background(SosRed.copy(alpha = 0.2f), CircleShape)
                                    .border(1.5.dp, SosRed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "SOS Warning",
                                    tint = SosRed,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "EMERGENCY SOS BEACON",
                                color = SosRed,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )

                            Text(
                                text = "Incoming broadcast received via mesh",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CardBackground),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "FROM: ${activeSosAlert.senderId}",
                                            color = SosCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "${activeSosAlert.hopCount} HOPS",
                                            color = SosAmber,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = activeSosAlert.payload,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    meshEngine.acknowledgeSosAlert(activeSosAlert.packetId)
                                    navigateTo(Screen.SOS_CENTER)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SosRed),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "RESPOND IN SOS CENTER",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    meshEngine.acknowledgeSosAlert(activeSosAlert.packetId)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "ACKNOWLEDGE & DISMISS",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
