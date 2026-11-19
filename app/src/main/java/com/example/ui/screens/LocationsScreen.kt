package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.MeteorologyCalculator
import com.example.domain.model.AqiStandard
import com.example.domain.model.SavedLocation
import com.example.domain.model.UserPreferences
import com.example.domain.model.WeatherBundle
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassSectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.WeatherConditionIcon
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.EmeraldClear
import com.example.ui.theme.SolarAmber
import com.example.viewmodel.WeatherUiState
import java.util.Locale

@Composable
fun LocationsScreen(
    uiState: WeatherUiState,
    savedLocations: List<SavedLocation>,
    cachedBundles: Map<String, WeatherBundle>,
    preferences: UserPreferences,
    onSearchQueryChange: (String) -> Unit,
    onAddLocation: (SavedLocation) -> Unit,
    onSelectLocation: (SavedLocation) -> Unit,
    onRemoveLocation: (SavedLocation) -> Unit,
    onRequestGps: () -> Unit,
    onRefreshAllCities: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Search & GPS Bar
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    GlassSectionHeader(
                        title = "Global & Indian Location Search",
                        icon = Icons.Rounded.Search,
                        subtitle = "Search cities, districts, or neighborhoods worldwide"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search Kolkata, Salt Lake, Darjeeling, Zurich…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuroraCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.20f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("city_search_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRequestGps,
                            modifier = Modifier.weight(1f).testTag("locations_gps_button")
                        ) {
                            Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Use Current GPS")
                        }
                        Surface(
                            onClick = onRefreshAllCities,
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("refresh_all_cities_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Sync all cities", tint = AuroraCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync All", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    // Autocomplete Results
                    if (uiState.searchResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                        uiState.searchResults.forEach { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddLocation(result) }
                                    .padding(vertical = 10.dp)
                                    .testTag("search_result_${result.id}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${result.adminRegion}${if (result.country.isNotBlank()) " · ${result.country}" else ""} (${String.format(Locale.US, "%.2f, %.2f", result.latitude, result.longitude)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.AddLocationAlt,
                                    contentDescription = "Add ${result.name}",
                                    tint = AuroraCyan
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                        }
                    }
                }
            }

            // Saved Locations List
            item {
                Text(
                    text = "SAVED LOCATIONS (${savedLocations.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            items(savedLocations, key = { it.id }) { loc ->
                val bundle = cachedBundles[loc.id] ?: if (uiState.activeLocation?.id == loc.id) uiState.weatherBundle else null
                val isSelected = loc.id == uiState.activeLocation?.id

                GlassCard(
                    highlightTint = if (isSelected) AuroraCyan else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectLocation(loc) }
                        .testTag("saved_city_card_${loc.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = if (loc.isCurrentGps) Icons.Rounded.MyLocation else Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = if (isSelected) AuroraCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = loc.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    StatusPill("ACTIVE", AuroraCyan.copy(alpha = 0.22f), AuroraCyan)
                                }
                            }
                            Text(
                                text = "${loc.adminRegion}, ${loc.country}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (bundle != null) {
                                val localNow = MeteorologyCalculator.formatEpochInOffset(
                                    System.currentTimeMillis(),
                                    bundle.utcOffsetSeconds,
                                    preferences.use24HourTime
                                )
                                Text(
                                    text = "$localNow ${bundle.timezoneAbbreviation} · ${MeteorologyCalculator.wmoDescription(bundle.current.weatherCode)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AuroraCyan
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (bundle != null) {
                                WeatherConditionIcon(
                                    weatherCode = bundle.current.weatherCode,
                                    isDay = bundle.current.isDay,
                                    size = 30.dp
                                )
                                Text(
                                    text = MeteorologyCalculator.formatTemp(bundle.current.temperatureC, preferences.tempUnit),
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Text("Tap to sync", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (savedLocations.size > 1) {
                                IconButton(
                                    onClick = { onRemoveLocation(loc) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove ${loc.name}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Side-by-Side City Weather Comparison Matrix
            val syncedBundles = savedLocations.mapNotNull { loc -> cachedBundles[loc.id] }
            if (syncedBundles.size >= 2) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        GlassSectionHeader(
                            title = "Multi-City Weather Comparison",
                            icon = Icons.Rounded.CompareArrows,
                            subtitle = "Synced live conditions across saved destinations"
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("CITY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.3f))
                            Text("TEMP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.8f))
                            Text("HUM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.7f))
                            Text("RAIN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.9f))
                            Text("AQI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.7f))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

                        syncedBundles.forEach { b ->
                            val aqi = if (preferences.aqiStandard == AqiStandard.INDIA_CPCB) b.airQuality?.indiaCpcbAqi else b.airQuality?.usAqi
                            val rain24 = b.daily.firstOrNull()?.precipitationSumMm ?: 0.0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = b.location.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1.3f)
                                )
                                Text(
                                    text = MeteorologyCalculator.formatTemp(b.current.temperatureC, preferences.tempUnit, true),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SolarAmber,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(0.8f)
                                )
                                Text(
                                    text = "${b.current.relativeHumidity}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(0.7f)
                                )
                                Text(
                                    text = MeteorologyCalculator.formatPrecipitation(rain24, preferences.precipUnit),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AuroraCyan,
                                    modifier = Modifier.weight(0.9f)
                                )
                                Text(
                                    text = aqi?.toString() ?: "—",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldClear,
                                    modifier = Modifier.weight(0.7f)
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
