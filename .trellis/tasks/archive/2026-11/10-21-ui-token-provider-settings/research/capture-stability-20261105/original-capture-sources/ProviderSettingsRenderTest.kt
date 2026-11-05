package com.example.myapplication.ui.screenshot

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import com.example.myapplication.model.*
import com.example.myapplication.ui.screen.settings.ProviderDetailScreen
import com.example.myapplication.ui.screen.settings.ProviderSettingsScreen
import com.example.myapplication.ui.theme.resolveAppColorScheme
import com.example.myapplication.viewmodel.SettingsUiState
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment

/** 真实提供商页面宿主；所有网络入口都是本地计数回调，不装配真实 API 或用户存储。 */
abstract class ProviderSettingsRenderTest : ScreenshotStageTest() {
    protected val state = mutableStateOf(fixtureState())
    protected val activeProviderId = mutableStateOf(ProviderId)
    protected val events = mutableListOf<String>()
    protected val healthRequests = mutableListOf<String>()
    private val captureMeasurements = linkedMapOf<String, String>()

    protected fun renderProvider(
        case: ScreenshotCase = ScreenshotCases.require("E11_config-save-narrow-light"),
        list: Boolean = false,
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
        val page: @Composable () -> Unit = content ?: {
            if (list) ListContent() else DetailContent()
        }
        when {
            colorSchemeState != null -> setColorSchemeStateStage(case, colorSchemeState, page)
            dynamic -> setColorSchemeStage(
                case,
                resolveAppColorScheme(theme, dark, lightColorScheme()),
                page
            )

            else -> setStage(case, theme, dark, page)
        }
        composeRule.waitForIdle()
    }

    @Composable
    protected fun ListContent(
        uiState: SettingsUiState = state.value,
        onAdd: (ProviderTemplate) -> String = { template ->
            events += "template:${template.type}"
            state.value = state.value.copy(showTemplateDialog = false)
            CreatedProviderId
        },
        onOpen: (String) -> Unit = { events += "open:$it" },
    ) {
        ProviderSettingsScreen(
            uiState = uiState,
            onEnsureProviderDrafts = { events += "ensure" },
            onShowAddDialog = {
                events += "show-template"; state.value = state.value.copy(showTemplateDialog = true)
            },
            onDismissAddDialog = {
                events += "dismiss-template"; state.value =
                state.value.copy(showTemplateDialog = false)
            },
            onAddProviderFromTemplate = onAdd,
            onOpenProviderDetail = onOpen,
            onCheckProviderHealth = { healthRequests += it },
            onConsumeMessage = {
                events += "consume"; state.value = state.value.copy(message = null)
            },
            onNavigateBack = { events += "back" },
        )
    }

    @Composable
    protected fun DetailContent(
        uiState: SettingsUiState = state.value,
        onSave: () -> Unit = { events += "save" },
        onConsumeMessage: () -> Unit = {
            events += "consume"; state.value = state.value.copy(message = null)
        },
        onNameChange: (String, String) -> Unit = { id, value ->
            updateProvider(id) { it.copy(name = value) }; events += "name:$id:$value"
        },
    ) {
        ProviderDetailScreen(
            providerId = activeProviderId.value,
            uiState = uiState,
            onUpdateProviderName = onNameChange,
            onUpdateProviderBaseUrl = { id, value -> updateProvider(id) { it.copy(baseUrl = value) }; events += "url:$id:$value" },
            onUpdateProviderApiKey = { id, value -> updateProvider(id) { it.copy(apiKey = value) }; events += "key:$id" },
            onUpdateProviderApiProtocol = { id, value -> updateProvider(id) { it.copy(apiProtocol = value) }; events += "protocol:$id:$value" },
            onUpdateProviderOpenAiTextApiMode = { id, value ->
                updateProvider(id) {
                    it.copy(
                        openAiTextApiMode = value
                    )
                }; events += "mode:$id:$value"
            },
            onUpdateProviderChatCompletionsPath = { id, value ->
                updateProvider(id) {
                    it.copy(
                        chatCompletionsPath = value
                    )
                }; events += "path:$id:$value"
            },
            onUpdateProviderSelectedModel = { id, model ->
                updateProvider(id) {
                    it.copy(
                        selectedModel = model
                    )
                }; events += "select:$id:$model"
            },
            onUpdateProviderModelAbilities = { id, model, abilities -> events += "abilities:$id:$model:$abilities" },
            onLoadModels = { id ->
                events += "fetch:$id"
                state.value = state.value.copy(
                    pendingFetchProviderId = id,
                    pendingFetchedModels = fetchedModels()
                )
            },
            onDeleteProvider = { events += "delete:$it" },
            onToggleProviderEnabled = { id -> updateProvider(id) { it.copy(enabled = !it.enabled) }; events += "toggle:$id" },
            onSave = onSave,
            onConsumeMessage = onConsumeMessage,
            onNavigateBack = { events += "back" },
            onConfirmFetchedModels = { id, models -> events += "confirm:$id:${models.sorted()}" },
            onDismissFetchedModels = {
                events += "dismiss-fetch"
                state.value = state.value.copy(
                    pendingFetchProviderId = "",
                    pendingFetchedModels = emptyList()
                )
            },
            onRemoveModel = { id, model -> events += "remove:$id:$model" },
            onClearAllModels = { events += "clear:$it" },
            onAddManualModel = { id, model, name -> events += "manual:$id:$model:$name" },
        )
    }

    protected fun updateProvider(id: String, update: (ProviderSettings) -> ProviderSettings) {
        state.value =
            state.value.copy(providers = state.value.providers.map { if (it.id == id) update(it) else it })
    }

    protected fun pageControl(matcher: SemanticsMatcher): SemanticsNodeInteraction =
        composeRule.onNode(matcher and hasAnyAncestor(hasTestTag(StageTag)))

    protected fun pageScroll(): SemanticsNodeInteraction = pageControl(hasScrollToNodeAction())

    protected fun reveal(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        pageScroll().performScrollToNode(matcher)
        return pageControl(matcher).performScrollTo().assertIsDisplayed()
    }

    protected fun selectModels() {
        pageControl(hasText("模型") and hasClickAction()).performClick()
        composeRule.waitForIdle()
    }

    protected fun windowMatcher(title: String): SemanticsMatcher =
        isRoot() and hasAnyDescendant(hasText(title)) and hasAnyDescendant(isDialog()) and
                !hasAnyDescendant(hasTestTag(StageTag))

    protected fun windowRoot(title: String): SemanticsNodeInteraction =
        composeRule.onNode(windowMatcher(title), useUnmergedTree = true)
            .assertExists("必须真实打开 $title 独立窗口，缺失时不能拍底页")

    protected fun inWindow(title: String, matcher: SemanticsMatcher): SemanticsNodeInteraction =
        composeRule.onNode(matcher and hasAnyAncestor(windowMatcher(title)), useUnmergedTree = true)

    protected fun windowControl(
        title: String,
        matcher: SemanticsMatcher
    ): SemanticsNodeInteraction =
        composeRule.onNode(matcher and hasAnyAncestor(windowMatcher(title)))

    protected fun scrollToEnd(
        scroll: SemanticsNodeInteraction = pageScroll(),
        evidenceName: String = "page"
    ) {
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        composeRule.waitForIdle()
        val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("必须达到真实滚动末尾", range.maxValue(), range.value(), 1f)
        captureMeasurements["${evidenceName}Scroll"] = "${range.value()}/${range.maxValue()}"
        captureMeasurements["${evidenceName}Viewport"] =
            scroll.fetchSemanticsNode().boundsInRoot.toString()
    }

    protected fun assertFullyVisible(
        node: SemanticsNodeInteraction,
        container: SemanticsNodeInteraction = composeRule.onNodeWithTag(StageTag),
        evidenceName: String = "operation",
    ) {
        node.assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().touchBoundsInRoot
        val unclipped = unclippedBounds(node)
        val limit = container.fetchSemanticsNode().boundsInRoot
        assertContains(limit, bounds)
        assertContains(limit, unclipped)
        captureMeasurements[evidenceName] = bounds.toString()
        captureMeasurements["${evidenceName}Unclipped"] = unclipped.toString()
    }

    protected fun assertTouchTarget(
        node: SemanticsNodeInteraction,
        container: SemanticsNodeInteraction = composeRule.onNodeWithTag(StageTag),
    ) {
        assertFullyVisible(node, container)
        val bounds = node.fetchSemanticsNode().touchBoundsInRoot
        val minimum = 48f * ScreenshotFixture.HostDensity
        assertTrue("真实触控宽不足48dp：$bounds", bounds.width >= minimum - 0.5f)
        assertTrue("真实触控高不足48dp：$bounds", bounds.height >= minimum - 0.5f)
    }

    protected fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    protected fun unclippedBounds(node: SemanticsNodeInteraction): Rect {
        val bounds = node.getUnclippedBoundsInRoot()
        val density = ScreenshotFixture.HostDensity
        return Rect(
            bounds.left.value * density, bounds.top.value * density,
            bounds.right.value * density, bounds.bottom.value * density
        )
    }

    protected fun recordBounds(name: String, node: SemanticsNodeInteraction) {
        captureMeasurements[name] = node.fetchSemanticsNode().touchBoundsInRoot.toString()
        captureMeasurements["${name}Unclipped"] = unclippedBounds(node).toString()
    }

    protected fun assertAbove(node: SemanticsNodeInteraction, obstacle: SemanticsNodeInteraction) {
        assertTrue(
            "末项完整操作不能被浮动操作或底栏遮挡",
            unclippedBounds(node).bottom <= unclippedBounds(obstacle).top + 0.5f
        )
    }

    protected fun assertTextFits(node: SemanticsNodeInteraction) {
        val layout = textLayout(node)
        assertFalse("文字不能纵向裁切", layout.didOverflowHeight)
        assertEquals(
            layout.layoutInput.text.length,
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
        )
        repeat(layout.lineCount) { line ->
            assertFalse("文字不能省略", layout.isLineEllipsized(line))
            assertTrue(
                "文字超出布局宽度",
                layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f
            )
        }
    }

    protected fun captureProvider(case: ScreenshotCase, visibleText: String) {
        pageControl(hasText(visibleText)).assertIsDisplayed()
        assertTrue("固定最终健康状态不应发起检测", healthRequests.isEmpty())
        writeEvidence(case, composeRule.onNodeWithTag(StageTag))
        captureCase(case)
    }

    protected fun captureProviderWindow(case: ScreenshotCase, title: String, operation: String) {
        assertEquals(case.testClass, javaClass.simpleName)
        val root = windowRoot(title).assertIsDisplayed()
        val control = windowControl(title, hasText(operation) and hasClickAction())
        assertFullyVisible(control, root)
        val bounds = root.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width > 0 && bounds.height > 0)
        assertTrue(bounds.width <= case.stageWidthDp * ScreenshotFixture.HostDensity)
        assertTrue(bounds.height <= case.stageHeightDp * ScreenshotFixture.HostDensity)
        val layout = textLayout(inWindow(title, hasText(operation)))
        assertEquals(case.fontScale, layout.layoutInput.density.fontScale, 0.001f)
        assertEquals(ScreenshotFixture.HostDensity, layout.layoutInput.density.density, 0.001f)
        assertTrue("固定最终健康状态不应发起检测", healthRequests.isEmpty())
        File("build/outputs/roborazzi/${case.id}-window.txt").apply { parentFile?.mkdirs() }
            .writeText("title=$title\noperation=$operation\nfontScale=${case.fontScale}\nbounds=$bounds\n" + root.printToString())
        writeEvidence(case, root)
        root.captureRoboImage(case.relativePath, StrictRoborazziOptions)
    }

    private fun writeEvidence(case: ScreenshotCase, root: SemanticsNodeInteraction) {
        File("build/outputs/roborazzi/${case.id}-semantics.xml").apply { parentFile?.mkdirs() }
            .writeText("<semantics id=\"${case.id}\" fontScale=\"${case.fontScale}\"><![CDATA[${root.printToString()}]]></semantics>")
        File("build/outputs/roborazzi/${case.id}-measurements.txt")
            .writeText(captureMeasurements.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }

    private fun assertContains(limit: Rect, bounds: Rect) {
        assertTrue(
            "整个操作触控区必须可见：$bounds / $limit",
            bounds.left >= limit.left - 0.5f && bounds.top >= limit.top - 0.5f &&
                    bounds.right <= limit.right + 0.5f && bounds.bottom <= limit.bottom + 0.5f
        )
    }

    companion object {
        const val ProviderId = "provider-fixture-main"
        const val OtherProviderId = "provider-fixture-other"
        const val CreatedProviderId = "provider-fixture-created"
        const val LongProviderName = "合成提供商：长篇叙事与多语言服务"
        const val OtherProviderName = "合成备用服务"
        const val LongModelName = "长篇叙事与多语言理解模型"
        const val LastModelName = "末项：长上下文与细腻情绪模型"
        const val LastModelId = "fixture-model-5"

        fun models(): List<ModelInfo> = (1..5).map { index ->
            ModelInfo(
                modelId = "fixture-model-$index",
                displayName = when (index) {
                    1 -> LongModelName; 5 -> LastModelName; else -> "合成模型 $index"
                },
                abilities = if (index == 1) linkedSetOf(
                    ModelAbility.VISION,
                    ModelAbility.REASONING,
                    ModelAbility.TOOL
                )
                else linkedSetOf(ModelAbility.VISION, ModelAbility.TOOL),
                abilitiesCustomized = true,
            )
        }

        fun fetchedModels(): List<ModelInfo> = models() + (6..12).map { index ->
            ModelInfo("fixture-model-$index", "云端合成模型 $index", setOf(ModelAbility.VISION))
        }

        fun fixtureProvider() = ProviderSettings(
            id = ProviderId,
            name = LongProviderName,
            type = ProviderType.CUSTOM,
            apiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
            openAiTextApiMode = OpenAiTextApiMode.CHAT_COMPLETIONS,
            baseUrl = "https://fixture.invalid/long-provider-path/v1/",
            apiKey = "fixture-placeholder-only",
            chatCompletionsPath = "/custom/chat/completions",
            selectedModel = models().first().modelId,
            models = models(),
        )

        fun fixtureState(): SettingsUiState {
            val first = fixtureProvider()
            val other = first.copy(
                id = OtherProviderId, name = OtherProviderName, enabled = false,
                type = ProviderType.DEEPSEEK, models = models().take(2)
            )
            val providers = listOf(first, other)
            return SettingsUiState(
                providers = providers,
                selectedProviderId = ProviderId,
                savedSettings = AppSettings(providers = providers, selectedProviderId = ProviderId),
                connectionHealthMap = mapOf(
                    ProviderId to ConnectionHealth.HEALTHY,
                    OtherProviderId to ConnectionHealth.UNHEALTHY
                ),
            )
        }
    }
}
