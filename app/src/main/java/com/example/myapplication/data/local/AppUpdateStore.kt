package com.example.myapplication.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.model.AppUpdateLocalState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appUpdateDataStore by preferencesDataStore(name = "app_update_state")

interface AppUpdateStateStore {
    val stateFlow: Flow<AppUpdateLocalState>

    suspend fun saveLastCheckAt(value: Long)

    suspend fun saveCachedMetadataJson(value: String)

    suspend fun clearCachedMetadata()

    suspend fun saveActiveDownloadId(value: Long)

    suspend fun clearActiveDownloadId()
}

class AppUpdateStore(
    private val context: Context,
) : AppUpdateStateStore {
    override val stateFlow: Flow<AppUpdateLocalState> = context.appUpdateDataStore.data.map { preferences ->
        AppUpdateLocalState(
            lastCheckAt = preferences[PreferencesKeys.lastCheckAt] ?: 0L,
            cachedMetadataJson = preferences[PreferencesKeys.cachedMetadataJson].orEmpty(),
            activeDownloadId = preferences[PreferencesKeys.activeDownloadId] ?: 0L,
        )
    }

    override suspend fun saveLastCheckAt(value: Long) {
        context.appUpdateDataStore.edit { preferences ->
            preferences[PreferencesKeys.lastCheckAt] = value
        }
    }

    override suspend fun saveCachedMetadataJson(value: String) {
        context.appUpdateDataStore.edit { preferences ->
            preferences[PreferencesKeys.cachedMetadataJson] = value
        }
    }

    override suspend fun clearCachedMetadata() {
        context.appUpdateDataStore.edit { preferences ->
            preferences[PreferencesKeys.cachedMetadataJson] = ""
        }
    }

    override suspend fun saveActiveDownloadId(value: Long) {
        context.appUpdateDataStore.edit { preferences ->
            preferences[PreferencesKeys.activeDownloadId] = value
        }
    }

    override suspend fun clearActiveDownloadId() {
        context.appUpdateDataStore.edit { preferences ->
            preferences[PreferencesKeys.activeDownloadId] = 0L
        }
    }

    private object PreferencesKeys {
        val lastCheckAt = longPreferencesKey("last_check_at")
        val cachedMetadataJson = stringPreferencesKey("cached_metadata_json")
        val activeDownloadId = longPreferencesKey("active_download_id")
    }
}
