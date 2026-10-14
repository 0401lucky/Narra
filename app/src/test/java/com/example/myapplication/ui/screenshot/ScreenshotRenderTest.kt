package com.example.myapplication.ui.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.RoleplayChatSummary
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayGroupParticipant
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.testutil.createTestAiServices
import com.example.myapplication.ui.component.roleplay.RoleplayMessageBubbleMode
import com.example.myapplication.ui.component.roleplay.RoleplayMessageItem
import com.example.myapplication.ui.component.roleplay.rememberImmersiveRoleplayColors
import com.example.myapplication.ui.screen.immersive.ImmersivePhoneCallbacks
import com.example.myapplication.ui.screen.immersive.ImmersivePhoneShell
import com.example.myapplication.ui.screen.roleplay.RoleplayOnlinePhoneContent
import com.example.myapplication.ui.screen.settings.AssistantListScreen
import com.example.myapplication.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers

/**
 * 截图安全网的渲染编排层：
 * - P01/P02/P03 页面级入口直接使用真实页面与纯合成状态；
 * - 气泡级入口使用真实 RoleplayMessageItem 与真实颜色解析；
 * - P02 使用 testutil 的真实 SettingsViewModel + FakeSettingsStore，不启动 AppGraph、网络或用户数据库。
 */
abstract class ScreenshotRenderTest : ScreenshotStageTest() {

    /** P01：ImmersivePhoneShell（消息首页 / 手机主壳），纯参数据入口。 */
    protected fun renderMessagesShellPage(
        case: ScreenshotCase,
        appColorTheme: AppColorTheme,
        darkTheme: Boolean,
        summaries: List<RoleplayChatSummary> = ScreenshotFixture.ChatSummaries,
    ) {
        setStage(case, appColorTheme, darkTheme) {
            ImmersivePhoneShell(
                settings = AppSettings(appColorTheme = appColorTheme),
                assistants = ScreenshotFixture.Assistants,
                chatSummaries = summaries,
                noticeMessage = null,
                errorMessage = null,
                onClearNoticeMessage = {},
                onClearErrorMessage = {},
                callbacks = noOpPhoneCallbacks(),
            )
        }
    }

    /** P02：AssistantListScreen + 真实 SettingsViewModel（FakeSettingsStore 提供合成角色）。 */
    protected fun renderAssistantListPage(
        case: ScreenshotCase,
        appColorTheme: AppColorTheme,
        darkTheme: Boolean,
        settings: AppSettings = assistantListSettings(appColorTheme),
    ) {
        val services = createTestAiServices(settings = settings, dispatcher = Dispatchers.Unconfined)
        val viewModel = SettingsViewModel(
            settingsRepository = services.settingsRepository,
            settingsEditor = services.settingsEditor,
            modelCatalogRepository = services.modelCatalogRepository,
        )
        setStage(case, appColorTheme, darkTheme) {
            AssistantListScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onNavigateToAssistantConfig = {},
                onDeleteAssistant = {},
            )
        }
        // Robolectric 暂停 looper：先把主线程任务跑完，真实 ViewModel 的 settingsFlow 才会生效。
        drainMainLooper()
        composeRule.waitForIdle()
    }

    /** P03：RoleplayOnlinePhoneContent（internal 纯参数据入口）。 */
    protected fun renderOnlineRoleplayPage(
        case: ScreenshotCase,
        appColorTheme: AppColorTheme,
        darkTheme: Boolean,
        messages: List<RoleplayMessageUiModel>,
        scenario: RoleplayScenario = ScreenshotFixture.SuwanScenario,
        backdropImage: ImageBitmap? = null,
        skin: RoleplayNoBackgroundSkinSettings = RoleplayNoBackgroundSkinSettings(),
        isSending: Boolean = false,
    ) {
        val assistant = ScreenshotFixture.Assistants.firstOrNull { it.id == scenario.assistantId }
        val settings = AppSettings(
            appColorTheme = appColorTheme,
            roleplayNoBackgroundSkin = skin,
            showRoleplayAiHelper = false,
        )
        setStage(case, appColorTheme, darkTheme) {
            val backdropState = ScreenshotFixture.backdropState(
                image = backdropImage,
                widthPx = case.stageWidthDp * 2,
                heightPx = case.stageHeightDp * 2,
            )
            val snackbarHostState = remember { SnackbarHostState() }
            RoleplayOnlinePhoneContent(
                scenario = scenario,
                assistant = assistant,
                settings = settings,
                backdropState = backdropState,
                contextStatus = RoleplayContextStatus(),
                groupParticipants = emptyList<RoleplayGroupParticipant>(),
                messages = messages,
                suggestions = emptyList<RoleplaySuggestionUiModel>(),
                input = "",
                inputFocusToken = 0L,
                replyToMessageId = "",
                replyToPreview = "",
                replyToSpeakerName = "",
                isSending = isSending,
                isGeneratingSuggestions = false,
                suggestionErrorMessage = null,
                pendingMemoryProposal = null,
                snackbarHostState = snackbarHostState,
                onInputChange = { _ -> },
                onGenerateSuggestions = {},
                onApplySuggestion = { _ -> },
                onClearSuggestions = {},
                onRetryTurn = { _ -> },
                onRetryAiPhoto = { _, _ -> },
                onEditUserMessage = { _ -> },
                onQuoteMessage = { _, _, _ -> },
                onPokeMessageAvatar = { _ -> },
                onClearQuotedMessage = {},
                onRecallMessage = { _ -> },
                onDeleteMessage = { _ -> },
                onScreenshotChat = {},
                onOpenDiary = {},
                onOpenVoiceMessage = {},
                onOpenTransferPlay = {},
                onOpenInvitePlay = {},
                onOpenGiftPlay = {},
                onOpenTaskPlay = {},
                onOpenPunishPlay = {},
                onConfirmTransferReceipt = { _ -> },
                onSend = {},
                pendingImageParts = emptyList<ChatMessagePart>(),
                onRemovePendingImagePart = { _ -> },
                onRequestGroupParticipantReply = { _ -> },
                onCancelSending = {},
                onApprovePendingMemoryProposal = {},
                onRejectPendingMemoryProposal = {},
                onOpenPhoneCheck = {},
                onOpenMoments = {},
                onOpenVideoCall = {},
                onOpenMailbox = {},
                onOpenWallet = {},
                onOpenSettings = {},
                onNavigateBack = {},
            )
        }
    }

    /**
     * 气泡组件级入口：真实 [RoleplayMessageItem] + 真实 [rememberImmersiveRoleplayColors]。
     * 组件舞台复刻线上消息列表既有内边距（横向 12dp、纵向 14dp、间距 10dp），
     * 避免“组件贴边”这种真实列表里不存在的构图差异。
     */
    internal fun renderBubbleStage(
        case: ScreenshotCase,
        appColorTheme: AppColorTheme,
        darkTheme: Boolean,
        messages: List<RoleplayMessageUiModel>,
        backdropImage: ImageBitmap? = null,
        skin: RoleplayNoBackgroundSkinSettings = RoleplayNoBackgroundSkinSettings(),
        bubbleMode: RoleplayMessageBubbleMode = RoleplayMessageBubbleMode.ONLINE_PHONE,
    ) {
        setStage(case, appColorTheme, darkTheme) {
            val backdropState = ScreenshotFixture.backdropState(
                image = backdropImage,
                widthPx = case.stageWidthDp * 2,
                heightPx = case.stageHeightDp * 2,
            )
            val colors = rememberImmersiveRoleplayColors(
                backdropState = backdropState,
                noBackgroundSkin = skin,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                messages.forEach { message ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        RoleplayMessageItem(
                            message = message,
                            colors = colors,
                            backdropState = backdropState,
                            onRetryTurn = { _ -> },
                            onRetryAiPhoto = { _, _ -> },
                            onEditUserMessage = { _ -> },
                            onConfirmTransferReceipt = { _ -> },
                            bubbleMode = bubbleMode,
                            noBackgroundSkin = skin,
                        )
                    }
                }
            }
        }
    }

    protected fun assistantListSettings(appColorTheme: AppColorTheme = AppColorTheme.MATCHA) = AppSettings(
        appColorTheme = appColorTheme,
        assistants = ScreenshotFixture.Assistants,
        selectedAssistantId = ScreenshotFixture.SuwanAssistant.id,
    )

    private fun noOpPhoneCallbacks() = ImmersivePhoneCallbacks(
        onNavigateBack = {},
        onOpenChat = { _ -> },
        onOpenChatManage = {},
        onOpenChatEdit = { _ -> },
        onOpenSettings = {},
        onOpenUserMasks = {},
        onOpenAssistantSettings = {},
        onOpenCharacterArtStudio = {},
        onOpenAssistantDetail = { _ -> },
        onOpenAssistantPrompt = { _ -> },
        onOpenAssistantMemory = { _ -> },
        onOpenAssistantWorldBook = { _ -> },
        onOpenWorldBookSettings = {},
        onOpenMemorySettings = {},
        onOpenContextTransferSettings = {},
        onOpenPresetLibrary = {},
        onSetDefaultUserPersonaMask = { _ -> },
        onOpenAssistantCreate = {},
        onCreateChat = { _, _, _ -> },
        onCreateGroupChat = { _, _ -> },
        onUpdatePinned = { _, _ -> },
        onUpdateMuted = { _, _ -> },
        onClearChat = { _ -> },
        onDeleteChat = { _ -> },
        onOpenPhoneCheck = { _ -> },
        onOpenMoments = { _ -> },
        onOpenDiary = { _ -> },
        onOpenVideoCall = { _ -> },
        onOpenMailbox = { _ -> },
        onOpenWallet = { _ -> },
    )
}
