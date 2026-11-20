package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.example.myapplication.model.CompanionTurnKind
import com.example.myapplication.roleplay.RoleplayOutputParser
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.RoleplayCompanionUiState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** 只补保存当前身份/运行中的已收结果，不导航或激活结果所属的旧场次。 */
@Composable
internal fun CompanionPendingResultsBar(
    state: RoleplayCompanionUiState,
    characterName: String,
    onSave: (String) -> Unit,
) {
    var showResults by rememberSaveable(state.scenarioId) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        NarraTextButton(
            onClick = { showResults = true },
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .testTag("companion_pending_results"),
        ) { Text("待保存结果（${state.pendingResults.size}）") }
    }
    if (showResults) NarraAlertDialog(
        title = "待保存结果",
        message = "这些完整结果已经保留，可补保存到原场次。不会重新生成或切换当前会话。",
        onDismiss = { showResults = false },
        onConfirm = { showResults = false },
        confirmLabel = "关闭",
        dismissLabel = null,
        content = {
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.pendingResults.forEach { result ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = NarraUi.space4),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
                ) {
                    val kind = when (result.turnKind) {
                        CompanionTurnKind.OPENING -> "开场回复"
                        CompanionTurnKind.INPUT -> "输入回复"
                        CompanionTurnKind.CONTINUE -> "继续回复"
                    }
                    val time = DateFormat.getDateTimeInstance(
                        DateFormat.SHORT, DateFormat.SHORT,
                        Locale.SIMPLIFIED_CHINESE
                    ).format(Date(result.createdAt))
                    Text("$time · $kind", style = MaterialTheme.typography.labelLarge)
                    Text(
                        RoleplayOutputParser().parseAssistantOutput(
                            result.rawReply,
                            characterName.ifBlank { "角色" }, allowNarration = true
                        )
                            .joinToString("\n") { it.content },
                        maxLines = 6, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    NarraTextButton(
                        onClick = { onSave(result.attemptId) }, enabled = !state.isBusy,
                        modifier = Modifier.testTag("companion_save_pending_${result.attemptId}"),
                    ) { Text("补保存这份结果") }
                }
            }
        },
    )
}
