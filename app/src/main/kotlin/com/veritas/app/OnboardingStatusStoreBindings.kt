package com.veritas.app

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.veritas.feature.onboarding.OnboardingStatusStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreOnboardingStatusStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OnboardingStatusStore {
    override val hasCompletedOnboarding: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences: Preferences ->
            preferences[SettingsKeys.HAS_COMPLETED_ONBOARDING] ?: false
        }

    override suspend fun setHasCompletedOnboarding(completed: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsKeys.HAS_COMPLETED_ONBOARDING] = completed
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingStatusStoreBindings {
    @Binds
    @Singleton
    abstract fun bindOnboardingStatusStore(
        dataStoreOnboardingStatusStore: DataStoreOnboardingStatusStore,
    ): OnboardingStatusStore
}
