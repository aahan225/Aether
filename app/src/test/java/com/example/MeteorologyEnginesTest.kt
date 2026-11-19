package com.example

import com.example.domain.engine.ForecastVerificationEngine
import com.example.domain.engine.MeteorologyCalculator
import com.example.domain.engine.WeatherReconciliationEngine
import com.example.domain.model.ModelComparisonPoint
import com.example.domain.model.PrecipitationUnit
import com.example.domain.model.PressureUnit
import com.example.domain.model.TemperatureUnit
import com.example.domain.model.WindUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeteorologyEnginesTest {

    @Test
    fun indiaCpcbAqi_computesCorrectSubIndexAndDominantPollutant() {
        // PM2.5 = 60 µg/m³ sits at upper bound of Satisfactory (AQI 100)
        val (aqi, dominant) = MeteorologyCalculator.computeIndiaCpcbAqi(
            pm25 = 60.0,
            pm10 = 40.0,
            no2 = 20.0,
            o3 = 30.0,
            so2 = 10.0,
            coUgM3 = 500.0
        )
        assertNotNull(aqi)
        assertEquals(100, aqi)
        assertEquals("PM2.5", dominant)
    }

    @Test
    fun unitFormatting_convertsTemperatureWindPrecipPressureAccurately() {
        assertEquals("32°F", MeteorologyCalculator.formatTemp(0.0, TemperatureUnit.FAHRENHEIT, true))
        assertEquals("212°F", MeteorologyCalculator.formatTemp(100.0, TemperatureUnit.FAHRENHEIT, true))
        assertEquals("10.0 m/s", MeteorologyCalculator.formatWind(36.0, WindUnit.MS))
        assertEquals("1.00 in", MeteorologyCalculator.formatPrecipitation(25.4, PrecipitationUnit.INCHES))
        assertEquals("29.92 inHg", MeteorologyCalculator.formatPressure(1013.25, PressureUnit.INHG))
    }

    @Test
    fun forecastVerification_computesMaeRmseBiasPodFarCsiBrier() {
        val obsTemp = listOf(28.0, 30.0, 32.0, 29.0)
        val fcTemp = listOf(29.0, 31.0, 31.0, 30.0) // errors: +1, +1, -1, +1 -> MAE=1.0, RMSE=1.0, Bias=+0.5
        val obsRain = listOf(0.0, 2.0, 0.0, 3.0)
        val fcRain = listOf(0.0, 2.0, 1.0, 0.0) // 1 hit, 1 miss, 1 false alarm, 1 CN

        val metrics = ForecastVerificationEngine.computeMetrics(
            modelId = "ecmwf",
            modelName = "ECMWF IFS",
            observedTemp = obsTemp,
            forecastTemp = fcTemp,
            observedPrecip = obsRain,
            forecastPrecip = fcRain
        )
        assertNotNull(metrics)
        assertEquals(1.0, metrics!!.tempMaeC, 0.001)
        assertEquals(1.0, metrics.tempRmseC, 0.001)
        assertEquals(0.5, metrics.tempBiasC, 0.001)
        assertEquals(50.0, metrics.precipAccuracyPct, 0.01)
        assertEquals(0.5, metrics.pod, 0.001)
        assertEquals(0.5, metrics.far, 0.001)
        assertEquals(1.0 / 3.0, metrics.csi, 0.001)
    }

    @Test
    fun reconciliation_flagsSignificantTemperatureDisagreement() {
        val models = listOf(
            ModelComparisonPoint("ecmwf", "ECMWF", "NWP", "25km", true, "Live", 30.0, 34.0, 24.0, 1.0, 15.0, "00Z"),
            ModelComparisonPoint("gfs", "GFS", "NWP", "25km", true, "Live", 28.0, 30.5, 23.0, 10.0, 18.0, "00Z")
        )
        val flags = WeatherReconciliationEngine.analyzeModelDisagreements(models)
        assertTrue(flags.any { it.contains("Temperature Disagreement") })
        assertTrue(flags.any { it.contains("Precipitation Divergence") })
    }

    @Test
    fun weatherNext3Schema_deserializesMoshiPayloadAndComputesUvWindVector() {
        val sampleJson = """
            {
              "model_version": "weathernext-3",
              "init_time_utc": "2026-11-19T00:00:00Z",
              "spatial_resolution_deg": 0.1,
              "ensemble_member_count": 64,
              "latitude": 22.5726,
              "longitude": 88.3639,
              "current": {
                "valid_time_utc": "2026-11-19T06:00:00Z",
                "temperature_2m_c": 29.4,
                "relative_humidity_2m_pct": 68.0,
                "wind_velocity": {
                  "u_component_ms": 3.0,
                  "v_component_ms": 4.0
                },
                "mean_sea_level_pressure_hpa": 1011.8
              }
            }
        """.trimIndent()

        val adapter = com.example.data.remote.WeatherApiProvider.moshi.adapter(
            com.example.data.model.WeatherResponse::class.java
        )
        val parsed = adapter.fromJson(sampleJson)
        assertNotNull(parsed)
        val nonNullParsed = requireNotNull(parsed)
        assertEquals(64, nonNullParsed.ensembleMemberCount)
        val current = requireNotNull(nonNullParsed.current)
        assertEquals(29.4, requireNotNull(current.temperature2mC), 0.001)
        assertEquals(68.0, requireNotNull(current.relativeHumidity2mPct), 0.001)
        // hypot(3, 4) = 5 m/s -> 18.0 km/h
        assertEquals(18.0, requireNotNull(current.resolvedWindSpeedKmh()), 0.01)
        assertNotNull(current.resolvedWindDirectionDeg())
    }

    @Test
    fun googleWeatherApiResponse_deserializesMoshiPayloadAndConvertsUnits() {
        val sampleGoogleJson = """
            {
              "currentTime": "2026-11-19T06:00:00Z",
              "isDaytime": true,
              "weatherCondition": {
                "type": "PARTLY_CLOUDY",
                "description": { "text": "Partly Cloudy", "languageCode": "en" }
              },
              "temperature": { "degrees": 86.0, "unit": "FAHRENHEIT" },
              "relativeHumidity": 62,
              "wind": {
                "speed": { "value": 10.0, "unit": "METERS_PER_SECOND" },
                "direction": { "degrees": 210, "cardinal": "SOUTH_SOUTHWEST" }
              },
              "airPressure": { "meanSeaLevelMillibars": 1012.4 }
            }
        """.trimIndent()

        val adapter = com.example.data.remote.WeatherApiProvider.moshi.adapter(
            com.example.data.model.GoogleWeatherCurrentResponse::class.java
        )
        val parsed = requireNotNull(adapter.fromJson(sampleGoogleJson))
        assertEquals(30.0, requireNotNull(parsed.temperature?.toCelsius()), 0.01)
        assertEquals(62, parsed.relativeHumidity)
        assertEquals(36.0, requireNotNull(parsed.wind?.speed?.toKmh()), 0.01)
        assertEquals(1012.4, requireNotNull(parsed.airPressure?.meanSeaLevelMillibars), 0.01)
        assertEquals("Partly Cloudy", parsed.weatherCondition?.description?.text)
    }

    @Test
    fun weatherAuthInterceptor_rejectsPlaceholderSecretsAndAcceptsRealTokens() {
        assertEquals(
            false,
            com.example.data.remote.WeatherAuthInterceptor.isConfiguredSecret(
                "YOUR_GOOGLE_WEATHER_API_KEY",
                "YOUR_GOOGLE_WEATHER_API_KEY"
            )
        )
        assertEquals(
            false,
            com.example.data.remote.WeatherAuthInterceptor.isConfiguredSecret(
                "",
                "YOUR_WEATHERNEXT_AUTH_TOKEN"
            )
        )
        assertEquals(
            true,
            com.example.data.remote.WeatherAuthInterceptor.isConfiguredSecret(
                "AIzaSyTestLiveConfiguredToken123",
                "YOUR_GOOGLE_WEATHER_API_KEY"
            )
        )
    }

    @Test
    fun openMeteoModelParser_extractsSparseSixHourlyAiModelWithLeadingNulls() {
        // Simulates GraphCast / AIFS returning nulls for past local hours before latest synoptic run
        val leadingNulls = List(26) { "null" }.joinToString(",")
        val sampleJson = """
            {
              "hourly": {
                "temperature_2m": [$leadingNulls, 24.5, null, null, null, null, null, 28.0, null, null, null, null, null, 31.2, null, null, null, null, null, 26.4],
                "precipitation": [$leadingNulls, 0.0, null, null, null, null, null, 1.5, null, null, null, null, null, 2.0, null, null, null, null, null, 0.0],
                "wind_speed_10m": [$leadingNulls, 11.0, null, null, null, null, null, 18.5, null, null, null, null, null, 14.2, null, null, null, null, null, 9.8]
              }
            }
        """.trimIndent()

        val summary = com.example.data.network.WeatherNetworkClient.parseOpenMeteoModelHourlyJson(
            body = sampleJson,
            resolvedModelId = "gfs_graphcast025"
        )
        assertNotNull(summary)
        val s = requireNotNull(summary)
        assertEquals(24.5, s.currentTempC, 0.01)
        assertEquals(31.2, s.next24hMaxC, 0.01)
        assertEquals(24.5, s.next24hMinC, 0.01)
        assertEquals(3.5, requireNotNull(s.next24hPrecipMm), 0.01)
        assertEquals(18.5, requireNotNull(s.maxWindKmh), 0.01)
    }
}
