package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Kotlin data classes with Moshi annotations for parsing:
 * 1. Official Google Maps Platform Weather API (`https://weather.googleapis.com/v1/`),
 *    which serves Google DeepMind WeatherNext 3 operational forecasts.
 * 2. WeatherNext 3 Gridded Cloud / Zarr JSON Proxy schema (`gcp-public-data-weathernext`),
 *    covering temperature, humidity, wind velocity (scalar + U10/V10 vector components),
 *    pressure, precipitation, multi-layer cloud cover, radiation, CAPE, and 64-member ensembles.
 */

// ==================== 1. GOOGLE WEATHER API (WEATHERNEXT 3 REST) ====================

@JsonClass(generateAdapter = true)
data class GoogleWeatherCurrentResponse(
    @Json(name = "currentTime") val currentTime: String = "",
    @Json(name = "timeZone") val timeZone: GoogleTimeZone? = null,
    @Json(name = "isDaytime") val isDaytime: Boolean = true,
    @Json(name = "weatherCondition") val weatherCondition: GoogleWeatherCondition? = null,
    @Json(name = "temperature") val temperature: GoogleTemperature? = null,
    @Json(name = "feelsLikeTemperature") val feelsLikeTemperature: GoogleTemperature? = null,
    @Json(name = "dewPoint") val dewPoint: GoogleTemperature? = null,
    @Json(name = "relativeHumidity") val relativeHumidity: Int? = null,
    @Json(name = "uvIndex") val uvIndex: Int? = null,
    @Json(name = "precipitation") val precipitation: GooglePrecipitation? = null,
    @Json(name = "wind") val wind: GoogleWind? = null,
    @Json(name = "visibility") val visibility: GoogleVisibility? = null,
    @Json(name = "cloudCover") val cloudCover: Int? = null,
    @Json(name = "airPressure") val airPressure: GoogleAirPressure? = null
)

@JsonClass(generateAdapter = true)
data class GoogleWeatherHourlyResponse(
    @Json(name = "forecastHours") val forecastHours: List<GoogleForecastHour> = emptyList(),
    @Json(name = "hours") val hours: List<GoogleForecastHour> = emptyList(),
    @Json(name = "nextPageToken") val nextPageToken: String? = null
) {
    fun allHours(): List<GoogleForecastHour> = forecastHours.ifEmpty { hours }
}

@JsonClass(generateAdapter = true)
data class GoogleForecastHour(
    @Json(name = "interval") val interval: GoogleTimeInterval? = null,
    @Json(name = "isDaytime") val isDaytime: Boolean = true,
    @Json(name = "weatherCondition") val weatherCondition: GoogleWeatherCondition? = null,
    @Json(name = "temperature") val temperature: GoogleTemperature? = null,
    @Json(name = "feelsLikeTemperature") val feelsLikeTemperature: GoogleTemperature? = null,
    @Json(name = "dewPoint") val dewPoint: GoogleTemperature? = null,
    @Json(name = "relativeHumidity") val relativeHumidity: Int? = null,
    @Json(name = "uvIndex") val uvIndex: Int? = null,
    @Json(name = "precipitation") val precipitation: GooglePrecipitation? = null,
    @Json(name = "wind") val wind: GoogleWind? = null,
    @Json(name = "cloudCover") val cloudCover: Int? = null
)

@JsonClass(generateAdapter = true)
data class GoogleTimeZone(
    @Json(name = "id") val id: String = "UTC"
)

@JsonClass(generateAdapter = true)
data class GoogleTimeInterval(
    @Json(name = "startTime") val startTime: String = "",
    @Json(name = "endTime") val endTime: String = ""
)

@JsonClass(generateAdapter = true)
data class GoogleWeatherCondition(
    @Json(name = "iconBaseUri") val iconBaseUri: String? = null,
    @Json(name = "description") val description: GoogleLocalizedText? = null,
    @Json(name = "type") val type: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleLocalizedText(
    @Json(name = "text") val text: String = "",
    @Json(name = "languageCode") val languageCode: String = "en"
)

@JsonClass(generateAdapter = true)
data class GoogleTemperature(
    @Json(name = "degrees") val degrees: Double? = null,
    @Json(name = "unit") val unit: String = "CELSIUS"
) {
    fun toCelsius(): Double? {
        val d = degrees ?: return null
        return if (unit.equals("FAHRENHEIT", ignoreCase = true)) (d - 32.0) * 5.0 / 9.0 else d
    }
}

@JsonClass(generateAdapter = true)
data class GooglePrecipitation(
    @Json(name = "probability") val probability: GooglePrecipProbability? = null,
    @Json(name = "qpf") val qpf: GooglePrecipQuantity? = null
)

@JsonClass(generateAdapter = true)
data class GooglePrecipProbability(
    @Json(name = "percent") val percent: Int? = null,
    @Json(name = "type") val type: String? = null
)

@JsonClass(generateAdapter = true)
data class GooglePrecipQuantity(
    @Json(name = "quantity") val quantity: Double? = null,
    @Json(name = "unit") val unit: String = "MILLIMETERS"
) {
    fun toMillimeters(): Double {
        val q = quantity ?: return 0.0
        return if (unit.equals("INCHES", ignoreCase = true)) q * 25.4 else q
    }
}

@JsonClass(generateAdapter = true)
data class GoogleWind(
    @Json(name = "speed") val speed: GoogleWindSpeed? = null,
    @Json(name = "gust") val gust: GoogleWindSpeed? = null,
    @Json(name = "direction") val direction: GoogleWindDirection? = null
)

@JsonClass(generateAdapter = true)
data class GoogleWindSpeed(
    @Json(name = "value") val value: Double? = null,
    @Json(name = "unit") val unit: String = "KILOMETERS_PER_HOUR"
) {
    fun toKmh(): Double? {
        val v = value ?: return null
        return when (unit.uppercase()) {
            "MILES_PER_HOUR" -> v * 1.60934
            "METERS_PER_SECOND" -> v * 3.6
            else -> v
        }
    }
}

@JsonClass(generateAdapter = true)
data class GoogleWindDirection(
    @Json(name = "degrees") val degrees: Int? = null,
    @Json(name = "cardinal") val cardinal: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleVisibility(
    @Json(name = "distance") val distance: Double? = null,
    @Json(name = "unit") val unit: String = "KILOMETERS"
)

@JsonClass(generateAdapter = true)
data class GoogleAirPressure(
    @Json(name = "meanSeaLevelMillibars") val meanSeaLevelMillibars: Double? = null
)

// ==================== 2. WEATHERNEXT 3 GRIDDED CLOUD / ZARR JSON SCHEMA ====================

@JsonClass(generateAdapter = true)
data class WeatherResponse(
    @Json(name = "model_version") val modelVersion: String = "weathernext-3",
    @Json(name = "init_time_utc") val initTimeUtc: String = "",
    @Json(name = "spatial_resolution_deg") val spatialResolutionDeg: Double = 0.1,
    @Json(name = "ensemble_member_count") val ensembleMemberCount: Int = 64,
    @Json(name = "latitude") val latitude: Double = 0.0,
    @Json(name = "longitude") val longitude: Double = 0.0,
    @Json(name = "elevation_m") val elevationMeters: Double? = null,
    @Json(name = "timezone") val timezone: String = "UTC",
    @Json(name = "current") val current: WeatherCurrentMetrics? = null,
    @Json(name = "hourly") val hourly: List<WeatherHourlyMetric> = emptyList(),
    @Json(name = "ensemble_summary") val ensembleSummary: WeatherEnsembleDistribution? = null,
    // Lightweight flat proxy fields
    @Json(name = "temperature_2m") val flatTemperature2m: Double? = null,
    @Json(name = "max_temp_24h") val flatMaxTemp24h: Double? = null,
    @Json(name = "min_temp_24h") val flatMinTemp24h: Double? = null,
    @Json(name = "precip_24h_mm") val flatPrecip24hMm: Double? = null,
    @Json(name = "max_wind_24h_kmh") val flatMaxWind24hKmh: Double? = null
)

@JsonClass(generateAdapter = true)
data class WindVelocity(
    @Json(name = "speed_kmh") val speedKmh: Double? = null,
    @Json(name = "gusts_kmh") val gustsKmh: Double? = null,
    @Json(name = "direction_deg") val directionDeg: Int? = null,
    @Json(name = "u_component_ms") val uComponentMs: Double? = null,
    @Json(name = "v_component_ms") val vComponentMs: Double? = null
) {
    fun resolvedSpeedKmh(): Double? {
        if (speedKmh != null) return speedKmh
        if (uComponentMs != null && vComponentMs != null) {
            return hypot(uComponentMs, vComponentMs) * 3.6
        }
        return null
    }

    fun resolvedDirectionDeg(): Int? {
        if (directionDeg != null) return directionDeg
        if (uComponentMs != null && vComponentMs != null) {
            return ((Math.toDegrees(atan2(-uComponentMs, -vComponentMs)) + 360.0) % 360.0).toInt()
        }
        return null
    }
}

@JsonClass(generateAdapter = true)
data class WeatherCurrentMetrics(
    @Json(name = "valid_time_utc") val validTimeUtc: String = "",
    @Json(name = "temperature_2m_c") val temperature2mC: Double? = null,
    @Json(name = "apparent_temperature_2m_c") val apparentTemperature2mC: Double? = null,
    @Json(name = "dew_point_2m_c") val dewPoint2mC: Double? = null,
    @Json(name = "relative_humidity_2m_pct") val relativeHumidity2mPct: Double? = null,
    @Json(name = "surface_pressure_hpa") val surfacePressureHpa: Double? = null,
    @Json(name = "mean_sea_level_pressure_hpa") val meanSeaLevelPressureHpa: Double? = null,
    @Json(name = "wind_velocity") val windVelocity: WindVelocity? = null,
    @Json(name = "u_component_of_wind_10m_ms") val windU10mMs: Double? = null,
    @Json(name = "v_component_of_wind_10m_ms") val windV10mMs: Double? = null,
    @Json(name = "wind_speed_10m_kmh") val windSpeed10mKmh: Double? = null,
    @Json(name = "wind_direction_10m_deg") val windDirection10mDeg: Int? = null,
    @Json(name = "wind_gusts_10m_kmh") val windGusts10mKmh: Double? = null,
    @Json(name = "total_precipitation_mm") val totalPrecipitationMm: Double? = null,
    @Json(name = "precipitation_probability_pct") val precipitationProbabilityPct: Int? = null,
    @Json(name = "snowfall_cm") val snowfallCm: Double? = null,
    @Json(name = "total_cloud_cover_pct") val totalCloudCoverPct: Int? = null,
    @Json(name = "low_cloud_cover_pct") val lowCloudCoverPct: Int? = null,
    @Json(name = "mid_cloud_cover_pct") val midCloudCoverPct: Int? = null,
    @Json(name = "high_cloud_cover_pct") val highCloudCoverPct: Int? = null,
    @Json(name = "downward_shortwave_radiation_wm2") val downwardShortwaveRadiationWm2: Double? = null,
    @Json(name = "cape_j_kg") val capeJKg: Double? = null,
    @Json(name = "visibility_m") val visibilityMeters: Double? = null,
    @Json(name = "uv_index") val uvIndex: Double? = null
) {
    fun resolvedWindSpeedKmh(): Double? {
        windVelocity?.resolvedSpeedKmh()?.let { return it }
        if (windSpeed10mKmh != null) return windSpeed10mKmh
        if (windU10mMs != null && windV10mMs != null) {
            return hypot(windU10mMs, windV10mMs) * 3.6
        }
        return null
    }

    fun resolvedWindDirectionDeg(): Int? {
        windVelocity?.resolvedDirectionDeg()?.let { return it }
        if (windDirection10mDeg != null) return windDirection10mDeg
        if (windU10mMs != null && windV10mMs != null) {
            return ((Math.toDegrees(atan2(-windU10mMs, -windV10mMs)) + 360.0) % 360.0).toInt()
        }
        return null
    }
}

@JsonClass(generateAdapter = true)
data class WeatherHourlyMetric(
    @Json(name = "lead_time_hours") val leadTimeHours: Int = 0,
    @Json(name = "valid_time_utc") val validTimeUtc: String = "",
    @Json(name = "temperature_2m_c") val temperature2mC: Double? = null,
    @Json(name = "apparent_temperature_2m_c") val apparentTemperature2mC: Double? = null,
    @Json(name = "dew_point_2m_c") val dewPoint2mC: Double? = null,
    @Json(name = "relative_humidity_2m_pct") val relativeHumidity2mPct: Double? = null,
    @Json(name = "surface_pressure_hpa") val surfacePressureHpa: Double? = null,
    @Json(name = "mean_sea_level_pressure_hpa") val meanSeaLevelPressureHpa: Double? = null,
    @Json(name = "wind_velocity") val windVelocity: WindVelocity? = null,
    @Json(name = "u_component_of_wind_10m_ms") val windU10mMs: Double? = null,
    @Json(name = "v_component_of_wind_10m_ms") val windV10mMs: Double? = null,
    @Json(name = "wind_speed_10m_kmh") val windSpeed10mKmh: Double? = null,
    @Json(name = "wind_gusts_10m_kmh") val windGusts10mKmh: Double? = null,
    @Json(name = "total_precipitation_mm") val totalPrecipitationMm: Double? = null,
    @Json(name = "precipitation_probability_pct") val precipitationProbabilityPct: Int? = null,
    @Json(name = "snowfall_cm") val snowfallCm: Double? = null,
    @Json(name = "total_cloud_cover_pct") val totalCloudCoverPct: Int? = null,
    @Json(name = "downward_shortwave_radiation_wm2") val downwardShortwaveRadiationWm2: Double? = null,
    @Json(name = "cape_j_kg") val capeJKg: Double? = null,
    @Json(name = "ensemble_temp_spread_c") val ensembleTempSpreadC: Double? = null
) {
    fun resolvedWindSpeedKmh(): Double? {
        windVelocity?.resolvedSpeedKmh()?.let { return it }
        if (windSpeed10mKmh != null) return windSpeed10mKmh
        if (windU10mMs != null && windV10mMs != null) {
            return hypot(windU10mMs, windV10mMs) * 3.6
        }
        return null
    }
}

@JsonClass(generateAdapter = true)
data class WeatherEnsembleDistribution(
    @Json(name = "member_count") val memberCount: Int = 64,
    @Json(name = "temp_24h_mean_c") val temp24hMeanC: Double? = null,
    @Json(name = "temp_24h_std_dev_c") val temp24hStdDevC: Double? = null,
    @Json(name = "temp_24h_p10_c") val temp24hP10C: Double? = null,
    @Json(name = "temp_24h_p50_c") val temp24hP50C: Double? = null,
    @Json(name = "temp_24h_p90_c") val temp24hP90C: Double? = null,
    @Json(name = "precip_prob_gt_1mm_pct") val precipProbGt1mmPct: Int? = null,
    @Json(name = "prob_temp_exceed_35c_pct") val probTempExceed35cPct: Int? = null
)
