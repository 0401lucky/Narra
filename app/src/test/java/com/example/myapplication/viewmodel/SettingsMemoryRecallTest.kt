package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.FunctionModelProviderIds
import com.example.myapplication.model.MEMORY_RECALL_TOKEN_BUDGET_MAX
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderFunctionModelMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.testutil.TestAiServices
import com.example.myapplication.testutil.createTestAiServices
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsMemoryRecallTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var apiFactories = 0
    private val chat = ProviderSettings(
        id = "chat",
        name = "合成聊天服务",
        baseUrl = "https://chat.example.test/v1/",
        apiKey = "fixture-chat-key",
        selectedModel = "fixture-chat",
        memoryModel = "fixture-extraction",
    )
    private val embedding = ProviderSettings(
        id = "embedding",
        name = "合成向量服务",
        baseUrl = "https://embedding.example.test/v1/",
        apiKey = "fixture-embedding-key",
        availableModels = listOf("fixture-vector"),
    )
    private val settings = AppSettings(
        providers = listOf(chat, embedding),
        selectedProviderId = chat.id,
        functionModelProviderIds = FunctionModelProviderIds(memoryProviderId = chat.id),
        memoryAutoSummaryEvery = 13,
        memoryCapacity = 2750,
        memoryRecallTokenBudget = 2345,
        memoryExtractionPrompt = "现有提取模板",
        memoryInjectionPrompt = "现有注入模板",
    )

    @After
    fun noModelApiWasRequested() {
        assertEquals(0, apiFactories)
    }

    @Test
    fun embeddingSelection_savesIndependentProviderWithoutChangingChatOrMemorySettings() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val services = services()
            val viewModel = viewModel(services)
            advanceUntilIdle()

            viewModel.updateProviderEmbeddingModel(embedding.id, "  fixture-vector  ")
            assertEquals(embedding.id, viewModel.draft().resolveEmbeddingProvider()?.id)
            assertEquals(
                "fixture-vector",
                viewModel.draft().resolveFunctionModel(ProviderFunction.EMBEDDING)
            )
            assertEquals(chat, viewModel.draft().activeProvider())
            var successes = 0
            viewModel.saveSettings { successes++ }
            advanceUntilIdle()

            val restored = services.settingsStore.settingsFlow.first()
            assertEquals(1, successes)
            assertEquals(embedding.id, restored.resolveEmbeddingProvider()?.id)
            assertEquals(embedding.apiKey, restored.resolveEmbeddingProvider()?.apiKey)
            assertEquals(chat.id, restored.selectedProviderId)
            assertEquals(chat.memoryModel, restored.resolveFunctionModel(ProviderFunction.MEMORY))
            assertEquals(settings.memoryRecallTokenBudget, restored.memoryRecallTokenBudget)
            assertEquals(settings.memoryAutoSummaryEvery, restored.memoryAutoSummaryEvery)
            assertEquals(settings.memoryCapacity, restored.memoryCapacity)
            assertEquals(settings.memoryExtractionPrompt, restored.memoryExtractionPrompt)
            assertEquals(settings.memoryInjectionPrompt, restored.memoryInjectionPrompt)
        }

    @Test
    fun followDefaultAndDisabledModes_bothDeactivateEmbedding() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val services = services()
            val viewModel = viewModel(services)
            advanceUntilIdle()
            for (mode in listOf(
                ProviderFunctionModelMode.FOLLOW_DEFAULT,
                ProviderFunctionModelMode.DISABLED
            )) {
                viewModel.updateProviderEmbeddingModel(embedding.id, "fixture-vector")
                viewModel.updateProviderEmbeddingModelMode(embedding.id, mode)
                assertNull(viewModel.draft().resolveEmbeddingProvider())
                assertEquals("", viewModel.draft().resolveFunctionModel(ProviderFunction.EMBEDDING))
                assertEquals(
                    ProviderFunctionModelMode.DISABLED,
                    viewModel.uiState.value.providers.last().embeddingModelMode
                )
                assertEquals(chat, viewModel.draft().activeProvider())
            }
            viewModel.saveSettings {}
            advanceUntilIdle()
            assertNull(services.settingsStore.settingsFlow.first().resolveEmbeddingProvider())
        }

    @Test
    fun invalidEmbeddingSelection_keepsExistingPurposeAssignments() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val incompatible =
                embedding.copy(id = "anthropic", apiProtocol = ProviderApiProtocol.ANTHROPIC)
            val services = services(settings.copy(providers = settings.providers + incompatible))
            val viewModel = viewModel(services)
            advanceUntilIdle()
            val initial = viewModel.uiState.value
            for ((providerId, model) in listOf(
                "missing" to "fixture-vector",
                embedding.id to " ",
                incompatible.id to "fixture-vector"
            )) {
                viewModel.updateProviderEmbeddingModel(providerId, model)
                assertEquals(initial.providers, viewModel.uiState.value.providers)
                assertEquals(
                    initial.functionModelProviderIds,
                    viewModel.uiState.value.functionModelProviderIds
                )
                assertNull(viewModel.draft().resolveEmbeddingProvider())
                assertTrue(viewModel.uiState.value.message.orEmpty().contains("请选择"))
            }
        }

    @Test
    fun deletedEmbeddingProvider_neverReassignsToChat() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val services = services()
            val viewModel = viewModel(services)
            advanceUntilIdle()
            viewModel.updateProviderEmbeddingModel(embedding.id, "fixture-vector")
            viewModel.deleteProvider(embedding.id)
            assertNull(viewModel.draft().resolveEmbeddingProvider())
            assertEquals(chat.id, viewModel.draft().activeProvider()?.id)
            viewModel.saveSettings {}
            advanceUntilIdle()
            assertNull(services.settingsStore.settingsFlow.first().resolveEmbeddingProvider())
            assertEquals(
                "",
                services.settingsStore.settingsFlow.first().functionModelProviderIds.embeddingProviderId
            )
        }

    @Test
    fun embeddingSaveFailure_keepsDraftAndDoesNotEnablePersistedConfiguration() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val services = services()
            val failingEditor = object : AiSettingsEditor by services.settingsEditor {
                override suspend fun saveProviderSettings(
                    providers: List<ProviderSettings>,
                    selectedProviderId: String
                ) {
                    throw IOException("合成设置写入失败")
                }
            }
            val viewModel = viewModel(services, failingEditor)
            advanceUntilIdle()
            viewModel.updateProviderEmbeddingModel(embedding.id, "fixture-vector")
            var successes = 0
            viewModel.saveSettings { successes++ }
            advanceUntilIdle()
            assertEquals(0, successes)
            assertFalse(viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.hasDraftChanges())
            assertEquals(
                "fixture-vector",
                viewModel.draft().resolveFunctionModel(ProviderFunction.EMBEDDING)
            )
            assertNull(services.settingsStore.settingsFlow.first().resolveEmbeddingProvider())
        }

    @Test
    fun memoryBudgetSave_updatesOnlyBudgetAndClampsAtStorageBoundary() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val services = services()
            val viewModel = viewModel(services)
            advanceUntilIdle()
            viewModel.updateMemoryRecallTokenBudget(Int.MAX_VALUE)
            advanceUntilIdle()
            val persisted = services.settingsStore.settingsFlow.first()
            assertEquals(
                settings.copy(memoryRecallTokenBudget = MEMORY_RECALL_TOKEN_BUDGET_MAX),
                persisted
            )
            assertEquals(
                persisted.memoryRecallTokenBudget,
                viewModel.storedSettings.value.memoryRecallTokenBudget
            )
        }

    @Test
    fun memoryBudgetSaveFailure_preservesValueAndShowsSanitizedMessage() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val services = services()
            val failingEditor = object : AiSettingsEditor by services.settingsEditor {
                override suspend fun saveMemoryRecallTokenBudget(tokenBudget: Int) {
                    throw IOException("Authorization: Bearer sk-fixture-budget-secret")
                }
            }
            val viewModel = viewModel(services, failingEditor)
            advanceUntilIdle()
            viewModel.updateMemoryRecallTokenBudget(4567)
            advanceUntilIdle()
            assertEquals(
                settings.memoryRecallTokenBudget,
                services.settingsStore.settingsFlow.first().memoryRecallTokenBudget
            )
            assertTrue(viewModel.uiState.value.message.orEmpty().isNotBlank())
            assertFalse(
                viewModel.uiState.value.message.orEmpty().contains("sk-fixture-budget-secret")
            )
            viewModel.consumeMessage()
            assertNull(viewModel.uiState.value.message)
        }

    @Test
    fun memoryBudgetCancellation_preservesValueWithoutFailureMessage() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val services = services()
            val cancelledEditor = object : AiSettingsEditor by services.settingsEditor {
                override suspend fun saveMemoryRecallTokenBudget(tokenBudget: Int) {
                    throw CancellationException("合成取消")
                }
            }
            val viewModel = viewModel(services, cancelledEditor)
            advanceUntilIdle()
            viewModel.updateMemoryRecallTokenBudget(4567)
            advanceUntilIdle()
            assertEquals(
                settings.memoryRecallTokenBudget,
                services.settingsStore.settingsFlow.first().memoryRecallTokenBudget
            )
            assertNull(viewModel.uiState.value.message)
        }

    private fun services(initial: AppSettings = settings): TestAiServices = createTestAiServices(
        settings = initial,
        dispatcher = mainDispatcherRule.dispatcher,
        apiServiceProvider = { _, _ ->
            apiFactories++
            error("记忆设置测试不应创建模型 API")
        },
    )

    private fun viewModel(
        services: TestAiServices,
        editor: AiSettingsEditor = services.settingsEditor
    ) = SettingsViewModel(
        settingsRepository = services.settingsRepository,
        settingsEditor = editor,
        modelCatalogRepository = services.modelCatalogRepository,
    )

    private fun SettingsViewModel.draft(): AppSettings = uiState.value.let { state ->
        state.savedSettings.copy(
            providers = state.providers,
            selectedProviderId = state.selectedProviderId,
            functionModelProviderIds = state.functionModelProviderIds,
        )
    }
}
