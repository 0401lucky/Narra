package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModelStore
import com.example.myapplication.data.repository.context.CompanionAppearanceTransferRepository
import com.example.myapplication.model.CompanionAppearanceImportPreview
import com.example.myapplication.model.ContextTransferOperationKind
import com.example.myapplication.model.ContextTransferOperationSummary
import com.example.myapplication.model.ContextTransferStage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionAppearanceTransferViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun replacingPreviewWithInvalidPackage_clearsCancelledPlanAndCannotConfirmIt() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val repository = AppearanceFake()
            val vm = CompanionAppearanceTransferViewModel("alice", repository)
            val owner = ViewModelStore().apply { put("appearance", vm) }
            try {
                advanceUntilIdle()
                vm.previewImport("valid")
                advanceUntilIdle()
                assertEquals("plan-1", vm.uiState.value.preview!!.operationId)
                vm.previewImport("invalid")
                advanceUntilIdle()
                assertEquals(listOf("plan-1"), repository.cancelled)
                assertNull(vm.uiState.value.preview)
                assertTrue(vm.uiState.value.pendingOperations.isEmpty())
                assertNotNull(vm.uiState.value.message)
                vm.confirmImport()
                advanceUntilIdle()
                assertEquals(0, repository.resumes)
                vm.previewImport("valid")
                advanceUntilIdle()
                assertEquals("plan-2", vm.uiState.value.preview!!.operationId)
                assertEquals(listOf("plan-1"), repository.cancelled)
            } finally {
                owner.clear()
            }
        }

    @Test
    fun replacingPartiallyImportedPreview_keepsItsReceiptAvailableToResume() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val repository = AppearanceFake()
            val vm = CompanionAppearanceTransferViewModel("alice", repository)
            val owner = ViewModelStore().apply { put("appearance", vm) }
            try {
                advanceUntilIdle()
                vm.previewImport("valid")
                advanceUntilIdle()
                repository.pending["plan-1"] = repository.pending.getValue("plan-1")
                    .copy(stage = ContextTransferStage.FILES_COMMITTED)
                vm.previewImport("valid")
                advanceUntilIdle()
                assertEquals("plan-2", vm.uiState.value.preview!!.operationId)
                assertTrue(repository.cancelled.isEmpty())
                assertEquals(
                    ContextTransferStage.FILES_COMMITTED,
                    vm.uiState.value.pendingOperations.single { it.operationId == "plan-1" }.stage
                )
                vm.resumeOperation("plan-1")
                advanceUntilIdle()
                assertEquals(1, repository.resumes)
                assertFalse(repository.pending.containsKey("plan-1"))
                assertEquals("plan-2", vm.uiState.value.preview!!.operationId)
            } finally {
                owner.clear()
            }
        }

    private class AppearanceFake : CompanionAppearanceTransferRepository {
        val pending = linkedMapOf<String, ContextTransferOperationSummary>()
        val cancelled = mutableListOf<String>()
        var resumes = 0
        private var next = 0

        override suspend fun prepareAppearanceImport(
            rawJson: String,
            characterId: String
        ): CompanionAppearanceImportPreview {
            require(rawJson == "valid") { "合成无效装扮包" }
            val id = "plan-${++next}"
            pending[id] = ContextTransferOperationSummary(
                id,
                ContextTransferOperationKind.PUBLIC_APPEARANCE,
                ContextTransferStage.PLANNED,
                "合成预览",
                settingsTargetCount = 1,
                targetAssistantIds = setOf(characterId)
            )
            return CompanionAppearanceImportPreview(id, "合成角色", 1, 1)
        }

        override suspend fun cancelPreview(operationId: String) {
            check(pending.getValue(operationId).stage == ContextTransferStage.PLANNED)
            cancelled += operationId
            pending.remove(operationId)
        }

        override suspend fun resumeOperation(operationId: String): ContextTransferOperationSummary {
            resumes++
            return pending.remove(operationId)!!
                .copy(stage = ContextTransferStage.COMPLETE, completedSettingsTargets = 1)
        }

        override suspend fun listPendingOperations(kind: ContextTransferOperationKind) =
            pending.values.filter { it.kind == kind }

        override suspend fun exportPackage(characterId: String): String = error("此测试不导出装扮")
    }
}
