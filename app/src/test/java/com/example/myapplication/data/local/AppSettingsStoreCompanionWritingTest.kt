package com.example.myapplication.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
class AppSettingsStoreCompanionWritingTest {
    @get:Rule
    val files = TemporaryFolder()
    private val key = stringPreferencesKey("companion_character_settings_json")

    @Test
    fun fullWritingRoundTrip_preservesRawSupplementAndUnrelatedSettings() = runTest {
        withStore { store, _ ->
            store.saveAssistants(
                listOf(
                    Assistant(
                        id = "alice",
                        systemPrompt = "原角色事实",
                        greeting = "原问候"
                    )
                ), "alice"
            )
            store.saveMemoryRecallTokenBudget(4321)
            advanceUntilIdle()
            val before = store.settingsFlow.first()
            val writing = writing().copy(
                extraStyle = "  自定义原文\r\n第二行  ",
                templates = listOf(
                    CompanionStyleTemplate(
                        "template-one",
                        " 夜色 ",
                        " 模板\r\n原文 "
                    )
                ),
            )
            val saved = store.updateCompanionSettings(
                "alice",
                0,
                CompanionSettingsPatch("Asia/Tokyo", writing)
            ) as CompanionSettingsWriteResult.Saved
            advanceUntilIdle()
            val after = store.settingsFlow.first()
            assertEquals(saved.settings, after.companionSettings("alice"))
            assertEquals(writing.extraStyle, after.companionSettings("alice").writing.extraStyle)
            assertEquals(
                writing.templates.single().text,
                after.companionSettings("alice").writing.templates.single().text
            )
            assertEquals(
                before.copy(companionCharacterSettings = after.companionCharacterSettings),
                after
            )
        }
    }

    @Test
    fun malformedIndividualFields_preserveOtherKnownValues() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            data.edit {
                it[key] =
                    """{"alice":{"schemaVersion":1,"revision":4,"zoneId":{},"writing":{"style":17,"pointOfView":"FIRST_YOU","extraStyle":"保留补充","detailHintsEnabled":false,"observation":{"enabled":true,"style":"future","fields":[{"id":"time","label":"时间旧名","enabled":false}],"customFields":[{"id":"bad id","label":"坏项"},{"id":"obs_valid","label":"合法项"}]}}}}"""
            }
            advanceUntilIdle()
            val recovered = store.settingsFlow.first().companionSettings("alice")
            assertEquals(4L, recovered.revision)
            assertEquals("", recovered.zoneId)
            assertNull(recovered.writing.style)
            assertEquals(CompanionPointOfView.FIRST_YOU, recovered.writing.pointOfView)
            assertEquals("保留补充", recovered.writing.extraStyle)
            assertFalse(recovered.writing.detailHintsEnabled)
            assertEquals("时间旧名", recovered.writing.observation.fields.first().label)
            assertFalse(recovered.writing.observation.fields.first().enabled)
            assertEquals(
                listOf("obs_valid"),
                recovered.writing.observation.customFields.map { it.id })
        }
    }

    @Test
    fun writingPatch_preservesUnknownNestedFieldsAndStableIds() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            data.edit {
                it[key] =
                    """{"alice":{"schemaVersion":1,"revision":2,"futureRoot":"keep","writing":{"futureWriting":true,"templates":[{"id":"template-one","name":"原模板","text":"文字","futureTemplate":42}],"observation":{"futureObservation":"keep","customFields":[{"id":"obs_coat","label":"衣着","futureField":"keep"}]}}}}"""
            }
            advanceUntilIdle()
            val current = store.settingsFlow.first().companionSettings("alice")
            val next = current.writing.copy(
                observation = current.writing.observation.copy(
                    customFields = listOf(CompanionObservationField("obs_coat", "新衣着"))
                )
            )
            assertTrue(
                store.updateCompanionSettings(
                    "alice",
                    2,
                    CompanionSettingsPatch(writing = next)
                ) is CompanionSettingsWriteResult.Saved
            )
            val root =
                JsonParser.parseString(data.data.first()[key]).asJsonObject["alice"].asJsonObject
            assertEquals("keep", root["futureRoot"].asString)
            val encoded = root["writing"].asJsonObject
            assertTrue(encoded["futureWriting"].asBoolean)
            assertEquals(
                42,
                encoded["templates"].asJsonArray[0].asJsonObject["futureTemplate"].asInt
            )
            assertEquals("keep", encoded["observation"].asJsonObject["futureObservation"].asString)
            val field =
                encoded["observation"].asJsonObject["customFields"].asJsonArray[0].asJsonObject
            assertEquals("obs_coat", field["id"].asString)
            assertEquals("新衣着", field["label"].asString)
            assertEquals("keep", field["futureField"].asString)
        }
    }

    @Test
    fun invalidNamesAndTooManyFields_doNotPartiallyWrite() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            val before = data.data.first()[key]
            val invalid = listOf(
                writing().copy(
                    observation = CompanionObservationSettings(
                        customFields = listOf(
                            CompanionObservationField("obs_dup", "时间")
                        )
                    )
                ),
                writing().copy(observation = CompanionObservationSettings(customFields = (1..7).map {
                    CompanionObservationField(
                        "obs_$it",
                        "字段$it"
                    )
                })),
                writing().copy(
                    templates = listOf(
                        CompanionStyleTemplate("a", "夜色", "一"),
                        CompanionStyleTemplate("b", " 夜色 ", "二")
                    )
                ),
            )
            for (value in invalid) {
                val result = runCatching {
                    store.updateCompanionSettings(
                        "alice",
                        0,
                        CompanionSettingsPatch("UTC", value)
                    )
                }
                assertTrue(result.exceptionOrNull() is IllegalArgumentException)
                assertEquals(before, data.data.first()[key])
            }
        }
    }

    @Test
    fun concurrentWritingSavesAndLaterZonePatch_respectRevision() = runTest {
        withStore { store, _ ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            val results =
                listOf(CompanionWritingStyle.CINEMATIC, CompanionWritingStyle.PLAIN).map { style ->
                    async {
                        store.updateCompanionSettings(
                            "alice",
                            0,
                            CompanionSettingsPatch(writing = writing().copy(style = style))
                        )
                    }
                }.awaitAll()
            assertEquals(1, results.count { it is CompanionSettingsWriteResult.Saved })
            assertEquals(1, results.count { it == CompanionSettingsWriteResult.Conflict })
            val winner =
                (results.single { it is CompanionSettingsWriteResult.Saved } as CompanionSettingsWriteResult.Saved).settings
            val zone = store.updateCompanionSettings(
                "alice",
                winner.revision,
                CompanionSettingsPatch("UTC")
            ) as CompanionSettingsWriteResult.Saved
            assertEquals(winner.writing, zone.settings.writing)
            assertEquals(2L, zone.settings.revision)
        }
    }

    @Test
    fun oldAssistantSaveAndRemoval_preserveThenRemoveOnlyOwnedWriting() = runTest {
        withStore { store, _ ->
            val assistants = listOf(Assistant(id = "alice"), Assistant(id = "bob"))
            store.saveAssistants(assistants, "alice")
            val saved = store.updateCompanionSettings(
                "alice",
                0,
                CompanionSettingsPatch(writing = writing())
            ) as CompanionSettingsWriteResult.Saved
            store.saveAssistants(assistants.map { it.copy(name = "新名字") }, "alice")
            advanceUntilIdle()
            assertEquals(saved.settings, store.settingsFlow.first().companionSettings("alice"))
            store.saveAssistants(assistants.filterNot { it.id == "alice" }, "bob")
            advanceUntilIdle()
            assertFalse(store.settingsFlow.first().companionCharacterSettings.containsKey("alice"))
            assertEquals(
                CompanionSettingsWriteResult.CharacterMissing,
                store.updateCompanionSettings(
                    "alice",
                    1,
                    CompanionSettingsPatch(writing = writing())
                )
            )
        }
    }

    @Test
    fun futureSchemaIsPreservedAndMissingWritingRemainsDefault() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            val raw =
                """{"alice":{"schemaVersion":9,"revision":8,"writing":{"futureStyle":"future"}}}"""
            data.edit { it[key] = raw }
            assertEquals(
                CompanionSettingsWriteResult.UnsupportedVersion,
                store.updateCompanionSettings(
                    "alice",
                    8,
                    CompanionSettingsPatch(writing = writing())
                )
            )
            assertEquals(raw, data.data.first()[key])
            data.edit { it[key] = """{"alice":{"revision":3,"zoneId":"UTC"}}""" }
            advanceUntilIdle()
            val old = store.settingsFlow.first().companionSettings("alice")
            assertEquals(CompanionWritingSettings(), old.writing)
            assertEquals("UTC", old.zoneId)
        }
    }

    @Test
    fun fileReopen_preservesPresentationChoicesAndCustomFieldIdentity() = runTest {
        var saved: CompanionCharacterSettings? = null
        withStore { store, _ ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            saved = (store.updateCompanionSettings(
                "alice",
                0,
                CompanionSettingsPatch(writing = writing())
            ) as CompanionSettingsWriteResult.Saved).settings
        }
        withStore { store, _ ->
            advanceUntilIdle()
            assertEquals(saved, store.settingsFlow.first().companionSettings("alice"))
        }
    }

    @Test
    fun oversizedSnapshotAndProtocolVersions_areNotTruncatedToSupportedVersion() {
        val raw =
            """{"snapshotVersion":4294967297,"sourceSettingsRevision":1,"observation":{"enabled":true}}"""
        assertNotEquals(
            1,
            CompanionWritingCodec.decodeSnapshot(JsonParser.parseString(raw))!!.snapshotVersion
        )
        val descriptor =
            CompanionDescriptorCodec.decode("""{"protocolVersion":4294967297,"presentation":$raw}""")
        assertNotEquals(1, descriptor.protocolVersion)
    }

    @Test
    fun copyAssistant_clonesWritingAtomicallyAndKeepsConcurrentRoles() = runTest {
        withStore { store, _ ->
            val old = Assistant(id = "alice", name = "旧名")
            store.saveAssistants(listOf(old, Assistant(id = "bob")), "bob")
            store.updateCompanionSettings(
                "alice",
                0,
                CompanionSettingsPatch("Asia/Tokyo", writing())
            )
            store.saveAssistants(
                listOf(
                    old.copy(name = "最新名"),
                    Assistant(id = "bob"),
                    Assistant(id = "newer")
                ), "newer"
            )
            store.saveAssistantsWithCompanionCopy(
                listOf(old, old.copy(id = "copy")),
                "alice",
                "alice",
                "copy"
            )
            advanceUntilIdle()
            val after = store.settingsFlow.first()
            assertEquals(
                setOf("alice", "bob", "newer", "copy"),
                after.assistants.map { it.id }.toSet()
            )
            assertEquals("最新名 (副本)", after.assistants.single { it.id == "copy" }.name)
            assertEquals("newer", after.selectedAssistantId)
            assertEquals(
                after.companionSettings("alice").writing,
                after.companionSettings("copy").writing
            )
            assertEquals("Asia/Tokyo", after.companionSettings("copy").zoneId)
            assertEquals(0L, after.companionSettings("copy").revision)
            store.updateCompanionSettings(
                "copy",
                0,
                CompanionSettingsPatch(writing = writing().copy(style = CompanionWritingStyle.PLAIN))
            )
            advanceUntilIdle()
            assertEquals(
                CompanionWritingStyle.LITERARY,
                store.settingsFlow.first().companionSettings("alice").writing.style
            )
        }
    }

    @Test
    fun unsupportedCopy_doesNotCreatePartialRoleOrOverwriteSettings() = runTest {
        withStore { store, data ->
            val source = Assistant(id = "alice")
            store.saveAssistants(listOf(source), "alice")
            val raw = """{"alice":{"schemaVersion":9,"revision":5,"writing":{"future":"keep"}}}"""
            data.edit { it[key] = raw }
            val result = runCatching {
                store.saveAssistantsWithCompanionCopy(
                    listOf(
                        source,
                        source.copy(id = "copy")
                    ), "alice", "alice", "copy"
                )
            }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            advanceUntilIdle()
            assertEquals(listOf("alice"), store.settingsFlow.first().assistants.map { it.id })
            assertEquals(raw, data.data.first()[key])
        }
    }

    private fun writing() = CompanionWritingSettings(
        CompanionWritingStyle.LITERARY, CompanionPointOfView.THIRD_YOU, "补充原文",
        listOf(CompanionStyleTemplate("template-one", "夜色", "慢一点")), false,
        CompanionObservationSettings(
            enabled = true,
            customFields = listOf(CompanionObservationField("obs_coat", "衣着"))
        ),
    )

    private suspend fun TestScope.withStore(block: suspend (AppSettingsStore, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> Unit) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val data = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(files.root, "writing.preferences_pb") })
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
