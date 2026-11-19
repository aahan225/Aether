package com.example.domain.engine

import com.example.domain.model.AqiStandard
import com.example.domain.model.AstronomyInfo
import com.example.domain.model.PrecipitationUnit
import com.example.domain.model.PressureUnit
import com.example.domain.model.TemperatureUnit
import com.example.domain.model.UserPreferences
import com.example.domain.model.WeatherScene
import com.example.domain.model.WindUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

object MeteorologyCalculator {

    fun wmoDescription(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1 -> "Mainly Clear"
        2 -> "Partly Cloudy"
        3 -> "Overcast"
        45 -> "Fog"
        48 -> "Depositing Rime Fog"
        51 -> "Light Drizzle"
        53 -> "Moderate Drizzle"
        55 -> "Dense Drizzle"
        56 -> "Light Freezing Drizzle"
        57 -> "Dense Freezing Drizzle"
        61 -> "Slight Rain"
        63 -> "Moderate Rain"
        65 -> "Heavy Rain"
        66 -> "Light Freezing Rain"
        67 -> "Heavy Freezing Rain"
        71 -> "Slight Snowfall"
        73 -> "Moderate Snowfall"
        75 -> "Heavy Snowfall"
        77 -> "Snow Grains"
        80 -> "Slight Rain Showers"
        81 -> "Moderate Rain Showers"
        82 -> "Violent Rain Showers"
        85 -> "Slight Snow Showers"
        86 -> "Heavy Snow Showers"
        95 -> "Thunderstorm"
        96 -> "Thunderstorm with Slight Hail"
        99 -> "Thunderstorm with Heavy Hail"
        else -> "Variable Sky"
    }

    fun resolveScene(
        weatherCode: Int,
        isDay: Boolean,
        solarProgress: Float
    ): WeatherScene {
        return when (weatherCode) {
            95, 96, 99 -> WeatherScene.THUNDERSTORM
            71, 73, 75, 77, 85, 86 -> WeatherScene.SNOW
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherScene.RAIN
            45, 48 -> WeatherScene.FOG
            3 -> WeatherScene.CLOUDY
            2 -> if (!isDay) WeatherScene.CLEAR_NIGHT else if (solarProgress in 0.0f..0.12f || solarProgress in 0.88f..1.0f) WeatherScene.GOLDEN_HOUR else WeatherScene.PARTLY_CLOUDY
            else -> when {
                !isDay -> WeatherScene.CLEAR_NIGHT
                solarProgress in 0.0f..0.14f || solarProgress in 0.86f..1.0f -> WeatherScene.GOLDEN_HOUR
                else -> WeatherScene.CLEAR_DAY
            }
        }
    }

    fun formatTemp(tempC: Double, unit: TemperatureUnit, includeUnit: Boolean = false): String {
        val value = when (unit) {
            TemperatureUnit.CELSIUS -> tempC
            TemperatureUnit.FAHRENHEIT -> tempC * 9.0 / 5.0 + 32.0
        }
        return if (includeUnit) "${value.roundToInt()}${unit.symbol}" else "${value.roundToInt()}°"
    }

    fun formatTempDelta(deltaC: Double, unit: TemperatureUnit): String {
        val v = if (unit == TemperatureUnit.FAHRENHEIT) deltaC * 9.0 / 5.0 else deltaC
        return String.format(Locale.US, "%.1f%s", v, unit.symbol)
    }

    fun formatWind(kmh: Double, unit: WindUnit): String {
        val v = when (unit) {
            WindUnit.KMH -> kmh
            WindUnit.MPH -> kmh * 0.621371
            WindUnit.MS -> kmh / 3.6
        }
        return String.format(Locale.US, "%.1f %s", v, unit.symbol)
    }

    fun formatPrecipitation(mm: Double, unit: PrecipitationUnit): String {
        return when (unit) {
            PrecipitationUnit.MM -> String.format(Locale.US, "%.1f mm", mm)
            PrecipitationUnit.INCHES -> String.format(Locale.US, "%.2f in", mm / 25.4)
        }
    }

    fun formatPressure(hpa: Double, unit: PressureUnit): String {
        return when (unit) {
            PressureUnit.HPA -> String.format(Locale.US, "%.0f hPa", hpa)
            PressureUnit.MBAR -> String.format(Locale.US, "%.0f mbar", hpa)
            PressureUnit.INHG -> String.format(Locale.US, "%.2f inHg", hpa * 0.029529983)
        }
    }

    fun windCardinal(degrees: Int): String {
        val dirs = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val idx = (((degrees % 360) + 360) % 360 / 22.5).roundToInt() % 16
        return dirs[idx]
    }

    fun uvCategory(uv: Double?): Pair<String, String> {
        val v = uv ?: return "Unavailable" to "UV index telemetry unavailable"
        return when {
            v < 3.0 -> "Low" to "Minimal protection needed outdoors."
            v < 6.0 -> "Moderate" to "Seek shade near midday; SPF 30+ advised."
            v < 8.0 -> "High" to "Wear sunglasses, hat & SPF 30+ sunscreen."
            v < 11.0 -> "Very High" to "Limit midday exposure between 11 AM–3 PM."
            else -> "Extreme" to "Avoid direct sun; full UV protection essential."
        }
    }

    fun dewPointComfort(dewPointC: Double): String = when {
        dewPointC < 10.0 -> "Dry & crisp air"
        dewPointC < 16.0 -> "Comfortable humidity"
        dewPointC < 20.0 -> "Noticeably humid"
        dewPointC < 24.0 -> "Muggy & sticky"
        else -> "Oppressive tropical moisture"
    }

    fun visibilityLabel(meters: Double?): String {
        val m = meters ?: return "N/A"
        val km = m / 1000.0
        val desc = when {
            km < 1.0 -> "Dense Fog"
            km < 4.0 -> "Haze / Mist"
            km < 10.0 -> "Moderate"
            else -> "Clear"
        }
        return String.format(Locale.US, "%.1f km · %s", km, desc)
    }

    /**
     * Computes official India National Air Quality Index (CPCB, 2014) from raw pollutant
     * concentrations (µg/m³ for PM2.5, PM10, NO2, O3, SO2; mg/m³ for CO — Open-Meteo returns CO in µg/m³).
     * Never mixes US EPA breakpoints with CPCB breakpoints.
     */
    fun computeIndiaCpcbAqi(
        pm25: Double?,
        pm10: Double?,
        no2: Double?,
        o3: Double?,
        so2: Double?,
        coUgM3: Double?
    ): Pair<Int?, String> {
        val subIndices = mutableListOf<Pair<String, Double>>()

        pm25?.let { v ->
            subIndices += "PM2.5" to piecewiseLinear(
                v,
                listOf(
                    0.0 to 30.0 to (0.0 to 50.0),
                    30.0 to 60.0 to (51.0 to 100.0),
                    60.0 to 90.0 to (101.0 to 200.0),
                    90.0 to 120.0 to (201.0 to 300.0),
                    120.0 to 250.0 to (301.0 to 400.0),
                    250.0 to 500.0 to (401.0 to 500.0)
                )
            )
        }
        pm10?.let { v ->
            subIndices += "PM10" to piecewiseLinear(
                v,
                listOf(
                    0.0 to 50.0 to (0.0 to 50.0),
                    50.0 to 100.0 to (51.0 to 100.0),
                    100.0 to 250.0 to (101.0 to 200.0),
                    250.0 to 350.0 to (201.0 to 300.0),
                    350.0 to 430.0 to (301.0 to 400.0),
                    430.0 to 600.0 to (401.0 to 500.0)
                )
            )
        }
        no2?.let { v ->
            subIndices += "NO₂" to piecewiseLinear(
                v,
                listOf(
                    0.0 to 40.0 to (0.0 to 50.0),
                    40.0 to 80.0 to (51.0 to 100.0),
                    80.0 to 180.0 to (101.0 to 200.0),
                    180.0 to 280.0 to (201.0 to 300.0),
                    280.0 to 400.0 to (301.0 to 400.0),
                    400.0 to 600.0 to (401.0 to 500.0)
                )
            )
        }
        o3?.let { v ->
            subIndices += "O₃" to piecewiseLinear(
                v,
                listOf(
                    0.0 to 50.0 to (0.0 to 50.0),
                    50.0 to 100.0 to (51.0 to 100.0),
                    100.0 to 168.0 to (101.0 to 200.0),
                    168.0 to 208.0 to (201.0 to 300.0),
                    208.0 to 748.0 to (301.0 to 400.0),
                    748.0 to 1000.0 to (401.0 to 500.0)
                )
            )
        }
        so2?.let { v ->
            subIndices += "SO₂" to piecewiseLinear(
                v,
                listOf(
                    0.0 to 40.0 to (0.0 to 50.0),
                    40.0 to 80.0 to (51.0 to 100.0),
                    80.0 to 380.0 to (101.0 to 200.0),
                    380.0 to 800.0 to (201.0 to 300.0),
                    800.0 to 1600.0 to (301.0 to 400.0),
                    1600.0 to 2000.0 to (401.0 to 500.0)
                )
            )
        }
        coUgM3?.let { ug ->
            val mg = ug / 1000.0
            subIndices += "CO" to piecewiseLinear(
                mg,
                listOf(
                    0.0 to 1.0 to (0.0 to 50.0),
                    1.0 to 2.0 to (51.0 to 100.0),
                    2.0 to 10.0 to (101.0 to 200.0),
                    10.0 to 17.0 to (201.0 to 300.0),
                    17.0 to 34.0 to (301.0 to 400.0),
                    34.0 to 50.0 to (401.0 to 500.0)
                )
            )
        }

        if (subIndices.isEmpty()) return null to "N/A"
        val maxEntry = subIndices.maxBy { it.second }
        return maxEntry.second.roundToInt().coerceIn(0, 500) to maxEntry.first
    }

    private fun piecewiseLinear(
        value: Double,
        ranges: List<Pair<Pair<Double, Double>, Pair<Double, Double>>>
    ): Double {
        val v = max(0.0, value)
        for ((concRange, aqiRange) in ranges) {
            if (v <= concRange.second) {
                val frac = (v - concRange.first) / max(1e-6, concRange.second - concRange.first)
                return aqiRange.first + frac * (aqiRange.second - aqiRange.first)
            }
        }
        return 500.0
    }

    fun aqiCategory(aqi: Int?, standard: AqiStandard): Pair<String, String> {
        val v = aqi ?: return "Unavailable" to "Air quality monitoring unavailable"
        return when (standard) {
            AqiStandard.INDIA_CPCB -> when {
                v <= 50 -> "Good" to "Minimal health impact."
                v <= 100 -> "Satisfactory" to "Minor breathing discomfort to sensitive people."
                v <= 200 -> "Moderate" to "Discomfort to people with asthma, heart or lung disease."
                v <= 300 -> "Poor" to "Breathing discomfort on prolonged exposure."
                v <= 400 -> "Very Poor" to "Respiratory illness on prolonged exposure; mask advised."
                else -> "Severe" to "Affects healthy people; avoid outdoor exertion."
            }
            AqiStandard.US_EPA -> when {
                v <= 50 -> "Good" to "Air quality is satisfactory with little risk."
                v <= 100 -> "Moderate" to "Acceptable; sensitive groups should limit heavy exertion."
                v <= 150 -> "Unhealthy for Sensitive" to "Sensitive groups may experience health effects."
                v <= 200 -> "Unhealthy" to "Everyone may begin to experience health effects."
                v <= 300 -> "Very Unhealthy" to "Health alert: elevated risk for everyone."
                else -> "Hazardous" to "Emergency conditions: remain indoors."
            }
        }
    }

    fun calculateAstronomy(
        sunriseIso: String,
        sunsetIso: String,
        currentLocalIso: String,
        daylightDurationSeconds: Double?,
        use24Hour: Boolean
    ): AstronomyInfo {
        val sr = parseLocalDateTimeSafe(sunriseIso)
        val ss = parseLocalDateTimeSafe(sunsetIso)
        val now = parseLocalDateTimeSafe(currentLocalIso)

        val fmt = if (use24Hour) DateTimeFormatter.ofPattern("HH:mm") else DateTimeFormatter.ofPattern("h:mm a")

        val goldenMorning = if (sr != null) "${sr.format(fmt)} – ${sr.plusMinutes(45).format(fmt)}" else "—"
        val blueMorning = if (sr != null) "${sr.minusMinutes(28).format(fmt)} – ${sr.format(fmt)}" else "—"
        val goldenEvening = if (ss != null) "${ss.minusMinutes(45).format(fmt)} – ${ss.format(fmt)}" else "—"
        val blueEvening = if (ss != null) "${ss.format(fmt)} – ${ss.plusMinutes(28).format(fmt)}" else "—"

        val totalSec = daylightDurationSeconds?.toLong()
            ?: if (sr != null && ss != null) java.time.Duration.between(sr, ss).seconds else 43200L
        val dlHours = totalSec / 3600
        val dlMins = (totalSec % 3600) / 60
        val daylightFormatted = "${dlHours}h ${dlMins}m"

        val solarProgress = if (sr != null && ss != null && now != null) {
            val total = java.time.Duration.between(sr, ss).toMinutes().coerceAtLeast(1)
            val elapsed = java.time.Duration.between(sr, now).toMinutes()
            (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else 0.5f

        // Synodic lunar cycle calculation from reference new moon (2000-01-06 18:14 UTC)
        val synodicMonth = 29.53058867
        val refEpochSeconds = 947182440L
        val nowEpoch = Instant.now().epochSecond
        val daysSinceRef = (nowEpoch - refEpochSeconds) / 86400.0
        val moonAge = ((daysSinceRef % synodicMonth) + synodicMonth) % synodicMonth
        val illumination = ((1.0 - cos(2.0 * PI * moonAge / synodicMonth)) / 2.0 * 100.0).roundToInt()

        val moonPhaseName = when {
            moonAge < 1.85 -> "New Moon"
            moonAge < 5.54 -> "Waxing Crescent"
            moonAge < 9.23 -> "First Quarter"
            moonAge < 12.92 -> "Waxing Gibbous"
            moonAge < 16.61 -> "Full Moon"
            moonAge < 20.30 -> "Waning Gibbous"
            moonAge < 23.99 -> "Last Quarter"
            moonAge < 27.68 -> "Waning Crescent"
            else -> "New Moon"
        }

        return AstronomyInfo(
            sunriseIso = sunriseIso,
            sunsetIso = sunsetIso,
            daylightFormatted = daylightFormatted,
            goldenHourMorning = goldenMorning,
            goldenHourEvening = goldenEvening,
            blueHourMorning = blueMorning,
            blueHourEvening = blueEvening,
            moonPhaseName = moonPhaseName,
            moonIlluminationPct = illumination,
            moonAgeDays = (moonAge * 10.0).roundToInt() / 10.0,
            solarProgress = solarProgress
        )
    }

    fun formatLocalTime(isoTime: String, use24Hour: Boolean): String {
        val dt = parseLocalDateTimeSafe(isoTime) ?: return isoTime.takeLast(5)
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        return dt.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    }

    fun formatHourLabel(isoTime: String, use24Hour: Boolean): String {
        val dt = parseLocalDateTimeSafe(isoTime) ?: return isoTime.takeLast(5)
        val pattern = if (use24Hour) "HH:00" else "h a"
        return dt.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    }

    fun formatDayLabel(dateIso: String, index: Int): String {
        if (index == 0) return "Today"
        if (index == 1) return "Tomorrow"
        return try {
            val d = LocalDate.parse(dateIso)
            d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US))
        } catch (_: Exception) {
            dateIso
        }
    }

    fun formatEpochInOffset(epochMillis: Long, utcOffsetSeconds: Int, use24Hour: Boolean): String {
        val offset = ZoneOffset.ofTotalSeconds(utcOffsetSeconds)
        val ldt = Instant.ofEpochMilli(epochMillis).atOffset(offset).toLocalDateTime()
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        return ldt.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    }

    fun formatFreshness(fetchedAtUtcMillis: Long): Pair<String, Boolean> {
        val ageMin = ((System.currentTimeMillis() - fetchedAtUtcMillis) / 60_000L).coerceAtLeast(0L)
        val isStale = ageMin >= 45L
        val label = when {
            ageMin < 1L -> "Updated just now"
            ageMin < 60L -> "Updated ${ageMin}m ago"
            else -> "Updated ${ageMin / 60}h ${ageMin % 60}m ago"
        }
        return label to isStale
    }

    fun parseLocalDateTimeSafe(iso: String): LocalDateTime? {
        return try {
            LocalDateTime.parse(iso)
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(iso.take(16))
            } catch (_: Exception) {
                null
            }
        }
    }

    fun formatPreferenceSummary(prefs: UserPreferences): String {
        return "${prefs.tempUnit.symbol} · ${prefs.windUnit.symbol} · ${prefs.pressureUnit.symbol} · ${prefs.aqiStandard.label}"
    }

    fun pressureTrendLabel(hourlyPressures: List<Double>): Pair<String, Double> {
        if (hourlyPressures.size < 4) return "Steady" to 0.0
        val delta = hourlyPressures[3] - hourlyPressures[0]
        val label = when {
            delta >= 1.5 -> "Rising Rapidly"
            delta >= 0.4 -> "Rising"
            delta <= -1.5 -> "Falling Rapidly"
            delta <= -0.4 -> "Falling"
            else -> "Steady"
        }
        return label to delta
    }

    fun outdoorActivityScore(
        tempC: Double,
        precipProb: Int,
        windKmh: Double,
        aqi: Int?,
        uv: Double?
    ): Pair<String, String> {
        var score = 100
        if (tempC > 35 || tempC < 5) score -= 30 else if (tempC > 30 || tempC < 12) score -= 12
        if (precipProb >= 60) score -= 35 else if (precipProb >= 30) score -= 15
        if (windKmh > 35) score -= 25 else if (windKmh > 22) score -= 10
        if ((aqi ?: 0) > 200) score -= 35 else if ((aqi ?: 0) > 100) score -= 15
        if ((uv ?: 0.0) >= 8.0) score -= 10
        val clamped = score.coerceIn(0, 100)
        return when {
            clamped >= 78 -> "Ideal ($clamped/100)" to "Favorable comfort, air quality & precipitation outlook."
            clamped >= 55 -> "Fair ($clamped/100)" to "Workable outdoors — check heat, wind, or shower windows."
            else -> "Challenging ($clamped/100)" to "Elevated weather or AQI stress — plan indoor alternatives."
        }
    }

    fun formatDegreesOffset(delta: Double): String {
        val sign = if (delta >= 0) "+" else ""
        return "$sign${String.format(Locale.US, "%.1f", delta)}°C"
    }

    fun absSpreadDescription(spreadC: Double): String = when {
        abs(spreadC) < 1.2 -> "High Consensus"
        abs(spreadC) < 2.5 -> "Moderate Spread"
        else -> "Divergent Models"
    }
}
