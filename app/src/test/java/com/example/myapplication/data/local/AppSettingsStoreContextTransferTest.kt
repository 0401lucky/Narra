package com.example.myapplication.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.myapplication.model.*
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsStoreContextTransferTest {
    @get:Rule
    val files = TemporaryFolder()
    private val planHash = "a".repeat(64)
    private val companionKey = stringPreferencesKey("companion_character_settings_json")

    @Test
    fun sameReceiptAfterUserEditsAndFileReopen_doesNotOverwriteOrIncrementRevision() = runTest {
        var target: ContextTransferSettingsTarget? = null
        var userRevision = 0L
        withStore { store, _ ->
            store.saveAssistants(
                listOf(
                    Assistant(id = "alice", name = "原角色"),
                    Assistant(id = "bob", name = "旁人")
                ), "bob"
            )
            val current = store.settingsFlow.first()
            target = ContextTransferSettingsTarget(
                "alice",
                current.resolvedAssistants().single { it.id == "alice" },
                importedAssistant = Assistant(id = "alice", name = "导入角色"),
                companionPatch = CompanionSettingsPatch(zoneId = "UTC")
            )
            assertEquals(
                ContextTransferSettingsResult.Applied,
                store.applyContextTransferTarget("same-operation", planHash, target!!)
            )
            val saved = store.settingsFlow.first()
            assertEquals(1L, saved.companionSettings("alice").revision)
            assertEquals("bob", saved.selectedAssistantId)
            store.saveAssistants(
                saved.assistants.map { if (it.id == "alice") it.copy(name = "用户后续名字") else it },
                "bob"
            )
            store.updateCompanionSettings("alice", 1, CompanionSettingsPatch(zoneId = "Asia/Tokyo"))
            userRevision = store.settingsFlow.first().companionSettings("alice").revision
            assertEquals(
                ContextTransferSettingsResult.AlreadyApplied,
                store.applyContextTransferTarget("same-operation", planHash, target!!)
            )
        }
        withStore { store, _ ->
            assertEquals(
                ContextTransferSettingsResult.AlreadyApplied,
                store.applyContextTransferTarget("same-operation", planHash, target!!)
            )
            val current = store.settingsFlow.first()
            assertEquals("用户后续名字", current.assistants.single { it.id == "alice" }.name)
            assertEquals("Asia/Tokyo", current.companionSettings("alice").zoneId)
            assertEquals(userRevision, current.companionSettings("alice").revision)
            assertEquals("bob", current.selectedAssistantId)
        }
    }

    @Test
    fun assistantOrCompanionCasConflict_leavesAllTargetFieldsAndReceiptUnchanged() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice", name = "原角色")), "alice")
            val current = store.settingsFlow.first()
            val target = ContextTransferSettingsTarget(
                "alice",
                current.assistants.single(),
                importedAssistant = current.assistants.single().copy(name = "导入"),
                companionPatch = CompanionSettingsPatch(zoneId = "UTC")
            )
            store.updateCompanionSettings("alice", 0, CompanionSettingsPatch(zoneId = "Asia/Tokyo"))
            val before = data.data.first()
            assertEquals(
                ContextTransferSettingsResult.Conflict,
                store.applyContextTransferTarget("old-revision", planHash, target)
            )
            assertEquals(before, data.data.first())
            val fresh = target.copy(expectedCompanionRevision = 1)
            store.saveAssistants(
                listOf(current.assistants.single().copy(name = "用户新名字")),
                "alice"
            )
            val newBefore = data.data.first()
            assertEquals(
                ContextTransferSettingsResult.Conflict,
                store.applyContextTransferTarget("old-assistant", planHash, fresh)
            )
            assertEquals(newBefore, data.data.first())
        }
    }

    @Test
    fun lateRetryAfterTargetDeletion_cannotRecreateCharacterOrOldCompanionSource() = runTest {
        withStore { store, _ ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            val target = ContextTransferSettingsTarget(
                "alice",
                store.settingsFlow.first().assistants.single(),
                importedAssistant = Assistant(id = "alice", name = "导入角色"),
                companionPatch = CompanionSettingsPatch(zoneId = "UTC")
            )
            assertEquals(
                ContextTransferSettingsResult.Applied,
                store.applyContextTransferTarget("deleted-target", planHash, target)
            )
            store.saveAssistants(emptyList(), DEFAULT_ASSISTANT_ID)
            assertEquals(
                ContextTransferSettingsResult.AlreadyApplied,
                store.applyContextTransferTarget("deleted-target", planHash, target)
            )
            assertTrue(store.settingsFlow.first().assistants.isEmpty())
            assertFalse(store.settingsFlow.first().companionCharacterSettings.containsKey("alice"))
        }
    }

    @Test
    fun simultaneousSameOperation_hasOneActualApplyAndOnePersistentReceipt() = runTest {
        withStore { store, _ ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            val target = ContextTransferSettingsTarget(
                "alice",
                store.settingsFlow.first().assistants.single(),
                companionPatch = CompanionSettingsPatch(zoneId = "UTC")
            )
            val results = listOf(
                async { store.applyContextTransferTarget("two-owners", planHash, target) },
                async {
                    store.applyContextTransferTarget(
                        "two-owners",
                        planHash,
                        target
                    )
                }).awaitAll()
            assertEquals(1, results.count { it == ContextTransferSettingsResult.Applied })
            assertEquals(1, results.count { it == ContextTransferSettingsResult.AlreadyApplied })
            assertEquals(1L, store.settingsFlow.first().companionSettings("alice").revision)
        }
    }

    @Test
    fun oldPackageMissingCompanionDomain_preservesFutureMapAndOtherRawCharacters() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice"), Assistant(id = "bob")), "bob")
            data.edit {
                it[companionKey] =
                    """{"alice":{"schemaVersion":9,"revision":3,"future":"keep"},"bob":{"revision":2,"zoneId":"Asia/Tokyo"}}"""
                val root =
                    JsonParser.parseString(it[stringPreferencesKey("assistants_json")]).asJsonArray
                root[1].asJsonObject.addProperty("futureAssistantField", "keep-bob")
                it[stringPreferencesKey("assistants_json")] = root.toString()
            }
            val before = data.data.first()[companionKey]
            val target = ContextTransferSettingsTarget(
                "alice",
                store.settingsFlow.first().assistants.first(),
                importedAssistant = Assistant(id = "alice", name = "旧包导入")
            )
            assertEquals(
                ContextTransferSettingsResult.Applied,
                store.applyContextTransferTarget("old-package", planHash, target)
            )
            assertEquals(before, data.data.first()[companionKey])
            val raw =
                JsonParser.parseString(data.data.first()[stringPreferencesKey("assistants_json")]).asJsonArray
            assertEquals("keep-bob", raw[1].asJsonObject["futureAssistantField"].asString)
            assertEquals("bob", store.settingsFlow.first().selectedAssistantId)
        }
    }

    @Test
    fun receiptBoundToPlanAndAction_rejectsChangedPayloadWithoutOverwriting() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            val target = ContextTransferSettingsTarget(
                "alice",
                store.settingsFlow.first().assistants.single(),
                companionPatch = CompanionSettingsPatch(zoneId = "UTC")
            )
            store.applyContextTransferTarget("immutable-plan", planHash, target)
            val before = data.data.first()
            assertTrue(runCatching {
                store.applyContextTransferTarget(
                    "immutable-plan",
                    planHash,
                    target.copy(companionPatch = CompanionSettingsPatch(zoneId = "Asia/Tokyo"))
                )
            }.isFailure)
            assertTrue(runCatching {
                store.applyContextTransferTarget(
                    "immutable-plan",
                    "b".repeat(64),
                    target
                )
            }.isFailure)
            assertEquals(before, data.data.first())
        }
    }

    @Test
    fun unsupportedFutureCompanion_doesNotPartiallyReplaceAssistant() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice", name = "原角色")), "alice")
            data.edit {
                it[companionKey] = """{"alice":{"schemaVersion":7,"revision":4,"future":"keep"}}"""
            }
            val target = ContextTransferSettingsTarget(
                "alice",
                store.settingsFlow.first().assistants.single(),
                Assistant(id = "alice", name = "未授权覆盖"),
                4,
                CompanionSettingsPatch(zoneId = "UTC")
            )
            val before = data.data.first()
            assertEquals(
                ContextTransferSettingsResult.UnsupportedVersion,
                store.applyContextTransferTarget("future", planHash, target)
            )
            assertEquals(before, data.data.first())
        }
    }

    private suspend fun TestScope.withStore(block: suspend (AppSettingsStore, DataStore<Preferences>) -> Unit) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val data = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(files.root, "transfer.preferences_pb") })
        val secure = object : SettingsSecureValueStore {
            override fun getString(key: String) = ""
            override fun putString(key: String, value: String) = Unit
            override fun getStringMap(key: String) = emptyMap<String, String>()
            override fun putStringMap(key: String, values: Map<String, String>) = Unit
        }
        val store = AppSettingsStore(data, secure, scope, dispatcher)
        try {
            advanceUntilIdle(); block(store, data)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
}
