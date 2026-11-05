package com.example.myapplication.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.ConnectionHealth
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderType
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 真机待执行的整页回归；本源集只使用本地合成状态，不读取 JVM Fake 或用户配置。 */
@RunWith(AndroidJUnit4::class)
class ProviderSettingsFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun list_200percentTemplateWaitsForDraft_andCancelDoesNotCreateProvider() {
        var state by mutableStateOf(fixtureState())
        val opened = mutableListOf<String>()
        var added = 0
        composeRule.setContent {
            ProviderFlowTheme {
                ProviderSettingsScreen(
                    uiState = state,
                    onEnsureProviderDrafts = { error("固定草稿不应重新初始化") },
                    onShowAddDialog = { state = state.copy(showTemplateDialog = true) },
                    onDismissAddDialog = { state = state.copy(showTemplateDialog = false) },
                    onAddProviderFromTemplate = {
                        added++
                        state = state.copy(showTemplateDialog = false)
                        "created-fixture"
                    },
                    onOpenProviderDetail = { opened += it },
                    onCheckProviderHealth = { error("固定健康结果不应触发检测") },
                    onConsumeMessage = {},
                    onNavigateBack = {},
                )
            }
        }
        assertTarget(composeRule.onNode(hasSetTextAction()))
        composeRule.onNodeWithText("新增").performTouchInput { click() }
        composeRule.onNodeWithText("取消").performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(0, added); assertTrue(opened.isEmpty()) }
        composeRule.onNodeWithText("新增").performTouchInput { click() }
        composeRule.onNode(hasText("OpenAI") and hasClickAction()).performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(1, added)
            assertTrue(opened.isEmpty())
            state = state.copy(
                providers = state.providers + provider().copy(id = "created-fixture"),
                connectionHealthMap = state.connectionHealthMap + ("created-fixture" to ConnectionHealth.HEALTHY),
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(listOf("created-fixture"), opened) }
    }

    @Test
    fun detail_200percentSaveAreaAndTabs_haveIndependentRealTouchBounds() {
        var state by mutableStateOf(fixtureState())
        var saves = 0
        composeRule.setContent {
            ProviderFlowTheme {
                ProviderDetailScreen(
                    providerId = "device-provider-fixture",
                    uiState = state,
                    onUpdateProviderName = { _, _ -> },
                    onUpdateProviderBaseUrl = { _, _ -> },
                    onUpdateProviderApiKey = { _, _ -> },
                    onUpdateProviderApiProtocol = { _, _ -> },
                    onUpdateProviderOpenAiTextApiMode = { _, _ -> },
                    onUpdateProviderChatCompletionsPath = { _, _ -> },
                    onUpdateProviderSelectedModel = { _, _ -> },
                    onUpdateProviderModelAbilities = { _, _, _ -> },
                    onLoadModels = {},
                    onDeleteProvider = {},
                    onToggleProviderEnabled = {},
                    onSave = { saves++; state = state.copy(isSaving = true) },
                    onConsumeMessage = {},
                    onNavigateBack = {},
                    onConfirmFetchedModels = { _, _ -> },
                    onDismissFetchedModels = {},
                    onRemoveModel = { _, _ -> },
                    onClearAllModels = {},
                    onAddManualModel = { _, _, _ -> },
                )
            }
        }
        val list = composeRule.onNode(hasScrollToNodeAction())
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        composeRule.waitForIdle()
        val range = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(range.maxValue(), range.value(), 1f)
        val save = composeRule.onNodeWithText("保存设置")
        val config = composeRule.onNode(hasText("配置") and hasClickAction())
        val models = composeRule.onNode(hasText("模型") and hasClickAction())
        listOf(save, config, models).forEach(::assertTarget)
        config.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertIsSelected()
        assertTrue(save.getUnclippedBoundsInRoot().bottom <= config.getUnclippedBoundsInRoot().top)
        save.performTouchInput { click() }
        composeRule.onNodeWithText("保存中...").assertIsNotEnabled().performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(1, saves) }
        models.performTouchInput { click() }.assertIsSelected()
        composeRule.onNodeWithText("暂无模型").assertIsDisplayed()
    }

    private fun assertTarget(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed()
        val raw = node.getUnclippedBoundsInRoot()
        val touch = node.fetchSemanticsNode().touchBoundsInRoot
        val root =
            composeRule.onNodeWithTag("provider_flow_stage").fetchSemanticsNode().boundsInRoot
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        val rawRoot = composeRule.onNodeWithTag("provider_flow_stage").getUnclippedBoundsInRoot()
        assertTrue(raw.right - raw.left >= 48.dp && raw.bottom - raw.top >= 48.dp)
        assertTrue(raw.left >= rawRoot.left && raw.top >= rawRoot.top && raw.right <= rawRoot.right && raw.bottom <= rawRoot.bottom)
        assertTrue(touch.width >= minimum && touch.height >= minimum)
        assertTrue(touch.left >= root.left && touch.top >= root.top && touch.right <= root.right && touch.bottom <= root.bottom)
    }

    @Composable
    private fun ProviderFlowTheme(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
            ChatAppTheme(dynamicColor = false) {
                Box(
                    Modifier.requiredSize(320.dp, 640.dp).testTag("provider_flow_stage")
                ) { content() }
            }
        }
    }

    private fun provider() = ProviderSettings(
        id = "device-provider-fixture", name = "合成提供商长名称",
        type = ProviderType.CUSTOM, baseUrl = "https://fixture.invalid/", apiKey = "synthetic-only"
    )

    private fun fixtureState() = SettingsUiState(
        providers = listOf(provider()), selectedProviderId = provider().id,
        connectionHealthMap = mapOf(provider().id to ConnectionHealth.HEALTHY)
    )
}
