package com.veritas.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        runTest {
            repository = SettingsRepository(ApplicationProvider.getApplicationContext<Context>())
            repository.resetAll()
        }
    }

    @After
    fun tearDown() {
        runTest {
            repository.resetAll()
        }
    }

    @Test
    fun settingsDefaultToPrivateLocalBehavior() =
        runTest {
            val settings = repository.settings.first()

            assertFalse(settings.overlayEnabled)
            assertTrue(settings.bubbleHaptics)
            assertTrue(settings.modelAutoUpdate)
            assertTrue(settings.modelWifiOnly)
            assertFalse(settings.telemetryOptIn)
            assertFalse(settings.telemetryPromptShown)
        }

    @Test
    fun telemetryChoicePersistsAndMarksPromptShown() =
        runTest {
            repository.setTelemetryOptIn(true)

            val settings = repository.settings.first()

            assertTrue(settings.telemetryOptIn)
            assertTrue(settings.telemetryPromptShown)
        }

    @Test
    fun resetAllRestoresDefaults() =
        runTest {
            repository.setOverlayEnabled(true)
            repository.setModelAutoUpdate(false)

            repository.resetAll()
            val settings = repository.settings.first()

            assertFalse(settings.overlayEnabled)
            assertTrue(settings.modelAutoUpdate)
        }
}
