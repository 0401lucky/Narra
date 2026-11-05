package com.example.myapplication.ui.screenshot

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import com.example.myapplication.model.*
import com.example.myapplication.data.repository.LocalImageStore
import com.example.myapplication.ui.LocalImagePersister
import com.example.myapplication.ui.screen.roleplay.RoleplaySettingsScreen
import com.example.myapplication.ui.screen.roleplay.TAG_ROLEPLAY_SETTINGS_LIST
import com.example.myapplication.ui.theme.resolveAppColorScheme
import com.example.myapplication.viewmodel.SettingsViewModel
import com.example.myapplication.viewmodel.updateRoleplayNoBackgroundSkin
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.robolectric.RuntimeEnvironment

/** 固定数据的真实 Screen → Content → Sidebar；不创建用户数据库或网络。 */
abstract class SessionSidebarRenderTest : ScreenshotStageTest() {
    protected val events = mutableListOf<String>()
    protected var pendingRestart: (() -> Unit)? = null
    protected var pendingReset: (() -> Unit)? = null
    protected val skin = RoleplayNoBackgroundSkinSettings(bubbleRadiusDp = 18,
        bubblePaddingHorizontalDp = 14, bubblePaddingVerticalDp = 10,
        showBubbleTail = true, onlineBackgroundGlassEnabled = false)
    protected val settings = mutableStateOf(AppSettings(
        roleplayNoBackgroundSkin = skin,
        assistants = ScreenshotFixture.Assistants,
        userPersonaMasks = (1..8).map { UserPersonaMask(id = "mask-$it", name = "面具$it：在漫长雨夜中旅行的记录者",
            personaPrompt = "保留独立身份与长篇规则，只影响当前角色。", createdAt = 1000, updatedAt = 1000) },
    ))
    protected fun renderSidebar(case: ScreenshotCase, theme: AppColorTheme = AppColorTheme.MATCHA,
        dark: Boolean = false, dynamic: Boolean = false, group: Boolean = false,
        settingsViewModel: SettingsViewModel? = null,
        scenarioOverride: RoleplayScenario? = null,
        colorSchemeState: State<ColorScheme>? = null) {
        if (case.fontScale == 2f) {
            RuntimeEnvironment.setQualifiers(ScreenshotFixture.NarrowQualifiers)
            useNarrowFontScale()
        }
        val scenario = scenarioOverride ?: ScreenshotFixture.SuwanScenario.copy(
            title = "雨夜的旧书店与一段需要完整保留的很长很长的会话标题",
            chatType = if (group) RoleplayChatType.GROUP else RoleplayChatType.SINGLE,
        )
        val providers = listOf(
            ProviderSettings(id = "fixture-provider", name = "合成主提供商",
                baseUrl = "https://fixture.invalid", apiKey = "fixture-not-a-real-credential",
                availableModels = listOf(LongModelName, "fixture-model-short")),
            ProviderSettings(id = "fixture-provider-backup", name = "合成备用提供商",
                baseUrl = "https://backup.invalid", apiKey = "fixture-not-a-real-credential",
                availableModels = listOf("fixture-backup")),
        )
        val imageStore = LocalImageStore(composeRule.activity)
        val screenContent: @Composable () -> Unit = {
            val stored = settingsViewModel?.storedSettings?.collectAsState()?.value
            val draft = settingsViewModel?.uiState?.collectAsState()?.value
            val displayed = if (stored != null && draft != null) stored.copy(roleplayNoBackgroundSkin = draft.roleplayNoBackgroundSkin)
                else settings.value
            RoleplaySettingsScreen(
                scenario = scenario, assistant = ScreenshotFixture.SuwanAssistant,
                settings = displayed, presets = (1..8).map { Preset(id = "preset-$it",
                    name = "预设$it：长篇叙事与细腻情绪的完整规则", description = "保持角色身份、自然节奏与清晰场景描写。", createdAt = 1000) },
                contextStatus = RoleplayContextStatus(),
                groupParticipants = if (group) ScreenshotFixture.Assistants.take(3).mapIndexed { index, assistant ->
                    RoleplayGroupParticipant(id = "member-$index", scenarioId = scenario.id, assistantId = assistant.id,
                        displayNameOverride = "${assistant.name}：名字很长的群聊参与成员", sortOrder = index, isMuted = index == 1)
                } else emptyList(),
                currentModel = LongModelName, currentProviderId = "fixture-provider", providerOptions = providers,
                isLoadingModels = false, loadingProviderId = "", isSavingModel = false,
                latestPromptDebugDump = "", contextGovernance = null, recentMemoryProposalHistory = emptyList(),
                longMemoryCount = 0, sceneMemoryCount = 0, isRefreshingConversationSummary = false,
                onOpenReadingMode = { if (settingsViewModel != null) settingsViewModel.saveSettings { events += "reading" } else events += "reading" },
                onUpdateShowRoleplayPresenceStrip = {}, onUpdateShowOnlineRoleplayNarration = {},
                onUpdateShowRoleplayAiHelper = {}, onUpdateScenarioNarrationEnabled = {},
                onUpdateScenarioDeepImmersionEnabled = { events += "immersion:$it" }, onUpdateScenarioTimeAwarenessEnabled = {},
                onUpdateScenarioNetMemeEnabled = {}, onUpdateScenarioOnlineProactiveReplyEnabled = {},
                onUpdateRoleplayLongformTargetChars = { events += "chars:$it" }, onUpdateScenarioInteractionMode = { events += "mode:$it" },
                onUpdateScenarioOnlineReplyRange = { _, _ -> }, onUpdateScenarioPresetId = { events += "preset:$it" },
                onUpdateScenarioUserPersonaMaskId = { events += "mask:$it" }, onUpdateScenarioVisual = { a, b, c, d, e -> events += "visual:$a|$b|$c|$d|$e" },
                onAddGroupParticipant = { events += "add:$it" }, onToggleGroupParticipantMuted = { events += "mute:$it" }, onRemoveGroupParticipant = { events += "remove:$it" },
                onUpdateGroupReplyMode = { events += "reply:$it" }, onUpdateRoleplayImmersiveMode = { settings.value = settings.value.copy(roleplayImmersiveMode = it); events += "immersive:$it" },
                onUpdateRoleplayHighContrast = { settings.value = settings.value.copy(roleplayHighContrast = it); events += "contrast:$it" },
                onUpdateRoleplayLineHeightScale = { settings.value = settings.value.copy(roleplayLineHeightScale = it); events += "line:$it" },
                onUpdateRoleplayNoBackgroundSkin = { if (settingsViewModel != null) settingsViewModel.updateRoleplayNoBackgroundSkin(it)
                    else settings.value = settings.value.copy(roleplayNoBackgroundSkin = it); events += "skin" },
                onSelectProvider = { events += "provider:$it" }, onSelectModel = { provider, model -> events += "model:$provider:$model" },
                onOpenProviderDetail = {}, onOpenProviderSettings = {}, onOpenAssistantPrompt = {}, onOpenUserMasks = { events += "manage-masks" },
                onOpenWorldBookSettings = {}, onOpenLongMemorySettings = {}, onOpenPresetLibrary = { events += "manage-presets" },
                onUpdateAssistantMemoryEnabled = {}, onRefreshConversationSummary = {}, onOpenContextLog = {},
                onRestartSession = { pendingRestart = it; events += "restart" }, onResetSession = { pendingReset = it; events += "reset" },
                onNavigateBack = { if (settingsViewModel != null) settingsViewModel.saveSettings { events += "back" } else events += "back" },
            )
        }
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalImagePersister provides imageStore) { screenContent() }
        }
        if (colorSchemeState != null) setColorSchemeStateStage(case, colorSchemeState, content)
        else if (dynamic) setColorSchemeStage(case, resolveAppColorScheme(theme, dark, lightColorScheme()), content)
        else setStage(case, theme, dark, content)
        composeRule.waitForIdle()
    }

    protected fun open(label: String) {
        composeRule.onNodeWithTag(TAG_ROLEPLAY_SETTINGS_LIST).performScrollToNode(hasText(label))
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
    }

    protected fun windowMatcher(title: String): SemanticsMatcher =
        isRoot() and hasAnyDescendant(hasText(title)) and
            hasAnyDescendant(isDialog()) and !hasAnyDescendant(hasTestTag(StageTag))

    protected fun windowRoot(title: String): SemanticsNodeInteraction = composeRule.onNode(
        windowMatcher(title), useUnmergedTree = true,
    ).assertExists("必须打开标题为 $title 的真实独立窗口，不能捕获底层舞台")

    protected fun captureWindow(case: ScreenshotCase, title: String, operation: String) {
        val root = windowRoot(title)
        root.assertIsDisplayed()
        composeRule.onNode(hasText(operation) and hasAnyAncestor(windowMatcher(title)), useUnmergedTree = true).assertIsDisplayed()
        val bounds = root.fetchSemanticsNode().boundsInRoot
        assertTrue("独立窗口必须有有效尺寸", bounds.width > 0 && bounds.height > 0)
        assertTrue("独立窗口不可超出固定宿主宽度", bounds.width <= case.stageWidthDp * ScreenshotFixture.HostDensity)
        assertTrue("独立窗口不可超出固定宿主高度", bounds.height <= case.stageHeightDp * ScreenshotFixture.HostDensity)
        assertEquals("窗口继承真实200%字体", case.fontScale,
            composeRule.activity.resources.configuration.fontScale, 0.001f)
        File("build/outputs/roborazzi/${case.id}-window.txt").apply { parentFile?.mkdirs() }
            .writeText("title=$title\noperation=$operation\nfontScale=${case.fontScale}\nbounds=$bounds\n" + root.printToString())
        writeSemantics(case, root)
        root.captureRoboImage(case.relativePath, StrictRoborazziOptions)
    }

    protected fun captureSidebar(case: ScreenshotCase) {
        writeSemantics(case, composeRule.onNodeWithTag(StageTag))
        captureCase(case)
    }

    private fun writeSemantics(case: ScreenshotCase, root: SemanticsNodeInteraction) {
        File("build/outputs/roborazzi/${case.id}-semantics.xml").apply { parentFile?.mkdirs() }
            .writeText("<semantics id=\"${case.id}\" fontScale=\"${case.fontScale}\"><![CDATA[${root.printToString()}]]></semantics>")
    }

    companion object {
        const val LongModelName = "fixture-model-长上下文与细腻情绪叙事完整模型名称"
    }
}
