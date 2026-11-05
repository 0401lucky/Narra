package com.example.myapplication.ui.screenshot

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import com.example.myapplication.R
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.ui.screen.settings.SettingsScreen
import com.example.myapplication.ui.theme.resolveAppColorScheme
import com.example.myapplication.viewmodel.SettingsUiState
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment

/** 真实设置首页与独立 Sheet 的固定数据宿主；只注入状态和回调，不访问用户存储或 API。 */
abstract class SettingsHomeRenderTest : ScreenshotStageTest() {
    protected val events = mutableListOf<String>()
    protected val state = mutableStateOf(fixtureState())

    protected fun renderHome(
        case: ScreenshotCase = ScreenshotCases.require("E10_home-narrow-light"),
        theme: AppColorTheme = AppColorTheme.MATCHA,
        dark: Boolean = false,
        dynamic: Boolean = false,
        colorSchemeState: State<ColorScheme>? = null,
        content: (@Composable () -> Unit)? = null,
    ) {
        if (case.fontScale == ScreenshotFixture.NarrowFontScale) {
            RuntimeEnvironment.setQualifiers(ScreenshotFixture.NarrowQualifiers)
            useNarrowFontScale()
        }
        val home: @Composable () -> Unit = content ?: { HomeContent() }
        when {
            colorSchemeState != null -> setColorSchemeStateStage(case, colorSchemeState, home)
            dynamic -> setColorSchemeStage(case, resolveAppColorScheme(theme, dark, lightColorScheme()), home)
            else -> setStage(case, theme, dark, home)
        }
        composeRule.waitForIdle()
    }

    @Composable
    protected fun HomeContent(
        uiState: SettingsUiState = state.value,
        onSave: () -> Unit = { events += "save" },
        onConsumeMessage: () -> Unit = {
            events += "consume"
            state.value = state.value.copy(message = null)
        },
        onNavigateBack: () -> Unit = { events += "back" },
        onThemeModeChange: (ThemeMode) -> Unit = { state.value = state.value.copy(themeMode = it); events += "mode:$it" },
    ) {
        SettingsScreen(
            uiState = uiState,
            onSave = onSave,
            onConsumeMessage = onConsumeMessage,
            onOpenRoleplay = { events += "roleplay" },
            onOpenProviderSettings = { events += "providers" },
            onOpenVoiceSynthesisSettings = { events += "voice-synthesis" },
            onOpenVoiceRecognitionSettings = { events += "voice-recognition" },
            onOpenUpdateSettings = { events += "updates" },
            onOpenComplianceSettings = { events += "compliance" },
            onOpenDiagnostics = { events += "diagnostics" },
            onOpenUserMasks = { events += "masks" },
            onOpenModelSettings = { events += "models" },
            onOpenAssistantSettings = { events += "assistants" },
            onOpenWorldBookSettings = { events += "worldbooks" },
            onOpenMemorySettings = { events += "memory" },
            onOpenContextTransferSettings = { events += "transfer" },
            onOpenRoleplayScripts = { events += "scripts" },
            onOpenHome = { events += "home" },
            onNavigateBack = onNavigateBack,
            onUpdateThemeMode = onThemeModeChange,
            onUpdateAppColorTheme = { state.value = state.value.copy(appColorTheme = it); events += "theme:$it" },
            onUpdateMessageTextScale = { state.value = state.value.copy(messageTextScale = it); events += "scale:$it" },
            onUpdateReasoningExpandedByDefault = { state.value = state.value.copy(reasoningExpandedByDefault = it); events += "reasoning:$it" },
            onUpdateShowThinkingContent = { state.value = state.value.copy(showThinkingContent = it); events += "thinking:$it" },
            onUpdateAutoCollapseThinking = { state.value = state.value.copy(autoCollapseThinking = it); events += "collapse-thinking:$it" },
            onUpdateAutoPreviewImages = { state.value = state.value.copy(autoPreviewImages = it); events += "preview:$it" },
            onUpdateCodeBlockAutoWrap = { state.value = state.value.copy(codeBlockAutoWrap = it); events += "wrap:$it" },
            onUpdateCodeBlockAutoCollapse = { state.value = state.value.copy(codeBlockAutoCollapse = it); events += "collapse-code:$it" },
        )
    }

    protected fun openHomeRow(label: String) {
        homeRow(label).performClick()
        composeRule.waitForIdle()
    }

    protected fun homeRow(label: String): SemanticsNodeInteraction {
        composeRule.onNode(hasScrollToNodeAction() and hasAnyAncestor(hasTestTag(StageTag)))
            .performScrollToNode(hasText(label))
        return composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(StageTag)))
            .performScrollTo().assertIsDisplayed()
    }

    protected fun openSheet(label: String, title: String) {
        openHomeRow(label)
        val expand = SemanticsMatcher.keyIsDefined(SemanticsActions.Expand) and hasAnyAncestor(windowMatcher(title))
        if (composeRule.onAllNodes(expand, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNode(expand, useUnmergedTree = true).performSemanticsAction(SemanticsActions.Expand) { it() }
            composeRule.waitForIdle()
        }
        windowRoot(title).assertIsDisplayed()
    }

    protected fun windowMatcher(title: String): SemanticsMatcher =
        isRoot() and hasAnyDescendant(hasText(title)) and
            hasAnyDescendant(isDialog()) and !hasAnyDescendant(hasTestTag(StageTag))

    protected fun windowRoot(title: String): SemanticsNodeInteraction = composeRule.onNode(
        windowMatcher(title), useUnmergedTree = true,
    ).assertExists("必须打开标题为 $title 的真实独立窗口，不能捕获底层设置首页")

    protected fun inWindow(title: String, matcher: SemanticsMatcher): SemanticsNodeInteraction =
        composeRule.onNode(matcher and hasAnyAncestor(windowMatcher(title)), useUnmergedTree = true)

    protected fun windowControl(title: String, matcher: SemanticsMatcher): SemanticsNodeInteraction =
        composeRule.onNode(matcher and hasAnyAncestor(windowMatcher(title)))

    protected fun dismissSheet(title: String) {
        inWindow(title, SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.Dismiss) { it() }
        composeRule.waitForIdle()
        composeRule.onNode(windowMatcher(title), useUnmergedTree = true).assertDoesNotExist()
    }

    protected fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    protected fun assertTextFits(node: SemanticsNodeInteraction, text: String) {
        val layout = textLayout(node)
        assertFalse("文字不能纵向裁切：$text", layout.didOverflowHeight)
        assertEquals("文字末尾必须完整：$text", text.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        repeat(layout.lineCount) { line ->
            assertFalse("文字不能省略：$text", layout.isLineEllipsized(line))
            assertTrue("文字必须位于可见布局内：$text", layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f)
        }
    }

    protected fun assertTouchTarget(node: SemanticsNodeInteraction, container: SemanticsNodeInteraction = composeRule.onNodeWithTag(StageTag)) {
        node.assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().touchBoundsInRoot
        val limit = container.fetchSemanticsNode().boundsInRoot
        val minimum = 48 * ScreenshotFixture.HostDensity
        assertTrue("真实点击宽度不足48dp：$bounds", bounds.width >= minimum - 0.5f)
        assertTrue("真实点击高度不足48dp：$bounds", bounds.height >= minimum - 0.5f)
        assertTrue("点击范围必须完整可达：$bounds / $limit",
            bounds.left >= limit.left - 0.5f && bounds.top >= limit.top - 0.5f &&
                bounds.right <= limit.right + 0.5f && bounds.bottom <= limit.bottom + 0.5f)
    }

    protected fun captureHome(case: ScreenshotCase) {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_title)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_default_model_prompt)).assertIsDisplayed()
        writeSemantics(case, composeRule.onNodeWithTag(StageTag))
        captureCase(case)
    }

    protected fun captureWindow(case: ScreenshotCase, title: String, operation: String) {
        assertEquals(case.testClass, javaClass.simpleName)
        val root = windowRoot(title).assertIsDisplayed()
        inWindow(title, hasText(operation)).assertIsDisplayed()
        val bounds = root.fetchSemanticsNode().boundsInRoot
        assertTrue("独立窗口必须有有效尺寸", bounds.width > 0 && bounds.height > 0)
        assertTrue("窗口不能超过固定宿主宽度", bounds.width <= case.stageWidthDp * ScreenshotFixture.HostDensity)
        assertTrue("窗口不能超过固定宿主高度", bounds.height <= case.stageHeightDp * ScreenshotFixture.HostDensity)
        val layouts = mutableListOf<TextLayoutResult>()
        inWindow(title, hasText(operation)).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("窗口必须绘制实际文字", layouts.isNotEmpty())
        assertEquals("窗口文字继承登记字体", case.fontScale, layouts.single().layoutInput.density.fontScale, 0.001f)
        assertEquals("窗口文字继承固定density", ScreenshotFixture.HostDensity, layouts.single().layoutInput.density.density, 0.001f)
        File("build/outputs/roborazzi/${case.id}-window.txt").apply { parentFile?.mkdirs() }
            .writeText("title=$title\noperation=$operation\nfontScale=${case.fontScale}\nbounds=$bounds\n" + root.printToString())
        writeSemantics(case, root)
        root.captureRoboImage(case.relativePath, StrictRoborazziOptions)
    }

    private fun writeSemantics(case: ScreenshotCase, root: SemanticsNodeInteraction) {
        File("build/outputs/roborazzi/${case.id}-semantics.xml").apply { parentFile?.mkdirs() }
            .writeText("<semantics id=\"${case.id}\" fontScale=\"${case.fontScale}\"><![CDATA[${root.printToString()}]]></semantics>")
    }

    companion object {
        const val LongModelName = "fixture-model-长上下文与细腻情绪完整模型名称"
        const val LongProviderName = "合成提供商：长篇叙事与多语言服务的完整名称"

        fun fixtureState(theme: AppColorTheme = AppColorTheme.MATCHA): SettingsUiState {
            val provider = ProviderSettings(
                id = "settings-fixture-provider",
                name = LongProviderName,
                baseUrl = "https://fixture.invalid",
                apiKey = "fixture-not-a-real-credential",
                selectedModel = LongModelName,
                availableModels = listOf(LongModelName, "fixture-short-model"),
            )
            val saved = AppSettings(providers = listOf(provider), selectedProviderId = provider.id, appColorTheme = theme)
            return SettingsUiState(
                providers = saved.providers,
                selectedProviderId = saved.selectedProviderId,
                savedSettings = saved,
                appColorTheme = theme,
                messageTextScale = 1.15f,
            )
        }
    }
}
