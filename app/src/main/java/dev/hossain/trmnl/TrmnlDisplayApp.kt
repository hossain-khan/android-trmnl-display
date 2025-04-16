package dev.hossain.trmnl

import android.app.Application
import dev.hossain.trmnl.di.AppComponent
import timber.log.Timber

/**
 * Application class for the app with key initializations.
 */
class TrmnlDisplayApp : Application() {
    private val appComponent: AppComponent by lazy { AppComponent.create(this) }

    fun appComponent(): AppComponent = appComponent

    override fun onCreate() {
        super.onCreate()
        installLoggingTree()
    }

    private fun installLoggingTree() {
        if (BuildConfig.DEBUG) {
            // Plant a debug tree for development builds
            Timber.plant(Timber.DebugTree())
        }
    }
}
