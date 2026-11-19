package com.example.domain.engine

import com.example.domain.model.AlertProvenance
import com.example.domain.model.AlertSeverity
import com.example.domain.model.CurrentConditions
import com.example.domain.model.DailyPoint
import com.example.domain.model.EnsembleSummary
import com.example.domain.model.HourlyPoint
import com.example.domain.model.ModelComparisonPoint
import com.example.domain.model.WeatherAlert
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

object WeatherReconciliationEngine {

    /**
     * Generates strictly data-grounded natural-language summaries derived directly from
     * hourly and daily forecast arrays. Never invents meteorological events.
     */
    fun generateDataGroundedSummaries(
        current: CurrentConditions,
        hourly: List<HourlyPoint>,
        daily: List<DailyPoint>,
        ensemble: EnsembleSummary?,
        use24Hour: Boolean
    ): List<String> {
        val bullets = mutableListOf<String>()
        val next24 = hourly.take(24)

        // 1. Precipitation onset & peak window
        val rainHours = next24.filter { (it.precipitationProbability ?: 0) >= 40 || it.precipitationMm >= 0.3 }
        if (rainHours.isNotEmpty()) {
            val firstRain = rainHours.first()
            val peakRain = rainHours.maxBy { max(it.precipitationMm, (it.precipitationProbability ?: 0) / 100.0) }
            val totalRain = next24.sumOf { it.precipitationMm }
            val startLabel = MeteorologyCalculator.formatHourLabel(firstRain.timeIso, use24Hour)
            val peakProb = peakRain.precipitationProbability
            val probText = if (peakProb != null) "up to $peakProb% probability" else "measurable rainfall"
            bullets += String.format(
                Locale.US,
                "Precipitation window opens around %s (%s, %.1f mm expected over 24h).",
                startLabel,
                probText,
                totalRain
            )
        } else {
            val maxCloud = next24.maxOfOrNull { it.cloudCoverPct } ?: current.cloudCoverPct
            val minCloud = next24.minOfOrNull { it.cloudCoverPct } ?: current.cloudCoverPct
            bullets += when {
                maxCloud < 25 -> "Predominantly clear skies expected across the next 24 hours with dry conditions."
                maxCloud - minCloud >= 40 -> "Variable cloud cover ($minCloud%–$maxCloud%) expected over the next 24 hours; negligible rain risk."
                else -> "Dry conditions expected over the next 24 hours with cloud cover near ${current.cloudCoverPct}%."
            }
        }

        // 2. Temperature evolution (sunset / evening drop or afternoon peak)
        if (next24.size >= 8) {
            val maxPt = next24.maxBy { it.temperatureC }
            val minPt = next24.minBy { it.temperatureC }
            val eveningDrop = maxPt.temperatureC - minPt.temperatureC
            val maxTime = MeteorologyCalculator.formatHourLabel(maxPt.timeIso, use24Hour)
            val minTime = MeteorologyCalculator.formatHourLabel(minPt.timeIso, use24Hour)
            if (eveningDrop >= 4.0) {
                bullets += String.format(
                    Locale.US,
                    "Temperatures peak near %.0f°C around %s before cooling by %.1f°C to %.0f°C near %s.",
                    maxPt.temperatureC,
                    maxTime,
                    eveningDrop,
                    minPt.temperatureC,
                    minTime
                )
            } else {
                bullets += String.format(
                    Locale.US,
                    "Steady thermal profile over 24h between %.0f°C and %.0f°C (feels like %.0f°C now).",
                    minPt.temperatureC,
                    maxPt.temperatureC,
                    current.apparentTemperatureC
                )
            }
        }

        // 3. Wind & thunderstorm convective indicator (CAPE)
        val peakGustPt = next24.maxByOrNull { it.windGustsKmh }
        val peakCapePt = next24.maxByOrNull { it.capeJKg ?: 0.0 }
        if (peakCapePt != null && (peakCapePt.capeJKg ?: 0.0) >= 1200.0) {
            val capeTime = MeteorologyCalculator.formatHourLabel(peakCapePt.timeIso, use24Hour)
            bullets += String.format(
                Locale.US,
                "Elevated convective energy (CAPE %.0f J/kg) near %s increases localized thunderstorm potential.",
                peakCapePt.capeJKg ?: 0.0,
                capeTime
            )
        } else if (peakGustPt != null && peakGustPt.windGustsKmh >= 35.0) {
            val gustTime = MeteorologyCalculator.formatHourLabel(peakGustPt.timeIso, use24Hour)
            bullets += String.format(
                Locale.US,
                "Breezy conditions ahead with gusts reaching %.0f km/h near %s from the %s.",
                peakGustPt.windGustsKmh,
                gustTime,
                MeteorologyCalculator.windCardinal(peakGustPt.windDirectionDeg)
            )
        } else if (ensemble != null) {
            bullets += String.format(
                Locale.US,
                "%d-member ensemble spread is ±%.1f°C over 24h (%s).",
                ensemble.memberCount,
                ensemble.next24hTempSpreadC,
                ensemble.confidenceLabel
            )
        } else if (daily.size >= 3) {
            val day3Max = daily[2].tempMaxC
            val todayMax = daily[0].tempMaxC
            val trend = day3Max - todayMax
            if (abs(trend) >= 2.0) {
                val direction = if (trend > 0) "warming" else "cooling"
                bullets += String.format(
                    Locale.US,
                    "72-hour outlook indicates a %s trend of %.1f°C compared with today's high.",
                    direction,
                    abs(trend)
                )
            }
        }

        return bullets.take(3)
    }

    /**
     * Identifies scientifically meaningful inter-model disagreements across equivalent timestamps.
     */
    fun analyzeModelDisagreements(models: List<ModelComparisonPoint>): List<String> {
        val active = models.filter { it.available && it.next24hMaxC != null }
        if (active.size < 2) return emptyList()

        val flags = mutableListOf<String>()

        val maxTemps = active.mapNotNull { m -> m.next24hMaxC?.let { m.displayName to it } }
        if (maxTemps.size >= 2) {
            val highest = maxTemps.maxBy { it.second }
            val lowest = maxTemps.minBy { it.second }
            val spread = highest.second - lowest.second
            if (spread >= 2.2) {
                flags += String.format(
                    Locale.US,
                    "24h High Temperature Disagreement (%.1f°C spread): %s predicts %.1f°C vs %s at %.1f°C.",
                    spread,
                    highest.first,
                    highest.second,
                    lowest.first,
                    lowest.second
                )
            }
        }

        val precips = active.mapNotNull { m -> m.next24hPrecipMm?.let { m.displayName to it } }
        if (precips.size >= 2) {
            val wettest = precips.maxBy { it.second }
            val driest = precips.minBy { it.second }
            val diff = wettest.second - driest.second
            if (diff >= 5.0) {
                flags += String.format(
                    Locale.US,
                    "Precipitation Divergence (%.1f mm spread): %s projects %.1f mm vs %s at %.1f mm.",
                    diff,
                    wettest.first,
                    wettest.second,
                    driest.first,
                    driest.second
                )
            }
        }

        val winds = active.mapNotNull { m -> m.maxWindKmh?.let { m.displayName to it } }
        if (winds.size >= 2) {
            val windiest = winds.maxBy { it.second }
            val calmest = winds.minBy { it.second }
            val diff = windiest.second - calmest.second
            if (diff >= 15.0) {
                flags += String.format(
                    Locale.US,
                    "Wind Speed Spread (%.0f km/h): %s peaks at %.0f km/h vs %s at %.0f km/h.",
                    diff,
                    windiest.first,
                    windiest.second,
                    calmest.first,
                    calmest.second
                )
            }
        }

        return flags
    }

    /**
     * Derives transparent, strictly labeled MODEL-DERIVED risk assessments (never impersonating
     * official meteorological agency alerts like IMD or NOAA NWS).
     */
    fun deriveModelRiskIndicators(
        locationName: String,
        current: CurrentConditions,
        hourly: List<HourlyPoint>,
        daily: List<DailyPoint>
    ): List<WeatherAlert> {
        val risks = mutableListOf<WeatherAlert>()
        val nowIso = Instant.now().toString()
        val next24 = hourly.take(24)

        val maxCape = next24.mapNotNull { it.capeJKg }.maxOrNull() ?: 0.0
        val hasThunderCode = next24.any { it.weatherCode in listOf(95, 96, 99) }
        if (hasThunderCode || maxCape >= 1500.0) {
            risks += WeatherAlert(
                id = "model_risk_tstorm_$locationName",
                title = "Convective Thunderstorm Potential",
                eventType = "Thunderstorm Risk",
                severity = if (maxCape >= 2500.0) AlertSeverity.SEVERE else AlertSeverity.MODERATE,
                provenance = AlertProvenance.MODEL_RISK_INDICATOR,
                issuingAuthority = "Aether NWP Risk Engine (Not an official agency warning)",
                affectedArea = locationName,
                validFrom = next24.firstOrNull()?.timeIso ?: nowIso,
                validUntil = next24.lastOrNull()?.timeIso ?: nowIso,
                description = String.format(
                    Locale.US,
                    "Forecast soundings show convective available potential energy (CAPE) up to %.0f J/kg%s.",
                    maxCape,
                    if (hasThunderCode) " with active thunderstorm weather codes in the next 24h" else ""
                ),
                precautions = listOf(
                    "Monitor live observed radar for rapidly developing storm cells.",
                    "Avoid open fields, tall isolated trees, and exposed water during lightning."
                ),
                updatedUtcIso = nowIso
            )
        }

        val rain24h = next24.sumOf { it.precipitationMm }
        if (rain24h >= 35.0) {
            risks += WeatherAlert(
                id = "model_risk_heavy_rain_$locationName",
                title = "Heavy Rainfall Accumulation Signal",
                eventType = "Heavy Rainfall Risk",
                severity = if (rain24h >= 65.0) AlertSeverity.SEVERE else AlertSeverity.MODERATE,
                provenance = AlertProvenance.MODEL_RISK_INDICATOR,
                issuingAuthority = "Aether NWP Risk Engine (Not an official agency warning)",
                affectedArea = locationName,
                validFrom = next24.firstOrNull()?.timeIso ?: nowIso,
                validUntil = next24.lastOrNull()?.timeIso ?: nowIso,
                description = String.format(
                    Locale.US,
                    "Multi-model hourly accumulation projects %.1f mm of rainfall over the next 24 hours, which may cause localized waterlogging.",
                    rain24h
                ),
                precautions = listOf(
                    "Allow extra travel time and avoid flooded underpasses.",
                    "Check official IMD / local civil defense bulletins for flood advisories."
                ),
                updatedUtcIso = nowIso
            )
        }

        val maxApparent = next24.maxOfOrNull { it.apparentTemperatureC } ?: current.apparentTemperatureC
        val maxActual = next24.maxOfOrNull { it.temperatureC } ?: current.temperatureC
        if (maxActual >= 40.0 || maxApparent >= 42.0) {
            risks += WeatherAlert(
                id = "model_risk_heat_$locationName",
                title = "Elevated Heat Stress Signal",
                eventType = "Heat Stress Risk",
                severity = if (maxActual >= 43.0 || maxApparent >= 46.0) AlertSeverity.SEVERE else AlertSeverity.MODERATE,
                provenance = AlertProvenance.MODEL_RISK_INDICATOR,
                issuingAuthority = "Aether NWP Risk Engine (Not an official agency warning)",
                affectedArea = locationName,
                validFrom = next24.firstOrNull()?.timeIso ?: nowIso,
                validUntil = next24.lastOrNull()?.timeIso ?: nowIso,
                description = String.format(
                    Locale.US,
                    "Forecast air temperature reaches %.1f°C with heat index / apparent temperature up to %.1f°C.",
                    maxActual,
                    maxApparent
                ),
                precautions = listOf(
                    "Hydrate regularly and limit strenuous outdoor activity between 11 AM and 4 PM.",
                    "Wear lightweight breathable clothing and check on vulnerable individuals."
                ),
                updatedUtcIso = nowIso
            )
        }

        val maxGust = next24.maxOfOrNull { it.windGustsKmh } ?: current.windGustsKmh
        if (maxGust >= 55.0) {
            risks += WeatherAlert(
                id = "model_risk_wind_$locationName",
                title = "Strong Wind Gust Signal",
                eventType = "High Wind Risk",
                severity = if (maxGust >= 75.0) AlertSeverity.SEVERE else AlertSeverity.MODERATE,
                provenance = AlertProvenance.MODEL_RISK_INDICATOR,
                issuingAuthority = "Aether NWP Risk Engine (Not an official agency warning)",
                affectedArea = locationName,
                validFrom = next24.firstOrNull()?.timeIso ?: nowIso,
                validUntil = next24.lastOrNull()?.timeIso ?: nowIso,
                description = String.format(
                    Locale.US,
                    "Forecast wind gusts reach %.0f km/h over the next 24 hours.",
                    maxGust
                ),
                precautions = listOf(
                    "Secure loose balcony or outdoor items.",
                    "Exercise caution when driving high-profile vehicles on exposed corridors."
                ),
                updatedUtcIso = nowIso
            )
        }

        val minVis = next24.mapNotNull { it.visibilityMeters }.minOrNull()
        if (minVis != null && minVis <= 500.0) {
            risks += WeatherAlert(
                id = "model_risk_fog_$locationName",
                title = "Dense Fog Visibility Reduction",
                eventType = "Dense Fog Risk",
                severity = AlertSeverity.MODERATE,
                provenance = AlertProvenance.MODEL_RISK_INDICATOR,
                issuingAuthority = "Aether NWP Risk Engine (Not an official agency warning)",
                affectedArea = locationName,
                validFrom = next24.firstOrNull()?.timeIso ?: nowIso,
                validUntil = next24.lastOrNull()?.timeIso ?: nowIso,
                description = String.format(
                    Locale.US,
                    "Surface visibility is forecast to drop near %.0f metres during overnight/early morning hours.",
                    minVis
                ),
                precautions = listOf(
                    "Use low-beam fog lights and increase following distance on highways.",
                    "Anticipate potential aviation or rail schedule delays."
                ),
                updatedUtcIso = nowIso
            )
        }

        val maxUv = daily.firstOrNull()?.uvIndexMax ?: current.uvIndex ?: 0.0
        if (maxUv >= 9.0) {
            risks += WeatherAlert(
                id = "model_risk_uv_$locationName",
                title = "Very High to Extreme Solar UV",
                eventType = "UV Advisory",
                severity = AlertSeverity.MINOR,
                provenance = AlertProvenance.MODEL_RISK_INDICATOR,
                issuingAuthority = "Aether NWP Risk Engine (Not an official agency warning)",
                affectedArea = locationName,
                validFrom = daily.firstOrNull()?.sunriseIso ?: nowIso,
                validUntil = daily.firstOrNull()?.sunsetIso ?: nowIso,
                description = String.format(Locale.US, "Peak midday UV index reaches %.1f.", maxUv),
                precautions = listOf(
                    "Apply broad-spectrum SPF 30+ sunscreen and wear UV-blocking eyewear."
                ),
                updatedUtcIso = nowIso
            )
        }

        return risks.sortedByDescending { it.severity.rank }
    }

    fun computeEnsembleStats(
        modelName: String,
        memberTempSeries: List<List<Double>>,
        memberPrecipSeries: List<List<Double>>
    ): EnsembleSummary? {
        if (memberTempSeries.size < 2) return null
        val horizon = memberTempSeries.minOf { it.size }.coerceAtMost(24)
        if (horizon == 0) return null

        val spreadPerHour = mutableListOf<Double>()
        var sumMean = 0.0
        var globalMin = Double.POSITIVE_INFINITY
        var globalMax = Double.NEGATIVE_INFINITY
        var exceed35Count = 0

        for (h in 0 until horizon) {
            val vals = memberTempSeries.map { it[h] }
            val mean = vals.average()
            val variance = vals.map { (it - mean) * (it - mean) }.average()
            val std = kotlin.math.sqrt(variance)
            spreadPerHour += (std * 10.0).roundToInt() / 10.0
            sumMean += mean
            globalMin = minOf(globalMin, vals.min())
            globalMax = maxOf(globalMax, vals.max())
        }

        for (member in memberTempSeries) {
            if (member.take(horizon).any { it >= 35.0 }) exceed35Count++
        }

        var rainMembers = 0
        for (member in memberPrecipSeries) {
            if (member.take(horizon).sum() >= 0.5) rainMembers++
        }

        val avgSpread = spreadPerHour.average()
        val probRain = if (memberPrecipSeries.isNotEmpty()) {
            (rainMembers.toDouble() / memberPrecipSeries.size * 100.0).roundToInt()
        } else 0
        val probExceed35 = (exceed35Count.toDouble() / memberTempSeries.size * 100.0).roundToInt()

        val confidenceLabel = when {
            avgSpread < 0.9 -> "High Statistical Consensus (±${String.format(Locale.US, "%.1f", avgSpread)}°C)"
            avgSpread < 1.8 -> "Moderate Ensemble Spread (±${String.format(Locale.US, "%.1f", avgSpread)}°C)"
            else -> "Elevated Forecast Uncertainty (±${String.format(Locale.US, "%.1f", avgSpread)}°C)"
        }

        return EnsembleSummary(
            modelName = modelName,
            memberCount = memberTempSeries.size,
            next24hTempMeanC = sumMean / horizon,
            next24hTempSpreadC = avgSpread,
            next24hTempMinC = globalMin,
            next24hTempMaxC = globalMax,
            precipProbabilityDerivedPct = probRain,
            probExceed35CPct = probExceed35,
            confidenceLabel = confidenceLabel,
            spreadSeriesC = spreadPerHour
        )
    }
}
