package com.example.myapplication.ui.screen.settings

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.myapplication.ui.screenshot.ContextTransferRenderTest
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.viewmodel.ContextTransferSection
import com.example.myapplication.viewmodel.ContextTransferViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Screen直接消费真实VM的门控状态；这是隔离宿主接线，不是生产AppGraph导航测试。 */
class ContextTransferBusyTest : ContextTransferRenderTest() {
    @Test
    fun pendingMerge_disablesEveryUiWriteAction_andRepeatedTouchesDoNotStartAnotherTransaction() {
        var apiCalls = 0
        val services = createTestAiServices(
            settings = AppSettings(),
            apiServiceProvider = { _, _ -> apiCalls++; error("资料库UI测试不得联网") },
            imageApiServiceProvider = { _, _ -> apiCalls++; error("资料库UI测试不得请求图片") },
            streamClientProvider = { _, _ -> apiCalls++; error("资料库UI测试不得请求流") },
        )
        val gate = CompletableDeferred<Unit>()
        val worlds = FakeWorldBookRepository()
        var writes = 0
        var transactions = 0
        val vm = ContextTransferViewModel(
            settingsRepository = services.settingsRepository,
            settingsEditor = services.settingsEditor,
            worldBookRepository = object : WorldBookRepository by worlds {
                override suspend fun upsertEntry(entry: WorldBookEntry) {
                    writes++
                    worlds.upsertEntry(entry)
                }
            },
            memoryRepository = FakeMemoryRepository(),
            conversationSummaryRepository = FakeConversationSummaryRepository(),
            presetRepository = FakePresetRepository(),
            importedAssistantAvatarSaver = { error("JSON世界书没有头像") },
            dataImportTransaction = { block -> transactions++; gate.await(); block() },
            existingConversationIdsProvider = { emptySet() },
        )
        val store = ViewModelStore().apply { put("transfer", vm) }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        try {
            drainMainLooper()
            vm.previewImportJson(
                ContextTransferCodec().encode(
                    ContextDataBundle(
                        exportedAt = 1, worldBookEntries = listOf(
                            WorldBookEntry(
                                id = "p06-gated-ui",
                                title = "合成港湾",
                                content = "本地测试正文",
                                createdAt = 1,
                                updatedAt = 1
                            ),
                        )
                    )
                ),
                ContextTransferSection.WORLD_BOOK,
            )
            drainMainLooper()
            assertNotNull(vm.uiState.value.importPreview)
            renderP06(ScreenshotCases.require("E06_transfer-last-narrow-dark")) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    ContextTransferScreen(
                        uiState = vm.uiState.collectAsStateWithLifecycle().value,
                        onExportJson = { _, _ -> error("busy期间不得导出") },
                        onPreviewImportPayload = { _, _ -> error("busy期间不得导入预览") },
                        onConfirmImport = vm::confirmImport,
                        onDismissImportPreview = vm::dismissImportPreview,
                        onConsumeMessage = vm::consumeMessage,
                        onNavigateBack = {},
                    )
                }
            }
            scrollToEnd()
            pageNode(hasText("确认导入") and hasClickAction()).performTouchInput { click() }
            drainMainLooper()
            composeRule.waitForIdle()
            assertTrue(vm.uiState.value.isBusy)
            assertEquals(1, transactions)
            assertEquals(0, writes)
            val preview =
                hasAnyDescendant(hasText("来源：上下文数据包")) and hasScrollToIndexAction().not() and
                        hasAnyDescendant(hasScrollToIndexAction()).not() and hasTestTag(StageTag).not() and isRoot().not()
            repeat(2) {
                pageNode(hasText("处理中…") and hasClickAction() and hasAnyAncestor(preview))
                    .assertIsNotEnabled().performTouchInput { click() }
                pageNode(hasText("取消") and hasClickAction()).assertIsNotEnabled()
                    .performTouchInput { click() }
            }
            listOf("资料库备份", "角色包", "世界书", "记忆档案包", "预设包").forEach { title ->
                pageList().performScrollToNode(hasContentDescription("$title：查看说明"))
                val buttons = composeRule.onAllNodes(
                    hasText("处理中…") and hasClickAction() and hasAnyAncestor(groupMatcher(title))
                )
                buttons.assertCountEquals(2)
                repeat(2) {
                    buttons[it].performScrollTo().assertIsNotEnabled().performTouchInput { click() }
                }
            }
            assertEquals(1, transactions)
            assertEquals(0, writes)
            assertTrue(registry.launches.isEmpty())
            assertNotNull(vm.uiState.value.importPreview)
            composeRule.runOnIdle { gate.complete(Unit) }
            drainMainLooper()
            composeRule.waitForIdle()
            assertFalse(vm.uiState.value.isBusy)
            assertNull(vm.uiState.value.importPreview)
            assertEquals(1, writes)
            assertEquals(1, vm.uiState.value.worldBookCount)
            assertEquals("p06-gated-ui", runBlocking { worlds.listEntries() }.single().id)
            assertEquals(0, apiCalls)
            assertNoExternalActivity()
        } finally {
            gate.complete(Unit)
            store.clear()
        }
    }
}
