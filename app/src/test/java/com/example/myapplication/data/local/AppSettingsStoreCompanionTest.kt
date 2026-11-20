package com.example.myapplication.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.CompanionSettingsPatch
import com.example.myapplication.model.CompanionSettingsWriteResult
import com.example.myapplication.model.companionSettings
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.DateTimeException

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsStoreCompanionTest {
    @get:Rule
    val files = TemporaryFolder()
    private val key = stringPreferencesKey("companion_character_settings_json")

    @Test
    fun concurrentRevisionUpdates_onlyOneWins() = runTest {
        withStore { store, _ ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            val updates = listOf("Asia/Shanghai", "Europe/Paris").map { zone ->
                async { store.updateCompanionSettings("alice", 0, CompanionSettingsPatch(zone)) }
            }.awaitAll()
            assertEquals(1, updates.count { it is CompanionSettingsWriteResult.Saved })
            assertEquals(1, updates.count { it == CompanionSettingsWriteResult.Conflict })
            val expected =
                (updates.single { it is CompanionSettingsWriteResult.Saved } as CompanionSettingsWriteResult.Saved).settings
            advanceUntilIdle()
            assertEquals(expected, store.settingsFlow.first().companionSettings("alice"))
        }
    }

    @Test
    fun oldAssistantSave_preservesSettingsAndUnknownFields() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice"), Assistant(id = "bob")), "alice")
            data.edit {
                it[key] =
                    """{"alice":{"schemaVersion":1,"revision":3,"zoneId":"UTC","future":"keep"},"bob":{"revision":2,"zoneId":"Asia/Tokyo"}}"""
            }
            store.saveAssistants(
                listOf(
                    Assistant(id = "alice", name = "新名字"),
                    Assistant(id = "bob")
                ), "alice"
            )
            val updated =
                store.updateCompanionSettings("alice", 3, CompanionSettingsPatch(" Europe/Paris "))
            assertTrue(updated is CompanionSettingsWriteResult.Saved)
            val root = JsonParser.parseString(data.data.first()[key]).asJsonObject
            assertEquals("keep", root["alice"].asJsonObject["future"].asString)
            assertEquals("Europe/Paris", root["alice"].asJsonObject["zoneId"].asString)
            assertEquals("Asia/Tokyo", root["bob"].asJsonObject["zoneId"].asString)
            store.saveAssistants(listOf(Assistant(id = "bob")), "bob")
            val retained = JsonParser.parseString(data.data.first()[key]).asJsonObject
            assertFalse(retained.has("alice"))
            assertTrue(retained.has("bob"))
            assertEquals(
                CompanionSettingsWriteResult.CharacterMissing,
                store.updateCompanionSettings("alice", 4, CompanionSettingsPatch("UTC"))
            )
        }
    }

    @Test
    fun futureVersionAndMalformedEntry_areNotDowngraded() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            for (raw in listOf(
                """{"alice":{"schemaVersion":9,"revision":4,"zoneId":"UTC","future":"keep"}}""",
                """{"alice":{"schemaVersion":1,"revision":-1,"zoneId":"UTC"}}""",
                """{"alice":false}"""
            )) {
                data.edit { it[key] = raw }
                assertEquals(
                    CompanionSettingsWriteResult.UnsupportedVersion,
                    store.updateCompanionSettings(
                        "alice",
                        4,
                        CompanionSettingsPatch("Europe/Paris")
                    )
                )
                assertEquals(raw, data.data.first()[key])
            }
        }
    }

    @Test
    fun missingFieldsDefaultAndInvalidZone_doesNotWrite() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            data.edit { it[key] = """{"alice":{}}""" }
            advanceUntilIdle()
            assertEquals(0L, store.settingsFlow.first().companionSettings("alice").revision)
            assertEquals("", store.settingsFlow.first().companionSettings("alice").zoneId)
            val before = data.data.first()[key]
            val result = runCatching {
                store.updateCompanionSettings(
                    "alice",
                    0,
                    CompanionSettingsPatch("not/a-zone")
                )
            }
            assertTrue(result.exceptionOrNull() is DateTimeException)
            assertEquals(before, data.data.first()[key])
            assertTrue(
                store.updateCompanionSettings(
                    "alice",
                    0,
                    CompanionSettingsPatch("")
                ) is CompanionSettingsWriteResult.Saved
            )
        }
    }

    @Test
    fun fileReopen_preservesCharacterZoneAndRevision() = runTest {
        withStore { store, _ ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            assertTrue(
                store.updateCompanionSettings(
                    "alice",
                    0,
                    CompanionSettingsPatch("Pacific/Kiritimati")
                ) is CompanionSettingsWriteResult.Saved
            )
        }
        withStore { store, _ ->
            advanceUntilIdle()
            val recovered = store.settingsFlow.first().companionSettings("alice")
            assertEquals(1L, recovered.revision)
            assertEquals("Pacific/Kiritimati", recovered.zoneId)
        }
    }

    private suspend fun TestScope.withStore(
        block: suspend (AppSettingsStore, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> Unit,
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val data = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(files.root, "companion.preferences_pb") })
        val secure = object : SettingsSecureValueStore {
            override fun getString(key: String) = ""
            override fun putString(key: String, value: String) = Unit
            override fun getStringMap(key: String) = emptyMap<String, String>()
            override fun putStringMap(key: String, values: Map<String, String>) = Unit
        }
        val store = AppSettingsStore(data, secure, scope, dispatcher)
        try {
            advanceUntilIdle()
            block(store, data)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
}
