package no.bikespeed.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
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
        if (grants.values.any { it }) {
            container.startSensors()
        }
    }

    private val container: AppContainer
        get() = (application as BikeSpeedApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Designed to run mounted on a bike handlebar: the screen must not sleep mid-ride.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (hasLocationPermission()) {
            container.startSensors()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    // For the recording service's persistent notification. A denial doesn't
                    // block recording — the service still runs, just without its pill.
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            )
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

        setContent {
            BikeSpeedTheme {
                BikeSpeedNavHost(container = container)
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
