package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModelStore
import com.example.myapplication.data.repository.context.ContextTransferRecoveryRepository
import com.example.myapplication.data.repository.context.ContextTransferCodec
import com.example.myapplication.model.*
import com.example.myapplication.testutil.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContextTransferRecoveryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun companionOnlyV3_isPreviewedAndRetryUsesFrozenOperationWithoutFalseSuccess() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val recovery = RecoveryFake()
            val services = createTestAiServices(
                settings = AppSettings(
                    assistants = listOf(
                        Assistant(
                            id = "alice",
                            name = "甲"
                        )
                    )
                ),
                dispatcher = mainDispatcherRule.dispatcher,
                apiServiceProvider = { _, _ -> error("资料操作禁止模型") },
                imageApiServiceProvider = { _, _ -> error("资料操作禁止图片模型") },
                streamClientProvider = { _, _ -> error("资料操作禁止流") })
            val vm = ContextTransferViewModel(
                services.settingsRepository,
                services.settingsEditor,
                FakeWorldBookRepository(),
                FakeMemoryRepository(),
                FakeConversationSummaryRepository(),
                contextTransferRecoveryRepository = recovery
            )
            val owner = ViewModelStore().apply { put("transfer", vm) }
            try {
                advanceUntilIdle()
                vm.previewImportJson(
                    ContextTransferCodec().encode(
                        ContextDataBundle(
                            version = 3,
                            companions = listOf(
                                CompanionContextTransferEntry(
                                    "alice",
                                    zoneId = "UTC"
                                )
                            )
                        )
                    ), ContextTransferSection.ALL
                )
                advanceUntilIdle()
                assertEquals(1, vm.uiState.value.importPreview!!.companionCount)
                assertTrue(vm.uiState.value.importPreview!!.canConfirmMemory)
                recovery.fail = true
                vm.confirmImport(); advanceUntilIdle()
                assertNotNull(vm.uiState.value.importPreview)
                assertFalse(vm.uiState.value.message.orEmpty().contains("已合并导入"))
                assertEquals(1, recovery.prepares)
                assertEquals(
                    ContextTransferStage.FILES_COMMITTED,
                    vm.uiState.value.pendingOperations.single().stage
                )
                recovery.fail = false
                vm.confirmImport(); advanceUntilIdle()
                assertEquals(1, recovery.prepares)
                assertEquals(2, recovery.resumes)
                assertNull(vm.uiState.value.importPreview)
                assertTrue(vm.uiState.value.message.orEmpty().contains("已合并导入"))
                assertTrue(vm.uiState.value.pendingOperations.isEmpty())
            } finally {
                owner.clear()
            }
        }

    @Test
    fun futureCompanionBundle_isRejectedBeforeAnyPlanAndExportUsesV3Domain() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val recovery = RecoveryFake()
            val services = createTestAiServices(
                settings = AppSettings(assistants = listOf(Assistant(id = "alice"))),
                dispatcher = mainDispatcherRule.dispatcher,
                apiServiceProvider = { _, _ -> error("禁止模型") },
                imageApiServiceProvider = { _, _ -> error("禁止图片模型") },
                streamClientProvider = { _, _ -> error("禁止流") })
            val vm = ContextTransferViewModel(
                services.settingsRepository,
                services.settingsEditor,
                FakeWorldBookRepository(),
                FakeMemoryRepository(),
                FakeConversationSummaryRepository(),
                contextTransferRecoveryRepository = recovery
            )
            val owner = ViewModelStore().apply { put("transfer", vm) }
            try {
                advanceUntilIdle()
                vm.previewImportJson(
                    """{"version":4,"exportedAt":1,"companions":[{"assistantId":"alice","schemaVersion":1,"zoneId":"UTC"}]}""",
                    ContextTransferSection.ALL
                )
                advanceUntilIdle()
                assertNull(vm.uiState.value.importPreview)
                assertNotNull(vm.uiState.value.message)
                assertEquals(0, recovery.prepares)
                var raw: String? = null
                vm.exportBundleJson(ContextTransferSection.ALL) { content, _ -> raw = content }
                advanceUntilIdle()
                val exported = ContextTransferCodec().decode(requireNotNull(raw))
                assertEquals(3, exported.version)
                assertEquals("alice", exported.companions!!.single().assistantId)
                assertEquals(setOf("alice"), recovery.exportIds)
            } finally {
                owner.clear()
            }
        }

    @Test
    fun restartedVm_canExplicitlyResumePersistedPendingOperation() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val recovery = RecoveryFake().apply {
                pending = operation.copy(stage = ContextTransferStage.ROOM_COMMITTED)
            }
            val services = createTestAiServices(
                settings = AppSettings(assistants = listOf(Assistant(id = "alice"))),
                dispatcher = mainDispatcherRule.dispatcher,
                apiServiceProvider = { _, _ -> error("禁止模型") },
                imageApiServiceProvider = { _, _ -> error("禁止图片模型") },
                streamClientProvider = { _, _ -> error("禁止流") })
            val vm = ContextTransferViewModel(
                services.settingsRepository,
                services.settingsEditor,
                FakeWorldBookRepository(),
                FakeMemoryRepository(),
                FakeConversationSummaryRepository(),
                contextTransferRecoveryRepository = recovery
            )
            val owner = ViewModelStore().apply { put("transfer", vm) }
            try {
                advanceUntilIdle()
                assertEquals(0, recovery.resumes)
                assertEquals(1, vm.uiState.value.pendingOperations.size)
                vm.resumePendingImport("not-owned"); advanceUntilIdle()
                assertEquals(0, recovery.resumes)
                vm.resumePendingImport("frozen-local"); advanceUntilIdle()
                assertEquals(1, recovery.resumes)
                assertEquals(0, recovery.prepares)
                assertTrue(vm.uiState.value.pendingOperations.isEmpty())
                assertTrue(vm.uiState.value.message.orEmpty().contains("已继续完成"))
            } finally {
                owner.clear()
            }
        }

    private class RecoveryFake : ContextTransferRecoveryRepository {
        val operation = ContextTransferOperationSummary(
            "frozen-local",
            ContextTransferOperationKind.PRIVATE_LIBRARY,
            ContextTransferStage.PLANNED,
            "合成导入",
            settingsTargetCount = 1,
            targetAssistantIds = setOf("alice")
        )
        var pending: ContextTransferOperationSummary? = null
        var prepares = 0
        var resumes = 0
        var fail = false
        var exportIds = emptySet<String>()
        override suspend fun exportPrivateCompanionData(assistantIds: Set<String>): List<CompanionContextTransferEntry> {
            exportIds = assistantIds; return listOf(
                CompanionContextTransferEntry(
                    "alice",
                    zoneId = "UTC"
                )
            )
        }

        override suspend fun preparePrivateImport(
            bundle: ContextDataBundle,
            memoryPlan: MemoryImportPlan?,
            expectedSettings: AppSettings
        ): ContextTransferOperationSummary {
            prepares++; pending = operation; return operation
        }

        override suspend fun resumeOperation(operationId: String): ContextTransferOperationSummary {
            check(operationId == operation.operationId)
            resumes++
            if (fail) {
                pending =
                    operation.copy(stage = ContextTransferStage.FILES_COMMITTED); error("合成设置阶段失败")
            }
            pending = null
            return operation.copy(
                stage = ContextTransferStage.COMPLETE,
                completedSettingsTargets = 1
            )
        }

        override suspend fun listPendingOperations(kind: ContextTransferOperationKind): List<ContextTransferOperationSummary> =
            listOfNotNull(pending).filter { it.kind == kind }

        override suspend fun exportPackage(characterId: String): String =
            error("私人资料不导出公开包")

        override suspend fun prepareAppearanceImport(
            rawJson: String,
            characterId: String
        ): CompanionAppearanceImportPreview = error("私人资料不导入公开包")

        override suspend fun cancelPreview(operationId: String) {
            pending = null
        }
    }
}
