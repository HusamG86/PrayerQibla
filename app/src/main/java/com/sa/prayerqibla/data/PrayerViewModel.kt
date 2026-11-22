package com.sa.prayerqibla.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sa.prayerqibla.TimeMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.TimeZone

/** Holds the UI state for the Compose screens and keeps it in sync with [PrayerRepository]. */
class PrayerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PrayerRepository(app)

    private val _config = MutableStateFlow(repo.loadConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val _permissions = MutableStateFlow(repo.permissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _cityName = MutableStateFlow("")
    val cityName: StateFlow<String> = _cityName.asStateFlow()

    /** Short machine-readable code ("denied" / "unavailable") for the UI to localize. */
    private val _locationMessage = MutableStateFlow<String?>(null)
    val locationMessage: StateFlow<String?> = _locationMessage.asStateFlow()

    private val _times = MutableStateFlow<DoubleArray?>(null)
    val times: StateFlow<DoubleArray?> = _times.asStateFlow()

    private val _month = MutableStateFlow<List<DoubleArray>>(emptyList())
    val month: StateFlow<List<DoubleArray>> = _month.asStateFlow()

    private var loadedMonth: Pair<Int, Int>? = null
    private var timesJob: Job? = null
    private var monthJob: Job? = null

    init {
        _cityName.value = displayName(_config.value)
        refreshTimes()
        val today = TimeMath.todayIn(
            System.currentTimeMillis(),
            TimeMath.zoneFromPrefs(_config.value.zone, null)
        )
        loadMonth(today.year, today.monthValue)
    }

    fun update(block: (AppConfig) -> AppConfig) {
        repo.saveConfig(block(_config.value))
        // Read back so the published state matches what the alarms actually use.
        val saved = repo.loadConfig()
        _config.value = saved
        _cityName.value = displayName(saved)
        recomputeTimes()
        loadedMonth?.let { (year, month) -> recomputeMonth(year, month) }
    }

    fun setPrayerEnabled(index: Int, enabled: Boolean) {
        val bit = PrayerRepository.PRAYER_BITS.getOrNull(index) ?: return
        update { config ->
            config.copy(mask = if (enabled) config.mask or bit else config.mask and bit.inv())
        }
    }

    fun setAllAlerts(enabled: Boolean) {
        update { it.copy(mask = if (enabled) PrayerRepository.ALL_PRAYER_MASK else 0) }
    }

    fun setLanguage(lang: String) {
        update { it.copy(lang = lang) }
    }

    fun setTheme(mode: ThemeMode) {
        update { it.copy(theme = mode) }
    }

    fun setCity(city: City) {
        clearLocationMessage()
        update {
            it.copy(
                cityId = city.id,
                lat = city.lat,
                lng = city.lng,
                zone = "Asia/Riyadh",
                custom = false,
                name = ""
            )
        }
    }

    fun setLocation(lat: Double, lng: Double) {
        if (!lat.isFinite() || !lng.isFinite() ||
            lat !in -90.0..90.0 || lng !in -180.0..180.0
        ) {
            setLocationError("unavailable")
            return
        }
        clearLocationMessage()
        update {
            it.copy(
                lat = lat,
                lng = lng,
                zone = TimeZone.getDefault().id,
                custom = true,
                cityId = "",
                name = ""
            )
        }
        repo.lookupCity(lat, lng, _config.value.lang) { city ->
            val current = _config.value
            // Ignore a late result if the user has since picked another location.
            if (!current.custom || current.lat != lat || current.lng != lng) return@lookupCity
            update { it.copy(name = city.trim()) }
        }
    }

    fun setLocationError(code: String) {
        _locationMessage.value = code
    }

    fun clearLocationMessage() {
        _locationMessage.value = null
    }

    fun refreshPermissions() {
        _permissions.value = repo.permissionState()
    }

    fun loadMonth(year: Int, month: Int) {
        loadedMonth = year to month
        recomputeMonth(year, month)
    }

    /** Force a recompute, e.g. after a day rollover while the screen is open. */
    fun refreshTimes() {
        recomputeTimes()
    }

    /** Times for any day, used to show the countdown across midnight. */
    fun timesFor(epochDay: Long): DoubleArray = repo.dayTimes(epochDay, _config.value)

    fun declination(): Double = with(_config.value) { repo.declination(lat, lng) }

    private fun recomputeTimes() {
        timesJob?.cancel()
        val config = _config.value
        val epochDay = TimeMath.todayIn(
            System.currentTimeMillis(),
            TimeMath.zoneFromPrefs(config.zone, null)
        ).toEpochDay()
        timesJob = viewModelScope.launch(Dispatchers.Default) {
            _times.value = repo.dayTimes(epochDay, config)
        }
    }

    private fun recomputeMonth(year: Int, month: Int) {
        monthJob?.cancel()
        val config = _config.value
        monthJob = viewModelScope.launch(Dispatchers.Default) {
            _month.value = repo.monthTimes(year, month, config)
        }
    }

    private fun displayName(config: AppConfig): String {
        if (config.custom) return config.name.trim()
        val city = PrayerRepository.CITIES.firstOrNull { it.id == config.cityId }
            ?: PrayerRepository.CITIES.first()
        return if (config.lang == "ar") city.ar else city.en
    }
}
