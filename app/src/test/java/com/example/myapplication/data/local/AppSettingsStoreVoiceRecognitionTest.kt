package com.example.myapplication.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.myapplication.model.VoiceRecognitionEngine
import com.example.myapplication.model.VoiceRecognitionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsStoreVoiceRecognitionTest {
    @Test
    fun saveVoiceRecognitionSettings_keepsApiKeyOutOfDataStoreJsonAndRoundTrips() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val secureStore = InMemorySecureValueStore()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val sharingScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val store = AppSettingsStore(
            dataStore = dataStore,
            secureValueStore = secureStore,
            sharingScope = sharingScope,
            settingsDispatcher = testDispatcher,
        )

        try {
            advanceUntilIdle()
            store.saveVoiceRecognitionSettings(
                VoiceRecognitionSettings(
                    enabled = true,
                    engine = VoiceRecognitionEngine.REMOTE_MODEL,
                    apiKey = "  sk-secret-key  ",
                    baseUrl = "  https://api.example.com/v1  ",
                    model = "whisper-large",
                    language = "zh",
                ),
            )
            advanceUntilIdle()

            // 明文 Key 不允许进入 DataStore
            val storedJson = dataStore.data.first()[
                stringPreferencesKey("voice_recognition_settings_json"),
            ].orEmpty()
            assertTrue(storedJson.isNotBlank())
            assertFalse(storedJson.contains("sk-secret-key"))

            // Key 只走加密通道，且已裁剪空白
            assertEquals("sk-secret-key", secureStore.getString("voice_recognition_api_key"))

            // 读取时把加密 Key 注回，整体往返一致
            val restored = store.settingsFlow.first().voiceRecognitionSettings
            assertTrue(restored.enabled)
            assertEquals(VoiceRecognitionEngine.REMOTE_MODEL, restored.engine)
            assertEquals("sk-secret-key", restored.apiKey)
            assertEquals("https://api.example.com/v1", restored.baseUrl)
            assertEquals("whisper-large", restored.model)
            assertEquals("zh", restored.language)
        } finally {
            sharingScope.cancel()
        }
    }

    @Test
    fun settingsFlow_missingVoiceRecognitionKeyFallsBackToDisabledDefaults() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val secureStore = InMemorySecureValueStore()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val sharingScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val store = AppSettingsStore(
            dataStore = dataStore,
            secureValueStore = secureStore,
            sharingScope = sharingScope,
            settingsDispatcher = testDispatcher,
        )

        try {
            advanceUntilIdle()
            val settings = store.settingsFlow.first().voiceRecognitionSettings

            // 老用户升级后行为零变化：默认关闭、默认走系统通道
            assertFalse(settings.enabled)
            assertEquals(VoiceRecognitionEngine.SYSTEM, settings.engine)
            assertEquals("", settings.apiKey)
            assertEquals("", settings.baseUrl)
        } finally {
            sharingScope.cancel()
        }
    }
}

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

private class InMemorySecureValueStore : SettingsSecureValueStore {
    private val strings = mutableMapOf<String, String>()
    private val maps = mutableMapOf<String, Map<String, String>>()

    override fun getString(key: String): String = strings[key].orEmpty()

    override fun putString(key: String, value: String) {
        strings[key] = value
    }

    override fun getStringMap(key: String): Map<String, String> = maps[key].orEmpty()

    override fun putStringMap(key: String, values: Map<String, String>) {
        maps[key] = values
    }
}
