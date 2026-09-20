package com.example.zerogrid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import com.example.zerogrid.admin.AdminPanelScreen
import com.example.zerogrid.auth.LoginScreen
import com.example.zerogrid.auth.ProfileCompletionScreen
import com.example.zerogrid.auth.RegisterScreen
import com.example.zerogrid.navigation.ZeroGridApp
import com.example.zerogrid.service.MeshForegroundService
import com.example.zerogrid.ui.theme.ZeroGridTheme
import com.zerogrid.mesh.app.ui.UserRole
import com.zerogrid.mesh.app.ui.UserSessionManager
import com.zerogrid.mesh.app.ui.navigation.AppScreen
import com.example.zerogrid.ui.ThemeMode
import com.example.zerogrid.ui.ThemePreferenceManager

    private val THEME_KEY = stringPreferencesKey("theme_mode")

// ── Main App Gateway ───────────────────────────────────────────────────────

@Composable
fun MainAppGateway() {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager.getInstance(context) }

    // Determine starting screen: auto-login if a token is already stored
    val startScreen = remember {
        if (sessionManager.isLoggedIn()) {
            if (!sessionManager.isProfileComplete()) {
                AppScreen.ProfileCompletion
            } else {
                when (sessionManager.getUserRole()) {
                    UserRole.ADMIN   -> AppScreen.AdminPanel
                    UserRole.CITIZEN -> AppScreen.UserDashboard
                    null             -> AppScreen.Login
                }
            }
        } else {
            AppScreen.Login
        }
    }

    var currentScreen by remember { mutableStateOf<AppScreen>(startScreen) }

    fun routeToDashboard(role: UserRole) {
        currentScreen = if (role == UserRole.ADMIN) AppScreen.AdminPanel else AppScreen.UserDashboard
    }

    fun logout() {
        Log.d("MainAppGateway", "User logged out. Stopping mesh service.")
        try {
            MeshForegroundService.stopService(context)
            com.example.zerogrid.mesh.engine.MeshEngine.getInstance(context).stopMesh()
        } catch (e: Exception) {
            Log.e("MainAppGateway", "Error stopping mesh service on logout", e)
        }
        sessionManager.clearSession()
        currentScreen = AppScreen.Login
    }

    // Start mesh service and sync FCM push token when the user enters an authenticated dashboard
    LaunchedEffect(currentScreen) {
        val isAuthenticated = currentScreen == AppScreen.UserDashboard || currentScreen == AppScreen.AdminPanel
        if (isAuthenticated && sessionManager.isLoggedIn()) {
            Log.d("MainAppGateway", "Authenticated screen active ($currentScreen). Starting mesh service.")
            MeshForegroundService.startService(context)
            com.example.zerogrid.service.ZeroGridFirebaseMessagingService.syncTokenWithBackend(context)
        }
    }

    // Back-handling: only block back on screens where it makes sense
    BackHandler(enabled = currentScreen == AppScreen.Register || currentScreen == AppScreen.ProfileCompletion) {
        if (currentScreen == AppScreen.Register) {
            currentScreen = AppScreen.Login
        } else if (currentScreen == AppScreen.ProfileCompletion) {
            val role = sessionManager.getUserRole() ?: UserRole.CITIZEN
            routeToDashboard(role)
        }
    }

    when (currentScreen) {

        // ── Auth screens ───────────────────────────────────────────────
        AppScreen.Login -> {
            LoginScreen(
                sessionManager = sessionManager,
                onNavigateToRegister = { currentScreen = AppScreen.Register },
                onLoginSuccess = { role, profileComplete ->
                    if (!profileComplete) {
                        currentScreen = AppScreen.ProfileCompletion
                    } else {
                        routeToDashboard(role)
                    }
                }
            )
        }

        AppScreen.Register -> {
            RegisterScreen(
                sessionManager = sessionManager,
                onNavigateToLogin = { currentScreen = AppScreen.Login },
                onRegisterSuccess = { role, profileComplete ->
                    if (!profileComplete) {
                        currentScreen = AppScreen.ProfileCompletion
                    } else {
                        routeToDashboard(role)
                    }
                }
            )
        }

        // ── Profile completion ─────────────────────────────────────────
        AppScreen.ProfileCompletion -> {
            ProfileCompletionScreen(
                sessionManager = sessionManager,
                onProfileCompleted = {
                    val role = sessionManager.getUserRole() ?: UserRole.CITIZEN
                    routeToDashboard(role)
                },
                onSkip = {
                    val role = sessionManager.getUserRole() ?: UserRole.CITIZEN
                    routeToDashboard(role)
                }
            )
        }

        // ── Citizen: existing mesh app ─────────────────────────────────
        AppScreen.UserDashboard -> {
            ZeroGridApp(onLogout = { logout() })
        }

        // ── Admin: dedicated admin panel ───────────────────────────────
        AppScreen.AdminPanel -> {
            AdminPanelScreen(
                sessionManager = sessionManager,
                onOpenMeshApp = { currentScreen = AppScreen.UserDashboard },
                onLogout = { logout() }
            )
        }
    }
}

// ── MainActivity ───────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d("MainActivity", "All required permissions granted.")
        } else {
            Log.w("MainActivity", "Some permissions were denied. Mesh functionality may be limited.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val startTime = System.currentTimeMillis()
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate started")

        checkAndRequestPermissions()

        val themePreferenceManager = ThemePreferenceManager(this)

        setContent {
            // Collect the theme state from DataStore
            val themeMode by themePreferenceManager.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            // Resolve the actual boolean to pass into your theme
            val isDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            ZeroGridTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppGateway()
                }
            }
        }
        Log.d("MainActivity", "onCreate finished in ${System.currentTimeMillis() - startTime}ms")
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.d("MainActivity", "Requesting missing permissions: $missingPermissions")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Log.d("MainActivity", "All required permissions already granted.")
        }
    }
}