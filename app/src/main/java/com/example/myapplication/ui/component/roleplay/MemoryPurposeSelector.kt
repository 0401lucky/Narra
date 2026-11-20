package com.example.myapplication.ui.component.roleplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi

@Composable
fun MemoryPurposeSelector(
    purpose: MemoryPurpose,
    onPurposeChange: (MemoryPurpose) -> Unit,
    modifier: Modifier = Modifier,
    originalPurpose: MemoryPurpose? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
        Text("记忆用途", style = MaterialTheme.typography.titleSmall)
        val choices = if (originalPurpose == MemoryPurpose.LEGACY_COMPAT) {
            listOf(MemoryPurpose.LEGACY_COMPAT, MemoryPurpose.CONTINUOUS, MemoryPurpose.INDEPENDENT)
        } else {
            listOf(MemoryPurpose.CONTINUOUS, MemoryPurpose.INDEPENDENT)
        }
        choices.forEach { choice ->
            FilterChip(
                selected = purpose == choice,
                onClick = { onPurposeChange(choice) },
                modifier = Modifier.fillMaxWidth().heightIn(min = NarraMinimumTouchTarget)
                    .testTag("memory_purpose_${choice.storageValue}"),
                shape = NarraShapes.small,
                label = { Text(choice.memoryPurposeLabel()) },
            )
        }
        Text(
            text = when (purpose) {
                MemoryPurpose.CONTINUOUS -> "日常相处按角色积累记忆，重新开始会话仍保留长期记忆。"
                MemoryPurpose.INDEPENDENT -> "独立剧情使用自己的档案，重新开始会建立新档案，旧档案保留。"
                MemoryPurpose.LEGACY_COMPAT -> "保留旧版的记忆范围，不自动迁入日常相处或独立剧情。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (originalPurpose != null && originalPurpose != purpose) {
            Text(
                "保存后使用新会话和对应档案；原对话与旧档案会保留。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

fun MemoryPurpose.memoryPurposeLabel(): String = when (this) {
    MemoryPurpose.CONTINUOUS -> "日常相处"
    MemoryPurpose.INDEPENDENT -> "独立剧情"
    MemoryPurpose.LEGACY_COMPAT -> "保留旧范围"
}
