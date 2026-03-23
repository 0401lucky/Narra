package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.UserAvatarLoadState
import com.example.myapplication.ui.component.rememberUserProfileAvatarState
import com.example.myapplication.ui.component.roleplay.RoleplayDialoguePanel
import com.example.myapplication.ui.component.roleplay.RoleplayPortraitSpec
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.screen.chat.ModelPickerSheet
import com.example.myapplication.ui.screen.chat.SpecialPlaySheet
import com.example.myapplication.ui.screen.chat.TransferPlayDraft
import com.example.myapplication.ui.screen.chat.TransferPlaySheet

private data class RoleplayActionRow(
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoleplayScreen(
    scenario: RoleplayScenario?,
    assistant: Assistant?,
    settings: AppSettings,
    contextStatus: RoleplayContextStatus,
    messages: List<RoleplayMessageUiModel>,
    suggestions: List<RoleplaySuggestionUiModel>,
    input: String,
    isSending: Boolean,
    isGeneratingSuggestions: Boolean,
    isScenarioLoading: Boolean,
    showAssistantMismatchDialog: Boolean,
    previousAssistantName: String,
    currentAssistantName: String,
    noticeMessage: String?,
    errorMessage: String?,
    latestPromptDebugDump: String,
    suggestionErrorMessage: String?,
    currentModel: String,
    currentProviderId: String,
    providerOptions: List<ProviderSettings>,
    isLoadingModels: Boolean,
    loadingProviderId: String,
    isSavingModel: Boolean,
    onClearNoticeMessage: () -> Unit,
    onClearErrorMessage: () -> Unit,
    onInputChange: (String) -> Unit,
    onGenerateSuggestions: () -> Unit,
    onApplySuggestion: (String) -> Unit,
    onClearSuggestions: () -> Unit,
    onRetryTurn: (String) -> Unit,
    onSendTransferPlay: (String, String, String) -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
    onSend: () -> Unit,
    onCancelSending: () -> Unit,
    onRestartSession: () -> Unit,
    onResetSession: () -> Unit,
    onDismissAssistantMismatch: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onOpenProviderDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(noticeMessage) {
        noticeMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearNoticeMessage()
        }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearErrorMessage()
        }
    }

    if (scenario == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (isScenarioLoading) "正在载入场景…" else "场景不存在或已被删除")
        }
        return
    }

    val userName = scenario.userDisplayNameOverride.trim()
        .ifBlank { settings.resolvedUserDisplayName() }
    val characterName = scenario.characterDisplayNameOverride.trim()
        .ifBlank { assistant?.name?.trim().orEmpty() }
        .ifBlank { "角色" }
    val highlightedSpeaker = when {
        isSending -> RoleplaySpeaker.CHARACTER
        else -> messages
            .asReversed()
            .firstOrNull { message ->
                message.speaker == RoleplaySpeaker.USER || message.speaker == RoleplaySpeaker.CHARACTER
            }
            ?.speaker
    }

    var showActionSheet by rememberSaveable { mutableStateOf(false) }
    var showModelSheet by rememberSaveable { mutableStateOf(false) }
    var showPromptDebugSheet by rememberSaveable { mutableStateOf(false) }
    var showConfirmResetDialog by rememberSaveable { mutableStateOf(false) }
    var showConfirmRestartDialog by rememberSaveable { mutableStateOf(false) }
    var showSpecialPlaySheet by rememberSaveable { mutableStateOf(false) }
    var showTransferSheet by rememberSaveable { mutableStateOf(false) }
    var transferCounterparty by rememberSaveable { mutableStateOf("") }
    var transferAmount by rememberSaveable { mutableStateOf("") }
    var transferNote by rememberSaveable { mutableStateOf("") }
    var viewingPortrait by remember { mutableStateOf<RoleplayPortraitSpec?>(null) }
    val transferDraft = TransferPlayDraft(
        counterparty = transferCounterparty,
        amount = transferAmount,
        note = transferNote,
    )

    val userPortraitSpec = RoleplayPortraitSpec(
        name = userName,
        avatarUri = scenario.userPortraitUri.ifBlank { settings.userAvatarUri },
        avatarUrl = scenario.userPortraitUrl.ifBlank { settings.userAvatarUrl },
        fallbackLabel = "用户",
    )
    val characterPortraitSpec = RoleplayPortraitSpec(
        name = characterName,
        avatarUri = scenario.characterPortraitUri.ifBlank { assistant?.avatarUri.orEmpty() },
        avatarUrl = scenario.characterPortraitUrl,
        fallbackLabel = "角色",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        RoleplaySceneBackground(
            backgroundUri = scenario.backgroundUri,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = scenario.title.ifBlank { "沉浸扮演" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (contextStatus.isContinuingSession) {
                                "继续剧情"
                            } else {
                                "新剧情入口"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(onClick = { showActionSheet = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                }
            }

            CompactPresenceStrip(
                user = userPortraitSpec,
                character = characterPortraitSpec,
                highlightedSpeaker = highlightedSpeaker,
                isSending = isSending,
                onUserClick = { viewingPortrait = userPortraitSpec },
                onCharacterClick = { viewingPortrait = characterPortraitSpec },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ContextStatusPill(text = if (contextStatus.isContinuingSession) "继续剧情" else "新剧情")
                    ContextStatusPill(text = if (contextStatus.hasSummary) "摘要已注入" else "未摘要")
                    ContextStatusPill(text = "记忆 ${contextStatus.memoryInjectionCount}")
                    ContextStatusPill(text = "世界书 ${contextStatus.worldBookHitCount}")
                }
            }

            RoleplayDialoguePanel(
                messages = messages,
                suggestions = suggestions,
                isGeneratingSuggestions = isGeneratingSuggestions,
                suggestionErrorMessage = suggestionErrorMessage,
                input = input,
                isSending = isSending,
                onInputChange = onInputChange,
                onGenerateSuggestions = onGenerateSuggestions,
                onApplySuggestion = onApplySuggestion,
                onClearSuggestions = onClearSuggestions,
                onRetryTurn = onRetryTurn,
                onOpenSpecialPlay = {
                    if (transferCounterparty.isBlank()) {
                        transferCounterparty = characterName
                    }
                    showSpecialPlaySheet = true
                },
                onConfirmTransferReceipt = onConfirmTransferReceipt,
                onSend = onSend,
                onCancel = { onCancelSending() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    if (showActionSheet) {
        val actionSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )
        ModalBottomSheet(
            onDismissRequest = { showActionSheet = false },
            sheetState = actionSheetState,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "剧情设置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = scenario.title.ifBlank { "沉浸扮演" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    RoleplayStatusOverview(
                        scenarioTitle = scenario.title.ifBlank { "沉浸扮演" },
                        characterName = characterName,
                        currentModel = currentModel,
                        contextStatus = contextStatus,
                    )
                }

                item {
                    ActionSheetSection(
                        title = "模型与调试",
                        rows = listOf(
                            RoleplayActionRow(
                                title = "切换模型",
                                subtitle = currentModel.ifBlank { "当前未选择模型" },
                                enabled = true,
                                onClick = {
                                    showActionSheet = false
                                    showModelSheet = true
                                },
                            ),
                            RoleplayActionRow(
                                title = "查看提示词",
                                subtitle = "查看当前上下文和 RP 提示词",
                                enabled = latestPromptDebugDump.isNotBlank(),
                                onClick = {
                                    showActionSheet = false
                                    showPromptDebugSheet = true
                                },
                            ),
                        ),
                    )
                }

                item {
                    ActionSheetSection(
                        title = "剧情操作",
                        rows = listOf(
                            RoleplayActionRow(
                                title = "重开剧情",
                                subtitle = "创建新的剧情线，不继承当前历史",
                                enabled = !isSending,
                                onClick = {
                                    showActionSheet = false
                                    showConfirmRestartDialog = true
                                },
                            ),
                            RoleplayActionRow(
                                title = "清空剧情",
                                subtitle = "保留场景配置，清空当前会话消息",
                                enabled = messages.isNotEmpty() && !isSending,
                                onClick = {
                                    showActionSheet = false
                                    showConfirmResetDialog = true
                                },
                            ),
                        ),
                    )
                }
            }
        }
    }

    if (showSpecialPlaySheet) {
        SpecialPlaySheet(
            onDismissRequest = { showSpecialPlaySheet = false },
            onOpenTransfer = {
                showSpecialPlaySheet = false
                if (transferCounterparty.isBlank()) {
                    transferCounterparty = characterName
                }
                showTransferSheet = true
            },
        )
    }

    if (showTransferSheet) {
        TransferPlaySheet(
            draft = transferDraft,
            onDraftChange = {
                transferCounterparty = it.counterparty
                transferAmount = it.amount
                transferNote = it.note
            },
            onDismissRequest = { showTransferSheet = false },
            onConfirm = {
                onSendTransferPlay(
                    transferDraft.counterparty.ifBlank { characterName },
                    transferDraft.amount,
                    transferDraft.note,
                )
                transferCounterparty = characterName
                transferAmount = ""
                transferNote = ""
                showTransferSheet = false
            },
        )
    }

    if (showModelSheet) {
        ModelPickerSheet(
            providerOptions = providerOptions,
            currentProviderId = currentProviderId,
            currentModel = currentModel,
            isLoadingModels = isLoadingModels,
            loadingProviderId = loadingProviderId,
            isSavingModel = isSavingModel,
            onDismissRequest = { showModelSheet = false },
            onSelectProvider = onSelectProvider,
            onOpenProviderDetail = { providerId ->
                showModelSheet = false
                onOpenProviderDetail(providerId)
            },
            onSelectModel = { providerId, model ->
                onSelectModel(providerId, model)
                showModelSheet = false
            },
        )
    }

    if (showPromptDebugSheet) {
        val promptSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )
        ModalBottomSheet(
            onDismissRequest = { showPromptDebugSheet = false },
            sheetState = promptSheetState,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 32.dp,
                ),
            ) {
                item {
                    Text(
                        text = "上下文调试",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item {
                    SelectionContainer {
                        Text(
                            text = latestPromptDebugDump.ifBlank { "暂无提示词调试信息" },
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }

    if (showConfirmResetDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmResetDialog = false },
            title = { Text("清空剧情") },
            text = { Text("确定要清空当前剧情的所有对话记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmResetDialog = false
                        onResetSession()
                    },
                ) {
                    Text("确定清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmResetDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showConfirmRestartDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmRestartDialog = false },
            title = { Text("重开剧情") },
            text = { Text("将创建一条新的剧情线，旧剧情历史不会继续沿用。是否继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmRestartDialog = false
                        onRestartSession()
                    },
                ) {
                    Text("重开")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRestartDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showAssistantMismatchDialog) {
        AlertDialog(
            onDismissRequest = onDismissAssistantMismatch,
            title = { Text("当前角色已改绑") },
            text = {
                Text(
                    "旧剧情绑定的是“$previousAssistantName”，当前场景改成了“$currentAssistantName”。继续沿用旧剧情可能出现人格和历史错位，建议重建剧情会话。",
                )
            },
            confirmButton = {
                TextButton(onClick = onRestartSession) {
                    Text("重建剧情")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissAssistantMismatch) {
                    Text("继续旧剧情")
                }
            },
        )
    }

    viewingPortrait?.let { spec ->
        PortraitViewerOverlay(
            spec = spec,
            onDismiss = { viewingPortrait = null },
        )
    }
}

@Composable
private fun CompactPresenceStrip(
    user: RoleplayPortraitSpec,
    character: RoleplayPortraitSpec,
    highlightedSpeaker: RoleplaySpeaker?,
    isSending: Boolean,
    onUserClick: () -> Unit,
    onCharacterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CompactPresenceChip(
            spec = user,
            isHighlighted = highlightedSpeaker == RoleplaySpeaker.USER,
            statusText = "用户",
            onClick = onUserClick,
            modifier = Modifier.weight(1f),
        )
        CompactPresenceChip(
            spec = character,
            isHighlighted = highlightedSpeaker == RoleplaySpeaker.CHARACTER || isSending,
            statusText = if (isSending) "回复中" else "角色",
            onClick = onCharacterClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactPresenceChip(
    spec: RoleplayPortraitSpec,
    isHighlighted: Boolean,
    statusText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageState = rememberUserProfileAvatarState(
        avatarUri = spec.avatarUri,
        avatarUrl = spec.avatarUrl,
    )
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isHighlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.64f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
            ) {
                if (imageState.loadState == UserAvatarLoadState.Success && imageState.imageBitmap != null) {
                    Image(
                        bitmap = imageState.imageBitmap,
                        contentDescription = spec.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = spec.name.take(1).ifBlank { spec.fallbackLabel.take(1) },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = spec.name.ifBlank { spec.fallbackLabel },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RoleplayStatusOverview(
    scenarioTitle: String,
    characterName: String,
    currentModel: String,
    contextStatus: RoleplayContextStatus,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (contextStatus.isContinuingSession) {
                    "当前正在继续 $scenarioTitle 的旧剧情"
                } else {
                    "当前是 $characterName 的新剧情入口"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContextStatusPill(text = if (contextStatus.isContinuingSession) "继续剧情" else "新剧情")
                ContextStatusPill(text = if (contextStatus.hasSummary) "摘要 ${contextStatus.summaryCoveredMessageCount}" else "未摘要")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContextStatusPill(text = "世界书 ${contextStatus.worldBookHitCount}")
                ContextStatusPill(text = "记忆 ${contextStatus.memoryInjectionCount}")
                currentModel.takeIf { it.isNotBlank() }?.let { modelName ->
                    ContextStatusPill(text = modelName)
                }
            }
        }
    }
}

@Composable
private fun ActionSheetSection(
    title: String,
    rows: List<RoleplayActionRow>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        rows.forEach { row ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = row.enabled, onClick = row.onClick),
                shape = RoundedCornerShape(18.dp),
                color = if (row.enabled) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (row.enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = row.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextStatusPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PortraitViewerOverlay(
    spec: RoleplayPortraitSpec,
    onDismiss: () -> Unit,
) {
    val imageState = rememberUserProfileAvatarState(
        avatarUri = spec.avatarUri,
        avatarUrl = spec.avatarUrl,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (imageState.loadState == UserAvatarLoadState.Success &&
                imageState.imageBitmap != null
            ) {
                Image(
                    bitmap = imageState.imageBitmap,
                    contentDescription = spec.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .clip(RoundedCornerShape(28.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = 0.08f),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = spec.fallbackLabel,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            Text(
                text = spec.name.ifBlank { spec.fallbackLabel },
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "点击任意处关闭",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.4f),
            )
        }
    }
}
