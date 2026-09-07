package com.example.myapplication.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderType
import com.example.myapplication.ui.theme.ChatAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderSettingsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun protocolHelp_hiddenUntilOpened_largeTextSelectionKeepsProviderCallbacks() {
        var provider by mutableStateOf(ProviderSettings(id = "test-provider", type = ProviderType.CUSTOM))
        var protocolCalls = 0
        var modeCalls = 0
        var otherCalls = 0
        val explanation = "OpenAI 兼容协议适用于大多数兼容网关与中转站。具体请求接口由文本接口模式和请求路径决定。"

        composeRule.setContent {
            ProviderTestTheme(fontScale = 1.6f) {
                ProviderConfig(
                    provider = provider,
                    onProtocolChange = { id, protocol ->
                        assertEquals(provider.id, id)
                        protocolCalls++
                        provider = provider.copy(apiProtocol = protocol)
                    },
                    onModeChange = { id, mode ->
                        assertEquals(provider.id, id)
                        modeCalls++
                        provider = provider.copy(openAiTextApiMode = mode)
                    },
                    onUnexpectedChange = { otherCalls++ },
                )
            }
        }

        composeRule.onNodeWithText(explanation).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("接口协议：查看说明")
            .performScrollTo()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText(explanation).assertIsDisplayed()
        composeRule.onNodeWithText("知道了").performClick()
        composeRule.onNodeWithText(explanation).assertDoesNotExist()

        composeRule.onNodeWithText("Responses API").performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithText("Anthropic").performScrollTo().performClick().assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(1, protocolCalls)
            assertEquals(1, modeCalls)
            assertEquals(0, otherCalls)
            assertEquals(ProviderApiProtocol.ANTHROPIC, provider.apiProtocol)
            assertEquals(OpenAiTextApiMode.RESPONSES, provider.openAiTextApiMode)
        }
    }

    @Test
    fun longModelName_allThemes_keepAbilityLayoutAndIndependentActions() {
        var theme by mutableStateOf(AppColorTheme.MATCHA)
        var dark by mutableStateOf(false)
        var selected by mutableStateOf(false)
        var selectCalls = 0
        var editCalls = 0
        var removeCalls = 0
        var overrideCalls = 0
        var showAbilities by mutableStateOf(false)
        val model = ModelInfo(
            modelId = "long-model",
            displayName = "Claude-Sonnet-Extended-Reasoning-2026-09-05-角色与多模态模型",
            abilities = ModelAbility.entries.toSet(),
            abilitiesCustomized = true,
        )

        composeRule.setContent {
            ProviderTestTheme(theme = theme, dark = dark, fontScale = 1.6f) {
                PremiumModelCard(
                    modelInfo = model,
                    brandColor = MaterialTheme.colorScheme.primary,
                    isSelected = selected,
                    onClick = {
                        selectCalls++
                        selected = true
                    },
                    onEditAbilities = {
                        editCalls++
                        showAbilities = true
                    },
                    onRemove = { removeCalls++ },
                )
                if (showAbilities) {
                    ModelAbilityOverrideDialog(
                        modelInfo = model,
                        onDismissRequest = { showAbilities = false },
                        onSave = { abilities ->
                            assertEquals(null, abilities)
                            overrideCalls++
                            showAbilities = false
                        },
                    )
                }
            }
        }

        AppColorTheme.entries.forEach { colorTheme ->
            listOf(false, true).forEach { isDark ->
                composeRule.runOnIdle {
                    theme = colorTheme
                    dark = isDark
                }
                val nameBounds = composeRule.onNodeWithText(model.displayName, useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val abilityBounds = composeRule.onNodeWithTag("provider_model_abilities_${model.modelId}", useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val editBounds = composeRule.onNodeWithContentDescription("设置模型能力")
                    .assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                    .fetchSemanticsNode().boundsInRoot
                val removeBounds = composeRule.onNodeWithContentDescription("移除模型")
                    .assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                    .fetchSemanticsNode().boundsInRoot
                assertTrue("名称和能力标签不能重叠", nameBounds.bottom <= abilityBounds.top)
                assertTrue("能力标签和操作按钮不能重叠", abilityBounds.bottom <= editBounds.top)
                assertTrue("编辑和移除按钮不能重叠", editBounds.right <= removeBounds.left)
            }
        }

        composeRule.onNodeWithContentDescription("设置模型能力").performClick()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithContentDescription("移除模型").performClick()
        composeRule.runOnIdle {
            assertEquals(0, selectCalls)
            assertEquals(1, editCalls)
            assertEquals(1, removeCalls)
            assertEquals(0, overrideCalls)
        }
        composeRule.onNodeWithText(model.displayName).performClick().assertIsSelected()
        composeRule.onNodeWithText("当前模型", useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, selectCalls)
            assertEquals(1, editCalls)
            assertEquals(1, removeCalls)
            assertEquals(0, overrideCalls)
        }
        composeRule.onNodeWithContentDescription("设置模型能力").performClick()
        composeRule.onNodeWithText("恢复自动").performClick()
        composeRule.runOnIdle { assertEquals(1, overrideCalls) }
    }

    @Test
    fun save_inFlightDisablesRepeatedTap_andEnablesAfterCompletion() {
        var saving by mutableStateOf(false)
        var saveCalls = 0
        composeRule.setContent {
            ProviderTestTheme {
                ProviderConfig(
                    provider = ProviderSettings(id = "test-provider", type = ProviderType.CUSTOM),
                    isSaving = saving,
                    onSave = {
                        saveCalls++
                        saving = true
                    },
                )
            }
        }

        composeRule.onNode(androidx.compose.ui.test.hasScrollAction())
            .performScrollToNode(androidx.compose.ui.test.hasText("保存设置"))
        composeRule.onNodeWithText("保存设置").performClick()
        composeRule.onNodeWithText("保存中...").assertIsNotEnabled().performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(1, saveCalls)
            saving = false
        }
        composeRule.onNodeWithText("保存设置").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(2, saveCalls) }
    }

    @Composable
    private fun ProviderTestTheme(
        theme: AppColorTheme = AppColorTheme.MATCHA,
        dark: Boolean = false,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            ChatAppTheme(appColorTheme = theme, darkTheme = dark) {
                Box(modifier = Modifier.width(320.dp)) { content() }
            }
        }
    }

    @Composable
    private fun ProviderConfig(
        provider: ProviderSettings,
        isSaving: Boolean = false,
        onProtocolChange: (String, ProviderApiProtocol) -> Unit = { _, _ -> },
        onModeChange: (String, OpenAiTextApiMode) -> Unit = { _, _ -> },
        onSave: () -> Unit = {},
        onUnexpectedChange: () -> Unit = {},
    ) {
        ConfigTabContent(
            provider = provider,
            isSaving = isSaving,
            providerCount = 1,
            onUpdateProviderName = { _, _ -> onUnexpectedChange() },
            onUpdateProviderBaseUrl = { _, _ -> onUnexpectedChange() },
            onUpdateProviderApiKey = { _, _ -> onUnexpectedChange() },
            onUpdateProviderApiProtocol = onProtocolChange,
            onUpdateProviderOpenAiTextApiMode = onModeChange,
            onUpdateProviderChatCompletionsPath = { _, _ -> onUnexpectedChange() },
            onToggleProviderEnabled = { onUnexpectedChange() },
            onSave = onSave,
            onDeleteProvider = { onUnexpectedChange() },
            onNavigateBack = onUnexpectedChange,
        )
    }
}
