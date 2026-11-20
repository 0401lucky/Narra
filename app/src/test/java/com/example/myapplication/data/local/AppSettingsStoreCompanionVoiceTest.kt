package com.example.myapplication.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.myapplication.model.*
import com.google.gson.JsonParser
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsStoreCompanionVoiceTest {
    @get:Rule
    val files = TemporaryFolder()
    private val key = stringPreferencesKey("companion_character_settings_json")

    @Test
    fun legacyWithoutVoice_defaultsOffAndExplicitVoicePatchKeepsOldBackgroundOff() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice", name = "Alice")), "alice")
            store.saveVoiceSynthesisSettings(
                VoiceSynthesisSettings(
                    enabled = false,
                    apiKey = "fake-private-key"
                )
            )
            advanceUntilIdle()
            val before = store.settingsFlow.first()
            assertFalse(before.companionSettings("alice").voice.enabled)
            assertFalse(before.companionSettings("alice").voice.autoPlay)
            val prefs = CompanionVoicePreferences(
                enabled = true,
                autoPlay = true,
                readMode = CompanionVoiceReadMode.TRANSLATED,
                sourceLanguage = " 中文 ",
                targetLanguage = " 日语 ",
                cacheLimitBytes = 32L * 1024 * 1024
            )
            val result = store.updateCompanionSettings(
                "alice",
                0,
                CompanionSettingsPatch(voice = prefs)
            ) as CompanionSettingsWriteResult.Saved
            advanceUntilIdle()
            val after = store.settingsFlow.first()
            assertEquals(prefs.normalized(), result.settings.voice)
            assertEquals(result.settings.voice, after.companionSettings("alice").voice)
            assertEquals(before.voiceSynthesisSettings, after.voiceSynthesisSettings)
            assertFalse(after.voiceSynthesisSettings.enabled)
            assertEquals("fake-private-key", after.voiceSynthesisSettings.apiKey)
            assertFalse(data.data.first().asMap().values.any {
                it.toString().contains("fake-private-key")
            })
        }
    }

    @Test
    fun voiceCasConflict_doesNotOverwriteSavedWritingOrUnknownFields() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            data.edit {
                it[key] =
                    """{"alice":{"schemaVersion":1,"revision":3,"zoneId":"Asia/Shanghai","futureRoot":7,"writing":{"extraStyle":"原文风"},"voice":{"schemaVersion":1,"futureVoice":"keep"}}}"""
            }
            val saved = store.updateCompanionSettings(
                "alice",
                3,
                CompanionSettingsPatch(voice = CompanionVoicePreferences(enabled = true))
            )
            assertTrue(saved is CompanionSettingsWriteResult.Saved)
            val beforeConflict = data.data.first()[key]
            assertSame(
                CompanionSettingsWriteResult.Conflict,
                store.updateCompanionSettings(
                    "alice",
                    3,
                    CompanionSettingsPatch(voice = CompanionVoicePreferences(enabled = false))
                )
            )
            assertEquals(beforeConflict, data.data.first()[key])
            val root = JsonParser.parseString(beforeConflict).asJsonObject["alice"].asJsonObject
            assertEquals("keep", root["voice"].asJsonObject["futureVoice"].asString)
            assertEquals("原文风", root["writing"].asJsonObject["extraStyle"].asString)
            assertEquals(7, root["futureRoot"].asInt)
        }
    }

    @Test
    fun futureVoiceVersion_refusesVoiceWriteAndOtherPatchesPreserveIt() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            data.edit {
                it[key] =
                    """{"alice":{"schemaVersion":1,"revision":5,"voice":{"schemaVersion":9,"enabled":true,"future":123}}}"""
            }
            val original = data.data.first()[key]
            assertEquals(
                9,
                store.settingsFlow.first().companionSettings("alice").voice.schemaVersion
            )
            assertSame(
                CompanionSettingsWriteResult.UnsupportedVersion,
                store.updateCompanionSettings(
                    "alice",
                    5,
                    CompanionSettingsPatch(voice = CompanionVoicePreferences(enabled = false))
                )
            )
            assertEquals(original, data.data.first()[key])
            assertTrue(
                store.updateCompanionSettings(
                    "alice",
                    5,
                    CompanionSettingsPatch(writing = CompanionWritingSettings(extraStyle = "新文风"))
                ) is CompanionSettingsWriteResult.Saved
            )
            val root =
                JsonParser.parseString(data.data.first()[key]).asJsonObject["alice"].asJsonObject
            assertEquals(9, root["voice"].asJsonObject["schemaVersion"].asInt)
            assertEquals(123, root["voice"].asJsonObject["future"].asInt)
            assertTrue(root["voice"].asJsonObject["enabled"].asBoolean)
        }
    }

    @Test
    fun malformedIndividualVoiceFields_keepSafeDefaultsAndOtherKnownFields() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            data.edit {
                it[key] =
                    """{"alice":{"schemaVersion":1,"revision":2,"voice":{"enabled":"true","autoPlay":true,"readMode":"future","sourceLanguage":{},"targetLanguage":"英语","cacheLimitBytes":9223372036854775807}}}"""
            }
            val voice = store.settingsFlow.first().companionSettings("alice").voice
            assertFalse(voice.enabled)
            assertTrue(voice.autoPlay)
            assertEquals(CompanionVoiceReadMode.ORIGINAL, voice.readMode)
            assertEquals("自动检测", voice.sourceLanguage)
            assertEquals("英语", voice.targetLanguage)
            assertEquals(CompanionVoicePreferences.MAX_CACHE_LIMIT_BYTES, voice.cacheLimitBytes)
        }
    }

    @Test
    fun invalidVoicePatch_andMissingCharacter_doNotPartiallyWrite() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            val before = data.data.first()[key]
            assertTrue(runCatching {
                store.updateCompanionSettings(
                    "alice",
                    0,
                    CompanionSettingsPatch(
                        voice = CompanionVoicePreferences(
                            targetLanguage = "x".repeat(61)
                        )
                    )
                )
            }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(before, data.data.first()[key])
            assertSame(
                CompanionSettingsWriteResult.CharacterMissing,
                store.updateCompanionSettings(
                    "gone",
                    0,
                    CompanionSettingsPatch(voice = CompanionVoicePreferences(enabled = true))
                )
            )
            assertEquals(before, data.data.first()[key])
        }
    }

    private suspend fun TestScope.withStore(block: suspend (AppSettingsStore, DataStore<Preferences>) -> Unit) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val data = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(files.root, "voice.preferences_pb") })
        val values = mutableMapOf<String, String>()
        val maps = mutableMapOf<String, Map<String, String>>()
        val secure = object : SettingsSecureValueStore {
            override fun getString(key: String) = values[key].orEmpty()
            override fun putString(key: String, value: String) {
                values[key] = value
            }

            override fun getStringMap(key: String) = maps[key].orEmpty()
            override fun putStringMap(key: String, values: Map<String, String>) {
                maps[key] = values
            }
        }
        val store = AppSettingsStore(data, secure, scope, dispatcher)
        try {
            advanceUntilIdle(); block(store, data)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
}
