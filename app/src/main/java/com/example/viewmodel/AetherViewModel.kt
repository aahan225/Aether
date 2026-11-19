package com.example.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.WeatherAiRepository
import com.example.data.local.AetherDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.repository.WeatherRepository
import com.example.domain.engine.ForecastVerificationEngine
import com.example.domain.engine.MeteorologyCalculator
import com.example.domain.engine.VerificationBenchmarkRegion
import com.example.domain.model.ChatMessage
import com.example.domain.model.RadarState
import com.example.domain.model.SavedLocation
import com.example.domain.model.UserPreferences
import com.example.domain.model.VerificationReport
import com.example.domain.model.WeatherBundle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private const val TAG = "AetherViewModel"

data class WeatherUiState(
    val activeLocation: SavedLocation? = null,
    val weatherBundle: WeatherBundle? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val gpsStatusMessage: String? = null,
    val searchQuery: String = "",
    val searchResults: List<SavedLocation> = emptyList(),
    val isSearching: Boolean = false,
    val radarState: RadarState = RadarState(isLoading = true),
    val selectedBenchmark: VerificationBenchmarkRegion = ForecastVerificationEngine.indiaAndGlobalBenchmarks.first(),
    val verificationReport: VerificationReport? = null,
    val isVerifying: Boolean = false,
    val verificationError: String? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isAssistantThinking: Boolean = false
)

class AetherViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AetherDatabase.getInstance(application)
    private val weatherRepository = WeatherRepository(database)
    private val preferencesRepository = UserPreferencesRepository(application)
    private val aiRepository = WeatherAiRepository()

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val savedLocations: StateFlow<List<SavedLocation>> = weatherRepository.savedLocationsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val cachedCityBundles: StateFlow<Map<String, WeatherBundle>> = weatherRepository.cachedBundlesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var weatherJob: Job? = null
    private var assistantJob: Job? = null

    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _uiState.update { it.copy(isOffline = false) }
        }

        override fun onLost(network: Network) {
            _uiState.update { it.copy(isOffline = !isNetworkConnected()) }
        }
    }

    init {
        _uiState.update { it.copy(isOffline = !isNetworkConnected()) }
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }

        viewModelScope.launch {
            val seeded = weatherRepository.ensureDefaultLocationsSeeded()
            val savedActiveId = preferencesRepository.activeLocationIdFlow.first()
            val initialLoc = seeded.firstOrNull { it.id == savedActiveId } ?: seeded.first()
            selectLocation(initialLoc, forceNetworkRefresh = true)
            refreshRadar()
            // Warm secondary saved locations quietly for instant city comparison
            launch { warmSavedCities(seeded.filter { it.id != initialLoc.id }.take(4)) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    private fun isNetworkConnected(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun selectLocation(location: SavedLocation, forceNetworkRefresh: Boolean = true) {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            preferencesRepository.setActiveLocationId(location.id)
            _uiState.update {
                it.copy(
                    activeLocation = location,
                    errorMessage = null,
                    gpsStatusMessage = null
                )
            }

            // 1. Hydrate from Room cache immediately for ultra-fast rendering & offline resilience
            val cached = weatherRepository.getCachedWeather(location.id)
            if (cached != null) {
                _uiState.update {
                    it.copy(
                        weatherBundle = cached,
                        isLoading = false,
                        isRefreshing = forceNetworkRefresh
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = true, isRefreshing = false) }
            }

            if (!forceNetworkRefresh) return@launch

            if (!isNetworkConnected()) {
                _uiState.update {
                    it.copy(
                        isOffline = true,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = if (cached == null) "Offline and no cached forecast available for ${location.name}." else null
                    )
                }
                return@launch
            }

            try {
                val prefs = preferences.value
                val fresh = weatherRepository.fetchLiveWeatherBundle(
                    location = location,
                    use24Hour = prefs.use24HourTime,
                    weatherNext3ProxyUrl = prefs.weatherNext3ProxyUrl
                )
                _uiState.update {
                    it.copy(
                        activeLocation = fresh.location,
                        weatherBundle = fresh,
                        isLoading = false,
                        isRefreshing = false,
                        isOffline = false,
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed fetching live weather for ${location.name}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = if (cached == null) {
                            "Unable to load live weather: ${e.localizedMessage ?: "Network timeout"}"
                        } else {
                            "Live refresh failed — displaying last cached snapshot."
                        }
                    )
                }
            }
        }
    }

    fun refreshCurrentWeather() {
        val loc = _uiState.value.activeLocation ?: savedLocations.value.firstOrNull() ?: return
        selectLocation(loc, forceNetworkRefresh = true)
    }

    private suspend fun warmSavedCities(locations: List<SavedLocation>) {
        if (!isNetworkConnected()) return
        val prefs = preferences.value
        for (loc in locations) {
            val cached = weatherRepository.getCachedWeather(loc.id)
            val isFresh = cached != null && (System.currentTimeMillis() - cached.fetchedAtUtcMillis) < 25 * 60_000L
            if (!isFresh) {
                runCatching {
                    weatherRepository.fetchLiveWeatherBundle(
                        location = loc,
                        use24Hour = prefs.use24HourTime,
                        weatherNext3ProxyUrl = prefs.weatherNext3ProxyUrl
                    )
                }
                delay(250L)
            }
        }
    }

    fun refreshAllSavedCities() {
        viewModelScope.launch {
            warmSavedCities(savedLocations.value)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(320L)
            _uiState.update { it.copy(isSearching = true) }
            try {
                val results = weatherRepository.searchLocations(query)
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Location search failed: ${e.message}")
                _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            }
        }
    }

    fun addAndSelectLocation(location: SavedLocation) {
        viewModelScope.launch {
            val order = (savedLocations.value.maxOfOrNull { it.sortOrder } ?: 0) + 1
            val toSave = location.copy(sortOrder = order)
            weatherRepository.saveLocation(toSave)
            _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
            selectLocation(toSave, forceNetworkRefresh = true)
        }
    }

    fun removeLocation(location: SavedLocation) {
        viewModelScope.launch {
            val currentList = savedLocations.value
            if (currentList.size <= 1) return@launch
            weatherRepository.removeLocation(location.id)
            if (_uiState.value.activeLocation?.id == location.id) {
                val next = currentList.firstOrNull { it.id != location.id }
                if (next != null) selectLocation(next, forceNetworkRefresh = false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun detectGpsLocation() {
        val app = getApplication<Application>()
        _uiState.update { it.copy(gpsStatusMessage = "Acquiring GPS coordinates…") }
        val fusedClient = LocationServices.getFusedLocationProviderClient(app)
        val tokenSource = CancellationTokenSource()

        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    handleGpsCoordinates(loc.latitude, loc.longitude)
                } else {
                    fusedClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            handleGpsCoordinates(last.latitude, last.longitude)
                        } else {
                            _uiState.update {
                                it.copy(gpsStatusMessage = "GPS unavailable — search or choose a saved location.")
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { err ->
                _uiState.update {
                    it.copy(gpsStatusMessage = "Location error: ${err.localizedMessage ?: "Unavailable"}")
                }
            }
    }

    private fun handleGpsCoordinates(lat: Double, lon: Double) {
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                var name = String.format(Locale.US, "%.2f°, %.2f°", lat, lon)
                var admin = "GPS Location"
                var country = ""
                var countryCode = ""
                runCatching {
                    @Suppress("DEPRECATION")
                    val addresses = Geocoder(getApplication(), Locale.getDefault()).getFromLocation(lat, lon, 1)
                    val addr = addresses?.firstOrNull()
                    if (addr != null) {
                        name = addr.locality ?: addr.subLocality ?: addr.subAdminArea ?: name
                        admin = addr.adminArea ?: addr.subAdminArea ?: "Detected Location"
                        country = addr.countryName ?: ""
                        countryCode = addr.countryCode ?: ""
                    }
                }
                SavedLocation(
                    id = "gps_current",
                    name = name,
                    adminRegion = admin,
                    country = country,
                    countryCode = countryCode,
                    latitude = lat,
                    longitude = lon,
                    timezone = "auto",
                    isCurrentGps = true,
                    sortOrder = -1
                )
            }
            weatherRepository.saveLocation(resolved)
            _uiState.update { it.copy(gpsStatusMessage = null) }
            selectLocation(resolved, forceNetworkRefresh = true)
        }
    }

    fun refreshRadar() {
        viewModelScope.launch {
            _uiState.update { it.copy(radarState = it.radarState.copy(isLoading = true, error = null)) }
            val state = weatherRepository.fetchRainViewerRadar()
            _uiState.update { it.copy(radarState = state) }
        }
    }

    fun runVerification(
        region: VerificationBenchmarkRegion = _uiState.value.selectedBenchmark,
        forceRefresh: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedBenchmark = region,
                    isVerifying = true,
                    verificationError = null
                )
            }
            try {
                val report = weatherRepository.runEmpiricalVerification(region, forceRefresh)
                _uiState.update {
                    it.copy(
                        verificationReport = report,
                        isVerifying = false,
                        verificationError = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Verification run failed", e)
                _uiState.update {
                    it.copy(
                        isVerifying = false,
                        verificationError = "Verification pipeline failed: ${e.localizedMessage ?: "Archive API unavailable"}"
                    )
                }
            }
        }
    }

    fun verifyActiveLocation() {
        val loc = _uiState.value.activeLocation ?: return
        val region = VerificationBenchmarkRegion(
            id = "custom_${loc.id}",
            name = "${loc.name}, ${loc.country}",
            category = "Active Location · Empirical Verification",
            latitude = loc.latitude,
            longitude = loc.longitude
        )
        runVerification(region, forceRefresh = true)
    }

    fun askWeatherAssistant(question: String) {
        val trimmed = question.trim()
        if (trimmed.isBlank() || _uiState.value.isAssistantThinking) return

        val timeFmt = DateTimeFormatter.ofPattern(if (preferences.value.use24HourTime) "HH:mm" else "h:mm a")
            .withZone(ZoneId.systemDefault())
        val nowStamp = timeFmt.format(Instant.now())

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = trimmed,
            timestampLabel = nowStamp
        )
        val assistantMsgId = UUID.randomUUID().toString()
        val placeholderMsg = ChatMessage(
            id = assistantMsgId,
            isUser = false,
            text = "",
            timestampLabel = nowStamp,
            isStreaming = true
        )

        _uiState.update {
            it.copy(
                chatMessages = it.chatMessages + userMsg + placeholderMsg,
                isAssistantThinking = true
            )
        }

        assistantJob?.cancel()
        assistantJob = viewModelScope.launch {
            try {
                // Ensure we have live or freshly cached weather before responding
                var bundle = _uiState.value.weatherBundle
                val activeLoc = _uiState.value.activeLocation
                if (bundle == null && activeLoc != null && isNetworkConnected()) {
                    bundle = runCatching {
                        weatherRepository.fetchLiveWeatherBundle(
                            location = activeLoc,
                            use24Hour = preferences.value.use24HourTime,
                            weatherNext3ProxyUrl = preferences.value.weatherNext3ProxyUrl
                        )
                    }.getOrNull()
                }

                aiRepository.streamWeatherAnswer(
                    question = trimmed,
                    activeBundle = bundle,
                    savedCityBundles = cachedCityBundles.value,
                    preferences = preferences.value
                ).collect { streamedText ->
                    _uiState.update { state ->
                        state.copy(
                            chatMessages = state.chatMessages.map { msg ->
                                if (msg.id == assistantMsgId) {
                                    msg.copy(text = streamedText, isStreaming = true)
                                } else msg
                            }
                        )
                    }
                }

                _uiState.update { state ->
                    state.copy(
                        isAssistantThinking = false,
                        chatMessages = state.chatMessages.map { msg ->
                            if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg
                        }
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update { state ->
                    state.copy(
                        isAssistantThinking = false,
                        chatMessages = state.chatMessages.map { msg ->
                            if (msg.id == assistantMsgId && msg.isStreaming) {
                                msg.copy(
                                    text = msg.text.ifBlank { "Request cancelled." },
                                    isStreaming = false
                                )
                            } else msg
                        }
                    )
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Assistant response error", e)
                val errText = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Unable to complete AI weather query (Firebase AI Logic error)."
                _uiState.update { state ->
                    state.copy(
                        isAssistantThinking = false,
                        chatMessages = state.chatMessages.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(text = errText, isStreaming = false, isError = true)
                            } else msg
                        }
                    )
                }
            }
        }
    }

    fun clearChatHistory() {
        assistantJob?.cancel()
        _uiState.update { it.copy(chatMessages = emptyList(), isAssistantThinking = false) }
    }

    fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            val prevProxy = preferences.value.weatherNext3ProxyUrl
            preferencesRepository.updatePreferences(transform)
            val newProxy = transform(preferences.value).weatherNext3ProxyUrl
            if (prevProxy != newProxy) {
                refreshCurrentWeather()
            }
        }
    }
}
