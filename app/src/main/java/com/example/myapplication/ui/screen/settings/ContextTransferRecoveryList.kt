package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.myapplication.model.ContextTransferOperationSummary
import com.example.myapplication.model.ContextTransferStage
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi

@Composable
fun ContextTransferRecoveryList(
    operations: List<ContextTransferOperationSummary>,
    isBusy: Boolean,
    onResume: (String) -> Unit,
) {
    if (operations.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
        Text("未完成的导入", style = MaterialTheme.typography.titleMedium)
        operations.forEach { operation ->
            Surface(
                shape = NarraShapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(NarraUi.space12),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space4)
                ) {
                    Text(operation.title)
                    Text(
                        when (operation.stage) {
                            ContextTransferStage.PLANNED -> "待继续导入"
                            ContextTransferStage.ROOM_COMMITTED -> "资料已保存，待继续素材与设置"
                            ContextTransferStage.FILES_COMMITTED -> "素材已保存，待更新设置"
                            ContextTransferStage.COMPLETE -> "导入完成"
                            ContextTransferStage.CANCELLED -> "已取消"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (operation.settingsTargetCount > 0) Text(
                        "已更新 ${operation.completedSettingsTargets}/${operation.settingsTargetCount} 个角色",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (operation.canResume) NarraTextButton(
                        onClick = { onResume(operation.operationId) }, enabled = !isBusy,
                        modifier = Modifier.testTag("context_transfer_resume_${operation.operationId}"),
                    ) { Text("继续导入") }
                }
            }
        }
    }
}
