package com.example.ai

import android.util.Log
import com.example.domain.engine.MeteorologyCalculator
import com.example.domain.model.UserPreferences
import com.example.domain.model.WeatherBundle
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.util.Locale

private const val TAG = "WeatherAiRepository"

class WeatherAiRepository {

    // Lazy initialization per Firebase AI Logic skill guidelines (never eager at construction)
    private val primaryModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val fallbackModel by lazy {
        Firebase.ai.generativeModel("gemini-3.5-flash-lite")
    }

    private val systemDirectives = """
        You are Aether, an elite Meteorological Intelligence Assistant inside a native Android weather application.
        STRICT SCIENTIFIC RULES:
        1. Answer ONLY using the LIVE METEOROLOGICAL TELEMETRY PACKET provided below. Never invent, extrapolate, or hallucinate forecast values.
        2. Always include the exact location name and local/UTC reference timestamp in your response.
        3. Distinguish transparently between official government warnings (IMD / NOAA NWS), ensemble/multi-model forecasts (Google DeepMind GraphCast, ECMWF AIFS, ECMWF IFS, NOAA GFS, DWD ICON), and Google DeepMind WeatherNext 3 status. Never claim a forecast came from WeatherNext 3 unless the packet explicitly states WeatherNext 3 is available=true.
        4. Be concise, practical, and structured (short paragraphs or crisp bullet points) for mobile reading. Give concrete timing windows (e.g., "between 5 PM and 8 PM") whenever supported by the hourly array.
        5. If the user asks about comparing saved cities, compare ONLY the saved cities present in the telemetry packet and note if any city hasn't been synced yet.
    """.trimIndent()

    fun streamWeatherAnswer(
        question: String,
        activeBundle: WeatherBundle?,
        savedCityBundles: Map<String, WeatherBundle>,
        preferences: UserPreferences
    ): Flow<String> = flow {
        val contextPacket = buildLiveContextPacket(activeBundle, savedCityBundles, preferences)
        val fullPrompt = """
            $systemDirectives

            $contextPacket

            USER QUESTION:
            $question
        """.trimIndent()

        // 1. Primary streaming attempt on gemini-3.8-flash
        val primaryError = tryStreamWithModel(primaryModel, fullPrompt) { emit(it) }
        if (primaryError == null) return@flow

        // 2. Primary unary fallback on gemini-3.8-flash (recovers from SSE stream hiccups)
        val unaryPrimaryResult = runCatchingUnary(primaryModel, fullPrompt)
        if (unaryPrimaryResult.isSuccess) {
            emit(unaryPrimaryResult.getOrThrow())
            return@flow
        }

        // 3. Secondary supported model fallback on gemini-3.5-flash-lite
        Log.w(TAG, "Primary gemini-3.8-flash failed (${primaryError.message}); trying gemini-3.5-flash-lite")
        val fallbackStreamError = tryStreamWithModel(fallbackModel, fullPrompt) { emit(it) }
        if (fallbackStreamError == null) return@flow

        val unaryFallbackResult = runCatchingUnary(fallbackModel, fullPrompt)
        if (unaryFallbackResult.isSuccess) {
            emit(unaryFallbackResult.getOrThrow())
            return@flow
        }

        // Surface authentic error transparently (never swallow or mock AI answers)
        val rootError = unaryFallbackResult.exceptionOrNull() ?: fallbackStreamError
        Log.e(TAG, "All Firebase AI Logic inference attempts failed", rootError)
        throw IllegalStateException(
            buildActionableErrorMessage(primaryError, rootError),
            rootError
        )
    }

    private suspend fun tryStreamWithModel(
        model: com.google.firebase.ai.GenerativeModel,
        prompt: String,
        onChunkAccumulated: suspend (String) -> Unit
    ): Throwable? {
        val accumulated = StringBuilder()
        return try {
            withTimeout(60_000L) {
                model.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        onChunkAccumulated(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                IllegalStateException("Model returned an empty response.")
            } else {
                null
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI weather assistant stream timed out", e)
            IllegalStateException("Response timed out after 60 seconds. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI weather assistant stream failed", e)
            e
        }
    }

    private suspend fun runCatchingUnary(
        model: com.google.firebase.ai.GenerativeModel,
        prompt: String
    ): Result<String> {
        return try {
            val response = withTimeout(45_000L) {
                model.generateContent(prompt)
            }
            val text = response.text?.trim().orEmpty()
            if (text.isNotBlank()) {
                Result.success(text)
            } else {
                Result.failure(IllegalStateException("Model returned an empty response."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI weather assistant unary timed out", e)
            Result.failure(IllegalStateException("Response timed out. Please retry.", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI weather assistant unary failed", e)
            Result.failure(e)
        }
    }

    private fun buildActionableErrorMessage(primary: Throwable, finalErr: Throwable): String {
        val raw = (primary.localizedMessage ?: finalErr.localizedMessage ?: finalErr.javaClass.simpleName).trim()
        return when {
            raw.contains("403") || raw.contains("App Check", ignoreCase = true) || raw.contains("attestation", ignoreCase = true) ->
                "Firebase App Check verification failed (HTTP 403). Please reopen the app or tap Retry. ($raw)"
            raw.contains("timeout", ignoreCase = true) ->
                "AI request timed out while waiting for Gemini. Check your connection and tap Retry."
            else ->
                "Unable to complete AI weather query: $raw"
        }
    }

    private fun buildLiveContextPacket(
        active: WeatherBundle?,
        savedBundles: Map<String, WeatherBundle>,
        prefs: UserPreferences
    ): String {
        val sb = StringBuilder()
        sb.appendLine("=== LIVE METEOROLOGICAL TELEMETRY PACKET ===")
        sb.appendLine("Current UTC Time: ${Instant.now()}")
        sb.appendLine("User Units: ${MeteorologyCalculator.formatPreferenceSummary(prefs)}")

        if (active == null) {
            sb.appendLine("Active Location Weather: NOT YET LOADED")
            return sb.toString()
        }

        val c = active.current
        val localNow = MeteorologyCalculator.formatEpochInOffset(
            active.fetchedAtUtcMillis,
            active.utcOffsetSeconds,
            prefs.use24HourTime
        )
        sb.appendLine("Active Location: ${active.location.name}, ${active.location.adminRegion}, ${active.location.country}")
        sb.appendLine("Coordinates: ${String.format(Locale.US, "%.4f, %.4f", active.location.latitude, active.location.longitude)} | TZ: ${active.location.timezone} (${active.timezoneAbbreviation})")
        sb.appendLine("Data Timestamp (Local): $localNow | Source Cache: ${active.isFromCache} | Primary Engine: ${active.primaryEngineLabel}")
        sb.appendLine(
            "Current: ${MeteorologyCalculator.formatTemp(c.temperatureC, prefs.tempUnit, true)}, " +
                "Feels Like ${MeteorologyCalculator.formatTemp(c.apparentTemperatureC, prefs.tempUnit, true)}, " +
                "Condition=${MeteorologyCalculator.wmoDescription(c.weatherCode)}, " +
                "Humidity=${c.relativeHumidity}%, DewPoint=${MeteorologyCalculator.formatTemp(c.dewPointC, prefs.tempUnit, true)}, " +
                "Wind=${MeteorologyCalculator.formatWind(c.windSpeedKmh, prefs.windUnit)} ${MeteorologyCalculator.windCardinal(c.windDirectionDeg)} " +
                "(Gusts ${MeteorologyCalculator.formatWind(c.windGustsKmh, prefs.windUnit)}), " +
                "Pressure=${MeteorologyCalculator.formatPressure(c.pressureMslHpa, prefs.pressureUnit)}, " +
                "Cloud=${c.cloudCoverPct}%, UV=${c.uvIndex ?: "N/A"}, CAPE=${c.capeJKg?.toInt() ?: "N/A"} J/kg"
        )

        active.airQuality?.let { aq ->
            sb.appendLine(
                "Air Quality (${prefs.aqiStandard.label}): India CPCB AQI=${aq.indiaCpcbAqi ?: "N/A"}, " +
                    "US EPA AQI=${aq.usAqi ?: "N/A"}, Dominant=${aq.dominantPollutant}, " +
                    "PM2.5=${aq.pm25 ?: "N/A"} µg/m³, PM10=${aq.pm10 ?: "N/A"} µg/m³, ObsTime=${aq.observationTimeIso}"
            )
        }

        active.ensemble?.let { ens ->
            sb.appendLine(
                "Ensemble (${ens.modelName}, ${ens.memberCount} members): " +
                    "24h Spread=±${String.format(Locale.US, "%.1f", ens.next24hTempSpreadC)}°C (${ens.confidenceLabel}), " +
                    "Derived P(Rain)=${ens.precipProbabilityDerivedPct}%, P(T>35°C)=${ens.probExceed35CPct}%"
            )
        }

        sb.appendLine("Next 18 Hours:")
        active.hourly.take(18).forEach { h ->
            val tLabel = MeteorologyCalculator.formatHourLabel(h.timeIso, prefs.use24HourTime)
            sb.appendLine(
                "  $tLabel: ${MeteorologyCalculator.formatTemp(h.temperatureC, prefs.tempUnit, true)}, " +
                    "${MeteorologyCalculator.wmoDescription(h.weatherCode)}, " +
                    "RainProb=${h.precipitationProbability ?: "N/A"}%, Precip=${MeteorologyCalculator.formatPrecipitation(h.precipitationMm, prefs.precipUnit)}, " +
                    "Wind=${MeteorologyCalculator.formatWind(h.windSpeedKmh, prefs.windUnit)}, CAPE=${h.capeJKg?.toInt() ?: 0}"
            )
        }

        sb.appendLine("Next 7 Days:")
        active.daily.take(7).forEach { d ->
            sb.appendLine(
                "  ${d.dateIso} (${d.horizon.label}): High ${MeteorologyCalculator.formatTemp(d.tempMaxC, prefs.tempUnit, true)} / " +
                    "Low ${MeteorologyCalculator.formatTemp(d.tempMinC, prefs.tempUnit, true)}, " +
                    "${MeteorologyCalculator.wmoDescription(d.weatherCode)}, " +
                    "RainProb=${d.precipitationProbabilityMax ?: "N/A"}%, Sum=${MeteorologyCalculator.formatPrecipitation(d.precipitationSumMm, prefs.precipUnit)}"
            )
        }

        if (active.alerts.isNotEmpty()) {
            sb.appendLine("Active Alerts:")
            active.alerts.take(5).forEach { a ->
                sb.appendLine("  [${a.provenance.label} | ${a.severity.label}] ${a.title} (${a.issuingAuthority}): ${a.description.take(160)}")
            }
        } else {
            sb.appendLine("Active Alerts: None")
        }

        sb.appendLine("Multi-Model Status:")
        active.modelComparisons.forEach { m ->
            sb.appendLine(
                "  ${m.displayName}: available=${m.available}, 24hMax=${m.next24hMaxC ?: "N/A"}°C, " +
                    "24hPrecip=${m.next24hPrecipMm ?: "N/A"}mm, note=${m.statusNote}"
            )
        }

        if (active.modelDisagreements.isNotEmpty()) {
            sb.appendLine("Flagged Model Disagreements: ${active.modelDisagreements.joinToString("; ")}")
        }

        if (savedBundles.isNotEmpty()) {
            sb.appendLine("Synced Saved Locations Snapshot:")
            savedBundles.values.forEach { b ->
                sb.appendLine(
                    "  ${b.location.name} (${b.location.country}): " +
                        "${MeteorologyCalculator.formatTemp(b.current.temperatureC, prefs.tempUnit, true)}, " +
                        "${MeteorologyCalculator.wmoDescription(b.current.weatherCode)}, " +
                        "Rain24h=${MeteorologyCalculator.formatPrecipitation(b.daily.firstOrNull()?.precipitationSumMm ?: 0.0, prefs.precipUnit)}, " +
                        "IndiaAQI=${b.airQuality?.indiaCpcbAqi ?: "N/A"}"
                )
            }
        }

        return sb.toString()
    }
}
