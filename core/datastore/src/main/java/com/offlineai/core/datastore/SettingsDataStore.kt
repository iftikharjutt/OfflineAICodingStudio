package com.offlineai.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// Singleton per process — safe across configuration changes
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
