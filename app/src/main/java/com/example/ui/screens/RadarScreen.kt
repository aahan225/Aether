package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.remote.WeatherApiProvider
import com.example.domain.engine.MeteorologyCalculator
import com.example.domain.model.RadarState
import com.example.domain.model.SavedLocation
import com.example.domain.model.UserPreferences
import com.example.domain.model.WeatherBundle
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldClear
import com.example.ui.theme.SolarAmber
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

private enum class RadarLayer(val label: String, val isObserved: Boolean, val description: String) {
    OBSERVED_RADAR("Rain Radar", true, "Live Observed Precipitation Radar (RainViewer · Past 2h)"),
    PRECIP_FORECAST("Precip Forecast", false, "Model Hourly Precipitation Projection Overlay"),
    WIND_FIELD("Wind Layer", false, "Model 10m Wind Vector Field"),
    TEMP_CONTOUR("Temperature", false, "Model 2m Thermal Field Overlay"),
    CLOUD_COVER("Cloud Cover", false, "Model Total Cloud Fraction Overlay")
}

private enum class RadarBasemap(
    val label: String,
    val attribution: String,
    val tileUrl: (z: Int, x: Int, y: Int, googleSession: String?, googleKey: String) -> String
) {
    OPEN_STREET_MAP(
        label = "OpenStreetMap",
        attribution = "© OpenStreetMap contributors · RainViewer",
        tileUrl = { z, x, y, _, _ -> "https://tile.openstreetmap.org/$z/$x/$y.png" }
    ),
    GOOGLE_MAPS(
        label = "Google Maps",
        attribution = "© Google Maps · RainViewer",
        tileUrl = { z, x, y, session, key ->
            if (!session.isNullOrBlank() && key.isNotBlank()) {
                "https://tile.googleapis.com/v1/2dtiles/$z/$x/$y?session=$session&key=$key"
            } else {
                "https://tile.openstreetmap.org/$z/$x/$y.png"
            }
        }
    ),
    CARTO_DARK(
        label = "Dark Radar",
        attribution = "© OpenStreetMap · © CARTO · RainViewer",
        tileUrl = { z, x, y, _, _ -> "https://a.basemaps.cartocdn.com/dark_all/$z/$x/$y.png" }
    ),
    CARTO_VOYAGER(
        label = "Street Light",
        attribution = "© OpenStreetMap · © CARTO · RainViewer",
        tileUrl = { z, x, y, _, _ -> "https://a.basemaps.cartocdn.com/rastertiles/voyager/$z/$x/$y.png" }
    )
}

private fun latLonToWorldTile(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
    val n = 2.0.pow(zoom)
    val clampedLat = lat.coerceIn(-85.0, 85.0)
    val latRad = Math.toRadians(clampedLat)
    val x = (lon + 180.0) / 360.0 * n
    val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
    return x to y
}

private fun worldTileToLatLon(x: Double, y: Double, zoom: Int): Pair<Double, Double> {
    val n = 2.0.pow(zoom)
    val lon = x / n * 360.0 - 180.0
    val latRad = atan(sinh(PI * (1.0 - 2.0 * y / n)))
    val lat = Math.toDegrees(latRad).coerceIn(-85.0, 85.0)
    return lat to lon
}

@Composable
fun RadarScreen(
    activeBundle: WeatherBundle?,
    savedLocations: List<SavedLocation>,
    radarState: RadarState,
    preferences: UserPreferences,
    onRefreshRadar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialLat = activeBundle?.location?.latitude ?: 22.5726
    val initialLon = activeBundle?.location?.longitude ?: 88.3639

    var centerLat by remember(initialLat, initialLon) { mutableDoubleStateOf(initialLat) }
    var centerLon by remember(initialLat, initialLon) { mutableDoubleStateOf(initialLon) }
    // RainViewer public tier caps max zoom at 7
    var zoom by remember { mutableIntStateOf(5) }
    var selectedLayer by remember { mutableStateOf(RadarLayer.OBSERVED_RADAR) }
    var googleTilesSession by remember { mutableStateOf<String?>(null) }
    val googleApiKey = remember { WeatherApiProvider.resolveGoogleApiKey() }
    var selectedBasemap by remember { mutableStateOf(RadarBasemap.OPEN_STREET_MAP) }

    LaunchedEffect(googleApiKey) {
        if (googleApiKey.isNotBlank()) {
            val session = WeatherApiProvider.createGoogleMapTilesSession(mapType = "roadmap")
            if (!session.isNullOrBlank()) {
                googleTilesSession = session
                selectedBasemap = RadarBasemap.GOOGLE_MAPS
            }
        }
    }

    var frameIdx by remember(radarState.frames.size) {
        mutableIntStateOf((radarState.frames.size - 1).coerceAtLeast(0))
    }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, radarState.frames.size, selectedLayer) {
        if (isPlaying && selectedLayer == RadarLayer.OBSERVED_RADAR && radarState.frames.size > 1) {
            while (isPlaying) {
                delay(if (preferences.reducedMotion) 1400L else 680L)
                frameIdx = (frameIdx + 1) % radarState.frames.size
            }
        }
    }

    val activeFrame = radarState.frames.getOrNull(frameIdx)
    val context = LocalContext.current
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0A1424))) {
        // FULL-SCREEN INTERACTIVE OPENSTREETMAP / CARTO SLIPPY TILE MAP + LIVE RAINVIEWER RADAR OVERLAY
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zoom) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        val tilePx = with(density) { 256.dp.toPx() }
                        val (cx, cy) = latLonToWorldTile(centerLat, centerLon, zoom)
                        val newX = cx - (pan.x / tilePx)
                        val newY = cy - (pan.y / tilePx)
                        val (nLat, nLon) = worldTileToLatLon(newX, newY, zoom)
                        centerLat = nLat
                        centerLon = ((nLon + 540.0) % 360.0) - 180.0
                        if (zoomChange > 1.18f && zoom < 7) zoom += 1
                        else if (zoomChange < 0.84f && zoom > 2) zoom -= 1
                    }
                }
        ) {
            val mapW = constraints.maxWidth.toFloat()
            val mapH = constraints.maxHeight.toFloat()
            val tileSizeDp = 256.dp
            val tileSizePx = with(density) { tileSizeDp.toPx() }

            val (centerTileX, centerTileY) = latLonToWorldTile(centerLat, centerLon, zoom)
            val maxTileIndex = (1 shl zoom) - 1

            val centerIntX = floor(centerTileX).toInt()
            val centerIntY = floor(centerTileY).toInt()
            val tilesHalfX = (mapW / tileSizePx / 2f).roundToInt() + 1
            val tilesHalfY = (mapH / tileSizePx / 2f).roundToInt() + 1

            // Render visible Slippy Map tiles (OpenStreetMap / CartoDB basemap + real RainViewer radar tile)
            for (dy in -tilesHalfY..tilesHalfY) {
                for (dx in -tilesHalfX..tilesHalfX) {
                    val ty = centerIntY + dy
                    if (ty < 0 || ty > maxTileIndex) continue
                    val txRaw = centerIntX + dx
                    val tx = ((txRaw % (maxTileIndex + 1)) + (maxTileIndex + 1)) % (maxTileIndex + 1)

                    val offsetX = (mapW / 2f + (txRaw - centerTileX) * tileSizePx).roundToInt()
                    val offsetY = (mapH / 2f + (ty - centerTileY) * tileSizePx).roundToInt()

                    val baseUrl = selectedBasemap.tileUrl(zoom, tx, ty, googleTilesSession, googleApiKey)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX, offsetY) }
                            .size(tileSizeDp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(baseUrl)
                                .addHeader("User-Agent", "AetherWeather/1.0 (Android; OpenStreetMap Radar)")
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (selectedLayer == RadarLayer.OBSERVED_RADAR && activeFrame != null) {
                            val radarTileUrl = "${radarState.host}${activeFrame.path}/256/$zoom/$tx/$ty/2/1_1.png"
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(radarTileUrl)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = "Observed precipitation radar tile",
                                contentScale = ContentScale.FillBounds,
                                alpha = 0.78f,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Supplementary Model Telemetry Layers (Explicitly labeled as Model Field, never fake radar!)
            if (selectedLayer != RadarLayer.OBSERVED_RADAR && activeBundle != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    when (selectedLayer) {
                        RadarLayer.WIND_FIELD -> {
                            val dirRad = Math.toRadians((activeBundle.current.windDirectionDeg - 90).toDouble())
                            val speedNorm = (activeBundle.current.windSpeedKmh / 40.0).coerceIn(0.3, 1.5).toFloat()
                            val spacing = 92f
                            var y = 60f
                            while (y < size.height - 60f) {
                                var x = 60f
                                while (x < size.width - 60f) {
                                    val len = 24f * speedNorm
                                    val ex = x + len * cos(dirRad).toFloat()
                                    val ey = y + len * kotlin.math.sin(dirRad).toFloat()
                                    drawLine(
                                        color = AuroraCyan.copy(alpha = 0.55f),
                                        start = Offset(x, y),
                                        end = Offset(ex, ey),
                                        strokeWidth = 3f,
                                        cap = StrokeCap.Round
                                    )
                                    drawCircle(color = AuroraCyan.copy(alpha = 0.75f), radius = 3.5f, center = Offset(ex, ey))
                                    x += spacing
                                }
                                y += spacing
                            }
                        }
                        RadarLayer.TEMP_CONTOUR -> {
                            val tempColor = when {
                                activeBundle.current.temperatureC >= 32.0 -> Color(0x55EF4444)
                                activeBundle.current.temperatureC >= 22.0 -> Color(0x55F59E0B)
                                activeBundle.current.temperatureC >= 12.0 -> Color(0x4438BDF8)
                                else -> Color(0x55818CF8)
                            }
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(tempColor, Color.Transparent),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = size.minDimension * 0.75f
                                )
                            )
                        }
                        RadarLayer.CLOUD_COVER -> {
                            val alpha = (activeBundle.current.cloudCoverPct / 100f * 0.45f).coerceIn(0.05f, 0.48f)
                            drawRect(color = Color(0xFFCBD5E1).copy(alpha = alpha))
                        }
                        RadarLayer.PRECIP_FORECAST -> {
                            val prob = (activeBundle.hourly.firstOrNull()?.precipitationProbability ?: 0) / 100f
                            val alpha = (prob * 0.42f).coerceIn(0.06f, 0.42f)
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(AuroraCyan.copy(alpha = alpha), Color.Transparent),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = size.minDimension * 0.65f
                                )
                            )
                        }
                        RadarLayer.OBSERVED_RADAR -> Unit
                    }
                }
            }

            // Saved & Active Location Geographic Markers
            savedLocations.forEach { loc ->
                val (lx, ly) = latLonToWorldTile(loc.latitude, loc.longitude, zoom)
                val px = (mapW / 2f + (lx - centerTileX) * tileSizePx).roundToInt()
                val py = (mapH / 2f + (ly - centerTileY) * tileSizePx).roundToInt()
                val isSelected = loc.id == activeBundle?.location?.id
                if (px in -40..(mapW.toInt() + 40) && py in -40..(mapH.toInt() + 40)) {
                    Surface(
                        color = if (isSelected) AuroraCyan else Color(0xFF0E1D34).copy(alpha = 0.88f),
                        contentColor = if (isSelected) Color(0xFF06111F) else Color.White,
                        shape = CircleShape,
                        shadowElevation = 6.dp,
                        modifier = Modifier.offset { IntOffset(px - 36, py - 16) }
                    ) {
                        Text(
                            text = loc.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // TOP OVERLAY: LAYER SELECTOR & PROVENANCE BADGE
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassCard(cornerRadius = 20.dp, contentPadding = 12.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Layers, contentDescription = null, tint = AuroraCyan, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Geospatial Weather Radar",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                    }
                    StatusPill(
                        text = if (selectedLayer.isObserved) "OBSERVED RADAR" else "MODEL LAYER",
                        containerColor = if (selectedLayer.isObserved) EmeraldClear.copy(alpha = 0.22f) else SolarAmber.copy(alpha = 0.22f),
                        contentColor = if (selectedLayer.isObserved) EmeraldClear else SolarAmber
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = selectedLayer.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RadarLayer.entries) { layer ->
                        FilterChip(
                            selected = selectedLayer == layer,
                            onClick = {
                                selectedLayer = layer
                                if (!layer.isObserved) isPlaying = false
                            },
                            label = { Text(layer.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AuroraCyan.copy(alpha = 0.25f),
                                selectedLabelColor = AuroraCyan,
                                labelColor = Color.White
                            ),
                            modifier = Modifier.testTag("radar_layer_${layer.name.lowercase()}")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val availableBasemaps = remember(googleTilesSession) {
                    RadarBasemap.entries.filter {
                        it != RadarBasemap.GOOGLE_MAPS || !googleTilesSession.isNullOrBlank()
                    }
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        Text(
                            text = "MAP:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    items(availableBasemaps) { mapStyle ->
                        FilterChip(
                            selected = selectedBasemap == mapStyle,
                            onClick = { selectedBasemap = mapStyle },
                            label = { Text(mapStyle.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SolarAmber.copy(alpha = 0.24f),
                                selectedLabelColor = SolarAmber,
                                labelColor = Color.White
                            ),
                            modifier = Modifier.testTag("radar_basemap_${mapStyle.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // RIGHT FLOATING ZOOM & RECENTER CONTROLS
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = Color(0xCC0D1B30),
                shape = CircleShape
            ) {
                IconButton(
                    onClick = { if (zoom < 7) zoom += 1 },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("radar_zoom_in")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Zoom in radar", tint = Color.White)
                }
            }
            Surface(
                color = Color(0xCC0D1B30),
                shape = CircleShape
            ) {
                IconButton(
                    onClick = { if (zoom > 2) zoom -= 1 },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("radar_zoom_out")
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Zoom out radar", tint = Color.White)
                }
            }
            Surface(
                color = Color(0xCC0D1B30),
                shape = CircleShape
            ) {
                IconButton(
                    onClick = {
                        centerLat = initialLat
                        centerLon = initialLon
                        zoom = 5
                    },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("radar_recenter")
                ) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = "Recenter map on active location", tint = AuroraCyan)
                }
            }
        }

        // BOTTOM TIMELINE SCRUBBER & PLAY/PAUSE
        GlassCard(
            cornerRadius = 22.dp,
            contentPadding = 14.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            if (radarState.error != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = radarState.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = CoralAlert,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRefreshRadar) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reload radar", tint = Color.White)
                    }
                }
            } else if (selectedLayer == RadarLayer.OBSERVED_RADAR && radarState.frames.isNotEmpty()) {
                val frameStamp = activeFrame?.let {
                    MeteorologyCalculator.formatEpochInOffset(
                        it.timestampSeconds * 1000L,
                        activeBundle?.utcOffsetSeconds ?: 0,
                        preferences.use24HourTime
                    )
                } ?: "—"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = AuroraCyan,
                        contentColor = Color(0xFF06111F),
                        shape = CircleShape
                    ) {
                        IconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("radar_play_pause")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause radar loop" else "Play radar loop"
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Observed Frame: $frameStamp",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Zoom $zoom/7 · ${frameIdx + 1}/${radarState.frames.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AuroraCyan
                            )
                        }
                        Slider(
                            value = frameIdx.toFloat(),
                            onValueChange = {
                                isPlaying = false
                                frameIdx = it.roundToInt().coerceIn(0, radarState.frames.lastIndex)
                            },
                            valueRange = 0f..(radarState.frames.lastIndex.coerceAtLeast(1)).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = AuroraCyan,
                                activeTrackColor = AuroraCyan
                            ),
                            modifier = Modifier.testTag("radar_timeline_slider")
                        )
                    }
                }
            } else {
                // Model projection metadata readout
                val c = activeBundle?.current
                Text(
                    text = if (c != null) {
                        String.format(
                            Locale.US,
                            "Model Grid Readout · Temp %s · Wind %s %s · Cloud %d%% · Precip %s",
                            MeteorologyCalculator.formatTemp(c.temperatureC, preferences.tempUnit, true),
                            MeteorologyCalculator.formatWind(c.windSpeedKmh, preferences.windUnit),
                            MeteorologyCalculator.windCardinal(c.windDirectionDeg),
                            c.cloudCoverPct,
                            MeteorologyCalculator.formatPrecipitation(c.precipitationMm, preferences.precipUnit)
                        )
                    } else "Loading live meteorological grid…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = selectedBasemap.attribution,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}
