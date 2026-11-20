package com.example.myapplication.data.repository.context

import android.app.Application
import com.example.myapplication.context.MemoryRecallEngine
import com.example.myapplication.data.local.memory.MemoryEntryEntity
import com.example.myapplication.model.*
import com.example.myapplication.testutil.MemoryExtractionFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ArchiveMemoryRepositoryTest {
    @Test
    fun activation_preservesUnprojectedLegacyWithoutBackfill() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.database.memoryDao().upsertMemoryEntry(
                MemoryEntryEntity(
                    "old",
                    "assistant",
                    "a",
                    "a",
                    "旧资料706",
                    72,
                    true,
                    "missing-message",
                    21,
                    11,
                    31
                )
            )
            assertEquals(MEMORY_ENGINE_CURRENT, f.archive.activateCurrentEngine())
            val facade = facade(f)
            val entry = facade.listEntries().single()
            assertEquals("old", entry.id)
            assertEquals(1L, entry.archiveRevision)
            assertEquals(72, entry.importance)
            assertTrue(entry.pinned)
            assertEquals(11L, entry.createdAt)
            val space = f.archive.listSpaces("a").single()
            val context = requireNotNull(f.archive.getManagementContext("a", space.id))
            val detail = requireNotNull(f.archive.getRecordDetails(context, entry.id))
            assertEquals(MemoryRecordKind.LEGACY_UNCLASSIFIED, detail.record.kind)
            assertEquals(MemorySourceStatus.MISSING, detail.sources.single().status)
            assertEquals("", detail.sources.single().content)
            assertTrue(f.archive.listPendingIngress(context).isEmpty())
            assertTrue(f.archive.listJobHistory(context).isEmpty())
            assertEquals(entry, facade.observeEntries().first().single())
        }
    }

    @Test
    fun legacyEdit_isSameIdCasAndVisibleToActualRecall() = runBlocking {
        MemoryExtractionFixture(true).use { f ->
            val runtime = f.contexts(purpose = MemoryPurpose.LEGACY_COMPAT).single()
            f.database.memoryDao().upsertMemoryEntry(
                MemoryEntryEntity(
                    "old",
                    "assistant",
                    "a",
                    "a",
                    "旧地址706",
                    updatedAt = 10
                )
            )
            f.archive.activateCurrentEngine()
            val facade = facade(f)
            val original = facade.listEntries().single()
            facade.upsertEntry(original.copy(content = "新地址819号", pinned = true))
            val current = facade.listEntries().single()
            assertEquals(original.id, current.id)
            assertEquals(2L, current.archiveRevision)
            assertEquals("新地址819号", f.database.memoryDao().listMemoryEntries().single().content)
            assertTrue(runCatching { facade.upsertEntry(original.copy(content = "旧草稿覆盖")) }.isFailure)
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    runtime,
                    "新地址819",
                    emptyList(),
                    5,
                    2000,
                    false
                )
            )
            assertTrue(plan.materials.any { it.ref.id == "old" && it.ref.revision == 2L && it.content == current.content })
            assertFalse(plan.section.contains("旧地址706"))
            f.reopen()
            assertEquals(current, facade(f).listEntries().single())
        }
    }

    @Test
    fun legacyDelete_requiresSeenRevisionAndKeepsTombstone() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.database.memoryDao()
                .upsertMemoryEntry(MemoryEntryEntity("old", "assistant", "a", "a", "原版本"))
            f.archive.activateCurrentEngine()
            val facade = facade(f)
            val original = facade.listEntries().single()
            facade.upsertEntry(original.copy(content = "新版本"))
            assertTrue(runCatching {
                facade.deleteEntry(
                    original.id,
                    requireNotNull(original.archiveRevision)
                )
            }.isFailure)
            assertTrue(runCatching { facade.deleteEntry(original.id) }.isFailure)
            facade.deleteEntry(original.id, 2)
            assertTrue(facade.listEntries().isEmpty())
            assertTrue(f.database.memoryDao().listMemoryEntries().isEmpty())
            assertEquals("DELETED", f.database.memoryArchiveDao().getRecord(original.id)?.status)
            assertTrue(runCatching { facade.upsertEntry(original.copy(content = "不得复活")) }.isFailure)
        }
    }

    @Test
    fun legacyCapacity_doesNotPhysicallyPruneOtherScopes() = runBlocking {
        MemoryExtractionFixture().use { f ->
            repeat(5) { index ->
                f.database.memoryDao().upsertMemoryEntry(
                    MemoryEntryEntity(
                        "old-$index",
                        "assistant",
                        if (index % 2 == 0) "a" else "b",
                        if (index % 2 == 0) "a" else "b",
                        "历史$index"
                    )
                )
            }
            f.archive.activateCurrentEngine()
            facade(f).pruneToCapacity(1)
            assertEquals(5, facade(f).listEntries().size)
            assertEquals(5, f.database.memoryDao().listMemoryEntries().size)
        }
    }

    @Test
    fun manualLegacyRevision_survivesDeletingOldMessage() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val runtime = f.contexts(purpose = MemoryPurpose.LEGACY_COMPAT).single()
            f.add(runtime, "original", "原消息来源")
            f.database.memoryDao().upsertMemoryEntry(
                MemoryEntryEntity(
                    "old",
                    "assistant",
                    "a",
                    "a",
                    "旧记忆",
                    sourceMessageId = "original"
                )
            )
            f.archive.activateCurrentEngine()
            val facade = facade(f)
            facade.upsertEntry(facade.listEntries().single().copy(content = "用户独立编辑的版本"))
            f.store.deleteMessageWithAssociations(
                requireNotNull(f.store.getConversation(runtime.conversationId)),
                runtime.conversationId,
                "original"
            )
            assertEquals("用户独立编辑的版本", facade.listEntries().single().content)
            assertEquals(
                "用户独立编辑的版本",
                f.database.memoryDao().listMemoryEntries().single().content
            )
        }
    }

    @Test
    fun failedLegacyWrite_rollsBackProjectionAndRevision() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.database.memoryDao()
                .upsertMemoryEntry(MemoryEntryEntity("old", "assistant", "a", "a", "回滚前正文"))
            f.archive.activateCurrentEngine()
            val facade = facade(f)
            val original = facade.listEntries().single()
            f.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER c6_fail_legacy BEFORE INSERT ON memory_record_revisions BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
            assertTrue(runCatching { facade.upsertEntry(original.copy(content = "不会留下的正文")) }.isFailure)
            assertEquals(original, facade.listEntries().single())
            assertEquals("回滚前正文", f.database.memoryDao().listMemoryEntries().single().content)
        }
    }

    @Test
    fun explicitCompatibility_survivesReadsAndRestart() = runBlocking {
        MemoryExtractionFixture(true).use { f ->
            val runtime = f.contexts().single()
            f.database.memoryDao().upsertMemoryEntry(
                MemoryEntryEntity(
                    "old",
                    "assistant",
                    "a",
                    "a",
                    "仍可管理的兼容资料"
                )
            )
            assertEquals(2, f.archive.getEngineVersion())
            f.archive.keepCompatibilityEngine()
            assertEquals(1, f.archive.getEngineVersion())
            f.reopen()
            assertEquals(1, f.archive.getEngineVersion())
            assertEquals(2, f.database.memoryArchiveDao().getEngineState()?.activationVersion)
            val facade = facade(f)
            facade.upsertEntry(facade.listEntries().single().copy(content = "兼容模式仍走档案CAS"))
            assertEquals(2L, facade.listEntries().single().archiveRevision)
            val coordinator =
                com.example.myapplication.conversation.ConversationMemoryExtractionCoordinator(
                    f.ai,
                    facade,
                    { f.now },
                    f.coordinator()
                )
            assertFalse(
                coordinator.updateRoleplayMemories(
                    runtime.conversationId,
                    f.settings.assistants.first(),
                    emptyList(),
                    f.settings,
                    8,
                    12
                ) { "不应发送的旧提取" })
            assertTrue(f.ai.snapshots.isEmpty())
        }
    }

    private fun facade(f: MemoryExtractionFixture) =
        ArchiveMemoryRepository(f.database, RoomMemoryRepository(f.database.memoryDao()), f.archive)
}
