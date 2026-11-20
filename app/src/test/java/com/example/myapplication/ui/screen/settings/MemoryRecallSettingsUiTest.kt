package com.example.myapplication.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.MemoryInjectionPosition
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.testutil.TestAiServices
import com.example.myapplication.testutil.createTestAiServices
import com.example.myapplication.ui.screen.settings.memory.MemoryManagementScreen
import com.example.myapplication.ui.screenshot.ProviderSettingsRenderTest
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.viewmodel.MemoryManagementUiState
import com.example.myapplication.viewmodel.SettingsViewModel
import com.example.myapplication.viewmodel.updateProviderEmbeddingModel
import com.example.myapplication.viewmodel.updateProviderEmbeddingModelMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 复用固定 Compose 宿主与真实触控边界检查；不生成或接纳截图。 */
class MemoryRecallSettingsUiTest : ProviderSettingsRenderTest() {
    private var apiFactories = 0
    private val chat = ProviderSettings(
        id = "chat",
        name = "聊天服务",
        baseUrl = "https://chat.example.test/v1/",
        apiKey = "fixture-chat-key",
        selectedModel = "fixture-chat",
    )
    private val embedding = ProviderSettings(
        id = "embedding",
        name = "向量服务",
        baseUrl = "https://embedding.example.test/v1/",
        apiKey = "fixture-embedding-key",
        availableModels = listOf("fixture-vector"),
    )
    private val settings = AppSettings(
        providers = listOf(chat, embedding),
        selectedProviderId = chat.id,
        memoryAutoSummaryEvery = 7,
        memoryCapacity = 1350,
    )

    @After
    fun noModelApiWasRequested() {
        assertEquals(0, apiFactories)
    }

    @Test
    fun embeddingPicker_narrowLargeFontCanSelectAndDisableWithFullTouchTargets() {
        val services = services()
        val viewModel = viewModel(services)
        drainMainLooper()
        renderProvider(content = { ModelSettingsContent(viewModel) })
        reveal(hasTestTag("embedding_model_card"))
        val disclosure = pageControl(hasTestTag("embedding_model_card"))
        assertTouchTarget(disclosure, pageScroll())
        disclosure.performTouchInput { click() }

        val title = "选择向量模型"
        windowRoot(title)
        windowControl(title, hasText("向量服务") and hasClickAction()).performTouchInput { click() }
        val modelList = windowControl(
            title,
            hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        )
        modelList.performScrollToNode(hasText("fixture-vector") and hasClickAction())
        val model =
            windowControl(title, hasText("fixture-vector") and hasClickAction()).performScrollTo()
        assertTouchTarget(model, modelList)
        composeRule.onNodeWithText("跟随默认模型").assertDoesNotExist()
        model.performTouchInput { click() }
        drainMainLooper()
        composeRule.runOnIdle {
            assertEquals(
                embedding.id,
                viewModel.uiState.value.functionModelProviderIds.embeddingProviderId
            )
            assertEquals("fixture-vector", viewModel.uiState.value.providers.last().embeddingModel)
            assertEquals(chat, viewModel.uiState.value.providers.first())
        }

        reveal(hasTestTag("embedding_model_card")).performTouchInput { click() }
        val disable = windowControl(title, hasText("关闭向量检索") and hasClickAction())
        assertTouchTarget(disable, windowRoot(title))
        disable.performTouchInput { click() }
        drainMainLooper()
        composeRule.runOnIdle {
            assertEquals(
                "",
                viewModel.uiState.value.providers.last()
                    .resolveFunctionModel(ProviderFunction.EMBEDDING)
            )
            assertEquals(chat, viewModel.uiState.value.providers.first())
        }
    }

    @Test
    fun embeddingPicker_withoutCompatibleProviderExplainsLocalFallbackAndDismisses() {
        val services = services(
            settings.copy(
                providers = listOf(embedding.copy(apiProtocol = ProviderApiProtocol.ANTHROPIC)),
                selectedProviderId = embedding.id,
            )
        )
        val viewModel = viewModel(services)
        drainMainLooper()
        renderProvider(
            case = ScreenshotCases.require("E11_config-light"),
            content = { ModelSettingsContent(viewModel) })
        reveal(hasTestTag("embedding_model_card")).performTouchInput { click() }
        val title = "尚无可用的向量服务"
        windowControl(
            title,
            hasText("请先添加并启用 OpenAI 兼容提供商，再为它添加向量模型。未启用时仍使用本地检索。")
        )
            .assertIsDisplayed()
        val dismiss = windowControl(title, hasText("知道了") and hasClickAction())
        assertTouchTarget(dismiss, windowRoot(title))
        dismiss.performTouchInput { click() }
        composeRule.onNodeWithText(title).assertDoesNotExist()
        assertFalse(viewModel.uiState.value.hasDraftChanges())
    }

    @Test
    fun memoryBudget_narrowLargeFontValidatesAndSavesThroughViewModel() {
        val services = services()
        val viewModel = viewModel(services)
        var budgetChanges = 0
        drainMainLooper()
        renderProvider(content = {
            MemorySettingsContent(viewModel) { budgetChanges++ }
        })
        val field = reveal(hasTestTag("memory_recall_token_budget"))
        field.performTextReplacement("0")
        reveal(hasTestTag("memory_recall_budget_save")).assertIsNotEnabled()
        field.performScrollTo().performTextReplacement("16001")
        reveal(hasTestTag("memory_recall_budget_save")).assertIsNotEnabled()
        field.performScrollTo().performTextReplacement("3456")
        val save = reveal(hasTestTag("memory_recall_budget_save")).assertIsEnabled()
        assertTouchTarget(save, pageScroll())
        save.performTouchInput { click() }
        drainMainLooper()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, budgetChanges)
            assertEquals(3456, viewModel.storedSettings.value.memoryRecallTokenBudget)
            assertNull(viewModel.uiState.value.message)
        }
        reveal(hasTestTag("memory_recall_budget_save")).assertIsNotEnabled()
        val persisted = runBlocking { services.settingsStore.settingsFlow.first() }
        assertEquals(settings.copy(memoryRecallTokenBudget = 3456), persisted)
    }

    @Composable
    private fun ModelSettingsContent(viewModel: SettingsViewModel) {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SettingsModelScreen(
            uiState = state,
            onLoadModels = { error("不应拉取模型") },
            onSelectProvider = { error("不应切换聊天提供商") },
            onSelectedModelChange = { _, _ -> error("不应切换聊天模型") },
            onUpdateFunctionModel = { function, providerId, model ->
                assertEquals(ProviderFunction.EMBEDDING, function)
                viewModel.updateProviderEmbeddingModel(providerId, model)
            },
            onUpdateFunctionModelMode = { function, providerId, mode ->
                assertEquals(ProviderFunction.EMBEDDING, function)
                viewModel.updateProviderEmbeddingModelMode(providerId, mode)
            },
            onConsumeMessage = viewModel::consumeMessage,
            onNavigateBack = {},
        )
    }

    @Composable
    private fun MemorySettingsContent(viewModel: SettingsViewModel, onBudgetChange: () -> Unit) {
        val stored by viewModel.storedSettings.collectAsStateWithLifecycle()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        MemoryManagementScreen(
            uiState = MemoryManagementUiState(message = state.message),
            assistants = emptyList(),
            memoryAutoSummaryEvery = stored.memoryAutoSummaryEvery,
            memoryCapacity = stored.memoryCapacity,
            memoryRecallTokenBudget = stored.memoryRecallTokenBudget,
            memoryExtractionPrompt = stored.memoryExtractionPrompt,
            memoryInjectionPrompt = stored.memoryInjectionPrompt,
            memoryInjectionPosition = MemoryInjectionPosition.AFTER_WORLD_BOOK,
            onUpdateMemoryAutoSummaryEvery = { error("不应改变提取频率") },
            onUpdateMemoryCapacity = { error("不应改变活动池") },
            onUpdateMemoryRecallTokenBudget = {
                onBudgetChange()
                viewModel.updateMemoryRecallTokenBudget(it)
            },
            onUpdateMemoryExtractionPrompt = { error("不应改变模板") },
            onUpdateMemoryInjectionPrompt = { error("不应改变模板") },
            onUpdateMemoryInjectionPosition = { error("不应改变注入位置") },
            onTogglePinned = { error("不应编辑记忆") },
            onDeleteMemory = { error("不应删除记忆") },
            onDeleteSummary = { error("不应删除摘要") },
            onConsumeMessage = viewModel::consumeMessage,
            onNavigateBack = {},
        )
    }

    private fun services(initial: AppSettings = settings): TestAiServices = createTestAiServices(
        settings = initial,
        apiServiceProvider = { _, _ ->
            apiFactories++
            error("设置交互测试不应创建模型 API")
        },
    )

    private fun viewModel(services: TestAiServices) = SettingsViewModel(
        settingsRepository = services.settingsRepository,
        settingsEditor = services.settingsEditor,
        modelCatalogRepository = services.modelCatalogRepository,
    )
}
