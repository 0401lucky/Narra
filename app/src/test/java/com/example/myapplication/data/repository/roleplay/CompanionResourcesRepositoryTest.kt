package com.example.myapplication.data.repository.roleplay

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.myapplication.data.local.AppSettingsStore
import com.example.myapplication.data.local.SettingsSecureValueStore
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.LocalImageStore
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionReplyProjector
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.testutil.CompanionTestFixture
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileInputStream
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompanionResourcesRepositoryTest : CompanionTestFixture() {
    @get:Rule
    val files = TemporaryFolder()

    @Test
    fun privateImport_preservesRealPixelsAndCasReference() = runTest {
        withSettings { settings, _ ->
            val repository = resources(settings)
            val original = png("source")
            val image = repository.importImage(
                uri(original).toString(),
                CompanionImageKind.PORTRAIT,
                "角色立绘"
            )
            assertTrue(
                image.path.startsWith(
                    File(
                        application.filesDir,
                        "user_images"
                    ).absolutePath
                )
            )
            assertNotEquals(original.absolutePath, image.path)
            assertEquals(12, image.width)
            assertEquals(18, image.height)
            assertArrayEquals(original.readBytes(), File(image.path).readBytes())
            val decoded = requireNotNull(BitmapFactory.decodeFile(image.path))
            assertEquals(PIXEL, decoded.getPixel(5, 6)); decoded.recycle()
            val visuals = CompanionVisualSettings(
                resources = listOf(image),
                outfits = listOf(CompanionOutfit("outfit", "套装", image.id)),
                selectedOutfitId = "outfit"
            )
            val result = repository.save("alice", 0, visuals, setOf(image.path))
            assertTrue(result is CompanionSettingsWriteResult.Saved)
            assertEquals(
                image.path,
                settings.settingsFlow.first()
                    .companionSettings("alice").visuals.resources.single().path
            )
            repository.discardImports(setOf(image.path))
            assertTrue(File(image.path).isFile)
        }
    }

    @Test
    fun badImage_isRejectedWithoutLeavingPrivateTarget() = runTest {
        withSettings { settings, _ ->
            val repository = resources(settings)
            val bad = File(files.root, "bad.bin").apply { writeText("not an image") }
            val before = privateFiles()
            val result = runCatching {
                repository.importImage(
                    uri(bad).toString(),
                    CompanionImageKind.PORTRAIT,
                    "坏图"
                )
            }
            assertTrue(result.isFailure)
            assertEquals(before, privateFiles())
            assertTrue(bad.isFile)
        }
    }

    @Test
    fun staleCas_keepsDraftFileAndDiscardCleansOnlyOwnedTarget() = runTest {
        withSettings { settings, _ ->
            val repository = resources(settings)
            val image = repository.importImage(
                uri(png("draft")).toString(),
                CompanionImageKind.PORTRAIT,
                "草稿图"
            )
            val unrelated = privatePng("unrelated")
            settings.updateCompanionSettings(
                "alice",
                0,
                CompanionSettingsPatch(zoneId = "Asia/Tokyo")
            )
            assertEquals(
                CompanionSettingsWriteResult.Conflict,
                repository.save(
                    "alice",
                    0,
                    CompanionVisualSettings(resources = listOf(image)),
                    setOf(image.path)
                )
            )
            assertTrue(File(image.path).isFile)
            assertTrue(
                settings.settingsFlow.first().companionSettings("alice").visuals.resources.isEmpty()
            )
            repository.discardImports(setOf(image.path, unrelated.absolutePath))
            assertFalse(File(image.path).exists())
            assertTrue(unrelated.isFile)
            assertEquals(
                "Asia/Tokyo",
                settings.settingsFlow.first().companionSettings("alice").zoneId
            )
        }
    }

    @Test
    fun otherCharacterReference_protectsSharedImportedFile() = runTest {
        withSettings { settings, _ ->
            val repository = resources(settings)
            val image = repository.importImage(
                uri(png("shared")).toString(),
                CompanionImageKind.PORTRAIT,
                "共享图"
            )
            assertTrue(
                settings.updateCompanionSettings(
                    "bob",
                    0,
                    CompanionSettingsPatch(
                        visuals = CompanionVisualSettings(
                            resources = listOf(
                                image
                            )
                        )
                    )
                ) is CompanionSettingsWriteResult.Saved
            )
            repository.discardImports(setOf(image.path))
            assertTrue(File(image.path).isFile)
            assertEquals(
                image.path,
                settings.settingsFlow.first()
                    .companionSettings("bob").visuals.resources.single().path
            )
        }
    }

    @Test
    fun sceneAndMessageReference_protectFileAliasesDuringDiscard() = runTest {
        withSettings { settings, _ ->
            val repository = resources(settings)
            val image = repository.importImage(
                uri(png("linked")).toString(),
                CompanionImageKind.BACKGROUND,
                "共享背景"
            )
            val context = runtime()
            val scene = requireNotNull(roleplay.getScenario(context.scenarioId))
            roleplay.upsertScenario(scene.copy(backgroundUri = image.path))
            store.upsertMessages(
                listOf(
                    ChatMessage(
                        "picture",
                        context.conversationId,
                        MessageRole.USER,
                        "一张图片",
                        createdAt = 10_000,
                        parts = listOf(imageMessagePart(Uri.fromFile(File(image.path)).toString()))
                    )
                )
            )
            repository.discardImports(setOf(image.path))
            assertTrue(File(image.path).isFile)
            val references = CompanionImageReferences(database, settings)
            assertFalse(references.deleteIfUnreferenced(image.path, LocalImageStore(application)))
            assertTrue(references.read().contains(File(image.path).canonicalPath))
        }
    }

    @Test
    fun rawFutureReferences_surviveSceneDeletionWithoutDowngrade() = runTest {
        withSettings { settings, data ->
            val references = CompanionImageReferences(database, settings)
            val safeRoleplay = RoomRoleplayRepository(
                database.roleplayDao(), ConversationRepository(store),
                imageFileCleaner = { path ->
                    references.deleteIfUnreferenced(
                        path,
                        LocalImageStore(application)
                    )
                }, database = database,
                memoryArchiveRepository = archive
            )
            for (kind in 0..2) {
                val image = privatePng("future-$kind")
                val root = JsonObject().apply {
                    add("alice", JsonObject().apply {
                        addProperty("schemaVersion", if (kind == 0) 9 else 1)
                        addProperty("revision", 4)
                        add("visuals", JsonObject().apply {
                            addProperty("schemaVersion", if (kind == 1) 9 else 1)
                            add("resources", JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("id", "future-image"); addProperty(
                                    "name",
                                    "未来素材"
                                )
                                    addProperty("kind", "FUTURE_KIND"); addProperty(
                                    "path",
                                    Uri.fromFile(image).toString()
                                )
                                })
                            })
                            add(
                                "futureExtension",
                                JsonObject().apply { addProperty("assetPath", image.absolutePath) })
                        })
                    })
                }
                val raw = AppJson.gson.toJson(root)
                val key = stringPreferencesKey("companion_character_settings_json")
                data.edit { it[key] = raw }
                advanceUntilIdle()
                assertTrue(
                    settings.settingsFlow.first()
                        .companionSettings("alice").visuals.resources.isEmpty()
                )
                assertTrue(requireNotNull(settings.companionImageReferences()).contains(image.absolutePath))
                val context = runtime()
                val scene = requireNotNull(roleplay.getScenario(context.scenarioId))
                roleplay.upsertScenario(scene.copy(backgroundUri = image.absolutePath))
                safeRoleplay.deleteScenario(scene.id)
                assertTrue(image.isFile)
                assertEquals(raw, data.data.first()[key])
                assertEquals(
                    PIXEL,
                    requireNotNull(BitmapFactory.decodeFile(image.absolutePath)).getPixel(2, 3)
                )
            }
        }
    }

    @Test
    fun missingExpressionAndDecor_useRealDefaultThenAvatar() = runTest {
        withSettings { settings, _ ->
            val repository = resources(settings)
            val happy = repository.importImage(
                uri(png("happy")).toString(),
                CompanionImageKind.PORTRAIT,
                "表情图"
            )
            val default = repository.importImage(
                uri(png("default")).toString(),
                CompanionImageKind.PORTRAIT,
                "默认图"
            )
            val decor = repository.importImage(
                uri(png("decor")).toString(),
                CompanionImageKind.DECOR,
                "装饰"
            )
            File(happy.path).writeText("invalid file")
            val visuals = CompanionVisualSettings(
                resources = listOf(happy, default, decor),
                outfits = listOf(
                    CompanionOutfit(
                        "set",
                        "套装",
                        default.id,
                        mapOf("happy" to happy.id)
                    )
                ),
                selectedOutfitId = "set",
                decorationIds = listOf(decor.id)
            )
            val avatar = privatePng("avatar")
            val scene = RoleplayScenario(
                id = "scene",
                assistantId = "alice",
                characterPortraitUri = "/missing-file.png"
            )
            var resolved = repository.resolve(
                visuals,
                scene,
                Assistant(id = "alice", avatarUri = avatar.absolutePath)
            )
            assertEquals(default.id, resolved.portraits("happy").first().resourceId)
            assertFalse(resolved.portraits("happy").any { it.resourceId == decor.id })
            assertTrue("默认图片必须真实删除后再验证回退，path.len=${default.path.length}", File(default.path).delete())
            resolved = repository.resolve(
                visuals,
                scene,
                Assistant(id = "alice", avatarUri = avatar.absolutePath)
            )
            assertEquals(avatar.absolutePath, resolved.portraits("happy").first().uri)
        }
    }

    @Test
    fun visualChanges_doNotMutateCommittedTextOrSourceVersion() = runTest {
        withSettings { settings, _ ->
            val context = runtime()
            val turn = prepared(context)
            received(turn, "<dialogue sprite=\"happy\">已提交原文。</dialogue>")
            assertTrue(repository.commitAttempt(turn.attempt.id) is CompanionCommitResult.Committed)
            val before = store.listMessages(context.conversationId)
            val reply =
                requireNotNull(CompanionReplyProjector.project(before.last { it.role == MessageRole.ASSISTANT }))
            val images = resources(settings)
            val image = images.importImage(
                uri(png("presentation")).toString(),
                CompanionImageKind.PORTRAIT,
                "本地图"
            )
            assertTrue(
                images.save(
                    "alice",
                    0,
                    CompanionVisualSettings(
                        resources = listOf(image),
                        outfits = listOf(CompanionOutfit("set", "套装", image.id)),
                        selectedOutfitId = "set"
                    ),
                    setOf(image.path)
                ) is CompanionSettingsWriteResult.Saved
            )
            val after = store.listMessages(context.conversationId)
            assertEquals(
                before.map { Triple(it.id, it.content, it.memoryRevision) },
                after.map { Triple(it.id, it.content, it.memoryRevision) })
            assertEquals(
                reply.source,
                CompanionReplyProjector.project(after.last { it.role == MessageRole.ASSISTANT })?.source
            )
            assertEquals(turn.turn.id, after.last().companionTurnId)
        }
    }

    private fun resources(settings: AppSettingsStore) = AndroidCompanionResourcesRepository(
        application,
        LocalImageStore(application),
        settings,
        CompanionImageReferences(database, settings)::read
    )

    private fun uri(file: File): Uri =
        Uri.parse("content://companion-files/${UUID.randomUUID()}").also { source ->
            shadowOf(application.contentResolver).registerInputStreamSupplier(source) {
                FileInputStream(
                    file
                )
            }
        }

    private fun png(name: String): File =
        File(files.root, "$name-${UUID.randomUUID().toString().take(6)}.png").also(::writePng)

    private fun privatePng(name: String): File = File(
        application.filesDir,
        "user_images/tests/$name-${UUID.randomUUID().toString().take(6)}.png"
    ).apply { parentFile!!.mkdirs() }.also(::writePng)

    private fun writePng(file: File) {
        val bitmap = Bitmap.createBitmap(12, 18, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(PIXEL)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    private fun privateFiles(): Set<String> =
        File(application.filesDir, "user_images").walkTopDown().filter { it.isFile }
            .map { it.canonicalPath }.toSet()

    private suspend fun TestScope.withSettings(block: suspend (AppSettingsStore, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> Unit) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val data = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(files.root, "resources.preferences_pb") })
        val secure = object : SettingsSecureValueStore {
            override fun getString(key: String) = ""
            override fun putString(key: String, value: String) = Unit
            override fun getStringMap(key: String) = emptyMap<String, String>()
            override fun putStringMap(key: String, values: Map<String, String>) = Unit
        }
        val settings = AppSettingsStore(data, secure, scope, dispatcher)
        try {
            advanceUntilIdle()
            settings.saveAssistants(listOf(Assistant(id = "alice"), Assistant(id = "bob")), "alice")
            advanceUntilIdle()
            block(settings, data)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    private companion object {
        val PIXEL: Int = Color.rgb(17, 109, 201)
    }
}
