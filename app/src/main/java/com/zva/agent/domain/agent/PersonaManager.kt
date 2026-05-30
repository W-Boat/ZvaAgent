package com.zva.agent.domain.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zva_settings")

data class AppSettings(
    val apiEndpoint: String = "",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val personaName: String = "Zva",
    val temperature: Float = 0.7f,
    val userName: String = "",
)

@Singleton
class PersonaManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val API_ENDPOINT = stringPreferencesKey("api_endpoint")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val PERSONA_NAME = stringPreferencesKey("persona_name")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val USER_NAME = stringPreferencesKey("user_name")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            apiEndpoint = prefs[Keys.API_ENDPOINT] ?: "",
            apiKey = prefs[Keys.API_KEY] ?: "",
            model = prefs[Keys.MODEL] ?: "gpt-4o-mini",
            personaName = prefs[Keys.PERSONA_NAME] ?: "Zva",
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7f,
            userName = prefs[Keys.USER_NAME] ?: "",
        )
    }

    val isOnboarded: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDED] ?: false
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.API_ENDPOINT] = settings.apiEndpoint
            prefs[Keys.API_KEY] = settings.apiKey
            prefs[Keys.MODEL] = settings.model
            prefs[Keys.PERSONA_NAME] = settings.personaName
            prefs[Keys.TEMPERATURE] = settings.temperature
            prefs[Keys.USER_NAME] = settings.userName
        }
    }

    suspend fun markOnboarded() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDED] = true
        }
    }
}
