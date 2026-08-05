package com.falahpro.app

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.falahpro.app.auth.AuthNavigation
import com.falahpro.app.auth.SupabaseAuthManager
import com.falahpro.app.dua.DuaDetailScreen
import com.falahpro.app.dua.DuaLibraryScreen
import com.falahpro.app.dua.DuaListScreen
import com.falahpro.app.profile.ProfileScreen
import com.falahpro.app.prayer.PrayerDiagnosticsScreen
import com.falahpro.app.prayer.PrayerTrackerScreen
import com.falahpro.app.prayer.rememberPrayerViewModel
import com.falahpro.app.prayer.rememberPrayerVisualEffects
import com.falahpro.app.qibla.QiblaScreen
import com.falahpro.app.tasbih.TasbihScreen
import com.falahpro.app.ui.theme.SplashScreenJcTheme
import io.github.jan.supabase.auth.status.SessionStatus
import java.util.concurrent.atomic.AtomicBoolean

class FalahPro : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashStartMs = SystemClock.uptimeMillis()
        val firstScreenDrawn = AtomicBoolean(false)
        val splashExitAllowed = AtomicBoolean(false)

        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            val underMinDuration =
                SystemClock.uptimeMillis() - splashStartMs < SPLASH_DURATION_MS
            underMinDuration || !splashExitAllowed.get()
        }
        splashScreen.setOnExitAnimationListener { provider ->
            // Instant reveal of the already-drawn screen.
            // Never alpha-fade the cream splash over dark UI (that blend = flicker).
            provider.iconView.animate().cancel()
            provider.view.animate().cancel()
            provider.iconView.visibility = View.GONE
            provider.iconView.alpha = 0f
            provider.view.alpha = 0f
            provider.remove()
            window.decorView.postDelayed({ maybeRequestNotifications() }, 400L)
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SplashScreenJcTheme(dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppBackground)
                ) {
                    AuthGate(
                        onFirstScreenDrawn = {
                            if (firstScreenDrawn.compareAndSet(false, true)) {
                                // Extra settle so Tasbih count/gradient finish first paint.
                                window.decorView.postDelayed({
                                    splashExitAllowed.set(true)
                                }, 120L)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
    }

    companion object {
        private const val SPLASH_DURATION_MS = 1_000L
        private val AppBackground = Color(0xFF1A120F)
    }
}

@Composable
fun AppNavigation(
    onFirstScreenDrawn: () -> Unit = {}
) {

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    fun navigateToTab(route: String) {
        // Profile is pushed on top of tabs; pop back to the tab when it's already under us.
        if (navController.popBackStack(route, inclusive = false)) {
            return
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A120F)),
        containerColor = Color(0xFF1A120F),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1A120F)
            ) {
                NavigationBarItem(
                    selected = currentRoute == "tasbih",
                    onClick = { navigateToTab("tasbih") },
                    icon = { Text("📿") },
                    label = { Text("Tasbih") }
                )
                NavigationBarItem(
                    selected = currentRoute == "prayer",
                    onClick = { navigateToTab("prayer") },
                    icon = { Text("🕌") },
                    label = { Text("Prayer") }
                )
                NavigationBarItem(
                    selected = currentRoute == "qibla",
                    onClick = { navigateToTab("qibla") },
                    icon = { Text("🧭") },
                    label = { Text("Qibla") }
                )
                NavigationBarItem(
                    selected = currentRoute == "dua",
                    onClick = { navigateToTab("dua") },
                    icon = { Text("📖") },
                    label = { Text("Dua") }
                )
            }
        }
    ) { innerPadding ->

        val prayerViewModel = rememberPrayerViewModel()
        val prayerVisualEffects = rememberPrayerVisualEffects()

        NavHost(
            navController = navController,
            startDestination = "tasbih",
            modifier = Modifier.padding(innerPadding)
        ) {

            composable("tasbih") {
                TasbihScreen(
                    onProfileClick = {
                        navController.navigate("profile") {
                            launchSingleTop = true
                        }
                    },
                    onReady = onFirstScreenDrawn
                )
            }

            composable("prayer") {
                PrayerTrackerScreen(
                    viewModel = prayerViewModel,
                    visualEffects = prayerVisualEffects
                )
            }

            if (BuildConfig.DEBUG) {
                composable("prayer_diagnostics") {
                    PrayerDiagnosticsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable("qibla") {
                QiblaScreen(
                    onBack = { navigateToTab("tasbih") }
                )
            }

            composable("dua") {
                DuaLibraryScreen(
                    onCategoryClick = { category ->
                        navController.navigate("dua_list/${Uri.encode(category.file)}")
                    }
                )
            }

            composable("dua_list/{file}") { backStackEntry ->
                val file = Uri.decode(backStackEntry.arguments?.getString("file").orEmpty())

                DuaListScreen(
                    fileName = file,
                    onBack = { navController.popBackStack() },
                    onDuaClick = { dua ->
                        navController.navigate(
                            "dua_detail/${Uri.encode(file)}/${dua.id}"
                        )
                    }
                )
            }

            composable("dua_detail/{file}/{duaId}") { backStackEntry ->
                val file = Uri.decode(backStackEntry.arguments?.getString("file").orEmpty())
                val duaId = backStackEntry.arguments?.getString("duaId")?.toIntOrNull() ?: -1

                DuaDetailScreen(
                    fileName = file,
                    duaId = duaId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("profile") {
                ProfileScreen(
                    onLogout = {
                        navController.navigate("tasbih") {
                            popUpTo(0)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AuthGate(
    onFirstScreenDrawn: () -> Unit = {}
) {
    var isLoggedIn by remember { mutableStateOf(false) }
    var authResolved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        SupabaseAuthManager.sessionStatus.collect { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    isLoggedIn = true
                    authResolved = true
                }
                is SessionStatus.NotAuthenticated -> {
                    isLoggedIn = false
                    authResolved = true
                }
                SessionStatus.Initializing -> {
                    // Wait for storage load before deciding Login vs App.
                }
                is SessionStatus.RefreshFailure -> {
                    isLoggedIn = SupabaseAuthManager.isLoggedIn()
                    authResolved = true
                }
            }
        }
    }

    if (!authResolved) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { onFirstScreenDrawn() }
        )
    } else if (isLoggedIn) {
        AppNavigation(onFirstScreenDrawn = onFirstScreenDrawn)
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { onFirstScreenDrawn() }
        ) {
            AuthNavigation(
                onLoginSuccess = {
                    isLoggedIn = true
                }
            )
        }
    }
}
