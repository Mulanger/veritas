package com.veritas.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

val veritasLogBuffer = VeritasLogBuffer()

@HiltAndroidApp
class VeritasApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Timber.plant(veritasLogBuffer)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
