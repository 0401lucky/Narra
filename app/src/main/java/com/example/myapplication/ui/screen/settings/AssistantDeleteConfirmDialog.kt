package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.component.NarraAlertDialog

@Composable
internal fun AssistantDeleteConfirmDialog(
    assistantName: String,
    extraMessage: String = "",
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    NarraAlertDialog(
        title = "删除助手",
        message = "将删除“$assistantName”，这个操作不可撤销。",
        onDismiss = onDismissRequest,
        onConfirm = onConfirm,
        confirmLabel = "确认删除",
        dismissLabel = "取消",
        isDestructive = true,
        content = if (extraMessage.isNotBlank()) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = extraMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else null,
    )
}
