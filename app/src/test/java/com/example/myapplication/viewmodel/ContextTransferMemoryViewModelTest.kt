package com.example.myapplication.viewmodel

import com.example.myapplication.context.MemoryArchiveIdentity
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
class ContextTransferMemoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun sourceOnlyRequiresExplicitLocalTarget() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val transfer = FakeMemoryArchiveTransferRepository()
        val vm = viewModel(transfer)
        advanceUntilIdle()
        vm.previewImportJson(
            ContextTransferCodec().encode(sourceOnlyBundle()),
            ContextTransferSection.MEMORY
        )
        advanceUntilIdle()
        val preview = requireNotNull(vm.uiState.value.importPreview)
        assertEquals(1, preview.memoryCount)
        assertEquals(1, preview.memorySourceCount)
        assertEquals(1, preview.memoryGroups.size)
        assertFalse(preview.canConfirmMemory)
        assertTrue(preview.memoryTargets.isEmpty())
        vm.confirmImport()
        advanceUntilIdle()
        assertTrue(transfer.importedBundles.isEmpty())
        vm.selectMemoryImportTarget(
            preview.memoryGroups.single().key,
            preview.memoryTargetOptions.single().target
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value.importPreview!!.canConfirmMemory)
        vm.confirmImport()
        advanceUntilIdle()
        assertEquals(1, transfer.importedBundles.size)
        assertEquals(1, vm.uiState.value.memoryCount)
        assertNull(vm.uiState.value.importPreview)
    }

    @Test
    fun memorySectionsExportAndOtherSectionsFilterGraph() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val transfer = FakeMemoryArchiveTransferRepository().apply {
                exportedData = sourceOnlyBundle().memoryArchive!!
            }
            val vm = viewModel(transfer)
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.memoryCount)
            for (section in ContextTransferSection.entries) {
                var exported: ContextDataBundle? = null
                vm.exportBundleJson(section) { raw, _ ->
                    exported = ContextTransferCodec().decode(raw)
                }
                advanceUntilIdle()
                assertEquals(2, exported!!.version)
                if (section in setOf(ContextTransferSection.MEMORY, ContextTransferSection.ALL)) {
                    assertEquals(
                        "仅直接来源的认知",
                        exported!!.memoryArchive!!.residents.single().content
                    )
                } else assertNull(exported!!.memoryArchive)
            }
        }

    @Test
    fun stateReplacementRequiresExplicitChoiceAndKeepsPendingOnFailure() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val transfer = FakeMemoryArchiveTransferRepository().apply {
                conflicts = listOf(
                    MemoryImportStateConflict(
                        "source-state",
                        "space-local",
                        "unknown",
                        MemoryStateKey.MOOD,
                        "local-state",
                        4L,
                        false
                    )
                )
            }
            val vm = viewModel(transfer)
            advanceUntilIdle()
            vm.previewImportJson(
                ContextTransferCodec().encode(sourceOnlyBundle()),
                ContextTransferSection.MEMORY
            )
            advanceUntilIdle()
            val preview = vm.uiState.value.importPreview!!
            vm.selectMemoryImportTarget(
                preview.memoryGroups.single().key,
                preview.memoryTargetOptions.single().target
            )
            advanceUntilIdle()
            assertFalse(vm.uiState.value.importPreview!!.canConfirmMemory)
            vm.confirmImport()
            advanceUntilIdle()
            assertTrue(transfer.importedBundles.isEmpty())
            vm.setReplaceMemoryStates(true)
            transfer.failure = IllegalStateException("合成恢复失败")
            vm.confirmImport()
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.importPreview)
            assertFalse(vm.uiState.value.isBusy)
            assertEquals(1, transfer.plans.size)
            transfer.failure = null
            vm.confirmImport()
            advanceUntilIdle()
            assertEquals(1, transfer.plans.size)
            assertEquals(1, transfer.importedBundles.size)
        }

    @Test
    fun futureVersionCannotFallBackToCharacterImporter() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val transfer = FakeMemoryArchiveTransferRepository()
            val vm = viewModel(transfer)
            advanceUntilIdle()
            val json = ContextTransferCodec().encode(sourceOnlyBundle())
                .replace("\"version\":2", "\"version\":999")
            vm.previewImportJson(json, ContextTransferSection.ALL)
            advanceUntilIdle()
            assertNull(vm.uiState.value.importPreview)
            assertTrue(transfer.importedBundles.isEmpty())
            assertNotNull(vm.uiState.value.message)
        }

    private fun viewModel(transfer: FakeMemoryArchiveTransferRepository): ContextTransferViewModel {
        val services = createTestAiServices(
            settings = AppSettings(assistants = listOf(Assistant(id = "local", name = "本机角色"))),
            dispatcher = mainDispatcherRule.dispatcher,
            apiServiceProvider = { _, _ -> error("资料测试不得联网") },
            imageApiServiceProvider = { _, _ -> error("资料测试不得请求图片") },
            streamClientProvider = { _, _ -> error("资料测试不得请求流") },
        )
        return ContextTransferViewModel(
            services.settingsRepository, services.settingsEditor,
            FakeWorldBookRepository(), FakeMemoryRepository(), FakeConversationSummaryRepository(),
            memoryTransferRepository = transfer
        )
    }

    private fun sourceOnlyBundle(): ContextDataBundle {
        val source = MemorySourceEvidence(
            "source",
            "space",
            "foreign",
            MemorySourceKind.MESSAGE,
            "original-conversation",
            "original-message",
            1L,
            "user",
            "USER",
            "unknown",
            listOf("foreign"),
            "仅直接来源的认知",
            8,
            MemorySourceStatus.AVAILABLE,
            1L,
            1L,
            MemoryArchiveIdentity.fingerprint("仅直接来源的认知")
        )
        val resident = MemoryResidentEntry(
            "resident", "space", "foreign", "unknown", MemoryResidentCategory.USER,
            source.content, 1L, "READY", false, false, null, 1L, 1L
        )
        return ContextDataBundle(
            version = 2, exportedAt = 1L, memoryArchive = MemoryArchiveTransferData(
                spaces = listOf(
                    MemorySpace(
                        "space",
                        "foreign",
                        MemoryPurpose.CONTINUOUS,
                        createdAt = 1L,
                        updatedAt = 1L
                    )
                ),
                sources = listOf(source), residents = listOf(resident),
                knowledgeRevisions = listOf(
                    MemoryKnowledgeRevision(
                        MemoryMaterialKind.RESIDENT,
                        "resident",
                        1L,
                        source.content,
                        "READY",
                        MemoryChangeOrigin.EXTRACTION,
                        false,
                        null,
                        null,
                        listOf(source.fullRef()),
                        emptyList(),
                        1L
                    )
                ),
            )
        )
    }
}
