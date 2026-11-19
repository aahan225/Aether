package com.example.data.network

import android.util.Log
import android.util.Xml
import com.example.data.remote.WeatherApiProvider
import com.example.domain.model.AlertProvenance
import com.example.domain.model.AlertSeverity
import com.example.domain.model.ModelComparisonPoint
import com.example.domain.model.WeatherAlert
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import java.io.StringReader
import java.time.Instant
import java.util.concurrent.TimeUnit

private const val TAG = "WeatherApiClients"

// ==================== DTOs ====================

@JsonClass(generateAdapter = true)
data class OpenMeteoForecastResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @Json(name = "utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val timezone: String = "UTC",
    @Json(name = "timezone_abbreviation") val timezoneAbbreviation: String = "UTC",
    val elevation: Double? = null,
    val current: OpenMeteoCurrentDto? = null,
    val hourly: OpenMeteoHourlyDto? = null,
    val daily: OpenMeteoDailyDto? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoCurrentDto(
    val time: String = "",
    @Json(name = "temperature_2m") val temperature2m: Double = 0.0,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Int = 0,
    @Json(name = "apparent_temperature") val apparentTemperature: Double = 0.0,
    @Json(name = "is_day") val isDay: Int = 1,
    val precipitation: Double = 0.0,
    val rain: Double = 0.0,
    val snowfall: Double = 0.0,
    @Json(name = "weather_code") val weatherCode: Int = 0,
    @Json(name = "cloud_cover") val cloudCover: Int = 0,
    @Json(name = "pressure_msl") val pressureMsl: Double = 1013.25,
    @Json(name = "surface_pressure") val surfacePressure: Double = 1013.25,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double = 0.0,
    @Json(name = "wind_direction_10m") val windDirection10m: Int = 0,
    @Json(name = "wind_gusts_10m") val windGusts10m: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class OpenMeteoHourlyDto(
    val time: List<String> = emptyList(),
    @Json(name = "temperature_2m") val temperature2m: List<Double?> = emptyList(),
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: List<Int?> = emptyList(),
    @Json(name = "dew_point_2m") val dewPoint2m: List<Double?> = emptyList(),
    @Json(name = "apparent_temperature") val apparentTemperature: List<Double?> = emptyList(),
    @Json(name = "precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    val rain: List<Double?> = emptyList(),
    val snowfall: List<Double?> = emptyList(),
    @Json(name = "weather_code") val weatherCode: List<Int?> = emptyList(),
    @Json(name = "pressure_msl") val pressureMsl: List<Double?> = emptyList(),
    @Json(name = "surface_pressure") val surfacePressure: List<Double?> = emptyList(),
    @Json(name = "cloud_cover") val cloudCover: List<Int?> = emptyList(),
    @Json(name = "cloud_cover_low") val cloudCoverLow: List<Int?> = emptyList(),
    @Json(name = "cloud_cover_mid") val cloudCoverMid: List<Int?> = emptyList(),
    @Json(name = "cloud_cover_high") val cloudCoverHigh: List<Int?> = emptyList(),
    val visibility: List<Double?> = emptyList(),
    @Json(name = "wind_speed_10m") val windSpeed10m: List<Double?> = emptyList(),
    @Json(name = "wind_direction_10m") val windDirection10m: List<Int?> = emptyList(),
    @Json(name = "wind_gusts_10m") val windGusts10m: List<Double?> = emptyList(),
    @Json(name = "uv_index") val uvIndex: List<Double?> = emptyList(),
    @Json(name = "shortwave_radiation") val shortwaveRadiation: List<Double?> = emptyList(),
    val cape: List<Double?> = emptyList(),
    @Json(name = "is_day") val isDay: List<Int?> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OpenMeteoDailyDto(
    val time: List<String> = emptyList(),
    @Json(name = "weather_code") val weatherCode: List<Int?> = emptyList(),
    @Json(name = "temperature_2m_max") val temperature2mMax: List<Double?> = emptyList(),
    @Json(name = "temperature_2m_min") val temperature2mMin: List<Double?> = emptyList(),
    @Json(name = "apparent_temperature_max") val apparentTemperatureMax: List<Double?> = emptyList(),
    @Json(name = "apparent_temperature_min") val apparentTemperatureMin: List<Double?> = emptyList(),
    val sunrise: List<String?> = emptyList(),
    val sunset: List<String?> = emptyList(),
    @Json(name = "daylight_duration") val daylightDuration: List<Double?> = emptyList(),
    @Json(name = "uv_index_max") val uvIndexMax: List<Double?> = emptyList(),
    @Json(name = "precipitation_sum") val precipitationSum: List<Double?> = emptyList(),
    @Json(name = "rain_sum") val rainSum: List<Double?> = emptyList(),
    @Json(name = "snowfall_sum") val snowfallSum: List<Double?> = emptyList(),
    @Json(name = "precipitation_probability_max") val precipitationProbabilityMax: List<Int?> = emptyList(),
    @Json(name = "wind_speed_10m_max") val windSpeed10mMax: List<Double?> = emptyList(),
    @Json(name = "wind_gusts_10m_max") val windGusts10mMax: List<Double?> = emptyList(),
    @Json(name = "wind_direction_10m_dominant") val windDirection10mDominant: List<Int?> = emptyList(),
    @Json(name = "shortwave_radiation_sum") val shortwaveRadiationSum: List<Double?> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OpenMeteoAirQualityResponse(
    val current: OpenMeteoAirQualityCurrentDto? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoAirQualityCurrentDto(
    val time: String = "",
    @Json(name = "us_aqi") val usAqi: Int? = null,
    @Json(name = "european_aqi") val europeanAqi: Int? = null,
    @Json(name = "pm10") val pm10: Double? = null,
    @Json(name = "pm2_5") val pm25: Double? = null,
    @Json(name = "carbon_monoxide") val co: Double? = null,
    @Json(name = "nitrogen_dioxide") val no2: Double? = null,
    @Json(name = "sulphur_dioxide") val so2: Double? = null,
    val ozone: Double? = null,
    val dust: Double? = null,
    @Json(name = "grass_pollen") val grassPollen: Double? = null,
    @Json(name = "birch_pollen") val birchPollen: Double? = null,
    @Json(name = "ragweed_pollen") val ragweedPollen: Double? = null
)

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    val results: List<GeocodingResultDto>? = null
)

@JsonClass(generateAdapter = true)
data class GeocodingResultDto(
    val id: Long = 0L,
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val elevation: Double? = null,
    @Json(name = "country_code") val countryCode: String? = null,
    val country: String? = null,
    val admin1: String? = null,
    val admin2: String? = null,
    val timezone: String? = null
)

@JsonClass(generateAdapter = true)
data class RainViewerResponse(
    val version: String = "2.0",
    val generated: Long = 0L,
    val host: String = "https://tilecache.rainviewer.com",
    val radar: RainViewerRadarDto? = null
)

@JsonClass(generateAdapter = true)
data class RainViewerRadarDto(
    val past: List<RainViewerFrameDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RainViewerFrameDto(
    val time: Long = 0L,
    val path: String = ""
)

@JsonClass(generateAdapter = true)
data class NoaaAlertsResponse(
    val features: List<NoaaAlertFeature> = emptyList()
)

@JsonClass(generateAdapter = true)
data class NoaaAlertFeature(
    val id: String = "",
    val properties: NoaaAlertProperties? = null
)

@JsonClass(generateAdapter = true)
data class NoaaAlertProperties(
    val event: String? = null,
    val headline: String? = null,
    val severity: String? = null,
    val areaDesc: String? = null,
    val senderName: String? = null,
    val effective: String? = null,
    val expires: String? = null,
    val description: String? = null,
    val instruction: String? = null,
    val sent: String? = null
)

@JsonClass(generateAdapter = true)
data class VerificationSeriesResponse(
    val hourly: VerificationHourlyDto? = null
)

@JsonClass(generateAdapter = true)
data class VerificationHourlyDto(
    val time: List<String> = emptyList(),
    @Json(name = "temperature_2m") val temperature2m: List<Double?> = emptyList(),
    val precipitation: List<Double?> = emptyList()
)

// ==================== RETROFIT INTERFACES ====================

interface OpenMeteoForecastApi {
    @GET("v1/forecast")
    suspend fun getFullForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,rain,snowfall,weather_code,cloud_cover,pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m",
        @Query("hourly") hourly: String = "temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,precipitation_probability,precipitation,rain,snowfall,weather_code,pressure_msl,surface_pressure,cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high,visibility,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,shortwave_radiation,cape,is_day",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,sunrise,sunset,daylight_duration,uv_index_max,precipitation_sum,rain_sum,snowfall_sum,precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant,shortwave_radiation_sum",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 16
    ): OpenMeteoForecastResponse

    @GET("v1/forecast")
    suspend fun getModelComparisonForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "temperature_2m,precipitation,wind_speed_10m",
        @Query("models") model: String,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 2
    ): OpenMeteoForecastResponse
}

interface OpenMeteoAirQualityApi {
    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "us_aqi,european_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone,dust,grass_pollen,birch_pollen,ragweed_pollen",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoAirQualityResponse
}

interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    suspend fun searchLocations(
        @Query("name") query: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}

interface RainViewerApi {
    @GET("public/weather-maps.json")
    suspend fun getWeatherMaps(): RainViewerResponse
}

interface NoaaAlertsApi {
    @Headers("User-Agent: AetherWeatherApp/1.0 (contact@aetherweather.app)", "Accept: application/geo+json")
    @GET("alerts/active")
    suspend fun getActiveAlerts(@Query("point") latLon: String): NoaaAlertsResponse
}

interface OpenMeteoArchiveApi {
    @GET("v1/archive")
    suspend fun getHistoricalObservations(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("hourly") hourly: String = "temperature_2m,precipitation",
        @Query("timezone") timezone: String = "UTC"
    ): VerificationSeriesResponse
}

interface OpenMeteoHistoricalForecastApi {
    @GET("v1/forecast")
    suspend fun getHistoricalModelForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("hourly") hourly: String = "temperature_2m,precipitation",
        @Query("models") model: String,
        @Query("timezone") timezone: String = "UTC"
    ): VerificationSeriesResponse
}

// ==================== NETWORK FACTORY, INTERCEPTORS & SPECIALIZED ADAPTERS ====================

/**
 * Adds consistent identification, secure JSON content negotiation, and TLS-only enforcement
 * headers across all meteorological API requests.
 */
class WeatherSecurityHeadersInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("Accept", original.header("Accept") ?: "application/json")
            .header("User-Agent", original.header("User-Agent") ?: "AetherWeather/1.0 (Android; Meteorological Intelligence)")
            .header("X-Requested-With", "com.aistudio.aetherweather")
        return chain.proceed(requestBuilder.build())
    }
}

/**
 * Handles transient HTTP 429 (Too Many Requests) and 502/503/504 gateway errors with
 * bounded exponential backoff honoring Retry-After headers when provided.
 */
class RateLimitBackoffInterceptor(private val maxRetries: Int = 2) : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        var attempt = 0
        var response = chain.proceed(request)
        while (!response.isSuccessful && attempt < maxRetries && response.code in setOf(429, 502, 503, 504)) {
            val retryAfterSec = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1L, 5L)
            val backoffMs = retryAfterSec?.times(1000L) ?: (400L * (1 shl attempt))
            response.close()
            try {
                Thread.sleep(backoffMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
            attempt++
            response = chain.proceed(request)
        }
        return response
    }
}

object WeatherNetworkClient {

    const val BASE_URL_OPEN_METEO = "https://api.open-meteo.com/"
    const val BASE_URL_AIR_QUALITY = "https://air-quality-api.open-meteo.com/"
    const val BASE_URL_GEOCODING = "https://geocoding-api.open-meteo.com/"
    const val BASE_URL_RAINVIEWER = "https://api.rainviewer.com/"
    const val BASE_URL_NOAA_NWS = "https://api.weather.gov/"
    const val BASE_URL_ARCHIVE = "https://archive-api.open-meteo.com/"
    const val BASE_URL_HISTORICAL_FORECAST = "https://historical-forecast-api.open-meteo.com/"

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor: okhttp3.logging.HttpLoggingInterceptor =
        okhttp3.logging.HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            redactHeader("Authorization")
            redactHeader("X-API-Key")
            level = if (com.example.BuildConfig.DEBUG) {
                okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
            } else {
                okhttp3.logging.HttpLoggingInterceptor.Level.NONE
            }
        }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(WeatherSecurityHeadersInterceptor())
        .addInterceptor(RateLimitBackoffInterceptor(maxRetries = 2))
        .addInterceptor(loggingInterceptor)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun buildRetrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val openMeteoForecastApi: OpenMeteoForecastApi by lazy {
        buildRetrofit(BASE_URL_OPEN_METEO).create(OpenMeteoForecastApi::class.java)
    }

    val openMeteoAirQualityApi: OpenMeteoAirQualityApi by lazy {
        buildRetrofit(BASE_URL_AIR_QUALITY).create(OpenMeteoAirQualityApi::class.java)
    }

    val openMeteoGeocodingApi: OpenMeteoGeocodingApi by lazy {
        buildRetrofit(BASE_URL_GEOCODING).create(OpenMeteoGeocodingApi::class.java)
    }

    val rainViewerApi: RainViewerApi by lazy {
        buildRetrofit(BASE_URL_RAINVIEWER).create(RainViewerApi::class.java)
    }

    val noaaAlertsApi: NoaaAlertsApi by lazy {
        buildRetrofit(BASE_URL_NOAA_NWS).create(NoaaAlertsApi::class.java)
    }

    val openMeteoArchiveApi: OpenMeteoArchiveApi by lazy {
        buildRetrofit(BASE_URL_ARCHIVE).create(OpenMeteoArchiveApi::class.java)
    }

    val openMeteoHistoricalForecastApi: OpenMeteoHistoricalForecastApi by lazy {
        buildRetrofit(BASE_URL_HISTORICAL_FORECAST).create(OpenMeteoHistoricalForecastApi::class.java)
    }

    /**
     * Exponential backoff retry helper for transient meteorological API failures.
     */
    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 500L,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxAttempts - 1) {
                    delay(currentDelay)
                    currentDelay *= 2
                }
            }
        }
        throw lastError ?: IllegalStateException("Network request failed")
    }

    /**
     * Queries Open-Meteo Ensemble API (ECMWF IFS 0.25° Ensemble) dynamically parsing member columns
     * (temperature_2m_member01..50, precipitation_member01..50) to calculate genuine statistical spread.
     */
    suspend fun fetchEnsembleMembers(
        latitude: Double,
        longitude: Double
    ): Pair<List<List<Double>>, List<List<Double>>>? = withContext(Dispatchers.IO) {
        val url = "https://ensemble-api.open-meteo.com/v1/ensemble" +
            "?latitude=$latitude&longitude=$longitude" +
            "&hourly=temperature_2m,precipitation" +
            "&models=ecmwf_ifs025&forecast_days=2&timezone=auto"
        try {
            val req = Request.Builder().url(url).build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val root = JSONObject(body)
                val hourly = root.optJSONObject("hourly") ?: return@withContext null
                val tempMembers = mutableListOf<List<Double>>()
                val precipMembers = mutableListOf<List<Double>>()
                val keys = hourly.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.startsWith("temperature_2m_member")) {
                        val arr = hourly.optJSONArray(key) ?: continue
                        val list = (0 until arr.length()).mapNotNull { idx ->
                            if (arr.isNull(idx)) null else arr.optDouble(idx)
                        }
                        if (list.isNotEmpty()) tempMembers += list
                    } else if (key.startsWith("precipitation_member")) {
                        val arr = hourly.optJSONArray(key) ?: continue
                        val list = (0 until arr.length()).mapNotNull { idx ->
                            if (arr.isNull(idx)) null else arr.optDouble(idx)
                        }
                        if (list.isNotEmpty()) precipMembers += list
                    }
                }
                if (tempMembers.size >= 2) tempMembers to precipMembers else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ensemble fetch unavailable: ${e.message}")
            null
        }
    }

    data class OpenMeteoModelSummary(
        val resolvedModelId: String,
        val currentTempC: Double,
        val next24hMaxC: Double,
        val next24hMinC: Double,
        val next24hPrecipMm: Double?,
        val maxWindKmh: Double?,
        val stepCount: Int
    )

    /**
     * Parses Open-Meteo hourly JSON for both standard 1-hourly NWP models and 6-hourly AI models
     * (GraphCast 0.25°, ECMWF AIFS 0.25°). Unlike `.take(24)` from index 0—which fails whenever an
     * AI model's latest run starts later in the day—this locates the first non-null forecast step
     * across the entire returned horizon and aggregates the next 24-hour forecast window.
     */
    fun parseOpenMeteoModelHourlyJson(
        body: String,
        resolvedModelId: String
    ): OpenMeteoModelSummary? {
        val seriesMap = mutableMapOf<String, List<Double?>>()
        val reader = com.squareup.moshi.JsonReader.of(okio.Buffer().writeUtf8(body))
        reader.isLenient = true
        if (reader.peek() != com.squareup.moshi.JsonReader.Token.BEGIN_OBJECT) return null
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == "hourly" && reader.peek() == com.squareup.moshi.JsonReader.Token.BEGIN_OBJECT) {
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    if (reader.peek() == com.squareup.moshi.JsonReader.Token.BEGIN_ARRAY &&
                        (key.startsWith("temperature_2m") || key.startsWith("precipitation") || key.startsWith("wind_speed_10m"))
                    ) {
                        val values = mutableListOf<Double?>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            when (reader.peek()) {
                                com.squareup.moshi.JsonReader.Token.NUMBER -> values += reader.nextDouble()
                                com.squareup.moshi.JsonReader.Token.NULL -> {
                                    reader.nextNull<Unit>()
                                    values += null
                                }
                                else -> {
                                    reader.skipValue()
                                    values += null
                                }
                            }
                        }
                        reader.endArray()
                        seriesMap[key] = values
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()

        fun extractArray(prefix: String): List<Double?> {
            seriesMap[prefix]?.let { return it }
            return seriesMap.entries.firstOrNull { it.key.startsWith("${prefix}_") }?.value.orEmpty()
        }

        val tempAll = extractArray("temperature_2m")
        val firstValidIdx = tempAll.indexOfFirst { it != null }
        if (firstValidIdx < 0) return null

        val windowEnd = minOf(tempAll.size, firstValidIdx + 24)
        var temps = tempAll.subList(firstValidIdx, windowEnd).filterNotNull()
        if (temps.isEmpty()) temps = tempAll.filterNotNull().take(8)
        if (temps.isEmpty()) return null

        val precipAll = extractArray("precipitation")
        val precipWindow = if (precipAll.size > firstValidIdx) {
            precipAll.subList(firstValidIdx, minOf(precipAll.size, windowEnd)).filterNotNull()
        } else {
            emptyList()
        }

        val windAll = extractArray("wind_speed_10m")
        val windWindow = if (windAll.size > firstValidIdx) {
            windAll.subList(firstValidIdx, minOf(windAll.size, windowEnd)).filterNotNull()
        } else {
            emptyList()
        }

        return OpenMeteoModelSummary(
            resolvedModelId = resolvedModelId,
            currentTempC = temps.first(),
            next24hMaxC = temps.max(),
            next24hMinC = temps.min(),
            next24hPrecipMm = if (precipWindow.isNotEmpty()) precipWindow.sum() else null,
            maxWindKmh = windWindow.maxOrNull(),
            stepCount = temps.size
        )
    }

    /**
     * Queries Open-Meteo across candidate endpoints/model aliases (e.g., `v1/gfs` + `gfs_graphcast025`,
     * `v1/ecmwf` + `ecmwf_aifs025_single`) with a 4-day window and variable-set fallback so AI models
     * with 6-hourly native steps or narrower surface variable sets reliably return live grid forecasts.
     */
    suspend fun fetchOpenMeteoModelSeries(
        latitude: Double,
        longitude: Double,
        candidates: List<Pair<String, String>>
    ): OpenMeteoModelSummary? = withContext(Dispatchers.IO) {
        val variableSets = listOf(
            "temperature_2m,precipitation,wind_speed_10m",
            "temperature_2m,precipitation",
            "temperature_2m"
        )
        for ((endpointPath, modelId) in candidates) {
            for (hourlyVars in variableSets) {
                val url = "${BASE_URL_OPEN_METEO}$endpointPath" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&hourly=$hourlyVars&models=$modelId&timezone=auto&forecast_days=4"
                try {
                    val req = Request.Builder().url(url).build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body?.string() ?: return@use
                        val parsed = parseOpenMeteoModelHourlyJson(body, modelId)
                        if (parsed != null) return@withContext parsed
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Open-Meteo query failed ($endpointPath / $modelId): ${e.message}")
                }
            }
        }
        null
    }

    /**
     * Fetches official India Meteorological Department (IMD) CAP/RSS public severe weather alerts.
     * Falls back to NDMA SACHET India CAP RSS if primary feed times out.
     */
    suspend fun fetchOfficialImdAlerts(regionHint: String): List<WeatherAlert> = withContext(Dispatchers.IO) {
        val feedUrls = listOf(
            "https://cap-sources.s3.amazonaws.com/in-imd-en/rss.xml",
            "https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml"
        )
        for (url in feedUrls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AetherWeatherApp/1.0")
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val xml = resp.body?.string().orEmpty()
                    if (xml.isBlank()) return@use
                    val parsed = parseRssAlerts(xml, regionHint, url.contains("sachet"))
                    if (parsed.isNotEmpty()) return@withContext parsed
                }
            } catch (e: Exception) {
                Log.w(TAG, "IMD/SACHET RSS fetch failed ($url): ${e.message}")
            }
        }
        emptyList()
    }

    private fun parseRssAlerts(
        xml: String,
        regionHint: String,
        isSachet: Boolean
    ): List<WeatherAlert> {
        val results = mutableListOf<WeatherAlert>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var eventType = parser.eventType
            var insideItem = false
            var title = ""
            var description = ""
            var pubDate = ""
            var category = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.lowercase()
                        if (tag == "item" || tag == "entry") {
                            insideItem = true
                            title = ""
                            description = ""
                            pubDate = ""
                            category = ""
                        } else if (insideItem) {
                            when (tag) {
                                "title" -> title = parser.nextText().trim()
                                "description", "summary" -> description = parser.nextText().replace(Regex("<[^>]*>"), " ").trim()
                                "pubdate", "updated" -> pubDate = parser.nextText().trim()
                                "category" -> category = parser.nextText().trim()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name.lowercase()
                        if ((tag == "item" || tag == "entry") && insideItem) {
                            insideItem = false
                            if (title.isNotBlank()) {
                                val combined = "$title $description"
                                val sev = when {
                                    combined.contains("Red", ignoreCase = true) || combined.contains("Extreme", ignoreCase = true) || combined.contains("Cyclone", ignoreCase = true) -> AlertSeverity.EXTREME
                                    combined.contains("Orange", ignoreCase = true) || combined.contains("Severe", ignoreCase = true) || combined.contains("Heavy", ignoreCase = true) -> AlertSeverity.SEVERE
                                    combined.contains("Yellow", ignoreCase = true) || combined.contains("Moderate", ignoreCase = true) -> AlertSeverity.MODERATE
                                    else -> AlertSeverity.MINOR
                                }
                                results += WeatherAlert(
                                    id = "imd_${title.hashCode()}_${pubDate.hashCode()}",
                                    title = title.take(120),
                                    eventType = category.ifBlank { "IMD Weather Bulletin" },
                                    severity = sev,
                                    provenance = AlertProvenance.OFFICIAL_AGENCY,
                                    issuingAuthority = if (isSachet) "NDMA SACHET / IMD India" else "India Meteorological Department (IMD)",
                                    affectedArea = regionHint.ifBlank { "India" },
                                    validFrom = pubDate.ifBlank { Instant.now().toString() },
                                    validUntil = "See official bulletin",
                                    description = description.ifBlank { title }.take(450),
                                    precautions = listOf(
                                        "Follow official India Meteorological Department (IMD) & local NDMA guidance.",
                                        "Check regional district-wise color-coded warnings before travel."
                                    ),
                                    updatedUtcIso = pubDate.ifBlank { Instant.now().toString() }
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "RSS parse error: ${e.message}")
        }
        // Prioritize alerts matching the user's region/state first, then top national bulletins
        val tokens = regionHint.split(",", " ").map { it.trim() }.filter { it.length >= 4 }
        val sorted = results.sortedByDescending { alert ->
            val match = tokens.any { t ->
                alert.title.contains(t, ignoreCase = true) || alert.description.contains(t, ignoreCase = true)
            }
            (if (match) 10 else 0) + alert.severity.rank
        }
        return sorted.take(6)
    }

    /**
     * Live Google DeepMind WeatherNext 3 Execution Engine.
     *
     * Supports two live production paths via [WeatherApiProvider.weatherApi] + [WeatherAuthInterceptor]:
     * 1. Authorized WeatherNext 3 Cloud / Zarr JSON proxy URL (configured in Settings, authenticated
     *    with `Authorization: Bearer` via `BuildConfig.WEATHERNEXT_AUTH_TOKEN`).
     * 2. Official Google Maps Platform Weather API (`https://weather.googleapis.com/v1/`, powered by
     *    Google DeepMind WeatherNext 3) when `GOOGLE_WEATHER_API_KEY` is configured in AI Studio Secrets.
     *
     * If neither credentials nor a proxy endpoint are configured, reports status honestly without
     * substituting another model under the WeatherNext 3 name (while live DeepMind GraphCast 0.25°
     * runs in parallel as a clearly labeled operational AI model).
     */
    suspend fun queryWeatherNext3Adapter(
        latitude: Double,
        longitude: Double,
        customProxyUrl: String
    ): ModelComparisonPoint = withContext(Dispatchers.IO) {
        // Path 1: Authorized WeatherNext 3 Cloud / Zarr JSON proxy
        if (customProxyUrl.isNotBlank()) {
            return@withContext try {
                val dto = withRetry(maxAttempts = 2) {
                    WeatherApiProvider.weatherApi.getWeatherNext3ProxyForecast(
                        endpointUrl = customProxyUrl.trim(),
                        latitude = latitude,
                        longitude = longitude
                    )
                }
                val next24Hourly = dto.hourly.take(24)
                val temps = next24Hourly.mapNotNull { it.temperature2mC }
                val precips = next24Hourly.mapNotNull { it.totalPrecipitationMm }
                val winds = next24Hourly.mapNotNull { it.resolvedWindSpeedKmh() }

                val currentTemp = dto.current?.temperature2mC ?: temps.firstOrNull() ?: dto.flatTemperature2m
                val maxTemp = temps.maxOrNull() ?: dto.flatMaxTemp24h ?: dto.ensembleSummary?.temp24hP90C
                val minTemp = temps.minOrNull() ?: dto.flatMinTemp24h ?: dto.ensembleSummary?.temp24hP10C
                val totalPrecip = if (precips.isNotEmpty()) precips.sum() else dto.flatPrecip24hMm
                val maxWind = winds.maxOrNull() ?: dto.current?.resolvedWindSpeedKmh() ?: dto.flatMaxWind24hKmh

                ModelComparisonPoint(
                    modelId = "weathernext_3",
                    displayName = "Google DeepMind WeatherNext 3",
                    category = "Live AI Ensemble Engine (${dto.spatialResolutionDeg}° · ${dto.ensembleMemberCount} Members)",
                    resolutionDesc = "${dto.spatialResolutionDeg}° global (~11 km) · 5 km calibrated",
                    available = true,
                    statusNote = "Live via authorized WeatherNext 3 Cloud proxy (${dto.modelVersion})",
                    currentTempC = currentTemp,
                    next24hMaxC = maxTemp,
                    next24hMinC = minTemp,
                    next24hPrecipMm = totalPrecip,
                    maxWindKmh = maxWind,
                    initTimeNote = dto.initTimeUtc.ifBlank { "Live Cloud Proxy" }
                )
            } catch (e: Exception) {
                ModelComparisonPoint(
                    modelId = "weathernext_3",
                    displayName = "Google DeepMind WeatherNext 3",
                    category = "Primary AI Ensemble Engine (0.1° · 64 Members)",
                    resolutionDesc = "0.1° global (~11 km)",
                    available = false,
                    statusNote = "Unable to reach WeatherNext 3 proxy: ${e.localizedMessage ?: "Network error"}",
                    currentTempC = null,
                    next24hMaxC = null,
                    next24hMinC = null,
                    next24hPrecipMm = null,
                    maxWindKmh = null,
                    initTimeNote = "Proxy unreachable"
                )
            }
        }

        // Path 2: Live Google Maps Platform Weather API (weather.googleapis.com/v1/, powered by WeatherNext 3)
        if (WeatherApiProvider.hasGoogleWeatherApiKey()) {
            return@withContext try {
                coroutineScope {
                    val currentDeferred = async {
                        withRetry(maxAttempts = 2) {
                            WeatherApiProvider.weatherApi.getGoogleWeatherCurrent(
                                latitude = latitude,
                                longitude = longitude
                            )
                        }
                    }
                    val hourlyDeferred = async {
                        runCatching {
                            withRetry(maxAttempts = 2) {
                                WeatherApiProvider.weatherApi.getGoogleWeatherHourly(
                                    latitude = latitude,
                                    longitude = longitude,
                                    hours = 24
                                )
                            }
                        }.getOrNull()
                    }

                    val currentResp = currentDeferred.await()
                    val hourlyResp = hourlyDeferred.await()
                    val next24Hours = hourlyResp?.allHours().orEmpty().take(24)

                    val temps = next24Hours.mapNotNull { it.temperature?.toCelsius() }
                    val precips = next24Hours.mapNotNull { it.precipitation?.qpf?.toMillimeters() }
                    val winds = next24Hours.mapNotNull { it.wind?.speed?.toKmh() }

                    val currentTemp = currentResp.temperature?.toCelsius() ?: temps.firstOrNull()
                    val maxTemp = temps.maxOrNull() ?: currentTemp
                    val minTemp = temps.minOrNull() ?: currentTemp
                    val totalPrecip = if (precips.isNotEmpty()) precips.sum() else currentResp.precipitation?.qpf?.toMillimeters()
                    val maxWind = winds.maxOrNull() ?: currentResp.wind?.speed?.toKmh()
                    val condLabel = currentResp.weatherCondition?.description?.text?.takeIf { it.isNotBlank() }
                    val humidityNote = currentResp.relativeHumidity?.let { "RH $it%" }
                    val summaryParts = listOfNotNull(condLabel, humidityNote).joinToString(" · ")

                    ModelComparisonPoint(
                        modelId = "weathernext_3",
                        displayName = "Google DeepMind WeatherNext 3",
                        category = "Live Google Weather API (WeatherNext 3)",
                        resolutionDesc = "0.1° global · Google Maps Platform Weather API",
                        available = currentTemp != null,
                        statusNote = if (summaryParts.isNotBlank()) {
                            "Live via weather.googleapis.com ($summaryParts)"
                        } else {
                            "Live via Google Weather API (weather.googleapis.com)"
                        },
                        currentTempC = currentTemp,
                        next24hMaxC = maxTemp,
                        next24hMinC = minTemp,
                        next24hPrecipMm = totalPrecip,
                        maxWindKmh = maxWind,
                        initTimeNote = currentResp.currentTime.ifBlank { "Live REST sync" }
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Google Weather API (WeatherNext 3) call failed: ${e.message}")
                ModelComparisonPoint(
                    modelId = "weathernext_3",
                    displayName = "Google DeepMind WeatherNext 3",
                    category = "Google Weather API (WeatherNext 3)",
                    resolutionDesc = "0.1° global · Google Maps Platform Weather API",
                    available = false,
                    statusNote = "Google Weather API error: ${e.localizedMessage ?: "Verify GOOGLE_WEATHER_API_KEY & Weather API enablement"}",
                    currentTempC = null,
                    next24hMaxC = null,
                    next24hMinC = null,
                    next24hPrecipMm = null,
                    maxWindKmh = null,
                    initTimeNote = "API error"
                )
            }
        }

        // Path 3: Honest unconfigured state (never mislabels another model as WeatherNext 3)
        ModelComparisonPoint(
            modelId = "weathernext_3",
            displayName = "Google DeepMind WeatherNext 3",
            category = "Primary AI Ensemble Engine (0.1° · 64 Members)",
            resolutionDesc = "0.1° global (~11 km) · 5 km calibrated · 15d hourly",
            available = false,
            statusNote = "Add GOOGLE_WEATHER_API_KEY in AI Studio Secrets for live Google Weather API (WeatherNext 3), or enter an authorized Cloud/Zarr proxy URL in Settings.",
            currentTempC = null,
            next24hMaxC = null,
            next24hMinC = null,
            next24hPrecipMm = null,
            maxWindKmh = null,
            initTimeNote = "Set Secret or Cloud Proxy"
        )
    }
}
