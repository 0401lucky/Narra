package com.example.myapplication.ui.screen.roleplay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.screen.settings.ContextTransferRecoveryList
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.CompanionAppearanceTransferUiState

data class CompanionAppearanceTransferCallbacks(
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onConfirm: () -> Unit,
    val onDismissPreview: () -> Unit,
    val onResume: (String) -> Unit,
    val onConsumeMessage: () -> Unit,
    val onDismiss: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanionAppearanceTransferScreen(
    state: CompanionAppearanceTransferUiState,
    callbacks: CompanionAppearanceTransferCallbacks,
) {
    BackHandler { if (state.preview != null && !state.isBusy) callbacks.onDismissPreview() else callbacks.onDismiss() }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().testTag("companion_appearance_transfer")
        ) {
            NarraTextButton(
                onClick = callbacks.onDismiss,
                modifier = Modifier.testTag("companion_appearance_transfer_close")
            ) { Text("返回") }
            if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(NarraUi.space16),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
            ) {
                Text("本地装扮包", style = MaterialTheme.typography.titleLarge)
                Text("只分享套装、表情键、外观布局和图片。私人原文、记忆、提示补充、音色样本与密钥不在包内。")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                    NarraTextButton(
                        onClick = callbacks.onExport,
                        enabled = !state.isBusy,
                        modifier = Modifier.testTag("companion_appearance_export")
                    ) { Text("导出装扮 JSON") }
                    NarraTextButton(
                        onClick = callbacks.onImport,
                        enabled = !state.isBusy,
                        modifier = Modifier.testTag("companion_appearance_import")
                    ) { Text("导入装扮 JSON") }
                }
                state.message?.let { message ->
                    Text(message, modifier = Modifier.testTag("companion_appearance_message"))
                    NarraTextButton(onClick = callbacks.onConsumeMessage) { Text("关闭提示") }
                }
                state.preview?.let { preview ->
                    Column(
                        Modifier.fillMaxWidth().testTag("companion_appearance_preview"),
                        verticalArrangement = Arrangement.spacedBy(NarraUi.space8)
                    ) {
                        Text("导入预览", style = MaterialTheme.typography.titleMedium)
                        Text("目标：${preview.targetName}")
                        Text("${preview.outfitCount} 套装扮 · ${preview.imageCount} 张图片")
                        Text(
                            "确认后更新此角色的装扮。若预览后配置已改变，将保留现值并提示重新预览。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                            NarraTextButton(
                                onClick = callbacks.onConfirm,
                                enabled = !state.isBusy,
                                modifier = Modifier.testTag("companion_appearance_accept")
                            ) { Text("确认导入") }
                            NarraTextButton(
                                onClick = callbacks.onDismissPreview,
                                enabled = !state.isBusy
                            ) { Text("取消预览") }
                        }
                    }
                }
                ContextTransferRecoveryList(
                    state.pendingOperations,
                    state.isBusy,
                    callbacks.onResume
                )
            }
        }
    }
}
