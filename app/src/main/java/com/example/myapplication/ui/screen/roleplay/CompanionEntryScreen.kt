package com.example.myapplication.ui.screen.roleplay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.myapplication.model.CompanionOpeningMode
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.CompanionEntryPhase
import com.example.myapplication.viewmodel.CompanionRecoveryAction
import com.example.myapplication.viewmodel.RoleplayCompanionUiState

data class CompanionEntryCallbacks(
    val onChooseOpening: (CompanionOpeningMode) -> Unit,
    val onRegenerate: () -> Unit,
    val onAccept: () -> Unit,
    val onRecover: () -> Unit,
    val onCancel: () -> Unit,
    val onBack: () -> Unit,
    val onRead: () -> Unit,
    val onSettings: () -> Unit,
    val onSaveZone: (String) -> Unit,
    val onSavePendingResult: (String) -> Unit = {},
    val onExpressionSettings: (() -> Unit)? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanionEntryScreen(
    state: RoleplayCompanionUiState,
    characterName: String,
    callbacks: CompanionEntryCallbacks,
) {
    BackHandler(onBack = callbacks.onBack)
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(NarraUi.space16),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space12)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                NarraTextButton(onClick = callbacks.onBack) { Text("返回") }
                NarraTextButton(
                    onClick = callbacks.onRead,
                    enabled = state.encounter != null
                ) { Text("查看记录") }
                NarraTextButton(
                    onClick = callbacks.onSettings,
                    enabled = state.encounter != null
                ) { Text("场景设置") }
                callbacks.onExpressionSettings?.let { open ->
                    NarraTextButton(
                        onClick = open,
                        enabled = state.encounter != null
                    ) { Text("陪伴表达") }
                }
            }
            Text(
                if (characterName.isBlank()) "准备见面" else "与 $characterName 见面",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "选择这次相遇的开场。预览接受后才会加入共同经历。",
                style = MaterialTheme.typography.bodyMedium
            )
            if (state.phase == CompanionEntryPhase.LOADING) CircularProgressIndicator()
            if (state.errorMessage != null) Text(
                state.errorMessage, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("companion_error")
            )
            if (state.encounter == null && state.phase != CompanionEntryPhase.LOADING) {
                Text("当前角色或会话不可用。可以返回角色列表后重新选择。")
            }
            if (state.encounter != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space8)
                ) {
                    OpeningButton(
                        "我靠近",
                        "companion_approach",
                        !state.isBusy
                    ) { callbacks.onChooseOpening(CompanionOpeningMode.APPROACH) }
                    OpeningButton(
                        "角色靠近",
                        "companion_invite",
                        !state.isBusy
                    ) { callbacks.onChooseOpening(CompanionOpeningMode.INVITE) }
                    OpeningButton(
                        "直接开始",
                        "companion_direct",
                        !state.isBusy
                    ) { callbacks.onChooseOpening(CompanionOpeningMode.DIRECT) }
                }
                Text(
                    "直接开始后可输入，或用“继续”留出角色回应的空间。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.preview.isNotBlank()) {
                Text("开场预览", style = MaterialTheme.typography.titleMedium)
                Text(
                    state.previewReply?.displayText
                        ?: com.example.myapplication.roleplay.RoleplayOutputParser()
                            .parseAssistantOutput(
                                state.preview,
                                characterName.ifBlank { "角色" },
                                allowNarration = true,
                            ).joinToString("\n\n") { it.content },
                    modifier = Modifier.testTag("companion_preview"),
                    style = MaterialTheme.typography.bodyLarge
                )
                CompanionObservationPanel(state.previewReply, enabled = state.observationEnabled)
            }
            if (state.phase == CompanionEntryPhase.GENERATING || state.phase == CompanionEntryPhase.ACCEPTING) {
                Text(if (state.phase == CompanionEntryPhase.ACCEPTING) "正在保存开场…" else "正在生成开场…")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                if (state.canAccept || state.phase == CompanionEntryPhase.ACCEPTING) {
                    NarraTextButton(
                        onClick = callbacks.onAccept, enabled = state.canAccept,
                        modifier = Modifier.testTag("companion_accept")
                    ) { Text("接受开场") }
                }
                if (state.isBusy) NarraTextButton(onClick = callbacks.onCancel) { Text("停止") }
                if (state.recoveryAction != CompanionRecoveryAction.NONE) {
                    NarraTextButton(
                        onClick = callbacks.onRecover, enabled = !state.isBusy,
                        modifier = Modifier.testTag("companion_recover")
                    ) { Text(recoveryLabel(state.recoveryAction)) }
                } else if (state.phase == CompanionEntryPhase.PREVIEW) {
                    NarraTextButton(
                        onClick = callbacks.onRegenerate,
                        enabled = !state.isBusy
                    ) { Text("重新生成开场") }
                }
            }
            if (state.encounter != null) {
                var zone by rememberSaveable(
                    state.encounter.context.characterId,
                    state.settingsRevision
                ) { mutableStateOf(state.zoneId) }
                OutlinedTextField(
                    value = zone,
                    onValueChange = { zone = it },
                    label = { Text("角色时区（可留空）") },
                    supportingText = { Text("例如 Asia/Shanghai；留空使用设备时区。仅在场景开启时间感知时生效。") },
                    modifier = Modifier.fillMaxWidth().testTag("companion_zone"),
                    enabled = !state.isBusy
                )
                NarraTextButton(
                    onClick = { callbacks.onSaveZone(zone) },
                    enabled = !state.isBusy && zone != state.zoneId
                ) { Text("保存时区") }
            }
        }
    }
}

@Composable
private fun OpeningButton(label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    NarraTextButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.heightIn(min = NarraMinimumTouchTarget).testTag(tag)
    ) { Text(label) }
}

data class CompanionSceneCallbacks(
    val onContinue: () -> Unit,
    val onRegenerate: () -> Unit,
    val onRecover: () -> Unit,
    val onNewEncounter: () -> Unit,
    val onExpressionSettings: (() -> Unit)? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompanionSceneActions(
    state: RoleplayCompanionUiState,
    callbacks: CompanionSceneCallbacks,
    reply: com.example.myapplication.model.ParsedCompanionReply? = null,
    observationEnabled: Boolean = false,
) {
    var showIncomplete by rememberSaveable { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = NarraUi.space12)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                NarraTextButton(
                    onClick = callbacks.onContinue,
                    enabled = !state.isBusy && state.recoveryAction == CompanionRecoveryAction.NONE,
                    modifier = Modifier.testTag("companion_continue")
                ) { Text("继续") }
                if (state.recoveryAction != CompanionRecoveryAction.NONE) {
                    NarraTextButton(
                        onClick = callbacks.onRecover, enabled = !state.isBusy,
                        modifier = Modifier.testTag("companion_recover")
                    ) { Text(recoveryLabel(state.recoveryAction)) }
                } else if (state.turn?.replyMessageId != null) {
                    NarraTextButton(
                        onClick = callbacks.onRegenerate, enabled = !state.isBusy,
                        modifier = Modifier.testTag("companion_regenerate")
                    ) { Text("重新生成最新回复") }
                }
                NarraTextButton(
                    onClick = callbacks.onNewEncounter, enabled = !state.isBusy,
                    modifier = Modifier.testTag("companion_new_encounter")
                ) { Text("开始新一场") }
                callbacks.onExpressionSettings?.let { open ->
                    NarraTextButton(onClick = open) { Text("陪伴表达") }
                }
                if (state.preview.isNotBlank() && state.completionStatus != PromptCompletionStatus.COMPLETED) {
                    NarraTextButton(onClick = { showIncomplete = true }) { Text("查看未完成结果") }
                }
            }
            CompanionObservationPanel(reply, enabled = observationEnabled)
        }
    }
    if (showIncomplete) NarraAlertDialog(
        title = "未完成结果", message = state.preview,
        onDismiss = { showIncomplete = false }, onConfirm = { showIncomplete = false },
        confirmLabel = "关闭", dismissLabel = null
    )
}

private fun recoveryLabel(action: CompanionRecoveryAction): String = when (action) {
    CompanionRecoveryAction.GENERATE -> "重新请求（可能计费）"
    CompanionRecoveryAction.SAVE -> "重试保存"
    CompanionRecoveryAction.ACCEPT -> "重试接受开场"
    CompanionRecoveryAction.NONE -> ""
}
