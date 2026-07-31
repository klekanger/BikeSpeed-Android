package no.bikespeed.app.service

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.bikespeed.app.model.AppLanguage
import no.bikespeed.app.model.MeasurementSystem

/**
 * Persists user-configurable settings. Values are stored in a canonical unit (km/h for the
 * gauge max) so a stored number's meaning never depends on the current display unit.
 *
 * Backed by [SharedPreferences], not DataStore, on purpose: `TripManager` reads
 * [autoPauseEnabled] synchronously inside the per-fix pipeline, and DataStore's async reads
 * would force that whole path async. Each setter is the persistence mechanism (the analogue
 * of the iOS `didSet` write-through); state is exposed both as plain properties (synchronous
 * reads for services) and as [StateFlow]s (for Compose).
 *
 * Injectable so tests get an in-memory store instead of reading and writing real preferences.
 */
class SettingsStore(private val prefs: SharedPreferences) {

    private object Keys {
        // Same key names as the iOS UserDefaults store.
        const val MAX_GAUGE_SPEED_KMH = "maxGaugeSpeedKMH"
        const val MEASUREMENT_SYSTEM = "measurementSystem"
        const val APP_LANGUAGE = "appLanguage"
        const val AUTO_PAUSE_ENABLED = "autoPauseEnabled"
    }

    private val _maxGaugeSpeedKmh = MutableStateFlow(
        if (prefs.contains(Keys.MAX_GAUGE_SPEED_KMH)) {
            Double.fromBits(prefs.getLong(Keys.MAX_GAUGE_SPEED_KMH, 0L))
        } else {
            60.0
        }
    )
    private val _measurementSystem = MutableStateFlow(
        MeasurementSystem.fromStorageKey(prefs.getString(Keys.MEASUREMENT_SYSTEM, null))
            ?: MeasurementSystem.METRIC
    )
    private val _appLanguage = MutableStateFlow(
        AppLanguage.fromStorageKey(prefs.getString(Keys.APP_LANGUAGE, null))
            ?: AppLanguage.SYSTEM
    )
    private val _autoPauseEnabled = MutableStateFlow(
        // contains(), not getBoolean(key, false): an unset key must read as the default-on,
        // while a deliberate "off" must survive — the two are indistinguishable through a
        // bare getBoolean default. Get this wrong and auto-pause silently ships disabled
        // for every new install.
        if (prefs.contains(Keys.AUTO_PAUSE_ENABLED)) {
            prefs.getBoolean(Keys.AUTO_PAUSE_ENABLED, true)
        } else {
            true
        }
    )

    val maxGaugeSpeedKmhFlow: StateFlow<Double> = _maxGaugeSpeedKmh.asStateFlow()
    val measurementSystemFlow: StateFlow<MeasurementSystem> = _measurementSystem.asStateFlow()
    val appLanguageFlow: StateFlow<AppLanguage> = _appLanguage.asStateFlow()
    val autoPauseEnabledFlow: StateFlow<Boolean> = _autoPauseEnabled.asStateFlow()

    /** Canonical km/h regardless of the display unit. Default 60. */
    var maxGaugeSpeedKmh: Double
        get() = _maxGaugeSpeedKmh.value
        set(value) {
            _maxGaugeSpeedKmh.value = value
            // Raw long bits: SharedPreferences has no putDouble, and a float would drift
            // values converted from mph.
            prefs.edit { putLong(Keys.MAX_GAUGE_SPEED_KMH, value.toRawBits()) }
        }

    var measurementSystem: MeasurementSystem
        get() = _measurementSystem.value
        set(value) {
            _measurementSystem.value = value
            prefs.edit { putString(Keys.MEASUREMENT_SYSTEM, value.storageKey) }
        }

    var appLanguage: AppLanguage
        get() = _appLanguage.value
        set(value) {
            _appLanguage.value = value
            prefs.edit { putString(Keys.APP_LANGUAGE, value.storageKey) }
        }

    var autoPauseEnabled: Boolean
        get() = _autoPauseEnabled.value
        set(value) {
            _autoPauseEnabled.value = value
            prefs.edit { putBoolean(Keys.AUTO_PAUSE_ENABLED, value) }
        }
}
