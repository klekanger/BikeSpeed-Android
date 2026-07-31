package no.bikespeed.app

import android.app.Application

class BikeSpeedApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(context = this)
    }
}
