package com.example.myapplication.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.CompanionCharacterSettings
import com.example.myapplication.model.CompanionDisplayMode
import com.example.myapplication.model.CompanionImageKind
import com.example.myapplication.model.CompanionImageResource
import com.example.myapplication.model.CompanionOutfit
import com.example.myapplication.model.CompanionPortraitPosition
import com.example.myapplication.model.CompanionSceneVisualOverride
import com.example.myapplication.model.CompanionSettingsPatch
import com.example.myapplication.model.CompanionSettingsWriteResult
import com.example.myapplication.model.CompanionStageLayout
import com.example.myapplication.model.CompanionVisualSettings
import com.example.myapplication.model.CompanionWritingSettings
import com.example.myapplication.model.CompanionWritingStyle
import com.example.myapplication.model.companionSettings
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsStoreCompanionVisualTest {
    @get:Rule
    val files = TemporaryFolder()
    private val key = stringPreferencesKey("companion_character_settings_json")

    @Test
    fun oldSettingsWithoutVisuals_keepListAndWritingUntilUserChooses() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            data.edit {
                it[key] =
                    """{"alice":{"schemaVersion":1,"revision":3,"zoneId":"Asia/Tokyo","writing":{"style":"LITERARY","extraStyle":"原补充"}}}"""
            }
            advanceUntilIdle()
            val before = store.settingsFlow.first().companionSettings("alice")
            assertNull(before.visuals.preferredMode)
            assertEquals(CompanionDisplayMode.LIST, before.visuals.displayMode)
            assertTrue(before.visuals.resources.isEmpty())
            assertEquals("原补充", before.writing.extraStyle)

            val saved = saved(
                store.updateCompanionSettings(
                    "alice",
                    3,
                    CompanionSettingsPatch(visuals = before.visuals.copy(preferredMode = CompanionDisplayMode.STAGE))
                )
            )
            advanceUntilIdle()
            val after = store.settingsFlow.first().companionSettings("alice")
            assertEquals(saved, after)
            assertEquals(CompanionDisplayMode.STAGE, after.visuals.displayMode)
            assertEquals(before.writing, after.writing)
            assertEquals(before.zoneId, after.zoneId)
        }
    }

    @Test
    fun fullVisualRoundTrip_preservesResourcesSceneChoiceAndUnrelatedSettings() = runTest {
        withStore { store, _ ->
            store.saveAssistants(
                listOf(
                    Assistant(
                        id = "alice",
                        name = "角色",
                        systemPrompt = "原人物事实",
                        greeting = "原问候"
                    )
                ), "alice"
            )
            store.saveMemoryRecallTokenBudget(4321)
            val writing = CompanionWritingSettings(
                style = CompanionWritingStyle.LITERARY,
                extraStyle = "  原补充\r\n第二行  ",
            )
            saved(
                store.updateCompanionSettings(
                    "alice",
                    0,
                    CompanionSettingsPatch("Asia/Tokyo", writing)
                )
            )
            advanceUntilIdle()
            val before = store.settingsFlow.first()
            val expected = visuals()

            val saved = saved(
                store.updateCompanionSettings(
                    "alice", 1, CompanionSettingsPatch(visuals = expected)
                )
            )
            advanceUntilIdle()
            val after = store.settingsFlow.first()
            assertEquals(expected, after.companionSettings("alice").visuals)
            assertEquals(saved, after.companionSettings("alice"))
            assertEquals(2L, saved.revision)
            assertEquals(writing, saved.writing)
            assertEquals("Asia/Tokyo", saved.zoneId)
            assertEquals(
                before.copy(companionCharacterSettings = after.companionCharacterSettings),
                after
            )
        }
    }

    @Test
    fun concurrentVisualSaves_publishOneWholeSnapshotAndOneConflict() = runTest {
        withStore { store, _ ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            val candidates = listOf(
                visuals().copy(preferredMode = CompanionDisplayMode.STAGE),
                visuals().copy(
                    preferredMode = CompanionDisplayMode.LIST,
                    layout = CompanionStageLayout(CompanionPortraitPosition.RIGHT, 0.55f, 0.3f)
                ),
            )
            val results = candidates.map { value ->
                async {
                    store.updateCompanionSettings(
                        "alice",
                        0,
                        CompanionSettingsPatch(visuals = value)
                    )
                }
            }.awaitAll()
            assertEquals(1, results.count { it is CompanionSettingsWriteResult.Saved })
            assertEquals(1, results.count { it == CompanionSettingsWriteResult.Conflict })
            val winner = saved(results.single { it is CompanionSettingsWriteResult.Saved })
            advanceUntilIdle()
            assertTrue(winner.visuals in candidates)
            assertEquals(winner, store.settingsFlow.first().companionSettings("alice"))
            assertEquals(1L, winner.revision)
        }
    }

    @Test
    fun writingAndZoneUpdates_keepVisualSnapshotAndRejectStaleVisualDraft() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            val initial = saved(
                store.updateCompanionSettings(
                    "alice",
                    0,
                    CompanionSettingsPatch(visuals = visuals())
                )
            )
            val writing =
                CompanionWritingSettings(style = CompanionWritingStyle.PLAIN, extraStyle = "新文风")
            val updated = saved(
                store.updateCompanionSettings(
                    "alice", initial.revision, CompanionSettingsPatch("UTC", writing)
                )
            )
            val beforeRejected = data.data.first()[key]

            assertEquals(
                CompanionSettingsWriteResult.Conflict, store.updateCompanionSettings(
                    "alice", initial.revision,
                    CompanionSettingsPatch(visuals = initial.visuals.copy(preferredMode = CompanionDisplayMode.LIST))
                )
            )
            assertEquals(beforeRejected, data.data.first()[key])
            advanceUntilIdle()
            val after = store.settingsFlow.first().companionSettings("alice")
            assertEquals(updated, after)
            assertEquals(initial.visuals, after.visuals)
            assertEquals(writing, after.writing)
            assertEquals("UTC", after.zoneId)
        }
    }

    @Test
    fun visualPatch_keepsUnknownFieldsForStableResourcesOutfitsAndScenes() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            data.edit {
                it[key] =
                    """{"alice":{"schemaVersion":1,"revision":2,"futureRoot":"保留","visuals":{"schemaVersion":1,"futureVisual":{"opaque":7},"resources":[{"id":"portrait","name":"旧立绘","path":"/owned/portrait.png","kind":"PORTRAIT","futureResource":42}],"outfits":[{"id":"set","name":"旧套装","defaultPortraitId":"portrait","expressions":{"happy":"portrait"},"futureOutfit":"保留"}],"selectedOutfitId":"set","layout":{"portraitPosition":"CENTER","portraitWidth":0.72,"portraitHeight":0.45,"futureLayout":true},"sceneOverrides":{"scene-a":{"selectedOutfitId":"set","layout":{"portraitPosition":"LEFT","portraitWidth":0.6,"portraitHeight":0.3,"futureSceneLayout":"保留"},"futureScene":"保留"}}}}}"""
            }
            advanceUntilIdle()
            val current = store.settingsFlow.first().companionSettings("alice")
            val updated = current.visuals.copy(
                resources = current.visuals.resources.map { it.copy(name = "新立绘") },
                outfits = current.visuals.outfits.map { it.copy(name = "新套装") },
                sceneOverrides = current.visuals.sceneOverrides.mapValues { (_, scene) ->
                    scene.copy(layout = scene.layout.copy(portraitPosition = CompanionPortraitPosition.RIGHT))
                },
            )
            saved(
                store.updateCompanionSettings(
                    "alice",
                    2,
                    CompanionSettingsPatch(visuals = updated)
                )
            )
            val entry = rawEntry(data)
            val encoded = entry["visuals"].asJsonObject
            assertEquals("保留", entry["futureRoot"].asString)
            assertEquals(7, encoded["futureVisual"].asJsonObject["opaque"].asInt)
            assertEquals(
                42,
                encoded["resources"].asJsonArray[0].asJsonObject["futureResource"].asInt
            )
            assertEquals(
                "新立绘",
                encoded["resources"].asJsonArray[0].asJsonObject["name"].asString
            )
            assertEquals(
                "保留",
                encoded["outfits"].asJsonArray[0].asJsonObject["futureOutfit"].asString
            )
            assertEquals("新套装", encoded["outfits"].asJsonArray[0].asJsonObject["name"].asString)
            assertTrue(encoded["layout"].asJsonObject["futureLayout"].asBoolean)
            val scene = encoded["sceneOverrides"].asJsonObject["scene-a"].asJsonObject
            assertEquals("保留", scene["futureScene"].asString)
            assertEquals("保留", scene["layout"].asJsonObject["futureSceneLayout"].asString)
            assertEquals("RIGHT", scene["layout"].asJsonObject["portraitPosition"].asString)
        }
    }

    @Test
    fun futureOrInvalidVisualVersion_rejectsVisualWriteAndOtherPatchesKeepRawDomain() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            for (version in listOf("2", "2147483648", "0", "\"2\"")) {
                val raw =
                    """{"alice":{"schemaVersion":1,"revision":5,"visuals":{"schemaVersion":$version,"resources":{"future":"保留"},"futurePayload":[1,2,3]}}}"""
                data.edit { it[key] = raw }
                assertEquals(
                    CompanionSettingsWriteResult.UnsupportedVersion,
                    store.updateCompanionSettings(
                        "alice",
                        5,
                        CompanionSettingsPatch(visuals = visuals())
                    )
                )
                assertEquals(raw, data.data.first()[key])
                val originalDomain =
                    JsonParser.parseString(raw).asJsonObject["alice"].asJsonObject["visuals"]
                val saved = saved(
                    store.updateCompanionSettings(
                        "alice",
                        5,
                        CompanionSettingsPatch(zoneId = "UTC")
                    )
                )
                assertEquals(6L, saved.revision)
                assertEquals(originalDomain, rawEntry(data)["visuals"])
            }
        }
    }

    @Test
    fun oldAssistantSaves_preserveVisualsAndRemovalOnlyClearsDeletedCharacter() = runTest {
        withStore { store, data ->
            val assistants = listOf(Assistant(id = "alice"), Assistant(id = "bob"))
            store.saveAssistants(assistants, "alice")
            val alice = saved(
                store.updateCompanionSettings(
                    "alice",
                    0,
                    CompanionSettingsPatch(visuals = visuals())
                )
            )
            val bob = saved(
                store.updateCompanionSettings(
                    "bob", 0,
                    CompanionSettingsPatch(visuals = visuals().copy(preferredMode = CompanionDisplayMode.LIST))
                )
            )
            store.saveAssistants(assistants.map { it.copy(name = "修改后的角色名") }, "bob")
            advanceUntilIdle()
            assertEquals(alice, store.settingsFlow.first().companionSettings("alice"))
            assertEquals(bob, store.settingsFlow.first().companionSettings("bob"))

            store.saveAssistants(assistants.filterNot { it.id == "alice" }, "bob")
            val beforeRejected = data.data.first()[key]
            assertEquals(
                CompanionSettingsWriteResult.CharacterMissing,
                store.updateCompanionSettings(
                    "alice",
                    alice.revision,
                    CompanionSettingsPatch(visuals = visuals())
                )
            )
            assertEquals(beforeRejected, data.data.first()[key])
            advanceUntilIdle()
            val after = store.settingsFlow.first()
            assertFalse(after.companionCharacterSettings.containsKey("alice"))
            assertEquals(bob, after.companionSettings("bob"))
        }
    }

    @Test
    fun copiedCharacter_keepsStableAssetReferencesAndIndependentPreferenceRevision() = runTest {
        withStore { store, _ ->
            val source = Assistant(id = "alice", name = "原角色")
            store.saveAssistants(listOf(source), "alice")
            val original = saved(
                store.updateCompanionSettings(
                    "alice",
                    0,
                    CompanionSettingsPatch(visuals = visuals())
                )
            )
            store.saveAssistantsWithCompanionCopy(
                listOf(source, source.copy(id = "copy", name = "角色副本")), "copy", "alice", "copy"
            )
            advanceUntilIdle()
            val copy = store.settingsFlow.first().companionSettings("copy")
            assertEquals(0L, copy.revision)
            assertEquals(original.visuals, copy.visuals)
            val changed = saved(
                store.updateCompanionSettings(
                    "copy", 0,
                    CompanionSettingsPatch(
                        visuals = copy.visuals.copy(
                            preferredMode = CompanionDisplayMode.LIST, sceneOverrides = emptyMap()
                        )
                    )
                )
            )
            advanceUntilIdle()
            assertEquals(changed, store.settingsFlow.first().companionSettings("copy"))
            assertEquals(original, store.settingsFlow.first().companionSettings("alice"))
            assertEquals(
                original.visuals.resources.map { it.path },
                changed.visuals.resources.map { it.path })
        }
    }

    @Test
    fun invalidResourceKindsReferencesAndLayout_doNotPartiallyPublish() = runTest {
        withStore { store, data ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            val initial = saved(
                store.updateCompanionSettings(
                    "alice",
                    0,
                    CompanionSettingsPatch(visuals = visuals())
                )
            )
            val before = data.data.first()[key]
            val valid = initial.visuals
            val invalid = listOf(
                valid.copy(resources = valid.resources + valid.resources.first()),
                valid.copy(outfits = valid.outfits.map { it.copy(defaultPortraitId = "decor") }),
                valid.copy(outfits = valid.outfits.map { it.copy(expressions = mapOf("happy" to "background")) }),
                valid.copy(selectedOutfitId = "missing"),
                valid.copy(selectedBackgroundId = "portrait"),
                valid.copy(decorationIds = listOf("portrait")),
                valid.copy(layout = valid.layout.copy(portraitWidth = Float.NaN)),
                valid.copy(
                    sceneOverrides = mapOf(
                        "scene-a" to CompanionSceneVisualOverride(
                            selectedOutfitId = "missing"
                        )
                    )
                ),
            )
            for (value in invalid) {
                val failure = runCatching {
                    store.updateCompanionSettings(
                        "alice",
                        initial.revision,
                        CompanionSettingsPatch(visuals = value)
                    )
                }.exceptionOrNull()
                assertTrue(failure is IllegalArgumentException)
                assertEquals(before, data.data.first()[key])
            }
            advanceUntilIdle()
            assertEquals(initial, store.settingsFlow.first().companionSettings("alice"))
        }
    }

    @Test
    fun explicitSceneReset_roundTripsWithoutClearingCharacterOutfitOrOtherScenes() = runTest {
        withStore { store, _ ->
            store.saveAssistants(listOf(Assistant(id = "alice")), "alice")
            val initial = saved(
                store.updateCompanionSettings(
                    "alice",
                    0,
                    CompanionSettingsPatch(visuals = visuals())
                )
            )
            val reset = initial.visuals.copy(
                sceneOverrides = initial.visuals.sceneOverrides +
                        ("scene-a" to CompanionSceneVisualOverride())
            )
            saved(
                store.updateCompanionSettings(
                    "alice",
                    initial.revision,
                    CompanionSettingsPatch(visuals = reset)
                )
            )
            advanceUntilIdle()
            val restored = store.settingsFlow.first().companionSettings("alice").visuals
            assertEquals("set", restored.selectedOutfitId)
            assertEquals("background", restored.selectedBackgroundId)
            assertNull(restored.forScene("scene-a").selectedOutfitId)
            assertNull(restored.forScene("scene-a").selectedBackgroundId)
            assertEquals(reset.sceneOverrides["scene-b"], restored.sceneOverrides["scene-b"])
            assertEquals("set", restored.forScene("unconfigured-scene").selectedOutfitId)
        }
    }

    private fun visuals() = CompanionVisualSettings(
        preferredMode = CompanionDisplayMode.STAGE,
        resources = listOf(
            CompanionImageResource(
                "portrait",
                "默认立绘",
                "/owned/portrait.png",
                CompanionImageKind.PORTRAIT,
                120,
                240
            ),
            CompanionImageResource(
                "happy",
                "开心立绘",
                "/owned/happy.png",
                CompanionImageKind.PORTRAIT,
                120,
                240
            ),
            CompanionImageResource(
                "background",
                "场景背景",
                "/owned/background.png",
                CompanionImageKind.BACKGROUND,
                800,
                600
            ),
            CompanionImageResource(
                "decor",
                "装饰",
                "/owned/decor.png",
                CompanionImageKind.DECOR,
                80,
                80
            ),
        ),
        outfits = listOf(CompanionOutfit("set", "角色套装", "portrait", mapOf("happy" to "happy"))),
        selectedOutfitId = "set",
        selectedBackgroundId = "background",
        decorationIds = listOf("decor"),
        layout = CompanionStageLayout(CompanionPortraitPosition.LEFT, 0.65f, 0.4f),
        sceneOverrides = mapOf(
            "scene-a" to CompanionSceneVisualOverride(
                "set", "background",
                CompanionStageLayout(CompanionPortraitPosition.RIGHT, 0.55f, 0.3f)
            ),
            "scene-b" to CompanionSceneVisualOverride(),
        ),
    )

    private fun saved(result: CompanionSettingsWriteResult): CompanionCharacterSettings {
        assertTrue(result is CompanionSettingsWriteResult.Saved)
        return (result as CompanionSettingsWriteResult.Saved).settings
    }

    private suspend fun rawEntry(data: DataStore<Preferences>): JsonObject =
        JsonParser.parseString(data.data.first()[key]).asJsonObject["alice"].asJsonObject

    private suspend fun TestScope.withStore(block: suspend (AppSettingsStore, DataStore<Preferences>) -> Unit) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val data = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(files.root, "visuals.preferences_pb") },
        )
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
