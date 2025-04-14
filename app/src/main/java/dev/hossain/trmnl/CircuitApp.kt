package dev.hossain.trmnl

import android.app.Application
import dev.hossain.trmnl.di.AppComponent

/**
 * Application class for the app with key initializations.
 */
class CircuitApp : Application() {
    private val appComponent: AppComponent by lazy { AppComponent.create(this) }

    fun appComponent(): AppComponent = appComponent
}
