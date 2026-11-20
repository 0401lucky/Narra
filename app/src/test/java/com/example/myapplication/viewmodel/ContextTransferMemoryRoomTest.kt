package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.withTransaction
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.data.repository.context.ContextTransferCodec
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.model.*
import com.example.myapplication.testutil.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ContextTransferMemoryRoomTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun allBundleRollbackAndPostCommitRetry() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        MemoryExtractionFixture().use { source ->
            MemoryExtractionFixture(persistent = true).use { destination ->
                val from = source.contexts().single()
                source.remember(from, "真实资料恢复仅生成一次")
                val target = destination.contexts().single()
                val bundle = source.transferBundle().copy(
                    assistants = listOf(Assistant(id = "imported-role", name = "导入角色")),
                    worldBookEntries = listOf(
                        WorldBookEntry(
                            id = "world",
                            title = "合成设定",
                            content = "设定正文"
                        )
                    ),
                )
                val services = createTestAiServices(
                    settings = destination.settings, dispatcher = mainDispatcherRule.dispatcher,
                    apiServiceProvider = { _, _ -> error("资料测试禁止真实 API") },
                    imageApiServiceProvider = { _, _ -> error("资料测试禁止图片 API") },
                    streamClientProvider = { _, _ -> error("资料测试禁止流 API") })
                var worldFails = true
                var settingsFail = true
                var notifications = 0
                val worlds = FakeWorldBookRepository()
                val editor = object : AiSettingsEditor by services.settingsEditor {
                    override suspend fun saveAssistants(
                        assistants: List<Assistant>,
                        selectedAssistantId: String
                    ) {
                        if (settingsFail) error("合成设置保存失败")
                        services.settingsEditor.saveAssistants(assistants, selectedAssistantId)
                    }
                }
                val vm = ContextTransferViewModel(
                    services.settingsRepository, editor,
                    object : WorldBookRepository by worlds {
                        override suspend fun upsertEntry(entry: WorldBookEntry) {
                            if (worldFails) error("合成世界书保存失败")
                            worlds.upsertEntry(entry)
                        }
                    },
                    FakeMemoryRepository(), FakeConversationSummaryRepository(),
                    memoryTransferRepository = destination.transfer { notifications++ },
                    dataImportTransaction = { block -> destination.database.withTransaction { block() } },
                )
                val store = ViewModelStore().apply { put("transfer-test", vm) }
                try {
                    advanceUntilIdle()
                    vm.previewImportJson(
                        ContextTransferCodec().encode(bundle),
                        ContextTransferSection.ALL
                    )
                    awaitIdle(vm)
                    val preview = requireNotNull(vm.uiState.value.importPreview)
                    val option =
                        preview.memoryTargetOptions.single { it.target == target.importTarget() }
                    vm.selectMemoryImportTarget(preview.memoryGroups.single().key, option.target)
                    awaitIdle(vm)
                    vm.confirmImport()
                    awaitIdle(vm)
                    assertNotNull(vm.uiState.value.importPreview)
                    assertTrue(destination.archive.listRecords(target).isEmpty())
                    assertEquals(0, receiptCount(destination))
                    assertEquals(0, notifications)
                    worldFails = false
                    vm.confirmImport()
                    awaitIdle(vm)
                    assertNotNull(vm.uiState.value.importPreview)
                    assertEquals(1, destination.archive.listRecords(target).size)
                    assertEquals(1, receiptCount(destination))
                    assertEquals(1, notifications)
                    settingsFail = false
                    vm.confirmImport()
                    awaitIdle(vm)
                    assertNull(vm.uiState.value.importPreview)
                    assertEquals(1, destination.archive.listRecords(target).size)
                    assertEquals(1, receiptCount(destination))
                    assertTrue(services.settingsRepository.settingsFlow.first().assistants.any { it.id == "imported-role" })
                    assertTrue(destination.ai.snapshots.isEmpty())
                    destination.reopen()
                    assertEquals(
                        "真实资料恢复仅生成一次",
                        requireNotNull(destination.archive.getRecallSnapshot(target)).records.single().content
                    )
                } finally {
                    store.clear()
                }
            }
        }
    }

    private suspend fun awaitIdle(vm: ContextTransferViewModel) = withContext(Dispatchers.Default) {
        withTimeout(30_000) { vm.uiState.first { !it.isBusy } }
    }

    private fun receiptCount(fixture: MemoryExtractionFixture): Int =
        fixture.database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM memory_transfer_receipts")
            .use { cursor -> check(cursor.moveToFirst()); cursor.getInt(0) }
}
