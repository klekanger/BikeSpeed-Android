package no.bikespeed.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import no.bikespeed.app.ui.main.MainScreen
import no.bikespeed.app.ui.theme.BikeSpeedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Designed to run mounted on a bike handlebar: the screen must not sleep mid-ride.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val container = (application as BikeSpeedApplication).container

        setContent {
            BikeSpeedTheme {
                MainScreen(
                    container = container,
                    // Navigation to the trip log and settings lands with those screens.
                    onTripLog = {},
                    onSettings = {},
                )
            }
        }
    }
}
