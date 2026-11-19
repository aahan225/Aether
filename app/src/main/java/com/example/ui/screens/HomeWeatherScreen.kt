package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.MeteorologyCalculator
import com.example.domain.model.AlertProvenance
import com.example.domain.model.AqiStandard
import com.example.domain.model.DailyPoint
import com.example.domain.model.ForecastHorizon
import com.example.domain.model.HourlyPoint
import com.example.domain.model.UserPreferences
import com.example.domain.model.WeatherBundle
import com.example.ui.components.AqiSpectrumBar
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassSectionHeader
import com.example.ui.components.InteractiveHourlyChart
import com.example.ui.components.MiniSparklineChart
import com.example.ui.components.StatusPill
import com.example.ui.components.SunAndMoonArcView
import com.example.ui.components.WeatherConditionIcon
import com.example.ui.components.WeatherSkeletonLoader
import com.example.ui.components.WindCompassDial
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldClear
import com.example.ui.theme.LavenderMist
import com.example.ui.theme.SolarAmber
import com.example.viewmodel.WeatherUiState
import java.util.Locale

@Composable
fun HomeWeatherScreen(
    uiState: WeatherUiState,
    preferences: UserPreferences,
    onRefresh: () -> Unit,
    onRequestGps: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenRadar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (uiState.isLoading && uiState.weatherBundle == null) {
            WeatherSkeletonLoader(modifier = Modifier.widthIn(max = 680.dp))
            return@Box
        }

        val bundle = uiState.weatherBundle
        if (bundle == null) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GlassCard {
                    Text(
                        text = uiState.errorMessage ?: "Weather telemetry is currently unavailable.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("retry_weather_button")
                    ) {
                        Text("Retry Live Fetch")
                    }
                }
            }
            return@Box
        }

        val (freshnessLabel, isStale) = MeteorologyCalculator.formatFreshness(bundle.fetchedAtUtcMillis)
        val localTimeNow = MeteorologyCalculator.formatEpochInOffset(
            System.currentTimeMillis(),
            bundle.utcOffsetSeconds,
            preferences.use24HourTime
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. LOCATION HEADER
            item(key = "header") {
                LocationHeaderBar(
                    bundle = bundle,
                    localTimeNow = localTimeNow,
                    freshnessLabel = freshnessLabel,
                    isStale = isStale || bundle.isFromCache,
                    isOffline = uiState.isOffline,
                    isRefreshing = uiState.isRefreshing,
                    gpsMessage = uiState.gpsStatusMessage,
                    onRefresh = onRefresh,
                    onRequestGps = onRequestGps,
                    onOpenLocations = onOpenLocations
                )
            }

            // 2. HERO WEATHER DISPLAY
            item(key = "hero") {
                HeroWeatherSection(
                    bundle = bundle,
                    preferences = preferences,
                    onOpenAssistant = onOpenAssistant,
                    onOpenRadar = onOpenRadar
                )
            }

            // 3. SEVERE WEATHER ALERTS BANNER (IF ANY)
            if (bundle.alerts.isNotEmpty()) {
                item(key = "alerts_banner") {
                    AlertsPreviewCard(bundle = bundle, onOpenAlerts = onOpenAlerts)
                }
            }

            // 4. DATA-GROUNDED WEATHER SUMMARY & OUTDOOR INDEX
            item(key = "summary") {
                IntelligentSummaryCard(bundle = bundle, preferences = preferences)
            }

            // 5. HOURLY FORECAST + INTERACTIVE GRAPH
            item(key = "hourly") {
                HourlyForecastSection(
                    hourly = bundle.hourly,
                    preferences = preferences
                )
            }

            // 6. 16-DAY EXPANDABLE DAILY FORECAST WITH HORIZON SKILL
            item(key = "daily") {
                DailyForecastSection(
                    daily = bundle.daily,
                    preferences = preferences
                )
            }

            // 7. WIND & BAROMETRIC PRESSURE ROW
            item(key = "wind_pressure") {
                WindAndPressureModule(bundle = bundle, preferences = preferences)
            }

            // 8. PRECIPITATION, HUMIDITY, UV, VISIBILITY & CLOUD LAYERS
            item(key = "atmosphere_grid") {
                AtmosphericMetricsGrid(bundle = bundle, preferences = preferences)
            }

            // 9. AIR QUALITY (INDIA CPCB / US EPA SCALE-AWARE)
            if (bundle.airQuality != null) {
                item(key = "aqi") {
                    AirQualityModule(bundle = bundle, preferences = preferences)
                }
            }

            // 10. ASTRONOMY: SUN ARC, GOLDEN/BLUE HOUR & MOON PHASE
            item(key = "astronomy") {
                AstronomyModule(bundle = bundle, use24Hour = preferences.use24HourTime)
            }

            // 11. TRANSPARENT MULTI-SOURCE & WEATHERNEXT 3 RECONCILIATION
            item(key = "sources") {
                MultiSourceProvenanceCard(bundle = bundle, preferences = preferences)
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LocationHeaderBar(
    bundle: WeatherBundle,
    localTimeNow: String,
    freshnessLabel: String,
    isStale: Boolean,
    isOffline: Boolean,
    isRefreshing: Boolean,
    gpsMessage: String?,
    onRefresh: () -> Unit,
    onRequestGps: () -> Unit,
    onOpenLocations: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenLocations)
                    .padding(vertical = 4.dp)
                    .testTag("location_header_selector")
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = "Selected location",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = bundle.location.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${bundle.location.adminRegion}${if (bundle.location.country.isNotBlank()) ", ${bundle.location.country}" else ""} · $localTimeNow ${bundle.timezoneAbbreviation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRequestGps,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("gps_detect_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "Detect GPS location",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(
                    onClick = onOpenLocations,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("search_locations_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search locations",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_weather_button")
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh weather data",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // Freshness / Offline / GPS status bar
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOffline) {
                StatusPill(
                    text = "OFFLINE · CACHED DATA",
                    containerColor = CoralAlert.copy(alpha = 0.22f),
                    contentColor = CoralAlert
                )
            } else if (isStale) {
                StatusPill(
                    text = "CACHED · $freshnessLabel",
                    containerColor = SolarAmber.copy(alpha = 0.22f),
                    contentColor = SolarAmber
                )
            } else {
                StatusPill(
                    text = "LIVE · $freshnessLabel",
                    containerColor = EmeraldClear.copy(alpha = 0.18f),
                    contentColor = EmeraldClear
                )
            }
            if (!gpsMessage.isNullOrBlank()) {
                Text(
                    text = gpsMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroraCyan
                )
            }
        }
    }
}

@Composable
private fun HeroWeatherSection(
    bundle: WeatherBundle,
    preferences: UserPreferences,
    onOpenAssistant: () -> Unit,
    onOpenRadar: () -> Unit
) {
    val c = bundle.current
    val today = bundle.daily.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = MeteorologyCalculator.formatTemp(c.temperatureC, preferences.tempUnit),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 86.sp, lineHeight = 86.sp),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = MeteorologyCalculator.wmoDescription(c.weatherCode),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                val hiLoText = if (today != null) {
                    "H ${MeteorologyCalculator.formatTemp(today.tempMaxC, preferences.tempUnit)} · L ${MeteorologyCalculator.formatTemp(today.tempMinC, preferences.tempUnit)} · Feels like ${MeteorologyCalculator.formatTemp(c.apparentTemperatureC, preferences.tempUnit)}"
                } else {
                    "Feels like ${MeteorologyCalculator.formatTemp(c.apparentTemperatureC, preferences.tempUnit)}"
                }
                Text(
                    text = hiLoText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            WeatherConditionIcon(
                weatherCode = c.weatherCode,
                isDay = c.isDay,
                size = 84.dp,
                contentDescription = MeteorologyCalculator.wmoDescription(c.weatherCode)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick action glass pills (AI Weather Assistant + Live Radar)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onOpenAssistant,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                contentColor = MaterialTheme.colorScheme.onBackground,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("hero_ask_ai_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = AuroraCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ask Aether AI", style = MaterialTheme.typography.labelLarge)
                }
            }

            Surface(
                onClick = onOpenRadar,
                color = Color.White.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.onBackground,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("hero_open_radar_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudQueue,
                        contentDescription = null,
                        tint = SolarAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Live Radar", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun AlertsPreviewCard(bundle: WeatherBundle, onOpenAlerts: () -> Unit) {
    val topAlert = bundle.alerts.first()
    val officialCount = bundle.alerts.count { it.provenance == AlertProvenance.OFFICIAL_AGENCY }
    val accent = if (officialCount > 0) CoralAlert else SolarAmber

    GlassCard(
        highlightTint = accent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAlerts)
            .testTag("home_alerts_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = "Weather alert",
                tint = accent,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = if (topAlert.provenance == AlertProvenance.OFFICIAL_AGENCY) "OFFICIAL AGENCY" else "MODEL RISK",
                        containerColor = accent.copy(alpha = 0.24f),
                        contentColor = accent
                    )
                    if (bundle.alerts.size > 1) {
                        Text(
                            "+${bundle.alerts.size - 1} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = topAlert.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = topAlert.issuingAuthority,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IntelligentSummaryCard(
    bundle: WeatherBundle,
    preferences: UserPreferences
) {
    val activeAqi = if (preferences.aqiStandard == AqiStandard.INDIA_CPCB) {
        bundle.airQuality?.indiaCpcbAqi
    } else {
        bundle.airQuality?.usAqi
    }
    val (outdoorLabel, outdoorNote) = MeteorologyCalculator.outdoorActivityScore(
        tempC = bundle.current.temperatureC,
        precipProb = bundle.hourly.firstOrNull()?.precipitationProbability ?: 0,
        windKmh = bundle.current.windSpeedKmh,
        aqi = activeAqi,
        uv = bundle.current.uvIndex
    )

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        GlassSectionHeader(
            title = "Atmospheric Intelligence Briefing",
            icon = Icons.Rounded.AutoAwesome,
            subtitle = "Derived directly from multi-model hourly telemetry",
            trailing = {
                bundle.ensemble?.let { ens ->
                    StatusPill(
                        text = "±${String.format(Locale.US, "%.1f", ens.next24hTempSpreadC)}°C spread",
                        containerColor = AuroraCyan.copy(alpha = 0.16f),
                        contentColor = AuroraCyan
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        bundle.summaryBullets.forEach { bullet ->
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text("•  ", style = MaterialTheme.typography.bodyLarge, color = AuroraCyan)
                Text(
                    text = bullet,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OUTDOOR & TRAVEL CONDITIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = outdoorNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(
                text = outdoorLabel,
                containerColor = EmeraldClear.copy(alpha = 0.18f),
                contentColor = EmeraldClear
            )
        }
    }
}

@Composable
private fun HourlyForecastSection(
    hourly: List<HourlyPoint>,
    preferences: UserPreferences
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        GlassSectionHeader(
            title = "Hourly Forecast & Thermal Curve",
            icon = Icons.Rounded.Schedule,
            subtitle = "Next 48 hours · Local timezone"
        )
        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("hourly_forecast_row")
        ) {
            itemsIndexed(hourly.take(36)) { index, pt ->
                val isNow = index == 0
                Surface(
                    color = if (isNow) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    else Color.White.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.width(74.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = if (isNow) "Now" else MeteorologyCalculator.formatHourLabel(pt.timeIso, preferences.use24HourTime),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isNow) AuroraCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal
                        )
                        WeatherConditionIcon(
                            weatherCode = pt.weatherCode,
                            isDay = pt.isDay,
                            size = 24.dp
                        )
                        Text(
                            text = MeteorologyCalculator.formatTemp(pt.temperatureC, preferences.tempUnit),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        val prob = pt.precipitationProbability ?: 0
                        Text(
                            text = if (prob > 0) "$prob%" else "0%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (prob >= 30) AuroraCyan else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                        Text(
                            text = "${pt.windSpeedKmh.toInt()} ${preferences.windUnit.symbol}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        InteractiveHourlyChart(hourly = hourly, preferences = preferences)
    }
}

@Composable
private fun DailyForecastSection(
    daily: List<DailyPoint>,
    preferences: UserPreferences
) {
    if (daily.isEmpty()) return
    val globalMin = daily.minOf { it.tempMinC }
    val globalMax = daily.maxOf { it.tempMaxC }
    val globalSpan = (globalMax - globalMin).coerceAtLeast(1.0)
    var expandedDayIdx by remember { mutableStateOf<Int?>(0) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        GlassSectionHeader(
            title = "${daily.size}-Day Forecast Outlook",
            icon = Icons.Rounded.CalendarMonth,
            subtitle = "Short-range (Days 1–3) has higher skill than extended (Days 8–16)"
        )
        Spacer(modifier = Modifier.height(12.dp))

        daily.forEachIndexed { idx, day ->
            // Horizon divider labels so users never mistake Day 12 for Day 1 certainty
            if (idx == 0 || idx == 3 || idx == 7) {
                if (idx > 0) Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = day.horizon.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (day.horizon) {
                            ForecastHorizon.SHORT_RANGE -> EmeraldClear
                            ForecastHorizon.MEDIUM_RANGE -> AuroraCyan
                            ForecastHorizon.EXTENDED -> SolarAmber
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = day.horizon.certaintyNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val isExpanded = expandedDayIdx == idx
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { expandedDayIdx = if (isExpanded) null else idx }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
                    .testTag("daily_forecast_row_$idx")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = MeteorologyCalculator.formatDayLabel(day.dateIso, idx),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(92.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(68.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        WeatherConditionIcon(weatherCode = day.weatherCode, isDay = true, size = 22.dp)
                        val prob = day.precipitationProbabilityMax ?: 0
                        if (prob >= 15) {
                            Text(
                                text = "$prob%",
                                style = MaterialTheme.typography.labelSmall,
                                color = AuroraCyan
                            )
                        }
                    }

                    // Temperature Range Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = MeteorologyCalculator.formatTemp(day.tempMinC, preferences.tempUnit),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(30.dp)
                        )
                        val startFrac = ((day.tempMinC - globalMin) / globalSpan).toFloat().coerceIn(0f, 0.85f)
                        val widthFrac = ((day.tempMaxC - day.tempMinC) / globalSpan).toFloat().coerceIn(0.12f, 1f - startFrac)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(startFrac + widthFrac)
                                    .height(6.dp)
                                    .padding(start = (startFrac * 70).dp)
                                    .clip(CircleShape)
                                    .background(Brush.horizontalGradient(listOf(AuroraCyan, SolarAmber)))
                            )
                        }
                        Text(
                            text = MeteorologyCalculator.formatTemp(day.tempMaxC, preferences.tempUnit),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(32.dp)
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse day details" else "Expand day details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 4.dp, end = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${MeteorologyCalculator.wmoDescription(day.weatherCode)} · Feels ${MeteorologyCalculator.formatTemp(day.apparentTempMinC, preferences.tempUnit)}–${MeteorologyCalculator.formatTemp(day.apparentTempMaxC, preferences.tempUnit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Precip: ${MeteorologyCalculator.formatPrecipitation(day.precipitationSumMm, preferences.precipUnit)}" +
                                    if (day.snowfallSumCm > 0.1) " (Snow ${String.format(Locale.US, "%.1f", day.snowfallSumCm)} cm)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = AuroraCyan
                            )
                            Text(
                                text = "Wind: ${MeteorologyCalculator.formatWind(day.windSpeedMaxKmh, preferences.windUnit)} (G ${MeteorologyCalculator.formatWind(day.windGustsMaxKmh, preferences.windUnit)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Peak UV: ${day.uvIndexMax?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SolarAmber
                            )
                            Text(
                                text = "Solar: ${day.shortwaveRadiationSumMj?.let { String.format(Locale.US, "%.1f MJ/m²", it) } ?: "N/A"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (idx < daily.lastIndex) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
            }
        }
    }
}

@Composable
private fun WindAndPressureModule(
    bundle: WeatherBundle,
    preferences: UserPreferences
) {
    val c = bundle.current
    val next24Pressures = remember(bundle.hourly) { bundle.hourly.take(24).map { it.pressureMslHpa } }
    val next24Winds = remember(bundle.hourly) { bundle.hourly.take(24).map { it.windSpeedKmh } }
    val (pTrend, pDelta) = MeteorologyCalculator.pressureTrendLabel(next24Pressures)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Wind Compass & Evolution
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            GlassSectionHeader(
                title = "Wind Dynamics & Compass",
                icon = Icons.Rounded.Air,
                subtitle = "10-metre sustained wind & gusts"
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WindCompassDial(
                    windDirectionDeg = c.windDirectionDeg,
                    windSpeedLabel = MeteorologyCalculator.formatWind(c.windSpeedKmh, preferences.windUnit),
                    gustsLabel = MeteorologyCalculator.formatWind(c.windGustsKmh, preferences.windUnit)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Direction: ${c.windDirectionDeg}° (${MeteorologyCalculator.windCardinal(c.windDirectionDeg)})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Peak 24h Gust: ${MeteorologyCalculator.formatWind(bundle.hourly.take(24).maxOfOrNull { it.windGustsKmh } ?: c.windGustsKmh, preferences.windUnit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MiniSparklineChart(values = next24Winds, lineColor = AuroraCyan)
                }
            }
        }

        // Barometric Pressure Trend
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            GlassSectionHeader(
                title = "Atmospheric Pressure (MSL & Surface)",
                icon = Icons.Rounded.Compress,
                trailing = {
                    StatusPill(
                        text = "$pTrend (${String.format(Locale.US, "%+.1f", pDelta)} hPa/3h)",
                        containerColor = LavenderMist.copy(alpha = 0.18f),
                        contentColor = LavenderMist
                    )
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = MeteorologyCalculator.formatPressure(c.pressureMslHpa, preferences.pressureUnit),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Surface: ${MeteorologyCalculator.formatPressure(c.surfacePressureHpa, preferences.pressureUnit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(modifier = Modifier.width(170.dp)) {
                    MiniSparklineChart(values = next24Pressures, lineColor = LavenderMist)
                }
            }
        }
    }
}

@Composable
private fun AtmosphericMetricsGrid(
    bundle: WeatherBundle,
    preferences: UserPreferences
) {
    val c = bundle.current
    val h0 = bundle.hourly.firstOrNull()
    val (uvCat, uvAdvice) = MeteorologyCalculator.uvCategory(c.uvIndex)
    val peakUvHour = bundle.hourly.take(24).maxByOrNull { it.uvIndex ?: 0.0 }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Humidity & Dew Point
            GlassCard(modifier = Modifier.weight(1f), contentPadding = 14.dp) {
                GlassSectionHeader(title = "Humidity", icon = Icons.Rounded.WaterDrop)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${c.relativeHumidity}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Dew Point ${MeteorologyCalculator.formatTemp(c.dewPointC, preferences.tempUnit, true)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AuroraCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = MeteorologyCalculator.dewPointComfort(c.dewPointC),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // UV Index
            GlassCard(modifier = Modifier.weight(1f), contentPadding = 14.dp) {
                GlassSectionHeader(title = "UV Index", icon = Icons.Rounded.WbSunny)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = c.uvIndex?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A",
                    style = MaterialTheme.typography.headlineMedium,
                    color = SolarAmber
                )
                Text(
                    text = "$uvCat · Peak ${peakUvHour?.let { MeteorologyCalculator.formatHourLabel(it.timeIso, preferences.use24HourTime) } ?: "—"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uvAdvice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Visibility & Fog
            GlassCard(modifier = Modifier.weight(1f), contentPadding = 14.dp) {
                GlassSectionHeader(title = "Visibility", icon = Icons.Rounded.Visibility)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = c.visibilityMeters?.let { String.format(Locale.US, "%.1f km", it / 1000.0) } ?: "N/A",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = MeteorologyCalculator.visibilityLabel(c.visibilityMeters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "CAPE: ${c.capeJKg?.toInt() ?: 0} J/kg",
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroraCyan
                )
            }

            // Multi-layer Cloud Cover & Solar Radiation
            GlassCard(modifier = Modifier.weight(1f), contentPadding = 14.dp) {
                GlassSectionHeader(title = "Cloud Layers", icon = Icons.Rounded.CloudQueue)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${c.cloudCoverPct}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "L ${h0?.cloudCoverLowPct ?: "—"}% · M ${h0?.cloudCoverMidPct ?: "—"}% · H ${h0?.cloudCoverHighPct ?: "—"}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Solar: ${c.shortwaveRadiationWm2?.toInt() ?: 0} W/m²",
                    style = MaterialTheme.typography.labelSmall,
                    color = SolarAmber
                )
            }
        }
    }
}

@Composable
private fun AirQualityModule(
    bundle: WeatherBundle,
    preferences: UserPreferences
) {
    val aq = bundle.airQuality ?: return
    val aqiValue = if (preferences.aqiStandard == AqiStandard.INDIA_CPCB) aq.indiaCpcbAqi else aq.usAqi
    val (category, healthNote) = MeteorologyCalculator.aqiCategory(aqiValue, preferences.aqiStandard)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        GlassSectionHeader(
            title = "Air Quality Index",
            icon = Icons.Rounded.Air,
            subtitle = "${preferences.aqiStandard.agency} · Obs ${MeteorologyCalculator.formatLocalTime(aq.observationTimeIso, preferences.use24HourTime)}",
            trailing = {
                StatusPill(
                    text = category,
                    containerColor = AuroraCyan.copy(alpha = 0.18f),
                    contentColor = AuroraCyan
                )
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = aqiValue?.toString() ?: "—",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = preferences.aqiStandard.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = "Dominant: ${aq.dominantPollutant}",
                style = MaterialTheme.typography.labelMedium,
                color = SolarAmber,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        AqiSpectrumBar(aqiValue = aqiValue, standard = preferences.aqiStandard)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = healthNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 6-pollutant telemetry breakdown
        val pollutants = listOf(
            "PM2.5" to (aq.pm25?.let { String.format(Locale.US, "%.1f", it) } ?: "—"),
            "PM10" to (aq.pm10?.let { String.format(Locale.US, "%.1f", it) } ?: "—"),
            "NO₂" to (aq.no2?.let { String.format(Locale.US, "%.1f", it) } ?: "—"),
            "O₃" to (aq.o3?.let { String.format(Locale.US, "%.1f", it) } ?: "—"),
            "SO₂" to (aq.so2?.let { String.format(Locale.US, "%.1f", it) } ?: "—"),
            "CO" to (aq.co?.let { String.format(Locale.US, "%.0f", it) } ?: "—")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            pollutants.forEach { (label, v) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Units: µg/m³ · Source: ${aq.sourceName}" +
                if (aq.grassPollen != null || aq.birchPollen != null) " · Grass Pollen: ${aq.grassPollen?.toInt() ?: 0} gr/m³" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
        )
    }
}

@Composable
private fun AstronomyModule(
    bundle: WeatherBundle,
    use24Hour: Boolean
) {
    val astro = bundle.astronomy
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        GlassSectionHeader(
            title = "Sun, Golden Hour & Lunar Cycle",
            icon = Icons.Rounded.WbTwilight,
            subtitle = "Astronomical calculation for ${bundle.location.name}"
        )
        Spacer(modifier = Modifier.height(10.dp))
        SunAndMoonArcView(astronomy = astro, use24Hour = use24Hour)
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("GOLDEN HOUR", style = MaterialTheme.typography.labelSmall, color = SolarAmber)
                Text("AM ${astro.goldenHourMorning}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                Text("PM ${astro.goldenHourEvening}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.NightsStay, contentDescription = null, tint = LavenderMist, modifier = Modifier.size(14.dp))
                    Text("LUNAR PHASE", style = MaterialTheme.typography.labelSmall, color = LavenderMist)
                }
                Text(astro.moonPhaseName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text("${astro.moonIlluminationPct}% lit · Day ${astro.moonAgeDays}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MultiSourceProvenanceCard(
    bundle: WeatherBundle,
    preferences: UserPreferences
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        GlassSectionHeader(
            title = "WeatherNext 3 & Multi-Model Reconciliation",
            icon = Icons.Rounded.Hub,
            subtitle = "Primary vs supplementary meteorological engines"
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Ensemble confidence block (only displayed when real ensemble members are returned)
        bundle.ensemble?.let { ens ->
            Surface(
                color = AuroraCyan.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Verified, contentDescription = null, tint = AuroraCyan, modifier = Modifier.size(16.dp))
                        Text(
                            text = "${ens.modelName} (${ens.memberCount} Members)",
                            style = MaterialTheme.typography.labelMedium,
                            color = AuroraCyan
                        )
                    }
                    Text(
                        text = ens.confidenceLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "24h Envelope: ${MeteorologyCalculator.formatTemp(ens.next24hTempMinC, preferences.tempUnit)} to ${MeteorologyCalculator.formatTemp(ens.next24hTempMaxC, preferences.tempUnit)} · P(Rain ≥0.5mm): ${ens.precipProbabilityDerivedPct}% · P(T>35°C): ${ens.probExceed35CPct}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Significant Disagreements
        if (bundle.modelDisagreements.isNotEmpty()) {
            Surface(
                color = SolarAmber.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "SIGNIFICANT MODEL DISAGREEMENTS DETECTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = SolarAmber,
                        fontWeight = FontWeight.Bold
                    )
                    bundle.modelDisagreements.forEach { flag ->
                        Text("• $flag", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Model comparison rows
        bundle.modelComparisons.forEach { m ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = m.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = m.resolutionDesc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(
                        text = when {
                            m.available -> "LIVE"
                            m.modelId == "weathernext_3" -> "KEY / PROXY"
                            else -> "OFFLINE"
                        },
                        containerColor = if (m.available) EmeraldClear.copy(alpha = 0.18f) else SolarAmber.copy(alpha = 0.18f),
                        contentColor = if (m.available) EmeraldClear else SolarAmber
                    )
                }
                if (m.available) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "24h Range: ${m.next24hMinC?.let { MeteorologyCalculator.formatTemp(it, preferences.tempUnit) } ?: "—"} – ${m.next24hMaxC?.let { MeteorologyCalculator.formatTemp(it, preferences.tempUnit) } ?: "—"}  ·  Rain: ${m.next24hPrecipMm?.let { MeteorologyCalculator.formatPrecipitation(it, preferences.precipUnit) } ?: "—"}  ·  Max Wind: ${m.maxWindKmh?.let { MeteorologyCalculator.formatWind(it, preferences.windUnit) } ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AuroraCyan
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = m.statusNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = bundle.spatialResolutionNote,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
