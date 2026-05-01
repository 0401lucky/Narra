package com.example.myapplication.ui.screen.roleplay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.MemoryProposalHistoryItem
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayGroupParticipant
import com.example.myapplication.model.RoleplayGroupReplyMode
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.isGroupChat
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.ui.component.AssistantAvatar
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassSurface
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import java.net.URI

internal val RoleplaySettingsPanelTitleColor = Color(0xFF1F2736)
internal val RoleplaySettingsPanelBodyColor = Color(0xFF697487)
internal val RoleplaySettingsPanelAccentColor = Color(0xFF586887)

internal enum class RoleplaySettingsPanelPage {
    MAIN,
    SCENE,
    IDENTITY,
    GROUP,
    THEME,
    QUICK,
    REGEX,
}

@Composable
internal fun RoleplaySettingsSidebarContent(
    activePage: RoleplaySettingsPanelPage,
    scenario: RoleplayScenario?,
    assistant: Assistant?,
    settings: AppSettings,
    contextStatus: RoleplayContextStatus,
    groupParticipants: List<RoleplayGroupParticipant>,
    currentModel: String,
    currentProviderId: String,
    providerOptions: List<ProviderSettings>,
    backdropState: ImmersiveBackdropState,
    latestPromptDebugDump: String,
    contextGovernance: ContextGovernanceSnapshot?,
    recentMemoryProposalHistory: List<MemoryProposalHistoryItem>,
    longMemoryCount: Int,
    sceneMemoryCount: Int,
    isRefreshingConversationSummary: Boolean,
    longformCharsText: String,
    onLongformCharsTextChange: (String) -> Unit,
    onNavigateToPage: (RoleplaySettingsPanelPage) -> Unit,
    onOpenReadingMode: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenContextLog: () -> Unit,
    onUpdateShowRoleplayPresenceStrip: (Boolean) -> Unit,
    onUpdateShowRoleplayStatusStrip: (Boolean) -> Unit,
    onUpdateShowOnlineRoleplayNarration: (Boolean) -> Unit,
    onUpdateShowRoleplayAiHelper: (Boolean) -> Unit,
    onUpdateScenarioNarrationEnabled: (Boolean) -> Unit,
    onUpdateScenarioDeepImmersionEnabled: (Boolean) -> Unit,
    onUpdateScenarioTimeAwarenessEnabled: (Boolean) -> Unit,
    onUpdateScenarioNetMemeEnabled: (Boolean) -> Unit,
    onUpdateRoleplayLongformTargetChars: (Int) -> Unit,
    onUpdateScenarioInteractionMode: (RoleplayInteractionMode) -> Unit,
    onToggleGroupParticipantMuted: (String) -> Unit,
    onRemoveGroupParticipant: (String) -> Unit,
    onUpdateGroupReplyMode: (RoleplayGroupReplyMode) -> Unit,
    systemHighContrastEnabled: Boolean,
    onUpdateRoleplayImmersiveMode: (RoleplayImmersiveMode) -> Unit,
    onUpdateRoleplayHighContrast: (Boolean) -> Unit,
    onUpdateRoleplayLineHeightScale: (RoleplayLineHeightScale) -> Unit,
    onUpdateRoleplayNoBackgroundSkin: (RoleplayNoBackgroundSkinSettings) -> Unit,
    onOpenProviderDetail: (String) -> Unit,
    onOpenProviderSettings: () -> Unit,
    onOpenAssistantPrompt: () -> Unit,
    onOpenUserMasks: () -> Unit,
    onOpenWorldBookSettings: () -> Unit,
    onOpenLongMemorySettings: () -> Unit,
    onUpdateAssistantMemoryEnabled: (Boolean) -> Unit,
    onRefreshConversationSummary: () -> Unit,
    onShowRestartDialog: () -> Unit,
    onShowResetDialog: () -> Unit,
) {
    AnimatedContent(
        targetState = activePage,
        transitionSpec = {
            if (targetState == RoleplaySettingsPanelPage.MAIN) {
                // 返回主页：从左滑入
                (slideInHorizontally { -it / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally { it / 3 } + fadeOut())
            } else {
                // 进入子页：从右滑入
                (slideInHorizontally { it / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
            }.using(SizeTransform(clip = false))
        },
        label = "settings_page_transition",
    ) { page ->
    when (page) {
        RoleplaySettingsPanelPage.MAIN -> RoleplaySettingsMainPanel(
            scenario = scenario,
            assistant = assistant,
            settings = settings,
            contextStatus = contextStatus,
            groupParticipants = groupParticipants,
            currentModel = currentModel,
            currentProviderId = currentProviderId,
            providerOptions = providerOptions,
            backdropState = backdropState,
            contextGovernance = contextGovernance,
            latestPromptDebugDump = latestPromptDebugDump,
            longMemoryCount = longMemoryCount,
            sceneMemoryCount = sceneMemoryCount,
            isRefreshingConversationSummary = isRefreshingConversationSummary,
            onNavigateToPage = onNavigateToPage,
            onOpenProviderDetail = onOpenProviderDetail,
            onOpenProviderSettings = onOpenProviderSettings,
            onOpenAssistantPrompt = onOpenAssistantPrompt,
            onOpenWorldBookSettings = onOpenWorldBookSettings,
            onOpenLongMemorySettings = onOpenLongMemorySettings,
            onUpdateAssistantMemoryEnabled = onUpdateAssistantMemoryEnabled,
            onRefreshConversationSummary = onRefreshConversationSummary,
            onOpenContextLog = onOpenContextLog,
            onShowRestartDialog = onShowRestartDialog,
            onShowResetDialog = onShowResetDialog,
        )

        RoleplaySettingsPanelPage.GROUP -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SettingsScreenPadding,
                top = 8.dp,
                end = SettingsScreenPadding,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DetailIntroCard(
                    backdropState = backdropState,
                    title = "群聊设置",
                    summary = "管理成员禁言、移除和回复模式。主题、记忆和高级选项仍在侧栏其它分区里。",
                )
            }
            item {
                RoleplaySettingsGroupMembersSection(
                    backdropState = backdropState,
                    settings = settings,
                    participants = groupParticipants,
                    onToggleMuted = onToggleGroupParticipantMuted,
                    onRemove = onRemoveGroupParticipant,
                )
            }
            item {
                RoleplaySettingsGroupReplyModeSection(
                    backdropState = backdropState,
                    scenario = scenario,
                    onUpdateReplyMode = onUpdateGroupReplyMode,
                )
            }
        }

        RoleplaySettingsPanelPage.SCENE -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SettingsScreenPadding,
                top = 8.dp,
                end = SettingsScreenPadding,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DetailIntroCard(
                    backdropState = backdropState,
                    title = "情景设定",
                    summary = "这里放当前剧情的互动模式、旁白/心声、深度沉浸和时间感知等会直接影响演出的设置。",
                )
            }
            item {
                RoleplaySettingsToggleSection(
                    backdropState = backdropState,
                    scenario = scenario,
                    settings = settings,
                    onUpdateShowRoleplayPresenceStrip = onUpdateShowRoleplayPresenceStrip,
                    onUpdateShowRoleplayStatusStrip = onUpdateShowRoleplayStatusStrip,
                    onUpdateShowRoleplayAiHelper = onUpdateShowRoleplayAiHelper,
                    onUpdateShowOnlineRoleplayNarration = onUpdateShowOnlineRoleplayNarration,
                )
            }
            item {
                RoleplaySettingsPluginSection(
                    backdropState = backdropState,
                    scenario = scenario,
                    onUpdateScenarioNarrationEnabled = onUpdateScenarioNarrationEnabled,
                    onUpdateScenarioDeepImmersionEnabled = onUpdateScenarioDeepImmersionEnabled,
                    onUpdateScenarioTimeAwarenessEnabled = onUpdateScenarioTimeAwarenessEnabled,
                    onUpdateScenarioNetMemeEnabled = onUpdateScenarioNetMemeEnabled,
                )
            }
            item {
                RoleplaySettingsInteractionSection(
                    backdropState = backdropState,
                    scenario = scenario,
                    longformCharsText = longformCharsText,
                    onLongformCharsTextChange = { raw ->
                        onLongformCharsTextChange(raw)
                        raw.filter(Char::isDigit).take(4).toIntOrNull()?.let(onUpdateRoleplayLongformTargetChars)
                    },
                    onUpdateScenarioInteractionMode = onUpdateScenarioInteractionMode,
                )
            }
        }

        RoleplaySettingsPanelPage.IDENTITY -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SettingsScreenPadding,
                top = 8.dp,
                end = SettingsScreenPadding,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DetailIntroCard(
                    backdropState = backdropState,
                    title = "用户身份",
                    summary = "这里汇总当前对话者身份、角色人设和后续可扩展的人设提示词入口。",
                )
            }
            item {
                RoleplayIdentitySummaryCard(
                    backdropState = backdropState,
                    scenario = scenario,
                    assistant = assistant,
                    settings = settings,
                )
            }
            item {
                RoleplayShortcutCard(
                    backdropState = backdropState,
                    title = "我的面具",
                    summary = roleplayMaskSummary(settings),
                    icon = Icons.Default.ManageAccounts,
                    onClick = onOpenUserMasks,
                    enabled = true,
                )
            }
            item {
                RoleplayShortcutCard(
                    backdropState = backdropState,
                    title = "人设提示词",
                    summary = assistant?.name?.ifBlank { "当前角色" } ?: "未绑定角色",
                    icon = Icons.Default.Style,
                    onClick = onOpenAssistantPrompt,
                    enabled = assistant != null,
                )
            }
        }

        RoleplaySettingsPanelPage.THEME -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SettingsScreenPadding,
                top = 8.dp,
                end = SettingsScreenPadding,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                RoleplayNoBackgroundThemeSection(
                    backdropState = backdropState,
                    settings = settings,
                    systemHighContrastEnabled = systemHighContrastEnabled,
                    onUpdateRoleplayNoBackgroundSkin = onUpdateRoleplayNoBackgroundSkin,
                    onUpdateRoleplayImmersiveMode = onUpdateRoleplayImmersiveMode,
                    onUpdateRoleplayHighContrast = onUpdateRoleplayHighContrast,
                    onUpdateRoleplayLineHeightScale = onUpdateRoleplayLineHeightScale,
                )
            }
        }

        RoleplaySettingsPanelPage.QUICK -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SettingsScreenPadding,
                top = 8.dp,
                end = SettingsScreenPadding,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DetailIntroCard(
                    backdropState = backdropState,
                    title = "快捷切换",
                    summary = "这里汇总阅读模式、当前模型、上下文日志和最近记忆提议，适合做运行态快速调节。",
                )
            }
            item {
                RoleplaySettingsActionsSection(
                    backdropState = backdropState,
                    currentModel = currentModel,
                    contextGovernance = contextGovernance,
                    latestPromptDebugDump = latestPromptDebugDump,
                    onOpenReadingMode = onOpenReadingMode,
                    onOpenModelPicker = onOpenModelPicker,
                    onOpenContextLog = onOpenContextLog,
                )
            }
            if (recentMemoryProposalHistory.isNotEmpty()) {
                item {
                    RoleplaySettingsMemoryHistorySection(
                        backdropState = backdropState,
                        history = recentMemoryProposalHistory,
                    )
                }
            }
        }

        RoleplaySettingsPanelPage.REGEX -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SettingsScreenPadding,
                top = 8.dp,
                end = SettingsScreenPadding,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DetailIntroCard(
                    backdropState = backdropState,
                    title = "正则",
                    summary = "这块先按入口预留出来，后面可以接世界书命中后的后处理、格式清洗或专门的剧情规则链。",
                )
            }
            item {
                PlaceholderInfoCard(
                    backdropState = backdropState,
                    title = "正则功能预留",
                    body = "当前项目里还没有像 tavo 那样单独暴露正则配置页，所以这里先把入口和信息架构留出来。后面要接时，建议和世界书、人设并列，做成每条规则可单独启停与预览。",
                )
            }
        }
    }
    }
}

@Composable
private fun RoleplaySettingsMainPanel(
    scenario: RoleplayScenario?,
    assistant: Assistant?,
    settings: AppSettings,
    contextStatus: RoleplayContextStatus,
    groupParticipants: List<RoleplayGroupParticipant>,
    currentModel: String,
    currentProviderId: String,
    providerOptions: List<ProviderSettings>,
    backdropState: ImmersiveBackdropState,
    contextGovernance: ContextGovernanceSnapshot?,
    latestPromptDebugDump: String,
    longMemoryCount: Int,
    sceneMemoryCount: Int,
    isRefreshingConversationSummary: Boolean,
    onNavigateToPage: (RoleplaySettingsPanelPage) -> Unit,
    onOpenProviderDetail: (String) -> Unit,
    onOpenProviderSettings: () -> Unit,
    onOpenAssistantPrompt: () -> Unit,
    onOpenWorldBookSettings: () -> Unit,
    onOpenLongMemorySettings: () -> Unit,
    onUpdateAssistantMemoryEnabled: (Boolean) -> Unit,
    onRefreshConversationSummary: () -> Unit,
    onOpenContextLog: () -> Unit,
    onShowRestartDialog: () -> Unit,
    onShowResetDialog: () -> Unit,
) {
    val activeProvider = providerOptions.firstOrNull { it.id == currentProviderId }
        ?: settings.activeProvider()
    val connectionSummary = activeProvider?.let { provider ->
        provider.name.ifBlank { provider.baseUrl.hostOrValue() }
            .ifBlank { provider.baseUrl.hostOrValue() }
    }.orEmpty().ifBlank { "未配置" }
    val assistantLinkedWorldBookCount = assistant?.linkedWorldBookBookIds?.size
        ?.takeIf { it > 0 }
        ?: assistant?.linkedWorldBookIds?.size
        ?: 0
    val summaryReady = settings.resolveFunctionProvider(ProviderFunction.TITLE_SUMMARY)
        ?.hasBaseCredentials() == true &&
        settings.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY).isNotBlank()
    val memoryReady = assistant?.memoryEnabled == true &&
        settings.resolveFunctionProvider(ProviderFunction.MEMORY)?.hasBaseCredentials() == true &&
        settings.resolveFunctionModel(ProviderFunction.MEMORY).isNotBlank()
    val summaryRefreshText = when {
        isRefreshingConversationSummary -> "处理中…"
        !summaryReady && !memoryReady -> "未配置模型"
        contextGovernance?.hasActionableSummaryRefresh == true -> "现在可刷新"
        contextStatus.hasSummary -> "摘要 ${contextStatus.summaryCoveredMessageCount} 条"
        longMemoryCount + sceneMemoryCount > 0 -> "已有记忆 ${longMemoryCount + sceneMemoryCount} 条"
        else -> "可手动触发"
    }
    val longMemorySummary = when {
        assistant == null -> "未绑定角色"
        assistant.memoryEnabled != true -> "先启用长记忆"
        longMemoryCount > 0 || sceneMemoryCount > 0 -> buildString {
            append("核心 $longMemoryCount")
            if (sceneMemoryCount > 0) {
                append(" · 本场 $sceneMemoryCount")
            }
        }
        else -> "暂无已写入记忆"
    }
    val effectiveUserName = RoleplayConversationSupport
        .resolveUserPersona(scenario, settings)
        .displayName
    val isGroupChat = scenario?.isGroupChat == true
    var advancedExpanded by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .background(Color.Transparent)
            .clickable(enabled = false) {}
            .padding(top = 8.dp)
            .testTag(TAG_ROLEPLAY_SETTINGS_LIST),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "overview") {
            Column(modifier = Modifier.animateItem()) {
                RoleplaySettingsOverviewCard(
                    backdropState = backdropState,
                    scenario = scenario,
                    assistant = assistant,
                    groupParticipants = groupParticipants,
                    currentModel = currentModel,
                    contextStatus = contextStatus,
                    contextGovernance = contextGovernance,
                )
            }
        }
        item(key = "label_chat") {
            Box(modifier = Modifier.animateItem()) {
                GroupLabel("聊天设定")
            }
        }
        item(key = "card_chat_scene") {
            Column(modifier = Modifier.animateItem()) {
            ImmersiveSettingsCard(backdropState) {
                SummaryLinkRow(
                    title = "情景设定",
                    summary = scenario?.interactionMode?.displayName ?: "未设置",
                    icon = Icons.Default.Tune,
                    onClick = { onNavigateToPage(RoleplaySettingsPanelPage.SCENE) },
                )
                SummaryDivider()
                SummaryLinkRow(
                    title = "用户身份",
                    summary = effectiveUserName,
                    icon = Icons.Default.ManageAccounts,
                    onClick = { onNavigateToPage(RoleplaySettingsPanelPage.IDENTITY) },
                )
                if (isGroupChat) {
                    SummaryDivider()
                    SummaryLinkRow(
                        title = "群聊设置",
                        summary = "${groupParticipants.size} 位成员 · ${scenario?.groupReplyMode?.displayName.orEmpty()}",
                        icon = Icons.Default.Group,
                        onClick = { onNavigateToPage(RoleplaySettingsPanelPage.GROUP) },
                    )
                }
            }
            }
        }
        item(key = "card_chat_theme") {
            Column(modifier = Modifier.animateItem()) {
            ImmersiveSettingsCard(backdropState) {
                SummaryLinkRow(
                    title = "主题",
                    summary = "${settings.roleplayNoBackgroundSkin.preset.displayName} · ${roleplayImmersiveModeLabel(settings.roleplayImmersiveMode)}",
                    icon = Icons.Default.Style,
                    onClick = { onNavigateToPage(RoleplaySettingsPanelPage.THEME) },
                )
                SummaryDivider()
                SummaryLinkRow(
                    title = "快捷切换",
                    summary = currentModel.ifBlank { "阅读模式 / 当前模型" },
                    icon = Icons.Default.SyncAlt,
                    onClick = { onNavigateToPage(RoleplaySettingsPanelPage.QUICK) },
                )
            }
            }
        }
        item(key = "label_advanced") {
            Box(modifier = Modifier.animateItem()) {
                ExpandableGroupHeader(
                    title = "高级选项",
                    expanded = advancedExpanded,
                    onToggle = { advancedExpanded = !advancedExpanded },
                )
            }
        }
        item(key = "advanced_content") {
            Column(modifier = Modifier.animateItem()) {
            AnimatedVisibility(
                visible = advancedExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ImmersiveSettingsCard(backdropState) {
                        SummaryLinkRow(
                            title = "提供商连接",
                            summary = connectionSummary,
                            icon = Icons.Default.Link,
                            onClick = {
                                val providerId = activeProvider?.id.orEmpty()
                                if (providerId.isNotBlank()) {
                                    onOpenProviderDetail(providerId)
                                } else {
                                    onOpenProviderSettings()
                                }
                            },
                        )
                    }
                    ImmersiveSettingsCard(backdropState) {
                        SummaryLinkRow(
                            title = "人设提示词",
                            summary = assistant?.name?.ifBlank { "当前角色" } ?: "未绑定角色",
                            icon = Icons.Default.AutoStories,
                            onClick = onOpenAssistantPrompt,
                            enabled = assistant != null,
                        )
                        SummaryDivider()
                        SummaryLinkRow(
                            title = "世界书",
                            summary = if (assistantLinkedWorldBookCount > 0) {
                                "$assistantLinkedWorldBookCount 组已挂载"
                            } else {
                                "未挂载"
                            },
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            onClick = onOpenWorldBookSettings,
                        )
                        SummaryDivider()
                        SummaryLinkRow(
                            title = "正则",
                            summary = "待接入",
                            icon = Icons.Default.Code,
                            onClick = { onNavigateToPage(RoleplaySettingsPanelPage.REGEX) },
                        )
                    }
                    ImmersiveSettingsCard(backdropState) {
                        SummarySwitchRow(
                            title = "长记忆",
                            summary = if (assistant?.memoryEnabled == true) "已启用" else "未启用",
                            icon = Icons.Default.Psychology,
                            checked = assistant?.memoryEnabled == true,
                            onCheckedChange = onUpdateAssistantMemoryEnabled,
                            enabled = assistant != null,
                        )
                        SummaryDivider()
                        SummaryLinkRow(
                            title = "长记忆管理",
                            summary = longMemorySummary,
                            icon = Icons.Default.ViewAgenda,
                            onClick = onOpenLongMemorySettings,
                            enabled = assistant != null,
                        )
                        SummaryDivider()
                        SummaryLinkRow(
                            title = "整理摘要与记忆",
                            summary = summaryRefreshText,
                            icon = Icons.Default.SettingsSuggest,
                            onClick = onRefreshConversationSummary,
                            enabled = (summaryReady || memoryReady) && !isRefreshingConversationSummary,
                            loading = isRefreshingConversationSummary,
                        )
                    }
                }
            }
            }
        }
        item(key = "label_creator") {
            Box(modifier = Modifier.animateItem()) {
                GroupLabel("创作者")
            }
        }
        item(key = "card_creator_debug") {
            Column(modifier = Modifier.animateItem()) {
            ImmersiveSettingsCard(backdropState) {
                SummaryLinkRow(
                    title = "上下文日志",
                    summary = if (latestPromptDebugDump.isNotBlank()) {
                        contextGovernance?.estimatedContextTokens?.takeIf { it > 0 }?.let { "约 $it tokens" }
                            ?: "查看本轮注入"
                    } else {
                        "查看最近 15 条"
                    },
                    icon = Icons.Default.SettingsSuggest,
                    onClick = onOpenContextLog,
                )
            }
            }
        }
        item(key = "footer_actions") {
            Box(modifier = Modifier.animateItem()) {
                FooterActionRow(
                    backdropState = backdropState,
                    onShowRestartDialog = onShowRestartDialog,
                    onShowResetDialog = onShowResetDialog,
                )
            }
        }
    }
}

@Composable
private fun RoleplaySettingsGroupMembersSection(
    backdropState: ImmersiveBackdropState,
    settings: AppSettings,
    participants: List<RoleplayGroupParticipant>,
    onToggleMuted: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val assistants = settings.resolvedAssistants()
    ImmersiveSettingsCard(backdropState) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (participants.isEmpty()) {
                Text(
                    text = "还没有群成员",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoleplaySettingsPanelBodyColor,
                )
            } else {
                participants.sortedBy { it.sortOrder }.forEachIndexed { index, participant ->
                    val assistant = assistants.firstOrNull { it.id == participant.assistantId }
                    RoleplayGroupParticipantRow(
                        participant = participant,
                        assistant = assistant,
                        onToggleMuted = onToggleMuted,
                        onRemove = onRemove,
                    )
                    if (index < participants.lastIndex) {
                        SummaryDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleplayGroupParticipantRow(
    participant: RoleplayGroupParticipant,
    assistant: Assistant?,
    onToggleMuted: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val displayName = participant.displayNameOverride.ifBlank { assistant?.name ?: "未知角色" }
    var showRemoveConfirm by rememberSaveable(participant.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AssistantAvatar(
            name = displayName,
            iconName = assistant?.iconName?.ifBlank { "auto_stories" } ?: "auto_stories",
            avatarUri = participant.avatarUriOverride.ifBlank { assistant?.avatarUri.orEmpty() },
            size = 42.dp,
            containerColor = RoleplaySettingsPanelBodyColor.copy(alpha = 0.14f),
            contentColor = RoleplaySettingsPanelTitleColor,
            cornerRadius = 14.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (participant.isMuted) {
                    RoleplaySettingsPanelBodyColor
                } else {
                    RoleplaySettingsPanelTitleColor
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (participant.isMuted) "已禁言" else "可自然接话",
                style = MaterialTheme.typography.bodySmall,
                color = RoleplaySettingsPanelBodyColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
        ) {
            TextButton(onClick = { onToggleMuted(participant.id) }) {
                Text(if (participant.isMuted) "解除" else "禁言")
            }
            TextButton(onClick = { showRemoveConfirm = true }) {
                Text("移除", color = Color(0xFFD95C5C))
            }
        }
    }
    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("移除群成员") },
            text = { Text("确认将 $displayName 从群聊中移除？历史消息会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirm = false
                        onRemove(participant.id)
                    },
                ) {
                    Text("移除", color = Color(0xFFD95C5C))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun RoleplaySettingsGroupReplyModeSection(
    backdropState: ImmersiveBackdropState,
    scenario: RoleplayScenario?,
    onUpdateReplyMode: (RoleplayGroupReplyMode) -> Unit,
) {
    ImmersiveSettingsCard(backdropState) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            RoleplayGroupReplyMode.entries.forEachIndexed { index, mode ->
                val selected = scenario?.groupReplyMode == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdateReplyMode(mode) }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = RoleplaySettingsPanelTitleColor,
                        )
                        Text(
                            text = mode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = RoleplaySettingsPanelBodyColor,
                        )
                    }
                    if (selected) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = RoleplaySettingsPanelAccentColor.copy(alpha = 0.16f),
                            contentColor = RoleplaySettingsPanelAccentColor,
                        ) {
                            Text(
                                text = "当前",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (index < RoleplayGroupReplyMode.entries.lastIndex) {
                    SummaryDivider()
                }
            }
        }
    }
}

@Composable
private fun RoleplaySettingsOverviewCard(
    backdropState: ImmersiveBackdropState,
    scenario: RoleplayScenario?,
    assistant: Assistant?,
    groupParticipants: List<RoleplayGroupParticipant>,
    currentModel: String,
    contextStatus: RoleplayContextStatus,
    contextGovernance: ContextGovernanceSnapshot?,
) {
    val palette = backdropState.palette
    val scenarioTitle = scenario?.title?.trim().orEmpty().ifBlank { "沉浸扮演" }
    val isGroupChat = scenario?.isGroupChat == true
    val characterName = if (isGroupChat) {
        "群聊 · ${groupParticipants.size} 位成员"
    } else {
        scenario?.characterDisplayNameOverride?.trim()
            .orEmpty()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
    }
    ImmersiveSettingsCard(backdropState) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isGroupChat) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = palette.panelTintStrong.copy(alpha = 0.85f),
                    contentColor = Color.White,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Group, contentDescription = null)
                    }
                }
            } else {
                AssistantAvatar(
                    name = characterName,
                    iconName = assistant?.iconName.orEmpty(),
                    avatarUri = assistant?.avatarUri.orEmpty(),
                    size = 54.dp,
                    containerColor = palette.panelTintStrong.copy(alpha = 0.85f),
                    contentColor = Color.White,
                    cornerRadius = 18.dp,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = scenarioTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Cursive),
                    fontWeight = FontWeight.SemiBold,
                    color = RoleplaySettingsPanelTitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = characterName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoleplaySettingsPanelBodyColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(if (contextStatus.isContinuingSession) "继续旧剧情" else "新剧情")
                        if (currentModel.isNotBlank()) append(" · $currentModel")
                        contextGovernance?.summarySupportingText?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = RoleplaySettingsPanelBodyColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RoleplayIdentitySummaryCard(
    backdropState: ImmersiveBackdropState,
    scenario: RoleplayScenario?,
    assistant: Assistant?,
    settings: AppSettings,
) {
    val effectiveUserPersona = RoleplayConversationSupport.resolveUserPersona(scenario, settings)
    val effectiveUserName = effectiveUserPersona.displayName
    val effectiveMaskName = settings.normalizedUserPersonaMasks()
        .firstOrNull { it.id == effectiveUserPersona.sourceMaskId }
        ?.name
        .orEmpty()
    val effectiveCharacterName = scenario?.characterDisplayNameOverride?.trim()
        .orEmpty()
        .ifBlank { assistant?.name?.trim().orEmpty() }
        .ifBlank { "未绑定" }
    val effectiveUserPersonaPrompt = effectiveUserPersona.personaPrompt
    ImmersiveSettingsCard(backdropState) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "当前身份概览",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Cursive),
                fontWeight = FontWeight.SemiBold,
                color = RoleplaySettingsPanelTitleColor,
            )
            IdentityMetric(
                "用户昵称",
                effectiveUserName,
                RoleplaySettingsPanelTitleColor,
                RoleplaySettingsPanelBodyColor,
            )
            IdentityMetric(
                "当前角色",
                effectiveCharacterName,
                RoleplaySettingsPanelTitleColor,
                RoleplaySettingsPanelBodyColor,
            )
            IdentityMetric(
                "当前面具",
                effectiveMaskName.ifBlank { "未绑定，使用全局个人资料。" },
                RoleplaySettingsPanelTitleColor,
                RoleplaySettingsPanelBodyColor,
            )
            IdentityMetric(
                "用户身份提示词",
                effectiveUserPersonaPrompt.ifBlank { "未填写，当前只使用昵称。" },
                RoleplaySettingsPanelTitleColor,
                RoleplaySettingsPanelBodyColor,
            )
        }
    }
}

@Composable
private fun IdentityMetric(
    label: String,
    value: String,
    titleColor: Color,
    bodyColor: Color,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = bodyColor,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = titleColor,
        )
    }
}

private fun roleplayMaskSummary(settings: AppSettings): String {
    val masks = settings.normalizedUserPersonaMasks()
    val defaultMask = settings.resolvedDefaultUserPersonaMask()
    return when {
        masks.isEmpty() -> "还没有面具，点击创建不同对话里的“我”"
        defaultMask != null -> "默认：${defaultMask.name} · 共 ${masks.size} 个身份"
        else -> "${masks.size} 个身份，未设置默认"
    }
}

@Composable
private fun RoleplayShortcutCard(
    backdropState: ImmersiveBackdropState,
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    ImmersiveSettingsCard(backdropState) {
        SummaryLinkRow(
            title = title,
            summary = summary,
            icon = icon,
            onClick = onClick,
            enabled = enabled,
            contentColor = RoleplaySettingsPanelTitleColor,
            supportingColor = RoleplaySettingsPanelBodyColor,
        )
    }
}

@Composable
private fun DetailIntroCard(
    backdropState: ImmersiveBackdropState,
    title: String,
    summary: String,
) {
    ImmersiveSettingsCard(backdropState) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Cursive),
                fontWeight = FontWeight.SemiBold,
                color = RoleplaySettingsPanelTitleColor,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = RoleplaySettingsPanelBodyColor,
            )
        }
    }
}

@Composable
private fun PlaceholderInfoCard(
    backdropState: ImmersiveBackdropState,
    title: String,
    body: String,
) {
    ImmersiveSettingsCard(backdropState) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = RoleplaySettingsPanelTitleColor,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = RoleplaySettingsPanelBodyColor,
            )
        }
    }
}

@Composable
private fun GroupLabel(
    title: String,
) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = FontFamily.Cursive,
        ),
        fontWeight = FontWeight.SemiBold,
        color = RoleplaySettingsPanelTitleColor.copy(alpha = 0.72f),
    )
}

@Composable
private fun ExpandableGroupHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Cursive),
            fontWeight = FontWeight.SemiBold,
            color = RoleplaySettingsPanelTitleColor.copy(alpha = 0.72f),
        )
        Text(
            text = if (expanded) "收起" else "展开",
            style = MaterialTheme.typography.bodySmall,
            color = RoleplaySettingsPanelBodyColor,
        )
    }
}

@Composable
private fun SummaryLinkRow(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = contentColor,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) contentColor else supportingColor.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) contentColor else supportingColor.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) supportingColor else supportingColor.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        } else {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) supportingColor else supportingColor.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SummarySwitchRow(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun SummaryDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 18.dp),
        color = RoleplaySettingsPanelBodyColor.copy(alpha = 0.18f),
    )
}

@Composable
private fun FooterActionRow(
    backdropState: ImmersiveBackdropState,
    onShowRestartDialog: () -> Unit,
    onShowResetDialog: () -> Unit,
) {
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        overlayColor = Color(0xFFF2EEE8).copy(alpha = 0.82f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FooterActionButton(
                title = "重启",
                tint = RoleplaySettingsPanelAccentColor,
                modifier = Modifier.weight(1f),
                onClick = onShowRestartDialog,
            )
            FooterActionButton(
                title = "重置",
                tint = Color(0xFFD95C5C),
                modifier = Modifier.weight(1f),
                onClick = onShowResetDialog,
            )
        }
    }
}

@Composable
private fun FooterActionButton(
    title: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = tint.copy(alpha = 0.12f),
        contentColor = tint,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Cursive),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun String.hostOrValue(): String {
    val trimmed = trim()
    if (trimmed.isBlank()) {
        return ""
    }
    return try {
        val normalized = if ("://" in trimmed) trimmed else "https://$trimmed"
        URI(normalized).host?.ifBlank { trimmed } ?: trimmed
    } catch (_: Exception) {
        trimmed
    }
}
