package no.bikespeed.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import no.bikespeed.app.ui.BikeSpeedNavHost
import no.bikespeed.app.ui.theme.BikeSpeedTheme

// AppCompatActivity, not ComponentActivity: the per-app locale backport
// (AppCompatDelegate.setApplicationLocales) needs it on pre-33 devices.
class MainActivity : AppCompatActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Only a *location* grant unlocks the sensors — the same request can carry
        // POST_NOTIFICATIONS, whose grant must not start a location request the app has no
        // right to make. See PermissionGating.
        if (PermissionGating.grantsLocation(grants)) {
            container.startSensors()
        }
    }

    private val container: AppContainer
        get() = (application as BikeSpeedApplication).container

    // The configuration Compose sees, fed from onConfigurationChanged. On API 29–32 the
    // per-app-locale backport applies a language change by calling onConfigurationChanged
    // directly and never dispatches it to the views, so Compose's own LocalConfiguration
    // stays stale and the open screen keeps its old strings. Never-equal: the backport
    // updates the Resources in place, and every delivery must invalidate stringResource.
    private var configuration by mutableStateOf(Configuration(), neverEqualPolicy())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Designed to run mounted on a bike handlebar: the screen must not sleep mid-ride.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Sensor start lives in onStart, which follows: it must also cover every return to
        // the foreground after onStop released the sensors.
        val missing = PermissionGating.permissionsToRequest(
            hasLocation = hasLocationPermission(),
            hasNotifications = hasPermission(Manifest.permission.POST_NOTIFICATIONS),
        )
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }

        // The in-app language override. Unlike iOS (where String(localized:) ignored the
        // override and needed a manual bundle lookup), setApplicationLocales makes every
        // getString follow it — including the recording notification.
        lifecycleScope.launch {
            container.settingsStore.appLanguageFlow.collect { language ->
                AppCompatDelegate.setApplicationLocales(
                    language.languageTag
                        ?.let { LocaleListCompat.forLanguageTags(it) }
                        ?: LocaleListCompat.getEmptyLocaleList() // SYSTEM: follow the device
                )
            }
        }

        configuration = Configuration(resources.configuration)
        setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                BikeSpeedTheme {
                    BikeSpeedNavHost(container = container)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configuration = Configuration(newConfig)
    }

    override fun onStart() {
        super.onStart()
        if (hasLocationPermission()) {
            container.startSensors()
        }
    }

    override fun onStop() {
        super.onStop()
        // No trip recording? Then a locked screen doesn't need high-accuracy GPS and two
        // 16–20 Hz sensor listeners running for the life of the process. A RUNNING trip
        // keeps everything on — the foreground service is holding the process for it.
        container.releaseSensorsUnlessRecording()
    }

    // Either grant will do: "approximate location only" is a choice the system dialog
    // offers, and a coarse-only pipeline degrades to a wide accuracy gate, not a crash.
    private fun hasLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
