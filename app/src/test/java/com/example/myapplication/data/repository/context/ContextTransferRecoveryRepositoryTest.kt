package com.example.myapplication.data.repository.context

import android.app.Application
import androidx.room.withTransaction
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.testutil.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContextTransferRecoveryRepositoryTest {
    @Test
    fun largeBoundedAppearancePlan_freezesPixelsOnceAndRestoresAfterReopen() = runBlocking<Unit> {
        CompanionTransferFixture().use { f ->
            f.initialize()
            val images = (1..3).map { seed ->
                syntheticTransferImage(syntheticTransferPng(800, 800, randomSeed = seed), 800, 800)
            }
            val pack = CompanionAppearancePackage(
                visuals = CompanionAppearanceData(resources = images.mapIndexed { index, image ->
                    CompanionAppearanceResource(
                        "background_$index",
                        "合成背景 $index",
                        image.sha256,
                        CompanionImageKind.BACKGROUND
                    )
                }),
                images = images,
            )
            val raw = CompanionAppearanceTransferCodec().encode(pack)
            assertTrue(raw.toByteArray(Charsets.UTF_8).size in (MemoryTransferJson.MAX_BYTES / 2 + 1)..MemoryTransferJson.MAX_BYTES)
            val repository = f.recovery()
            val preview = repository.prepareAppearanceImport(raw, "bob")
            val file =
                File(
                    f.context.filesDir,
                    "companion_transfers/plans/${preview.operationId}/plan.json"
                )
            assertTrue(file.isFile)
            assertTrue(file.length() <= MemoryTransferJson.MAX_BYTES)
            assertEquals(3, Regex("\"dataBase64\"").findAll(file.readText()).count())
            assertFalse(File(f.context.filesDir, "companion_transfers/assets").exists())
            f.reopenDatabase()
            assertEquals(
                ContextTransferStage.COMPLETE,
                f.recovery().resumeOperation(preview.operationId).stage
            )
            val restored =
                f.settings.settingsFlow.first().companionSettings("bob").visuals.resources
            assertEquals(3, restored.size)
            assertTrue(restored.all { File(it.path).isFile })
        }
    }

    @Test
    fun noMemoryRoomCommitRetry_preservesLaterWorldAndPresetEditsAfterReopen() = runBlocking<Unit> {
        CompanionTransferFixture().use { f ->
            f.initialize()
            var rejected = true
            val settings = object : SettingsStore by f.settings {
                override suspend fun applyContextTransferTarget(
                    operationId: String,
                    planHash: String,
                    target: ContextTransferSettingsTarget
                ): ContextTransferSettingsResult {
                    if (rejected) throw IOException("synthetic settings failure")
                    return f.settings.applyContextTransferTarget(operationId, planHash, target)
                }
            }
            var fileStages = 0
            val files = object : CompanionTransferFileStore by f.files {
                override suspend fun publishImages(images: List<CompanionTransferImage>): List<CompanionPublishedTransferImage> {
                    fileStages++; return f.files.publishImages(images)
                }
            }
            val bundle = ContextDataBundle(
                version = 3,
                exportedAt = 1L,
                assistants = listOf(Assistant(id = "new-role", name = "导入角色")),
                worldBookEntries = listOf(
                    WorldBookEntry(
                        id = "world",
                        title = "合成",
                        content = "导入正文"
                    )
                ),
                presets = listOf(Preset(id = "preset", name = "合成预设"))
            )
            val repository = f.recovery(settings, fileStore = files)
            val op = repository.preparePrivateImport(bundle, null, f.settings.settingsFlow.first())
            assertTrue(runCatching { repository.resumeOperation(op.operationId) }.isFailure)
            assertEquals(
                "FILES_COMMITTED",
                f.database.contextTransferOperationDao().get(op.operationId)!!.stage
            )
            f.worlds.upsertEntry(f.worlds.getEntry("world")!!.copy(content = "用户后续设定"))
            f.presets.upsertPreset(f.presets.getPreset("preset")!!.copy(name = "用户后续预设"))
            f.reopenDatabase(); rejected = false
            val result = f.recovery(settings, fileStore = files).resumeOperation(op.operationId)
            assertEquals(ContextTransferStage.COMPLETE, result.stage)
            assertEquals("用户后续设定", f.worlds.getEntry("world")!!.content)
            assertEquals("用户后续预设", f.presets.getPreset("preset")!!.name)
            assertEquals(1, fileStages)
            assertEquals(
                1,
                f.settings.settingsFlow.first().assistants.count { it.id == "new-role" })
            f.recovery(settings, fileStore = files).resumeOperation(op.operationId)
            assertEquals(1, fileStages)
        }
    }

    @Test
    fun settingReceiptBeforeRoomAck_survivesBothReopensWithoutOverwritingUser() =
        runBlocking<Unit> {
            CompanionTransferFixture().use { f ->
                f.initialize()
                val bundle = ContextDataBundle(
                    version = 3,
                    exportedAt = 1L,
                    companions = listOf(
                        CompanionContextTransferEntry("alice", zoneId = "UTC"),
                        CompanionContextTransferEntry("bob", zoneId = "Europe/Paris")
                    )
                )
                val oldPlanFiles = object : CompanionTransferFileStore by f.files {
                    override suspend fun freezePlan(
                        operationId: String,
                        json: String
                    ): ContextTransferPlanFile {
                        val payload = JsonParser.parseString(json).asJsonObject
                        val sources = payload.getAsJsonObject("bundle").getAsJsonArray("companions")
                            .associateBy { it.asJsonObject["assistantId"].asString }
                        payload.getAsJsonArray("targets").forEach { target ->
                            target.asJsonObject.add(
                                "companion",
                                sources.getValue(target.asJsonObject["assistantId"].asString)
                                    .deepCopy()
                            )
                        }
                        val bytes = AppJson.gson.toJson(payload).toByteArray(Charsets.UTF_8)
                        val legacy =
                            File(f.context.filesDir, "companion_transfers/plans/$operationId.json")
                        check(!legacy.exists())
                        check(legacy.parentFile!!.mkdirs() || legacy.parentFile!!.isDirectory)
                        legacy.outputStream().use { it.write(bytes); it.flush(); it.fd.sync() }
                        return ContextTransferPlanFile(
                            CompanionTransferImageValidation.sha256(bytes),
                            true
                        )
                    }
                }
                var crash = true
                val repository = f.recovery(
                    fileStore = oldPlanFiles,
                    hook = { _, point ->
                        if (point == "afterSettingsTarget:alice" && crash) {
                            crash = false; throw IOException("synthetic process boundary")
                        }
                    })
                val op =
                    repository.preparePrivateImport(bundle, null, f.settings.settingsFlow.first())
                assertTrue(
                    File(
                        f.context.filesDir,
                        "companion_transfers/plans/${op.operationId}.json"
                    ).isFile
                )
                assertFalse(
                    File(
                        f.context.filesDir,
                        "companion_transfers/plans/${op.operationId}/plan.json"
                    ).exists()
                )
                assertTrue(runCatching { repository.resumeOperation(op.operationId) }.isFailure)
                assertEquals(
                    "[]",
                    f.database.contextTransferOperationDao()
                        .get(op.operationId)!!.completedSettingsJson
                )
                val written = f.settings.settingsFlow.first {
                    it.companionSettings("alice")
                        .let { companion -> companion.revision == 1L && companion.zoneId == "UTC" }
                }
                assertEquals(1L, written.companionSettings("alice").revision)
                f.settings.updateCompanionSettings(
                    "alice",
                    1,
                    CompanionSettingsPatch(zoneId = "Asia/Tokyo")
                )
                f.settings.saveAssistants(written.assistants.map {
                    if (it.id == "alice") it.copy(
                        name = "后续用户名字"
                    ) else it
                }, "alice")
                f.reopenDatabase(); f.reopenSettings()
                val result = f.recovery().resumeOperation(op.operationId)
                assertEquals(ContextTransferStage.COMPLETE, result.stage)
                val current = f.settings.settingsFlow.first {
                    it.companionSettings("bob").zoneId == "Europe/Paris"
                }
                assertEquals("后续用户名字", current.assistants.single { it.id == "alice" }.name)
                assertEquals("Asia/Tokyo", current.companionSettings("alice").zoneId)
                assertEquals(2L, current.companionSettings("alice").revision)
                assertEquals("Europe/Paris", current.companionSettings("bob").zoneId)
                assertEquals(2, result.completedSettingsTargets)
            }
        }

    @Test
    fun partialTargetConflict_doesNotRepeatAlreadyAcknowledgedTarget() = runBlocking<Unit> {
        CompanionTransferFixture().use { f ->
            f.initialize()
            val repository = f.recovery()
            val op = repository.preparePrivateImport(
                ContextDataBundle(
                    version = 3,
                    companions = listOf(
                        CompanionContextTransferEntry("alice", zoneId = "UTC"),
                        CompanionContextTransferEntry("bob", zoneId = "Europe/Paris")
                    )
                ), null, f.settings.settingsFlow.first()
            )
            val bobWrite = f.settings.updateCompanionSettings(
                "bob",
                0,
                CompanionSettingsPatch(zoneId = "Asia/Tokyo")
            )
            assertTrue(bobWrite is CompanionSettingsWriteResult.Saved)
            assertTrue(runCatching { repository.resumeOperation(op.operationId) }.isFailure)
            val row = f.database.contextTransferOperationDao().get(op.operationId)!!
            assertEquals("FILES_COMMITTED", row.stage)
            assertEquals(
                listOf("alice"),
                AppJson.gson.fromJson(row.completedSettingsJson, Array<String>::class.java).toList()
            )
            val aliceWrite = f.settings.updateCompanionSettings(
                "alice",
                1,
                CompanionSettingsPatch(zoneId = "Asia/Shanghai")
            )
            assertTrue(aliceWrite is CompanionSettingsWriteResult.Saved)
            assertTrue(runCatching { repository.resumeOperation(op.operationId) }.isFailure)
            val after = f.settings.settingsFlow.first {
                val alice = it.companionSettings("alice")
                val bob = it.companionSettings("bob")
                alice.revision == 2L && alice.zoneId == "Asia/Shanghai" && bob.revision == 1L && bob.zoneId == "Asia/Tokyo"
            }
            assertEquals(
                "Asia/Shanghai",
                after.companionSettings("alice").zoneId
            )
            assertEquals(2L, after.companionSettings("alice").revision)
            assertEquals(
                "Asia/Tokyo",
                after.companionSettings("bob").zoneId
            )
            assertEquals(
                1,
                repository.listPendingOperations(ContextTransferOperationKind.PRIVATE_LIBRARY).size
            )
        }
    }

    @Test
    fun changedRoomPrecondition_refusesBeforeAnyStageWrite() = runBlocking<Unit> {
        CompanionTransferFixture().use { f ->
            f.initialize()
            val original = WorldBookEntry(id = "world", title = "合成", content = "原值")
            f.worlds.upsertEntry(original)
            val repository = f.recovery()
            val op = repository.preparePrivateImport(
                ContextDataBundle(
                    version = 3,
                    worldBookEntries = listOf(original.copy(content = "导入值")),
                    assistants = listOf(Assistant(id = "new-role"))
                ), null, f.settings.settingsFlow.first()
            )
            f.worlds.upsertEntry(original.copy(content = "冻结后用户编辑"))
            assertTrue(runCatching { repository.resumeOperation(op.operationId) }.isFailure)
            assertEquals("冻结后用户编辑", f.worlds.getEntry("world")!!.content)
            assertEquals(
                "PLANNED",
                f.database.contextTransferOperationDao().get(op.operationId)!!.stage
            )
            assertFalse(f.settings.settingsFlow.first().assistants.any { it.id == "new-role" })
        }
    }

    @Test
    fun outerRoomFailure_rollsBackMemoryAndWorldsAndSamePlanCanContinue() = runBlocking<Unit> {
        CompanionTransferFixture().use { source ->
            CompanionTransferFixture().use { f ->
                source.initialize(); f.initialize()
                val original = source.archive.ensureDailySpace("alice", MEMORY_UNKNOWN_PERSONA)
                source.archive.addRecord(original, ManualMemoryRecordDraft("已授权私人原始记忆"))
                val bundle = ContextDataBundle(
                    version = 3,
                    memoryArchive = source.memoryTransfer.exportArchive(),
                    worldBookEntries = listOf(
                        WorldBookEntry(
                            id = "one",
                            title = "一",
                            content = "第一条"
                        ), WorldBookEntry(id = "two", title = "二", content = "第二条")
                    )
                )
                val target = f.archive.ensureDailySpace("alice", MEMORY_UNKNOWN_PERSONA)
                val memoryTarget = MemoryImportTarget(
                    MemoryImportTargetMode.EXISTING,
                    "alice",
                    target.spaceId,
                    target.personaKey,
                    target.generation
                )
                val groups = MemoryTransferLegacyConversion.sourceGroups(
                    MemoryTransferLegacyConversion.materialize(bundle)
                )
                val memoryPlan = f.memoryTransfer.prepareImport(
                    bundle,
                    groups.associate { it.key to memoryTarget })
                val worlds = object : WorldBookRepository by f.worlds {
                    override suspend fun upsertEntry(entry: WorldBookEntry) {
                        f.worlds.upsertEntry(entry); if (entry.id == "two") throw IOException("synthetic outer transaction failure")
                    }
                }
                val repository = f.recovery(worldRepository = worlds)
                val op = repository.preparePrivateImport(
                    bundle,
                    memoryPlan,
                    f.settings.settingsFlow.first()
                )
                assertTrue(runCatching { repository.resumeOperation(op.operationId) }.isFailure)
                assertEquals(0, f.archive.listRecords(target).size)
                assertTrue(f.worlds.listEntries().isEmpty())
                assertEquals(
                    "PLANNED",
                    f.database.contextTransferOperationDao().get(op.operationId)!!.stage
                )
                assertEquals(0L, queryCount(f, "memory_transfer_receipts"))
                val completed = f.recovery().resumeOperation(op.operationId)
                assertEquals(ContextTransferStage.COMPLETE, completed.stage)
                val restored = f.memoryTransfer.exportArchive()
                assertEquals("已授权私人原始记忆", restored.records.single().content)
                assertTrue(restored.origins.isNotEmpty())
                assertTrue(restored.origins.all { it.origin.bundleVersion == 3 })
                assertEquals(
                    restored,
                    ContextTransferCodec().decode(
                        ContextTransferCodec().encode(
                            ContextDataBundle(
                                version = 3,
                                memoryArchive = restored
                            )
                        )
                    ).memoryArchive
                )
                assertTrue(restored.sources.isNotEmpty())
                assertTrue(restored.sources.all { it.messageId.isEmpty() && it.conversationId.isEmpty() })
                restored.sources.forEach { source ->
                    f.database.openHelper.readableDatabase.query(
                        "SELECT messageId, conversationId FROM memory_source_evidence WHERE id = ?",
                        arrayOf(source.id),
                    ).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertTrue(cursor.isNull(0))
                        assertEquals("", cursor.getString(1))
                        assertFalse(cursor.moveToNext())
                    }
                }
                assertTrue(f.database.conversationDao().listConversations().isEmpty())
                assertEquals(1L, queryCount(f, "memory_transfer_receipts"))
                f.recovery().resumeOperation(op.operationId)
                assertEquals(1, f.memoryTransfer.exportArchive().records.size)
                assertEquals(1L, queryCount(f, "memory_transfer_receipts"))
            }
        }
    }

    @Test
    fun incompleteFileStage_retryUsesExistingHashWithoutReplacingPublishedBytes() =
        runBlocking<Unit> {
            CompanionTransferFixture().use { f ->
                f.initialize()
                val first = syntheticAppearance()
                val secondBytes = syntheticTransferPng(3, 2)
                val second = CompanionTransferImage(
                    CompanionTransferImageValidation.sha256(secondBytes),
                    "image/png",
                    3,
                    2,
                    Base64.getEncoder().encodeToString(secondBytes)
                )
                val pack = first.copy(
                    visuals = first.visuals.copy(
                        resources = first.visuals.resources + CompanionAppearanceResource(
                            "bg",
                            "背景",
                            second.sha256,
                            CompanionImageKind.BACKGROUND
                        ), selectedBackgroundId = "bg"
                    ), images = first.images + second
                )
                var fail = true
                var retained: CompanionPublishedTransferImage? = null
                val files = object : CompanionTransferFileStore by f.files {
                    override suspend fun publishImages(images: List<CompanionTransferImage>): List<CompanionPublishedTransferImage> {
                        if (fail) {
                            fail = false; retained = f.files.publishImages(images.take(1))
                                .single(); throw IOException("synthetic file-stage failure")
                        }
                        return f.files.publishImages(images)
                    }
                }
                val repository = f.recovery(fileStore = files)
                val preview = repository.prepareAppearanceImport(
                    CompanionAppearanceTransferCodec().encode(pack), "bob"
                )
                assertTrue(runCatching { repository.resumeOperation(preview.operationId) }.isFailure)
                assertEquals(
                    "ROOM_COMMITTED",
                    f.database.contextTransferOperationDao().get(preview.operationId)!!.stage
                )
                val originalFile = File(retained!!.path)
                val before = originalFile.readBytes()
                val timestamp = originalFile.lastModified()
                f.reopenDatabase()
                assertEquals(
                    ContextTransferStage.COMPLETE,
                    f.recovery(fileStore = files).resumeOperation(preview.operationId).stage
                )
                assertArrayEquals(before, originalFile.readBytes())
                assertEquals(timestamp, originalFile.lastModified())
                assertEquals(
                    2,
                    f.settings.settingsFlow.first().companionSettings("bob").visuals.resources.size
                )
            }
        }

    @Test
    fun frozenPlanAndPublishedFileTampering_refusesWithoutChangingSettings() = runBlocking<Unit> {
        CompanionTransferFixture().use { f ->
            f.initialize()
            val repository = f.recovery()
            val preview = repository.prepareAppearanceImport(
                CompanionAppearanceTransferCodec().encode(syntheticAppearance()), "bob"
            )
            val plan =
                File(
                    f.context.filesDir,
                    "companion_transfers/plans/${preview.operationId}/plan.json"
                )
            assertTrue(plan.isFile)
            plan.appendText(" ")
            assertTrue(runCatching { repository.resumeOperation(preview.operationId) }.isFailure)
            assertEquals(
                "PLANNED",
                f.database.contextTransferOperationDao().get(preview.operationId)!!.stage
            )
            assertEquals(0L, f.settings.settingsFlow.first().companionSettings("bob").revision)
            val next = f.recovery(hook = { _, point ->
                if (point == "afterFilesPublish") throw IOException("synthetic before receipt")
            })
            val other = next.prepareAppearanceImport(
                CompanionAppearanceTransferCodec().encode(syntheticAppearance()), "bob"
            )
            assertTrue(runCatching { next.resumeOperation(other.operationId) }.isFailure)
            val image = File(
                f.context.filesDir,
                "companion_transfers/assets/${syntheticTransferImage().sha256}/image.png"
            )
            assertTrue(image.isFile)
            image.writeText("user changed private synthetic content")
            assertTrue(runCatching { f.recovery().resumeOperation(other.operationId) }.isFailure)
            assertEquals("user changed private synthetic content", image.readText())
            assertEquals(0L, f.settings.settingsFlow.first().companionSettings("bob").revision)
        }
    }

    @Test
    fun oldPrivateMissingDomain_keepsExistingCompanionSettings() = runBlocking<Unit> {
        CompanionTransferFixture().use { f ->
            f.initialize()
            val written = f.settings.updateCompanionSettings(
                "alice",
                0,
                CompanionSettingsPatch(
                    zoneId = "Asia/Tokyo",
                    writing = CompanionWritingSettings(extraStyle = "用户既有写法")
                )
            )
            assertTrue(written is CompanionSettingsWriteResult.Saved)
            val before = f.settings.settingsFlow.first {
                val companion = it.companionSettings("alice")
                companion.revision == 1L && companion.zoneId == "Asia/Tokyo" && companion.writing.extraStyle == "用户既有写法"
            }
            val oldBundle = ContextTransferCodec().decode(
                ContextTransferCodec().encode(
                    ContextDataBundle(
                        version = 2,
                        assistants = listOf(before.assistants.single { it.id == "alice" }
                            .copy(name = "旧包名字"))
                    )
                )
            )
            val repository = f.recovery()
            val op = repository.preparePrivateImport(oldBundle, null, before)
            repository.resumeOperation(op.operationId)
            val after =
                f.settings.settingsFlow.first { it.assistants.single { assistant -> assistant.id == "alice" }.name == "旧包名字" }
            assertEquals(
                before.companionSettings("alice"),
                after.companionSettings("alice")
            )
            assertEquals(
                "旧包名字",
                after.assistants.single { it.id == "alice" }.name
            )
        }
    }

    @Test
    fun publicExport_stripsMetadataAndPrivateFieldsAndMapsDuplicatePixels() = runBlocking<Unit> {
        CompanionTransferFixture().use { f ->
            f.initialize()
            val source = File(f.context.filesDir, "source.png").apply {
                writeBytes(
                    syntheticTransferPng(metadata = true)
                )
            }
            val image = CompanionImageResource(
                "private-original-image-id",
                "合成立绘一",
                source.path,
                CompanionImageKind.PORTRAIT,
                2,
                3
            )
            val visuals = CompanionVisualSettings(
                resources = listOf(
                    image,
                    image.copy(id = "private-alias", name = "立绘二")
                ),
                outfits = listOf(
                    CompanionOutfit(
                        "private-original-outfit",
                        "春装",
                        image.id,
                        mapOf("happy" to "private-alias")
                    )
                ),
                selectedOutfitId = "private-original-outfit"
            )
            val written = f.settings.updateCompanionSettings(
                "alice",
                0,
                CompanionSettingsPatch(
                    writing = CompanionWritingSettings(extraStyle = "PRIVATE_RAW_SENTINEL"),
                    visuals = visuals
                )
            )
            assertTrue(written is CompanionSettingsWriteResult.Saved)
            val current = f.settings.settingsFlow.first {
                val companion = it.companionSettings("alice")
                companion.revision == 1L && companion.visuals == visuals && companion.writing.extraStyle == "PRIVATE_RAW_SENTINEL"
            }
            f.settings.saveAssistants(current.assistants.map {
                if (it.id == "alice") it.copy(
                    systemPrompt = "PRIVATE_KEY_SENTINEL",
                    avatarUri = source.path
                ) else it
            }, "alice")
            val protectedSettings = f.settings.settingsFlow.first {
                it.assistants.single { assistant -> assistant.id == "alice" }.let { assistant ->
                    assistant.systemPrompt == "PRIVATE_KEY_SENTINEL" && assistant.avatarUri == source.path
                }
            }
            val repository = f.recovery()
            val raw = repository.exportPackage("alice")
            for (sentinel in listOf(
                source.path,
                "private-original-image-id",
                "private-original-outfit",
                "PRIVATE_RAW_SENTINEL",
                "PRIVATE_KEY_SENTINEL",
                "voice-clone-sample-secret"
            )) assertFalse(raw.contains(sentinel))
            val pack = CompanionAppearanceTransferCodec().decode(raw)
            assertEquals(1, pack.images.size)
            assertEquals(2, pack.visuals.resources.size)
            assertFalse(
                String(
                    Base64.getDecoder().decode(pack.images.single().dataBase64),
                    Charsets.ISO_8859_1
                ).contains("private_path")
            )
            val beforeAlice = protectedSettings.companionSettings("alice")
            val preview = repository.prepareAppearanceImport(raw, "bob")
            repository.resumeOperation(preview.operationId)
            val after = f.settings.settingsFlow.first {
                it.companionSettings("bob")
                    .let { companion -> companion.revision == 1L && companion.visuals.resources.size == 2 }
            }
            val bob = after.companionSettings("bob").visuals
            assertEquals(2, bob.resources.size)
            assertEquals(1, bob.resources.map { it.path }.distinct().size)
            assertTrue(bob.resources.all { it.id.startsWith("transfer_") && it.id !in visuals.resources.map { old -> old.id } })
            assertEquals(beforeAlice, after.companionSettings("alice"))
            assertTrue(source.isFile)
        }
    }

    @Test
    fun avatarFileStage_isFrozenBeforeRoomAndResumesWithoutRepeatedAvatarWork() =
        runBlocking<Unit> {
            CompanionTransferFixture().use { f ->
                f.initialize()
                val written = f.settings.updateCompanionSettings(
                    "alice",
                    0,
                    CompanionSettingsPatch(zoneId = "Asia/Tokyo")
                )
                assertTrue(written is CompanionSettingsWriteResult.Saved)
                val before = f.settings.settingsFlow.first {
                    it.companionSettings("alice")
                        .let { companion -> companion.revision == 1L && companion.zoneId == "Asia/Tokyo" }
                }
                var preparedImages = 0
                var publishedStages = 0
                val files = object : CompanionTransferFileStore by f.files {
                    override suspend fun prepareImage(bytes: ByteArray): CompanionTransferImage {
                        preparedImages++; return f.files.prepareImage(bytes)
                    }

                    override suspend fun publishImages(images: List<CompanionTransferImage>): List<CompanionPublishedTransferImage> {
                        publishedStages++; return f.files.publishImages(images)
                    }
                }
                val input = syntheticTransferPng(metadata = true)
                val bundle = ContextDataBundle(
                    version = 1,
                    assistants = listOf(before.assistants.single { it.id == "alice" }
                        .copy(name = "图片角色卡")),
                    worldBookEntries = listOf(
                        WorldBookEntry(
                            id = "avatar-world",
                            title = "合成",
                            content = "原图角色的设定"
                        )
                    )
                )
                val repository = f.recovery(
                    fileStore = files,
                    hook = { _, point -> if (point == "afterRoomCommit") throw IOException("synthetic before file stage") })
                val op = repository.preparePrivateImportWithAvatar(
                    bundle,
                    null,
                    before,
                    ContextTransferAvatarInput("alice", input)
                )
                assertEquals(1, preparedImages)
                assertFalse(File(f.context.filesDir, "companion_transfers/assets").exists())
                input.fill(0)
                assertTrue(runCatching { repository.resumeOperation(op.operationId) }.isFailure)
                assertEquals(0, publishedStages)
                assertEquals(
                    "ROOM_COMMITTED",
                    f.database.contextTransferOperationDao().get(op.operationId)!!.stage
                )
                f.reopenDatabase()
                assertEquals(
                    ContextTransferStage.COMPLETE,
                    f.recovery(fileStore = files).resumeOperation(op.operationId).stage
                )
                assertEquals(1, preparedImages)
                assertEquals(1, publishedStages)
                val after = f.settings.settingsFlow.first {
                    it.assistants.single { assistant -> assistant.id == "alice" }
                        .let { assistant -> assistant.name == "图片角色卡" && assistant.avatarUri.isNotBlank() }
                }
                val avatar = File(after.assistants.single { it.id == "alice" }.avatarUri)
                assertTrue(avatar.isFile)
                assertTrue(avatar.path.contains("companion_transfers"))
                assertFalse(
                    String(
                        avatar.readBytes(),
                        Charsets.ISO_8859_1
                    ).contains("private_path")
                )
                assertEquals(before.companionSettings("alice"), after.companionSettings("alice"))
                f.recovery(fileStore = files).resumeOperation(op.operationId)
                assertEquals(1, preparedImages)
                assertEquals(1, publishedStages)
            }
        }

    @Test
    fun collidingLocalPlanId_cannotRemoveOrReplaceExistingFrozenPlan() = runBlocking<Unit> {
        CompanionTransferFixture().use { f ->
            f.initialize()
            val repository = RoomContextTransferRecoveryRepository(
                f.database,
                f.settings,
                f.worlds,
                f.presets,
                f.memoryTransfer,
                f.files,
                newId = { "same-local-plan" })
            val bundle = ContextDataBundle(
                version = 3,
                companions = listOf(CompanionContextTransferEntry("alice", zoneId = "UTC"))
            )
            val first =
                repository.preparePrivateImport(bundle, null, f.settings.settingsFlow.first())
            val file =
                File(f.context.filesDir, "companion_transfers/plans/${first.operationId}/plan.json")
            assertTrue(file.isFile)
            val bytes = file.readBytes()
            assertTrue(runCatching {
                repository.preparePrivateImport(
                    bundle,
                    null,
                    f.settings.settingsFlow.first()
                )
            }.isFailure)
            assertArrayEquals(bytes, file.readBytes())
            assertEquals(
                1,
                repository.listPendingOperations(ContextTransferOperationKind.PRIVATE_LIBRARY).size
            )
            assertEquals(
                ContextTransferStage.COMPLETE,
                repository.resumeOperation(first.operationId).stage
            )
            assertEquals("UTC", f.settings.settingsFlow.first().companionSettings("alice").zoneId)
        }
    }

    @Test
    fun cancelledPreview_hasNoBusinessWritesAndLateResumeCannotReviveIt() = runBlocking<Unit> {
        CompanionTransferFixture().use { f ->
            f.initialize()
            val before = f.settings.settingsFlow.first()
            val repository = f.recovery()
            val preview = repository.prepareAppearanceImport(
                CompanionAppearanceTransferCodec().encode(syntheticAppearance()), "bob"
            )
            repository.cancelPreview(preview.operationId)
            assertTrue(
                runCatching { repository.resumeOperation(preview.operationId) }.exceptionOrNull()?.message?.contains(
                    "已取消"
                ) == true
            )
            assertEquals(before, f.settings.settingsFlow.first())
            assertTrue(
                repository.listPendingOperations(ContextTransferOperationKind.PUBLIC_APPEARANCE)
                    .isEmpty()
            )
            assertFalse(File(f.context.filesDir, "companion_transfers/assets").exists())
            assertEquals(
                "CANCELLED",
                f.database.contextTransferOperationDao().get(preview.operationId)!!.stage
            )
        }
    }

    private fun queryCount(f: CompanionTransferFixture, table: String): Long =
        f.database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table")
            .use { it.moveToFirst(); it.getLong(0) }
}
