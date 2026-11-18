package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_preferences")

enum class ThemePreference(val label: String) {
    DARK("Futuristic Dark"),
    LIGHT("Clean Light"),
    SYSTEM("System Default")
}

data class LocalAppPreferences(
    val themePreference: ThemePreference = ThemePreference.DARK,
    val voiceRate: Float = 1.0f,
    val voicePitch: Float = 1.12f,
    val autoSpeakReplies: Boolean = false,
    val selectedVoiceName: String = "",
    val githubOwnerRepo: String = "",
    val githubWorkflowFile: String = "android-build.yml",
    val githubToken: String = ""
)

class UserPreferencesStore(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val VOICE_RATE = floatPreferencesKey("voice_rate")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val VOICE_NAME = stringPreferencesKey("voice_name")
        val GH_REPO = stringPreferencesKey("gh_owner_repo")
        val GH_WORKFLOW = stringPreferencesKey("gh_workflow")
        val GH_TOKEN = stringPreferencesKey("gh_token")
    }

    val preferencesFlow: Flow<LocalAppPreferences> = context.jarvisDataStore.data.map { prefs ->
        val rawTheme = prefs[Keys.THEME] ?: ThemePreference.DARK.name
        val theme = ThemePreference.entries.firstOrNull { it.name == rawTheme } ?: ThemePreference.DARK
        LocalAppPreferences(
            themePreference = theme,
            voiceRate = prefs[Keys.VOICE_RATE] ?: 1.0f,
            voicePitch = prefs[Keys.VOICE_PITCH] ?: 1.12f,
            autoSpeakReplies = prefs[Keys.AUTO_SPEAK] ?: false,
            selectedVoiceName = prefs[Keys.VOICE_NAME] ?: "",
            githubOwnerRepo = prefs[Keys.GH_REPO] ?: "",
            githubWorkflowFile = prefs[Keys.GH_WORKFLOW] ?: "android-build.yml",
            githubToken = prefs[Keys.GH_TOKEN] ?: ""
        )
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.jarvisDataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setVoiceSettings(rate: Float, pitch: Float, autoSpeak: Boolean, voiceName: String) {
        context.jarvisDataStore.edit {
            it[Keys.VOICE_RATE] = rate.coerceIn(0.5f, 2.0f)
            it[Keys.VOICE_PITCH] = pitch.coerceIn(0.7f, 1.5f)
            it[Keys.AUTO_SPEAK] = autoSpeak
            it[Keys.VOICE_NAME] = voiceName
        }
    }

    suspend fun setGitHubConfig(ownerRepo: String, workflowFile: String, token: String) {
        context.jarvisDataStore.edit {
            it[Keys.GH_REPO] = ownerRepo.trim()
            it[Keys.GH_WORKFLOW] = workflowFile.trim().ifBlank { "android-build.yml" }
            it[Keys.GH_TOKEN] = token.trim()
        }
    }
}
