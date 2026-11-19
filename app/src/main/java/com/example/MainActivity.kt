package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.domain.engine.MeteorologyCalculator
import com.example.domain.model.WeatherScene
import com.example.ui.components.WeatherEnvironmentBackground
import com.example.ui.screens.HomeWeatherScreen
import com.example.ui.screens.IntelSubTab
import com.example.ui.screens.IntelligenceScreen
import com.example.ui.screens.LocationsScreen
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AetherTheme
import com.example.ui.theme.AuroraCyan
import com.example.viewmodel.AetherViewModel

private enum class AetherDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    WEATHER("Weather", Icons.Rounded.WbSunny, Icons.Outlined.WbSunny, "nav_weather"),
    RADAR("Radar", Icons.Rounded.Radar, Icons.Outlined.Radar, "nav_radar"),
    INTEL("Intel", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_intel"),
    LOCATIONS("Cities", Icons.Rounded.LocationCity, Icons.Outlined.LocationCity, "nav_cities"),
    SETTINGS("Settings", Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (MANDATORY): Configure Firebase App Check with launch intent before UI / ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            AetherWeatherApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}

@Composable
fun AetherWeatherApp(
    viewModel: AetherViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val savedLocations by viewModel.savedLocations.collectAsStateWithLifecycle()
    val cachedCityBundles by viewModel.cachedCityBundles.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(AetherDestination.WEATHER) }
    var intelInitialSubTab by remember { mutableStateOf(IntelSubTab.ASSISTANT) }

    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.detectGpsLocation()
        }
    }

    val requestGpsWithPermission: () -> Unit = {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            viewModel.detectGpsLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    // Handle system back press from secondary destinations back to Home Weather
    BackHandler(enabled = currentTab != AetherDestination.WEATHER) {
        currentTab = AetherDestination.WEATHER
    }

    val activeScene = remember(uiState.weatherBundle) {
        val b = uiState.weatherBundle
        if (b != null) {
            MeteorologyCalculator.resolveScene(
                weatherCode = b.current.weatherCode,
                isDay = b.current.isDay,
                solarProgress = b.astronomy.solarProgress
            )
        } else {
            WeatherScene.CLEAR_NIGHT
        }
    }

    AetherTheme(
        themeMode = preferences.themeMode,
        weatherScene = activeScene
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Immersive dynamic atmospheric sky background
            WeatherEnvironmentBackground(
                scene = activeScene,
                themeMode = preferences.themeMode,
                reducedMotion = preferences.reducedMotion
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 640.dp

                if (useNavRail) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavigationRail(
                            containerColor = Color(0xCC0A1424),
                            contentColor = Color.White,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            AetherDestination.entries.forEach { dest ->
                                val selected = currentTab == dest
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = dest },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }

                        Scaffold(
                            containerColor = Color.Transparent,
                            contentWindowInsets = WindowInsets.safeDrawing,
                            modifier = Modifier.weight(1f)
                        ) { innerPadding ->
                            AetherScreenContent(
                                currentTab = currentTab,
                                intelInitialSubTab = intelInitialSubTab,
                                uiState = uiState,
                                preferences = preferences,
                                savedLocations = savedLocations,
                                cachedCityBundles = cachedCityBundles,
                                viewModel = viewModel,
                                onRequestGps = requestGpsWithPermission,
                                onNavigate = { dest, subTab ->
                                    if (subTab != null) intelInitialSubTab = subTab
                                    currentTab = dest
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                } else {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        bottomBar = {
                            NavigationBar(
                                containerColor = Color(0xD90A1424),
                                contentColor = Color.White,
                                tonalElevation = 0.dp
                            ) {
                                AetherDestination.entries.forEach { dest ->
                                    val selected = currentTab == dest
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = dest },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        },
                                        label = { Text(dest.label, maxLines = 1) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = AuroraCyan,
                                            selectedTextColor = AuroraCyan,
                                            indicatorColor = AuroraCyan.copy(alpha = 0.18f),
                                            unselectedIconColor = Color.White.copy(alpha = 0.68f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.68f)
                                        ),
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        AetherScreenContent(
                            currentTab = currentTab,
                            intelInitialSubTab = intelInitialSubTab,
                            uiState = uiState,
                            preferences = preferences,
                            savedLocations = savedLocations,
                            cachedCityBundles = cachedCityBundles,
                            viewModel = viewModel,
                            onRequestGps = requestGpsWithPermission,
                            onNavigate = { dest, subTab ->
                                if (subTab != null) intelInitialSubTab = subTab
                                currentTab = dest
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AetherScreenContent(
    currentTab: AetherDestination,
    intelInitialSubTab: IntelSubTab,
    uiState: com.example.viewmodel.WeatherUiState,
    preferences: com.example.domain.model.UserPreferences,
    savedLocations: List<com.example.domain.model.SavedLocation>,
    cachedCityBundles: Map<String, com.example.domain.model.WeatherBundle>,
    viewModel: AetherViewModel,
    onRequestGps: () -> Unit,
    onNavigate: (AetherDestination, IntelSubTab?) -> Unit,
    modifier: Modifier = Modifier
) {
    when (currentTab) {
        AetherDestination.WEATHER -> HomeWeatherScreen(
            uiState = uiState,
            preferences = preferences,
            onRefresh = { viewModel.refreshCurrentWeather() },
            onRequestGps = onRequestGps,
            onOpenLocations = { onNavigate(AetherDestination.LOCATIONS, null) },
            onOpenAlerts = { onNavigate(AetherDestination.INTEL, IntelSubTab.ALERTS) },
            onOpenAssistant = { onNavigate(AetherDestination.INTEL, IntelSubTab.ASSISTANT) },
            onOpenRadar = { onNavigate(AetherDestination.RADAR, null) },
            modifier = modifier
        )

        AetherDestination.RADAR -> RadarScreen(
            activeBundle = uiState.weatherBundle,
            savedLocations = savedLocations,
            radarState = uiState.radarState,
            preferences = preferences,
            onRefreshRadar = { viewModel.refreshRadar() },
            modifier = modifier
        )

        AetherDestination.INTEL -> IntelligenceScreen(
            uiState = uiState,
            initialSubTab = intelInitialSubTab,
            onAskAssistant = { viewModel.askWeatherAssistant(it) },
            onClearChat = { viewModel.clearChatHistory() },
            onSelectBenchmark = { viewModel.runVerification(it) },
            onVerifyActiveLocation = { viewModel.verifyActiveLocation() },
            modifier = modifier
        )

        AetherDestination.LOCATIONS -> LocationsScreen(
            uiState = uiState,
            savedLocations = savedLocations,
            cachedBundles = cachedCityBundles,
            preferences = preferences,
            onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
            onAddLocation = {
                viewModel.addAndSelectLocation(it)
                onNavigate(AetherDestination.WEATHER, null)
            },
            onSelectLocation = {
                viewModel.selectLocation(it)
                onNavigate(AetherDestination.WEATHER, null)
            },
            onRemoveLocation = { viewModel.removeLocation(it) },
            onRequestGps = {
                onRequestGps()
                onNavigate(AetherDestination.WEATHER, null)
            },
            onRefreshAllCities = { viewModel.refreshAllSavedCities() },
            modifier = modifier
        )

        AetherDestination.SETTINGS -> SettingsScreen(
            preferences = preferences,
            onUpdatePreferences = { viewModel.updatePreferences(it) },
            modifier = modifier
        )
    }
}
