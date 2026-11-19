package com.example.domain.model

import com.squareup.moshi.JsonClass

enum class TemperatureUnit(val symbol: String, val label: String) {
    CELSIUS("°C", "Celsius (°C)"),
    FAHRENHEIT("°F", "Fahrenheit (°F)")
}

enum class WindUnit(val symbol: String, val label: String) {
    KMH("km/h", "Kilometres/hour (km/h)"),
    MPH("mph", "Miles/hour (mph)"),
    MS("m/s", "Metres/second (m/s)")
}

enum class PrecipitationUnit(val symbol: String, val label: String) {
    MM("mm", "Millimetres (mm)"),
    INCHES("in", "Inches (in)")
}

enum class PressureUnit(val symbol: String, val label: String) {
    HPA("hPa", "Hectopascals (hPa)"),
    MBAR("mbar", "Millibars (mbar)"),
    INHG("inHg", "Inches of Mercury (inHg)")
}

enum class AqiStandard(val label: String, val agency: String) {
    INDIA_CPCB("India AQI (CPCB)", "CPCB National AQI (0–500)"),
    US_EPA("US AQI (EPA)", "US EPA AQI (0–500)")
}

enum class ThemeMode(val label: String) {
    DYNAMIC_WEATHER("Dynamic Weather"),
    DARK("Midnight Dark"),
    LIGHT("Daylight Mist"),
    AUTOMATIC("Follow System")
}

enum class WeatherScene {
    CLEAR_DAY,
    CLEAR_NIGHT,
    GOLDEN_HOUR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    RAIN,
    THUNDERSTORM,
    SNOW
}

enum class ForecastHorizon(val label: String, val certaintyNote: String) {
    SHORT_RANGE("Short-Range (Days 1–3)", "High meteorological skill"),
    MEDIUM_RANGE("Medium-Range (Days 4–7)", "Moderate skill — track ensemble spread"),
    EXTENDED("Extended Outlook (Days 8–16)", "Lower certainty — synoptic trend only")
}

enum class AlertProvenance(val label: String) {
    OFFICIAL_AGENCY("Official Agency Warning"),
    MODEL_RISK_INDICATOR("Model-Derived Risk Assessment")
}

enum class AlertSeverity(val label: String, val rank: Int) {
    EXTREME("Extreme", 4),
    SEVERE("Severe", 3),
    MODERATE("Moderate", 2),
    MINOR("Advisory", 1)
}

@JsonClass(generateAdapter = true)
data class SavedLocation(
    val id: String,
    val name: String,
    val adminRegion: String,
    val country: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val elevationMeters: Double? = null,
    val isCurrentGps: Boolean = false,
    val sortOrder: Int = 0
)

@JsonClass(generateAdapter = true)
data class CurrentConditions(
    val observationTimeIso: String,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val relativeHumidity: Int,
    val dewPointC: Double,
    val surfacePressureHpa: Double,
    val pressureMslHpa: Double,
    val windSpeedKmh: Double,
    val windDirectionDeg: Int,
    val windGustsKmh: Double,
    val precipitationMm: Double,
    val rainMm: Double,
    val snowfallCm: Double,
    val cloudCoverPct: Int,
    val weatherCode: Int,
    val isDay: Boolean,
    val visibilityMeters: Double?,
    val uvIndex: Double?,
    val shortwaveRadiationWm2: Double?,
    val capeJKg: Double?
)

@JsonClass(generateAdapter = true)
data class HourlyPoint(
    val timeIso: String,
    val epochSeconds: Long,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val relativeHumidity: Int,
    val dewPointC: Double,
    val precipitationProbability: Int?,
    val precipitationMm: Double,
    val rainMm: Double,
    val snowfallCm: Double,
    val weatherCode: Int,
    val pressureMslHpa: Double,
    val surfacePressureHpa: Double,
    val cloudCoverPct: Int,
    val cloudCoverLowPct: Int?,
    val cloudCoverMidPct: Int?,
    val cloudCoverHighPct: Int?,
    val visibilityMeters: Double?,
    val windSpeedKmh: Double,
    val windDirectionDeg: Int,
    val windGustsKmh: Double,
    val uvIndex: Double?,
    val shortwaveRadiationWm2: Double?,
    val capeJKg: Double?,
    val isDay: Boolean
)

@JsonClass(generateAdapter = true)
data class DailyPoint(
    val dateIso: String,
    val dayIndex: Int,
    val weatherCode: Int,
    val tempMaxC: Double,
    val tempMinC: Double,
    val apparentTempMaxC: Double,
    val apparentTempMinC: Double,
    val sunriseIso: String,
    val sunsetIso: String,
    val daylightDurationSeconds: Double?,
    val uvIndexMax: Double?,
    val precipitationSumMm: Double,
    val rainSumMm: Double,
    val snowfallSumCm: Double,
    val precipitationProbabilityMax: Int?,
    val windSpeedMaxKmh: Double,
    val windGustsMaxKmh: Double,
    val windDirectionDominantDeg: Int,
    val shortwaveRadiationSumMj: Double?,
    val horizon: ForecastHorizon
)

@JsonClass(generateAdapter = true)
data class EnsembleSummary(
    val modelName: String,
    val memberCount: Int,
    val next24hTempMeanC: Double,
    val next24hTempSpreadC: Double,
    val next24hTempMinC: Double,
    val next24hTempMaxC: Double,
    val precipProbabilityDerivedPct: Int,
    val probExceed35CPct: Int,
    val confidenceLabel: String,
    val spreadSeriesC: List<Double>
)

@JsonClass(generateAdapter = true)
data class ModelComparisonPoint(
    val modelId: String,
    val displayName: String,
    val category: String,
    val resolutionDesc: String,
    val available: Boolean,
    val statusNote: String,
    val currentTempC: Double?,
    val next24hMaxC: Double?,
    val next24hMinC: Double?,
    val next24hPrecipMm: Double?,
    val maxWindKmh: Double?,
    val initTimeNote: String
)

@JsonClass(generateAdapter = true)
data class ProviderHealth(
    val name: String,
    val role: String,
    val status: String,
    val isLive: Boolean,
    val resolution: String,
    val lastUpdatedUtc: Long?,
    val detail: String
)

@JsonClass(generateAdapter = true)
data class AirQualityData(
    val observationTimeIso: String,
    val usAqi: Int?,
    val europeanAqi: Int?,
    val indiaCpcbAqi: Int?,
    val dominantPollutant: String,
    val pm25: Double?,
    val pm10: Double?,
    val no2: Double?,
    val o3: Double?,
    val so2: Double?,
    val co: Double?,
    val dust: Double?,
    val grassPollen: Double?,
    val birchPollen: Double?,
    val ragweedPollen: Double?,
    val sourceName: String
)

@JsonClass(generateAdapter = true)
data class WeatherAlert(
    val id: String,
    val title: String,
    val eventType: String,
    val severity: AlertSeverity,
    val provenance: AlertProvenance,
    val issuingAuthority: String,
    val affectedArea: String,
    val validFrom: String,
    val validUntil: String,
    val description: String,
    val precautions: List<String>,
    val updatedUtcIso: String
)

@JsonClass(generateAdapter = true)
data class AstronomyInfo(
    val sunriseIso: String,
    val sunsetIso: String,
    val daylightFormatted: String,
    val goldenHourMorning: String,
    val goldenHourEvening: String,
    val blueHourMorning: String,
    val blueHourEvening: String,
    val moonPhaseName: String,
    val moonIlluminationPct: Int,
    val moonAgeDays: Double,
    val solarProgress: Float
)

@JsonClass(generateAdapter = true)
data class WeatherBundle(
    val location: SavedLocation,
    val fetchedAtUtcMillis: Long,
    val utcOffsetSeconds: Int,
    val timezoneAbbreviation: String,
    val primaryEngineLabel: String,
    val spatialResolutionNote: String,
    val current: CurrentConditions,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>,
    val airQuality: AirQualityData?,
    val ensemble: EnsembleSummary?,
    val modelComparisons: List<ModelComparisonPoint>,
    val providerHealth: List<ProviderHealth>,
    val modelDisagreements: List<String>,
    val alerts: List<WeatherAlert>,
    val summaryBullets: List<String>,
    val astronomy: AstronomyInfo,
    val isFromCache: Boolean = false
)

data class RadarFrame(
    val timestampSeconds: Long,
    val path: String,
    val isObserved: Boolean
)

data class RadarState(
    val host: String = "https://tilecache.rainviewer.com",
    val generatedEpoch: Long = 0L,
    val frames: List<RadarFrame> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ModelVerificationMetrics(
    val modelId: String,
    val modelName: String,
    val sampleHours: Int,
    val tempMaeC: Double,
    val tempRmseC: Double,
    val tempBiasC: Double,
    val precipAccuracyPct: Double,
    val pod: Double,
    val far: Double,
    val csi: Double,
    val brierScore: Double
)

@JsonClass(generateAdapter = true)
data class VerificationReport(
    val regionName: String,
    val regionCategory: String,
    val latitude: Double,
    val longitude: Double,
    val evaluationWindowDays: Int,
    val periodStartIso: String,
    val periodEndIso: String,
    val generatedAtUtcMillis: Long,
    val referenceSource: String,
    val models: List<ModelVerificationMetrics>,
    val weatherNext3Note: String
)

data class UserPreferences(
    val tempUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windUnit: WindUnit = WindUnit.KMH,
    val precipUnit: PrecipitationUnit = PrecipitationUnit.MM,
    val pressureUnit: PressureUnit = PressureUnit.HPA,
    val use24HourTime: Boolean = false,
    val aqiStandard: AqiStandard = AqiStandard.INDIA_CPCB,
    val themeMode: ThemeMode = ThemeMode.DYNAMIC_WEATHER,
    val reducedMotion: Boolean = false,
    val refreshIntervalMinutes: Int = 30,
    val severeAlertsEnabled: Boolean = true,
    val precipAlertsEnabled: Boolean = true,
    val weatherNext3ProxyUrl: String = ""
)

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestampLabel: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)
