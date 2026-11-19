package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.AqiStandard
import com.example.domain.model.PrecipitationUnit
import com.example.domain.model.PressureUnit
import com.example.domain.model.TemperatureUnit
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import com.example.domain.model.WindUnit
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassSectionHeader
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.SolarAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onUpdatePreferences: ((UserPreferences) -> UserPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    var proxyDraft by remember(preferences.weatherNext3ProxyUrl) {
        mutableStateOf(preferences.weatherNext3ProxyUrl)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. UNITS & SCALES
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    GlassSectionHeader(
                        title = "Meteorological Units & Scales",
                        icon = Icons.Rounded.Straighten,
                        subtitle = "Customize temperature, wind, precipitation, pressure & AQI standards"
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    SettingChipGroup(
                        label = "Temperature",
                        options = TemperatureUnit.entries,
                        selected = preferences.tempUnit,
                        optionLabel = { it.label },
                        onSelect = { u -> onUpdatePreferences { it.copy(tempUnit = u) } }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingChipGroup(
                        label = "Wind Speed",
                        options = WindUnit.entries,
                        selected = preferences.windUnit,
                        optionLabel = { it.symbol },
                        onSelect = { u -> onUpdatePreferences { it.copy(windUnit = u) } }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingChipGroup(
                        label = "Precipitation",
                        options = PrecipitationUnit.entries,
                        selected = preferences.precipUnit,
                        optionLabel = { it.label },
                        onSelect = { u -> onUpdatePreferences { it.copy(precipUnit = u) } }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingChipGroup(
                        label = "Atmospheric Pressure",
                        options = PressureUnit.entries,
                        selected = preferences.pressureUnit,
                        optionLabel = { it.symbol },
                        onSelect = { u -> onUpdatePreferences { it.copy(pressureUnit = u) } }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingChipGroup(
                        label = "Air Quality Standard (Never Mixed)",
                        options = AqiStandard.entries,
                        selected = preferences.aqiStandard,
                        optionLabel = { it.label },
                        onSelect = { s -> onUpdatePreferences { it.copy(aqiStandard = s) } }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingSwitchRow(
                        title = "24-Hour Time Format",
                        subtitle = "Display timestamps as 17:00 instead of 5:00 PM",
                        checked = preferences.use24HourTime,
                        testTag = "setting_24h_switch",
                        onCheckedChange = { v -> onUpdatePreferences { it.copy(use24HourTime = v) } }
                    )
                }
            }

            // 2. APPEARANCE & ACCESSIBILITY
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    GlassSectionHeader(
                        title = "Theme & Motion Accessibility",
                        icon = Icons.Rounded.Palette,
                        subtitle = "Dynamic sky atmospheres and reduced-motion controls"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingChipGroup(
                        label = "Visual Theme",
                        options = ThemeMode.entries,
                        selected = preferences.themeMode,
                        optionLabel = { it.label },
                        onSelect = { m -> onUpdatePreferences { it.copy(themeMode = m) } }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingSwitchRow(
                        title = "Reduced Motion Mode",
                        subtitle = "Disables continuous rain, snow, cloud drift, and shimmer animations for comfort and battery savings",
                        checked = preferences.reducedMotion,
                        testTag = "setting_reduced_motion_switch",
                        onCheckedChange = { v -> onUpdatePreferences { it.copy(reducedMotion = v) } }
                    )
                }
            }

            // 3. REFRESH & ALERTS PREFERENCES
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    GlassSectionHeader(
                        title = "Alerts & Refresh Cadence",
                        icon = Icons.Rounded.NotificationsActive,
                        subtitle = "Control meteorological alert monitoring and cache cadence"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingSwitchRow(
                        title = "Severe Weather Alerts (IMD / NOAA)",
                        subtitle = "Highlight official severe weather agency bulletins on the home screen",
                        checked = preferences.severeAlertsEnabled,
                        testTag = "setting_severe_alerts_switch",
                        onCheckedChange = { v -> onUpdatePreferences { it.copy(severeAlertsEnabled = v) } }
                    )
                    SettingSwitchRow(
                        title = "Precipitation Onset Alerts",
                        subtitle = "Surface upcoming 24-hour rainfall windows in intelligence summaries",
                        checked = preferences.precipAlertsEnabled,
                        testTag = "setting_precip_alerts_switch",
                        onCheckedChange = { v -> onUpdatePreferences { it.copy(precipAlertsEnabled = v) } }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingChipGroup(
                        label = "Cache Freshness Threshold",
                        options = listOf(15, 30, 60),
                        selected = preferences.refreshIntervalMinutes,
                        optionLabel = { "$it min" },
                        onSelect = { m -> onUpdatePreferences { it.copy(refreshIntervalMinutes = m) } }
                    )
                }
            }

            // 4. WEATHERNEXT 3 PROVIDER ADAPTER CONFIGURATION
            item {
                GlassCard(highlightTint = SolarAmber, modifier = Modifier.fillMaxWidth()) {
                    GlassSectionHeader(
                        title = "Google DeepMind WeatherNext 3 Engine",
                        icon = Icons.Rounded.Hub,
                        subtitle = "Live Google Weather API or authorized Cloud/Zarr proxy"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "WeatherNext 3 runs live via the Google Maps Platform Weather API (weather.googleapis.com) when GOOGLE_WEATHER_API_KEY is set in AI Studio Secrets, or via an authorized Cloud/Zarr JSON proxy URL below (authenticated with WEATHERNEXT_AUTH_TOKEN):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = proxyDraft,
                        onValueChange = { proxyDraft = it },
                        placeholder = { Text("https://your-weathernext3-proxy.run.app/forecast") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("weathernext_proxy_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onUpdatePreferences { it.copy(weatherNext3ProxyUrl = proxyDraft.trim()) } },
                        modifier = Modifier.testTag("save_weathernext_proxy_button")
                    ) {
                        Text("Save WeatherNext 3 Endpoint")
                    }
                }
            }

            // 5. SCIENTIFIC INTEGRITY & SOURCES DISCLOSURE
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    GlassSectionHeader(
                        title = "Data Provenance & Spatial Resolution",
                        icon = Icons.Rounded.CloudSync,
                        subtitle = "Zero fabricated data · Transparent source attribution"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Primary AI Engine: Google DeepMind WeatherNext 3 (live via Google Weather API key in Secrets or authorized Cloud/Zarr proxy).\n" +
                            "• Live Operational AI & NWP Models: Google DeepMind GraphCast 0.25° (AI), ECMWF AIFS 0.25° (AI), ECMWF IFS 0.25° + 50-member Ensemble, NOAA GFS Seamless, DWD ICON Seamless.\n" +
                            "• Live Radar: RainViewer Observed Radar (past 2 hours, Zoom ≤ 7) over OpenStreetMap & CartoDB basemaps.\n" +
                            "• Air Quality: CAMS Global Atmospheric Composition with distinct India CPCB & US EPA AQI scales.\n" +
                            "• Official Warnings: India Meteorological Department (IMD) / NDMA SACHET CAP RSS & NOAA NWS Alerts.\n" +
                            "• AI Assistant: Gemini 3.8 Flash via Firebase AI Logic (App Check attested).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> SettingChipGroup(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { opt ->
                FilterChip(
                    selected = opt == selected,
                    onClick = { onSelect(opt) },
                    label = { Text(optionLabel(opt), style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AuroraCyan.copy(alpha = 0.24f),
                        selectedLabelColor = AuroraCyan
                    )
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
