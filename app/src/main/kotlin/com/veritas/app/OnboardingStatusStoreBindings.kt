package com.veritas.app

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
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

private val Context.veritasPreferences by preferencesDataStore(name = "veritas_preferences")

private val HAS_COMPLETED_ONBOARDING_KEY = booleanPreferencesKey("has_completed_onboarding")

@Singleton
class DataStoreOnboardingStatusStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OnboardingStatusStore {
    override val hasCompletedOnboarding: Flow<Boolean> =
        context.veritasPreferences.data.map { preferences: Preferences ->
            preferences[HAS_COMPLETED_ONBOARDING_KEY] ?: false
        }

    override suspend fun setHasCompletedOnboarding(completed: Boolean) {
        context.veritasPreferences.edit { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING_KEY] = completed
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
