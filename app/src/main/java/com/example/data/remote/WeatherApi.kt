package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.GoogleWeatherCurrentResponse
import com.example.data.model.GoogleWeatherHourlyResponse
import com.example.data.model.WeatherResponse
import com.example.data.network.RateLimitBackoffInterceptor
import com.example.data.network.WeatherSecurityHeadersInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

private const val TAG = "WeatherApi"

/**
 * Custom OkHttp Interceptor that injects secure authentication credentials from BuildConfig
 * (populated via the AI Studio Secrets panel and `.env` — never hardcoded in source):
 *
 * - Requests targeting `weather.googleapis.com` or `tile.googleapis.com` receive `X-Goog-Api-Key`
 *   (and `key` query param) from `BuildConfig.GOOGLE_WEATHER_API_KEY` or `BuildConfig.GOOGLE_MAPS_API_KEY`.
 * - Requests targeting an authorized WeatherNext 3 Cloud / Zarr proxy receive
 *   `Authorization: Bearer <token>` using `BuildConfig.WEATHERNEXT_AUTH_TOKEN`.
 */
class WeatherAuthInterceptor(
    private val googleApiKeyProvider: () -> String = { WeatherApiProvider.resolveGoogleApiKey() },
    private val weatherNextBearerTokenProvider: () -> String = { BuildConfig.WEATHERNEXT_AUTH_TOKEN }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = original.url.host.lowercase()
        val builder = original.newBuilder()

        if (host.endsWith("weather.googleapis.com") || host.endsWith("tile.googleapis.com")) {
            val apiKey = googleApiKeyProvider().trim()
            if (isConfiguredSecret(apiKey, "YOUR_GOOGLE_WEATHER_API_KEY") &&
                isConfiguredSecret(apiKey, "YOUR_GOOGLE_MAPS_API_KEY")
            ) {
                val updatedUrl = original.url.newBuilder()
                    .setQueryParameter("key", apiKey)
                    .build()
                builder
                    .url(updatedUrl)
                    .header("X-Goog-Api-Key", apiKey)
            }
        } else {
            val token = weatherNextBearerTokenProvider().trim()
            if (isConfiguredSecret(token, "YOUR_WEATHERNEXT_AUTH_TOKEN") &&
                original.header("Authorization").isNullOrBlank()
            ) {
                builder.header("Authorization", "Bearer $token")
            }
        }

        return chain.proceed(builder.build())
    }

    companion object {
        fun isConfiguredSecret(value: String?, placeholder: String): Boolean {
            if (value.isNullOrBlank()) return false
            val trimmed = value.trim()
            return trimmed != placeholder && !trimmed.startsWith("YOUR_")
        }
    }
}

/**
 * Retrofit API interface for Google Maps Platform Weather API (powered by Google DeepMind
 * WeatherNext 3) and authorized WeatherNext 3 Cloud / Zarr proxy endpoints.
 */
interface WeatherApi {

    @GET("v1/currentConditions:lookup")
    suspend fun getGoogleWeatherCurrent(
        @Query("location.latitude") latitude: Double,
        @Query("location.longitude") longitude: Double,
        @Query("unitsSystem") unitsSystem: String = "METRIC",
        @Query("languageCode") languageCode: String = "en"
    ): GoogleWeatherCurrentResponse

    @GET("v1/forecast/hours:lookup")
    suspend fun getGoogleWeatherHourly(
        @Query("location.latitude") latitude: Double,
        @Query("location.longitude") longitude: Double,
        @Query("hours") hours: Int = 24,
        @Query("unitsSystem") unitsSystem: String = "METRIC",
        @Query("languageCode") languageCode: String = "en"
    ): GoogleWeatherHourlyResponse

    @GET
    suspend fun getWeatherNext3ProxyForecast(
        @Url endpointUrl: String,
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Header("X-Aether-Model") modelHeader: String = "weathernext-3"
    ): WeatherResponse
}

object WeatherApiProvider {

    const val BASE_URL_GOOGLE_WEATHER = "https://weather.googleapis.com/"

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor { msg ->
        Log.d(TAG, msg)
    }.apply {
        redactHeader("Authorization")
        redactHeader("X-Goog-Api-Key")
        redactHeader("X-API-Key")
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(WeatherSecurityHeadersInterceptor())
        .addInterceptor(WeatherAuthInterceptor())
        .addInterceptor(RateLimitBackoffInterceptor(maxRetries = 2))
        .addInterceptor(loggingInterceptor)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val weatherApi: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_GOOGLE_WEATHER)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WeatherApi::class.java)
    }

    fun resolveGoogleApiKey(): String {
        val weatherKey = BuildConfig.GOOGLE_WEATHER_API_KEY.trim()
        if (WeatherAuthInterceptor.isConfiguredSecret(weatherKey, "YOUR_GOOGLE_WEATHER_API_KEY")) {
            return weatherKey
        }
        val mapsKey = BuildConfig.GOOGLE_MAPS_API_KEY.trim()
        if (WeatherAuthInterceptor.isConfiguredSecret(mapsKey, "YOUR_GOOGLE_MAPS_API_KEY")) {
            return mapsKey
        }
        return ""
    }

    fun hasGoogleWeatherApiKey(): Boolean = resolveGoogleApiKey().isNotBlank()

    /**
     * Creates a Google Maps Platform 2D Map Tiles session token when a valid Maps key is
     * configured via AI Studio Secrets. Returns null gracefully if the key is absent or
     * if the Map Tiles API is not enabled on the key.
     */
    suspend fun createGoogleMapTilesSession(mapType: String = "roadmap"): String? = withContext(Dispatchers.IO) {
        if (!hasGoogleWeatherApiKey()) return@withContext null
        try {
            val payload = JSONObject().apply {
                put("mapType", mapType)
                put("language", "en-US")
                put("region", "US")
            }.toString()
            val req = Request.Builder()
                .url("https://tile.googleapis.com/v1/createSession")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                JSONObject(body).optString("session").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Map Tiles session unavailable: ${e.message}")
            null
        }
    }
}
