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
    val contextSize: Int = 4096,
    val threadCount: Int = 4,
    val isDarkMode: Boolean = true,
    val fontSize: Int = 14,
    val autoSaveOnPreview: Boolean = true,
    val systemPrompt: String = "You are a helpful expert AI coding assistant.",
    val useModelBForReviewAndDebug: Boolean = false,
    /** When true, offload layers to GPU (needs Vulkan-built llama_engine.so). */
    val useGpu: Boolean = true,
    /** Layers to offload; 99 = all. Ignored when useGpu is false. */
    val gpuLayers: Int = 99
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
        val SYSTEM_PROMPT = androidx.datastore.preferences.core.stringPreferencesKey("system_prompt")
        val USE_MODEL_B = booleanPreferencesKey("use_model_b")
        val USE_GPU = booleanPreferencesKey("use_gpu")
        val GPU_LAYERS = intPreferencesKey("gpu_layers")
    }

    init {
        dataStore?.let { store ->
            viewModelScope.launch {
                store.data.collect { prefs ->
                    _settings.value = AppSettings(
                        contextSize = prefs[Keys.CONTEXT_SIZE] ?: 4096,
                        threadCount = prefs[Keys.THREAD_COUNT] ?: 4,
                        isDarkMode = prefs[Keys.DARK_MODE] ?: true,
                        autoSaveOnPreview = prefs[Keys.AUTO_SAVE] ?: true,
                        systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: "You are a helpful expert AI coding assistant.",
                        useModelBForReviewAndDebug = prefs[Keys.USE_MODEL_B] ?: false,
                        useGpu = prefs[Keys.USE_GPU] ?: true,
                        gpuLayers = prefs[Keys.GPU_LAYERS] ?: 99
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
                prefs[Keys.SYSTEM_PROMPT] = settings.systemPrompt
                prefs[Keys.USE_MODEL_B] = settings.useModelBForReviewAndDebug
                prefs[Keys.USE_GPU] = settings.useGpu
                prefs[Keys.GPU_LAYERS] = settings.gpuLayers
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

    fun updateSystemPrompt(prompt: String) {
        _settings.value = _settings.value.copy(systemPrompt = prompt)
        persist(_settings.value)
    }

    fun toggleUseModelB(enabled: Boolean) {
        _settings.value = _settings.value.copy(useModelBForReviewAndDebug = enabled)
        persist(_settings.value)
    }

    fun toggleUseGpu(enabled: Boolean) {
        _settings.value = _settings.value.copy(useGpu = enabled)
        persist(_settings.value)
    }

    fun updateGpuLayers(layers: Int) {
        _settings.value = _settings.value.copy(gpuLayers = layers.coerceIn(0, 99))
        persist(_settings.value)
    }

    /** Effective layers to pass to native load (0 if GPU disabled). */
    fun effectiveGpuLayers(): Int {
        val s = _settings.value
        return if (s.useGpu) s.gpuLayers else 0
    }
}
