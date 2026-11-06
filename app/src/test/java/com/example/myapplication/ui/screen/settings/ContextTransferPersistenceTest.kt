package com.example.myapplication.ui.screen.settings

import androidx.lifecycle.ViewModelStore
import com.example.myapplication.data.repository.context.ContextTransferCodec
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ContextDataBundle
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.testutil.FakeConversationSummaryRepository
import com.example.myapplication.testutil.FakeMemoryRepository
import com.example.myapplication.testutil.FakePresetRepository
import com.example.myapplication.testutil.FakeWorldBookRepository
import com.example.myapplication.testutil.createTestAiServices
import com.example.myapplication.viewmodel.AssistantAvatarImport
import com.example.myapplication.viewmodel.ContextImportPayload
import com.example.myapplication.viewmodel.ContextTransferSection
import com.example.myapplication.viewmodel.ContextTransferViewModel
import com.example.myapplication.viewmodel.MainDispatcherRule
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 验证现有VM取消/失败边界；头像、仓储和所有API都由本地可观测Fake隔离。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContextTransferPersistenceTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun previewAndCancel_neverWrite_andCancelledPendingImportCannotBeConfirmed() =
        runTest(dispatcherRule.dispatcher.scheduler) {
            Fixture(listOf(existing)).use { fixture ->
                advanceUntilIdle()
                fixture.vm.previewImportJson(
                    bundleJson(existing.copy(title = "导入草稿")),
                    ContextTransferSection.WORLD_BOOK
                )
                advanceUntilIdle()
                val preview = fixture.vm.uiState.value.importPreview!!
                assertEquals(1, preview.worldBookCount)
                assertEquals(existing.id, preview.conflicts.single().id)
                assertEquals(listOf(existing), fixture.worlds.listEntries())
                assertEquals(0, fixture.worldWrites)
                assertEquals(0, fixture.transactions)
                assertTrue(fixture.avatars.isEmpty())
                fixture.vm.dismissImportPreview()
                fixture.vm.confirmImport()
                advanceUntilIdle()
                assertNull(fixture.vm.uiState.value.importPreview)
                assertFalse(fixture.vm.uiState.value.isBusy)
                assertEquals(0, fixture.transactions)
                assertEquals(listOf(existing), fixture.worlds.listEntries())
            }
        }

    @Test
    fun mergeFailure_retainsPreviewAndPending_forRetryAndGatedSuccess() =
        runTest(dispatcherRule.dispatcher.scheduler) {
            Fixture().use { fixture ->
                advanceUntilIdle()
                fixture.vm.previewImportJson(
                    bundleJson(existing),
                    ContextTransferSection.WORLD_BOOK
                )
                advanceUntilIdle()
                val preview = fixture.vm.uiState.value.importPreview
                fixture.transactionFailure =
                    IllegalStateException("/data/user/0/synthetic/database")
                fixture.vm.confirmImport()
                advanceUntilIdle()
                assertFalse(fixture.vm.uiState.value.isBusy)
                assertEquals(preview, fixture.vm.uiState.value.importPreview)
                assertEquals("导入失败", fixture.vm.uiState.value.message)
                assertEquals(0, fixture.worldWrites)
                assertTrue(fixture.worlds.listEntries().isEmpty())
                fixture.transactionFailure = null
                val gate = CompletableDeferred<Unit>()
                fixture.transactionGate = gate
                fixture.vm.confirmImport()
                runCurrent()
                assertTrue(fixture.vm.uiState.value.isBusy)
                assertNull(fixture.vm.uiState.value.message)
                assertEquals(preview, fixture.vm.uiState.value.importPreview)
                assertEquals(0, fixture.worldWrites)
                gate.complete(Unit)
                advanceUntilIdle()
                assertFalse(fixture.vm.uiState.value.isBusy)
                assertNull(fixture.vm.uiState.value.importPreview)
                assertEquals(1, fixture.vm.uiState.value.worldBookCount)
                assertEquals(1, fixture.worldWrites)
                assertEquals(existing.id, fixture.worlds.listEntries().single().id)
                assertEquals("上下文数据已合并导入", fixture.vm.uiState.value.message)
                fixture.vm.consumeMessage()
                assertNull(fixture.vm.uiState.value.message)
                fixture.vm.confirmImport()
                advanceUntilIdle()
                assertEquals(1, fixture.worldWrites)
            }
        }

    @Test
    fun avatarFailure_isCaughtBeforeMerge_andRetryPreservesOriginalImagePayload() =
        runTest(dispatcherRule.dispatcher.scheduler) {
            Fixture().use { fixture ->
                advanceUntilIdle()
                val png = pngCard()
                fixture.vm.previewImportPayload(
                    ContextImportPayload("synthetic-card.png", "image/png", binaryContent = png),
                    ContextTransferSection.ASSISTANTS,
                )
                advanceUntilIdle()
                val preview = fixture.vm.uiState.value.importPreview!!
                assertEquals(1, preview.assistantCount)
                assertEquals(1, preview.worldBookCount)
                assertTrue(fixture.avatars.isEmpty())
                fixture.avatarFailure = IllegalStateException("/data/user/0/synthetic/avatar.png")
                fixture.vm.confirmImport()
                advanceUntilIdle()
                assertFalse(fixture.vm.uiState.value.isBusy)
                assertEquals(preview, fixture.vm.uiState.value.importPreview)
                assertEquals("导入失败", fixture.vm.uiState.value.message)
                assertEquals(0, fixture.transactions)
                assertEquals(0, fixture.worldWrites)
                assertArrayEquals(png, fixture.avatars.single().bytes)
                fixture.avatarFailure = null
                fixture.vm.confirmImport()
                advanceUntilIdle()
                assertEquals(2, fixture.avatars.size)
                assertArrayEquals(png, fixture.avatars.last().bytes)
                assertNull(fixture.vm.uiState.value.importPreview)
                val assistant = fixture.vm.settings.value.assistants.single()
                assertEquals("content://p06.fixture/avatar", assistant.avatarUri)
                assertEquals(assistant.id, fixture.worlds.listEntries().single().scopeId)
                assertEquals(1, fixture.transactions)
                assertEquals(1, fixture.worldWrites)
            }
        }

    @Test
    fun invalidNextPreview_clearsPreviousPendingWithoutWriting() =
        runTest(dispatcherRule.dispatcher.scheduler) {
            Fixture().use { fixture ->
                advanceUntilIdle()
                fixture.vm.previewImportJson(
                    bundleJson(existing),
                    ContextTransferSection.WORLD_BOOK
                )
                advanceUntilIdle()
                assertNotNull(fixture.vm.uiState.value.importPreview)
                fixture.vm.previewImportJson("not a data bundle", ContextTransferSection.WORLD_BOOK)
                advanceUntilIdle()
                assertFalse(fixture.vm.uiState.value.isBusy)
                assertNull(fixture.vm.uiState.value.importPreview)
                assertNotNull(fixture.vm.uiState.value.message)
                fixture.vm.confirmImport()
                advanceUntilIdle()
                assertEquals(0, fixture.transactions)
                assertEquals(0, fixture.worldWrites)
            }
        }

    private inner class Fixture(initialEntries: List<WorldBookEntry> = emptyList()) :
        AutoCloseable {
        val worlds = FakeWorldBookRepository(initialEntries)
        var worldWrites = 0
        var transactions = 0
        var transactionFailure: Throwable? = null
        var transactionGate: CompletableDeferred<Unit>? = null
        var avatarFailure: Throwable? = null
        val avatars = mutableListOf<AssistantAvatarImport>()
        private var apiCalls = 0
        private val services = createTestAiServices(
            settings = AppSettings(assistants = emptyList()),
            dispatcher = dispatcherRule.dispatcher,
            apiServiceProvider = { _, _ -> apiCalls++; error("资料库测试不得调用API") },
            imageApiServiceProvider = { _, _ -> apiCalls++; error("资料库测试不得调用图片API") },
            streamClientProvider = { _, _ -> apiCalls++; error("资料库测试不得调用流式客户端") },
        )
        val vm = ContextTransferViewModel(
            settingsRepository = services.settingsRepository,
            settingsEditor = services.settingsEditor,
            worldBookRepository = object : WorldBookRepository by worlds {
                override suspend fun upsertEntry(entry: WorldBookEntry) {
                    worldWrites++
                    worlds.upsertEntry(entry)
                }
            },
            memoryRepository = FakeMemoryRepository(),
            conversationSummaryRepository = FakeConversationSummaryRepository(),
            presetRepository = FakePresetRepository(),
            importedAssistantAvatarSaver = { avatar ->
                avatars += avatar
                avatarFailure?.let { throw it }
                "content://p06.fixture/avatar"
            },
            dataImportTransaction = { block ->
                transactions++
                transactionFailure?.let { throw it }
                transactionGate?.await()
                block()
            },
            existingConversationIdsProvider = { emptySet() },
        )
        private val store = ViewModelStore().apply { put("transfer", vm) }

        override fun close() {
            store.clear()
            assertEquals("全部网络注入应保持未调用", 0, apiCalls)
        }
    }

    private fun bundleJson(entry: WorldBookEntry) = ContextTransferCodec().encode(
        ContextDataBundle(exportedAt = 1L, worldBookEntries = listOf(entry)),
    )

    private fun pngCard(): ByteArray {
        val json =
            """{"spec":"chara_card_v2","data":{"name":"合成守灯人","description":"只用于测试","character_book":{"name":"合成群岛","entries":[{"name":"港湾","keys":["港湾"],"content":"灯塔位于港湾。"}]}}}"""
        val metadata = "chara\u0000${
            Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        }"
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        listOf(
            "IHDR" to ByteArray(13),
            "tEXt" to metadata.toByteArray(StandardCharsets.ISO_8859_1),
            "IEND" to ByteArray(0)
        ).forEach { (type, data) ->
            output.write(ByteBuffer.allocate(4).putInt(data.size).array())
            output.write(type.toByteArray(StandardCharsets.US_ASCII))
            output.write(data)
            output.write(ByteArray(4))
        }
        return output.toByteArray()
    }

    private companion object {
        val existing = WorldBookEntry(
            id = "p06-fixed-entry",
            title = "合成港湾",
            content = "测试正文",
            createdAt = 1L,
            updatedAt = 1L
        )
    }
}
