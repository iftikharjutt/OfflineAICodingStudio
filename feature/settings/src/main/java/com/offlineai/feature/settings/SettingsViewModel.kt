package com.offlineai.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppSettings(
    val contextSize: Int = 8192,
    val threadCount: Int = 4,
    val isDarkMode: Boolean = true,
    val fontSize: Int = 14,
    val autoSaveOnPreview: Boolean = true
)

class SettingsViewModel(
    private val dataStore: DataStore<Preferences>? = null
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private object Keys {
        val CONTEXT_SIZE = intPreferencesKey("context_size")
        val THREAD_COUNT = intPreferencesKey("thread_count")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
    }

    init {
        dataStore?.let { store ->
            viewModelScope.launch {
                store.data.collect { prefs ->
                    _settings.value = AppSettings(
                        contextSize = prefs[Keys.CONTEXT_SIZE] ?: 8192,
                        threadCount = prefs[Keys.THREAD_COUNT] ?: 4,
                        isDarkMode = prefs[Keys.DARK_MODE] ?: true,
                        autoSaveOnPreview = prefs[Keys.AUTO_SAVE] ?: true
                    )
                }
            }
        }
    }

    private fun persist(settings: AppSettings) {
        viewModelScope.launch {
            dataStore?.edit { prefs ->
                prefs[Keys.CONTEXT_SIZE] = settings.contextSize
                prefs[Keys.THREAD_COUNT] = settings.threadCount
                prefs[Keys.DARK_MODE] = settings.isDarkMode
                prefs[Keys.AUTO_SAVE] = settings.autoSaveOnPreview
            }
        }
    }

    fun updateContextSize(size: Int) {
        _settings.value = _settings.value.copy(contextSize = size)
        persist(_settings.value)
    }

    fun updateThreadCount(threads: Int) {
        _settings.value = _settings.value.copy(threadCount = threads)
        persist(_settings.value)
    }

    fun toggleDarkMode(enabled: Boolean) {
        _settings.value = _settings.value.copy(isDarkMode = enabled)
        persist(_settings.value)
    }

    fun toggleAutoSave(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoSaveOnPreview = enabled)
        persist(_settings.value)
    }
}
