package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AqiStandard
import com.example.domain.model.PrecipitationUnit
import com.example.domain.model.PressureUnit
import com.example.domain.model.TemperatureUnit
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import com.example.domain.model.WindUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aetherDataStore: DataStore<Preferences> by preferencesDataStore(name = "aether_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
        val WIND_UNIT = stringPreferencesKey("wind_unit")
        val PRECIP_UNIT = stringPreferencesKey("precip_unit")
        val PRESSURE_UNIT = stringPreferencesKey("pressure_unit")
        val USE_24H = booleanPreferencesKey("use_24h")
        val AQI_STANDARD = stringPreferencesKey("aqi_standard")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val REFRESH_INTERVAL = intPreferencesKey("refresh_interval_min")
        val SEVERE_ALERTS = booleanPreferencesKey("severe_alerts")
        val PRECIP_ALERTS = booleanPreferencesKey("precip_alerts")
        val WEATHERNEXT_PROXY_URL = stringPreferencesKey("weathernext_proxy_url")
        val ACTIVE_LOCATION_ID = stringPreferencesKey("active_location_id")
    }

    val preferencesFlow: Flow<UserPreferences> = context.aetherDataStore.data.map { p ->
        UserPreferences(
            tempUnit = p[Keys.TEMP_UNIT]?.let { runCatching { TemperatureUnit.valueOf(it) }.getOrNull() }
                ?: TemperatureUnit.CELSIUS,
            windUnit = p[Keys.WIND_UNIT]?.let { runCatching { WindUnit.valueOf(it) }.getOrNull() }
                ?: WindUnit.KMH,
            precipUnit = p[Keys.PRECIP_UNIT]?.let { runCatching { PrecipitationUnit.valueOf(it) }.getOrNull() }
                ?: PrecipitationUnit.MM,
            pressureUnit = p[Keys.PRESSURE_UNIT]?.let { runCatching { PressureUnit.valueOf(it) }.getOrNull() }
                ?: PressureUnit.HPA,
            use24HourTime = p[Keys.USE_24H] ?: false,
            aqiStandard = p[Keys.AQI_STANDARD]?.let { runCatching { AqiStandard.valueOf(it) }.getOrNull() }
                ?: AqiStandard.INDIA_CPCB,
            themeMode = p[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DYNAMIC_WEATHER,
            reducedMotion = p[Keys.REDUCED_MOTION] ?: false,
            refreshIntervalMinutes = p[Keys.REFRESH_INTERVAL] ?: 30,
            severeAlertsEnabled = p[Keys.SEVERE_ALERTS] ?: true,
            precipAlertsEnabled = p[Keys.PRECIP_ALERTS] ?: true,
            weatherNext3ProxyUrl = p[Keys.WEATHERNEXT_PROXY_URL].orEmpty()
        )
    }

    val activeLocationIdFlow: Flow<String?> = context.aetherDataStore.data.map { it[Keys.ACTIVE_LOCATION_ID] }

    suspend fun setActiveLocationId(id: String) {
        context.aetherDataStore.edit { it[Keys.ACTIVE_LOCATION_ID] = id }
    }

    suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        context.aetherDataStore.edit { prefs ->
            val current = UserPreferences(
                tempUnit = prefs[Keys.TEMP_UNIT]?.let { runCatching { TemperatureUnit.valueOf(it) }.getOrNull() } ?: TemperatureUnit.CELSIUS,
                windUnit = prefs[Keys.WIND_UNIT]?.let { runCatching { WindUnit.valueOf(it) }.getOrNull() } ?: WindUnit.KMH,
                precipUnit = prefs[Keys.PRECIP_UNIT]?.let { runCatching { PrecipitationUnit.valueOf(it) }.getOrNull() } ?: PrecipitationUnit.MM,
                pressureUnit = prefs[Keys.PRESSURE_UNIT]?.let { runCatching { PressureUnit.valueOf(it) }.getOrNull() } ?: PressureUnit.HPA,
                use24HourTime = prefs[Keys.USE_24H] ?: false,
                aqiStandard = prefs[Keys.AQI_STANDARD]?.let { runCatching { AqiStandard.valueOf(it) }.getOrNull() } ?: AqiStandard.INDIA_CPCB,
                themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DYNAMIC_WEATHER,
                reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
                refreshIntervalMinutes = prefs[Keys.REFRESH_INTERVAL] ?: 30,
                severeAlertsEnabled = prefs[Keys.SEVERE_ALERTS] ?: true,
                precipAlertsEnabled = prefs[Keys.PRECIP_ALERTS] ?: true,
                weatherNext3ProxyUrl = prefs[Keys.WEATHERNEXT_PROXY_URL].orEmpty()
            )
            val updated = transform(current)
            prefs[Keys.TEMP_UNIT] = updated.tempUnit.name
            prefs[Keys.WIND_UNIT] = updated.windUnit.name
            prefs[Keys.PRECIP_UNIT] = updated.precipUnit.name
            prefs[Keys.PRESSURE_UNIT] = updated.pressureUnit.name
            prefs[Keys.USE_24H] = updated.use24HourTime
            prefs[Keys.AQI_STANDARD] = updated.aqiStandard.name
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.REDUCED_MOTION] = updated.reducedMotion
            prefs[Keys.REFRESH_INTERVAL] = updated.refreshIntervalMinutes
            prefs[Keys.SEVERE_ALERTS] = updated.severeAlertsEnabled
            prefs[Keys.PRECIP_ALERTS] = updated.precipAlertsEnabled
            prefs[Keys.WEATHERNEXT_PROXY_URL] = updated.weatherNext3ProxyUrl
        }
    }
}
