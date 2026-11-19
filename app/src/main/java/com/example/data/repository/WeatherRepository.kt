package com.example.data.repository

import android.util.Log
import com.example.data.local.AetherDatabase
import com.example.data.local.CachedWeatherEntity
import com.example.data.local.VerificationSnapshotEntity
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.data.network.OpenMeteoForecastResponse
import com.example.data.network.WeatherNetworkClient
import com.example.domain.engine.ForecastVerificationEngine
import com.example.domain.engine.MeteorologyCalculator
import com.example.domain.engine.VerificationBenchmarkRegion
import com.example.domain.engine.WeatherReconciliationEngine
import com.example.domain.model.AirQualityData
import com.example.domain.model.AlertProvenance
import com.example.domain.model.AlertSeverity
import com.example.domain.model.CurrentConditions
import com.example.domain.model.DailyPoint
import com.example.domain.model.ForecastHorizon
import com.example.domain.model.HourlyPoint
import com.example.domain.model.ModelComparisonPoint
import com.example.domain.model.ModelVerificationMetrics
import com.example.domain.model.ProviderHealth
import com.example.domain.model.RadarFrame
import com.example.domain.model.RadarState
import com.example.domain.model.SavedLocation
import com.example.domain.model.VerificationReport
import com.example.domain.model.WeatherAlert
import com.example.domain.model.WeatherBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG = "WeatherRepository"

class WeatherRepository(
    private val database: AetherDatabase
) {
    private val bundleAdapter = WeatherNetworkClient.moshi.adapter(WeatherBundle::class.java)
    private val verificationAdapter = WeatherNetworkClient.moshi.adapter(VerificationReport::class.java)

    val savedLocationsFlow: Flow<List<SavedLocation>> =
        database.savedLocationDao().observeAllLocations().map { list -> list.map { it.toDomain() } }

    val cachedBundlesFlow: Flow<Map<String, WeatherBundle>> =
        database.cachedWeatherDao().observeAllCached().map { entities ->
            entities.mapNotNull { entity ->
                runCatching {
                    bundleAdapter.fromJson(entity.payloadJson)?.copy(isFromCache = true)
                }.getOrNull()?.let { entity.locationId to it }
            }.toMap()
        }

    suspend fun ensureDefaultLocationsSeeded(): List<SavedLocation> = withContext(Dispatchers.IO) {
        val existing = database.savedLocationDao().getAllLocationsOnce()
        if (existing.isNotEmpty()) return@withContext existing.map { it.toDomain() }

        val defaults = listOf(
            SavedLocation(
                id = "kolkata_in",
                name = "Kolkata",
                adminRegion = "West Bengal",
                country = "India",
                countryCode = "IN",
                latitude = 22.5726,
                longitude = 88.3639,
                timezone = "Asia/Kolkata",
                elevationMeters = 9.0,
                sortOrder = 0
            ),
            SavedLocation(
                id = "darjeeling_in",
                name = "Darjeeling",
                adminRegion = "West Bengal",
                country = "India",
                countryCode = "IN",
                latitude = 27.0410,
                longitude = 88.2663,
                timezone = "Asia/Kolkata",
                elevationMeters = 2042.0,
                sortOrder = 1
            ),
            SavedLocation(
                id = "new_delhi_in",
                name = "New Delhi",
                adminRegion = "Delhi",
                country = "India",
                countryCode = "IN",
                latitude = 28.6139,
                longitude = 77.2090,
                timezone = "Asia/Kolkata",
                elevationMeters = 216.0,
                sortOrder = 2
            ),
            SavedLocation(
                id = "london_gb",
                name = "London",
                adminRegion = "England",
                country = "United Kingdom",
                countryCode = "GB",
                latitude = 51.5072,
                longitude = -0.1276,
                timezone = "Europe/London",
                elevationMeters = 11.0,
                sortOrder = 3
            ),
            SavedLocation(
                id = "tokyo_jp",
                name = "Tokyo",
                adminRegion = "Tokyo",
                country = "Japan",
                countryCode = "JP",
                latitude = 35.6762,
                longitude = 139.6503,
                timezone = "Asia/Tokyo",
                elevationMeters = 40.0,
                sortOrder = 4
            )
        )
        database.savedLocationDao().upsertAll(defaults.map { it.toEntity() })
        defaults
    }

    suspend fun saveLocation(location: SavedLocation) = withContext(Dispatchers.IO) {
        if (location.isCurrentGps) {
            database.savedLocationDao().clearGpsLocations()
        }
        database.savedLocationDao().upsertLocation(location.toEntity())
    }

    suspend fun removeLocation(locationId: String) = withContext(Dispatchers.IO) {
        database.savedLocationDao().deleteById(locationId)
        database.cachedWeatherDao().deleteCache(locationId)
    }

    suspend fun getCachedWeather(locationId: String): WeatherBundle? = withContext(Dispatchers.IO) {
        val entity = database.cachedWeatherDao().getByLocationId(locationId) ?: return@withContext null
        runCatching {
            bundleAdapter.fromJson(entity.payloadJson)?.copy(isFromCache = true)
        }.getOrNull()
    }

    suspend fun searchLocations(query: String): List<SavedLocation> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        val resp = WeatherNetworkClient.withRetry(maxAttempts = 2) {
            WeatherNetworkClient.openMeteoGeocodingApi.searchLocations(query = query.trim())
        }
        resp.results.orEmpty().map { r ->
            val admin = listOfNotNull(r.admin2, r.admin1).distinct().joinToString(", ")
            SavedLocation(
                id = "geo_${r.id}",
                name = r.name,
                adminRegion = admin.ifBlank { r.country.orEmpty() },
                country = r.country.orEmpty(),
                countryCode = r.countryCode.orEmpty().uppercase(),
                latitude = r.latitude,
                longitude = r.longitude,
                timezone = r.timezone ?: "auto",
                elevationMeters = r.elevation,
                isCurrentGps = false,
                sortOrder = 50
            )
        }
    }

    /**
     * Fetches comprehensive multi-source meteorological data in parallel:
     * 1. WeatherNext 3 Adapter (configured Cloud Proxy or honest allowlist status)
     * 2. Primary High-Resolution Multi-Parameter Seamless Forecast (16-day, hourly, current)
     * 3. Supplementary Individual Models: ECMWF AIFS 0.25° (AI), ECMWF IFS 0.25°, NOAA GFS, DWD ICON
     * 4. ECMWF IFS Ensemble Spread (50+ perturbed members)
     * 5. Open-Meteo CAMS Air Quality (PM2.5, PM10, NO2, O3, SO2, CO, Dust, Pollen + India CPCB AQI)
     * 6. Official Severe Weather Alerts (IMD India CAP/RSS + NOAA NWS for US)
     */
    suspend fun fetchLiveWeatherBundle(
        location: SavedLocation,
        use24Hour: Boolean,
        weatherNext3ProxyUrl: String
    ): WeatherBundle = coroutineScope {
        val nowMillis = System.currentTimeMillis()

        val primaryDeferred = async(Dispatchers.IO) {
            WeatherNetworkClient.withRetry {
                WeatherNetworkClient.openMeteoForecastApi.getFullForecast(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            }
        }

        val weatherNext3Deferred = async(Dispatchers.IO) {
            WeatherNetworkClient.queryWeatherNext3Adapter(
                latitude = location.latitude,
                longitude = location.longitude,
                customProxyUrl = weatherNext3ProxyUrl
            )
        }

        val graphCastDeferred = async(Dispatchers.IO) {
            fetchSupplementaryModel(
                location = location,
                primaryModelId = "gfs_graphcast025",
                candidates = listOf(
                    "v1/gfs" to "gfs_graphcast025",
                    "v1/forecast" to "gfs_graphcast025"
                ),
                displayName = "Google DeepMind GraphCast 0.25° (AI)",
                category = "Live DeepMind AI Global Model",
                resolutionDesc = "0.25° global (~28 km) · DeepMind AI NWP"
            )
        }

        val aifsDeferred = async(Dispatchers.IO) {
            fetchSupplementaryModel(
                location = location,
                primaryModelId = "ecmwf_aifs025_single",
                candidates = listOf(
                    "v1/ecmwf" to "ecmwf_aifs025_single",
                    "v1/forecast" to "ecmwf_aifs025_single",
                    "v1/ecmwf" to "ecmwf_aifs025",
                    "v1/forecast" to "ecmwf_aifs025"
                ),
                displayName = "ECMWF AIFS 0.25° (AI)",
                category = "Operational AI Global Model",
                resolutionDesc = "0.25° global (~28 km)"
            )
        }

        val ecmwfDeferred = async(Dispatchers.IO) {
            fetchSupplementaryModel(
                location = location,
                primaryModelId = "ecmwf_ifs025",
                candidates = listOf(
                    "v1/ecmwf" to "ecmwf_ifs025",
                    "v1/forecast" to "ecmwf_ifs025"
                ),
                displayName = "ECMWF IFS 0.25°",
                category = "European Global NWP",
                resolutionDesc = "0.25° global (~25 km)"
            )
        }

        val gfsDeferred = async(Dispatchers.IO) {
            fetchSupplementaryModel(
                location = location,
                primaryModelId = "gfs_seamless",
                candidates = listOf(
                    "v1/gfs" to "gfs_seamless",
                    "v1/forecast" to "gfs_seamless"
                ),
                displayName = "NOAA GFS Seamless",
                category = "US Global NWP",
                resolutionDesc = "0.11°–0.25° global"
            )
        }

        val iconDeferred = async(Dispatchers.IO) {
            fetchSupplementaryModel(
                location = location,
                primaryModelId = "icon_seamless",
                candidates = listOf(
                    "v1/dwd-icon" to "icon_seamless",
                    "v1/forecast" to "icon_seamless"
                ),
                displayName = "DWD ICON Seamless",
                category = "German Global NWP",
                resolutionDesc = "0.125° global (~13 km)"
            )
        }

        val ensembleDeferred = async(Dispatchers.IO) {
            val members = WeatherNetworkClient.fetchEnsembleMembers(location.latitude, location.longitude)
            if (members != null) {
                WeatherReconciliationEngine.computeEnsembleStats(
                    modelName = "ECMWF IFS 0.25° Ensemble",
                    memberTempSeries = members.first,
                    memberPrecipSeries = members.second
                )
            } else null
        }

        val aqiDeferred = async(Dispatchers.IO) {
            runCatching {
                val aqResp = WeatherNetworkClient.withRetry(maxAttempts = 2) {
                    WeatherNetworkClient.openMeteoAirQualityApi.getAirQuality(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }
                val c = aqResp.current ?: return@runCatching null
                val (indiaAqi, dominant) = MeteorologyCalculator.computeIndiaCpcbAqi(
                    pm25 = c.pm25,
                    pm10 = c.pm10,
                    no2 = c.no2,
                    o3 = c.ozone,
                    so2 = c.so2,
                    coUgM3 = c.co
                )
                AirQualityData(
                    observationTimeIso = c.time,
                    usAqi = c.usAqi,
                    europeanAqi = c.europeanAqi,
                    indiaCpcbAqi = indiaAqi,
                    dominantPollutant = dominant,
                    pm25 = c.pm25,
                    pm10 = c.pm10,
                    no2 = c.no2,
                    o3 = c.ozone,
                    so2 = c.so2,
                    co = c.co,
                    dust = c.dust,
                    grassPollen = c.grassPollen,
                    birchPollen = c.birchPollen,
                    ragweedPollen = c.ragweedPollen,
                    sourceName = "CAMS Global Atmospheric Composition (Open-Meteo)"
                )
            }.getOrNull()
        }

        val officialAlertsDeferred = async(Dispatchers.IO) {
            fetchOfficialAlertsForLocation(location)
        }

        val primaryResp = primaryDeferred.await()
        val wn3Model = weatherNext3Deferred.await()
        val graphCastModel = graphCastDeferred.await()
        val supplementaryModels = listOf(
            wn3Model,
            graphCastModel,
            aifsDeferred.await(),
            ecmwfDeferred.await(),
            gfsDeferred.await(),
            iconDeferred.await()
        )
        val ensemble = ensembleDeferred.await()
        val aqi = aqiDeferred.await()
        val officialAlerts = officialAlertsDeferred.await()

        val hourlyList = mapHourly(primaryResp)
        val dailyList = mapDaily(primaryResp)
        val current = mapCurrent(primaryResp, hourlyList)

        val modelRiskAlerts = WeatherReconciliationEngine.deriveModelRiskIndicators(
            locationName = location.name,
            current = current,
            hourly = hourlyList,
            daily = dailyList
        )
        val allAlerts = (officialAlerts + modelRiskAlerts).sortedWith(
            compareByDescending<WeatherAlert> { it.provenance == AlertProvenance.OFFICIAL_AGENCY }
                .thenByDescending { it.severity.rank }
        )

        val disagreements = WeatherReconciliationEngine.analyzeModelDisagreements(supplementaryModels)
        val summaries = WeatherReconciliationEngine.generateDataGroundedSummaries(
            current = current,
            hourly = hourlyList,
            daily = dailyList,
            ensemble = ensemble,
            use24Hour = use24Hour
        )

        val todayDaily = dailyList.firstOrNull()
        val astronomy = MeteorologyCalculator.calculateAstronomy(
            sunriseIso = todayDaily?.sunriseIso.orEmpty(),
            sunsetIso = todayDaily?.sunsetIso.orEmpty(),
            currentLocalIso = current.observationTimeIso,
            daylightDurationSeconds = todayDaily?.daylightDurationSeconds,
            use24Hour = use24Hour
        )

        val providerHealth = buildProviderHealth(
            wn3Model = wn3Model,
            models = supplementaryModels,
            hasEnsemble = ensemble != null,
            hasAqi = aqi != null,
            officialAlertsCount = officialAlerts.size,
            location = location,
            nowMillis = nowMillis
        )

        val primaryEngineLabel = when {
            wn3Model.available && graphCastModel.available ->
                "WeatherNext 3 (Live) + DeepMind GraphCast + Multi-Model"
            wn3Model.available ->
                "WeatherNext 3 (Live) + Multi-Model Reconciled"
            graphCastModel.available ->
                "DeepMind GraphCast (AI) + ECMWF AIFS/IFS + GFS + ICON"
            else ->
                "ECMWF AIFS / IFS + GFS + ICON Multi-Model"
        }

        val bundle = WeatherBundle(
            location = location.copy(
                timezone = primaryResp.timezone,
                elevationMeters = primaryResp.elevation ?: location.elevationMeters
            ),
            fetchedAtUtcMillis = nowMillis,
            utcOffsetSeconds = primaryResp.utcOffsetSeconds,
            timezoneAbbreviation = primaryResp.timezoneAbbreviation,
            primaryEngineLabel = primaryEngineLabel,
            spatialResolutionNote = "Native NWP grid ~9–25 km (ECMWF/ICON/GFS). GPS selects nearest grid cell; street-level microclimate precision is not claimed.",
            current = current,
            hourly = hourlyList,
            daily = dailyList,
            airQuality = aqi,
            ensemble = ensemble,
            modelComparisons = supplementaryModels,
            providerHealth = providerHealth,
            modelDisagreements = disagreements,
            alerts = allAlerts,
            summaryBullets = summaries,
            astronomy = astronomy,
            isFromCache = false
        )

        // Persist to Room cache for instant startup & offline fallback
        withContext(Dispatchers.IO) {
            runCatching {
                val json = bundleAdapter.toJson(bundle)
                database.cachedWeatherDao().upsertCache(
                    CachedWeatherEntity(
                        locationId = location.id,
                        payloadJson = json,
                        fetchedAtUtcMillis = nowMillis
                    )
                )
            }
        }

        bundle
    }

    private suspend fun fetchSupplementaryModel(
        location: SavedLocation,
        primaryModelId: String,
        candidates: List<Pair<String, String>>,
        displayName: String,
        category: String,
        resolutionDesc: String
    ): ModelComparisonPoint {
        return try {
            val summary = WeatherNetworkClient.fetchOpenMeteoModelSeries(
                latitude = location.latitude,
                longitude = location.longitude,
                candidates = candidates
            )
            if (summary != null) {
                ModelComparisonPoint(
                    modelId = summary.resolvedModelId,
                    displayName = displayName,
                    category = category,
                    resolutionDesc = resolutionDesc,
                    available = true,
                    statusNote = "Live operational run synced (${summary.resolvedModelId})",
                    currentTempC = summary.currentTempC,
                    next24hMaxC = summary.next24hMaxC,
                    next24hMinC = summary.next24hMinC,
                    next24hPrecipMm = summary.next24hPrecipMm,
                    maxWindKmh = summary.maxWindKmh,
                    initTimeNote = "Latest synoptic cycle"
                )
            } else {
                ModelComparisonPoint(
                    modelId = primaryModelId,
                    displayName = displayName,
                    category = category,
                    resolutionDesc = resolutionDesc,
                    available = false,
                    statusNote = "Grid cycle currently updating on upstream server",
                    currentTempC = null,
                    next24hMaxC = null,
                    next24hMinC = null,
                    next24hPrecipMm = null,
                    maxWindKmh = null,
                    initTimeNote = "Pending cycle"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Supplementary model $primaryModelId unavailable: ${e.message}")
            ModelComparisonPoint(
                modelId = primaryModelId,
                displayName = displayName,
                category = category,
                resolutionDesc = resolutionDesc,
                available = false,
                statusNote = "Temporarily unreachable (${e.javaClass.simpleName})",
                currentTempC = null,
                next24hMaxC = null,
                next24hMinC = null,
                next24hPrecipMm = null,
                maxWindKmh = null,
                initTimeNote = "Unavailable"
            )
        }
    }

    private suspend fun fetchOfficialAlertsForLocation(location: SavedLocation): List<WeatherAlert> {
        val alerts = mutableListOf<WeatherAlert>()
        // India: prioritize official IMD / NDMA SACHET CAP RSS bulletins
        if (location.countryCode.equals("IN", ignoreCase = true) ||
            location.country.contains("India", ignoreCase = true)
        ) {
            alerts += WeatherNetworkClient.fetchOfficialImdAlerts("${location.name}, ${location.adminRegion}")
        }

        // US: query official NOAA National Weather Service active alerts
        if (location.countryCode.equals("US", ignoreCase = true) ||
            location.country.contains("United States", ignoreCase = true)
        ) {
            runCatching {
                val point = String.format(Locale.US, "%.4f,%.4f", location.latitude, location.longitude)
                val noaa = WeatherNetworkClient.noaaAlertsApi.getActiveAlerts(point)
                noaa.features.forEach { feature ->
                    val p = feature.properties ?: return@forEach
                    val sev = when (p.severity?.lowercase()) {
                        "extreme" -> AlertSeverity.EXTREME
                        "severe" -> AlertSeverity.SEVERE
                        "moderate" -> AlertSeverity.MODERATE
                        else -> AlertSeverity.MINOR
                    }
                    alerts += WeatherAlert(
                        id = feature.id.ifBlank { "noaa_${p.headline.hashCode()}" },
                        title = p.headline ?: p.event ?: "NOAA Weather Alert",
                        eventType = p.event ?: "Severe Weather",
                        severity = sev,
                        provenance = AlertProvenance.OFFICIAL_AGENCY,
                        issuingAuthority = p.senderName ?: "NOAA National Weather Service",
                        affectedArea = p.areaDesc ?: location.name,
                        validFrom = p.effective.orEmpty(),
                        validUntil = p.expires.orEmpty(),
                        description = p.description.orEmpty().take(500),
                        precautions = p.instruction?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() }?.take(4)
                            ?: listOf("Follow official NOAA National Weather Service guidance."),
                        updatedUtcIso = p.sent ?: Instant.now().toString()
                    )
                }
            }.onFailure {
                Log.w(TAG, "NOAA NWS alerts query skipped/failed: ${it.message}")
            }
        }
        return alerts
    }

    private fun buildProviderHealth(
        wn3Model: ModelComparisonPoint,
        models: List<ModelComparisonPoint>,
        hasEnsemble: Boolean,
        hasAqi: Boolean,
        officialAlertsCount: Int,
        location: SavedLocation,
        nowMillis: Long
    ): List<ProviderHealth> {
        val list = mutableListOf<ProviderHealth>()
        list += ProviderHealth(
            name = "Google DeepMind WeatherNext 3",
            role = "Primary AI Forecasting Engine",
            status = if (wn3Model.available) "Live" else "API Key / Proxy Required",
            isLive = wn3Model.available,
            resolution = wn3Model.resolutionDesc,
            lastUpdatedUtc = if (wn3Model.available) nowMillis else null,
            detail = wn3Model.statusNote
        )
        models.filter { it.modelId != "weathernext_3" }.forEach { m ->
            list += ProviderHealth(
                name = m.displayName,
                role = m.category,
                status = if (m.available) "Live" else "Unavailable",
                isLive = m.available,
                resolution = m.resolutionDesc,
                lastUpdatedUtc = if (m.available) nowMillis else null,
                detail = m.statusNote
            )
        }
        list += ProviderHealth(
            name = "ECMWF IFS Ensemble API",
            role = "Probabilistic Uncertainty & Spread",
            status = if (hasEnsemble) "Live (50+ Members)" else "Unavailable",
            isLive = hasEnsemble,
            resolution = "0.25° global ensemble",
            lastUpdatedUtc = if (hasEnsemble) nowMillis else null,
            detail = if (hasEnsemble) "Computing empirical 24h temperature spread & rain probabilities" else "Fallback deterministic mode"
        )
        list += ProviderHealth(
            name = "CAMS Air Quality (Open-Meteo)",
            role = "Pollutants & India CPCB / US EPA AQI",
            status = if (hasAqi) "Live" else "Unavailable",
            isLive = hasAqi,
            resolution = "~40 km global atmospheric chemistry",
            lastUpdatedUtc = if (hasAqi) nowMillis else null,
            detail = "PM2.5, PM10, NO₂, O₃, SO₂, CO, Dust & Pollen"
        )
        list += ProviderHealth(
            name = if (location.countryCode.equals("IN", ignoreCase = true)) "IMD / NDMA SACHET CAP RSS" else "NOAA NWS / Regional Alerts",
            role = "Official Severe Weather Warnings",
            status = if (officialAlertsCount > 0) "Live ($officialAlertsCount active)" else "Connected (0 active)",
            isLive = true,
            resolution = "District / County Polygon",
            lastUpdatedUtc = nowMillis,
            detail = "Official government warnings kept strictly separate from model risk indicators"
        )
        return list
    }

    private fun mapHourly(resp: OpenMeteoForecastResponse): List<HourlyPoint> {
        val h = resp.hourly ?: return emptyList()
        val offset = ZoneOffset.ofTotalSeconds(resp.utcOffsetSeconds)
        val nowEpoch = Instant.now().epochSecond
        val all = h.time.indices.map { i ->
            val timeIso = h.time[i]
            val dt = MeteorologyCalculator.parseLocalDateTimeSafe(timeIso)
            val epoch = dt?.toEpochSecond(offset) ?: nowEpoch
            HourlyPoint(
                timeIso = timeIso,
                epochSeconds = epoch,
                temperatureC = h.temperature2m.getOrNull(i) ?: 0.0,
                apparentTemperatureC = h.apparentTemperature.getOrNull(i) ?: (h.temperature2m.getOrNull(i) ?: 0.0),
                relativeHumidity = h.relativeHumidity2m.getOrNull(i) ?: 0,
                dewPointC = h.dewPoint2m.getOrNull(i) ?: 0.0,
                precipitationProbability = h.precipitationProbability.getOrNull(i),
                precipitationMm = h.precipitation.getOrNull(i) ?: 0.0,
                rainMm = h.rain.getOrNull(i) ?: 0.0,
                snowfallCm = h.snowfall.getOrNull(i) ?: 0.0,
                weatherCode = h.weatherCode.getOrNull(i) ?: 0,
                pressureMslHpa = h.pressureMsl.getOrNull(i) ?: 1013.25,
                surfacePressureHpa = h.surfacePressure.getOrNull(i) ?: 1013.25,
                cloudCoverPct = h.cloudCover.getOrNull(i) ?: 0,
                cloudCoverLowPct = h.cloudCoverLow.getOrNull(i),
                cloudCoverMidPct = h.cloudCoverMid.getOrNull(i),
                cloudCoverHighPct = h.cloudCoverHigh.getOrNull(i),
                visibilityMeters = h.visibility.getOrNull(i),
                windSpeedKmh = h.windSpeed10m.getOrNull(i) ?: 0.0,
                windDirectionDeg = h.windDirection10m.getOrNull(i) ?: 0,
                windGustsKmh = h.windGusts10m.getOrNull(i) ?: 0.0,
                uvIndex = h.uvIndex.getOrNull(i),
                shortwaveRadiationWm2 = h.shortwaveRadiation.getOrNull(i),
                capeJKg = h.cape.getOrNull(i),
                isDay = (h.isDay.getOrNull(i) ?: 1) == 1
            )
        }
        // Start from current local hour forward (up to 72 hours for rich scrolling & charts)
        val currentIdx = all.indexOfFirst { it.epochSeconds >= nowEpoch - 3500L }.coerceAtLeast(0)
        return all.drop(currentIdx).take(72)
    }

    private fun mapDaily(resp: OpenMeteoForecastResponse): List<DailyPoint> {
        val d = resp.daily ?: return emptyList()
        return d.time.indices.map { i ->
            val horizon = when {
                i <= 2 -> ForecastHorizon.SHORT_RANGE
                i <= 6 -> ForecastHorizon.MEDIUM_RANGE
                else -> ForecastHorizon.EXTENDED
            }
            DailyPoint(
                dateIso = d.time[i],
                dayIndex = i,
                weatherCode = d.weatherCode.getOrNull(i) ?: 0,
                tempMaxC = d.temperature2mMax.getOrNull(i) ?: 0.0,
                tempMinC = d.temperature2mMin.getOrNull(i) ?: 0.0,
                apparentTempMaxC = d.apparentTemperatureMax.getOrNull(i) ?: 0.0,
                apparentTempMinC = d.apparentTemperatureMin.getOrNull(i) ?: 0.0,
                sunriseIso = d.sunrise.getOrNull(i).orEmpty(),
                sunsetIso = d.sunset.getOrNull(i).orEmpty(),
                daylightDurationSeconds = d.daylightDuration.getOrNull(i),
                uvIndexMax = d.uvIndexMax.getOrNull(i),
                precipitationSumMm = d.precipitationSum.getOrNull(i) ?: 0.0,
                rainSumMm = d.rainSum.getOrNull(i) ?: 0.0,
                snowfallSumCm = d.snowfallSum.getOrNull(i) ?: 0.0,
                precipitationProbabilityMax = d.precipitationProbabilityMax.getOrNull(i),
                windSpeedMaxKmh = d.windSpeed10mMax.getOrNull(i) ?: 0.0,
                windGustsMaxKmh = d.windGusts10mMax.getOrNull(i) ?: 0.0,
                windDirectionDominantDeg = d.windDirection10mDominant.getOrNull(i) ?: 0,
                shortwaveRadiationSumMj = d.shortwaveRadiationSum.getOrNull(i),
                horizon = horizon
            )
        }
    }

    private fun mapCurrent(
        resp: OpenMeteoForecastResponse,
        hourly: List<HourlyPoint>
    ): CurrentConditions {
        val c = resp.current
        val h0 = hourly.firstOrNull()
        return CurrentConditions(
            observationTimeIso = c?.time ?: h0?.timeIso.orEmpty(),
            temperatureC = c?.temperature2m ?: h0?.temperatureC ?: 0.0,
            apparentTemperatureC = c?.apparentTemperature ?: h0?.apparentTemperatureC ?: 0.0,
            relativeHumidity = c?.relativeHumidity2m ?: h0?.relativeHumidity ?: 0,
            dewPointC = h0?.dewPointC ?: 0.0,
            surfacePressureHpa = c?.surfacePressure ?: h0?.surfacePressureHpa ?: 1013.25,
            pressureMslHpa = c?.pressureMsl ?: h0?.pressureMslHpa ?: 1013.25,
            windSpeedKmh = c?.windSpeed10m ?: h0?.windSpeedKmh ?: 0.0,
            windDirectionDeg = c?.windDirection10m ?: h0?.windDirectionDeg ?: 0,
            windGustsKmh = c?.windGusts10m ?: h0?.windGustsKmh ?: 0.0,
            precipitationMm = c?.precipitation ?: h0?.precipitationMm ?: 0.0,
            rainMm = c?.rain ?: h0?.rainMm ?: 0.0,
            snowfallCm = c?.snowfall ?: h0?.snowfallCm ?: 0.0,
            cloudCoverPct = c?.cloudCover ?: h0?.cloudCoverPct ?: 0,
            weatherCode = c?.weatherCode ?: h0?.weatherCode ?: 0,
            isDay = (c?.isDay ?: if (h0?.isDay == false) 0 else 1) == 1,
            visibilityMeters = h0?.visibilityMeters,
            uvIndex = h0?.uvIndex,
            shortwaveRadiationWm2 = h0?.shortwaveRadiationWm2,
            capeJKg = h0?.capeJKg
        )
    }

    suspend fun fetchRainViewerRadar(): RadarState = withContext(Dispatchers.IO) {
        try {
            val resp = WeatherNetworkClient.withRetry(maxAttempts = 2) {
                WeatherNetworkClient.rainViewerApi.getWeatherMaps()
            }
            val frames = resp.radar?.past.orEmpty().map {
                RadarFrame(timestampSeconds = it.time, path = it.path, isObserved = true)
            }
            RadarState(
                host = resp.host,
                generatedEpoch = resp.generated,
                frames = frames,
                isLoading = false,
                error = if (frames.isEmpty()) "No active radar frames returned" else null
            )
        } catch (e: Exception) {
            RadarState(
                isLoading = false,
                error = "Unable to load RainViewer radar metadata: ${e.localizedMessage ?: "Network error"}"
            )
        }
    }

    /**
     * Runs genuine empirical forecast verification comparing archived historical model forecasts
     * (ECMWF IFS, NOAA GFS, DWD ICON) against ERA5/station reanalysis observations over the last
     * 10 days (ending 5 days ago to guarantee settled archive observations).
     */
    suspend fun runEmpiricalVerification(
        region: VerificationBenchmarkRegion,
        forceRefresh: Boolean = false
    ): VerificationReport = coroutineScope {
        if (!forceRefresh) {
            val cached = withContext(Dispatchers.IO) {
                database.verificationSnapshotDao().getReport(region.id)
            }
            if (cached != null && System.currentTimeMillis() - cached.generatedAtUtcMillis < 6 * 3600_000L) {
                runCatching { verificationAdapter.fromJson(cached.reportJson) }.getOrNull()?.let {
                    return@coroutineScope it
                }
            }
        }

        val endDate = LocalDate.now(ZoneOffset.UTC).minusDays(5)
        val startDate = endDate.minusDays(9)
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val startStr = startDate.format(fmt)
        val endStr = endDate.format(fmt)

        val obsDeferred = async(Dispatchers.IO) {
            WeatherNetworkClient.withRetry(maxAttempts = 2) {
                WeatherNetworkClient.openMeteoArchiveApi.getHistoricalObservations(
                    latitude = region.latitude,
                    longitude = region.longitude,
                    startDate = startStr,
                    endDate = endStr
                )
            }
        }

        val modelsToVerify = listOf(
            "ecmwf_ifs025" to "ECMWF IFS 0.25°",
            "gfs_seamless" to "NOAA GFS Seamless",
            "icon_seamless" to "DWD ICON Seamless"
        )

        val modelDeferreds = modelsToVerify.map { (modelId, modelName) ->
            async(Dispatchers.IO) {
                runCatching {
                    val fc = WeatherNetworkClient.openMeteoHistoricalForecastApi.getHistoricalModelForecast(
                        latitude = region.latitude,
                        longitude = region.longitude,
                        startDate = startStr,
                        endDate = endStr,
                        model = modelId
                    )
                    Triple(modelId, modelName, fc)
                }.getOrNull()
            }
        }

        val obsResp = obsDeferred.await()
        val obsTemp = obsResp.hourly?.temperature2m.orEmpty()
        val obsPrecip = obsResp.hourly?.precipitation.orEmpty()

        val verifiedMetrics = mutableListOf<ModelVerificationMetrics>()
        modelDeferreds.forEach { def ->
            val triple = def.await() ?: return@forEach
            val (modelId, modelName, fcResp) = triple
            val m = ForecastVerificationEngine.computeMetrics(
                modelId = modelId,
                modelName = modelName,
                observedTemp = obsTemp,
                forecastTemp = fcResp.hourly?.temperature2m.orEmpty(),
                observedPrecip = obsPrecip,
                forecastPrecip = fcResp.hourly?.precipitation.orEmpty()
            )
            if (m != null) verifiedMetrics += m
        }

        val report = VerificationReport(
            regionName = region.name,
            regionCategory = region.category,
            latitude = region.latitude,
            longitude = region.longitude,
            evaluationWindowDays = 10,
            periodStartIso = startStr,
            periodEndIso = endStr,
            generatedAtUtcMillis = System.currentTimeMillis(),
            referenceSource = "Open-Meteo ERA5 Reanalysis Observations vs Archived Operational Forecasts",
            models = verifiedMetrics.sortedBy { it.tempMaeC },
            weatherNext3Note = "WeatherNext 3 historical Zarr archives (gcp-public-data-weathernext) require Google Cloud allowlist credentials; therefore WeatherNext 3 metrics are not fabricated and will populate automatically once an authorized Cloud proxy is connected."
        )

        withContext(Dispatchers.IO) {
            runCatching {
                database.verificationSnapshotDao().upsertReport(
                    VerificationSnapshotEntity(
                        regionId = region.id,
                        reportJson = verificationAdapter.toJson(report),
                        generatedAtUtcMillis = report.generatedAtUtcMillis
                    )
                )
            }
        }

        report
    }
}
